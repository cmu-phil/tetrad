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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.*;

/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestPc extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestPc(String name) {
        super(name);
    }


    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
        RandomUtil.getInstance().setSeed(-1857293L);

    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    public void testSearch2() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    public void testSearch3() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "A-->D,A---B,B-->D,C-->D,D-->E");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    public void testSearch4() {
        IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A-->B,C-->B,A-->D,C-->D",
                knowledge);
    }

    public void testShowInefficiency() {

        int numVars = 20;
        int numEdges = 20;
        int maxSample = 2000;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 7,
                5, 5, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        SemPm semPm = new SemPm(trueGraph);
        SemIm semIm = new SemIm(semPm);
        DataSet _dataSet = semIm.simulateData(maxSample, false);
        Graph previousResult = null;

        int[] rows = new int[maxSample];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        DataSet dataSet = _dataSet.subsetRows(rows);
        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);

        Pc search = new Pc(test);
        Graph resultGraph = search.search();

        if (previousResult != null) {
            Set<Edge> resultEdges = resultGraph.getEdges();
            Set<Edge> previousEdges = previousResult.getEdges();

            List<Edge> addedEdges = new LinkedList<Edge>();

            for (Edge edge : resultEdges) {
                if (!previousEdges.contains(edge)) {
                    addedEdges.add(edge);
                }
            }

            List<Edge> removedEdges = new LinkedList<Edge>();

            for (Edge edge : previousEdges) {
                if (!resultEdges.contains(edge)) {
                    removedEdges.add(edge);
                }
            }

            if (!addedEdges.isEmpty() && !removedEdges.isEmpty()) {
                System.out.println("\nn = " + maxSample + ":");

                if (!addedEdges.isEmpty()) {
                    System.out.println("Added: " + addedEdges);
                }

                if (!removedEdges.isEmpty()) {
                    System.out.println("Removed: " + removedEdges);
                }
            }
        }

        System.out.println("Final graph = " + previousResult);
    }

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

        int length = citesString.length();
        char[] citesChars = citesString.toCharArray();
        DataReader reader = new DataReader();
        ICovarianceMatrix dataSet = reader.parseCovariance(citesChars);
        System.out.println(dataSet);


        IKnowledge knowledge = new Knowledge2();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        Iterator iterator = knowledge.forbiddenEdgesIterator();

        while (iterator.hasNext()) {
            System.out.println(iterator.next());
        }


        Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.11));
        pc.setKnowledge(knowledge);

        Graph pattern = pc.search();

        System.out.println("Pattern = " + pattern);
    }

    public void test7() {
        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(1000, false);
        Pc pc = new Pc(new IndTestFisherZ(data, 0.05));
        Graph pattern = pc.search();
        System.out.println(pattern);
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new IndTestDSep(graph);
        Pc pc = new Pc(independence);

        // Run search
//        Graph resultGraph = pc.search();
        Graph resultGraph = pc.search(new FasStableConcurrent(independence), independence.getVariables());

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
        System.out.println("\nInput graph:");
        System.out.println(graph);
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
        System.out.println("\nTrue graph:");
        System.out.println(trueGraph);

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch2(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(1000, false);

        // Set up search.
//        IndependenceTest independence = new IndTestDSep(graph);
        IndependenceTest independence = new IndTestFisherZ(dataSet, 0.05);
//        IndependenceTest independence = new IndTestFisherZBootstrap(dataSet, 0.001, 15, 1000);

        GraphSearch pcSearch = new Pc(independence);
//        GraphSearch pcSearch = new Npc(independence, knowledge);

        // Run search
        Graph resultGraph = pcSearch.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
        System.out.println("\nInput graph:");
        System.out.println(graph);
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
        System.out.println("\nTrue graph:");
        System.out.println(trueGraph);

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(String inputGraph, String outputGraph,
                                    IKnowledge knowledge) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);
        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(1000, false);

        // Set up search.
//        IndependenceTest independence = new IndTestGraph(graph);
        IndependenceTest independence = new IndTestFisherZ(dataSet, 0.001);
        Pc pcSearch = new Pc(independence);
        pcSearch.setKnowledge(knowledge);

        // Run search
        Graph resultGraph = pcSearch.search();

        // Build comparison graph.
//        Graph trueGraph = GraphConverter.convert(outputGraph);
        GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
//        System.out.println("\nKnowledge:");
        System.out.println(knowledge);
        System.out.println("\nInput graph:");
        System.out.println(graph);
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
//        System.out.println("\nTrue graph:");
//        System.out.println(trueGraph);

        // Do test.
//        assertTrue(resultGraph.equals(trueGraph));
    }

    public void rtest5() {
        Graph graph = new Dag(GraphUtils.randomGraph(20, 0, 20, 3,
                2, 2, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);
        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);

//        Pc pc = new Pc(test);
//        pc.search();

        allAtDepth(test, 3);
    }

    private void allAtDepth(IndependenceTest test, int depth) {
//        System.out.println("depth = " + depth);
        List<Double> pValues = new ArrayList<Double>();

        List<Node> nodes = new LinkedList<Node>(test.getVariables());

        for (int d = 0; d <= depth; d++) {
            for (Node x : nodes) {

//            System.out.println("Adjacent nodes for " + x + " = " + b);
//            System.out.println("Depth = " + depth);

                for (Node y : nodes) {
                    if (x == y) continue;

                    ChoiceGenerator cg = new ChoiceGenerator(nodes.size(), d);
                    int[] choice;

                    while ((choice = cg.next()) != null) {
                        List<Node> condSet = GraphUtils.asList(choice, nodes);

                        if (condSet.contains(x) || condSet.contains(y)) continue;

                        test.isIndependent(x, y, condSet);

                        pValues.add(test.getPValue());

                        System.out.println("FDR cutoff = " + StatUtils.fdrCutoff(test.getAlpha(), pValues, false));
                    }
                }
            }
        }
    }

//    public void testPcStable() {
//        try {
//            String dir = "/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2014.02.20/erich";
//            String filename = "data1.txt";
//
//            File file = new File(dir, filename);
//
//            DataReader reader = new DataReader();
//            reader.setVariablesSupplied(true);
//            reader.setDelimiter(DelimiterType.WHITESPACE);
//
//            DataSet data = reader.parseTabular(file);
//            double alpha = 0.001;
//
//            IndependenceTest test = new IndTestFisherZ(data, alpha);
//
//            IKnowledge knowledge = new Knowledge2();
//            knowledge.setForbidden("X1", "X2");
//            knowledge.setRequired("X3", "X4");
//
//            knowledge.addToTier(1, "X5");
//            knowledge.addToTier(1, "X8");
//            knowledge.addToTier(2, "X6");
//            knowledge.addToTier(2, "X7");
//
//            PcStable pc = new PcStable(test);
//            pc.setKnowledge(knowledge);
//            Graph graph = pc.search();
//
//            System.out.println(graph);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }

    public void testPcStable2() {
        Graph graph = GraphUtils.randomGraph(50, 50, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(200, false);

        TetradLogger.getInstance().setForceLog(false);
        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        PcStable pc = new PcStable(test);
        pc.setVerbose(false);
        Graph pattern = pc.search();

        for (int i = 0; i < 100; i++) {
            DataSet data2 = DataUtils.reorderColumns(data);
            IndependenceTest test2 = new IndTestFisherZ(data2, 0.05);
            PcStable pc2 = new PcStable(test2);
            pc2.setVerbose(false);
            Graph pattern2 = pc2.search();
            System.out.println(pattern.equals(pattern2));
        }
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestPc.class);
    }
}





