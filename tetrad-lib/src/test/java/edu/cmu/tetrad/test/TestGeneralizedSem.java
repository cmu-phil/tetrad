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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.Vector;
import org.junit.Test;

import java.text.ParseException;
import java.util.*;

import static org.junit.Assert.*;


/**
 * @author josephramsey
 */
//@Ignore
public class TestGeneralizedSem {

    private final boolean printStuff = false;

    @Test
    public void test1() {
        GeneralizedSemPm pm = makeTypicalPm();

        print(pm);

        Node x1 = pm.getNode("X1");
        Node x2 = pm.getNode("X2");
        Node x3 = pm.getNode("X3");
        Node x4 = pm.getNode("X4");
        Node x5 = pm.getNode("X5");

        SemGraph graph = pm.getGraph();

        List<Node> variablesNodes = pm.getVariableNodes();
        print(variablesNodes);

        List<Node> errorNodes = pm.getErrorNodes();
        print(errorNodes);


        try {
            pm.setNodeExpression(x1, "cos(B1) + E_X1");
            print(pm);

            final String b1 = "B1";
            final String b2 = "B2";
            final String b3 = "B3";

            Set<Node> nodes = pm.getReferencingNodes(b1);

            assertTrue(nodes.contains(x1));
            assertTrue(!nodes.contains(x2) && !nodes.contains(x2));

            Set<String> referencedParameters = pm.getReferencedParameters(x3);

            print("Parameters referenced by X3 are: " + referencedParameters);

            assertTrue(referencedParameters.contains(b1) && referencedParameters.contains(b2));
            assertFalse(referencedParameters.contains(b1) && referencedParameters.contains(b3));

            Node e_x3 = pm.getNode("E_X3");
//
            for (Node node : pm.getNodes()) {
                Set<Node> referencingNodes = pm.getReferencingNodes(node);
                print("Nodes referencing " + node + " are: " + referencingNodes);
            }

            for (Node node : pm.getVariableNodes()) {
                Set<Node> referencingNodes = pm.getReferencedNodes(node);
                print("Nodes referenced by " + node + " are: " + referencingNodes);
            }

            Set<Node> referencingX3 = pm.getReferencingNodes(x3);
            assertTrue(referencingX3.contains(x4));
            assertFalse(referencingX3.contains(x5));

            Set<Node> referencedByX3 = pm.getReferencedNodes(x3);
            assertTrue(referencedByX3.contains(x1) && referencedByX3.contains(x2) && referencedByX3.contains(e_x3)
                       && !referencedByX3.contains(x4));

            pm.setNodeExpression(x5, "a * E^X2 + X4 + E_X5");

            Node e_x5 = pm.getErrorNode(x5);

            graph.setShowErrorTerms(true);
            assertEquals(e_x5, graph.getExogenous(x5));

            pm.setNodeExpression(e_x5, "Beta(3, 5)");

            print(pm);

            String parameterExpressionString = pm.getParameterExpressionString(b1);
            assertEquals("U(-1.0, 1.0)", parameterExpressionString);
            pm.setParameterExpression(b1, "N(0, 2)");
            assertEquals("N(0, 2)", pm.getParameterExpressionString(b1));

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            print(im);

            DataSet dataSet = im.simulateDataFisher(10);

            print(dataSet);

        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private void print(GeneralizedSemPm pm) {
        if (this.printStuff) {
            System.out.println(pm);
        }
    }

    private void print(List<Node> errorNodes) {
        if (this.printStuff) {
            System.out.println(errorNodes);
        }
    }

    private void print(GeneralizedSemIm im) {
        if (this.printStuff) {
            System.out.println(im);
        }
    }

    private void print(DataSet dataSet) {
        if (this.printStuff) {
            System.out.println(dataSet);
        }
    }

    private void print(String x) {
        if (this.printStuff) {
            System.out.println(x);
        }
    }


    private void print(SemPm semPm) {
        if (this.printStuff) {
            System.out.println(semPm);
        }
    }

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(2999983L);

        final int sampleSize = 1000;

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

        Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 5,
                30, 15, 15, false));
        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);

        semIm.simulateDataReducedForm(1000, false);

        GeneralizedSemPm pm = new GeneralizedSemPm(semPm);
        GeneralizedSemIm im = new GeneralizedSemIm(pm, semIm);

        Vector e = new Vector(5);

        for (int i = 0; i < e.size(); i++) {
            e.set(i, RandomUtil.getInstance().nextNormal(0, 1));
        }

        Vector record1 = semIm.simulateOneRecord(e);
        Vector record2 = im.simulateOneRecord(e);

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

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < numVars; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph graph = RandomGraph.randomGraphRandomForwardEdges(nodes, 0, numVars, 30, 15, 15, false, true);

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


