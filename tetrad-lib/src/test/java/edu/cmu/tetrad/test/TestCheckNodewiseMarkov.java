package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.io.File;
import java.util.List;



public class TestCheckNodewiseMarkov {

    public static void main(String... args) {
//        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(10, 40, 40, 0.5, 1.0, 0.8);
        String filePath = "testTrueGraphForCheckNodewiseMarkov.txt";
        File file = new File(filePath);
        if (file.exists()) {
            System.out.println("Loading true graph file: " + filePath);
            testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(file, 0.5, 1.0, 0.8);
        } else {
            System.out.println("File does not exist at the specified path.");
        }
    }

    public static void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket(File txtFile, double threshold, double shuffleThreshold, double lowRecallBound) {
        Graph trueGraph = GraphSaveLoadUtils.loadGraphTxt(txtFile);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        DataSet data = im.simulateData(10000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
//        TODO VBC: Next check different search algo to generate estimated graph. e.g. PC
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        System.out.println("~~~~~~~~~~~~~Full Graph~~~~~~~~~~~~~~~");
        estimatedCpdag = GraphUtils.replaceNodes(estimatedCpdag, trueGraph.getNodes());
        double whole_ap = new AdjacencyPrecision().getValue(trueGraph, estimatedCpdag, null);
        double whole_ar = new AdjacencyRecall().getValue(trueGraph, estimatedCpdag, null);
        double whole_ahp = new ArrowheadPrecision().getValue(trueGraph, estimatedCpdag, null);
        double whole_ahr = new ArrowheadRecall().getValue(trueGraph, estimatedCpdag, null);
        double whole_lgp = new LocalGraphPrecision().getValue(trueGraph, estimatedCpdag, null);
        double whole_lgr = new LocalGraphRecall().getValue(trueGraph, estimatedCpdag, null);
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
        DataSet data = im.simulateData(10000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
//        TODO VBC: Next check different search algo to generate estimated graph. e.g. PC
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag, threshold, shuffleThreshold, lowRecallBound);
        System.out.println("~~~~~~~~~~~~~Full Graph~~~~~~~~~~~~~~~");
        estimatedCpdag = GraphUtils.replaceNodes(estimatedCpdag, trueGraph.getNodes());
        double whole_ap = new AdjacencyPrecision().getValue(trueGraph, estimatedCpdag, null);
        double whole_ar = new AdjacencyRecall().getValue(trueGraph, estimatedCpdag, null);
        double whole_ahp = new ArrowheadPrecision().getValue(trueGraph, estimatedCpdag, null);
        double whole_ahr = new ArrowheadRecall().getValue(trueGraph, estimatedCpdag, null);
        double whole_lgp = new LocalGraphPrecision().getValue(trueGraph, estimatedCpdag, null);
        double whole_lgr = new LocalGraphRecall().getValue(trueGraph, estimatedCpdag, null);
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
        DataSet data = im.simulateData(1000, false);
        data = DataTransforms.shuffleColumns(data); // Permute the data columns, this matters to some algorithms, e.g. PC.
        SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
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
