///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
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

    //    @Test
    public void explore1() {
        RandomUtil.getInstance().setSeed(1450184147770L);

        final int numVars = 10;
        final double edgesPerNode = 1.0;
        final int numCases = 1000;
        final double penaltyDiscount = 2.0;

        final int numEdges = (int) (numVars * edgesPerNode);

        final List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        final Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false, true);
//        printDegreeDistribution(dag, System.out);

        final int[] causalOrdering = new int[vars.size()];

        for (int i = 0; i < vars.size(); i++) {
            causalOrdering[i] = i;
        }

        final LargeScaleSimulation simulator = new LargeScaleSimulation(dag, vars, causalOrdering);
        simulator.setOut(this.out);
        final DataSet data = simulator.simulateDataFisher(numCases);

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        final ICovarianceMatrix cov = new CovarianceMatrix(data);
        final SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(penaltyDiscount);

        final Fges fges = new Fges(score);
        fges.setVerbose(false);
        fges.setOut(this.out);
        fges.setFaithfulnessAssumed(true);

        final Graph estCPDAG = fges.search();

//        printDegreeDistribution(estCPDAG, out);

        final Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        final int[][] counts = SearchGraphUtils.graphComparison(estCPDAG, trueCPDAG, null);

        final int[][] expectedCounts = {
                {2, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 8, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

        for (int i = 0; i < counts.length; i++) {
            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
        }
//

//        System.out.println(MatrixUtils.toString(expectedCounts));
//        System.out.println(MatrixUtils.toString(counts));

    }

    @Test
    public void explore2() {
        RandomUtil.getInstance().setSeed(1457220623122L);

        final int numVars = 20;
        final double edgeFactor = 1.0;
        final int numCases = 1000;
        final double structurePrior = 1;
        final double samplePrior = 1;

        final List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        final Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false, true);
//        printDegreeDistribution(dag, out);

        final BayesPm pm = new BayesPm(dag, 2, 3);
        final BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        final DataSet data = im.simulateData(numCases, false);

//        out.println("Finishing simulation");

        final BDeScore score = new BDeScore(data);
        score.setSamplePrior(samplePrior);
        score.setStructurePrior(structurePrior);

        final Fges ges = new Fges(score);
        ges.setVerbose(false);
        ges.setFaithfulnessAssumed(false);

        final Graph estCPDAG = ges.search();

        final Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

        final int[][] counts = SearchGraphUtils.graphComparison(estCPDAG, trueCPDAG, null);

        final int[][] expectedCounts = {
                {2, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {2, 0, 0, 13, 0, 3},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

//        for (int i = 0; i < counts.length; i++) {
//            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
//        }

//        System.out.println(MatrixUtils.toString(expectedCounts));
//        System.out.println(MatrixUtils.toString(counts));
//        System.out.println(RandomUtil.getInstance().getSeed());
    }


    @Test
    public void testExplore3() {
        final Graph graph = GraphConverter.convert("A-->B,A-->C,B-->D,C-->D");
        final Fges fges = new Fges(new GraphScore(graph));
        final Graph CPDAG = fges.search();
        assertEquals(SearchGraphUtils.cpdagForDag(graph), CPDAG);
    }

    @Test
    public void testExplore4() {
        final Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,B-->E,C-->E,D-->E");
        final Fges fges = new Fges(new GraphScore(graph));
        final Graph CPDAG = fges.search();
        assertEquals(SearchGraphUtils.cpdagForDag(graph), CPDAG);
    }

    @Test
    public void testExplore5() {
        final Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
        final Fges fges = new Fges(new GraphScore(graph));
        fges.setFaithfulnessAssumed(false);
        final Graph CPDAG = fges.search();
        assertEquals(SearchGraphUtils.cpdagForDag(graph), CPDAG);
    }


    @Test
    public void testFromGraphSimpleFges() {

        // This may fail if faithfulness is assumed but should pass if not.

        final Node x1 = new GraphNode("X1");
        final Node x2 = new GraphNode("X2");
        final Node x3 = new GraphNode("X3");
        final Node x4 = new GraphNode("X4");

        final Graph g = new EdgeListGraph();
        g.addNode(x1);
        g.addNode(x2);
        g.addNode(x3);
        g.addNode(x4);

        g.addDirectedEdge(x1, x2);
        g.addDirectedEdge(x1, x3);
        g.addDirectedEdge(x4, x2);
        g.addDirectedEdge(x4, x3);

        final Graph CPDAG1 = new Pc(new IndTestDSep(g)).search();
        final Fges fges = new Fges(new GraphScore(g));
        fges.setFaithfulnessAssumed(true);
        final Graph CPDAG2 = fges.search();

//        System.out.println(CPDAG1);
//        System.out.println(CPDAG2);

        assertEquals(CPDAG1, CPDAG2);
    }

    @Test
    public void testFromGraphSimpleFgesMb() {

        // This may fail if faithfulness is assumed but should pass if not.

        final Node x1 = new GraphNode("X1");
        final Node x2 = new GraphNode("X2");
        final Node x3 = new GraphNode("X3");
        final Node x4 = new GraphNode("X4");

        final Graph dag = new EdgeListGraph();
        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);

        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x1, x3);
        dag.addDirectedEdge(x4, x2);
        dag.addDirectedEdge(x4, x3);

        final GraphScore fgesScore = new GraphScore(dag);

        final Fges fges = new Fges(fgesScore);
        final Graph CPDAG1 = fges.search();

        final Set<Node> mb = new HashSet<>();
        mb.add(x1);

        mb.addAll(CPDAG1.getAdjacentNodes(x1));

        for (final Node child : CPDAG1.getChildren(x1)) {
            mb.addAll(CPDAG1.getParents(child));
        }

        final Graph mb1 = CPDAG1.subgraph(new ArrayList<>(mb));

        final FgesMb fgesMb = new FgesMb(fgesScore);
        final Graph mb2 = fgesMb.search(x1);

        assertEquals(mb1, mb2);
    }

    //    @Test
    public void testFgesMbFromGraph() {
        RandomUtil.getInstance().setSeed(1450184147770L);

        final int numNodes = 20;
        final int numIterations = 2;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            final Graph dag = GraphUtils.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            final GraphScore fgesScore = new GraphScore(dag);

            final Fges fges = new Fges(fgesScore);
            final Graph CPDAG1 = fges.search();

            final Node x1 = fgesScore.getVariable("X1");

            final Set<Node> mb = new HashSet<>();
            mb.add(x1);

            mb.addAll(CPDAG1.getAdjacentNodes(x1));

            for (final Node child : CPDAG1.getChildren(x1)) {
                mb.addAll(CPDAG1.getParents(child));
            }

            final Graph mb1 = CPDAG1.subgraph(new ArrayList<>(mb));

            final FgesMb fgesMb = new FgesMb(fgesScore);
            final Graph mb2 = fgesMb.search(x1);

            assertEquals(mb1, mb2);
        }
    }


    private void printDegreeDistribution(final Graph dag, final PrintStream out) {
        int max = 0;

        for (final Node node : dag.getNodes()) {
            final int degree = dag.getAdjacentNodes(node).size();
            if (degree > max) max = degree;
        }

        final int[] counts = new int[max + 1];
        final Map<Integer, List<Node>> names = new HashMap<>();

        for (int i = 0; i <= max; i++) {
            names.put(i, new ArrayList<Node>());
        }

        for (final Node node : dag.getNodes()) {
            final int degree = dag.getAdjacentNodes(node).size();
            counts[degree]++;
            names.get(degree).add(node);
        }

        for (int k = 0; k < counts.length; k++) {
            if (counts[k] == 0) continue;

            out.print(k + " " + counts[k]);

            for (final Node node : names.get(k)) {
                out.print(" " + node.getName());
            }

            out.println();
        }
    }

    @Test
    public void clarkTest() {
        final RandomGraph randomGraph = new RandomForward();

        final Simulation simulation = new LinearFisherModel(randomGraph);

        final Parameters parameters = new Parameters();

        parameters.set(Params.NUM_MEASURES, 100);
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

        final DataSet dataSet = (DataSet) simulation.getDataModel(0);
        final Graph trueGraph = simulation.getTrueGraph(0);

//        trueGraph = SearchGraphUtils.CPDAGForDag(trueGraph);

        final ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
        final IndependenceWrapper test = new FisherZ();

        final Algorithm fges = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(score);

        final Graph fgesGraph = fges.search(dataSet, parameters);

        clarkTestForAlpha(0.05, parameters, dataSet, trueGraph, fgesGraph, test);
        clarkTestForAlpha(0.01, parameters, dataSet, trueGraph, fgesGraph, test);

    }

    private void clarkTestForAlpha(final double alpha, final Parameters parameters, final DataSet dataSet, Graph trueGraph,
                                   Graph CPDAG, final IndependenceWrapper test) {
        parameters.set(Params.ALPHA, alpha);

        final List<Node> nodes = dataSet.getVariables();

        trueGraph = GraphUtils.replaceNodes(trueGraph, nodes);
        CPDAG = GraphUtils.replaceNodes(CPDAG, nodes);

        final IndependenceTest _test = test.getTest(dataSet, parameters);

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
            final Node x = nodes.get(0);
            final Node y = nodes.get(1);

            final boolean trueAncestral = ancestral(x, y, trueGraph);
            final boolean estAncestral = ancestral(x, y, CPDAG);

            if (trueAncestral && estAncestral) {
                tp1++;
            }

            if (!trueAncestral && estAncestral) {
                fn1++;
            }

            if (trueAncestral && !estAncestral) {
                fp1++;
            }

            final boolean dependent = !_test.isIndependent(x, y);

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

        final double prec1 = tp1 / (double) (tp1 + fp1);
        final double rec1 = tp1 / (double) (tp1 + fn1);

        final double prec2 = tp2 / (double) (tp2 + fp2);
        final double rec2 = tp2 / (double) (tp2 + fn2);

        System.out.println("Experiment 1: Comparing ancestral connection in true DAG versus estimated CPDAG");

        System.out.println("Precision = " + prec1 + " recall = " + rec1);

        System.out.println("Experiment 1: Comparing ancestral connection in true DAG to judgement of independence by Fisher Z");

        System.out.println("Precision = " + prec2 + " recall = " + rec2);
    }

    private boolean ancestral(final Node x, final Node y, final Graph graph) {
        return graph.isAncestorOf(x, y) || graph.isAncestorOf(y, x);
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
        final IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A---D,B---A,B-->C,C---A",
                knowledge);
    }

    @Test
    public void testSearch5() {
        final IKnowledge knowledge = new Knowledge2();
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

        final char[] citesChars = citesString.toCharArray();
        final ICovarianceMatrix cov = DataUtils.parseCovariance(citesChars, "//", DelimiterType.WHITESPACE, '\"', "*");

        final IKnowledge knowledge = new Knowledge2();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        final SemBicScore score = new SemBicScore(cov);
//        score.setRuleType(SemBicScore.RuleType.NANDY);
//        score.setPenaltyDiscount(1);
//        score.setStructurePrior(0);
        final Fges fges = new Fges(score);
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
            trueGraph = GraphUtils.readerToGraphTxt(trueString);
            CPDAG = GraphUtils.replaceNodes(CPDAG, trueGraph.getNodes());
            assertEquals(trueGraph, CPDAG);
        } catch (final IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkSearch(final String inputGraph, final String outputGraph) {

        // Set up graph and node objects.
        final Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        final Fges fges = new Fges(new GraphScore(graph));

        // Run search
        Graph resultGraph = fges.search();

        // Build comparison graph.
        final Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
//        System.out.println("\nInput graph:");
//        System.out.println(graph);
//        System.out.println("\nResult graph:");
//        System.out.println(resultGraph);
//        System.out.println("\nTrue graph:");
//        System.out.println(trueGraph);

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(final String inputGraph, final String answerGraph,
                                    final IKnowledge knowledge) {
        // Set up graph and node objects.
        final Graph input = GraphConverter.convert(inputGraph);

        // Set up search.
        final Fges fges = new Fges(new GraphScore(input));

        // Set up search.
        fges.setKnowledge(knowledge);

        // Run search
        final Graph result = fges.search();

        // Build comparison graph.
        final Graph answer = GraphConverter.convert(answerGraph);
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
            final Graph dag = GraphUtils.randomDag(numNodes, 0, aveDegree * numNodes / 2, 10, 10, 10, false);
            final Fges fges = new Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setVerbose(true);
            final Graph CPDAG1 = fges.search();
            final Graph CPDAG2 = new Pc(new IndTestDSep(dag)).search();
            assertEquals(CPDAG2, CPDAG1);
        }
    }

    //    @Test
    public void testFromData() {
        final int numIterations = 1;

        final Parameters params = new Parameters();

        final int[] nodeOptions = {5, 10, 20, 30, 40, 50, 75, 100};
        final int[] avgDegreeOptions = {2, 4, 6};
        final int[] sampleSizeOptions = {100, 500, 1000, 10000, 100000};

        final int numRowsInTable = nodeOptions.length * avgDegreeOptions.length * sampleSizeOptions.length;

        final TextTable table = new TextTable(numRowsInTable + 1, 5);

        table.setToken(0, 0, "# Nodes");
        table.setToken(0, 1, "Avg Degree");
        table.setToken(0, 2, "# Samples");
        table.setToken(0, 3, "True # edges");
        table.setToken(0, 4, "Est # Edges");

        int count = 0;

        for (final int numNodes : nodeOptions) {
            for (final int avgDegree : avgDegreeOptions) {
                for (final int sampleSize : sampleSizeOptions) {
                    for (int q = 0; q < 1; q++) {
                        for (int i = 0; i < numIterations; i++) {
                            final Graph dag = GraphUtils.randomDag(numNodes, 0,
                                    (avgDegree * numNodes) / 2, 100, 100, 100, false);
                            final SemPm pm = new SemPm(dag);
                            final SemIm im = new SemIm(pm, params);
                            final DataSet data = im.simulateData(sampleSize, false);
                            final SemBicScore score = new SemBicScore(data);
                            score.setPenaltyDiscount(.5);
                            final Fges fges = new Fges(score);
                            fges.setFaithfulnessAssumed(false);
                            fges.setVerbose(false);
                            final Graph CPDAG1 = fges.search();
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
        final int numNodes = 20;
        final int numIterations = 20;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            final Graph dag = GraphUtils.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            Graph knowledgeGraph = GraphUtils.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            knowledgeGraph = GraphUtils.replaceNodes(knowledgeGraph, dag.getNodes());

            final IKnowledge knowledge = forbiddenKnowledge(knowledgeGraph);

            final Fges fges = new Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setKnowledge(knowledge);
            final Graph CPDAG1 = fges.search();

            for (final Edge edge : knowledgeGraph.getEdges()) {
                final Node x = Edges.getDirectedEdgeTail(edge);
                final Node y = Edges.getDirectedEdgeHead(edge);

                if (CPDAG1.isParentOf(x, y)) {
                    System.out.println("Knowledge violated: " + edge + " x = " + x + " y = " + y);
                }

                assertFalse(CPDAG1.isParentOf(x, y));
            }
        }
    }

    //    @Test
    public void testFromGraphWithRequiredKnowledge() {
        final int numNodes = 20;
        final int numIterations = 20;

        for (int i = 0; i < numIterations; i++) {
            System.out.println("Iteration " + (i + 1));
            final Graph dag = GraphUtils.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            Graph knowledgeGraph = GraphUtils.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            knowledgeGraph = GraphUtils.replaceNodes(knowledgeGraph, dag.getNodes());

            final IKnowledge knowledge = requiredKnowledge(knowledgeGraph);

            final Fges fges = new Fges(new GraphScore(dag));
            fges.setFaithfulnessAssumed(true);
            fges.setKnowledge(knowledge);
            final Graph CPDAG1 = fges.search();

            for (final Edge edge : knowledgeGraph.getEdges()) {
                final Node x = Edges.getDirectedEdgeTail(edge);
                final Node y = Edges.getDirectedEdgeHead(edge);

                if (!CPDAG1.isParentOf(x, y)) {
                    System.out.println("Knowledge violated: " + edge + " x = " + x + " y = " + y);
                }

                assertTrue(CPDAG1.isParentOf(x, y));
            }
        }
    }


    private IKnowledge forbiddenKnowledge(final Graph graph) {
        final IKnowledge knowledge = new Knowledge2(graph.getNodeNames());

        final List<Node> nodes = graph.getNodes();

        for (final Edge edge : graph.getEdges()) {
            final Node n1 = Edges.getDirectedEdgeTail(edge);
            final Node n2 = Edges.getDirectedEdgeHead(edge);

            if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                continue;
            }

            knowledge.setForbidden(n1.getName(), n2.getName());
        }

        return knowledge;
    }

    private IKnowledge requiredKnowledge(final Graph graph) {

        final IKnowledge knowledge = new Knowledge2(graph.getNodeNames());

        for (final Edge edge : graph.getEdges()) {
            final Node n1 = Edges.getDirectedEdgeTail(edge);
            final Node n2 = Edges.getDirectedEdgeHead(edge);

            if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
                continue;
            }

            knowledge.setRequired(n1.getName(), n2.getName());
        }

        return knowledge;
    }


//    @Test
//    public void testFromData() {
//        Parameters parameters = new Parameters();
//        parameters.set(Params.STANDARDIZE, false);
//        parameters.set(Params.MEASUREMENT_VARIANCE, 0);
//        parameters.set(Params.NUM_RUNS, 1);
//        parameters.set(Params.DIFFERENT_GRAPHS, true);
//        parameters.set(Params.SAMPLE_SIZE, 1000);
//
//        parameters.set(Params.NUM_MEASURES, 100);
//        parameters.set(Params.NUM_LATENTS, 0);
//        parameters.set(Params.AVG_DEGREE, 6);
//
////        parameters.set("maxDegree", 100);
////        parameters.set("maxIndegree", 100);
////        parameters.set("maxOutdegree", 100);
//
//        parameters.set(Params.SYMMETRIC_FIRST_STEP, true);
//
//        parameters.set(Params.ADJACENCY_FAITHFULNESS_ASSUMED, false);
//        parameters.set(Params.PENALTY_DISCOUNT, 2);
//        parameters.set(Params.ALPHA, 0.001);
//
//        parameters.set(Params.COEF_LOW, 0.2);
//        parameters.set(Params.COEF_HIGH, 0.9);
//        parameters.set(Params.VAR_LOW, 1);
//        parameters.set(Params.VAR_HIGH, 3);
//        parameters.set(Params.COEF_SYMMETRIC, true);
//        parameters.set(Params.COV_SYMMETRIC, true);
//
//        parameters.set(Params.RANDOMIZE_COLUMNS, true);
//
//        SemSimulation simulation = new SemSimulation(new RandomForward());
//        simulation.createData(parameters);
//        Graph dag = simulation.getTrueGraph(0);
//
//        DataModel dataSet = simulation.getDataModel(0);
//
//        edu.cmu.tetrad.algcomparison.algorithm.oracle.CPDAG.PcFges pcFges
//                = new edu.cmu.tetrad.algcomparison.algorithm.oracle.CPDAG.PcFges(
//                new edu.cmu.tetrad.algcomparison.score.SemBicScore(),false);
//
//        long start = System.currentTimeMillis();
//
//        Graph graph = pcFges.search(dataSet, parameters);
//
//        long stop = System.currentTimeMillis();
//
//        System.out.println("Elapsed " + (stop - start) + " ms");
//
//        graph = GraphUtils.replaceNodes(graph, dag.getNodes());
//
//        System.out.println(MisclassificationUtils.edgeMisclassifications(graph, dag));
//
//    }

    private Graph getSubgraph(final Graph graph, final boolean discrete1, final boolean discrete2, final DataSet dataSet) {
        final Graph newGraph = new EdgeListGraph(graph.getNodes());

        for (final Edge edge : graph.getEdges()) {
            final Node node1 = dataSet.getVariable(edge.getNode1().getName());
            final Node node2 = dataSet.getVariable(edge.getNode2().getName());

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

    //    @Test
    public void testAjData() {
        final double penalty = 4;

        try {

            for (int i = 0; i < 50; i++) {
                final File dataPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
                        "Simulated_data_for_Madelyn/simulation/data/DAG_" + i + "_data.txt");
                final DataSet Dk = DataUtils.loadContinuousData(dataPath, "//", '\"' ,
                        "*", true, Delimiter.TAB);

                final File graphPath = new File("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/2016.05.25/" +
                        "Simulated_data_for_Madelyn/simulation/networks/DAG_" + i + "_graph.txt");

                final Graph dag = GraphUtils.loadGraphTxt(graphPath);

                final long start = System.currentTimeMillis();

//            Graph CPDAG = searchSemFges(Dk);
//            Graph CPDAG = searchBdeuFges(Dk, k);
                final Graph CPDAG = searchMixedFges(Dk, penalty);

                final long stop = System.currentTimeMillis();

                final long elapsed = stop - start;
                final long elapsedSeconds = elapsed / 1000;

                final Graph trueCPDAG = SearchGraphUtils.cpdagForDag(dag);

                final GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison(CPDAG, trueCPDAG);
                final NumberFormat nf = new DecimalFormat("0.00");

                System.out.println(i +
                        "\t" + nf.format(comparison.getAdjPrec()) +
                        "\t" + nf.format(comparison.getAdjRec()) +
                        "\t" + nf.format(comparison.getAhdPrec()) +
                        "\t" + nf.format(comparison.getAhdRec()) +
                        "\t" + elapsedSeconds);
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private Graph searchSemFges(DataSet Dk, final double penalty) {
        Dk = DataUtils.convertNumericalDiscreteToContinuous(Dk);
        final SemBicScore score = new SemBicScore(new CovarianceMatrix(Dk));
        score.setPenaltyDiscount(penalty);
        final Fges fges = new Fges(score);
        return fges.search();
    }

    private Graph searchBdeuFges(DataSet Dk, final int k) {
        final Discretizer discretizer = new Discretizer(Dk);
        final List<Node> nodes = Dk.getVariables();

        for (final Node node : nodes) {
            if (node instanceof ContinuousVariable) {
                discretizer.equalIntervals(node, k);
            }
        }

        Dk = discretizer.discretize();

        final BDeuScore score = new BDeuScore(Dk);
        score.setSamplePrior(1.0);
        score.setStructurePrior(1.0);
        final Fges fges = new Fges(score);
        return fges.search();
    }

    private Graph searchMixedFges(final DataSet dk, final double penalty) {
        final MixedBicScore score = new MixedBicScore(dk);
        score.setPenaltyDiscount(penalty);
        final Fges fges = new Fges(score);
        return fges.search();
    }

    public Graph searchMGMFges(final DataSet ds, final double penalty) {
        final MGM m = new MGM(ds, new double[]{0.1, 0.1, 0.1});
        //m.setVerbose(this.verbose);
        final Graph gm = m.search();
        final DataSet dataSet = MixedUtils.makeContinuousData(ds);
        final SemBicScore score = new SemBicScore(new CovarianceMatrix(dataSet));
        score.setPenaltyDiscount(penalty);
        final Fges fg = new Fges(score);
        fg.setBoundGraph(gm);
        fg.setVerbose(false);
        return fg.search();
    }

    public DataSet getMixedDataAjStyle(Graph g, final int k, final int samps) {

        final HashMap<String, Integer> nd = new HashMap<>();

        final List<Node> nodes = g.getNodes();

        Collections.shuffle(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            if (i < nodes.size() / 2) {
                nd.put(nodes.get(i).getName(), k);
            } else {
                nd.put(nodes.get(i).getName(), 0);
            }
        }

        g = MixedUtils.makeMixedGraph(g, nd);


        final GeneralizedSemPm pm = MixedUtils.GaussianCategoricalPm(g, "Split(-1.5,-.5,.5,1.5)");
//        System.out.println(pm);

        final GeneralizedSemIm im = MixedUtils.GaussianCategoricalIm(pm);
//        System.out.println(im);

        final DataSet ds = im.simulateDataFisher(samps);
        return MixedUtils.makeMixedData(ds, nd);
    }

    //    @Test
    public void testBestAlgorithms() {
        final String[] algorithms = {"SemFGES", "BDeuFGES", "MixedFGES", "PC", "PCS", "CPC", "MGMFges", "MGMPcs"};
        final String[] statLabels = {"AP", "AR", "OP", "OR", "SUM", "McAdj", "McOr", "F1Adj", "F1Or", "E"};

        final int numMeasures = 30;
        final int numEdges = 60;

        final int numRuns = 50;
        final int maxCategories = 5;
        final int sampleSize = 1000;
        final double penaltyDiscount = 4.0;
        final double ofInterestCutoff = 0.05;

        final double[][][][] allAllRet = new double[maxCategories][][][];
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

            final double[][][] allRet = new double[algorithms.length][][];

            for (int t = 0; t < algorithms.length; t++) {
                allRet[t] = printStats(algorithms, t, numRuns, sampleSize, numMeasures,
                        numCategories, numEdges);
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

        printBestStats(allAllRet, algorithms, statLabels, maxCategories, ofInterestCutoff);
    }

    private double[][] printStats(final String[] algorithms, final int t, final int numRuns,
                                  final int sampleSize, final int numMeasures, final int numCategories,
                                  final int numEdges) {
        final NumberFormat nf = new DecimalFormat("0.00");

        final double[] sumAdjPrecision = new double[4];
        final double[] sumAdjRecall = new double[4];
        final double[] sumArrowPrecision = new double[4];
        final double[] sumArrowRecall = new double[4];
        final double[] sumSum = new double[4];
        final double[] sumMcAdj = new double[4];
        final double[] sumMcOr = new double[4];
        final double[] sumF1Adj = new double[4];
        final double[] sumF1Or = new double[4];
        double totalElapsed = 0.0;

        final int[] countAP = new int[4];
        final int[] countAR = new int[4];
        final int[] countOP = new int[4];
        final int[] countOR = new int[4];
        final int[] countSum = new int[4];
        final int[] countMcAdj = new int[4];
        final int[] countMcOr = new int[4];
        final int[] countF1Adj = new int[4];
        final int[] countF1Or = new int[4];

        for (int i = 0; i < numRuns; i++) {
            final List<Node> nodes = new ArrayList<>();

            for (int r = 0; r < numMeasures; r++) {
                final String name = "X" + (r + 1);
                nodes.add(new ContinuousVariable(name));
            }

            final Graph dag = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numEdges,
                    10, 10, 10, false);
            final DataSet data = getMixedDataAjStyle(dag, numCategories, sampleSize);

            Graph out;
            final double penalty = 4;

            final long start = System.currentTimeMillis();

            switch (t) {
                case 0:
                    out = searchSemFges(data, penalty);
                    break;
                case 1:
                    out = searchBdeuFges(data, numCategories);
                    break;
                case 2:
                    out = searchMixedFges(data, penalty);
                    break;
                case 3:
                    out = searchMGMFges(data, penalty);
                    break;
                default:
                    throw new IllegalStateException();
            }

            out = GraphUtils.replaceNodes(out, dag.getNodes());

            final Graph[] est = new Graph[4];

            est[0] = out;
            est[1] = getSubgraph(out, true, true, data);
            est[2] = getSubgraph(out, true, false, data);
            est[3] = getSubgraph(out, false, false, data);

            final Graph[] truth = new Graph[4];

            truth[0] = dag;
            truth[1] = getSubgraph(dag, true, true, data);
            truth[2] = getSubgraph(dag, true, false, data);
            truth[3] = getSubgraph(dag, false, false, data);

            final long stop = System.currentTimeMillis();

            final long elapsed = stop - start;
            totalElapsed += elapsed;

            for (int u = 0; u < 4; u++) {
                int adjTp = 0;
                int adjFp = 0;
                final int adjTn;
                int adjFn = 0;
                int arrowsTp = 0;
                int arrowsFp = 0;
                int arrowsTn = 0;
                int arrowsFn = 0;

                for (final Edge edge : est[u].getEdges()) {
                    if (truth[u].isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                        adjTp++;
                    } else {
                        adjFp++;
                    }

                    if (edge.isDirected()) {
                        final Edge _edge = truth[u].getEdge(edge.getNode1(), edge.getNode2());

                        if (edge != null && edge.equals(_edge)) {
                            arrowsTp++;
                        } else {
                            arrowsFp++;
                        }
                    }
                }

                final List<Node> nodes1 = truth[u].getNodes();

                for (int w = 0; w < nodes1.size(); w++) {
                    for (final int s = w + 1; w < nodes1.size(); w++) {
                        final Node W = nodes1.get(w);
                        final Node S = nodes1.get(s);

                        if (truth[u].isAdjacentTo(W, S)) {
                            if (!est[u].isAdjacentTo(W, S)) {
                                adjFn++;
                            }

                            final Edge e1 = truth[u].getEdge(W, S);
                            final Edge e2 = est[u].getEdge(W, S);

                            if (!(e2 != null && e2.equals(e1))) {
                                arrowsFn++;
                            }
                        }

                        final Edge e1 = truth[u].getEdge(W, S);
                        final Edge e2 = est[u].getEdge(W, S);

                        if (!(e1 != null && e2 == null) || (e1 != null && e2 != null && !e1.equals(e2))) {
                            arrowsFn++;
                        }
                    }
                }

                final int allEdges = truth[u].getNumNodes() * (truth[u].getNumNodes() - 1);

                adjTn = allEdges / 2 - (adjFn + adjFp + adjTp);
                arrowsTn = allEdges - (arrowsFn + arrowsFp + arrowsTp);

                final double adjPrecision = adjTp / (double) (adjTp + adjFp);
                final double adjRecall = adjTp / (double) (adjTp + adjFn);

                final double arrowPrecision = arrowsTp / (double) (arrowsTp + arrowsFp);
                final double arrowRecall = arrowsTp / (double) (arrowsTp + arrowsFn);

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

                final double sum = adjPrecision + adjRecall + arrowPrecision + arrowRecall;
                final double mcAdj = (adjTp * adjTn - adjFp * adjFn) /
                        Math.sqrt((adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn));
                final double mcOr = (arrowsTp * arrowsTn - arrowsFp * arrowsFn) /
                        Math.sqrt((arrowsTp + arrowsFp) * (arrowsTp + arrowsFn) *
                                (arrowsTn + arrowsFp) * (arrowsTn + arrowsFn));
                final double f1Adj = 2 * (adjPrecision * adjRecall) / (adjPrecision + adjRecall);
                final double f1Arrows = 2 * (arrowPrecision * arrowRecall) / (arrowPrecision + arrowRecall);

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

        final double[] avgAdjPrecision = new double[4];
        final double[] avgAdjRecall = new double[4];
        final double[] avgArrowPrecision = new double[4];
        final double[] avgArrowRecall = new double[4];
        final double[] avgSum = new double[4];
        final double[] avgMcAdj = new double[4];
        final double[] avgMcOr = new double[4];
        final double[] avgF1Adj = new double[4];
        final double[] avgF1Or = new double[4];
        final double[] avgElapsed = new double[4];

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
            avgElapsed[u] = -totalElapsed / (double) numRuns;
        }

        final double[][] ret = new double[][]{
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
            final String header = getHeader(u);

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

    private String getHeader(final int u) {
        final String header;

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

    private void printBestStats(final double[][][][] allAllRet, final String[] algorithms, final String[] statLabels,
                                final int maxCategories, final double ofInterestCutoff) {
        final TextTable table = new TextTable(allAllRet.length + 1, allAllRet[0][0].length + 1);


        class Pair {
            private final String algorithm;
            private final double stat;

            public Pair(final String algorithm, final double stat) {
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
            for (int numCategories = 2; numCategories <= maxCategories; numCategories++) {

                table.setToken(numCategories - 1, 0, numCategories + "");

                for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {
//                double maxStat = Double.NaN;
                    String maxAlg = "-";

                    final List<Pair> algStats = new ArrayList<>();

                    for (int t = 0; t < algorithms.length; t++) {
                        final double stat = allAllRet[numCategories - 2][t][statIndex][u];
                        if (!Double.isNaN(stat)) {
                            algStats.add(new Pair(algorithms[t], stat));
                        }
                    }

                    if (algStats.isEmpty()) {
                        maxAlg = "-";
                    } else {
                        Collections.sort(algStats, new Comparator<Pair>() {

                            @Override
                            public int compare(final Pair o1, final Pair o2) {
                                return -Double.compare(o1.getStat(), o2.getStat());
                            }
                        });

                        final double maxStat = algStats.get(0).getStat();
                        maxAlg = algStats.get(0).getAlgorithm();

                        final double minStat = algStats.get(algStats.size() - 1).getStat();

                        final double diff = maxStat - minStat;
                        final double ofInterest = maxStat - ofInterestCutoff * (diff);

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


        final NumberFormat nf = new DecimalFormat("0.00");

        System.out.println();
        System.out.println("Details:");
        System.out.println();
        System.out.println("Average statistics");

        for (int u = 0; u < 4; u++) {
            System.out.println();
            System.out.println(getHeader(u));
            System.out.println();

            for (int numCategories = 2; numCategories <= maxCategories; numCategories++) {
                System.out.println("\n# categories = " + numCategories);

                for (int t = 0; t < algorithms.length; t++) {
                    final String algorithm = algorithms[t];

                    System.out.println("\nAlgorithm = " + algorithm);
                    System.out.println();

                    for (int statIndex = 0; statIndex < allAllRet[numCategories - 2][0].length; statIndex++) {
                        final String statLabel = statLabels[statIndex];
                        final double stat = allAllRet[numCategories - 2][t][statIndex][u];
                        System.out.println("\tAverage" + statLabel + " = " + nf.format(stat));
                    }
                }
            }
        }

    }

    //    @Test
    public void test7() {
        for (int i = 0; i < 10; i++) {

            final Graph graph = GraphUtils.randomGraph(10, 0,
                    10, 10, 10, 10, false);
            final SemPm semPm = new SemPm(graph);
            final SemIm semIm = new SemIm(semPm);
            final DataSet dataSet = semIm.simulateData(1000, false);

            final Fges fges = new Fges(new SemBicScore(new CovarianceMatrix(dataSet)));
            final Graph CPDAG = fges.search();

            final Graph dag = dagFromCPDAG(CPDAG);

            assertFalse(dag.existsDirectedCycle());
        }
    }

    private Graph dagFromCPDAG(final Graph CPDAG) {
        final Graph dag = new EdgeListGraph(CPDAG);

        final MeekRules rules = new MeekRules();

        WHILE:
        while (true) {
            final List<Edge> edges = new ArrayList<>(dag.getEdges());

            for (final Edge edge : edges) {
                if (Edges.isUndirectedEdge(edge)) {
                    final Node x = edge.getNode1();
                    final Node y = edge.getNode2();

                    final List<Node> okx = dag.getAdjacentNodes(x);
                    okx.removeAll(dag.getChildren(x));
                    okx.remove(y);

                    final List<Node> oky = dag.getAdjacentNodes(y);
                    oky.removeAll(dag.getChildren(y));
                    oky.remove(x);

                    if (!okx.isEmpty()) {
                        final Node other = okx.get(0);
                        dag.removeEdge(other, x);
                        dag.removeEdge(y, x);
                        dag.addDirectedEdge(other, x);
                        dag.addDirectedEdge(y, x);
                    } else if (!oky.isEmpty()) {
                        final Node other = oky.get(0);
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

        final Parameters parameters = new Parameters();

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

        final RandomGraph graph = new RandomForward();
        final LinearFisherModel sim = new LinearFisherModel(graph);
        sim.createData(parameters, false);
        Graph previous = null;
        int prevDiff = Integer.MAX_VALUE;

//        for (int l = 7; l >= 1; l--) {
        for (int i = 2; i <= 20; i++) {
            parameters.set(Params.PENALTY_DISCOUNT, i / (double) 10);
//            parameters.set("alpha", Double.parseDouble("1E-" + l));

//            ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
//            Algorithm alg = new edu.cmu.tetrad.algcomparison.algorithm.oracle.CPDAG.Fges(score);

            final ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            final Algorithm alg = new CPC(new FisherZ());

            final Graph out = alg.search(sim.getDataModel(0), parameters);
//            Graph out = GraphUtils.undirectedGraph(alg.search(sim.getDataModel(0), parameters));

            final Set<Edge> edges1 = out.getEdges();

            final int numEdges = edges1.size();

            if (previous != null) {
                final Set<Edge> edges2 = previous.getEdges();
                edges2.removeAll(edges1);
                final int diff = edges2.size();
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
        final Graph trueGraph = sim.getTrueGraph(0);

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        final Statistic ap = new AdjacencyPrecision();
        final Statistic ar = new AdjacencyRecall();
        final Statistic ahp = new ArrowheadPrecision();
        final Statistic ahr = new ArrowheadRecall();

        System.out.println("AP = " + ap.getValue(trueGraph, estGraph, null));
        System.out.println("AR = " + ar.getValue(trueGraph, estGraph, null));
        System.out.println("AHP = " + ahp.getValue(trueGraph, estGraph, null));
        System.out.println("AHR = " + ahr.getValue(trueGraph, estGraph, null));
    }

    @Test
    public void testSemBicDiffs() {
        final int N = 1000;
        final int numCond = 3;

        final Graph graph = GraphUtils.randomGraph(10, 0, 20, 100,
                100, 100, false);
        final List<Node> nodes = graph.getNodes();
        buildIndexing(nodes);
        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);
        final DataSet dataSet = im.simulateData(N, false);
        final SemBicScore score = new SemBicScore(dataSet);

        final IndTestDSep dsep = new IndTestDSep(graph);
        int count = 1;

        for (int i = 0; i < 10000; i++) {
            Collections.shuffle(nodes);

            final Node x = nodes.get(0);
            final Node y = nodes.get(1);
            final Set<Node> z = new HashSet<>();

            for (int c = 3; c <= 2 + numCond; c++) {
                z.add(nodes.get(c));
            }

            final boolean _dsep = dsep.isIndependent(x, y, new ArrayList<>(z));
            final double diff = scoreGraphChange(x, y, z, this.hashIndices, score);
            final boolean diffNegative = diff < 0;

            if (!_dsep && _dsep != diffNegative) {
                System.out.println(count++ + "\t" + (_dsep ? "dsep" : "dconn") + "\t" + (diffNegative ? "indep" : "dep") + "\tdiff = " + diff);
            }
        }

    }

    private double scoreGraphChange(final Node x, final Node y, final Set<Node> parents,
                                    final Map<Node, Integer> hashIndices, final SemBicScore score) {
        final int yIndex = hashIndices.get(y);

        if (x == y) {
            throw new IllegalArgumentException();
        }
        if (parents.contains(y)) {
            throw new IllegalArgumentException();
        }

        if (parents.contains(x)) {
            throw new IllegalArgumentException();
        }

        final int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (final Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        return score.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private HashMap<Node, Integer> hashIndices;

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(final List<Node> nodes) {
        this.hashIndices = new HashMap<>();

        int i = -1;

        for (final Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    public static void main(final String... args) {
        if (args.length > 0) {
            final int numMeasures = Integer.parseInt(args[0]);
            final int avgDegree = Integer.parseInt(args[1]);

            final Parameters parameters = new Parameters();

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

            final RandomGraph graph = new RandomForward();
            final LinearFisherModel sim = new LinearFisherModel(graph);
            sim.createData(parameters, false);
            final ScoreWrapper score = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            final Algorithm alg = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(score);

            parameters.set(Params.ALPHA, 1e-8);

            for (int i = 0; i < 5; i++) {
                final Graph out1 = alg.search(sim.getDataModel(0), parameters);
                System.out.println(out1);
            }

        } else {
            new TestFges().test9();
        }
    }

}




