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
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.TextTable;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestPcMax extends TestCase {

    private PrintStream out = System.out;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestPcMax(String name) {
        super(name);
    }

    public void testPcMax() {
        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;
        double alpha = .005;

        final int numEdges = (int) (numVars * edgesPerNode);

        out.println("Tests performance of the PCT algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making dag");
        Graph dag;
        int[] causalOrdering = new int[vars.size()];

        dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges);
//        dag = DataGraphUtils.scaleFreeGraph(vars, 0, 0.05, 0.05, 5, 15);
        printDegreeDistribution(dag, System.out);

        for (int i = 0; i < vars.size(); i++) {
            causalOrdering[i] = i;
        }

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");

        LargeSemSimulator simulator = new LargeSemSimulator(dag, vars, causalOrdering);
        simulator.setOut(out);
        DataSet data = simulator.simulateDataAcyclic(numCases);

//        SemPm pm = new SemPm(dag);
//        SemIm im = new SemIm(pm);
//        DataSet data = im.simulateData(numCases, false);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        System.out.println("Making covariance matrix");

//        ICovarianceMatrix cov = new CovarianceMatrix(data);
        ICovarianceMatrix cov = new CovarianceMatrixOnTheFly(data);

        System.out.println("Covariance matrix done");


        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms\n");

        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);
//        final IndependenceTest test = new IndTestRegressionAD(data, alpha);
        PcMax pc = new PcMax(test);
        pc.setTrueDag(dag);
        pc.setVerbose(true);
        pc.setDepth(3);

        System.out.println("\nStarting PCMax");

        Graph estPattern = pc.search();

        System.out.println("Done with PCMax");

        printDegreeDistribution(estPattern, System.out);

        long time4 = System.currentTimeMillis();

        out.println(new Date());

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + numEdges);
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running PCT/PCT) " + (time4 - time3) + " ms");
        out.println("Elapsed (cov + PCT/PCT) " + (time4 - time2) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        out.println("# edges in true pattern = " + truePattern.getNumEdges());
        out.println("# edges in est pattern = " + estPattern.getNumEdges());

        graphComparison(estPattern, truePattern);
    }

    public void testPcMaxDiscrete() {
        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;
        double alpha = .01;

        final int numEdges = (int) (numVars * edgesPerNode);

        out.println("Tests performance of the PCT algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making dag");
        Graph dag;

        dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, numEdges);
//        dag = DataGraphUtils.scaleFreeGraph(vars, 0, 0.05, 0.05, 5, 15);
        printDegreeDistribution(dag, System.out);

        BayesPm pm = new BayesPm(dag, 2, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);

        DataSet data = im.simulateData(numCases, false);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        long time3 = System.currentTimeMillis();

        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms\n");

//        final IndependenceTest test = new ProbabilisticMAPIndependence(data);
        final IndependenceTest test = new IndTestChiSquare(data, alpha);
        PcMax pc = new PcMax(test);
        pc.setVerbose(true);
        pc.setDepth(-1);

        System.out.println("\nStarting PCMax");

        Graph estPattern = pc.search();

        System.out.println("Done with PCMax");

        printDegreeDistribution(estPattern, System.out);

        long time4 = System.currentTimeMillis();

        out.println(new Date());

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + numEdges);
        out.println("# Cases = " + numCases);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running PCT/PCT) " + (time4 - time3) + " ms");
        out.println("Elapsed (cov + PCT/PCT) " + (time4 - time2) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        out.println("# edges in true pattern = " + truePattern.getNumEdges());
        out.println("# edges in est pattern = " + estPattern.getNumEdges());

        graphComparison(estPattern, truePattern);
    }


    private int[][] graphComparison(Graph estPattern, Graph truePattern) {
        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison(estPattern, truePattern);

        out.println("Adjacencies:");
        out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

        out.println("Arrow Orientations:");
        out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

        int[][] counts = edgeMisclassificationCounts(truePattern, estPattern);
        out.println(edgeMisclassifications(counts));

        return counts;
    }

    public static String edgeMisclassifications1(int[][] counts) {
        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
//        table2.setToken(4, 0, "<-o");
        table2.setToken(4, 0, "-->");
//        table2.setToken(6, 0, "<--");
        table2.setToken(5, 0, "<->");
        table2.setToken(6, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 5 && j == 5) table2.setToken(i + 1, j + 1, "*");
                else
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        builder.append(table2.toString());

        return builder.toString();
    }

    private static int[][] edgeMisclassificationCounts1(Graph leftGraph, Graph topGraph) {
        topGraph = GraphUtils.replaceNodes(topGraph, leftGraph.getNodes());

        int[][] counts = new int[6][6];

        for (Edge est : topGraph.getEdges()) {
            Node x = est.getNode1();
            Node y = est.getNode2();

            Edge left = leftGraph.getEdge(x, y);

            Edge top = topGraph.getEdge(x, y);

            int m = getType1(left);
            int n = getType1(top);

            counts[m][n]++;
        }

        System.out.println("# edges in true graph = " + leftGraph.getNumEdges());
        System.out.println("# edges in est graph = " + topGraph.getNumEdges());

        for (Edge edge : leftGraph.getEdges()) {
            if (topGraph.getEdge(edge.getNode1(), edge.getNode2()) == null) {
                int m = getType1(edge);
                counts[m][5]++;
            }
        }

        return counts;
    }

    private static int getType1(Edge edge) {
        if (edge == null) {
            return 5;
        }

        Endpoint e1 = edge.getEndpoint1();
        Endpoint e2 = edge.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.CIRCLE) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.TAIL) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 4;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
    }


    public static String edgeMisclassifications(int[][] counts) {
        if (false) {
            return edgeMisclassifications1(counts);
        }

        StringBuilder builder = new StringBuilder();

        TextTable table2 = new TextTable(9, 7);

        table2.setToken(1, 0, "---");
        table2.setToken(2, 0, "o-o");
        table2.setToken(3, 0, "o->");
        table2.setToken(4, 0, "<-o");
        table2.setToken(5, 0, "-->");
        table2.setToken(6, 0, "<--");
        table2.setToken(7, 0, "<->");
        table2.setToken(8, 0, "No Edge");
        table2.setToken(0, 1, "---");
        table2.setToken(0, 2, "o-o");
        table2.setToken(0, 3, "o->");
        table2.setToken(0, 4, "-->");
        table2.setToken(0, 5, "<->");
        table2.setToken(0, 6, "No Edge");

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 6; j++) {
                if (i == 7 && j == 5) table2.setToken(i + 1, j + 1, "*");
                else
                    table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        builder.append(table2.toString());

        int correctEdges = 0;
        int estimatedEdges = 0;

        for (int i = 0; i < counts.length; i++) {
            for (int j = 0; j < counts[0].length - 1; j++) {
                if ((i == 0 && j == 0) || (i == 1 && j == 1) || (i == 2 && j == 2) || (i == 4 && j == 3) || (i == 6 && j == 4)) {
                    correctEdges += counts[i][j];
                }

                estimatedEdges += counts[i][j];
            }
        }

        NumberFormat nf = new DecimalFormat("0.00");

        builder.append("\nRatio correct edges to estimated edges = " + nf.format((correctEdges / (double) estimatedEdges)));

        return builder.toString();
    }

    private static int[][] edgeMisclassificationCounts(Graph leftGraph, Graph topGraph) {
        if (false) {
            return edgeMisclassificationCounts1(leftGraph, topGraph);
        }

        topGraph = GraphUtils.replaceNodes(topGraph, leftGraph.getNodes());

        int[][] counts = new int[8][6];

        for (Edge est : topGraph.getEdges()) {
            Node x = est.getNode1();
            Node y = est.getNode2();

            Edge left = leftGraph.getEdge(x, y);

            Edge top = topGraph.getEdge(x, y);

            int m = getTypeLeft(left, top);
            int n = getTypeTop(top);

            counts[m][n]++;
        }

        System.out.println("# edges in true graph = " + leftGraph.getNumEdges());
        System.out.println("# edges in est graph = " + topGraph.getNumEdges());

        for (Edge edgeLeft : leftGraph.getEdges()) {
            final Edge edgeTop = topGraph.getEdge(edgeLeft.getNode1(), edgeLeft.getNode2());
            if (edgeTop == null) {
                int m = getTypeLeft(edgeLeft, edgeLeft);
                counts[m][5]++;
            }
        }

        return counts;
    }

    private static int getTypeTop(Edge edgeTop) {
        if (edgeTop == null) {
            return 5;
        }

        Endpoint e1 = edgeTop.getEndpoint1();
        Endpoint e2 = edgeTop.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 4;
        }

        throw new IllegalArgumentException("Unsupported edgeTop type : " + e1 + " " + e2);
    }


    private static int getTypeLeft(Edge edgeLeft, Edge edgeTop) {
        if (edgeLeft == null) {
            return 7;
        }

        Endpoint e1 = edgeLeft.getEndpoint1();
        Endpoint e2 = edgeLeft.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW && edgeTop.equals(edgeLeft.reverse())) {
            return 3;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW && edgeTop.equals(edgeLeft.reverse())) {
            return 5;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 4;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
    }

    private static int getIndex(Endpoint endpoint) {
        if (endpoint == Endpoint.CIRCLE) return 0;
        if (endpoint == Endpoint.ARROW) return 1;
        if (endpoint == Endpoint.TAIL) return 2;
        if (endpoint == null) return 3;
        throw new IllegalArgumentException();
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
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestPcMax.class);
    }
}





