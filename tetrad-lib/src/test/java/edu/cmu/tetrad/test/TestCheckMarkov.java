package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.Kci;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
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
        System.out.println("Alpha = " + alpha + " % Dependent = " +
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
    public void testPrecissionRecallForLocal() {
        Graph dag = RandomGraph.randomDag(30, 0, 10, 100, 100,
                100, false); // truegraph
        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);
        SemBicScore score = new SemBicScore(data, true);
        PermutationSearch search = new PermutationSearch(new Boss(score));
        Graph cpdag = search.search(); // estimatedgraph
        IndependenceTest fisherZTest = new IndTestFisherZ(data, 0.05);
        MarkovCheck markovCheck = new MarkovCheck(cpdag, fisherZTest, ConditioningSetType.LOCAL_MARKOV);

        List<List<Node>> accepts_rejects = markovCheck.getAndersonDarlingTestAcceptsRejectsNodesForAllNodes(fisherZTest, cpdag, 0.05);
        List<Node> accepts = accepts_rejects.get(0);
        List<Node> rejects = accepts_rejects.get(1);
        System.out.println("Accepts size: " + accepts.size());
        System.out.println("Rejects size: " + rejects.size());
        System.out.println("Estimated Graph size: " + cpdag.getNodes().size());
        System.out.println("True Graph size: " + dag.getNodes().size());




        List<Double> acceptsPrecision = new ArrayList<>();
        List<Double> acceptsRecall = new ArrayList<>();
        for(Node a: accepts) {
            double precision = markovCheck.getPrecisionOrRecallOnMarkovBlanketGraph(a, cpdag, dag, true);
            double recall = markovCheck.getPrecisionOrRecallOnMarkovBlanketGraph(a, cpdag, dag, false);
            acceptsPrecision.add(precision);
            acceptsRecall.add(recall);
        }
        System.out.println("Accepts Precissions: " + acceptsPrecision);
        System.out.println("Accepts Recall: " + acceptsRecall);


    }
}
