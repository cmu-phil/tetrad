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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.Vector;
import org.junit.Ignore;
import org.junit.Test;

import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Joseph Ramsey
 */
@Ignore
public class TestGeneralizedSem {

    private final boolean printStuff = false;

    @Test
    public void test1() {
        final GeneralizedSemPm pm = makeTypicalPm();

        print(pm);

        final Node x1 = pm.getNode("X1");
        final Node x2 = pm.getNode("X2");
        final Node x3 = pm.getNode("X3");
        final Node x4 = pm.getNode("X4");
        final Node x5 = pm.getNode("X5");

        final SemGraph graph = pm.getGraph();

        final List<Node> variablesNodes = pm.getVariableNodes();
        print(variablesNodes);

        final List<Node> errorNodes = pm.getErrorNodes();
        print(errorNodes);


        try {
            pm.setNodeExpression(x1, "cos(B1) + E_X1");
            print(pm);

            final String b1 = "B1";
            final String b2 = "B2";
            final String b3 = "B3";

            final Set<Node> nodes = pm.getReferencingNodes(b1);

            assertTrue(nodes.contains(x1));
            assertTrue(!nodes.contains(x2) && !nodes.contains(x2));

            final Set<String> referencedParameters = pm.getReferencedParameters(x3);

            print("Parameters referenced by X3 are: " + referencedParameters);

            assertTrue(referencedParameters.contains(b1) && referencedParameters.contains(b2));
            assertTrue(!(referencedParameters.contains(b1) && referencedParameters.contains(b3)));

            final Node e_x3 = pm.getNode("E_X3");
//
            for (final Node node : pm.getNodes()) {
                final Set<Node> referencingNodes = pm.getReferencingNodes(node);
                print("Nodes referencing " + node + " are: " + referencingNodes);
            }

            for (final Node node : pm.getVariableNodes()) {
                final Set<Node> referencingNodes = pm.getReferencedNodes(node);
                print("Nodes referenced by " + node + " are: " + referencingNodes);
            }

            final Set<Node> referencingX3 = pm.getReferencingNodes(x3);
            assertTrue(referencingX3.contains(x4));
            assertTrue(!referencingX3.contains(x5));

            final Set<Node> referencedByX3 = pm.getReferencedNodes(x3);
            assertTrue(referencedByX3.contains(x1) && referencedByX3.contains(x2) && referencedByX3.contains(e_x3)
                    && !referencedByX3.contains(x4));

            pm.setNodeExpression(x5, "a * E^X2 + X4 + E_X5");

            final Node e_x5 = pm.getErrorNode(x5);

            graph.setShowErrorTerms(true);
            assertTrue(e_x5.equals(graph.getExogenous(x5)));

            pm.setNodeExpression(e_x5, "Beta(3, 5)");

            print(pm);

            assertEquals("Split(-1.5,-.5,.5,1.5)", pm.getParameterExpressionString(b1));
            pm.setParameterExpression(b1, "N(0, 2)");
            assertEquals("N(0, 2)", pm.getParameterExpressionString(b1));

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print(im);

            final DataSet dataSet = im.simulateDataFisher(10);

            print(dataSet);

        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    private void print(final GeneralizedSemPm pm) {
        if (this.printStuff) {
            System.out.println(pm);
        }
    }

    private void print(final List<Node> errorNodes) {
        if (this.printStuff) {
            System.out.println(errorNodes);
        }
    }

    private void print(final GeneralizedSemIm im) {
        if (this.printStuff) {
            System.out.println(im);
        }
    }

    private void print(final DataSet dataSet) {
        if (this.printStuff) {
            System.out.println(dataSet);
        }
    }

    private void print(final String x) {
        if (this.printStuff) {
            System.out.println(x);
        }
    }


    private void print(final SemPm semPm) {
        if (this.printStuff) {
            System.out.println(semPm);
        }
    }

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(2999983L);

        final int sampleSize = 1000;

        final List<Node> variableNodes = new ArrayList<>();
        final ContinuousVariable x1 = new ContinuousVariable("X1");
        final ContinuousVariable x2 = new ContinuousVariable("X2");
        final ContinuousVariable x3 = new ContinuousVariable("X3");
        final ContinuousVariable x4 = new ContinuousVariable("X4");
        final ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        final Graph _graph = new EdgeListGraph(variableNodes);
        final SemGraph graph = new SemGraph(_graph);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x2, x5);

        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm);
        final DataSet dataSet = semIm.simulateData(sampleSize, false);

        print(semPm);

        final GeneralizedSemPm _semPm = new GeneralizedSemPm(semPm);
        final GeneralizedSemIm _semIm = new GeneralizedSemIm(_semPm, semIm);
        final DataSet _dataSet = _semIm.simulateDataMinimizeSurface(sampleSize, false);

        print(_semPm);

//        System.out.println(_dataSet);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final double[] col = dataSet.getDoubleData().getColumn(j).toArray();
            final double[] _col = _dataSet.getDoubleData().getColumn(j).toArray();

            final double mean = StatUtils.mean(col);
            final double _mean = StatUtils.mean(_col);

            final double variance = StatUtils.variance(col);
            final double _variance = StatUtils.variance(_col);

            assertEquals(mean, _mean, 0.3);
            assertEquals(1.0, variance / _variance, .2);
        }
    }

    @Test
    public void test3() {
        RandomUtil.getInstance().setSeed(49293843L);

        final List<Node> variableNodes = new ArrayList<>();
        final ContinuousVariable x1 = new ContinuousVariable("X1");
        final ContinuousVariable x2 = new ContinuousVariable("X2");
        final ContinuousVariable x3 = new ContinuousVariable("X3");
        final ContinuousVariable x4 = new ContinuousVariable("X4");
        final ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        final Graph _graph = new EdgeListGraph(variableNodes);
        final SemGraph graph = new SemGraph(_graph);
        graph.setShowErrorTerms(true);

        final Node e1 = graph.getExogenous(x1);
        final Node e2 = graph.getExogenous(x2);
        final Node e3 = graph.getExogenous(x3);
        final Node e4 = graph.getExogenous(x4);
        final Node e5 = graph.getExogenous(x5);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x2, x5);
        graph.addDirectedEdge(x5, x1);

        final GeneralizedSemPm pm = new GeneralizedSemPm(graph);

        final List<Node> variablesNodes = pm.getVariableNodes();
        print(variablesNodes);

        final List<Node> errorNodes = pm.getErrorNodes();
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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print(im);

            final DataSet dataSet = im.simulateDataNSteps(1000, false);

//            System.out.println(dataSet);

            final double[] d1 = dataSet.getDoubleData().getColumn(0).toArray();
            final double[] d2 = dataSet.getDoubleData().getColumn(1).toArray();

            final double cov = StatUtils.covariance(d1, d2);

            assertEquals(-0.002, cov, 0.001);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test4() {
        // For X3

        final Map<String, String[]> templates = new HashMap<>();

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

        for (final String template : templates.keySet()) {
            final GeneralizedSemPm semPm = makeTypicalPm();
            print(semPm.getGraph().toString());

            final Set<Node> shouldWork = new HashSet<>();

            for (final String name : templates.get(template)) {
                shouldWork.add(semPm.getNode(name));
            }

            final Set<Node> works = new HashSet<>();

            for (int i = 0; i < semPm.getNodes().size(); i++) {
                print("-----------");
                print(semPm.getNodes().get(i).toString());
                print("Trying template: " + template);
                String _template = template;

                final Node node = semPm.getNodes().get(i);

                try {
                    _template = TemplateExpander.getInstance().expandTemplate(_template, semPm, node);
                } catch (final Exception e) {
                    print("Couldn't expand template: " + template);
                    continue;
                }

                try {
                    semPm.setNodeExpression(node, _template);
                    print("Set formula " + _template + " for " + node);

                    if (semPm.getVariableNodes().contains(node)) {
                        works.add(node);
                    }

                } catch (final Exception e) {
                    print("Couldn't set formula " + _template + " for " + node);
                }
            }

            for (final String parameter : semPm.getParameters()) {
                print("-----------");
                print(parameter);
                print("Trying template: " + template);
                String _template = template;

                try {
                    _template = TemplateExpander.getInstance().expandTemplate(_template, semPm, null);
                } catch (final Exception e) {
                    print("Couldn't expand template: " + template);
                    continue;
                }

                try {
                    semPm.setParameterExpression(parameter, _template);
                    print("Set formula " + _template + " for " + parameter);
                } catch (final Exception e) {
                    print("Couldn't set formula " + _template + " for " + parameter);
                }
            }

            assertEquals(shouldWork, works);
        }
    }

    @Test
    public void test5() {
        RandomUtil.getInstance().setSeed(29999483L);

        final List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 5; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        final Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false));
        final SemPm semPm = new SemPm(graph);
        final SemIm semIm = new SemIm(semPm);

        semIm.simulateDataReducedForm(1000, false);

        final GeneralizedSemPm pm = new GeneralizedSemPm(semPm);
        final GeneralizedSemIm im = new GeneralizedSemIm(pm, semIm);

        final Vector e = new Vector(5);

        for (int i = 0; i < e.size(); i++) {
            e.set(i, RandomUtil.getInstance().nextNormal(0, 1));
        }

        final Vector record1 = semIm.simulateOneRecord(e);
        final Vector record2 = im.simulateOneRecord(e);

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

        final int numVars = 5;

        final List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        final Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numVars, 30, 15, 15, false, true);

        final SemPm spm = new SemPm(graph);

        final Parameters params = new Parameters();
        params.set("coefLow", 0.5);
        params.set("coefHigh", 1.5);
        params.set("varLow", 1);
        params.set("varHigh", 3);

        final SemIm sim = new SemIm(spm, params);

        final GeneralizedSemPm pm = new GeneralizedSemPm(spm);
        final GeneralizedSemIm im = new GeneralizedSemIm(pm, sim);

        final DataSet data = im.simulateData(1000, false);

        print(im);

        final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        final GeneralizedSemIm estIm = estimator.estimate(pm, data);

        print(estIm);
        print(estimator.getReport());

        final double aSquaredStar = estimator.getaSquaredStar();

        assertEquals(1.04, aSquaredStar, 0.01);
    }

    @Test
    public void test7() {
        RandomUtil.getInstance().setSeed(29999483L);

        final List<Node> nodes = new ArrayList<>();
        final int numVars = 10;

        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        final Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numVars, 30, 15, 15, false, true);

        final GeneralizedSemPm pm = new GeneralizedSemPm(graph);
        final GeneralizedSemIm im = new GeneralizedSemIm(pm);

        print(im);

        final DataSet data = im.simulateDataRecursive(1000, false);

        final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        final GeneralizedSemIm estIm = estimator.estimate(pm, data);

        print(estIm);
        print(estimator.getReport());

        final double aSquaredStar = estimator.getaSquaredStar();

        assertEquals(0.67, aSquaredStar, 0.01);
    }

    @Test
    public void test8() {
        RandomUtil.getInstance().setSeed(29999483L);

        final Node x = new GraphNode("X");
        final Node y = new GraphNode("Y");

        final List<Node> nodes = new ArrayList<>();
        nodes.add(x);
        nodes.add(y);

        final Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x, y);

        final SemPm spm = new SemPm(graph);
        final SemIm sim = new SemIm(spm);

        sim.setEdgeCoef(x, y, 20);
        sim.setErrVar(x, 1);
        sim.setErrVar(y, 1);

        final GeneralizedSemPm pm = new GeneralizedSemPm(spm);
        final GeneralizedSemIm im = new GeneralizedSemIm(pm, sim);

        print(im);

        try {
            pm.setParameterEstimationInitializationExpression("b1", "U(10, 30)");
            pm.setParameterEstimationInitializationExpression("T1", "U(.1, 3)");
            pm.setParameterEstimationInitializationExpression("T2", "U(.1, 3)");
        } catch (final ParseException e) {
            e.printStackTrace();
        }

        final DataSet data = im.simulateDataRecursive(1000, false);

        final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        final GeneralizedSemIm estIm = estimator.estimate(pm, data);

        print(estIm);
//        System.out.println(estimator.getReport());

        final double aSquaredStar = estimator.getaSquaredStar();

        assertEquals(0.69, aSquaredStar, 0.01);
    }

    @Test
    public void test9() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(1000, false);

            pm.setNodeExpression(pm.getErrorNode(x1), "Beta(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Beta(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Beta(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Beta(c7, c8)");

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());


            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(0.62, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test10() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(1000, false);

            pm.setNodeExpression(pm.getErrorNode(x1), "Gamma(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Gamma(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Gamma(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Gamma(c7, c8)");


            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(1.42, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test11() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(1000, false);

            final GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 8);
            imInit.setParameterValue("c2", 8);
            imInit.setParameterValue("c3", 8);
            imInit.setParameterValue("c4", 8);

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(7.07, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test12() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(1000, false);

            final GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 3);
            imInit.setParameterValue("c2", 4);
            imInit.setParameterValue("c3", 3);
            imInit.setParameterValue("c4", 4);
            imInit.setParameterValue("c5", 3);
            imInit.setParameterValue("c6", 4);
            imInit.setParameterValue("c7", 3);
            imInit.setParameterValue("c8", 4);

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(2.56, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test13() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(500, false);

            final GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c2", 1);
            imInit.setParameterValue("c4", 1);
            imInit.setParameterValue("c6", 1);
            imInit.setParameterValue("c8", 1);

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(3.19, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test14() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(1000, false);

            final GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 8);
            imInit.setParameterValue("c2", 8);
            imInit.setParameterValue("c3", 8);
            imInit.setParameterValue("c4", 8);

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(71.25, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test15() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print("True model: ");
            print(im);

            final DataSet data = im.simulateDataRecursive(1000, false);

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(.79, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test16() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

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

            final DataSet data = im.simulateDataRecursive(1000, false);

            final GeneralizedSemIm imInit = new GeneralizedSemIm(pm);

            imInit.setParameterValue("a1", .5);
            imInit.setParameterValue("a2", .5);
            imInit.setParameterValue("a3", .5);
            imInit.setParameterValue("a4", .7);
            imInit.setParameterValue("c1", 2);
            imInit.setParameterValue("c2", 2);
            imInit.setParameterValue("c3", 2);
            imInit.setParameterValue("c4", 2);

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(50.38, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test17() {
        RandomUtil.getInstance().setSeed(29999483L);

        try {
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
            g.addDirectedEdge(x2, x3);
            g.addDirectedEdge(x3, x4);
            g.addDirectedEdge(x1, x4);

            final GeneralizedSemPm pm = new GeneralizedSemPm(g);

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

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

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

            final DataSet data = im.simulateDataRecursive(1000, false);

            final GeneralizedSemIm imInit = new GeneralizedSemIm(pm);

            imInit.setParameterValue("a1", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("a2", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("a3", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("a4", RandomUtil.getInstance().nextUniform(-3, 3));
            imInit.setParameterValue("c1", RandomUtil.getInstance().nextUniform(1, 3));
            imInit.setParameterValue("c2", RandomUtil.getInstance().nextUniform(1, 3));
            imInit.setParameterValue("c3", RandomUtil.getInstance().nextUniform(1, 3));
            imInit.setParameterValue("c4", RandomUtil.getInstance().nextUniform(1, 3));

            final GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            final GeneralizedSemIm estIm = estimator.estimate(pm, data);

            print("\n\n\nEstimated model: ");
            print(estIm);
            print(estimator.getReport());

            final double aSquaredStar = estimator.getaSquaredStar();

            assertEquals(14.26, aSquaredStar, 0.01);
        } catch (final ParseException e) {
            e.printStackTrace();
        }
    }

    private GeneralizedSemPm makeTypicalPm() {
        final List<Node> variableNodes = new ArrayList<>();
        final ContinuousVariable x1 = new ContinuousVariable("X1");
        final ContinuousVariable x2 = new ContinuousVariable("X2");
        final ContinuousVariable x3 = new ContinuousVariable("X3");
        final ContinuousVariable x4 = new ContinuousVariable("X4");
        final ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        final Graph _graph = new EdgeListGraph(variableNodes);
        final SemGraph graph = new SemGraph(_graph);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x2, x5);

        return new GeneralizedSemPm(graph);
    }
}


