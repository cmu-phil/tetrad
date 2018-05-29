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

import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.FAS;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.PcAll;
import edu.cmu.tetrad.algcomparison.independence.*;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestChiSquare;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradVector;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.text.ParseException;
import java.util.*;

import static java.util.Collections.sort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Joseph Ramsey
 */
@Ignore
public class TestCci {

    private boolean printStuff = false;

    @Test
    public void test1() {

        String[] linearFunctions = new String[]{
                "TSUM(NEW(B)*$)"
        };

        String[] nonlinearFunctions = new String[]{
//                "abs(TSUM(NEW(B) * $))",
//                "TSUM(NEW(B) * (abs($) ^ .8))",
//                "TSUM(NEW(B) * (abs($) ^ 1.05))",
//                "TSUM(NEW(B) * (abs($) ^ 1.5))",
                "TSUM(NEW(B)*$) + TSUM(NEW(B) * (abs($) ^ 1.5))",
                "(TSUM(NEW(B)*$) + TSUM(NEW(B) * ($^2)))",
//                "TSUM(NEW(B) * ($ ^ 2))",
                "TSUM(NEW(B) * ($ ^ 3))",
//                "TSUM(NEW(B) * ln(cosh($)))",
                "tanh(NEW(B) * (TSUM($)))",
                "(TSUM(sin(NEW(B) * $)) + TSUM(cos(NEW(B) * $)))"
        };

        String[] gaussianErrors = new String[]{
                "Normal(0, 0.3)"
        };

        String[] nonGaussianErrors = new String[]{
//                "0.5 * Uniform(-1, 1)",
                "(U(0, 1)^3 - .5)",
//                "0.1 * Laplace(0, 1)"
        };

//        String parameters = "Split(-.6, -.2, .2, .6)";
        String parameters = "U(.2, .5)";

        int index = 1;

        try {
            for (String linearFunction : linearFunctions) {
                for (String nonlinearFunction : nonlinearFunctions) {
                    for (String gaussianError : gaussianErrors) {
                        for (String nonGaussianError : nonGaussianErrors) {
                            final double quantile = 0.33;
                            final double alpha = 0.01;
                            final int N = 1000;

                            File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations8/example" + index++);
                            dir.mkdirs();

                            PrintStream out = new PrintStream(new File(dir, "description.txt"));

                            out.println("Linear function " + linearFunction);
                            out.println("Nonlinear function " + nonlinearFunction);
                            out.println("Gaussian error " + gaussianError);
                            out.println("Non-Gaussian error " + nonGaussianError);
                            out.println("Parameters " + parameters);
                            out.println("Sample size " + N);
                            out.println("Quantile = " + quantile);
                            out.println("Alpha = " + alpha);

                            out.println();
                            out.close();

                            Graph graph = GraphUtils.randomGraph(20, 0, 20, 100, 100, 100, true);

                            GeneralizedSemPm pm1 = getPm(graph, linearFunction, gaussianError, parameters);
                            GeneralizedSemPm pm2 = getPm(graph, linearFunction, nonGaussianError, parameters);
                            GeneralizedSemPm pm3 = getPm(graph, nonlinearFunction, gaussianError, parameters);
                            GeneralizedSemPm pm4 = getPm(graph, nonlinearFunction, nonGaussianError, parameters);

                            GeneralizedSemIm im1 = new GeneralizedSemIm(pm1);
                            GeneralizedSemIm im2 = new GeneralizedSemIm(pm2);
                            GeneralizedSemIm im3 = new GeneralizedSemIm(pm3);
                            GeneralizedSemIm im4 = new GeneralizedSemIm(pm4);

                            DataSet D1 = im1.simulateData(N, false);
                            DataSet D2 = im2.simulateData(N, false);
                            DataSet D3 = im3.simulateData(N, false);
                            DataSet D4 = im4.simulateData(N, false);

//                                D1 = DataUtils.center(D1);
//                                D2 = DataUtils.center(D2);
//                                D3 = DataUtils.center(D3);
//                                D4 = DataUtils.center(D4);

//                                // Save these dataset out so we can compare with the White test. The judgements should be that edges for
//                                // D1 and D2 are nonlinear and edges for D3 and D4 are nonlinear.
//                                // Make sure you save out the graph as well.
//
//                                DataWriter.writeRectangularData(D1, new FileWriter(new File(dir, "D1.txt")), '\t');
//                                DataWriter.writeRectangularData(D2, new FileWriter(new File(dir, "D2.txt")), '\t');
//                                DataWriter.writeRectangularData(D3, new FileWriter(new File(dir, "D3.txt")), '\t');
//                                DataWriter.writeRectangularData(D4, new FileWriter(new File(dir, "D4.txt")), '\t');

//                                GraphUtils.saveGraph(graph, new File(dir, "graph.txt"), false);

//                                List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
//                                sort(edges);
//                                List<Node> variables = graph.getNodes();
//
//                                PrintStream graphOut = new PrintStream(new FileOutputStream(new File(dir, "graph.indices.txt")));
//
//                                for (int i = 0; i < edges.size(); i++) {
//                                    Edge edge = edges.get(i);
//
//                                    Node x = edge.getNode1();
//                                    Node y = edge.getNode2();
//
//                                    int j1 = variables.indexOf(x);
//                                    int j2 = variables.indexOf(y);
//
//                                    graphOut.println((j1 + 1) + "\t" + (j2 + 1));
//                                }
//
//                                out.close();

                            Parameters parameters1 = new Parameters();
                            parameters1.set("alpha", 0.001);
                            parameters1.set("penaltyDiscount", 1);

                            parameters1.set("depth", -1);
                            parameters1.set("twoCycleAlpha", 1e-20);
                            parameters1.set("extraEdgeThreshold", .5);
                            parameters1.set("faskDelta", -0.2);

//                                parameters1.set("useFasAdjacencies");
//                                parameters1.set("useCorrDiffAdjacencies");
//                                 Bootstrapping
//                                parameters1.set("bootstrapSampleSize");
//                                parameters1.set("bootstrapEnsemble");
//                                parameters1.set("verbose");

                            DataSet d = D4;

                            d = DataUtils.getNonparanormalTransformed(d);
//
//
//                            IndependenceWrapper test = new SemBicTest();
//                            FAS pcall = new FAS(test);
//                            Graph g2 = pcall.search(D4, parameters1);

                            Score score = new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly(d));

                            edu.cmu.tetrad.search.Fask fask = new edu.cmu.tetrad.search.Fask(d, score);
//                            fask.setInitialGraph(g2);
                            Graph _g = fask.search();

//                            _g = g2;

                            _g = GraphUtils.replaceNodes(_g, graph.getNodes());

//                            System.out.println("graph = " + graph + " _g = " + _g);

                            double ap = new AdjacencyPrecision().getValue(_g, graph);
                            double ar = new AdjacencyRecall().getValue(graph, _g);
                            double ahp = new ArrowheadPrecisionIgnore2c().getValue(graph, _g);
                            double ahr = new ArrowheadRecall().getValue(graph, _g);

                            System.out.println("\nNonlinear function: " + nonlinearFunction);
                            System.out.println("AP = " + ap + " AR = " + ar + " AHP = " + ahp + " AHR = " + ahr);
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private GeneralizedSemPm getPm(Graph graph, String function, String error, String parameters) {
        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        List<Node> errorNodes = pm.getErrorNodes();

        try {
            for (Node node : variablesNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate(
                        function, pm, node);
                pm.setNodeExpression(node, _template);
            }

            for (Node node : errorNodes) {
                String _template = TemplateExpander.getInstance().expandTemplate(error, pm, node);
                pm.setNodeExpression(node, _template);
            }

            for (String p : pm.getParameters()) {
                pm.setParameterExpression(p, parameters);
            }
        } catch (ParseException e) {
            System.out.println(e);
        }

        return pm;
    }

    private void print(GeneralizedSemPm pm) {
        if (printStuff) {
            System.out.println(pm);
        }
    }

    private void print(List<Node> errorNodes) {
        if (printStuff) {
            System.out.println(errorNodes);
        }
    }

    private void print(GeneralizedSemIm im) {
        if (printStuff) {
            System.out.println(im);
        }
    }

    private void print(DataSet dataSet) {
        if (printStuff) {
            System.out.println(dataSet);
        }
    }

    private void print(String x) {
        if (printStuff) {
            System.out.println(x);
        }
    }


    private void print(SemPm semPm) {
        if (printStuff) {
            System.out.println(semPm);
        }
    }

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(2999983L);

        int sampleSize = 1000;

        List<Node> variableNodes = new ArrayList<>();
        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");
        ContinuousVariable x3 = new ContinuousVariable("X3");
        ContinuousVariable x4 = new ContinuousVariable("X4");
        ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        Graph _graph = new EdgeListGraph(variableNodes);
        SemGraph graph = new SemGraph(_graph);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x2, x5);

        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);
        DataSet dataSet = semIm.simulateData(sampleSize, false);

        print(semPm);

        GeneralizedSemPm _semPm = new GeneralizedSemPm(semPm);
        GeneralizedSemIm _semIm = new GeneralizedSemIm(_semPm, semIm);
        DataSet _dataSet = _semIm.simulateDataMinimizeSurface(sampleSize, false);

        print(_semPm);

//        System.out.println(_dataSet);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            double[] col = dataSet.getDoubleData().getColumn(j).toArray();
            double[] _col = _dataSet.getDoubleData().getColumn(j).toArray();

            double mean = StatUtils.mean(col);
            double _mean = StatUtils.mean(_col);

            double variance = StatUtils.variance(col);
            double _variance = StatUtils.variance(_col);

            assertEquals(mean, _mean, 0.3);
            assertEquals(1.0, variance / _variance, .2);
        }
    }

    @Test
    public void test3() {
        RandomUtil.getInstance().setSeed(49293843L);

        List<Node> variableNodes = new ArrayList<>();
        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");
        ContinuousVariable x3 = new ContinuousVariable("X3");
        ContinuousVariable x4 = new ContinuousVariable("X4");
        ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        Graph _graph = new EdgeListGraph(variableNodes);
        SemGraph graph = new SemGraph(_graph);
        graph.setShowErrorTerms(true);

        Node e1 = graph.getExogenous(x1);
        Node e2 = graph.getExogenous(x2);
        Node e3 = graph.getExogenous(x3);
        Node e4 = graph.getExogenous(x4);
        Node e5 = graph.getExogenous(x5);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x2, x5);
        graph.addDirectedEdge(x5, x1);

        GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        List<Node> variablesNodes = pm.getVariableNodes();
        print(variablesNodes);

        List<Node> errorNodes = pm.getErrorNodes();
        print(errorNodes);


        try {
            pm.setNodeExpression(x1, "cos(b1) + a1 * X5 + E_X1");
            pm.setNodeExpression(x2, "a2 * X1 + E_X2");
            pm.setNodeExpression(x3, "tan(a3*X2 + a4*X1) + E_X3");
            pm.setNodeExpression(x4, "0.1 * E^X2 + X3 + E_X4");
            pm.setNodeExpression(x5, "0.1 * E^X4 + a6* X2 + E_X5");
            pm.setNodeExpression(e1, "U(0, 1)");
            pm.setNodeExpression(e2, "U(0, 1)");
            pm.setNodeExpression(e3, "U(0, 1)");
            pm.setNodeExpression(e4, "U(0, 1)");
            pm.setNodeExpression(e5, "U(0, 1)");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print(im);

            DataSet dataSet = im.simulateDataNSteps(1000, false);

//            System.out.println(dataSet);

            double[] d1 = dataSet.getDoubleData().getColumn(0).toArray();
            double[] d2 = dataSet.getDoubleData().getColumn(1).toArray();

            double cov = StatUtils.covariance(d1, d2);

            assertEquals(-0.002, cov, 0.001);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        // For X3

        Map<String, String[]> templates = new HashMap<>();

        templates.put("NEW(b) + NEW(b) + NEW(c) + NEW(c) + NEW(c)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("NEW(X1) + NEW(b) + NEW(c) + NEW(c) + NEW(c)", new String[]{});
        templates.put("$", new String[]{});
        templates.put("TSUM($)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("TPROD($)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("TPROD($) + X2", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("TPROD($) + TSUM($)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("tan(TSUM(NEW(a)*$))", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("Normal(0, 1)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("Normal(m, s)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("Normal(NEW(m), s)", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("Normal(NEW(m), NEW(s)) + m1 + s6", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("TSUM($) + a", new String[]{"X1", "X2", "X3", "X4", "X5"});
        templates.put("TSUM($) + TSUM($) + TSUM($) + 1", new String[]{"X1", "X2", "X3", "X4", "X5"});

        for (String template : templates.keySet()) {
            GeneralizedSemPm semPm = makeTypicalPm();
            print(semPm.getGraph().toString());

            Set<Node> shouldWork = new HashSet<>();

            for (String name : templates.get(template)) {
                shouldWork.add(semPm.getNode(name));
            }

            Set<Node> works = new HashSet<>();

            for (int i = 0; i < semPm.getNodes().size(); i++) {
                print("-----------");
                print(semPm.getNodes().get(i).toString());
                print("Trying template: " + template);
                String _template = template;

                Node node = semPm.getNodes().get(i);

                try {
                    _template = TemplateExpander.getInstance().expandTemplate(_template, semPm, node);
                } catch (Exception e) {
                    print("Couldn't expand template: " + template);
                    continue;
                }

                try {
                    semPm.setNodeExpression(node, _template);
                    print("Set formula " + _template + " for " + node);

                    if (semPm.getVariableNodes().contains(node)) {
                        works.add(node);
                    }

                } catch (Exception e) {
                    print("Couldn't set formula " + _template + " for " + node);
                }
            }

            for (String parameter : semPm.getParameters()) {
                print("-----------");
                print(parameter);
                print("Trying template: " + template);
                String _template = template;

                try {
                    _template = TemplateExpander.getInstance().expandTemplate(_template, semPm, null);
                } catch (Exception e) {
                    print("Couldn't expand template: " + template);
                    continue;
                }

                try {
                    semPm.setParameterExpression(parameter, _template);
                    print("Set formula " + _template + " for " + parameter);
                } catch (Exception e) {
                    print("Couldn't set formula " + _template + " for " + parameter);
                }
            }

            assertEquals(shouldWork, works);
        }
    }

    @Test
    public void test5() {
        RandomUtil.getInstance().setSeed(29999483L);

        List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 5; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false));
        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);

        semIm.simulateDataReducedForm(1000, false);

        GeneralizedSemPm pm = new GeneralizedSemPm(semPm);
        GeneralizedSemIm im = new GeneralizedSemIm(pm, semIm);

        TetradVector e = new TetradVector(5);

        for (int i = 0; i < e.size(); i++) {
            e.set(i, RandomUtil.getInstance().nextNormal(0, 1));
        }

        TetradVector record1 = semIm.simulateOneRecord(e);
        TetradVector record2 = im.simulateOneRecord(e);

        print("XXX1" + e);
        print("XXX2" + record1);
        print("XXX3" + record2);

        for (int i = 0; i < record1.size(); i++) {
            assertEquals(record1.get(i), record2.get(i), 1e-10);
        }
    }

    @Test
    public void test6() {
        RandomUtil.getInstance().setSeed(29999483L);

        int numVars = 5;

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numVars, 30, 15, 15, false, true);

        SemPm spm = new SemPm(graph);

        Parameters params = new Parameters();
        params.set("coefLow", 0.5);
        params.set("coefHigh", 1.5);
        params.set("varLow", 1);
        params.set("varHigh", 3);

        SemIm sim = new SemIm(spm, params);

        GeneralizedSemPm pm = new GeneralizedSemPm(spm);
        GeneralizedSemIm im = new GeneralizedSemIm(pm, sim);

        DataSet data = im.simulateData(1000, false);

        print(im);

        GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        GeneralizedSemIm estIm = estimator.estimate(pm, data);

        print(estIm);
        print(estimator.getReport());

        double aSquaredStar = estimator.getaSquaredStar();

        assertEquals(1.04, aSquaredStar, 0.01);
    }

    @Test
    public void test7() {
        RandomUtil.getInstance().setSeed(29999483L);

        List<Node> nodes = new ArrayList<>();
        int numVars = 10;

        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numVars, 30, 15, 15, false, true);

        GeneralizedSemPm pm = new GeneralizedSemPm(graph);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);

        print(im);

        DataSet data = im.simulateDataRecursive(1000, false);

        GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        GeneralizedSemIm estIm = estimator.estimate(pm, data);

        print(estIm);
        print(estimator.getReport());

        double aSquaredStar = estimator.getaSquaredStar();

        assertEquals(0.67, aSquaredStar, 0.01);
    }

    @Test
    public void test8() {
        RandomUtil.getInstance().setSeed(29999483L);

        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");

        List<Node> nodes = new ArrayList<>();
        nodes.add(x);
        nodes.add(y);

        Graph graph = new EdgeListGraphSingleConnections(nodes);

        graph.addDirectedEdge(x, y);

        SemPm spm = new SemPm(graph);
        SemIm sim = new SemIm(spm);

        sim.setEdgeCoef(x, y, 20);
        sim.setErrVar(x, 1);
        sim.setErrVar(y, 1);

        GeneralizedSemPm pm = new GeneralizedSemPm(spm);
        GeneralizedSemIm im = new GeneralizedSemIm(pm, sim);

        print(im);

        try {
            pm.setParameterEstimationInitializationExpression("b1", "U(10, 30)");
            pm.setParameterEstimationInitializationExpression("T1", "U(.1, 3)");
            pm.setParameterEstimationInitializationExpression("T2", "U(.1, 3)");
        } catch (ParseException e) {
            e.printStackTrace();
        }

        DataSet data = im.simulateDataRecursive(1000, false);

        GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        GeneralizedSemIm estIm = estimator.estimate(pm, data);

        print(estIm);
//        System.out.println(estimator.getReport());

        double aSquaredStar = estimator.getaSquaredStar();

        assertEquals(0.69, aSquaredStar, 0.01);
    }

    @Test
    public void test9() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * tan(X1) + E_X2");
            pm.setNodeExpression(x3, "a2 * tan(X2) + E_X3");
            pm.setNodeExpression(x4, "a3 * tan(X1) + a4 * tan(X3) ^ 2 + E_X4");

//            pm.setNodeExpression(x1, "E_X1");
//            pm.setNodeExpression(x2, "a1 * X1^2 + E_X2");
//            pm.setNodeExpression(x3, "a2 * X2^2 + E_X3");
//            pm.setNodeExpression(x4, "a3 * X1^2 + a4 * X3 ^ 2 + E_X4");
//
            pm.setNodeExpression(pm.getErrorNode(x1), "Beta(5, 2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Beta(2, 5)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Beta(1, 3)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Beta(1, 7)");

            pm.setParameterEstimationInitializationExpression("c1", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c2", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c3", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c4", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c5", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c6", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c7", "U(1, 3)");
            pm.setParameterEstimationInitializationExpression("c8", "U(1, 3)");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            pm.setNodeExpression(pm.getErrorNode(x1), "Beta(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Beta(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Beta(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Beta(c7, c8)");

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());


            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(0.62, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test10() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * tan(X1) + E_X2");
            pm.setNodeExpression(x3, "a2 * tan(X2) + E_X3");
            pm.setNodeExpression(x4, "a3 * tan(X1) * a4 * tan(X3) ^ 2 + E_X4");
//
//            pm.setNodeExpression(x2, "a1 * X1 + E_X2");
//            pm.setNodeExpression(x3, "a2 * X2 + E_X3");
//            pm.setNodeExpression(x4, "a3 * X1 + a4 * X3 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "Gamma(5, 2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Gamma(5, 2)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Gamma(5, 2)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Gamma(5, 2)");

            pm.setParameterEstimationInitializationExpression("c1", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c2", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c3", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c4", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c5", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c6", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c7", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c8", "U(1, 5)");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            pm.setNodeExpression(pm.getErrorNode(x1), "Gamma(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Gamma(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Gamma(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Gamma(c7, c8)");


            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(1.42, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test11() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * tan(X1) + E_X2");
            pm.setNodeExpression(x3, "a2 * tan(X2) + E_X3");
            pm.setNodeExpression(x4, "a3 * tan(X1) + a4 * tan(X3) ^ 2 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "N(0, c1)");
            pm.setNodeExpression(pm.getErrorNode(x2), "N(0, c2)");
            pm.setNodeExpression(pm.getErrorNode(x3), "N(0, c3)");
            pm.setNodeExpression(pm.getErrorNode(x4), "N(0, c4)");

            pm.setParameterExpression("c1", "4");
            pm.setParameterExpression("c2", "4");
            pm.setParameterExpression("c3", "4");
            pm.setParameterExpression("c4", "4");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 8);
            imInit.setParameterValue("c2", 8);
            imInit.setParameterValue("c3", 8);
            imInit.setParameterValue("c4", 8);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(7.07, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test12() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * tan(X1) + E_X2");
            pm.setNodeExpression(x3, "a2 * tan(X2) + E_X3");
            pm.setNodeExpression(x4, "a3 * tan(X1) + a4 * tan(X3) ^ 2 + E_X4");
//
//            pm.setNodeExpression(x2, "a1 * X1 + E_X1");
//            pm.setNodeExpression(x3, "a2 * X2 + E_X1");
//            pm.setNodeExpression(x4, "a3 * X1 + a4 * X3 + E_X1");

            pm.setNodeExpression(pm.getErrorNode(x1), "Normal(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Normal(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Normal(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Normal(c7, c8)");

            pm.setParameterExpression("c1", "1");
            pm.setParameterExpression("c2", "5");
            pm.setParameterExpression("c3", "1");
            pm.setParameterExpression("c4", "5");
            pm.setParameterExpression("c5", "1");
            pm.setParameterExpression("c6", "5");
            pm.setParameterExpression("c7", "1");
            pm.setParameterExpression("c8", "5");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 3);
            imInit.setParameterValue("c2", 4);
            imInit.setParameterValue("c3", 3);
            imInit.setParameterValue("c4", 4);
            imInit.setParameterValue("c5", 3);
            imInit.setParameterValue("c6", 4);
            imInit.setParameterValue("c7", 3);
            imInit.setParameterValue("c8", 4);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(2.56, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test13() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * tan(X1) + E_X1");
            pm.setNodeExpression(x3, "a2 * tan(X2) + E_X1");
            pm.setNodeExpression(x4, "a3 * tan(X1) + a4 * tan(X3) + E_X1");
//
//            pm.setNodeExpression(x1, "E_X1");
//            pm.setNodeExpression(x2, "a1 * X1 + E_X2");
//            pm.setNodeExpression(x3, "a2 * X2 + E_X3");
//            pm.setNodeExpression(x4, "a3 * X1 + a4 * X3 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "StudentT(c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "StudentT(c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "StudentT(c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "StudentT(c8)");
//
//            pm.setNodeExpression(pm.getErrorNode(x1), "N(0, c2)");
//            pm.setNodeExpression(pm.getErrorNode(x2), "N(0, c4)");
//            pm.setNodeExpression(pm.getErrorNode(x3), "N(0, c6)");
//            pm.setNodeExpression(pm.getErrorNode(x4), "N(0, c8)");

            pm.setParameterExpression("c2", "3");
            pm.setParameterExpression("c4", "3");
            pm.setParameterExpression("c6", "3");
            pm.setParameterExpression("c8", "3");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(500, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c2", 1);
            imInit.setParameterValue("c4", 1);
            imInit.setParameterValue("c6", 1);
            imInit.setParameterValue("c8", 1);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(3.19, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test14() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * tan(X1) + E_X2");
            pm.setNodeExpression(x3, "a2 * tan(X2) + E_X3");
            pm.setNodeExpression(x4, "a3 * tan(X1) + a4 * tan(X3) ^ 2 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "N(0, c1)");
            pm.setNodeExpression(pm.getErrorNode(x2), "N(0, c2)");
            pm.setNodeExpression(pm.getErrorNode(x3), "N(0, c3)");
            pm.setNodeExpression(pm.getErrorNode(x4), "N(0, c4)");

            pm.setParameterExpression("a1", "1");
            pm.setParameterExpression("a2", "1");
            pm.setParameterExpression("a3", "1");
            pm.setParameterExpression("a4", "1");
            pm.setParameterExpression("c1", "4");
            pm.setParameterExpression("c2", "4");
            pm.setParameterExpression("c3", "4");
            pm.setParameterExpression("c4", "4");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 8);
            imInit.setParameterValue("c2", 8);
            imInit.setParameterValue("c3", 8);
            imInit.setParameterValue("c4", 8);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(71.25, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * X1 + E_X2");
            pm.setNodeExpression(x3, "a2 * X2 + E_X3");
            pm.setNodeExpression(x4, "a3 * X1 + a4 * X3 ^ 2 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "Gamma(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "ChiSquare(c3)");
            pm.setNodeExpression(pm.getErrorNode(x3), "ChiSquare(c4)");
            pm.setNodeExpression(pm.getErrorNode(x4), "ChiSquare(c5)");

            pm.setParameterExpression("c1", "5");
            pm.setParameterExpression("c2", "2");
            pm.setParameterExpression("c3", "10");
            pm.setParameterExpression("c4", "10");
            pm.setParameterExpression("c5", "10");

            pm.setParameterEstimationInitializationExpression("c1", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c2", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c3", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c4", "U(1, 5)");
            pm.setParameterEstimationInitializationExpression("c5", "U(1, 5)");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(.79, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test16() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * X1 + E_X2");
            pm.setNodeExpression(x3, "a2 * X2 + E_X3");
            pm.setNodeExpression(x4, "a3 * X1 + a4 * X3 ^ 2 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "N(0, c1)");
            pm.setNodeExpression(pm.getErrorNode(x2), "N(0, c2)");
            pm.setNodeExpression(pm.getErrorNode(x3), "N(0, c3)");
            pm.setNodeExpression(pm.getErrorNode(x4), "N(0, c4)");

//            pm.setParameterExpression("c1", "1");
//            pm.setParameterExpression("c2", "1");
//            pm.setParameterExpression("c3", "1");
//            pm.setParameterExpression("c4", "1");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            im.setParameterValue("a1", 1);
            im.setParameterValue("a2", 1);
            im.setParameterValue("a3", 1);
            im.setParameterValue("a4", 1);
            im.setParameterValue("c1", 1);
            im.setParameterValue("c2", 1);
            im.setParameterValue("c3", 1);
            im.setParameterValue("c4", 1);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);

            imInit.setParameterValue("a1", .5);
            imInit.setParameterValue("a2", .5);
            imInit.setParameterValue("a3", .5);
            imInit.setParameterValue("a4", .7);
            imInit.setParameterValue("c1", 2);
            imInit.setParameterValue("c2", 2);
            imInit.setParameterValue("c3", 2);
            imInit.setParameterValue("c4", 2);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(50.38, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test17() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
            Node x1 = new GraphNode("X1");
            Node x2 = new GraphNode("X2");
            Node x3 = new GraphNode("X3");
            Node x4 = new GraphNode("X4");

            Graph g = new EdgeListGraphSingleConnections();
            g.addNode(x1);
            g.addNode(x2);
            g.addNode(x3);
            g.addNode(x4);

            g.addDirectedEdge(x1, x2);
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            GeneralizedSemPm pm = new GeneralizedSemPm(g);

            pm.setNodeExpression(x1, "E_X1");
            pm.setNodeExpression(x2, "a1 * X1 + E_X2");
            pm.setNodeExpression(x3, "a2 * X2 + E_X3");
            pm.setNodeExpression(x4, "a3 * X1 * a3 * a4 * X3 + E_X4");

            pm.setNodeExpression(pm.getErrorNode(x1), "N(0, c1)");
            pm.setNodeExpression(pm.getErrorNode(x2), "N(0, c2)");
            pm.setNodeExpression(pm.getErrorNode(x3), "N(0, c3)");
            pm.setNodeExpression(pm.getErrorNode(x4), "N(0, c4)");

//            pm.setParameterExpression("c1", "1");
//            pm.setParameterExpression("c2", "1");
//            pm.setParameterExpression("c3", "1");
//            pm.setParameterExpression("c4", "1");

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            im.setParameterValue("a1", 1);
            im.setParameterValue("a2", 1);
            im.setParameterValue("a3", 1);
            im.setParameterValue("a4", 1);
            im.setParameterValue("c1", 1);
            im.setParameterValue("c2", 1);
            im.setParameterValue("c3", 1);
            im.setParameterValue("c4", 1);

            print("True model: ");
            print(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);

            imInit.setParameterValue("a1", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("a2", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("a3", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("a4", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("c1", RandomUtil.getInstance().nextUniform(1, 3));
            imInit.setParameterValue("c2", RandomUtil.getInstance().nextUniform(1, 3));
            imInit.setParameterValue("c3", RandomUtil.getInstance().nextUniform(1, 3));
            imInit.setParameterValue("c4", RandomUtil.getInstance().nextUniform(1, 3));

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(14.26, aSquaredStar, 0.01);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private GeneralizedSemPm makeTypicalPm() {
        List<Node> variableNodes = new ArrayList<>();
        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");
        ContinuousVariable x3 = new ContinuousVariable("X3");
        ContinuousVariable x4 = new ContinuousVariable("X4");
        ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        Graph _graph = new EdgeListGraph(variableNodes);
        SemGraph graph = new SemGraph(_graph);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x2, x5);

        return new GeneralizedSemPm(graph);
    }
}


