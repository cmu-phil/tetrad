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
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SemBicScore;
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
import static java.lang.Math.sqrt;
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

    @Test
    public void test1() {

        try {

            final double bootstrapSampleSize = 100;
            final int numBootstraps = 100;
            final double alpha = .05;
            final int sensitivity = 2;
            final double max = .4;
            final int N = 1000;
            final double variance = 1.5;

            File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations/joe.test");
            dir.mkdirs();

            final String linearFunction = "TSUM(NEW(B)*$)";

            final String nonlinearFunction = max + "* sin("  + (1.0 / max) + "* ((TSUM(NEW(B)*$^4) + ERROR)))";
//            final String nonlinearFunction = factor + "tanh("  + invfactor + "TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3))";
//            final String nonlinearFunction = factor + " tanh(" + invfactor +  " TSUM(NEW(B)*$) + TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3))";
//            final String nonlinearFunction = factor + "tanh(" + invfactor + "(TSUM(NEW(B) * $)))";
//            final String nonlinearFunction = factor + "sin(" + invfactor + "(TSUM(NEW(B) * $)))";

            final String gaussianError = "Normal(0, " + variance + ")";

            double uniformBound = sqrt(variance * 12) / 2.0;

//            final String nonGaussianError = "U(-" + uniformBound + ", " + uniformBound + ")";
//            final String nonGaussianError = "Beta(2, 5)";
//            final String nonGaussianError = "Laplace(0, sqrt(1 / 2))";
//            final String nonGaussianError = "Split(-.5,-0,.0,.5)";
            final String nonGaussianError = "U(-5, 0)";

            final String parameters = "Split(-1,-.2,.2,1)";

            PrintStream out = new PrintStream(new File(dir, "description.txt"));

            out.println("Linear function " + linearFunction);
            out.println("Nonlinear function " + nonlinearFunction);
            out.println("Gaussian error " + gaussianError);
            out.println("Non-Gaussian error " + nonGaussianError);
            out.println("Parameters " + parameters);
            out.println("Sample size " + N);

            out.println();

            out.close();

            System.out.println("Percent in bootstrap = " + bootstrapSampleSize);
            System.out.println("Num bootstraps = " + numBootstraps);
            System.out.println("Alpha = " + alpha);
            System.out.println("Sensitivity = " + sensitivity);
            System.out.println("Max = " + max);
            System.out.println("Variance = " + variance);

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

            doTest(bootstrapSampleSize, numBootstraps, alpha, sensitivity, graph, D1, D2, D3, D4, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // To the same but loading datasets and graphs in from files.
    @Test
    public void test2() {
        final int numBootstraps = 100;
        final double bootstrapSampleSize = 100;
        final double alpha = .05;
        final double sensitivity = 0.25;
        boolean singleEdge = true;

        try {
            for (int i = 1; i <= 20; i++) {

                File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations/example" + i);

                Graph graph = GraphUtils.loadGraphTxt(new File(dir, "graph.txt"));

                DataSet D1 = readInContinuousData(dir, "D1.txt");
                DataSet D2 = readInContinuousData(dir, "D2.txt");
                DataSet D3 = readInContinuousData(dir, "D3.txt");
                DataSet D4 = readInContinuousData(dir, "D4.txt");

                System.out.print((i) + ".\t");

                doTest(bootstrapSampleSize, numBootstraps, alpha, sensitivity, graph, D1, D2, D3, D4, singleEdge);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static DataSet readInContinuousData(File dir, String s) throws IOException {
        Dataset dataset1 = new ContinuousTabularDataFileReader(new File(dir, s), Delimiter.TAB).readInData();
        return (DataSet) DataConvertUtils.toDataModel(dataset1);
    }

    @Test
    public void test3() {

        final String f = ".15";
        String factor = f + " * ";
        String invfactor = "";//+ "(1/" + f + ") * ";


        final String linearFunction = "TSUM(NEW(B)*$)";

        //            final String nonlinearFunction = factor + " tanh(" + invfactor + " TSUM(NEW(B)*$^2))";
//            final String nonlinearFunction = factor + " tanh(" + invfactor + "(TSUM(NEW(B)*$^3)))";
//            final String nonlinearFunction = factor + "tanh("  + invfactor + "TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3))";
//            final String nonlinearFunction = factor + " tanh(" + invfactor +  " TSUM(NEW(B)*$) + TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3))";
//            final String nonlinearFunction = factor + "tanh(" + invfactor + "(TSUM(NEW(B) * $)))";
//            final String nonlinearFunction = factor + "sin(" + invfactor + "(TSUM(NEW(B) * $)))";

//        final String nonlinearFunction = factor + " tanh(" + invfactor + " TSUM(NEW(B)*$^2))";
//        final String nonlinearFunction = factor + " tanh(" + invfactor + "(TSUM(NEW(B)*$^3)))";
//            final String nonlinearFunction = factor + "tanh("  + invfactor + "TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3))";
//        final String nonlinearFunction = factor + " tanh(" + invfactor + " TSUM(NEW(B)*$) + TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3))";
//        final String nonlinearFunction = factor + "tanh(" + invfactor + "(TSUM(NEW(B) * $)))";
        final String nonlinearFunction = factor + "sin(" + invfactor + "(TSUM(NEW(B) * $)))";

        final String gaussianError = "Normal(0, 1)";


//            final String nonGaussianError = "Uniform(-sqrt(12) / 2, sqrt(12) / 2)";
//            final String nonGaussianError = "Beta(2, 5)";
        final String nonGaussianError = "Laplace(0, sqrt(1 / 2))";

        String parameters = "Split(-.9,-.2,.2,.9)";


        final int N = 1000;

        File dir = null;

        try {
            dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations/joe.test");
            dir.mkdirs();


            PrintStream out = new PrintStream(new File(dir, "description.txt"));

            out.println("Linear function " + linearFunction);
            out.println("Nonlinear function " + nonlinearFunction);
            out.println("Gaussian error " + gaussianError);
            out.println("Non-Gaussian error " + nonGaussianError);
            out.println("Parameters " + parameters);
            out.println("Sample size " + N);

            out.println();

            out.close();


            System.out.println("Linear function " + linearFunction);
            System.out.println("Nonlinear function " + nonlinearFunction);
            System.out.println("Gaussian error " + gaussianError);
            System.out.println("Non-Gaussian error " + nonGaussianError);
            System.out.println("Parameters " + parameters);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        System.out.println();

        final double bootstrapSamplSize = 50;
        final int numBootstraps = 100;
        final double alpha = 0.999;
        System.out.println("Bootstrap sample size = " + bootstrapSamplSize);
        System.out.println("Num bootstraps = " + numBootstraps);
        System.out.println("Alpha = " + alpha);


        try {

            Graph graph = GraphUtils.randomGraph(10, 0, 10, 100, 100, 100, false);


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

            graphOut.close();


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

            // Save these dataset out so we can compare with the White test. The judgements should be that edges for
            // D1 and D2 are nonlinear and edges for D3 and D4 are nonlinear.
            // Make sure you save out the graph as well.

            DataWriter.writeRectangularData(D1, new FileWriter(new File(dir, "D1.txt")), '\t');
            DataWriter.writeRectangularData(D2, new FileWriter(new File(dir, "D2.txt")), '\t');
            DataWriter.writeRectangularData(D3, new FileWriter(new File(dir, "D3.txt")), '\t');
            DataWriter.writeRectangularData(D4, new FileWriter(new File(dir, "D4.txt")), '\t');

            doTest3(dir, bootstrapSamplSize, numBootstraps, alpha, graph, D1, D2, D3, D4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Try just looking at a triangle.
    @Test
    public void test4() {

        final String linearFunction = "TSUM(NEW(B)*$)";
//        final String nonlinearFunction = "TSUM(NEW(B)*$^3)";
//        final String nonlinearFunction = "TSUM(NEW(B)*$^2) + TSUM(NEW(B)*$^3)";
        final String nonlinearFunction = "tanh(TSUM(.1 * $))";
        final String gaussianError = "Normal(0, .3)";
        final String nonGaussianError = "0.2 * U(-1, 1)";
        final String parameters = "Split(-.9,-.2,.2,.9)";

        System.out.println("Linear function " + linearFunction);
        System.out.println("Nonlinear function " + nonlinearFunction);
        System.out.println("Gaussian error " + gaussianError);
        System.out.println("Non-Gaussian error " + nonGaussianError);
        System.out.println("Parameters " + parameters);

        File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations/joe.test");
        dir.mkdirs();


        System.out.println();

        final double bootstrapSampleSize = 100;
        final int numBootstraps = 200;
        final double alpha = 0.05;
        final double varCutoff = 0.05;

        System.out.println("Percent in bootstrap = " + bootstrapSampleSize);
        System.out.println("Num boottraps = " + numBootstraps);
        System.out.println("Alpha = " + alpha);


        try {

//            Graph graph = GraphUtils.randomGraph(10, 0, 10, 100, 100, 100, false);

            Node x1 = new ContinuousVariable("X1");
            Node x2 = new ContinuousVariable("X2");
            Node x3 = new ContinuousVariable("X3");

            Graph graph = new EdgeListGraph();
            graph.addNode(x1);
            graph.addNode(x2);
            graph.addNode(x3);

            graph.addDirectedEdge(x1, x2);
            graph.addDirectedEdge(x2, x3);
            graph.addDirectedEdge(x1, x3);

            GeneralizedSemPm pm1 = getPm(graph, linearFunction, gaussianError, parameters);
            GeneralizedSemPm pm2 = getPm(graph, linearFunction, nonGaussianError, parameters);
            GeneralizedSemPm pm3 = getPm(graph, nonlinearFunction, gaussianError, parameters);
            GeneralizedSemPm pm4 = getPm(graph, nonlinearFunction, nonGaussianError, parameters);

            GeneralizedSemIm im1 = new GeneralizedSemIm(pm1);
            GeneralizedSemIm im2 = new GeneralizedSemIm(pm2);
            GeneralizedSemIm im3 = new GeneralizedSemIm(pm3);
            GeneralizedSemIm im4 = new GeneralizedSemIm(pm4);

            DataSet D1 = im1.simulateData(1000, false);
            DataSet D2 = im2.simulateData(1000, false);
            DataSet D3 = im3.simulateData(1000, false);
            DataSet D4 = im4.simulateData(1000, false);

            // Save these dataset out so we can compare with the White test. The judgements should be that edges for
            // D1 and D2 are nonlinear and edges for D3 and D4 are nonlinear.
            // Make sure you save out the graph as well.

            DataWriter.writeRectangularData(D1, new FileWriter(new File(dir, "D1.txt")), '\t');
            DataWriter.writeRectangularData(D2, new FileWriter(new File(dir, "D2.txt")), '\t');
            DataWriter.writeRectangularData(D3, new FileWriter(new File(dir, "D3.txt")), '\t');
            DataWriter.writeRectangularData(D4, new FileWriter(new File(dir, "D4.txt")), '\t');

            GraphUtils.saveGraph(graph, new File(dir, "graphedges.txt"), false);

            doTest3(dir, bootstrapSampleSize, numBootstraps, alpha, graph, D1, D2, D3, D4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doTest(double bootstrapSampleSize, int numBootstraps, double alpha, double sensitivity, Graph graph,
                        DataSet D1, DataSet D2, DataSet D3, DataSet D4, boolean singleEdge) {
        DataSet[] datasets = {D1, D2, D3, D4};

        List<Node> variables = graph.getNodes();


        double[][] variances = new double[D1.getNumColumns()][4];

        for (int i = 0; i < datasets.length; i++) {
            DataSet dataset = datasets[i];
            double[][] data = dataset.getDoubleData().transpose().toArray();

            for (int j = 0; j < dataset.getNumColumns(); j++) {
                double var = StatUtils.variance(data[j]);
                variances[j][i] = var;
            }
        }

        List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
        sort(edges);

        int[][] result = new int[edges.size()][4];

        for (int d = 0; d < 4; d++) {
            DataSet d2 = datasets[d];
            double[][] data = d2.getDoubleData().transpose().toArray();

            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);

                Node z = edge.getNode2();
                Node x = edge.getNode1();
                int __z = variables.indexOf(z);
                double[] _z = data[__z];

                List<Node> parents = graph.getParents(z);

                int __x = variables.indexOf(x);
                double[] _x = data[__x];

                List<Node> otherParents;

                if (singleEdge) {
                    otherParents = new ArrayList<>();
                } else {
                    otherParents = new ArrayList<>(parents);
                    otherParents.remove(x);
                }

                double[][] _otherParents = new double[otherParents.size()][];

                for (int j = 0; j < otherParents.size(); j++) {
                    int __o = variables.indexOf(otherParents.get(j));
                    double[] _o = data[__o];
                    _otherParents[j] = _o;
                }

                final boolean linear = DataUtils.linear(_x, _z, _otherParents, bootstrapSampleSize,
                        numBootstraps, alpha, sensitivity);

                result[i][d] = linear ? 0 : 1;
            }
        }

        int[] sums = new int[4];

        for (int d = 0; d < 4; d++) {
            int sum = 0;

            for (int i = 0; i < result.length; i++) {
                sum += result[i][d];
            }

            sums[d] = sum;
        }

        System.out.println(MatrixUtils.toString(sums));
    }

    private void doTest3(File dir, double bootstrapSampleSize, int numBootstraps, double alpha, Graph graph,
                         DataSet D1, DataSet D2, DataSet D3, DataSet D4) throws FileNotFoundException {
        int N = D1.getNumRows();

        DataSet[] datasets = {D1, D2, D3, D4};

        System.out.println("\nGraph\n\n" + graph);

        DataSet d0 = datasets[0];
        graph = GraphUtils.replaceNodes(graph, d0.getVariables());
        final SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(d0));
        score.setPenaltyDiscount(3);
        IndependenceTest test = new IndTestScore(score);

        for (int d = 0; d < 4; d++) {
            List<Integer> result = new ArrayList<>();
            List<Node> xNodes = new ArrayList<>();
            List<Node> zNodes = new ArrayList<>();
            List<Edge> edgeEdges = new ArrayList<>();
            List<Edge> fakeEdges = new ArrayList<>();
            List<List<Node>> trueParents = new ArrayList<>();
            List<Double> forward = new ArrayList<>();
            List<Double> backward = new ArrayList<>();

            System.out.println("\nD" + (d + 1));

            DataSet _D = datasets[d];
            double[][] data = _D.getDoubleData().transpose().toArray();
            List<Node> variables = d0.getVariables();
            graph = GraphUtils.replaceNodes(graph, variables);

            for (Node x : variables) {
                for (Node z : variables) {
                    if (x == z) continue;

//                    if (!GraphUtils.isDConnectedTo(x, z, new ArrayList<>(), graph)) continue;
//
//                    if (test.isIndependent(x, z, new ArrayList<>())) continue;

                    int __z = variables.indexOf(z);
                    double[] _z = data[__z];

                    int __x = variables.indexOf(x);
                    double[] _x = data[__x];

                    double[][] _otherParents = new double[0][data[0].length];

                    final double linearPValue = DataUtils.linearPValue(_z, _x, _otherParents, bootstrapSampleSize,
                            numBootstraps, alpha, 4);
                    final double linearBackwardsPValue = DataUtils.linearPValue(_x, _z, _otherParents, bootstrapSampleSize,
                            numBootstraps, alpha, 4);

//                    if (linearPValue < 0.7 || linearBackwardsPValue < 0.7) continue;

//                    if (abs(linearPValue - linearBackwardsPValue) > 0.03) continue;

//                    if (linearPValue < linearBackwardsPValue)  continue;

                    forward.add(linearPValue);
                    backward.add(linearBackwardsPValue);


                    result.add(linearPValue > alpha ? 0 : 1);

                    xNodes.add(x);
                    zNodes.add(z);
                    edgeEdges.add(graph.getEdge(x, z));

                    Edge fakeEdge = Edges.directedEdge(x, z);
                    if (fakeEdge.equals(graph.getEdge(x, z))) fakeEdge = null;

                    fakeEdges.add(fakeEdge);
                    trueParents.add(graph.getParents(z));
                }
            }

            for (int i = 0; i < result.size(); i++) {
//                if (forward.get(i) > 0.99900) {
//                if (fakeEdges.get(i) == null) {
                System.out.println((i + 1) + ". " + result.get(i) + "   " +
                        "x = " + xNodes.get(i) + "   z = " + zNodes.get(i) + "   edge = " + edgeEdges.get(i)
                        + "   fake edge = " + fakeEdges.get(i) + "   trueParents = " + trueParents.get(i) + " forward = " + forward.get(i) + " backward = " + backward.get(i));
//                }
            }

            System.out.println(result.size() + " edges");
        }


//        System.out.println(MatrixUtils.toString(result));
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
}


