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
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.TextTable;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.round;

/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestFgs extends TestCase {

    private PrintStream out = System.out;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestFgs(String name) {
        super(name);
    }

    public void testFgs() {
        int numVars = 1000;
        double edgesPerNode = 2.0;
        int numCases = 1000;
        double penaltyDiscount = 4.0;

        final int numEdges = (int) (numVars * edgesPerNode);

        out.println("Tests performance of the GES algorithm");

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

        Fgs ges = new Fgs(cov);
        ges.setVerbose(false);
        ges.setLog(false);
        ges.setNumPatternsToStore(0);
        ges.setPenaltyDiscount(penaltyDiscount);
        ges.setOut(System.out);
        ges.setFaithfulnessAssumed(true);
        ges.setDepth(1);
        ges.setCycleBound(5);

        System.out.println("\nStarting GES");

        Graph estPattern = ges.search();

        System.out.println("Done with GES");

        printDegreeDistribution(estPattern, System.out);

        long time4 = System.currentTimeMillis();

        out.println(new Date());

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + numEdges);
        out.println("# Cases = " + numCases);
        out.println("Penalty discount = " + penaltyDiscount);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running GES/GES) " + (time4 - time3) + " ms");
        out.println("Elapsed (cov + GES/GES) " + (time4 - time2) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        out.println("# edges in true pattern = " + truePattern.getNumEdges());
        out.println("# edges in est pattern = " + estPattern.getNumEdges());

        graphComparison(estPattern, truePattern);
    }

    public void testFgsDiscrete() {
        int numVars = 40;
        double edgeFactor = 1.0;
        int numCases = 1000;
        double structurePrior = .01;
        double samplePrior = 10;

        out.println("Tests performance of the GES algorithm");

        long time1 = System.currentTimeMillis();

        System.out.println("Making list of vars");

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        System.out.println("Finishing list of vars");

        System.out.println("Making dag");

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgeFactor));
//        Graph dag = DataGraphUtils.randomDagPreferentialAttachment(vars, 0, (int) (numVars * edgeFactor), .01);
        printDegreeDistribution(dag, System.out);

        System.out.println("Graph done");

        out.println("Graph done");

        System.out.println("Starting simulation");
        List<Node> nodes = dag.getNodes();
        int[] tierOrdering = new int[nodes.size()];

        for (int i = 0; i < nodes.size(); i++) {
            tierOrdering[i] = i;
        }

        BayesPm pm = new BayesPm(dag, 2, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(numCases, false);

        System.out.println("Finishing simulation");

        long time2 = System.currentTimeMillis();

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");

        long time3 = System.currentTimeMillis();

        Fgs ges = new Fgs(data);
        ges.setVerbose(true);
        ges.setLog(false);
        ges.setNumPatternsToStore(0);
        ges.setOut(out);
        ges.setFaithfulnessAssumed(false);
        ges.setDepth(3);

        ges.setStructurePrior(structurePrior);
        ges.setSamplePrior(samplePrior);

        System.out.println("\nStarting GES");

        Graph estPattern = ges.search();

        System.out.println("Done with GES");

        out.println(estPattern);

        long time4 = System.currentTimeMillis();

        out.println(new Date());

        out.println("# Vars = " + numVars);
        out.println("# Edges = " + (int) (numVars * edgeFactor));
        out.println("# Cases = " + numCases);
        out.println("Structure prior = " + structurePrior);
        out.println("Sample prior = " + samplePrior);

        out.println("Elapsed (simulating the data): " + (time2 - time1) + " ms");
        out.println("Elapsed (calculating cov): " + (time3 - time2) + " ms");
        out.println("Elapsed (running GES/GES) " + (time4 - time3) + " ms");
        out.println("Elapsed (cov + GES/GES) " + (time4 - time2) + " ms");

        final Graph truePattern = SearchGraphUtils.patternForDag(dag);

        out.println("# edges in true pattern = " + truePattern.getNumEdges());
        out.println("# edges in est pattern = " + estPattern.getNumEdges());

        graphComparison(estPattern, truePattern);
        printDegreeDistribution(estPattern, System.out);

        System.out.println("See outout file.");
    }


    private double[] printCorrectArrows(Graph dag, Graph outGraph, Graph truePag) {
        int correctArrows = 0;
        int totalEstimatedArrows = 0;
        int totalTrueArrows = 0;
        int correctNonAncestorRelationships = 0;

        double[] stats = new double[6];

        for (Edge edge : outGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            final Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.ARROW) {
                if (!dag.isAncestorOf(x, y)) {
                    correctNonAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }

            if (ey == Endpoint.ARROW) {
                if (!dag.isAncestorOf(y, x)) {
                    correctNonAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.ARROW) {
                    correctArrows++;
                }

                totalEstimatedArrows++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.ARROW) {
                totalTrueArrows++;
            }

            if (ey == Endpoint.ARROW) {
                totalTrueArrows++;
            }
        }

        out.println();
        out.println("# correct arrows = " + correctArrows);
        out.println("# total estimated arrows = " + totalEstimatedArrows);
        out.println("# correct arrow nonancestor relationships = " + correctNonAncestorRelationships);
        out.println("# total true arrows = " + totalTrueArrows);

        out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctArrows / (double) totalEstimatedArrows;
        out.println("Arrow precision = " + nf.format(precision));
        final double recall = correctArrows / (double) totalTrueArrows;
        out.println("Arrow recall = " + nf.format(recall));
        final double proportionCorrectNonAncestorRelationships = correctNonAncestorRelationships /
                (double) totalEstimatedArrows;
        out.println("Proportion correct arrow nonancestor relationships " + nf.format(proportionCorrectNonAncestorRelationships));

        stats[0] = correctArrows;
        stats[1] = totalEstimatedArrows;
        stats[2] = totalTrueArrows;
        stats[3] = precision;
        stats[4] = recall;
        stats[5] = proportionCorrectNonAncestorRelationships;

        return stats;
    }

    private double[] printCorrectTails(Graph dag, Graph outGraph, Graph truePag) {
        int correctTails = 0;
        int correctAncestorRelationships = 0;
        int totalEstimatedTails = 0;
        int totalTrueTails = 0;

        double[] stats = new double[6];

        for (Edge edge : outGraph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            final Edge edge1 = truePag.getEdge(x, y);

            if (ex == Endpoint.TAIL) {
                if (dag.isAncestorOf(x, y)) {
                    correctAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(x) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }

            if (ey == Endpoint.TAIL) {
                if (dag.isAncestorOf(y, x)) {
                    correctAncestorRelationships++;
                }

                if (edge1 != null && edge1.getProximalEndpoint(y) == Endpoint.TAIL) {
                    correctTails++;
                }

                totalEstimatedTails++;
            }
        }

        for (Edge edge : truePag.getEdges()) {
            Endpoint ex = edge.getEndpoint1();
            Endpoint ey = edge.getEndpoint2();

            if (ex == Endpoint.TAIL) {
                totalTrueTails++;
            }

            if (ey == Endpoint.TAIL) {
                totalTrueTails++;
            }
        }

        out.println();
        out.println("# correct tails = " + correctTails);
        out.println("# total estimated tails = " + totalEstimatedTails);
        out.println("# correct tail ancestor relationships = " + correctAncestorRelationships);
        out.println("# total true tails = " + totalTrueTails);

        out.println();
        NumberFormat nf = new DecimalFormat("0.00");
        final double precision = correctTails / (double) totalEstimatedTails;
        out.println("Tail precision = " + nf.format(precision));
        final double recall = correctTails / (double) totalTrueTails;
        out.println("Tail recall = " + nf.format(recall));
        final double proportionCorrectAncestorRelationships = correctAncestorRelationships /
                (double) totalEstimatedTails;
        out.println("Proportion correct tail ancestor relationships " + nf.format(proportionCorrectAncestorRelationships));

        stats[0] = correctTails;
        stats[1] = totalEstimatedTails;
        stats[2] = totalTrueTails;
        stats[3] = precision;
        stats[4] = recall;
        stats[5] = proportionCorrectAncestorRelationships;

        return stats;
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

    private void printAverageStatistics(String name, List<double[]> arrowStats, List<double[]> tailStats,
                                        List<Long> elapsedTimes) {
        NumberFormat nf = new DecimalFormat("0");
        NumberFormat nf2 = new DecimalFormat("0.00");

        out.println();
        out.println(name);

        double[] avgArrowStats = new double[6];

        for (int i = 0; i < avgArrowStats.length; i++) {
            double sum = 0.0;

            for (double[] d : arrowStats) {
                sum += d[i];
            }

            avgArrowStats[i] = sum / (double) arrowStats.size();
        }

        out.println();
        out.println("Avg Correct Arrows = " + nf.format(avgArrowStats[0]));
        out.println("Avg Estimated Arrows = " + nf.format(avgArrowStats[1]));
        out.println("Avg True Arrows = " + nf.format(avgArrowStats[2]));
        out.println("Avg Arrow Precision = " + nf2.format(avgArrowStats[3]));
        out.println("Avg Arrow Recall = " + nf2.format(avgArrowStats[4]));
        out.println("Avg Proportion Correct Nonancestor Relationships = " + nf2.format(avgArrowStats[5]));

        double[] avgTailStats = new double[6];

        for (int i = 0; i < avgTailStats.length; i++) {
            double sum = 0.0;

            for (double[] d : tailStats) {
                sum += d[i];
            }

            avgTailStats[i] = sum / (double) tailStats.size();
        }

        out.println();
        out.println("Avg Correct Tails = " + nf.format(avgTailStats[0]));
        out.println("Avg Estimated Tails = " + nf.format(avgTailStats[1]));
        out.println("Avg True Tails = " + nf.format(avgTailStats[2]));
        out.println("Avg Tail Precision = " + nf2.format(avgTailStats[3]));
        out.println("Avg Tail Recall = " + nf2.format(avgTailStats[4]));
        out.println("Avg Proportion Correct Ancestor Relationships = " + nf2.format(avgTailStats[5]));

        double sumElapsed = 0;

        for (Long e : elapsedTimes) {
            sumElapsed += (double) e;
        }

        out.println();
        out.println("Average Elapsed Time = " + nf.format(sumElapsed / (double) elapsedTimes.size()) + " ms");
        out.println();
    }

    private void printAverageConfusion(String name, List<int[][]> countsList) {
        final int rows = countsList.get(0).length;
        final int cols = countsList.get(0)[0].length;

        int[][] average = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                int sum = 0;

                for (int[][] counts : countsList) {
                    sum += counts[i][j];
                }

                average[i][j] = (int) round((sum / (double) countsList.size()));
            }
        }

        out.println();
        out.println(name);
        out.println(edgeMisclassifications(average));

    }

    public static String endpointMisclassification(List<Node> _nodes, Graph estGraph, Graph refGraph) {
        int[][] counts = new int[4][4];

        for (int i = 0; i < _nodes.size(); i++) {
            for (int j = 0; j < _nodes.size(); j++) {
                if (i == j) continue;

                Endpoint endpoint1 = refGraph.getEndpoint(_nodes.get(i), _nodes.get(j));
                Endpoint endpoint2 = estGraph.getEndpoint(_nodes.get(i), _nodes.get(j));

                int index1 = getIndex(endpoint1);
                int index2 = getIndex(endpoint2);

                counts[index1][index2]++;
            }
        }

        TextTable table2 = new TextTable(5, 5);

        table2.setToken(0, 1, "-o");
        table2.setToken(0, 2, "->");
        table2.setToken(0, 3, "--");
        table2.setToken(0, 4, "NO EDGE");
        table2.setToken(1, 0, "-o");
        table2.setToken(2, 0, "->");
        table2.setToken(3, 0, "--");
        table2.setToken(4, 0, "NO EDGE");

        int sum = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) continue;
                else sum += counts[i][j];
            }
        }

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (i == 3 && j == 3) table2.setToken(i + 1, j + 1, "");
                else table2.setToken(i + 1, j + 1, "" + counts[i][j]);
            }
        }

        return table2.toString();

        //        println("\n" + name);
        //        println(table2.toString());
        //        println("");
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

    public void rtest5() {
        try {
            DataSet data = BigDataSetUtility.readInContinuousData(new File("/Users/jdramsey/Downloads/YeastNoDupe.csv"), ',');

            Fgs ges = new Fgs(data);
            ges.setDepth(3);
            ges.setVerbose(true);
            Graph pattern = ges.search();

            System.out.println(pattern);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestFgs.class);
    }
}





