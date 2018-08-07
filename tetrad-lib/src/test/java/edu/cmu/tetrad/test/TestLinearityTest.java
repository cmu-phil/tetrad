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
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Test;

import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.abs;
import static java.util.Collections.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;


/**
 * Tests the BTN linearity test.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class TestLinearityTest {

    public void test1() {

        try {
            double alpha = 1e-6;

            final double bootstrapSampleSize = 100;
            final int numBootstraps = 100;
            final double sensitivity = .15;
            final int N = 500;

            File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations/joe.test");
            dir.mkdirs();

            final String linearFunction = "TSUM(NEW(B)*$)";
            final String nonlinearFunction = "TSUM(NEW(B)*$^1.05)";
            final String gaussianError = "Normal(0, .5)";
            final String nonGaussianError = "Laplace(0, .378)";
            final String parameters = "Split(-.5,-.2,.2,.5)";


            PrintStream out = new PrintStream(new File(dir, "description.txt"));

            out.println("Linear function " + linearFunction);
            out.println("Nonlinear function " + nonlinearFunction);
            out.println("Gaussian error " + gaussianError);
            out.println("Non-Gaussian error " + nonGaussianError);
            out.println("Parameters " + parameters);
            out.println("Sample size " + N);
            out.println("Percent in bootstrap = " + bootstrapSampleSize);
            out.println("Num bootstraps = " + numBootstraps);
            out.println("Sensitivity = " + sensitivity);

            out.println();
            out.close();

            Graph graph = GraphUtils.randomGraph(100, 0, 100, 100, 100, 100, true);

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

            D1 = DataUtils.center(D1);
            D2 = DataUtils.center(D2);
            D3 = DataUtils.center(D3);
            D4 = DataUtils.center(D4);

            // Save these dataset out so we can compare with the White test. The judgements should be that edges for
            // D1 and D2 are nonlinear and edges for D3 and D4 are nonlinear.
            // Make sure you save out the graph as well.

            DataWriter.writeRectangularData(D1, new FileWriter(new File(dir, "D1.txt")), '\t');
            DataWriter.writeRectangularData(D2, new FileWriter(new File(dir, "D2.txt")), '\t');
            DataWriter.writeRectangularData(D3, new FileWriter(new File(dir, "D3.txt")), '\t');
            DataWriter.writeRectangularData(D4, new FileWriter(new File(dir, "D4.txt")), '\t');

            GraphUtils.saveGraph(graph, new File(dir, "graph.txt"), false);

            List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
            sort(edges);
            List<Node> variables = graph.getNodes();

            PrintStream graphOut = new PrintStream(new FileOutputStream(new File(dir, "graph.indices.txt")));

            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);

                Node x = edge.getNode1();
                Node y = edge.getNode2();

                int j1 = variables.indexOf(x);
                int j2 = variables.indexOf(y);

                graphOut.println((j1 + 1) + "\t" + (j2 + 1));
            }

            out.close();

            doTest(alpha, graph, D1, D2, D3, D4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // To the same but loading datasets and graphs in from files.
    public void test2() {
        final double alpha = 1e-6;

        try {
            for (int i = 1; i <= 10; i++) {

                File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations7/example" + i);

                Graph graph = GraphUtils.loadGraphTxt(new File(dir, "graph.txt"));

                DataSet D1 = readInContinuousData(dir, "D1.txt");
                DataSet D2 = readInContinuousData(dir, "D2.txt");
                DataSet D3 = readInContinuousData(dir, "D3.txt");
                DataSet D4 = readInContinuousData(dir, "D4.txt");

                System.out.print((i) + ".\t");

                doTest(alpha, graph, D1, D2, D3, D4);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void test3() {

        double alpha = 1e-6;

        final double bootstrapSampleSize = 100;
        final int numBootstraps = 100;
        final int N = 1000;


        String[] linearFunctions = new String[]{
                "TSUM(NEW(B)*$)"
        };

        String[] nonlinearFunctions = new String[]{
                "abs(TSUM(NEW(B) * $))",
                "TSUM(NEW(B) * (abs($) ^ .8))",
                "TSUM(NEW(B) * (abs($) ^ 1.05))",
                "TSUM(NEW(B) * (abs($) ^ 1.5))",
                "(TSUM(NEW(B)*$) + TSUM(NEW(B) * ($^2)))",
                "TSUM(NEW(B) * ($ ^ 2))",
                "TSUM(NEW(B) * ($ ^ 3))",
                "TSUM(NEW(B) * ln(cosh($)))",
                " tanh(NEW(B) * (TSUM($)))",
                " (TSUM(sin(NEW(B) * $)) + TSUM(cos(NEW(B) * $)))"
        };

        String[] gaussianErrors = new String[]{
                "Normal(0, 0.3)"
        };

        String[] nonGaussianErrors = new String[]{
//                "0.5 * Uniform(-1, 1)",
//                "(U(0, 1)^3 - .5)",
                "0.1 * Laplace(0, 1)"
        };

        String parameters = "Split(-.5, -.2, .2, .5)";

        int index = 1;

        try {
            for (String linearFunction : linearFunctions) {
                for (String nonlinearFunction : nonlinearFunctions) {
                    for (String gaussianError : gaussianErrors) {
                        for (String nonGaussianError : nonGaussianErrors) {

                            nonlinearFunction = "1 * " + nonlinearFunction;

                            File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations7/example" + index++);
                            dir.mkdirs();

                            PrintStream out = new PrintStream(new File(dir, "description.txt"));

                            out.println("Linear function " + linearFunction);
                            out.println("Nonlinear function " + nonlinearFunction);
                            out.println("Gaussian error " + gaussianError);
                            out.println("Non-Gaussian error " + nonGaussianError);
                            out.println("Parameters " + parameters);
                            out.println("Sample size " + N);
                            out.println("Percent in bootstrap = " + bootstrapSampleSize);
                            out.println("Num bootstraps = " + numBootstraps);

                            out.println();
                            out.close();

                            Graph graph = GraphUtils.randomGraph(100, 0, 100, 100, 100, 100, true);

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

                            D1 = DataUtils.center(D1);
                            D2 = DataUtils.center(D2);
                            D3 = DataUtils.center(D3);
                            D4 = DataUtils.center(D4);

                            // Save these dataset out so we can compare with the White test. The judgements should be that edges for
                            // D1 and D2 are nonlinear and edges for D3 and D4 are nonlinear.
                            // Make sure you save out the graph as well.

                            DataWriter.writeRectangularData(D1, new FileWriter(new File(dir, "D1.txt")), '\t');
                            DataWriter.writeRectangularData(D2, new FileWriter(new File(dir, "D2.txt")), '\t');
                            DataWriter.writeRectangularData(D3, new FileWriter(new File(dir, "D3.txt")), '\t');
                            DataWriter.writeRectangularData(D4, new FileWriter(new File(dir, "D4.txt")), '\t');

                            GraphUtils.saveGraph(graph, new File(dir, "graph.txt"), false);

                            List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
                            sort(edges);
                            List<Node> variables = graph.getNodes();

                            PrintStream graphOut = new PrintStream(new FileOutputStream(new File(dir, "graph.indices.txt")));

                            for (int i = 0; i < edges.size(); i++) {
                                Edge edge = edges.get(i);

                                Node x = edge.getNode1();
                                Node y = edge.getNode2();

                                int j1 = variables.indexOf(x);
                                int j2 = variables.indexOf(y);

                                graphOut.println((j1 + 1) + "\t" + (j2 + 1));
                            }

                            out.close();

                            doTest(alpha, graph, D1, D2, D3, D4);
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void test5() {
        String function = "TSUM($^3)";
        String error = "U(-1, 1)";
        final String parameters = "";
        int N = 1000;

        Node X = new GraphNode("X");
        Node Y = new GraphNode("Y");
        final ContinuousVariable R = new ContinuousVariable("R");

        Graph graph = new EdgeListGraph();
        graph.addNode(X);
        graph.addNode(Y);
        graph.addDirectedEdge(X, Y);

        GeneralizedSemPm pm = getPm(graph, function, error, parameters);
        GeneralizedSemIm im = new GeneralizedSemIm(pm);
        DataSet dataSet = im.simulateData(N, false);

        RegressionDataset regression = new RegressionDataset(dataSet);

        RegressionResult result = regression.regress(dataSet.getVariable("Y"),
                Collections.singletonList(dataSet.getVariable("X")));
        double[] r = result.getResiduals().toArray();

        int indexx = dataSet.getColumn(dataSet.getVariable("X"));
        double[] x = dataSet.getDoubleData().transpose().toArray()[indexx];

        System.out.println("var XR = " + StatUtils.covariance(x, r));
    }

    public static DataSet readInContinuousData(File dir, String s) throws IOException {
        Dataset dataset1 = new ContinuousTabularDataFileReader(new File(dir, s), Delimiter.TAB).readInData();
        return (DataSet) DataConvertUtils.toDataModel(dataset1);
    }


    private void doTest(double alpha, Graph graph,
                        DataSet D1, DataSet D2, DataSet D3, DataSet D4) {
        DataSet[] datasets = {D1, D2, D3, D4};

        List<Node> variables = graph.getNodes();


        double[][] variances = new double[D4.getNumColumns()][4];

        for (int i = 0; i < datasets.length; i++) {
            DataSet dataset = datasets[i];
            if (dataset == null) continue;
            double[][] data = dataset.getDoubleData().transpose().toArray();

            for (int j = 0; j < dataset.getNumColumns(); j++) {
                double var = StatUtils.variance(data[j]);
                variances[j][i] = var;
            }
        }

        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        sort(edges);

        int[][] result = new int[edges.size()][4];
        int[][] linear = new int[edges.size()][4];
        int[][] dep = new int[edges.size()][4];

        for (int d = 0; d < 4; d++) {
            DataSet d2 = datasets[d];
            if (d2 == null) continue;
            double[][] data = d2.getDoubleData().transpose().toArray();

            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);

                Node y = edge.getNode2();
                Node x = edge.getNode1();
                int __y = variables.indexOf(y);
                double[] _y = data[__y];

                int __x = variables.indexOf(x);
                double[] _x = data[__x];

                final boolean _linear = DataUtils.linear4(_x, _y, alpha);
                final boolean xydep = DataUtils.xydep(_x, _y);

                result[i][d] = !_linear && xydep ? 1 : 0;
                linear[i][d] = _linear ? 1 : 0;
                dep[i][d] = xydep ? 1 : 0;
            }
        }

        int[] sumsResult = new int[4];
        int[] sumsLinear = new int[4];
        int[] sumsDep = new int[4];

        for (int d = 0; d < 4; d++) {
            int sum = 0;

            for (int i = 0; i < result.length; i++) {
                sum += result[i][d];
            }

            sumsResult[d] = sum;

            int sum2 = 0;

            for (int i = 0; i < result.length; i++) {
                sum2 += linear[i][d];
            }

            sumsLinear[d] = sum2;

            int sum3 = 0;

            for (int i = 0; i < result.length; i++) {
                sum3 += dep[i][d];
            }

            sumsDep[d] = sum3;
        }

        System.out.println("# nonlinear = " + MatrixUtils.toString(sumsResult) + " || # linear = " + MatrixUtils.toString(sumsLinear)
                + " || # dep = " + MatrixUtils.toString(sumsDep));
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

    public static void main(String...args) {
        new TestLinearityTest().test3();;
    }
}


