///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.Kci;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TestCheckMarkov {

    public static void main(String... args) {
        new TestCheckMarkov().test1();
    }

    @Test
    public void test1() {
        double alpha = 0.05;
        int numIndep = 0;
        int total = 0;

        Graph dag = RandomGraph.randomDag(10, 0, 10, 100, 100,
                100, false);

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);

        Kci test = new Kci(data);
        test.setAlpha(alpha);
        test.setApproximate(true);
        test.setNumPermutations(1000);
        test.setScalingFactor(1.0);

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

                IndependenceResult result = null;
                try {
                    result = test.checkIndependence(x, y, new HashSet<>(cond));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

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
        Graph cpdag = null;
        try {
            cpdag = search.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        MarkovCheck markovCheck = new MarkovCheck(cpdag, test, ConditioningSetType.LOCAL_MARKOV);
        markovCheck.setFractionResample(0.7);

        try {
            System.out.println(markovCheck.getMarkovCheckRecordString());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test3() {
        Graph dag = RandomGraph.randomDag(20, 4, 40, 100, 100,
                100, false);
        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(10000, false);

        IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(data), 0.01);
        SemBicScore score = new SemBicScore(data, 1, true);

        Fcit fcit = new Fcit(test, score);
        fcit.setVerbose(true);
        fcit.setDepth(7);
        fcit.setCompleteRuleSetUsed(false);
        Graph pag;

        try {
            pag = fcit.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        MarkovCheck markovCheck = new MarkovCheck(pag, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_MAG);
        markovCheck.setFractionResample(1.0);
        markovCheck.generateResults(true, true); // Note the ordered local Markov property only returns indep case.

        try {
            System.out.println(markovCheck.getMarkovCheckRecordString());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void test4() {
        for (int run = 0; run < 1; run++) {

            Graph dag = RandomGraph.randomDag(20, 4, 40, 100, 100,
                    100, false);
            SemPm pm = new SemPm(dag);
            SemIm im = new SemIm(pm);
            DataSet data = im.simulateData(10000, false);

            IndTestFisherZ test = new IndTestFisherZ(new CovarianceMatrix(data), 0.01);
            SemBicScore score = new SemBicScore(data, 1, true);

            GraspFci alg = new GraspFci(test, score);
            alg.setVerbose(false);
            alg.setDepth(7);
            alg.setCompleteRuleSetUsed(false);
            Graph pag;

            try {
                pag = alg.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            MarkovCheck markovCheck = new MarkovCheck(pag, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_MAG);
            markovCheck.setFractionResample(1.0);
            markovCheck.generateResults(true, true); // Note the ordered local Markov property only returns indep case.

            System.out.println("Run # " + (run + 1) + " num tests indep = " + markovCheck.getNumTests(true));
        }
    }


    //    @Test
    public void testGaussianDAGPrecisionRecallForLocalOnMarkovBlanket() {
//        Graph trueGraph = RandomGraph.randomDag(100, 0, 400, 100, 100, 100, false);
        Graph trueGraph = RandomGraph.randomDag(80, 0, 80, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        DataSet data = im.simulateData(10000, false);
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingAdjAHConfusionMatrix(data, trueGraph, estimatedCpdag);
        testGaussianDAGPrecisionRecallForLocalOnMarkovBlanketUsingLGConfusionMatrix(data, trueGraph, estimatedCpdag);
        System.out.println("~~~~~~~~~~~~~Full Graph~~~~~~~~~~~~~~~");
        estimatedCpdag = GraphUtils.replaceNodes(estimatedCpdag, trueGraph.getNodes());
        double whole_ap = new AdjacencyPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ar = new AdjacencyRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahp = new ArrowheadPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_ahr = new ArrowheadRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgp = new LocalGraphPrecision().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        double whole_lgr = new LocalGraphRecall().getValue(trueGraph, estimatedCpdag, null, new Parameters());
        System.out.println("whole_ap: " + whole_ap);
        System.out.println("whole_ar: " + whole_ar);
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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


    //    @Test
    public void testGaussianDAGPrecisionRecallForLocalOnParents() {
        Graph trueGraph = RandomGraph.randomDag(10, 0, 10, 100, 100, 100, false);
        System.out.println("Test True Graph: " + trueGraph);
        System.out.println("Test True Graph size: " + trueGraph.getNodes().size());

        SemPm pm = new SemPm(trueGraph);
        // Parameters without additional setting default tobe Gaussian
        SemIm im = new SemIm(pm, new Parameters());
        DataSet data = im.simulateData(1000, false);
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

        for (Node a : accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");

        }
        for (Node a : rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");
        }
    }

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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
        for (Node a : accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");

        }
        for (Node a : rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");
        }
    }

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

        for (Node a : accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");

        }
        for (Node a : rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraph);
            System.out.println("=====================");
        }
    }

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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
        for (Node a : accepts) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");

        }
        for (Node a : rejects) {
            System.out.println("=====================");
            markovCheck.getPrecisionAndRecallOnMarkovBlanketGraph(a, estimatedCpdag, trueGraphCPDAG);
            System.out.println("=====================");
        }
    }


    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

    //    @Test
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
        edu.cmu.tetrad.search.score.SemBicScore score = new SemBicScore(data, true);
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

