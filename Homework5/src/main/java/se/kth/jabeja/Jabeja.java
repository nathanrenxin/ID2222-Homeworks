package se.kth.jabeja;

import org.apache.log4j.Logger;
import se.kth.jabeja.config.Config;
import se.kth.jabeja.config.NodeSelectionPolicy;
import se.kth.jabeja.io.FileIO;
import se.kth.jabeja.rand.RandNoGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Jabeja {
  final static Logger logger = Logger.getLogger(Jabeja.class);
  private final Config config;
  private final HashMap<Integer/*id*/, Node/*neighbors*/> entireGraph;
  private final List<Integer> nodeIds;
  private int numberOfSwaps;
  private int round;
  private float T;
  private float T_min = 0.00001f;
  private boolean resultFileCreated = false;

  //-------------------------------------------------------------------
  public Jabeja(HashMap<Integer, Node> graph, Config config) {
    if (config.getTask()==2.1f || config.getTask()==3f)
      config.setTemperature(1f);
    this.entireGraph = graph;
    this.nodeIds = new ArrayList(entireGraph.keySet());
    this.round = 0;
    this.numberOfSwaps = 0;
    this.config = config;
    this.T = config.getTemperature();
  }


  //-------------------------------------------------------------------
  public void startJabeja() throws IOException {
    for (round = 0; round < config.getRounds(); round++) {

      if (config.getTask()==2.2f && round % config.getRestart()==0) {
          T = config.getTemperature();
      }
      //ArrayList<Integer> allNodes = new ArrayList<Integer>();
      //allNodes.addAll(entireGraph.keySet());
      //Collections.shuffle(allNodes,new Random(round));
      for (int id : entireGraph.keySet()) {
        sampleAndSwap(id);
      }
      //logger.info("node: " + 2395 + "color: " + entireGraph.get(2395).getColor());

      //one cycle for all nodes have completed.
      //reduce the temperature
      saCoolDown();
      report();
    }
  }

  /**
   * Simulated analealing cooling function
   */
  private void saCoolDown(){
    // TODO for second task
    if (config.getTask() == 1f || config.getTask()==2.2f) {
      if (T > 1)
        T -= config.getDelta();
      if (T < 1)
        T = 1;
    }
    else if (config.getTask()==2.1f || config.getTask()==3f){
      if (T > T_min) {
        T *= config.getAlpha2();
      }
    }
  }

  /**
   * Sample and swap algorith at node p
   * @param nodeId
   */
  private void sampleAndSwap(int nodeId) {
    Node partner = null;
    Node nodep = entireGraph.get(nodeId);

    if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
            || config.getNodeSelectionPolicy() == NodeSelectionPolicy.LOCAL) {
      // swap with random neighbors
      // TODO
      Integer[] neighbors = getNeighbors(nodep);
      partner = findPartner(nodep.getId(), neighbors);
    }

    if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
            || config.getNodeSelectionPolicy() == NodeSelectionPolicy.RANDOM) {
      // if local policy fails then randomly sample the entire graph
      // TODO
      if (partner == null) {
        Integer[] sample = getSample(nodep.getId());
        partner = findPartner(nodep.getId(), sample);
      }
    }

    // swap the colors
    // TODO
    if (partner != null && partner.getColor() != nodep.getColor()) {
      int colorToSwap = partner.getColor();
      partner.setColor(nodep.getColor());
      nodep.setColor(colorToSwap);
      numberOfSwaps++;
    }

  }

  public Node findPartner(int nodeId, Integer[] nodes){

    Node nodep = entireGraph.get(nodeId);

    Node bestPartner = null;
    double highestBenefit = 0;

    // TODO
    for (Integer node : nodes) {
      Node nodeq = entireGraph.get(node);

      int degreePP = getDegree(nodep, nodep.getColor());
      int degreeQQ = getDegree(nodeq, nodeq.getColor());
      double oldDegree = Math.pow(degreePP, config.getAlpha()) + Math.pow(degreeQQ, config.getAlpha());

      int degreePQ = getDegree(nodep, nodeq.getColor());
      int degreeQP = getDegree(nodeq, nodep.getColor());
      if (nodep.getNeighbours().contains(node)) {
        degreePQ = degreePQ - 1;
        degreeQP = degreeQP - 1;
      }
      double newDegree = Math.pow(degreePQ, config.getAlpha()) + Math.pow(degreeQP, config.getAlpha());

      if (config.getTask()==1f || config.getTask()==2.2f){
        if (newDegree * T > oldDegree && newDegree > highestBenefit) {
          bestPartner = nodeq;
          highestBenefit = newDegree;
        }
      }
      else if (config.getTask()==2.1f){
        if (calcAcceptanceProbability(newDegree, oldDegree, T)> RandNoGenerator.nextDouble() && newDegree > highestBenefit) {
          bestPartner = nodeq;
          highestBenefit = newDegree;
        }
      }
      else if (config.getTask()==3f){
        if (calcAcceptanceProbabilityB(newDegree, oldDegree, T)> RandNoGenerator.nextDouble() && newDegree > highestBenefit) {
          bestPartner = nodeq;
          highestBenefit = newDegree;
        }
      }
    }

    return bestPartner;
  }

  private double calcAcceptanceProbability(double newDegree, double oldDegree, float T) {
    return Math.exp((newDegree - oldDegree) / T);
  }

  private double calcAcceptanceProbabilityB(double newDegree, double oldDegree, float T) {
      return 1-((oldDegree - newDegree) / (Math.max(oldDegree, newDegree)*T));
  }

  /**
   * The the degreee on the node based on color
   * @param node
   * @param colorId
   * @return how many neighbors of the node have color == colorId
   */
  private int getDegree(Node node, int colorId){
    int degree = 0;
    for(int neighborId : node.getNeighbours()){
      Node neighbor = entireGraph.get(neighborId);
      if(neighbor.getColor() == colorId){
        degree++;
      }
    }
    return degree;
  }

  /**
   * Returns a uniformly random sample of the graph
   * @param currentNodeId
   * @return Returns a uniformly random sample of the graph
   */
  private Integer[] getSample(int currentNodeId) {
    int count = config.getUniformRandomSampleSize();
    int rndId;
    int size = entireGraph.size();
    ArrayList<Integer> rndIds = new ArrayList<Integer>();

    while (true) {
      rndId = nodeIds.get(RandNoGenerator.nextInt(size));
      if (rndId != currentNodeId && !rndIds.contains(rndId)) {
        rndIds.add(rndId);
        count--;
      }

      if (count == 0)
        break;
    }

    Integer[] ids = new Integer[rndIds.size()];
    return rndIds.toArray(ids);
  }

  /**
   * Get random neighbors. The number of random neighbors is controlled using
   * -closeByNeighbors command line argument which can be obtained from the config
   * using {@link Config#getRandomNeighborSampleSize()}
   * @param node
   * @return
   */
  private Integer[] getNeighbors(Node node) {
    ArrayList<Integer> list = node.getNeighbours();
    int count = config.getRandomNeighborSampleSize();
    int rndId;
    int index;
    int size = list.size();
    ArrayList<Integer> rndIds = new ArrayList<Integer>();

    if (size <= count)
      rndIds.addAll(list);
    else {
      while (true) {
        index = RandNoGenerator.nextInt(size);
        rndId = list.get(index);
        if (!rndIds.contains(rndId)) {
          rndIds.add(rndId);
          count--;
        }

        if (count == 0)
          break;
      }
    }

    Integer[] arr = new Integer[rndIds.size()];
    return rndIds.toArray(arr);
  }


  /**
   * Generate a report which is stored in a file in the output dir.
   *
   * @throws IOException
   */
  private void report() throws IOException {
    int grayLinks = 0;
    int migrations = 0; // number of nodes that have changed the initial color
    int size = entireGraph.size();

    for (int i : entireGraph.keySet()) {
      Node node = entireGraph.get(i);
      int nodeColor = node.getColor();
      ArrayList<Integer> nodeNeighbours = node.getNeighbours();

      if (nodeColor != node.getInitColor()) {
        migrations++;
      }

      if (nodeNeighbours != null) {
        for (int n : nodeNeighbours) {
          Node p = entireGraph.get(n);
          int pColor = p.getColor();

          if (nodeColor != pColor)
            grayLinks++;
        }
      }
    }

    int edgeCut = grayLinks / 2;

    logger.info("round: " + round +
            ", edge cut:" + edgeCut +
            ", swaps: " + numberOfSwaps +
            ", migrations: " + migrations);

    saveToFile(edgeCut, migrations);
  }

  private void saveToFile(int edgeCuts, int migrations) throws IOException {
    String delimiter = "\t\t";
    String outputFilePath;

    //output file name
    File inputFile = new File(config.getGraphFilePath());

    if (config.getTask()==1f){
      outputFilePath = config.getOutputDir() +
              File.separator +
              inputFile.getName() + "_" +
              "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
              "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
              "T" + "_" + config.getTemperature() + "_" +
              "D" + "_" + config.getDelta() + "_" +
              "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
              "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
              "A" + "_" + config.getAlpha() + "_" +
              "R" + "_" + config.getRounds() + "_" +
              "TASK" + "_" + config.getTask() +".txt";
    }
    else if (config.getTask()==2.1f){
      outputFilePath = config.getOutputDir() +
              File.separator +
              inputFile.getName() + "_" +
              "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
              "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
              "T" + "_" + config.getTemperature() + "_" +
              "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
              "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
              "A" + "_" + config.getAlpha() + "_" +
              "A2" + "_" + config.getAlpha2() + "_" +
              "R" + "_" + config.getRounds() + "_" +
              "TASK" + "_" + config.getTask() +".txt";
    }
    else if (config.getTask()==2.2f){
      outputFilePath = config.getOutputDir() +
              File.separator +
              inputFile.getName() + "_" +
              "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
              "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
              "T" + "_" + config.getTemperature() + "_" +
              "D" + "_" + config.getDelta() + "_" +
              "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
              "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
              "A" + "_" + config.getAlpha() + "_" +
              "R" + "_" + config.getRounds() + "_" +
              "RA" + "_" + config.getRestart() + "_" +
              "TASK" + "_" + config.getTask() +".txt";
    }
    else {
      outputFilePath = config.getOutputDir() +
              File.separator +
              inputFile.getName() + "_" +
              "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
              "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
              "T" + "_" + config.getTemperature() + "_" +
              "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
              "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
              "A" + "_" + config.getAlpha() + "_" +
              "A2" + "_" + config.getAlpha2() + "_" +
              "R" + "_" + config.getRounds() + "_" +
              "TASK" + "_" + config.getTask() +".txt";
    }


    if (!resultFileCreated) {
      File outputDir = new File(config.getOutputDir());
      if (!outputDir.exists()) {
        if (!outputDir.mkdir()) {
          throw new IOException("Unable to create the output directory");
        }
      }
      // create folder and result file with header
      String header = "# Migration is number of nodes that have changed color.";
      header += "\n\nRound" + delimiter + "Edge-Cut" + delimiter + "Swaps" + delimiter + "Migrations" + delimiter + "Skipped" + "\n";
      FileIO.write(header, outputFilePath);
      resultFileCreated = true;
    }

    FileIO.append(round + delimiter + (edgeCuts) + delimiter + numberOfSwaps + delimiter + migrations + "\n", outputFilePath);
  }
}
