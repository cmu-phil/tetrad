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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Joseph Ramsey
 */
public class TestFgs {


    private PrintStream out = System.out;
//    private OutputStream out =

    @Test
    public void explore1() {
        RandomUtil.getInstance().setSeed(1450184147770L);

        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;
        double penaltyDiscount = 2.0;

        final int numEdges = (int) (numVars * edgesPerNode);

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges, 30, 15, 15, false);
//        printDegreeDistribution(dag, System.out);

        int[] causalOrdering = new int[vars.size()];

        for (int i = 0; i < vars.size(); i++) {
            causalOrdering[i] = i;
        }

        LargeSemSimulator simulator = new LargeSemSimulator(dag, vars, causalOrdering);
        simulator.setOut(out);
        DataSet data = simulator.simulateDataAcyclic(numCases);

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);

        Fgs fgs = new Fgs(cov);
        fgs.setVerbose(false);
        fgs.setNumPatternsToStore(0);
        fgs.setPenaltyDiscount(penaltyDiscount);
        fgs.setOut(out);
        fgs.setFaithfulnessAssumed(true);
        fgs.setDepth(1);
        fgs.setCycleBound(5);

        Graph estPattern = fgs.search();

//        printDegreeDistribution(estPattern, out);

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        int[][] counts = SearchGraphUtils.graphComparison(estPattern, truePattern, null);

        int[][] expectedCounts = {
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
//
//        System.out.println(MatrixUtils.toString(expectedCounts));
//        System.out.println(MatrixUtils.toString(counts));

    }

    @Test
    public void explore2() {
        RandomUtil.getInstance().setSeed(1457220623122L);

        int numVars = 20;
        double edgeFactor = 1.0;
        int numCases = 1000;
        double structurePrior = 1;
        double samplePrior = 1;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor),
                30, 15, 15, false);
//        printDegreeDistribution(dag, out);

        BayesPm pm = new BayesPm(dag, 2, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(numCases, false);

//        out.println("Finishing simulation");

        Fgs ges = new Fgs(data);
        ges.setVerbose(false);
        ges.setNumPatternsToStore(0);
        ges.setFaithfulnessAssumed(false);
//        ges.setDepth(3);

        ges.setStructurePrior(structurePrior);
        ges.setSamplePrior(samplePrior);

        Graph estPattern = ges.search();

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        int[][] counts = SearchGraphUtils.graphComparison(estPattern, truePattern, null);

        int[][] expectedCounts = {
                {2, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 15, 0, 1},
                {0, 0, 0, 1, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
        };

        for (int i = 0; i < counts.length; i++) {
            assertTrue(Arrays.equals(counts[i], expectedCounts[i]));
        }

//        System.out.println(MatrixUtils.toString(expectedCounts));
//        System.out.println(MatrixUtils.toString(counts));
//        System.out.println(RandomUtil.getInstance().getSeed());
    }


    @Test
    public void testExplore3() {
        Graph graph = GraphConverter.convert("A-->B,A-->C,B-->D,C-->D");
        Fgs fgs = new Fgs(new GraphScore(graph));
        fgs.setPenaltyDiscount(2);
        Graph pattern = fgs.search();
        assertEquals(SearchGraphUtils.patternForDag(graph), pattern);
    }

    @Test
    public void testExplore4() {
        Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,B-->E,C-->E,D-->E");
        Fgs fgs = new Fgs(new GraphScore(graph));
        fgs.setPenaltyDiscount(2);
        Graph pattern = fgs.search();
        assertEquals(SearchGraphUtils.patternForDag(graph), pattern);
    }

    @Test
    public void testExplore5() {
        Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
        Fgs fgs = new Fgs(new GraphScore(graph));
        fgs.setFaithfulnessAssumed(false);
        fgs.setPenaltyDiscount(2);
        Graph pattern = fgs.search();
        assertEquals(SearchGraphUtils.patternForDag(graph), pattern);
    }

    @Test
    public void testExplore6() {
        Graph graph = GraphConverter.convert("A-->B,A-->C,A-->D,A->E,B-->F,C-->F,D-->F,E-->F");
        Fgs fgs = new Fgs(new GraphScore(graph));
        fgs.setFaithfulnessAssumed(false);
        fgs.setPenaltyDiscount(1);
        Graph pattern = fgs.search();
    }

    @Test
    public void testFromGraph() {
        int numNodes = 6;
        int numIterations = 1;

        for (int i = 0; i < numIterations; i++) {
//            System.out.println("Iteration " + (i + 1));
            Graph dag = GraphUtils.randomDag(numNodes, 0, numNodes, 10, 10, 10, false);
            Fgs fgs = new Fgs(new GraphScore(dag));
            Graph pattern1 = fgs.search();
            Fgs pc = new Fgs(new GraphScore(dag));
            Graph pattern2 = pc.search();
            assertEquals(pattern2, pattern1);
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
            names.put(i, new ArrayList<Node>());
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
        IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", /*"A---B,B-->C,D",*/"A---B,B-->C,A---D,C-->D,A---C",
                knowledge);
    }

    @Test
    public void testSearch5() {
        IKnowledge knowledge = new Knowledge2();
        knowledge.setTier(1, Collections.singletonList("A"));
        knowledge.setTier(2, Collections.singletonList("B"));

        checkWithKnowledge("A-->B", "A-->B", knowledge);
    }

    @Test
    public void testCites() {
        String citesString = "164\n" +
                "ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS\n" +
                "1.0\n" +
                ".62\t1.0\n" +
                ".25\t.09\t1.0\n" +
                ".16\t.28\t.07\t1.0\n" +
                "-.10\t.00\t.03\t.10\t1.0\n" +
                ".29\t.25\t.34\t.37\t.13\t1.0\n" +
                ".18\t.15\t.19\t.41\t.43\t.55\t1.0";

        char[] citesChars = citesString.toCharArray();
        DataReader reader = new DataReader();
        ICovarianceMatrix dataSet = reader.parseCovariance(citesChars);

        IKnowledge knowledge = new Knowledge2();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        Fgs fgs = new Fgs(new SemBicScore(dataSet, 1));
        fgs.setKnowledge(knowledge);

        Graph pattern = fgs.search();

//        System.out.println(pattern);

        String trueString = "Graph Nodes:\n" +
                "ABILITY GPQ PREPROD QFJ SEX CITES PUBS \n" +
                "\n" +
                "Graph Edges: \n" +
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
        } catch (IOException e) {
            e.printStackTrace();
        }

        assertEquals(pattern, trueGraph);
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        Fgs fgs = new Fgs(new GraphScore(graph));

        // Run search
        Graph resultGraph = fgs.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

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
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(String inputGraph, String answerGraph,
                                    IKnowledge knowledge) {
        // Set up graph and node objects.
        Graph input = GraphConverter.convert(inputGraph);

        // Set up search.
        Fgs fgs = new Fgs(new GraphScore(input));

        // Set up search.
        fgs.setKnowledge(knowledge);

        // Run search
        Graph result = fgs.search();

        // Build comparison graph.
        Graph answer = GraphConverter.convert(answerGraph);
//        Graph answer = new Pc(new IndTestDSep(input)).search();

        System.out.println("Input = " + input);
        System.out.println("Knowledge = " + knowledge);
        System.out.println("Answer = " + answer);
        System.out.println("Result graph = " + result);

        // Do test.
        assertEquals(answer, result);
    }

    @Test
    public void testPcStable2() {
        RandomUtil.getInstance().setSeed(1450030184196L);
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = GraphUtils.randomGraph(nodes, 0, 10, 30, 15, 15, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(200, false);

        TetradLogger.getInstance().setForceLog(false);
        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        PcStable pc = new PcStable(test);
        pc.setVerbose(false);
        Graph pattern = pc.search();

        for (int i = 0; i < 1; i++) {
            DataSet data2 = DataUtils.reorderColumns(data);
            IndependenceTest test2 = new IndTestFisherZ(data2, 0.05);
            PcStable pc2 = new PcStable(test2);
            pc2.setVerbose(false);
            Graph pattern2 = pc2.search();
            assertTrue(pattern.equals(pattern2));
        }
    }

    @Test
    public void test22() {
        Graph graph = GraphUtils.randomGraph(100, 0, 200, 10, 10, 10, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        Fgs fgs = new Fgs(data);
        Graph fgsGraph = fgs.search();
        Graph dag = SearchGraphUtils.dagFromPattern(fgsGraph);

//        System.out.println("score1 = " + fgs.scoreDag(dag));

        graph = GraphUtils.replaceNodes(graph, dag.getNodes());

//        System.out.println("Score2 = " + fgs.scoreDag(graph));
    }
}




