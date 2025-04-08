package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



public class TestCheckNodewiseMarkov {

    public static void main(String... args) {
//        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(10, 40, 40, 0.5, 1.0, 0.8);//        String filePath = "testTrueGraphForCheckNodewiseMarkov.txt";
//        File file = new File(filePath);
//        if (file.exists()) {
//            System.out.println("Loading true graph file: " + filePath);
//            // testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(file, 0.5, 1.0, 0.8);
//            testGaussianDAGPrecisionRecallForLocalOnDirectNeighbours(file, 0.5, 1.0, 0.8);
//        } else {
//            System.out.println("File does not exist at the specified path.");
//        }

        // Create overall simulation directory per run
        File markovSimulationDir = new File("markovCheckSimulation/");
        if (!markovSimulationDir.exists()) {
            boolean created = markovSimulationDir.mkdirs();
            if (!created) {
                System.err.println("Failed to create markovCheckSimulation/ directory.");
            }
        }
        for (int run = 0; run < 10; run++) {
            testGaussianDAGPrecisionRecallForLatentVariableOnLocalOrderedMarkov(run,10, 0,
                    20, 30, 40, 5, false, 0.5,
                    1.0, 0.8);
        }
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(File txtFile, double threshold, double shuffleThreshold, double lowRecallBound) {
        Graph trueGraph = GraphSaveLoadUtils.loadGraphTxt(txtFile);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(10000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        TODO VBC: Next check different search algo to generate estimated graph. e.g. PC
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        System.out.println("~~~~~~~~~~~~~Full Graph~~~~~~~~~~~~~~~");
        estimatedCpdag = GraphUtils.replaceNodes(estimatedCpdag, trueGraph.getNodes());
        double whole_ap = new AdjacencyPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ar = new AdjacencyRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahp = new ArrowheadPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahr = new ArrowheadRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgp = new LocalGraphPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgr = new LocalGraphRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        System.out.println("whole_ap: " + whole_ap);
        System.out.println("whole_ar: " + whole_ar );
        System.out.println("whole_ahp: " + whole_ahp);
        System.out.println("whole_ahr: " + whole_ahr);
        System.out.println("whole_lgp: " + whole_lgp);
        System.out.println("whole_lgr: " + whole_lgr);
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(int numNodes, int maxNumEdges, int maxDegree, double threshold, double shuffleThreshold, double lowRecallBound) {
//        Graph trueGraph = RandomGraph.randomDag(100, 0, 400, 100, 100, 100, false);
        Graph trueGraph = RandomGraph.randomDag(numNodes, 0, maxNumEdges, maxDegree, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(10000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        TODO VBC: Next check different search algo to generate estimated graph. e.g. PC
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        System.out.println("~~~~~~~~~~~~~Full Graph~~~~~~~~~~~~~~~");
        estimatedCpdag = GraphUtils.replaceNodes(estimatedCpdag, trueGraph.getNodes());
        double whole_ap = new AdjacencyPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ar = new AdjacencyRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahp = new ArrowheadPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahr = new ArrowheadRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgp = new LocalGraphPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgr = new LocalGraphRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        System.out.println("whole_ap: " + whole_ap);
        System.out.println("whole_ar: " + whole_ar );
        System.out.println("whole_ahp: " + whole_ahp);
        System.out.println("whole_ahr: " + whole_ahr);
        System.out.println("whole_lgp: " + whole_lgp);
        System.out.println("whole_lgr: " + whole_lgr);
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(DataSet data, Graph trueGraph, Graph estimatedCpdag, double threshold, double shuffleThreshold, double lowRecallBound) {
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // Using Adj, AH confusion matrix
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData(fisherZTest, estimatedCpdag, trueGraph, threshold, shuffleThreshold, lowRecallBound);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(DataSet data, Graph trueGraph, Graph estimatedCpdag, double threshold, double shuffleThreshold, double lowRecallBound) {
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // Using Local Graph (LG) confusion matrix
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData2(fisherZTest, estimatedCpdag, trueGraph, threshold, shuffleThreshold, lowRecallBound);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    public static void testGaussianDAGPrecisionRecallForLatentVariableOnLocalOrderedMarkov(int runID, int numNodes, int numLatentConfounders,
                                                                                           int maxNumEdges, int maxDegree, int maxIndegree,
                                                                                           int maxOutdegree, boolean connected,
                                                                                           double threshold, double shuffleThreshold, double lowRecallBound) {
        // Create simulation directory per run
        File simulationDir = new File("markovCheckSimulation/simulation"+runID+"/");
        if (!simulationDir.exists()) {
            boolean created = simulationDir.mkdirs();
            if (!created) {
                System.err.println("Failed to create simulation directory.");
            }
        }
        // Graph trueGraph = GraphSaveLoadUtils.loadGraphTxt(txtFile);
        // Graph trueGraph = RandomGraph.randomDag(100, 0, 400, 100, 100, 100, false);
        Graph trueGraph = RandomGraph.randomDag(numNodes, numLatentConfounders, maxNumEdges, maxDegree, maxIndegree, maxOutdegree, connected);
        File graphFile = new File(simulationDir, "trueGraph.txt");
        try (Writer out = new FileWriter(graphFile)) {
            out.write(trueGraph.toString());
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception while saving graph: " + e.getMessage());
        }
        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        int sampleSize = 10000;
        DataSet data = im.simulateData(sampleSize, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC
        // Save dataset to the simulation  directory
        File datasetFile = new File(simulationDir, "permutedData.txt");
        try {
            Writer out = new FileWriter(datasetFile);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception while saving dataset: " + e.getMessage());
        }
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);

        // Simulate different FCI methods
        List<Graph> estimatedFCIPAGs = new ArrayList<>();
        List<String> methodNames = Arrays.asList("BossFCI", "GaspFCI", "FCI", "FCIMax", "RFCI");
        for (String methodName : methodNames) {
            try {
                Graph estimatedPAG = null;

                // Create FCI-method-specific subdirectory
                File methodDir = new File(simulationDir, methodName);
                if (!methodDir.exists() && !methodDir.mkdirs()) {
                    throw new IOException("Failed to create directory: " + methodDir.getAbsolutePath());
                }

                switch (methodName) {
                    case "BossFCI":
                        BossFci bossFCI = new BossFci(fisherZTest, score);
                        bossFCI.setGuaranteePag(true);
                        estimatedPAG = bossFCI.search();
                        break;
                    case "GaspFCI":
                        GraspFci gaspFCI = new GraspFci(fisherZTest, score);
                        gaspFCI.setGuaranteePag(true);
                        estimatedPAG = gaspFCI.search();
                        break;
                    case "FCI":
                        Fci fci = new Fci(fisherZTest); // seems fci does not need score input?
                        fci.setGuaranteePag(true);
                        estimatedPAG = fci.search();
                        break;
                    case "FCIMax":
                        FciMax fciMax = new FciMax(fisherZTest); // seems fciMax does not need score input?
                        fciMax.setGuaranteePag(true);
                        estimatedPAG = fciMax.search();
                        break;
                    case "RFCI":
                        Rfci rfci = new Rfci(fisherZTest); // seems fciMax does not need score input?
                        estimatedPAG = rfci.search(); // returns the RFCI PAG.
                        break;
                    // TODO: Add other FCI variants here if needed
                    default:
                        throw new IllegalArgumentException("Unsupported FCI method: " + methodName);
                }
                // estimatedFCIPAGs.add(estimatedPAG);

                // Save estimated graph
                File estGraphFile = new File(methodDir, "estimatedPAG.txt");
                try (Writer out = new FileWriter(estGraphFile)) {
                    out.write(estimatedPAG.toString());
                } catch (IOException e) {
                    TetradLogger.getInstance().log("IO Exception while saving graph for " + methodName + ": " + e.getMessage());
                }

                // Stats Evaluation
                File statsFile = new File(methodDir, "stats.txt");
                testGaussianDAGPrecisionRecallForForLatentVariableOnLocalOrderedMarkov(
                        statsFile, fisherZTest, data, trueGraph, estimatedPAG, threshold, shuffleThreshold, lowRecallBound);

                estimatedPAG = GraphUtils.replaceNodes(estimatedPAG, trueGraph.getNodes());
                Graph truePAG = GraphTransforms.dagToPag(trueGraph);

                double whole_ap = new AdjacencyPrecision().getValue(truePAG, estimatedPAG, null, new Parameters());
                double whole_ar = new AdjacencyRecall().getValue(truePAG, estimatedPAG, null, new Parameters());
                double whole_ahp = new ArrowheadPrecision().getValue(truePAG, estimatedPAG, null, new Parameters());
                double whole_ahr = new ArrowheadRecall().getValue(truePAG, estimatedPAG, null, new Parameters());
                double whole_lgp = new LocalGraphPrecision().getValue(truePAG, estimatedPAG, null, new Parameters());
                double whole_lgr = new LocalGraphRecall().getValue(truePAG, estimatedPAG, null, new Parameters());
                // Save statistical data in the simulation directory
                try (Writer out = new FileWriter(statsFile, true)) {
                    out.write("whole_ap: " + whole_ap + "\n" );
                    out.write("whole_ar: " + whole_ar + "\n" );
                    out.write("whole_ahp: " + whole_ahp + "\n" );
                    out.write("whole_ahr: " + whole_ahr + "\n" );
                    out.write("whole_lgp: " + whole_lgp + "\n" );
                    out.write("whole_lgr: " + whole_lgr + "\n" );
                } catch (IOException e) {
                    TetradLogger.getInstance().log("IO Exception while saving statistics: " + e.getMessage());
                }
                File descriptionFile = new File(methodDir, "description.txt");
                try (Writer out = new FileWriter(descriptionFile)) {
                    out.write("Simulated Gaussian DAG with the following RandomGraph.randomDag(...) parameters:\n");
                    out.write("numNodes: " + numNodes + "\n" );
                    out.write("numLatentConfounders: " + numLatentConfounders + "\n" );
                    out.write("maxNumEdges: " + maxNumEdges + "\n" );
                    out.write("maxDegree: " + maxDegree + "\n" );
                    out.write("maxIndegree: " + maxIndegree + "\n" );
                    out.write("maxOutdegree: " + maxOutdegree + "\n" );
                    out.write("connected: " + connected + "\n" );
                    out.write("\n");
                    out.write("Other Simulation Settings:\n");
                    out.write("threshold: " + threshold + "\n" );
                    out.write("shuffleThreshold: " + shuffleThreshold + "\n" );
                    out.write("lowRecallBound: " + lowRecallBound + "\n" );
                } catch (IOException e) {
                    TetradLogger.getInstance().log("IO Exception while saving description: " + e.getMessage());
                }

                System.out.println("-----------------------Graph Simulation " + runID +" for : "+ methodName + "-----------------------");
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("~~~~~~~~~~~~~Graph Simulation " + runID + "~~~~~~~~~~~~~~~");
    }

    /**
     * For LV-light paper's usage under ORDERED_LOCAL_MARKOV_MAG conditioning set type
     * @see OrderedLocalMarkovProperty
     * @see ConditioningSetType
     */
    private static void testGaussianDAGPrecisionRecallForForLatentVariableOnLocalOrderedMarkov(File statsFile, IndependenceTest fisherZTest, DataSet data, Graph trueGraph, Graph estimatedPAG, double threshold, double shuffleThreshold, double lowRecallBound) {
        MarkovCheck markovCheck = new MarkovCheck(estimatedPAG, fisherZTest, ConditioningSetType.ORDERED_LOCAL_MARKOV_MAG);
        markovCheck.generateResults(true);
        double andersonDarlingA2 = markovCheck.getAndersonDarlingA2(true);
        double andersonDarlingP = markovCheck.getAndersonDarlingP(true);
        double finsherCombinedP = markovCheck.getFisherCombinedP(true);
        double kSPvalue = markovCheck.getKsPValue(true);
        double fractionDep = markovCheck.getFractionDependent(true);
        // number of tests generateResults actually did
        int numTests = markovCheck.getNumTests(true);

        // Save further statistical data in the simulation stats
        try (Writer out = new FileWriter(statsFile)) {
            out.write("andersonDarlingA2: " + andersonDarlingA2 + "\n" );
            out.write("andersonDarlingP: " + andersonDarlingP + "\n" );
            out.write("finsherCombinedP: " + finsherCombinedP + "\n" );
            out.write("kSPvalue: " + kSPvalue + "\n" );
            out.write("fractionDep: " + fractionDep + "\n" );
            out.write("numTests: " + numTests + "\n" );
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception while saving statistics: " + e.getMessage());
        }
        // TODO VBC: print a report file of one role of each graph, each col be the above stats numbers
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnDirectNeighbours(File txtFile, double threshold, double shuffleThreshold, double lowRecallBound) {
        Graph trueGraph = GraphSaveLoadUtils.loadGraphTxt(txtFile);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(10000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
//        TODO VBC: Next check different search algo to generate estimated graph. e.g. PC
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        System.out.println("~~~~~~~~~~~~~Full Graph~~~~~~~~~~~~~~~");
        estimatedCpdag = GraphUtils.replaceNodes(estimatedCpdag, trueGraph.getNodes());
        double whole_ap = new AdjacencyPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ar = new AdjacencyRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahp = new ArrowheadPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahr = new ArrowheadRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgp = new LocalGraphPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgr = new LocalGraphRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        System.out.println("whole_ap: " + whole_ap);
        System.out.println("whole_ar: " + whole_ar );
        System.out.println("whole_ahp: " + whole_ahp);
        System.out.println("whole_ahr: " + whole_ahr);
        System.out.println("whole_lgp: " + whole_lgp);
        System.out.println("whole_lgr: " + whole_lgr);
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnDirectNeighboursUsingAdjAHConfusionMatrix(DataSet data, Graph trueGraph, Graph estimatedCpdag, double threshold, double shuffleThreshold, double lowRecallBound) {
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.PARENTS_AND_NEIGHBORS);
        // Using Adj, AH confusion matrix
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData(fisherZTest, estimatedCpdag, trueGraph, threshold, shuffleThreshold, lowRecallBound);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnDirectNeighboursUsingLGConfusionMatrix(DataSet data, Graph trueGraph, Graph estimatedCpdag, double threshold, double shuffleThreshold, double lowRecallBound) {
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.PARENTS_AND_NEIGHBORS);
        // Using Local Graph (LG) confusion matrix
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData2(fisherZTest, estimatedCpdag, trueGraph, threshold, shuffleThreshold, lowRecallBound);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    @Test
    public void testGaussianCPDAGPrecisionRecallForLocalOnMarkovBlanket() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        // The completed partially directed acyclic graph (CPDAG) for the given DAG.
        Graph trueGraphCPDAG = GraphTransforms.dagToCpdag(trueGraph);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph CPDAG: " + trueGraphCPDAG);

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
//        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData(fisherZTest, estimatedCpdag, trueGraph, 0.05, 0.3, 0.8);

        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    @Test
    public void testNonGaussianDAGPrecisionRecallForLocalOnMarkovBlanket() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);

        Parameters params = new Parameters();
        // Manually set non-Gaussian
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm im = new SemIm(pm, params);
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        //        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.3);
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData(fisherZTest, estimatedCpdag, trueGraph, 0.05, 0.3, 0.8);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    @Test
    public void testNonGaussianCPDAGPrecisionRecallForLocalOnMarkovBlanket() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        // The completed partially directed acyclic graph (CPDAG) for the given DAG.
        Graph trueGraphCPDAG = GraphTransforms.dagToCpdag(trueGraph);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph CPDAG: " + trueGraphCPDAG);

        SemPm pm = new SemPm(trueGraph);

        Parameters params = new Parameters();
        // Manually set non-Gaussian
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm im = new SemIm(pm, params);
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        //        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData(fisherZTest, estimatedCpdag, trueGraph, 0.05, 0.3, 0.8);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }



    @Test
    public void testGaussianDAGPrecisionRecallForLocalOnParents() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        // TODO VBC: confirm on the choice of ConditioningSetType.
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.LOCAL_MARKOV);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());

        for(Node a: accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");

        }
        for (Node a: rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");
        }
    }

    @Test
    public void testGaussianCPDAGPrecisionRecallForLocalOnParents() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        // The completed partially directed acyclic graph (CPDAG) for the given DAG.
        Graph trueGraphCPDAG = GraphTransforms.dagToCpdag(trueGraph);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph CPDAG: " + trueGraphCPDAG);

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());

        // Compare the Est CPDAG with True graph's CPDAG.
        for(Node a: accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");

        }
        for (Node a: rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");
        }
    }

    @Test
    public void testNonGaussianDAGPrecisionRecallForLocalOnParents() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        Parameters params = new Parameters();
        // Manually set non-Gaussian
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm im = new SemIm(pm, params);
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        // TODO VBC: confirm on the choice of ConditioningSetType.
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.LOCAL_MARKOV);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());

        for(Node a: accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");

        }
        for (Node a: rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");
        }
    }

    @Test
    public void testNonGaussianCPDAGPrecisionRecallForLocalOnParents() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        // The completed partially directed acyclic graph (CPDAG) for the given DAG.
        Graph trueGraphCPDAG = GraphTransforms.dagToCpdag(trueGraph);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph CPDAG: " + trueGraphCPDAG);

        SemPm pm = new SemPm(trueGraph);

        Parameters params = new Parameters();
        // Manually set non-Gaussian
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm im = new SemIm(pm, params);
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());

        // Compare the Est CPDAG with True graph's CPDAG.
        for(Node a: accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");

        }
        for (Node a: rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");
        }
    }


    @Test
    public void testGaussianCPDAGPrecisionRecallForLocalOnMarkovBlanket2() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        // The completed partially directed acyclic graph (CPDAG) for the given DAG.
        Graph trueGraphCPDAG = GraphTransforms.dagToCpdag(trueGraph);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph CPDAG: " + trueGraphCPDAG);

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
//        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes2(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData2(fisherZTest, estimatedCpdag, trueGraph, 0.05, 0.3, 0.8);

        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    @Test
    public void testNonGaussianDAGPrecisionRecallForLocalOnMarkovBlanket2() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);

        Parameters params = new Parameters();
        // Manually set non-Gaussian
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm im = new SemIm(pm, params);
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        //        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes2(fisherZTest, estimatedCpdag, 0.05, 0.3);
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData2(fisherZTest, estimatedCpdag, trueGraph, 0.05, 0.3, 0.8);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    @Test
    public void testNonGaussianCPDAGPrecisionRecallForLocalOnMarkovBlanket2() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        // The completed partially directed acyclic graph (CPDAG) for the given DAG.
        Graph trueGraphCPDAG = GraphTransforms.dagToCpdag(trueGraph);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph CPDAG: " + trueGraphCPDAG);

        SemPm pm = new SemPm(trueGraph);

        Parameters params = new Parameters();
        // Manually set non-Gaussian
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm im = new SemIm(pm, params);
        // Simulate permuted dataset and save a copy of it.
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        File file = new File(".", "testPermutedData.txt");
        try {
            Writer out = new FileWriter(file);
            DataWriter.writeRectangularData(data, out, '\t');
            out.close();
        } catch (IOException e) {
            TetradLogger.getInstance().log("IO Exception: " + e.getMessage());
        }

        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = null;
        try {
            estimatedCpdag = new PermutationSearch(new Boss(score)).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        //        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes2(fisherZTest, estimatedCpdag, 0.05, 0.5);
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData2(fisherZTest, estimatedCpdag, trueGraph, 0.05, 0.3, 0.8);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }
}
