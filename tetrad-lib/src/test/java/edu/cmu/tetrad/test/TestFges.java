///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.CPC;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TextTable;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestFges {


    private final PrintStream out = System.out;
//    private OutputStream out =

    @Test
    public void explore1() {
        RandomUtil.getInstance().setSeed(1450184147770L);

        final int numVars = 10;
        final double edgesPerNode = 2.0;
        final int numCases = 1000;
        final double penaltyDiscount = 2.0;

        final int numEdges = (int) (numVars * edgesPerNode);

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);
//        printDegreeDistribution(dag, System.out);

        int[] causalOrdering = new int[vars.size()];

        for (int i = 0; i < vars.size(); i++) {
            causalOrdering[i] = i;
        }

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(numCases, false);

        System.out.println("data done");

        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(penaltyDiscount);

        Fges alg = new Fges(score);
        alg.setVerbose(true);
        alg.setOut(this.out);
        alg.setFaithfulnessAssumed(true);
        Graph estCPDAG = alg.search();

        Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        estCPDAG = GraphUtils.replaceNodes(estCPDAG, vars);

        System.out.println("true = " + trueCPDAG + " est = " + estCPDAG);

        double ap = new AdjacencyPrecision().getValue(trueCPDAG, estCPDAG, data);
        double ar = new AdjacencyRecall().getValue(trueCPDAG, estCPDAG, data);

        System.out.println("ap = " + ap + " ar = " + ar);



//        int[][] counts = SearchGraphUtils.graphComparison(estCPDAG, trueCPDAG, null);
//
//        int[][] expectedCounts = {
//                {2, 0, 0, 0, 0, 0},
//                {0, 0, 0, 0, 0, 0},
//                {0, 0, 0, 0, 0, 0},
//                {0, 0, 0, 0, 0, 0},
//                {0, 0, 0, 8, 0, 0},
//                {0, 0, 0, 0, 0, 0},
//                {0, 0, 0, 0, 0, 0},
//                {0, 0, 0, 0, 0, 0},
//        };
//
//        for (int i = 0; i < counts.length; i++) {
//            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
//        }

    }

    @Test
    public void explore2() {
        RandomUtil.getInstance().setSeed(1457220623122L);

        final int numVars = 10;
        final double edgeFactor = 1.0;
        final int numCases = 1000;
        final double structurePrior = 1;
        final double samplePrior = 1;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false, true);
//        printDegreeDistribution(dag, out);

        BayesPm pm = new BayesPm(dag, 2, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(numCases, false);

//        out.println("Finishing simulation");

        BDeScore score = new BDeScore(data);
        score.setSamplePrior(samplePrior);
        score.setStructurePrior(structurePrior);

        Fges ges = new Fges(score);
        ges.setVerbose(false);
        ges.setFaithfulnessAssumed(false);

        Graph estCPDAG = ges.search();

        Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        int[][] counts = SearchGraphUtils.graphComparison(trueCPDAG, estCPDAG, null);

        int[][] expectedCounts = {
                {2, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {2, 0, 0, 13, 0, 3},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

    }


    @Test
    public void testExplore3() {
        Graph graph = GraphUtils.convert("A-->B,A-->C,B-->D,C-->D");
        Fges fges = new Fges(new GraphScore(graph));
        Graph CPDAG = fges.search();
        assertEquals(SearchGraphUtils.cpdagForDag(graph), CPDAG);
    }

    @Test
    public void testExplore4() {
        Graph graph = GraphUtils.convert("A-->B,A-->C,A-->D,B-->E,C-->E,D-->E");
        Fges fges = new Fges(new GraphScore(graph));
        Graph CPDAG = fges.search();
        assertEquals(SearchGraphUtils.cpdagForDag(graph), CPDAG);
    }

    @Test
    public void testExplore5() {
        Graph graph = GraphUtils.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
        Fges fges = new Fges(new GraphScore(graph));
        fges.setFaithfulnessAssumed(false);
        Graph CPDAG = fges.search();
        assertEquals(SearchGraphUtils.cpdagForDag(graph), CPDAG);
    }


    @Test
    public void testFromGraphSimpleFges() {

        // This may fail if faithfulness is assumed but should pass if not.

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        Graph g = new EdgeListGraph();
        g.addNode(x1);
        g.addNode(x2);
        g.addNode(x3);
        g.addNode(x4);

        g.addDirectedEdge(x1, x2);
        g.addDirectedEdge(x1, x3);
        g.addDirectedEdge(x4, x2);
        g.addDirectedEdge(x4, x3);

        Graph CPDAG1 = new Pc(new IndTestDSep(g)).search();
        Fges fges = new Fges(new GraphScore(g));
        fges.setFaithfulnessAssumed(true);
        Graph CPDAG2 = fges.search();

        assertEquals(CPDAG1, CPDAG2);
    }

    @Test
    public void testFromGraphSimpleFgesMb() {

        // This may fail if faithfulness is assumed but should pass if not.

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        Graph dag = new EdgeListGraph();
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);

        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x1, x3);
        dag.addDirectedEdge(x4, x2);
        dag.addDirectedEdge(x4, x3);

        GraphScore fgesScore = new GraphScore(dag);

        Fges fges = new Fges(fgesScore);
        Graph CPDAG1 = fges.search();

        Set<Node> mb = new HashSet<>();
        mb.add(x1);

        mb.addAll(CPDAG1.getAdjacentNodes(x1));

        for (Node child : CPDAG1.getChildren(x1)) {
            mb.addAll(CPDAG1.getParents(child));
        }

        Graph mb1 = CPDAG1.subgraph(new ArrayList<>(mb));

        FgesMb fgesMb = new FgesMb(fgesScore);
        Graph mb2 = fgesMb.search(x1);

        assertEquals(mb1, mb2);
    }

    //    @Test
    public void testFgesMbFromGraph() {
        RandomUtil.getInstance().setSeed(1450184147770L);

        final int numNodes = 20;
        final int numIterations = 2;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            GraphScore fgesScore = new GraphScore(dag);

            Fges fges = new Fges(fgesScore);
            Graph CPDAG1 = fges.search();

            Node x1 = fgesScore.getVariable("X1");

            Set<Node> mb = new HashSet<>();
            mb.add(x1);

            mb.addAll(CPDAG1.getAdjacentNodes(x1));

            for (Node child : CPDAG1.getChildren(x1)) {
                mb.addAll(CPDAG1.getParents(child));
            }

            Graph mb1 = CPDAG1.subgraph(new ArrayList<>(mb));

            FgesMb fgesMb = new FgesMb(fgesScore);
            Graph mb2 = fgesMb.search(x1);

            assertEquals(mb1, mb2);
        }
    }


    private void printDegreeDistribution(Graph dag, PrintStream out) {
        int max = 0;

        for (Node node : dag.getNodes()) {
            int degree = dag.getAdjacentNodes(node).size();
            if (degree > max) max = degree;
        }

        int[] counts = new int[max + 1];
        Map<Integer, List<Node>> names = new HashMap<>();

        for (int i = 0; i <= max; i++) {
            names.put(i, new ArrayList<>());
        }

        for (Node node : dag.getNodes()) {
            int degree = dag.getAdjacentNodes(node).size();
            counts[degree]++;
            names.get(degree).add(node);
        }

        for (int k = 0; k < counts.length; k++) {
            if (counts[k] == 0) continue;

            out.print(k + " " + counts[k]);

            for (Node node : names.get(k)) {
                out.print(" " + node.getName());
            }

            out.println();
        }
    }

    @Test
    public void clarkTest() {
        RandomGraph randomGraph = new RandomForward();

        Simulation simulation = new LinearFisherModel(randomGraph);

        Parameters parameters = new Parameters();

        parameters.set(Params.NUM_MEASURES, 10);
        parameters.set(Params.NUM_LATENTS, 0);
        parameters.set(Params.COEF_LOW, 0.2);
        parameters.set(Params.COEF_HIGH, 0.8);
        parameters.set(Params.AVG_DEGREE, 2);
        parameters.set(Params.MAX_DEGREE, 100);
        parameters.set(Params.MAX_INDEGREE, 100);
        parameters.set(Params.MAX_OUTDEGREE, 100);
        parameters.set(Params.CONNECTED, false);

        parameters.set(Params.NUM_RUNS, 1);
        parameters.set(Params.DIFFERENT_GRAPHS, false);
        parameters.set(Params.SAMPLE_SIZE, 1000);

        parameters.set(Params.FAITHFULNESS_ASSUMED, false);
        parameters.set(Params.MAX_DEGREE, -1);
        parameters.set(Params.VERBOSE, false);

        parameters.set(Params.ALPHA, 0.01);

        simulation.createData(parameters, false);

        DataSet dataSet = (DataSet) simulation.getDataModel(0);
        Graph trueGraph = simulation.getTrueGraph(0);

//        trueGraph = SearchGraphUtils.CPDAGForDag(trueGraph);

        ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
        IndependenceWrapper test = new FisherZ();

        Algorithm fges = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(score);

        Graph fgesGraph = fges.search(dataSet, parameters);

        clarkTestForAlpha(0.05, parameters, dataSet, trueGraph, fgesGraph, test);
        clarkTestForAlpha(0.01, parameters, dataSet, trueGraph, fgesGraph, test);

    }

    private void clarkTestForAlpha(double alpha, Parameters parameters, DataSet dataSet, Graph trueGraph,
                                   Graph CPDAG, IndependenceWrapper test) {
        parameters.set(Params.ALPHA, alpha);

        List<Node> nodes = dataSet.getVariables();

        trueGraph = GraphUtils.replaceNodes(trueGraph, nodes);
        CPDAG = GraphUtils.replaceNodes(CPDAG, nodes);

        IndependenceTest _test = test.getTest(dataSet, parameters);

        System.out.println(parameters);

        final int numSamples = 1000;

        System.out.println("\nNumber of random pairs of variables selected = " + numSamples);

        int tp1 = 0;
        int fp1 = 0;
        int fn1 = 0;

        int tp2 = 0;
        int fp2 = 0;
        int fn2 = 0;

        for (int i = 0; i < numSamples; i++) {
            Collections.shuffle(nodes);
            Node x = nodes.get(0);
            Node y = nodes.get(1);

            boolean trueAncestral = ancestral(x, y, trueGraph);
            boolean estAncestral = ancestral(x, y, CPDAG);

            if (trueAncestral && estAncestral) {
                tp1++;
            }

            if (!trueAncestral && estAncestral) {
                fn1++;
            }

            if (trueAncestral && !estAncestral) {
                fp1++;
            }

            boolean dependent = !_test.checkIndependence(x, y).independent();

            if (trueAncestral && dependent) {
                tp2++;
            }

            if (!trueAncestral && dependent) {
                fn2++;
            }

            if (trueAncestral && !dependent) {
                fp2++;
            }
        }

        double prec1 = tp1 / (double) (tp1 + fp1);
        double rec1 = tp1 / (double) (tp1 + fn1);

        double prec2 = tp2 / (double) (tp2 + fp2);
        double rec2 = tp2 / (double) (tp2 + fn2);

        System.out.println("Experiment 1: Comparing ancestral connection in true DAG versus estimated CPDAG");

        System.out.println("Precision = " + prec1 + " recall = " + rec1);

        System.out.println("Experiment 1: Comparing ancestral connection in true DAG to judgement of independence by Fisher Z");

        System.out.println("Precision = " + prec2 + " recall = " + rec2);
    }

    private boolean ancestral(Node x, Node y, Graph graph) {
        return graph.paths().isAncestorOf(x, y) || graph.paths().isAncestorOf(y, x);
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch2() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch3() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "A-->D,A---B,B-->D,C-->D,D-->E");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch4() {
        Knowledge knowledge = new Knowledge();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A---D,B---A,B-->C,C---A,C-->D",
                knowledge);
    }

    @Test
    public void testSearch5() {
        Knowledge knowledge = new Knowledge();
        knowledge.setTier(1, Collections.singletonList("A"));
        knowledge.setTier(2, Collections.singletonList("B"));

        checkWithKnowledge("A-->B", "A-->B", knowledge);
    }

    @Test
    public void testCites() {
        final String citesString = "164\n" +
                "ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS\n" +
                "1.0\n" +
                ".62\t1.0\n" +
                ".25\t.09\t1.0\n" +
                ".16\t.28\t.07\t1.0\n" +
                "-.10\t.00\t.03\t.10\t1.0\n" +
                ".29\t.25\t.34\t.37\t.13\t1.0\n" +
                ".18\t.15\t.19\t.41\t.43\t.55\t1.0";

        char[] citesChars = citesString.toCharArray();
        ICovarianceMatrix cov = SimpleDataLoader.loadCovarianceMatrix(citesChars, "//", DelimiterType.WHITESPACE, '\"', "*");

        Knowledge knowledge = new Knowledge();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        SemBicScore score = new SemBicScore(cov);
        Fges fges = new Fges(score);
        fges.setKnowledge(knowledge);

        fges.setVerbose(true);

        Graph CPDAG = fges.search();

        System.out.println(CPDAG);

        final String trueString = "Graph Nodes:\n" +
                "Graph Nodes:\n" +
                "Graph Nodes:;ABILITY;GPQ;PREPROD;QFJ;SEX;CITES;PUBS\n" +
                "\n" +
                "Graph Edges:\n" +
                "1. ABILITY --> GPQ\n" +
                "2. ABILITY --> PREPROD\n" +
                "3. ABILITY --> PUBS\n" +
                "4. GPQ --> QFJ\n" +
                "5. PREPROD --> CITES\n" +
                "6. PUBS --> CITES\n" +
                "7. QFJ --> CITES\n" +
                "8. QFJ --> PUBS\n" +
                "9. SEX --> PUBS";

        Graph trueGraph = null;


        try {
            trueGraph = GraphPersistence.readerToGraphTxt(trueString);
            CPDAG = GraphUtils.replaceNodes(CPDAG, trueGraph.getNodes());
            assertEquals(trueGraph, CPDAG);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphUtils.convert(inputGraph);

        // Set up search.
        Fges fges = new Fges(new GraphScore(graph));

        // Run search
        Graph resultGraph = fges.search();

        // Build comparison graph.
        Graph trueGraph = GraphUtils.convert(outputGraph);

        // PrintUtil out problem and graphs.

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(String inputGraph, String answerGraph,
                                    Knowledge knowledge) {
        // Set up graph and node objects.
        Graph input = GraphUtils.convert(inputGraph);

        // Set up search.
        Fges fges = new Fges(new GraphScore(input));

        // Set up search.
        fges.setKnowledge(knowledge);

        // Run search
        Graph result = fges.search();

        // Build comparison graph.
        Graph answer = GraphUtils.convert(answerGraph);
//        Graph answer = new PC(new IndTestDSep(input)).search();

//        System.out.println("Input = " + input);
//        System.out.println("Knowledge = " + knowledge);
//        System.out.println("Answer = " + answer);
//        System.out.println("Result graph = " + result);

        // Do test.
        assertEquals(answer, result);
    }

    @Test
    public void testFromGraph() {
        final int numNodes = 10;
        final int aveDegree = 4;
        final int numIterations = 1;

        for (int i = 0; i < numIterations; i++) {
            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, aveDegree * numNodes / 2, 10, 10, 10, false);
            Fges fges = new Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setVerbose(true);
            fges.setParallelized(true);
            Graph CPDAG1 = fges.search();
            Graph CPDAG2 = SearchGraphUtils.cpdagFromDag(dag);
            assertEquals(CPDAG2, CPDAG1);
        }
    }

    //    @Test
    public void testFromData() {
        final int numIterations = 1;

        Parameters params = new Parameters();

        int[] nodeOptions = {5, 10, 20, 30, 40, 50, 75, 100};
        int[] avgDegreeOptions = {2, 4, 6};
        int[] sampleSizeOptions = {100, 500, 1000, 10000, 100000};

        int numRowsInTable = nodeOptions.length * avgDegreeOptions.length * sampleSizeOptions.length;

        TextTable table = new TextTable(numRowsInTable + 1, 5);

        table.setToken(0, 0, "# Nodes");
        table.setToken(0, 1, "Avg Degree");
        table.setToken(0, 2, "# Samples");
        table.setToken(0, 3, "True # edges");
        table.setToken(0, 4, "Est # Edges");

        int count = 0;

        for (int numNodes : nodeOptions) {
            for (int avgDegree : avgDegreeOptions) {
                for (int sampleSize : sampleSizeOptions) {
                    for (int q = 0; q < 1; q++) {
                        for (int i = 0; i < numIterations; i++) {
                            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0,
                                    (avgDegree * numNodes) / 2, 100, 100, 100, false);
                            SemPm pm = new SemPm(dag);
                            SemIm im = new SemIm(pm, params);
                            DataSet data = im.simulateData(sampleSize, false);
                            SemBicScore score = new SemBicScore(data);
                            score.setPenaltyDiscount(.5);
                            Fges fges = new Fges(score);
                            fges.setFaithfulnessAssumed(false);
                            fges.setVerbose(false);
                            Graph CPDAG1 = fges.search();
                            System.out.println("num nodes = " + numNodes + " avg degree = " + avgDegree
                                    + " sample size = " + sampleSize
                                    + " true # edges = " + dag.getNumEdges()
                                    + " est # edges = " + CPDAG1.getNumEdges());

                            count++;
                            table.setToken(count, 0, "" + numNodes);
                            table.setToken(count, 1, "" + avgDegree);
                            table.setToken(count, 2, "" + sampleSize);
                            table.setToken(count, 3, "" + dag.getNumEdges());
                            table.setToken(count, 4, "" + CPDAG1.getNumEdges());

                        }
                    }
                }
            }
        }

        System.out.println("\n==========================\n");
        System.out.println(table);

    }


    @Test
    public void testFromGraphWithForbiddenKnowledge() {
        final int numNodes = 10;
        final int numIterations = 1;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            Graph knowledgeGraph = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            knowledgeGraph = GraphUtils.replaceNodes(knowledgeGraph, dag.getNodes());

            Knowledge knowledge = forbiddenKnowledge(knowledgeGraph);

            Fges fges = new Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setKnowledge(knowledge);
            Graph CPDAG1 = fges.search();

            for (Edge edge : knowledgeGraph.getEdges()) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (CPDAG1.isParentOf(x, y)) {
                    System.out.println("Knowledge violated: " + edge + " x = " + x + " y = " + y);
                }

                assertFalse(CPDAG1.isParentOf(x, y));
            }

            // This sometimes fails if the following cycle check is uncommented.
//            assertFalse(CPDAG1.existsDirectedCycle());
        }
    }

    @Test
    public void testFromGraphWithRequiredKnowledge() {
        final int numNodes = 20;
        final int numIterations = 20;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            Graph knowledgeGraph = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            knowledgeGraph = GraphUtils.replaceNodes(knowledgeGraph, dag.getNodes());

            Knowledge knowledge = requiredKnowledge(knowledgeGraph);

            Fges fges = new Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setKnowledge(knowledge);
            Graph CPDAG1 = fges.search();

            for (Edge edge : knowledgeGraph.getEdges()) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (!CPDAG1.isParentOf(x, y)) {
                    System.out.println("Knowledge violated: " + edge + " x = " + x + " y = " + y);
                }

                assertTrue(CPDAG1.isParentOf(x, y));
            }
        }
    }


    private Knowledge forbiddenKnowledge(Graph graph) {
        Knowledge knowledge = new Knowledge(graph.getNodeNames());

        for (Edge edge : graph.getEdges()) {
            Node n1 = Edges.getDirectedEdgeTail(edge);
            Node n2 = Edges.getDirectedEdgeHead(edge);

            if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                continue;
            }

            knowledge.setForbidden(n1.getName(), n2.getName());
        }

        return knowledge;
    }

    private Knowledge requiredKnowledge(Graph graph) {

        Knowledge knowledge = new Knowledge(graph.getNodeNames());

        for (Edge edge : graph.getEdges()) {
            Node n1 = Edges.getDirectedEdgeTail(edge);
            Node n2 = Edges.getDirectedEdgeHead(edge);

            if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                continue;
            }

            knowledge.setRequired(n1.getName(), n2.getName());
        }

        return knowledge;
    }


    private Graph getSubgraph(Graph graph, boolean discrete1, boolean discrete2, DataSet dataSet) {
        Graph newGraph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            Node node1 = dataSet.getVariable(edge.getNode1().getName());
            Node node2 = dataSet.getVariable(edge.getNode2().getName());

            if (discrete1 && node1 instanceof DiscreteVariable) {
                if (discrete2 && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                }
            } else if (!discrete1 && node1 instanceof ContinuousVariable) {
                if (!discrete2 && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            } else if ((discrete1 && !discrete2) || (!discrete1 && discrete2)) {
                if (node1 instanceof ContinuousVariable && node2 instanceof DiscreteVariable) {
                    newGraph.addEdge(edge);
                } else if (node1 instanceof DiscreteVariable && node2 instanceof ContinuousVariable) {
                    newGraph.addEdge(edge);
                }
            }
        }

        return newGraph;
    }

    private Graph searchSemFges(DataSet Dk) {
        Dk = DataUtils.convertNumericalDiscreteToContinuous(Dk);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(Dk));
        score.setPenaltyDiscount(2.0);
        Fges fges = new Fges(score);
        return fges.search();
    }

    private Graph searchBdeuFges(DataSet Dk, int k) {
        Discretizer discretizer = new Discretizer(Dk);
        List<Node> nodes = Dk.getVariables();

        for (Node node : nodes) {
            if (node instanceof ContinuousVariable) {
                discretizer.equalIntervals(node, k);
            }
        }

        Dk = discretizer.discretize();

        BDeuScore score = new BDeuScore(Dk);
        score.setSamplePrior(1.0);
        score.setStructurePrior(1.0);
        Fges fges = new Fges(score);
        return fges.search();
    }

    private Graph searchMixedFges(DataSet dk) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(dk, 2.0, 0.0, true);
        score.setPenaltyDiscount(2.0);
        Fges fges = new Fges(score);
        return fges.search();
    }

    public Graph searchMGMFges(DataSet ds, double penalty) {
        MGM m = new MGM(ds, new double[]{0.1, 0.1, 0.1});
        //m.setVerbose(this.verbose);
        Graph gm = m.search();
        DataSet dataSet = MixedUtils.makeContinuousData(ds);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
        score.setPenaltyDiscount(penalty);
        Fges fg = new Fges(score);
        fg.setBoundGraph(gm);
        fg.setVerbose(false);
        return fg.search();
    }

    public DataSet getMixedDataAjStyle(Graph g, int k, int samps) {

        HashMap<String, Integer> nd = new HashMap<>();

        List<Node> nodes = g.getNodes();

        Collections.shuffle(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            if (i < nodes.size() / 2) {
                nd.put(nodes.get(i).getName(), k);
            } else {
                nd.put(nodes.get(i).getName(), 0);
            }
        }

        g = MixedUtils.makeMixedGraph(g, nd);


        GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(g, "Split(-1.5,-.5,.5,1.5)");
//        System.out.println(pm);

        GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);
//        System.out.println(im);

        DataSet ds = im.simulateDataFisher(samps);
        return MixedUtils.makeMixedData(ds, nd);
    }

    //    @Test
    public void testBestAlgorithms() {
        String[] algorithms = {"SemFGES", "BDeuFGES", "MixedFGES", "PC", "PCS", "CPC", "MGMFges", "MGMPcs"};
        String[] statLabels = {"AP", "AR", "OP", "OR", "SUM", "McAdj", "McOr", "F1Adj", "F1Or", "E"};

        final int numMeasures = 10;
        final int numEdges = 10;

        final int numRuns = 50;
        final int maxCategories = 5;
        final int sampleSize = 1000;
        final double penaltyDiscount = 4.0;
        final double ofInterestCutoff = 0.05;

        double[][][][] allAllRet = new double[maxCategories][][][];
        int latentIndex = -1;

        for (int numCategories = 2; numCategories <= maxCategories; numCategories++) {
            latentIndex++;

            System.out.println();

            System.out.println("num categories = " + numCategories);
            System.out.println("num measures = " + numMeasures);
            System.out.println("num edges = " + numEdges);
            System.out.println("sample size = " + sampleSize);
            System.out.println("penaltyDiscount = " + penaltyDiscount);
            System.out.println("num runs = " + numRuns);

            double[][][] allRet = new double[algorithms.length][][];

            for (int t = 0; t < algorithms.length; t++) {
                allRet[t] = printStats(algorithms, t,
                        numCategories);
            }

            allAllRet[latentIndex] = allRet;
        }

        System.out.println();
        System.out.println("=======");
        System.out.println();
        System.out.println("Algorithms with max = " + ofInterestCutoff + "*(max - min) < stat <= max.");
        System.out.println();
        System.out.println("AP = Average Adj Precision; AR = Average Adj Recall");
        System.out.println("OP = Average orientation (arrow) Precision; OR = Average orientation (arrow) recall");
        System.out.println("McAdj = Mathew's correlation for adjacencies; McOr = Mathew's correlatin for orientatons");
        System.out.println("F1Adj = F1 score for adjacencies; F1Or = F1 score for orientations");
        System.out.println("E = Averaged Elapsed Time (ms), AP/P");
        System.out.println();
        System.out.println("num categories = 2 to " + maxCategories);
        System.out.println("sample size = " + sampleSize);
        System.out.println("penaltyDiscount = " + penaltyDiscount);
        System.out.println("num runs = " + numRuns);
        System.out.println();
        System.out.println("num measures = " + numMeasures);
        System.out.println("num edges = " + numEdges);

        printBestStats(allAllRet, algorithms, statLabels);
    }

    private double[][] printStats(String[] algorithms, int t,
                                  int numCategories) {
        NumberFormat nf = new DecimalFormat("0.00");

        double[] sumAdjPrecision = new double[4];
        double[] sumAdjRecall = new double[4];
        double[] sumArrowPrecision = new double[4];
        double[] sumArrowRecall = new double[4];
        double[] sumSum = new double[4];
        double[] sumMcAdj = new double[4];
        double[] sumMcOr = new double[4];
        double[] sumF1Adj = new double[4];
        double[] sumF1Or = new double[4];
        double totalElapsed = 0.0;

        int[] countAP = new int[4];
        int[] countAR = new int[4];
        int[] countOP = new int[4];
        int[] countOR = new int[4];
        int[] countSum = new int[4];
        int[] countMcAdj = new int[4];
        int[] countMcOr = new int[4];
        int[] countF1Adj = new int[4];
        int[] countF1Or = new int[4];

        for (int i = 0; i < 50; i++) {
            List<Node> nodes = new ArrayList<>();

            for (int r = 0; r < 30; r++) {
                String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
            }

            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(nodes, 0, 60,
                    10, 10, 10, false);
            DataSet data = getMixedDataAjStyle(dag, numCategories, 1000);

            Graph out;
            final double penalty = 2;

            long start = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

            switch (t) {
                case 0:
                    out = searchSemFges(data);
                    break;
                case 1:
                    out = searchBdeuFges(data, numCategories);
                    break;
                case 2:
                    out = searchMixedFges(data);
                    break;
                case 3:
                    out = searchMGMFges(data, penalty);
                    break;
                default:
                    throw new IllegalStateException();
            }

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            Graph[] est = new Graph[4];

            est[0] = out;
            est[1] = getSubgraph(out, true, true, data);
            est[2] = getSubgraph(out, true, false, data);
            est[3] = getSubgraph(out, false, false, data);

            Graph[] truth = new Graph[4];

            truth[0] = dag;
            truth[1] = getSubgraph(dag, true, true, data);
            truth[2] = getSubgraph(dag, true, false, data);
            truth[3] = getSubgraph(dag, false, false, data);

            long stop = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

            long elapsed = stop - start;
            totalElapsed += elapsed;

            for (int u = 0; u < 4; u++) {
                int adjTp = 0;
                int adjFp = 0;
                int adjTn;
                int adjFn = 0;
                int arrowsTp = 0;
                int arrowsFp = 0;
                int arrowsTn = 0;
                int arrowsFn = 0;

                for (Edge edge : est[u].getEdges()) {
                    if (truth[u].isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                        adjTp++;
                    } else {
                        adjFp++;
                    }

                    if (edge.isDirected()) {
                        Edge _edge = truth[u].getEdge(edge.getNode1(), edge.getNode2());

                        if (edge != null && edge.equals(_edge)) {
                            arrowsTp++;
                        } else {
                            arrowsFp++;
                        }
                    }
                }

                List<Node> nodes1 = truth[u].getNodes();

                for (int w = 0; w < nodes1.size(); w++) {
                    for (int s = w + 1; w < nodes1.size(); w++) {
                        Node W = nodes1.get(w);
                        Node S = nodes1.get(s);

                        if (truth[u].isAdjacentTo(W, S)) {
                            if (!est[u].isAdjacentTo(W, S)) {
                                adjFn++;
                            }

                            Edge e1 = truth[u].getEdge(W, S);
                            Edge e2 = est[u].getEdge(W, S);

                            if (!(e2 != null && e2.equals(e1))) {
                                arrowsFn++;
                            }
                        }

                        Edge e1 = truth[u].getEdge(W, S);
                        Edge e2 = est[u].getEdge(W, S);

                        if (!(e1 != null && e2 == null) || (e1 != null && e2 != null && !e1.equals(e2))) {
                            arrowsFn++;
                        }
                    }
                }

                int allEdges = truth[u].getNumNodes() * (truth[u].getNumNodes() - 1);

                adjTn = allEdges / 2 - (adjFn + adjFp + adjTp);
                arrowsTn = allEdges - (arrowsFn + arrowsFp + arrowsTp);

                double adjPrecision = adjTp / (double) (adjTp + adjFp);
                double adjRecall = adjTp / (double) (adjTp + adjFn);

                double arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
                double arrowRecall = arrowsTp / (double) (arrowsTp + arrowsFn);

                if (!Double.isNaN(adjPrecision)) {
                    sumAdjPrecision[u] += adjPrecision;
                    countAP[u]++;
                }

                if (!Double.isNaN(adjRecall)) {
                    sumAdjRecall[u] += adjRecall;
                    countAR[u]++;
                }

                if (!Double.isNaN(arrowPrecision)) {
                    sumArrowPrecision[u] += arrowPrecision;
                    countOP[u]++;
                }

                if (!Double.isNaN(arrowRecall)) {
                    sumArrowRecall[u] += arrowRecall;
                    countOR[u]++;
                }

                double sum = adjPrecision + adjRecall + arrowPrecision + arrowRecall;
                double mcAdj = (adjTp * adjTn - adjFp * adjFn) /
                        Math.sqrt((adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn));
                double mcOr = (arrowsTp * arrowsTn - arrowsFp * arrowsFn) /
                        Math.sqrt((arrowsTp + arrowsFp) * (arrowsTp + arrowsFn) *
                                (arrowsTn + arrowsFp) * (arrowsTn + arrowsFn));
                double f1Adj = 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
                double f1Arrows = 2 * (arrowPrecision * arrowRecall) / (arrowPrecision + arrowRecall);

                if (f1Arrows < 0) {
                    System.out.println();
                }

                if (!Double.isNaN(sum)) {
                    sumSum[u] += sum;
                    countSum[u]++;
                }

                if (!Double.isNaN(mcAdj)) {
                    sumMcAdj[u] += mcAdj;
                    countMcAdj[u]++;
                }

                if (!Double.isNaN(mcOr)) {
                    sumMcOr[u] += mcOr;
                    countMcOr[u]++;
                }

                if (!Double.isNaN(f1Adj)) {
                    sumF1Adj[u] += f1Adj;
                    countF1Adj[u]++;
                }

                if (!Double.isNaN(f1Arrows)) {
                    sumF1Or[u] += f1Arrows;
                    countF1Or[u]++;
                }
            }
        }

        double[] avgAdjPrecision = new double[4];
        double[] avgAdjRecall = new double[4];
        double[] avgArrowPrecision = new double[4];
        double[] avgArrowRecall = new double[4];
        double[] avgSum = new double[4];
        double[] avgMcAdj = new double[4];
        double[] avgMcOr = new double[4];
        double[] avgF1Adj = new double[4];
        double[] avgF1Or = new double[4];
        double[] avgElapsed = new double[4];

        for (int u = 0; u < 4; u++) {
            avgAdjPrecision[u] = sumAdjPrecision[u] / (double) countAP[u];
            avgAdjRecall[u] = sumAdjRecall[u] / (double) countAR[u];
            avgArrowPrecision[u] = sumArrowPrecision[u] / (double) countOP[u];
            avgArrowRecall[u] = sumArrowRecall[u] / (double) countOR[u];
            avgSum[u] = sumSum[u] / (double) countSum[u];
            avgMcAdj[u] = sumMcAdj[u] / (double) countMcAdj[u];
            avgMcOr[u] = sumMcOr[u] / (double) countMcOr[u];
            avgF1Adj[u] = sumF1Adj[u] / (double) countF1Adj[u];
            avgF1Or[u] = sumF1Or[u] / (double) countF1Or[u];
            avgElapsed[u] = -totalElapsed / (double) 50;
        }

        double[][] ret = {
                avgAdjPrecision,
                avgAdjRecall,
                avgArrowPrecision,
                avgArrowRecall,
                avgSum,
                avgMcAdj,
                avgMcOr,
                avgF1Adj,
                avgF1Or,
                avgElapsed
        };

        System.out.println();

        for (int u = 0; u < 4; u++) {
            String header = getHeader(u);

            System.out.println("\n" + header + "\n");

            System.out.println(algorithms[t] + " adj precision " + nf.format(avgAdjPrecision[u]));
            System.out.println(algorithms[t] + " adj recall " + nf.format(avgAdjRecall[u]));
            System.out.println(algorithms[t] + " arrow precision " + nf.format(avgArrowPrecision[u]));
            System.out.println(algorithms[t] + " arrow recall " + nf.format(avgArrowRecall[u]));
            System.out.println(algorithms[t] + " sum " + nf.format(avgSum[u]));
            System.out.println(algorithms[t] + " McAdj " + nf.format(avgMcAdj[u]));
            System.out.println(algorithms[t] + " McOr " + nf.format(avgMcOr[u]));
            System.out.println(algorithms[t] + " F1adj " + nf.format(avgF1Adj[u]));
            System.out.println(algorithms[t] + " F1Or " + nf.format(avgF1Or[u]));
            System.out.println(algorithms[t] + " avg elapsed " + nf.format(avgElapsed[u]));
        }


        return ret;
    }

    private String getHeader(int u) {
        String header;

        switch (u) {
            case 0:
                header = "All edges";
                break;
            case 1:
                header = "Discrete-discrete";
                break;
            case 2:
                header = "Discrete-continuous";
                break;
            case 3:
                header = "Continuous-continuous";
                break;
            default:
                throw new IllegalStateException();
        }
        return header;
    }

    private void printBestStats(double[][][][] allAllRet, String[] algorithms, String[] statLabels) {
        TextTable table = new TextTable(allAllRet.length + 1, allAllRet[0][0].length + 1);


        class Pair {
            private final String algorithm;
            private final double stat;

            public Pair(String algorithm, double stat) {
                this.algorithm = algorithm;
                this.stat = stat;
            }

            public String getAlgorithm() {
                return this.algorithm;
            }

            public double getStat() {
                return this.stat;
            }
        }


        System.out.println();
        System.out.println("And the winners are... !");

        for (int u = 0; u < 4; u++) {
            for (int numCategories = 2; numCategories <= 5; numCategories++) {

                table.setToken(numCategories - 1, 0, numCategories + "");

                for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {
//                double maxStat = Double.NaN;
                    String maxAlg = "-";

                    List<Pair> algStats = new ArrayList<>();

                    for (int t = 0; t < algorithms.length; t++) {
                        double stat = allAllRet[numCategories - 2][t][statIndex][u];
                        if (!Double.isNaN(stat)) {
                            algStats.add(new Pair(algorithms[t], stat));
                        }
                    }

                    if (algStats.isEmpty()) {
                        maxAlg = "-";
                    } else {
                        Collections.sort(algStats, new Comparator<Pair>() {

                            @Override
                            public int compare(Pair o1, Pair o2) {
                                return -Double.compare(o1.getStat(), o2.getStat());
                            }
                        });

                        double maxStat = algStats.get(0).getStat();
                        maxAlg = algStats.get(0).getAlgorithm();

                        double minStat = algStats.get(algStats.size() - 1).getStat();

                        double diff = maxStat - minStat;
                        double ofInterest = maxStat - 0.05 * (diff);

                        for (int i = 1; i < algStats.size(); i++) {
                            if (algStats.get(i).getStat() >= ofInterest) {
                                maxAlg += "," + algStats.get(i).getAlgorithm();
                            }
                        }
                    }

                    table.setToken(numCategories - 1, statIndex + 1, maxAlg);
                }
            }

            for (int j = 0; j < statLabels.length; j++) {
                table.setToken(0, j + 1, statLabels[j]);
            }

            System.out.println();
            System.out.println(getHeader(u));
            System.out.println();

            System.out.println(table);
        }


        NumberFormat nf = new DecimalFormat("0.00");

        System.out.println();
        System.out.println("Details:");
        System.out.println();
        System.out.println("Average statistics");

        for (int u = 0; u < 4; u++) {
            System.out.println();
            System.out.println(getHeader(u));
            System.out.println();

            for (int numCategories = 2; numCategories <= 5; numCategories++) {
                System.out.println("\n# categories = " + numCategories);

                for (int t = 0; t < algorithms.length; t++) {
                    String algorithm = algorithms[t];

                    System.out.println("\nAlgorithm = " + algorithm);
                    System.out.println();

                    for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {
                        String statLabel = statLabels[statIndex];
                        double stat = allAllRet[numCategories - 2][t][statIndex][u];
                        System.out.println("\tAverage" + statLabel + " = " + nf.format(stat));
                    }
                }
            }
        }

    }

    //    @Test
    public void test7() {
        for (int i = 0; i < 10; i++) {

            Graph graph = edu.cmu.tetrad.graph.RandomGraph.randomGraph(10, 0,
                    10, 10, 10, 10, false);
            SemPm semPm = new SemPm(graph);
            SemIm semIm = new SemIm(semPm);
            DataSet dataSet = semIm.simulateData(1000, false);

            Fges fges = new Fges(new SemBicScore(new CovarianceMatrix(dataSet)));
            Graph CPDAG = fges.search();

            Graph dag = dagFromCPDAG(CPDAG);

            assertFalse(dag.paths().existsDirectedCycle());
        }
    }

    private Graph dagFromCPDAG(Graph CPDAG) {
        Graph dag = new EdgeListGraph(CPDAG);

        MeekRules rules = new MeekRules();

        WHILE:
        while (true) {
            List<Edge> edges = new ArrayList<>(dag.getEdges());

            for (Edge edge : edges) {
                if (Edges.isUndirectedEdge(edge)) {
                    Node x = edge.getNode1();
                    Node y = edge.getNode2();

                    List<Node> okx = dag.getAdjacentNodes(x);
                    okx.removeAll(dag.getChildren(x));
                    okx.remove(y);

                    List<Node> oky = dag.getAdjacentNodes(y);
                    oky.removeAll(dag.getChildren(y));
                    oky.remove(x);

                    if (!okx.isEmpty()) {
                        Node other = okx.get(0);
                        dag.removeEdge(other, x);
                        dag.removeEdge(y, x);
                        dag.addDirectedEdge(other, x);
                        dag.addDirectedEdge(y, x);
                    } else if (!oky.isEmpty()) {
                        Node other = oky.get(0);
                        dag.removeEdge(other, y);
                        dag.removeEdge(x, y);
                        dag.addDirectedEdge(other, y);
                        dag.addDirectedEdge(x, y);
                    } else {
                        dag.removeEdge(x, y);
                        dag.addDirectedEdge(x, y);
                    }

                    rules.orientImplied(dag);
                    continue WHILE;
                }
            }

            break;
        }

        return dag;
    }

    public void test9() {

        Parameters parameters = new Parameters();

        parameters.set(Params.NUM_MEASURES, 50);
        parameters.set(Params.NUM_LATENTS, 0);
        parameters.set(Params.AVG_DEGREE, 2);
        parameters.set(Params.MAX_DEGREE, 20);
        parameters.set(Params.MAX_INDEGREE, 20);
        parameters.set(Params.MAX_OUTDEGREE, 20);
        parameters.set(Params.CONNECTED, false);

        parameters.set(Params.COEF_LOW, 0.2);
        parameters.set(Params.COEF_HIGH, 0.9);
        parameters.set(Params.VAR_LOW, 1);
        parameters.set(Params.VAR_HIGH, 3);
        parameters.set(Params.VERBOSE, false);
        parameters.set(Params.COEF_SYMMETRIC, true);
        parameters.set(Params.NUM_RUNS, 1);
        parameters.set(Params.PERCENT_DISCRETE, 0);
        parameters.set(Params.NUM_CATEGORIES, 3);
        parameters.set(Params.DIFFERENT_GRAPHS, true);
        parameters.set(Params.SAMPLE_SIZE, 500);
        parameters.set(Params.INTERVAL_BETWEEN_SHOCKS, 10);
        parameters.set(Params.INTERVAL_BETWEEN_RECORDINGS, 10);
        parameters.set(Params.FISHER_EPSILON, 0.001);
        parameters.set(Params.RANDOMIZE_COLUMNS, true);

        RandomGraph graph = new RandomForward();
        LinearFisherModel sim = new LinearFisherModel(graph);
        sim.createData(parameters, false);
        Graph previous = null;
        int prevDiff = Integer.MAX_VALUE;

//        for (int l = 7; l >= 1; l--) {
        for (int i = 2; i <= 20; i++) {
            parameters.set(Params.PENALTY_DISCOUNT, i / (double) 10);

            ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            Algorithm alg = new CPC(new FisherZ());

            Graph out = alg.search(sim.getDataModel(0), parameters);
//            Graph out = GraphUtils.undirectedGraph(alg.search(sim.getDataModel(0), parameters));

            Set<Edge> edges1 = out.getEdges();

            int numEdges = edges1.size();

            if (previous != null) {
                Set<Edge> edges2 = previous.getEdges();
                edges2.removeAll(edges1);
                int diff = edges2.size();
//
                System.out.println("Penalty discount =" + parameters.getDouble(Params.PENALTY_DISCOUNT)
                        + " # edges = " + numEdges
                        + " # additional = " + diff);

                previous = out;
                if (diff > prevDiff) break;
                prevDiff = diff;
            } else {
                previous = out;
            }
        }

        Graph estGraph = previous;
        Graph trueGraph = sim.getTrueGraph(0);

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        Statistic ap = new AdjacencyPrecision();
        Statistic ar = new AdjacencyRecall();
        Statistic ahp = new ArrowheadPrecision();
        Statistic ahr = new ArrowheadRecall();

        System.out.println("AP = " + ap.getValue(trueGraph, estGraph, null));
        System.out.println("AR = " + ar.getValue(trueGraph, estGraph, null));
        System.out.println("AHP = " + ahp.getValue(trueGraph, estGraph, null));
        System.out.println("AHR = " + ahr.getValue(trueGraph, estGraph, null));
    }

    @Test
    public void testSemBicDiffs() {
        final int N = 1000;
        final int numCond = 3;

        Graph graph = edu.cmu.tetrad.graph.RandomGraph.randomGraph(10, 0, 20, 100,
                100, 100, false);
        List<Node> nodes = graph.getNodes();
        buildIndexing(nodes);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(N, false);
        SemBicScore score = new SemBicScore(dataSet);

        IndTestDSep dsep = new IndTestDSep(graph);
        int count = 1;

        for (int i = 0; i < 10000; i++) {
            Collections.shuffle(nodes);

            Node x = nodes.get(0);
            Node y = nodes.get(1);
            Set<Node> z = new HashSet<>();

            for (int c = 3; c <= 2 + numCond; c++) {
                z.add(nodes.get(c));
            }

            boolean _dsep = dsep.checkIndependence(x, y, new ArrayList<>(z)).independent();
            double diff = scoreGraphChange(x, y, z, this.hashIndices, score);
            boolean diffNegative = diff < 0;

            if (!_dsep && _dsep != diffNegative) {
                System.out.println(count++ + "\t" + (_dsep ? "dsep" : "dconn") + "\t" + (diffNegative ? "indep" : "dep") + "\tdiff = " + diff);
            }
        }

    }

    private double scoreGraphChange(Node x, Node y, Set<Node> parents,
                                    Map<Node, Integer> hashIndices, SemBicScore score) {
        int yIndex = hashIndices.get(y);

        if (x == y) {
            throw new IllegalArgumentException();
        }
        if (parents.contains(y)) {
            throw new IllegalArgumentException();
        }

        if (parents.contains(x)) {
            throw new IllegalArgumentException();
        }

        int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        return score.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private HashMap<Node, Integer> hashIndices;

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new HashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    public static void main(String... args) {
        if (args.length > 0) {
            int numMeasures = Integer.parseInt(args[0]);
            int avgDegree = Integer.parseInt(args[1]);

            Parameters parameters = new Parameters();

            parameters.set(Params.NUM_MEASURES, numMeasures);
            parameters.set(Params.NUM_LATENTS, 0);
            parameters.set(Params.AVG_DEGREE, avgDegree);
            parameters.set(Params.MAX_DEGREE, 20);
            parameters.set(Params.MAX_INDEGREE, 20);
            parameters.set(Params.MAX_OUTDEGREE, 20);
            parameters.set(Params.CONNECTED, false);

            parameters.set(Params.COEF_LOW, 0.2);
            parameters.set(Params.COEF_HIGH, 0.9);
            parameters.set(Params.VAR_LOW, 1);
            parameters.set(Params.VAR_HIGH, 3);
            parameters.set(Params.VERBOSE, false);
            parameters.set(Params.COEF_SYMMETRIC, true);
            parameters.set(Params.NUM_RUNS, 1);
            parameters.set(Params.PERCENT_DISCRETE, 0);
            parameters.set(Params.NUM_CATEGORIES, 3);
            parameters.set(Params.DIFFERENT_GRAPHS, true);
            parameters.set(Params.SAMPLE_SIZE, 1000);
            parameters.set(Params.INTERVAL_BETWEEN_SHOCKS, 10);
            parameters.set(Params.INTERVAL_BETWEEN_RECORDINGS, 10);
            parameters.set(Params.FISHER_EPSILON, 0.001);
            parameters.set(Params.RANDOMIZE_COLUMNS, true);

            RandomGraph graph = new RandomForward();
            LinearFisherModel sim = new LinearFisherModel(graph);
            sim.createData(parameters, false);
            ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            Algorithm alg = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(score);

            parameters.set(Params.ALPHA, 1e-8);

            for (int i = 0; i < 5; i++) {
                Graph out1 = alg.search(sim.getDataModel(0), parameters);
                System.out.println(out1);
            }

        } else {
            new TestFges().test9();
        }
    }

}




