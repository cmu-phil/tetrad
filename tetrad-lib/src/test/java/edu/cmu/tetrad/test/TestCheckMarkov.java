package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.Kci;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TestCheckMarkov {

    public static void main(String... args) {
        new TestCheckMarkov().test1();
    }

    public void test1() {
        double alpha = 0.05;
        int numIndep = 0;
        int total = 0;

        Graph dag = RandomGraph.randomDag(10, 0, 10, 100, 100,
                100, false);

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);

        Kci test = new Kci(data, alpha);
        test.setApproximate(true);
        test.setNumBootstraps(1000);
        test.setWidthMultiplier(1.0);
//
//        IndTestFisherZ test = new IndTestFisherZ(data, alpha);

        test.setVerbose(false);

        dag = GraphUtils.replaceNodes(dag, test.getVariables());

        System.out.println("DAG = " + dag);

        for (Node x : dag.getNodes()) {

            List<Node> desc = dag.paths().getDescendants(Collections.singletonList(x));

            List<Node> nondesc = dag.getNodes();
            nondesc.removeAll(desc);

            List<Node> cond = dag.getParents(x);

            System.out.println("Node " + x + " parents = " + cond
                               + " non-descendants = " + nondesc);

            for (Node y : nondesc) {
                System.out.print("\t" + LogUtilsSearch.independenceFact(x, y, new HashSet<>(cond)));

                IndependenceResult result = test.checkIndependence(x, y, new HashSet<>(cond));

                if (result.isIndependent()) {
                    numIndep++;
                }

                total++;

                System.out.print(" " + (result.isIndependent() ? "Independent" : "Dependent"));
                System.out.print(" p = " + result.getPValue());
                System.out.println();
            }

        }

        System.out.println();
        System.out.println("Alpha = " + alpha + " Fraction Dependent = " +
                           NumberFormatUtil.getInstance().getNumberFormat().format(
                                   1d - numIndep / (double) total));
    }

    /**
     * Test of getMarkovCheckRecordString method, of class MarkovCheck.
     */
    @Test
    public void test2() {
        Graph dag = RandomGraph.randomDag(10, 0, 10, 100, 100,
                100, false);
        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);

        SemBicScore score = new SemBicScore(data, true);

        PermutationSearch search = new PermutationSearch(new Boss(score));
        Graph cpdag = search.search();

        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        MarkovCheck markovCheck = new MarkovCheck(cpdag, test, ConditioningSetType.LOCAL_MARKOV);
        markovCheck.setPercentResample(0.7);

        System.out.println(markovCheck.getMarkovCheckRecordString());
    }

    @Test
    public void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket() {
//        Graph trueGraph = RandomGraph.randomDag(100, 0, 400, 100, 100, 100, false);
        Graph trueGraph = RandomGraph.randomDag(80, 0, 80, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        DataSet data = im.simulateData(10000, false);
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
        score.setPenaltyDiscount(2);
        Graph estimatedCpdag = new PermutationSearch(new Boss(score)).search();
//        TODO VBC: Next check different search algo to generate estimated graph. e.g. PC
        System.out.println("Test Estimated CPDAG Graph: " + estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag);
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

    public void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(DataSet data, Graph trueGraph, Graph estimatedCpdag) {
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // Using Adj, AH confusion matrix
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData(fisherZTest, estimatedCpdag, trueGraph, 0.05, 1.0, 0.8);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
    }

    public void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(DataSet data, Graph trueGraph, Graph estimatedCpdag) {
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(estimatedCpdag, fisherZTest, ConditioningSetType.MARKOV_BLANKET);
        // Using Local Graph (LG) confusion matrix
        // ADTest pass/fail threshold default to be 0.05. shuffleThreshold default to be 0.5
        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodesPlotData2(fisherZTest, estimatedCpdag, trueGraph, 0.05, 1.0, 0.8);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, false);
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
