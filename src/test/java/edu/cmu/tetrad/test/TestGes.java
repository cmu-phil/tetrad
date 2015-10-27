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
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

/**
 * Tests GES.
 *
 * @author Joseph Ramsey
 */
public class TestGes extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestGes(String name) {
        super(name);
    }

    public void testBlank() {
        // Blank to keep the automatic JUnit runner happy.
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    public void rtestSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    public void testSearch5() {
        int numVars = 10;
        int numEdges = 10;
        int sampleSize = 1000;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 7,
                5, 5, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        System.out.println("********** SAMPLE SIZE = " + sampleSize);

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        Ges ges = new Ges(dataSet);
        ges.setTrueGraph(trueGraph);

        // Run search
        Graph resultGraph = ges.search();

        // PrintUtil out problem and graphs.
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
    }

    public void testSearch6() {
        Dag trueGraph = new Dag(GraphUtils.randomGraph(10, 0, 10, 30, 15, 15, false));

        int sampleSize = 1000;

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        Ges ges = new Ges(dataSet);

        Graph pattern = ges.search();

        System.out.println("True graph = " + SearchGraphUtils.patternForDag(trueGraph));
        System.out.println("Pattern = " + pattern);
    }

    public void testSearch7() {
        Dag trueGraph = new Dag(GraphUtils.randomGraph(50, 0, 50, 30, 15, 15, false));

        int sampleSize = 1000;

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        Ges ges = new Ges(dataSet);

        long start = System.currentTimeMillis();

        Graph pattern = ges.search();

        long stop = System.currentTimeMillis();

        Graph truePattern = SearchGraphUtils.patternForDag(trueGraph);

        System.out.println(SearchGraphUtils.graphComparisonString("GES pattern ", pattern, "True pattern", truePattern, false));


        System.out.println("Elapsed time = " + (start - stop) / 1000 + " seconds ");

    }

    public void testSearch9() {
        TetradLogger.getInstance().setForceLog(false);

        Graph trueGraph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        trueGraph.addNode(x1);
        trueGraph.addNode(x2);
        trueGraph.addNode(x3);
        trueGraph.addNode(x4);
        trueGraph.addNode(x5);

        trueGraph.addDirectedEdge(x1, x3);
        trueGraph.addDirectedEdge(x2, x3);
        trueGraph.addDirectedEdge(x3, x4);
        trueGraph.addDirectedEdge(x4, x5);
        trueGraph.addDirectedEdge(x1, x5);
        trueGraph.addDirectedEdge(x2, x5);

        System.out.println("True graph = " + trueGraph);

        int sampleSize = 1000;

        System.out.println("Large sem simulator");
        SemPm pm = new SemPm(trueGraph);
        SemImInitializationParams params = new SemImInitializationParams();
        SemIm im = new SemIm(pm, params);

        System.out.println("... simulating data");
        DataSet dataSet = im.simulateData(sampleSize, false);

        Ges ges = new Ges(dataSet);
        ges.setNumPatternsToStore(0);

        ges.setTrueGraph(trueGraph);

        long start = System.currentTimeMillis();

        Graph pattern = ges.search();

        long stop = System.currentTimeMillis();

        Graph truePattern = SearchGraphUtils.patternForDag(trueGraph);

        System.out.println(SearchGraphUtils.graphComparisonString("GES pattern ", pattern, "True pattern", truePattern, false));

        System.out.println("Elapsed time = " + (stop - start) / 1000 + " seconds ");

        System.out.println(pattern);
    }

    public void test10() {
        NumberFormat nf = new DecimalFormat("0.0000000");

        for (int n = 25; n <= 10000; n += 25) {
            double _p = .01;

            // Find the value for v that will yield p = _p

            for (double v = 0.0; ; v += 1) {
                double f = Math.exp((v - Math.log(n)) / (n / 2.0));
                double p = 1 - ProbUtils.fCdf(f, n, n);

                if (p <= _p) {
                    System.out.println(n + " " + nf.format(p) + " " + nf.format(v));
                    break;
                }
            }
        }
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);
        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(500, false);

        // Set up search.
        Ges ges = new Ges(dataSet);
        ges.setTrueGraph(graph);

        // Run search
        Graph resultGraph = ges.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
        System.out.println("\nInput graph:");
        System.out.println(graph);
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);

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
        Ges ges = new Ges(dataSet);
        ges.setKnowledge(knowledge);

        // Run search
        Graph resultGraph = ges.search();

        // PrintUtil out problem and graphs.
        System.out.println(knowledge);
        System.out.println("Input graph:");
        System.out.println(graph);
        System.out.println("Result graph:");
        System.out.println(resultGraph);

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    public void testPowerSet() {
        List<Node> nodes = new ArrayList<Node>();

        nodes.add(new GraphNode("X"));
        nodes.add(new GraphNode("Y"));
        nodes.add(new GraphNode("Z"));

        System.out.println(powerSet(nodes));
    }

    private static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<Set<Node>>();
        int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<Node>();
            String selection = Integer.toBinaryString(i);

            int shift = nodes.size() - selection.length();

            for (int j = nodes.size() - 1; j >= 0; j--) {
                if (j >= shift && selection.charAt(j - shift) == '1') {
                    newSet.add(nodes.get(j));
                }
            }
            subsets.add(newSet);
        }

        return subsets;
    }

    public void rtestGes() {
        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 400;
        int numRuns = 10;
        double alpha = .001;
        double penaltyDiscount = 1;
        boolean conservative = false;

        System.out.println("Num vars = " + numVars);
        System.out.println("Num edges = " + (int) (numVars * edgesPerNode));
        System.out.println("Num cases = " + numCases);
        System.out.println("Num runs = " + numRuns);
        System.out.println("Alpha = " + alpha);
        System.out.println("Penalty discount = " + penaltyDiscount);
        System.out.println();

        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        int numMethods = 8;

        int[][] adjFpErrors = new int[numRuns][numMethods];
        int[][] adjFnErrors = new int[numRuns][numMethods];
        int[][] arrowFpErrors = new int[numRuns][numMethods];
        int[][] arrowFnErrors = new int[numRuns][numMethods];

        for (int t = 0; t < numRuns; t++) {

            Graph graph = GraphUtils.randomDagQuick(vars, 0, (int) (numVars * edgesPerNode));

            DataSet data;

            if (false) {
                LargeSemSimulator simulator = new LargeSemSimulator(graph);
                data = simulator.simulateDataAcyclic(numCases);
            } else {
                GeneralizedSemPm pm = new GeneralizedSemPm(graph);

                if (true) {
                    List<Node> variablesNodes = pm.getVariableNodes();
                    List<Node> errorNodes = pm.getErrorNodes();
                    Map<String, String> paramMap = new HashMap<String, String>();
                    paramMap.put("a", "U(-1,1)");

                    try {

                        for (Node node : errorNodes) {
                            String _template = TemplateExpander.getInstance().expandTemplate("N(0, 1)", pm, node);
//                            String _template = TemplateExpander.getInstance().expandTemplate("Gamma(2,5)", pm, node);
//                            String _template = TemplateExpander.getInstance().expandTemplate("Mixture(.5,Normal(-1,.5),.5,Normal(1,.5))", pm, node);
                            pm.setNodeExpression(node, _template);
                        }

                        for (Node node : variablesNodes) {
                            String _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * $) + ERROR", pm, node);
//                            String _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * signum($) * abs($)^1.5) + ERROR", pm, node);
//                            String _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * sin($)) + ERROR", pm, node);
                            pm.setNodeExpression(node, _template);
                        }


                        Set<String> parameters = pm.getParameters();

                        for (String parameter : parameters) {
                            for (String type : paramMap.keySet()) {
                                if (parameter.startsWith(type)) {
                                    pm.setParameterExpression(parameter, paramMap.get(type));
                                }
                            }
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                }

                GeneralizedSemIm im = new GeneralizedSemIm(pm);
                data = im.simulateData(numCases, false);
            }

            System.out.println("Start nonparanormal transform");
            DataSet X = DataUtils.getNonparanormalTransformed(data);
            System.out.println("Finish nonparanormal transform");

            ICovarianceMatrix covStandard = new CovarianceMatrix(data);
            ICovarianceMatrix covNonparanormal = new CovarianceMatrix(X);

            int m = -1; // method.

            {
                System.out.println("\nPC Standard");

                Pc ges = new Pc(new IndTestFisherZ(covStandard, alpha));

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nPC Nonparanormal");

                Pc ges = new Pc(new IndTestFisherZ(covNonparanormal, alpha));

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
//            {
//                System.out.println("\nPC Nonlinear");
//
//                Pc ges = new Pc(new IndTestConditionalCorrelation(data, alpha));
//
//                Graph outGraph = ges.search();
//
//                DataGraphUtils.GraphComparison comparison = DataGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));
//
//                System.out.println("Adjacencies:");
//                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());
//
//                System.out.println("Orientations:");
//                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());
//
//                m++;
//                adjFpErrors[t][m] += comparison.getAdjFp();
//                adjFnErrors[t][m] += comparison.getAdjFn();
//                arrowFpErrors[t][m] += comparison.getArrowptFp();
//                arrowFnErrors[t][m] += comparison.getArrowptFn();
//            }
            {
                System.out.println("\nGES Standard AIC");

                GesConcurrent ges = new GesConcurrent(covStandard);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent.ScoreType.AIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nGES Nonparanormal AIC");

                GesConcurrent ges = new GesConcurrent(covNonparanormal);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent1.ScoreType.AIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nGES Standard BIC");

                GesConcurrent ges = new GesConcurrent(covStandard);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent1.ScoreType.BIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nGES Nonparanormal BIC");

                GesConcurrent ges = new GesConcurrent(covNonparanormal);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent.ScoreType.BIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nPC-S then GES-S");

//                GesConcurrent1 ges = new GesConcurrent1(covStandard);
                Pc ges = new Pc(new IndTestFisherZ(covStandard, alpha));

                Graph outGraph = ges.search();

                GesOrienter orienter = new GesOrienter(covStandard);
                orienter.orient(outGraph);

//                new Lofs2(outGraph, Collections.singletonList(data)).orient();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nTruth");

                final Graph pattern = SearchGraphUtils.patternForDag(graph);
                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(pattern, pattern);

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjCorrect();
                adjFnErrors[t][m] += comparison.getAdjCorrect();
                arrowFpErrors[t][m] += comparison.getArrowptCorrect();
                arrowFnErrors[t][m] += comparison.getArrowptCorrect();
            }
        }

        System.out.println("Num vars = " + numVars);
        System.out.println("Num edges = " + (int) (numVars * edgesPerNode));
        System.out.println("Num cases = " + numCases);
        System.out.println("Num runs = " + numRuns);
        System.out.println("Alpha = " + alpha);
        System.out.println("Penalty discount = " + penaltyDiscount);
        System.out.println("Conservative = " + conservative);
        System.out.println();
        System.out.println();
        System.out.println();

        NumberFormat nf = new DecimalFormat("0.00");

        double[][] adjFpRates = new double[numRuns][numMethods - 1];
        double[][] adjRecoveryRates = new double[numRuns][numMethods - 1];
        double[][] arrowFpRates = new double[numRuns][numMethods - 1];
        double[][] arrowRecoveryRates = new double[numRuns][numMethods - 1];

        for (int t = 0; t < numRuns; t++) {

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = adjFpErrors[t][numMethods - 1];
                final double adjFpRate = adjFpErrors[t][s] / (double) truth;
                adjFpRates[t][s] = adjFpRate;
//                System.out.print(nf.format(adjFpRate) + "\t");
            }

            System.out.print("\t");

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = adjFnErrors[t][numMethods - 1];
                final double adjRecoveryRate = 1.0 - (adjFnErrors[t][s] / (double) truth);
                adjRecoveryRates[t][s] = adjRecoveryRate;
//                System.out.print(nf.format(adjRecoveryRate) + "\t");
            }

            System.out.print("\t");

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = arrowFpErrors[t][numMethods - 1];
                final double arrowFpRate = arrowFpErrors[t][s] / (double) truth;
                arrowFpRates[t][s] = arrowFpRate;
//                System.out.print(nf.format(arrowFpRate) + "\t");
            }

            System.out.print("\t");

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = arrowFnErrors[t][numMethods - 1];
                final double arrowRecovery = 1.0 - (arrowFnErrors[t][s] / (double) truth);
                arrowRecoveryRates[t][s] = arrowRecovery;
//                System.out.print(nf.format(arrowRecovery) + "\t");
            }

            System.out.println();
        }

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(adjFpRates[t][s]) + "\t");
            }

            System.out.println();
        }

        System.out.println();

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(adjRecoveryRates[t][s]) + "\t");
            }
            System.out.println();
        }

        System.out.println();

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(arrowFpRates[t][s]) + "\t");
            }
            System.out.println();
        }

        System.out.println();

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(arrowRecoveryRates[t][s]) + "\t");
            }
            System.out.println();
        }

        System.out.println();

        System.out.println("Averages");

        averages(adjFpRates);
        averages(adjRecoveryRates);
        averages(arrowFpRates);
        averages(arrowRecoveryRates);
    }

    private double[] averages(double[][] rates) {
        NumberFormat nf = new DecimalFormat("0.00");

        double[] avg = new double[rates[0].length];

        for (int j = 0; j < rates[0].length; j++) {
            double sum = 0.0;

            for (int i = 0; i < rates.length; i++) {
                sum += rates[i][j];
            }

            double a = sum / rates.length;

            System.out.print(nf.format(a) + "\t");
            avg[j] = a;
        }

        System.out.println();

        return avg;
    }

    public void rtestGes2(int nonLinearType, int nonGaussianType, int numVars, int numCases) {
        // Use this one.

//        int numVars = 50;
        double edgesPerNode = 1.0;
//        int numCases = 1000;
        int numRuns = 10;
        double alpha = .000001;
        double penaltyDiscount = 2;
        boolean conservative = false;

        System.out.println("Num vars = " + numVars);
        System.out.println("Num edges = " + (int) (numVars * edgesPerNode));
        System.out.println("Num cases = " + numCases);
        System.out.println("Num runs = " + numRuns);
        System.out.println("Alpha = " + alpha);
        System.out.println("Penalty discount = " + penaltyDiscount);
        System.out.println();

        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        // Include one extra method for the truth.
        int numMethods = 9;

        int[][] adjFpErrors = new int[numRuns][numMethods];
        int[][] adjFnErrors = new int[numRuns][numMethods];
        int[][] arrowFpErrors = new int[numRuns][numMethods];
        int[][] arrowFnErrors = new int[numRuns][numMethods];

        for (int t = 0; t < numRuns; t++) {

            Graph graph = GraphUtils.randomDagQuick(vars, 0, (int) (numVars * edgesPerNode));

            DataSet data;

            if (false) {
                LargeSemSimulator simulator = new LargeSemSimulator(graph);
                data = simulator.simulateDataAcyclic(numCases);
            } else {
                GeneralizedSemPm pm = new GeneralizedSemPm(graph);

                if (true) {
                    List<Node> variablesNodes = pm.getVariableNodes();
                    List<Node> errorNodes = pm.getErrorNodes();
                    Map<String, String> paramMap = new HashMap<String, String>();
                    paramMap.put("a", "U(-1,1)");

                    try {

                        for (Node node : errorNodes) {
                            String _template;

                            if (nonGaussianType == 1) {
                                _template = TemplateExpander.getInstance().expandTemplate("N(0, 1)", pm, node);
                            } else if (nonGaussianType == 2) {
                                _template = TemplateExpander.getInstance().expandTemplate("Gamma(2,5)", pm, node);
                            } else if (nonGaussianType == 3) {
                                _template = TemplateExpander.getInstance().expandTemplate("Mixture(.5,Normal(-1,.5),.5,Normal(1,.5))", pm, node);
                            } else {
                                throw new IllegalArgumentException();
                            }

                            pm.setNodeExpression(node, _template);
                        }

                        for (Node node : variablesNodes) {
                            String _template;
                            if (nonLinearType == 1) {
                                _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * $) + ERROR", pm, node);
                            } else if (nonLinearType == 2) {
                                _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * signum($) * abs($)^1.5) + ERROR", pm, node);
                            } else if (nonLinearType == 3) {
                                _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * sin($)) + ERROR", pm, node);
                            } else {
                                throw new IllegalArgumentException();
                            }
                            pm.setNodeExpression(node, _template);
                        }


                        Set<String> parameters = pm.getParameters();

                        for (String parameter : parameters) {
                            for (String type : paramMap.keySet()) {
                                if (parameter.startsWith(type)) {
                                    pm.setParameterExpression(parameter, paramMap.get(type));
                                }
                            }
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                }

                GeneralizedSemIm im = new GeneralizedSemIm(pm);
                data = im.simulateDataRecursive(numCases, false);
            }

            System.out.println("Start nonparanormal transform");
            DataSet X = DataUtils.getNonparanormalTransformed(data);
            System.out.println("Finish nonparanormal transform");

            ICovarianceMatrix covStandard = new CovarianceMatrix(data);
            ICovarianceMatrix covNonparanormal = new CovarianceMatrix(X);

            int m = -1; // method.

            {
                System.out.println("\nPC Standard");

                Pc ges = new Pc(new IndTestFisherZ(covStandard, alpha));

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nPC Nonparanormal");

                Pc ges = new Pc(new IndTestFisherZ(covNonparanormal, alpha));

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
//            {
//                System.out.println("\nPC Nonlinear");
//
//                Pc ges = new Pc(new IndTestConditionalCorrelation(data, alpha));
//
//                Graph outGraph = ges.search();
//
//                DataGraphUtils.GraphComparison comparison = DataGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));
//
//                System.out.println("Adjacencies:");
//                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());
//
//                System.out.println("Orientations:");
//                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());
//
//                m++;
//                adjFpErrors[t][m] += comparison.getAdjFp();
//                adjFnErrors[t][m] += comparison.getAdjFn();
//                arrowFpErrors[t][m] += comparison.getArrowptFp();
//                arrowFnErrors[t][m] += comparison.getArrowptFn();
//            }
            {
                System.out.println("\nGES Standard AIC");

                GesConcurrent ges = new GesConcurrent(covStandard);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent.ScoreType.AIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nGES Nonparanormal AIC");

                GesConcurrent ges = new GesConcurrent(covNonparanormal);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent.ScoreType.AIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nGES Standard BIC");

                GesConcurrent ges = new GesConcurrent(covStandard);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent.ScoreType.BIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nGES Nonparanormal BIC");

                GesConcurrent ges = new GesConcurrent(covNonparanormal);
                ges.setVerbose(false);
                ges.setNumPatternsToStore(0);
                ges.setPenaltyDiscount(penaltyDiscount);
//                ges.setScoreType(GesConcurrent.ScoreType.BIC);
//                ges.setConservative(conservative);

                Graph outGraph = ges.search();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nPC-S then GES-S");

//                GesConcurrent1 ges = new GesConcurrent1(covStandard);
                Pc ges = new Pc(new IndTestFisherZ(covStandard, alpha));

                Graph outGraph = ges.search();

                GesOrienter orienter = new GesOrienter(covStandard);
                orienter.orient(outGraph);

//                new Lofs2(outGraph, Collections.singletonList(data)).orient();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
            {
                System.out.println("\nPC-L then GES-L");

//                GesConcurrent1 ges = new GesConcurrent1(covStandard);
                Pc ges = new Pc(new IndTestFisherZ(covNonparanormal, alpha));

                Graph outGraph = ges.search();

                GesOrienter orienter = new GesOrienter(covNonparanormal);
                orienter.orient(outGraph);

//                new Lofs2(outGraph, Collections.singletonList(data)).orient();

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjFp();
                adjFnErrors[t][m] += comparison.getAdjFn();
                arrowFpErrors[t][m] += comparison.getArrowptFp();
                arrowFnErrors[t][m] += comparison.getArrowptFn();
            }
//            {
//                System.out.println("\nPC-S then GES-S");
//
//                GesConcurrent1 ges = new GesConcurrent1(covStandard);
////                Pc ges = new Pc(new IndTestFisherZ(covStandard, alpha));
//
//                Graph outGraph = ges.search();
//
//                GesOrienter  orienter = new GesOrienter(covStandard);
//                orienter.orient(outGraph);
//
////                new Lofs2(outGraph, Collections.singletonList(data)).orient();
//
//                DataGraphUtils.GraphComparison comparison = DataGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));
//
//                System.out.println("Adjacencies:");
//                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());
//
//                System.out.println("Orientations:");
//                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());
//
//                m++;
//                adjFpErrors[t][m] += comparison.getAdjFp();
//                adjFnErrors[t][m] += comparison.getAdjFn();
//                arrowFpErrors[t][m] += comparison.getArrowptFp();
//                arrowFnErrors[t][m] += comparison.getArrowptFn();
//            }
//            {
//                System.out.println("\nPC Nonlinear");
//
//                final IndTestConditionalCorrelation test = new IndTestConditionalCorrelation(data, .05);
//                Graph _graph = new EdgeListGraph(data.getVariables());
//                test.setAlpha(alpha);
//
//                FasStableConcurrent fas = new FasStableConcurrent(_graph, test);
//
////                fas.setVerbose(true);
//
//                Pc pc = new Pc(test);
//                pc.setDepth(2);
//
//                Graph outGraph = pc.search(fas, data.getVariables());
////                Graph outGraph = pc.search();
//
//                DataGraphUtils.GraphComparison comparison = DataGraphUtils.getGraphComparison2(outGraph, SearchGraphUtils.patternForDag(graph));
//
//                System.out.println("Adjacencies:");
//                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());
//
//                System.out.println("Orientations:");
//                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());
//
//                m++;
//                adjFpErrors[t][m] += comparison.getAdjFp();
//                adjFnErrors[t][m] += comparison.getAdjFn();
//                arrowFpErrors[t][m] += comparison.getArrowptFp();
//                arrowFnErrors[t][m] += comparison.getArrowptFn();
//            }
//            n
            {
                System.out.println("\nTruth");

                final Graph pattern = SearchGraphUtils.patternForDag(graph);
                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(pattern, pattern);

                System.out.println("Adjacencies:");
                System.out.println("Correct " + comparison.getAdjCorrect() + " FP = " + comparison.getAdjFp() + " FN = " + comparison.getAdjFn());

                System.out.println("Orientations:");
                System.out.println("Correct " + comparison.getArrowptCorrect() + " FP = " + comparison.getArrowptFp() + " FN = " + comparison.getArrowptFn());

                m++;
                adjFpErrors[t][m] += comparison.getAdjCorrect();
                adjFnErrors[t][m] += comparison.getAdjCorrect();
                arrowFpErrors[t][m] += comparison.getArrowptCorrect();
                arrowFnErrors[t][m] += comparison.getArrowptCorrect();
            }

        }

        System.out.println("Num vars = " + numVars);
        System.out.println("Num edges = " + (int) (numVars * edgesPerNode));
        System.out.println("Num cases = " + numCases);
        System.out.println("Num runs = " + numRuns);
        System.out.println("Alpha = " + alpha);
        System.out.println("Penalty discount = " + penaltyDiscount);
        System.out.println("Conservative = " + conservative);
        System.out.println();
        System.out.println();
        System.out.println();

        NumberFormat nf = new DecimalFormat("0.00");

        double[][] adjFpRates = new double[numRuns][numMethods - 1];
        double[][] adjRecoveryRates = new double[numRuns][numMethods - 1];
        double[][] arrowFpRates = new double[numRuns][numMethods - 1];
        double[][] arrowRecoveryRates = new double[numRuns][numMethods - 1];

        for (int t = 0; t < numRuns; t++) {

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = adjFpErrors[t][numMethods - 1];
                final double adjFpRate = adjFpErrors[t][s] / (double) truth;
                adjFpRates[t][s] = adjFpRate;
//                System.out.print(nf.format(adjFpRate) + "\t");
            }

            System.out.print("\t");

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = adjFnErrors[t][numMethods - 1];
                final double adjRecoveryRate = 1.0 - (adjFnErrors[t][s] / (double) truth);
                adjRecoveryRates[t][s] = adjRecoveryRate;
//                System.out.print(nf.format(adjRecoveryRate) + "\t");
            }

            System.out.print("\t");

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = arrowFpErrors[t][numMethods - 1];
                final double arrowFpRate = arrowFpErrors[t][s] / (double) truth;
                arrowFpRates[t][s] = arrowFpRate;
//                System.out.print(nf.format(arrowFpRate) + "\t");
            }

            System.out.print("\t");

            for (int s = 0; s < numMethods - 1; s++) {
                int truth = arrowFnErrors[t][numMethods - 1];
                final double arrowRecovery = 1.0 - (arrowFnErrors[t][s] / (double) truth);
                arrowRecoveryRates[t][s] = arrowRecovery;
//                System.out.print(nf.format(arrowRecovery) + "\t");
            }

            System.out.println();
        }

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(adjFpRates[t][s]) + "\t");
            }

            System.out.println();
        }

        System.out.println();

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(adjRecoveryRates[t][s]) + "\t");
            }
            System.out.println();
        }

        System.out.println();

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(arrowFpRates[t][s]) + "\t");
            }
            System.out.println();
        }

        System.out.println();

        for (int t = 0; t < numRuns; t++) {
            for (int s = 0; s < numMethods - 1; s++) {
                System.out.print(nf.format(arrowRecoveryRates[t][s]) + "\t");
            }
            System.out.println();
        }

        System.out.println();

        System.out.println("Averages");

        averages(adjFpRates);
        averages(adjRecoveryRates);
        averages(arrowFpRates);
        averages(arrowRecoveryRates);
    }


    public void rtest3() {
        int numVars = 10;
        double edgesPerNode = 1.0;
        int numCases = 1000;
        int numRuns = 1;
        double alpha = 1.0 / numCases;
        double penaltyDiscount = 1;
        boolean conservative = false;

        System.out.println("Num vars = " + numVars);
        System.out.println("Num edges = " + (int) (numVars * edgesPerNode));
        System.out.println("Num cases = " + numCases);
        System.out.println("Num runs = " + numRuns);
        System.out.println("Alpha = " + alpha);
        System.out.println("Penalty discount = " + penaltyDiscount);
        System.out.println();

        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        List<Node> vars = new ArrayList<Node>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        int numMethods = 7;

        int[][] adjFpErrors = new int[numRuns][numMethods];
        int[][] adjFnErrors = new int[numRuns][numMethods];
        int[][] arrowFpErrors = new int[numRuns][numMethods];
        int[][] arrowFnErrors = new int[numRuns][numMethods];

        for (int t = 0; t < numRuns; t++) {

            Graph graph = GraphUtils.randomDagQuick(vars, 0, (int) (numVars * edgesPerNode));

            DataSet data;

            if (false) {
                LargeSemSimulator simulator = new LargeSemSimulator(graph);
                data = simulator.simulateDataAcyclic(numCases);
            } else {
                GeneralizedSemPm pm = new GeneralizedSemPm(graph);

                if (false) {
                    List<Node> variablesNodes = pm.getVariableNodes();
                    List<Node> errorNodes = pm.getErrorNodes();
                    Map<String, String> paramMap = new HashMap<String, String>();
                    paramMap.put("a", "U(-.8,.8)");

                    try {
                        for (Node node : variablesNodes) {
                            String _template = TemplateExpander.getInstance().expandTemplate("TSUM(NEW(a) * abs($) * $^1.5) + ERROR", pm, node);
                            pm.setNodeExpression(node, _template);
                        }

                        for (Node node : errorNodes) {
                            String _template = TemplateExpander.getInstance().expandTemplate("Beta(2,5)", pm, node);
//                            String _template = TemplateExpander.getInstance().expandTemplate("Mixture(.5,Normal(-1,.5),.5,Normal(1,.5))", pm, node);
                            pm.setNodeExpression(node, _template);
                        }

                        Set<String> parameters = pm.getParameters();

                        for (String parameter : parameters) {
                            for (String type : paramMap.keySet()) {
                                if (parameter.startsWith(type)) {
                                    pm.setParameterExpression(parameter, paramMap.get(type));
                                }
                            }
                        }
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                }

                GeneralizedSemIm im = new GeneralizedSemIm(pm);
                data = im.simulateData(numCases, false);
            }
//
            System.out.println("Start nonparanormal transform");
            DataSet X = DataUtils.getNonparanormalTransformed(data);
            System.out.println("Finish nonparanormal transform");

            ICovarianceMatrix covNonparanormal = new CovarianceMatrix(X);

            GesConcurrent ges = new GesConcurrent(covNonparanormal);
//            ges.setScoreType(GesConcurrent.ScoreType.BIC);

            Graph g = ges.search();

            System.out.println(g);


            GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(g, SearchGraphUtils.patternForDag(graph));
            System.out.println("Adj FP = " + comparison.getAdjFp());
            System.out.println("Adj FN = " + comparison.getAdjFn());
            System.out.println("Arrow FP = " + comparison.getArrowptFp());
            System.out.println("Arrow FN = " + comparison.getArrowptFn());
        }


    }

    public static void main(String... args) {
        if (args.length == 0) {
            new TestGes("Title").rtestGes2(3, 3, 50, 1000);
        } else if (args.length == 4) {
            final int nonLinearType = Integer.parseInt(args[0]);
            final int nonGaussianType = Integer.parseInt(args[1]);
            final int numVars = Integer.parseInt(args[2]);
            final int numCases = Integer.parseInt(args[3]);

            new TestGes("Title").rtestGes2(nonGaussianType, nonLinearType, numVars, numCases);
        } else {
            throw new IllegalArgumentException("Not a configuration!");
        }
    }


    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestGes.class);
    }
}





