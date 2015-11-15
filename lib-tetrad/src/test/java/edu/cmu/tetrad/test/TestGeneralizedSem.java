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

import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.math3.distribution.RealDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Tests Sem.
 *
 * @author Joseph Ramsey
 */
public class TestGeneralizedSem extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestGeneralizedSem(String name) {
        super(name);
    }

    public void test1() {
        GeneralizedSemPm pm = makeTypicalPm();

        System.out.println(pm);

        Node x1 = pm.getNode("X1");
        Node x2 = pm.getNode("X2");
        Node x3 = pm.getNode("X3");
        Node x4 = pm.getNode("X4");
        Node x5 = pm.getNode("X5");

        SemGraph graph = pm.getGraph();

        List<Node> variablesNodes = pm.getVariableNodes();
        System.out.println(variablesNodes);

        List<Node> errorNodes = pm.getErrorNodes();
        System.out.println(errorNodes);


        try {
            pm.setNodeExpression(x1, "cos(B1) + E_X1");
            System.out.println(pm);

            String b1 = "B1";
            String b2 = "B2";
            String b3 = "B3";

            Set<Node> nodes = pm.getReferencingNodes(b1);

            assertTrue(nodes.contains(x1));
            assertTrue(!nodes.contains(x2) && !nodes.contains(x2));

            Set<String> referencedParameters = pm.getReferencedParameters(x3);

            System.out.println("Parameters referenced by X3 are: " + referencedParameters);

            assertTrue(referencedParameters.contains(b1) && referencedParameters.contains(b2));
            assertTrue(!(referencedParameters.contains(b1) && referencedParameters.contains(b3)));

            Node e_x3 = pm.getNode("E_X3");
//
            for (Node node : pm.getNodes()) {
                Set<Node> referencingNodes = pm.getReferencingNodes(node);
                System.out.println("Nodes referencing " + node + " are: " + referencingNodes);
            }

            for (Node node : pm.getVariableNodes()) {
                Set<Node> referencingNodes = pm.getReferencedNodes(node);
                System.out.println("Nodes referenced by " + node + " are: " + referencingNodes);
            }

            Set<Node> referencingX3 = pm.getReferencingNodes(x3);
            assertTrue(referencingX3.contains(x4));
            assertTrue(!referencingX3.contains(x5));

            Set<Node> referencedByX3 = pm.getReferencedNodes(x3);
            assertTrue(referencedByX3.contains(x1) && referencedByX3.contains(x2) && referencedByX3.contains(e_x3)
                    && !referencedByX3.contains(x4));

            pm.setNodeExpression(x5, "a * E^X2 + X4 + E_X5");

            Node e_x5 = pm.getErrorNode(x5);

            graph.setShowErrorTerms(true);
            assertTrue(e_x5.equals(graph.getExogenous(x5)));

            pm.setNodeExpression(e_x5, "Beta(3, 5)");

            System.out.println(pm);

            assertEquals("Split(-1.5,-.5,.5,1.5)", pm.getParameterExpressionString(b1));
            pm.setParameterExpression(b1, "N(0, 2)");
            assertEquals("N(0, 2)", pm.getParameterExpressionString(b1));

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            System.out.println(im);

            DataSet dataSet = im.simulateDataAvoidInfinity(10, false);

            System.out.println(dataSet);

        } catch (ParseException e) {
            System.out.println(e);
        }
    }

    public void rtest2() {
        RandomUtil.getInstance().setSeed(2999983L);

        int sampleSize = 1000;

        List<Node> variableNodes = new ArrayList<Node>();
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

        System.out.println(semPm);

        GeneralizedSemPm _semPm = new GeneralizedSemPm(semPm);
        GeneralizedSemIm _semIm = new GeneralizedSemIm(_semPm, semIm);
        DataSet _dataSet = _semIm.simulateDataMinimizeSurface(sampleSize, false);

        System.out.println(_semPm);

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

    public void test3() {
        List<Node> variableNodes = new ArrayList<Node>();
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
        System.out.println(variablesNodes);

        List<Node> errorNodes = pm.getErrorNodes();
        System.out.println(errorNodes);


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

            System.out.println(im);

            DataSet dataSet = im.simulateDataNSteps(1000, false);

//            System.out.println(dataSet);
        } catch (ParseException e) {
            System.out.println(e);
        }
    }

    public void test4() {
        makeSamplePm();
    }

    private GeneralizedSemPm makeSamplePm() {
        // For X3

        Map<String, String[]> templates = new HashMap<String, String[]>();

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
            System.out.println(semPm.getGraph());

            Set<Node> shouldWork = new HashSet<Node>();

            for (String name : templates.get(template)) {
                shouldWork.add(semPm.getNode(name));
            }

            Set<Node> works = new HashSet<Node>();

            for (int i = 0; i < semPm.getNodes().size(); i++) {
                System.out.println("-----------");
                System.out.println(semPm.getNodes().get(i));
                System.out.println("Trying template: " + template);
                String _template = template;

                Node node = semPm.getNodes().get(i);

                try {
                    _template = TemplateExpander.getInstance().expandTemplate(_template, semPm, node);
                } catch (Exception e) {
                    System.out.println("Couldn't expand template: " + template);
                    continue;
                }

                try {
                    semPm.setNodeExpression(node, _template);
                    System.out.println("Set formula " + _template + " for " + node);

                    if (semPm.getVariableNodes().contains(node)) {
                        works.add(node);
                    }

                } catch (Exception e) {
                    System.out.println("Couldn't set formula " + _template + " for " + node);
                }
            }

            for (String parameter : semPm.getParameters()) {
                System.out.println("-----------");
                System.out.println(parameter);
                System.out.println("Trying template: " + template);
                String _template = template;

                try {
                    _template = TemplateExpander.getInstance().expandTemplate(_template, semPm, null);
                } catch (Exception e) {
                    System.out.println("Couldn't expand template: " + template);
                    continue;
                }

                try {
                    semPm.setParameterExpression(parameter, _template);
                    System.out.println("Set formula " + _template + " for " + parameter);
                } catch (Exception e) {
                    System.out.println("Couldn't set formula " + _template + " for " + parameter);
                }
            }

            assertEquals(shouldWork, works);

            return semPm;
        }

        throw new NullPointerException();
    }

    public void test5() {
        RandomUtil.getInstance().setSeed(29999483L);

        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));
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

        System.out.println("XXX1" + e);
        System.out.println("XXX2" + record1);
        System.out.println("XXX3" + record2);

        for (int i = 0; i < record1.size(); i++) {
            assertEquals(record1.get(i), record2.get(i), 1e-10);
        }
    }

    public void test6() {
        RandomUtil.getInstance().setSeed(29999483L);

        int numVars = 5;

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numVars);

        SemPm spm = new SemPm(graph);

        SemImInitializationParams params = new SemImInitializationParams();
        params.setCoefRange(0.5, 1.5);
        params.setVarRange(1, 3);

        SemIm sim = new SemIm(spm, params);

        GeneralizedSemPm pm = new GeneralizedSemPm(spm);
        GeneralizedSemIm im = new GeneralizedSemIm(pm, sim);

        DataSet data = im.simulateData(1000, false);

        System.out.println(im);

        GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        GeneralizedSemIm estIm = estimator.estimate(pm, data);

        System.out.println(estIm);
        System.out.println(estimator.getReport());
    }

    public void test7() {
        RandomUtil.getInstance().setSeed(29999483L);

        List<Node> nodes = new ArrayList<>();
        int numVars = 10;

        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = GraphUtils.randomGraphRandomForwardEdges(nodes, 0, numVars);

        GeneralizedSemPm pm = new GeneralizedSemPm(graph);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);

        System.out.println(im);

        DataSet data = im.simulateDataRecursive(1000, false);

        GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
        GeneralizedSemIm estIm = estimator.estimate(pm, data);

        System.out.println(estIm);
        System.out.println(estimator.getReport());
    }

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

        System.out.println(im);

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

        System.out.println(estIm);
//        System.out.println(estimator.getReport());
    }

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

            System.out.println("True model: ");
            System.out.println(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            pm.setNodeExpression(pm.getErrorNode(x1), "Beta(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Beta(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Beta(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Beta(c7, c8)");

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            pm.setNodeExpression(pm.getErrorNode(x1), "Gamma(c1, c2)");
            pm.setNodeExpression(pm.getErrorNode(x2), "Gamma(c3, c4)");
            pm.setNodeExpression(pm.getErrorNode(x3), "Gamma(c5, c6)");
            pm.setNodeExpression(pm.getErrorNode(x4), "Gamma(c7, c8)");


            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 8);
            imInit.setParameterValue("c2", 8);
            imInit.setParameterValue("c3", 8);
            imInit.setParameterValue("c4", 8);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

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

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

            DataSet data = im.simulateDataRecursive(500, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c2", 1);
            imInit.setParameterValue("c4", 1);
            imInit.setParameterValue("c6", 1);
            imInit.setParameterValue("c8", 1);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemIm imInit = new GeneralizedSemIm(pm);
            imInit.setParameterValue("c1", 8);
            imInit.setParameterValue("c2", 8);
            imInit.setParameterValue("c3", 8);
            imInit.setParameterValue("c4", 8);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

            DataSet data = im.simulateDataRecursive(1000, false);

            GeneralizedSemEstimator estimator = new GeneralizedSemEstimator();
            GeneralizedSemIm estIm = estimator.estimate(pm, data);

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

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

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("True model: ");
            System.out.println(im);

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

            System.out.println("\n\n\nEstimated model: ");
            System.out.println(estIm);
            System.out.println(estimator.getReport());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public void test18() {
        RandomUtil.getInstance().setSeed(29999483L);

        NumberFormat nf = new DecimalFormat("0.0000");

        try {
            List<Double> drawFromDistribution = new ArrayList<>();
            ExpressionParser parser = new ExpressionParser();
            Expression expression = parser.parseExpression("Normal(0, 1)");

            GeneralizedSemEstimator.MyContext context = new GeneralizedSemEstimator.MyContext();

            for (int k = 0; k < 1000; k++) {
                double evaluate = expression.evaluate(context);
                drawFromDistribution.add(evaluate);
            }

            double[] d = new double[drawFromDistribution.size()];
            for (int i = 0; i < drawFromDistribution.size(); i++) d[i] = drawFromDistribution.get(i);

            EmpiricalCdf dist = new EmpiricalCdf(drawFromDistribution);

            for (double x = -3; x <= 3; x+= 0.1) {
                double density = dist.density(x);

//                if (Double.isNaN(density) || Double.isInfinite(density)) {
//                    density = 0.0;
//                }

                System.out.println(nf.format(x) + " " + nf.format(density));
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

    }


    private GeneralizedSemPm makeTypicalPm() {
        List<Node> variableNodes = new ArrayList<Node>();
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

        GeneralizedSemPm semPm = new GeneralizedSemPm(graph);
        return semPm;
    }


    private String replaceNewParameters(GeneralizedSemPm semPm, String formula, List<String> usedNames) {
        String parameterPattern = "\\$|(([a-zA-Z]{1})([a-zA-Z0-9-_/]*))";
        Pattern p = Pattern.compile("NEW\\((" + parameterPattern + ")\\)");

        while (true) {
            Matcher m = p.matcher(formula);

            if (!m.find()) {
                break;
            }

//            String group0 = m.group(0).replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)");
            String group0 = Pattern.quote(m.group(0));
            String group1 = m.group(1);

            String nextName = semPm.nextParameterName(group1, usedNames);
            formula = formula.replaceFirst(group0, nextName);
            usedNames.add(nextName);
//            System.out.println(formula);
        }
        return formula;
    }

    public void testEmpiricalDistribution() {
        NumberFormat nf = new DecimalFormat("0.0000");

        try {
            Graph g = new EdgeListGraphSingleConnections();
            Node x = new GraphNode("X");
            g.addNode(x);
            GeneralizedSemPm pm = new GeneralizedSemPm(g);
            pm.setNodeExpression(x, "E_X");
            Node error = pm.getErrorNode(x);
            pm.setNodeExpression(error, "N(0, 1)");

            GeneralizedSemEstimator.MyContext context = new GeneralizedSemEstimator.MyContext();

            Expression expression = pm.getNodeExpression(error);
            RealDistribution dist1 = expression.getRealDistribution(context);
            RealDistribution dist2 = new EmpiricalDistributionForExpression(pm, error, context).getDist();

            for (double z = -2; z <= 2.01; z += 0.1) {
                System.out.println(nf.format(z) + " " + (nf.format(dist1.density(z) -
                        dist2.density(z))));
//                System.out.println(nf.format(z) + " " + (nf.format(dist1.density(z) -
//                        dist2.density(z))));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestGeneralizedSem.class);
    }
}


