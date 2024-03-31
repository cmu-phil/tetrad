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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FgesMb;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.score.BdeScore;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author josephramsey
 */
public class TestFges {
    private final PrintStream out = System.out;
    boolean precomputeCovariances = true;
    //    private OutputStream out =
    private HashMap<Node, Integer> hashIndices;

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
            Algorithm alg = new Fges(score);

            parameters.set(Params.ALPHA, 1e-8);

            for (int i = 0; i < 5; i++) {
                Graph out1 = alg.search(sim.getDataModel(0), parameters);
                System.out.println(out1);
            }
        }
    }

    @NotNull
    private static Parameters getParameters() {
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
        return parameters;
    }

    private static Graph getGraph(ICovarianceMatrix cov) {
        Knowledge knowledge = new Knowledge();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        SemBicScore score = new SemBicScore(cov);
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(score);
        fges.setKnowledge(knowledge);

        fges.setVerbose(true);

        return fges.search();
    }

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

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(numCases, false);

        System.out.println("data done");

        ICovarianceMatrix cov = SimpleDataLoader.getCovarianceMatrix(data, false);
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(penaltyDiscount);

        edu.cmu.tetrad.search.Fges alg = new edu.cmu.tetrad.search.Fges(score);
        alg.setVerbose(true);
        alg.setOut(this.out);
        alg.setFaithfulnessAssumed(true);
        Graph estCPDAG = alg.search();

        Graph trueCPDAG = GraphTransforms.cpdagForDag(dag);

        estCPDAG = GraphUtils.replaceNodes(estCPDAG, vars);

        System.out.println("true = " + trueCPDAG + " est = " + estCPDAG);

        double ap = new AdjacencyPrecision().getValue(trueCPDAG, estCPDAG, data);
        double ar = new AdjacencyRecall().getValue(trueCPDAG, estCPDAG, data);

        System.out.println("ap = " + ap + " ar = " + ar);
    }

    @Test
    public void explore2() {
        RandomUtil.getInstance().setSeed(1457220623122L);

        final int numVars = 10;
        final double edgeFactor = 1.0;
        final int numCases = 1000;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false, true);

        BayesPm pm = new BayesPm(dag, 2, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.InitializationMethod.RANDOM);
        DataSet data = im.simulateData(numCases, false);

        BdeScore score = new BdeScore(data);

        edu.cmu.tetrad.search.Fges ges = new edu.cmu.tetrad.search.Fges(score);
        ges.setVerbose(false);
        ges.setFaithfulnessAssumed(false);
    }

    @Test
    public void testExplore3() {
        Graph graph = GraphUtils.convert("A-->B,A-->C,B-->D,C-->D");
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(graph));
        Graph CPDAG = fges.search();
        assertEquals(GraphTransforms.cpdagForDag(graph), CPDAG);
    }

    @Test
    public void testExplore4() {
        Graph graph = GraphUtils.convert("A-->B,A-->C,A-->D,B-->E,C-->E,D-->E");
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(graph));
        Graph CPDAG = fges.search();
        assertEquals(GraphTransforms.cpdagForDag(graph), CPDAG);
    }

    @Test
    public void testExplore5() {
        Graph graph = GraphUtils.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(graph));
        fges.setFaithfulnessAssumed(true);
        Graph CPDAG = fges.search();
        assertEquals(GraphTransforms.cpdagForDag(graph), CPDAG);
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

        Graph CPDAG1 = new Pc(new MsepTest(g)).search();
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(g));
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

        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(fgesScore);
        Graph CPDAG1 = fges.search();

        Set<Node> mb = new HashSet<>();
        mb.add(x1);

        mb.addAll(CPDAG1.getAdjacentNodes(x1));

        for (Node child : CPDAG1.getChildren(x1)) {
            mb.addAll(CPDAG1.getParents(child));
        }

        Graph mb1 = CPDAG1.subgraph(new ArrayList<>(mb));

        FgesMb fgesMb = new FgesMb(fgesScore);
        Graph mb2 = fgesMb.search(Collections.singletonList(x1));

        assertEquals(mb1, mb2);
    }

    @Test
    public void clarkTest() {
        RandomGraph randomGraph = new RandomForward();

        Simulation simulation = new LinearFisherModel(randomGraph);

        Parameters parameters = getParameters();

        simulation.createData(parameters, false);

        DataSet dataSet = (DataSet) simulation.getDataModel(0);
        Graph trueGraph = simulation.getTrueGraph(0);

        ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
        IndependenceWrapper test = new FisherZ();

        Algorithm fges = new Fges(score);

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
            RandomUtil.shuffle(nodes);
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

            boolean dependent = !_test.checkIndependence(x, y).isIndependent();

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
        knowledge.setTier(0, Collections.singletonList("A"));
        knowledge.setTier(1, Collections.singletonList("B"));

        checkWithKnowledge("A-->B", "A-->B", knowledge);
    }

    @Test
    public void testCites() {
        final String citesString = """
                164
                ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS
                1.0
                .62\t1.0
                .25\t.09\t1.0
                .16\t.28\t.07\t1.0
                -.10\t.00\t.03\t.10\t1.0
                .29\t.25\t.34\t.37\t.13\t1.0
                .18\t.15\t.19\t.41\t.43\t.55\t1.0""";

        char[] citesChars = citesString.toCharArray();
        ICovarianceMatrix cov = SimpleDataLoader.loadCovarianceMatrix(citesChars, "//", DelimiterType.WHITESPACE, '\"', "*");

        Graph CPDAG = getGraph(cov);

        System.out.println(CPDAG);

        final String trueString = """
                Graph Nodes:
                Graph Nodes:
                Graph Nodes:;ABILITY;GPQ;PREPROD;QFJ;SEX;CITES;PUBS

                Graph Edges:
                1. ABILITY --> GPQ
                2. ABILITY --> PREPROD
                3. ABILITY --> PUBS
                4. GPQ --> QFJ
                5. PREPROD --> CITES
                6. PUBS --> CITES
                7. QFJ --> CITES
                8. QFJ --> PUBS
                9. SEX --> PUBS""";

        try {
            Graph trueGraph = GraphSaveLoadUtils.readerToGraphTxt(trueString);
            CPDAG = GraphUtils.replaceNodes(CPDAG, trueGraph.getNodes());
            assertEquals(trueGraph, CPDAG);
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Error in testCites");
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
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(graph));

        // Run search
        Graph resultGraph = fges.search();

        // Build comparison graph.
        Graph trueGraph = GraphUtils.convert(outputGraph);

        // PrintUtil out problem and graphs.

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertEquals(resultGraph, trueGraph);
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
        edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(input));

        // Set up search.
        fges.setKnowledge(knowledge);

        // Run search
        Graph result = fges.search();

        // Build comparison graph.
        Graph answer = GraphUtils.convert(answerGraph);
        answer = GraphUtils.replaceNodes(answer, input.getNodes());

        assertEquals(answer, result);
    }

    @Test
    public void testFromGraph() {
        final int numNodes = 10;
        final int aveDegree = 4;
        final int numIterations = 10;

        for (int i = 0; i < numIterations; i++) {
            Graph dag = edu.cmu.tetrad.graph.RandomGraph.randomDag(numNodes, 0, aveDegree * numNodes / 2, 10, 10, 10, false);
            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setVerbose(true);
            fges.setNumThreads(1);
            Graph CPDAG1 = fges.search();
            Graph CPDAG2 = GraphTransforms.cpdagForDag(dag);
            assertEquals(CPDAG2, CPDAG1);
        }
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

            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(dag));
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

            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(new GraphScore(dag));
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

    @Test
    public void testSemBicDiffs() {
        final int N = 1000;
        final int numCond = 3;

        Graph graph = edu.cmu.tetrad.graph.RandomGraph.randomGraph(10, 0, 10, 100,
                100, 100, false);
        List<Node> nodes = graph.getNodes();
        buildIndexing(nodes);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(N, false);
        SemBicScore score = new SemBicScore(dataSet, precomputeCovariances);

        MsepTest msep = new MsepTest(graph);
        int count = 1;

        for (int i = 0; i < 10000; i++) {
            RandomUtil.shuffle(nodes);

            Node x = nodes.get(0);
            Node y = nodes.get(1);
            Set<Node> z = new HashSet<>();

            for (int c = 3; c <= 2 + numCond; c++) {
                z.add(nodes.get(c));
            }

            boolean _msep = msep.checkIndependence(x, y, new HashSet<>(z)).isIndependent();
            double diff = scoreGraphChange(x, y, z, this.hashIndices, score);
            boolean diffNegative = diff < 0;

            if (!_msep && diffNegative) {
                System.out.println(count++ + "\t" + "mconn" + "\t" + "indep" + "\tdiff = " + diff);
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

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new HashMap<>();

        int i = -1;

        for (Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }
}




