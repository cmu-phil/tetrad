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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DataWriter;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.TemplateExpander;
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.dbmi.data.Dataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Test;

import java.io.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.checkedCollection;
import static java.util.Collections.sort;


/**
 * Tests the SLiCK linearity test.
 *
 * Symmetric about the Linear Coefficient (Kolmogorov-Smirnov/Kuiper) test for linearity
 *
 * @author Bryan Andrews bja43@pitt.edu
 */
public final class TestSLiCK {

    public void test1() {

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
                "0.5 * Uniform(-1, 1)",
//                "(U(0, 1)^3 - .5)",
//                "0.1 * Laplace(0, 1)"
        };

        String parameters = "Split(-.5, -.2, .2, .5)";

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

                            doTest(quantile, alpha, graph, D1, D2, D3, D4, true);
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // To the same but loading datasets and graphs in from files.
    public void test2() {
        final double quantile = 0.33;
        final double alpha = 0.01;
        boolean singleEdge = true;

        try {
            for (int i = 1; i <= 21; i++) {

                File dir = new File("/Users/user/Box Sync/data/nonlinearity/simulations3/example" + i);

                Graph graph = GraphUtils.loadGraphTxt(new File(dir, "graph.txt"));

                DataSet D1 = readInContinuousData(dir, "D1.txt");
                DataSet D2 = readInContinuousData(dir, "D2.txt");
                DataSet D3 = readInContinuousData(dir, "D3.txt");
                DataSet D4 = readInContinuousData(dir, "D4.txt");

                System.out.print((i) + ".\t");

                doTest(quantile, alpha, graph, D1, D2, D3, D4, singleEdge);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static DataSet readInContinuousData(File dir, String s) throws IOException {
        Dataset dataset1 = new ContinuousTabularDataFileReader(new File(dir, s), Delimiter.TAB).readInData();
        return (DataSet) DataConvertUtils.toDataModel(dataset1);
    }


    private boolean KStest(ArrayList<Double> x0, ArrayList<Double> x1, double alpha) {
        Collections.sort(x0);
        Collections.sort(x1);

        int n0 = x0.size();
        int n1 = x1.size();

        double step0 = 1.0/n0;
        double step1 = 1.0/n1;

        double cdf0 = 0;
        double cdf1 = 0;

        int i0 = -1;
        int i1 = -1;

        double x;
        double KSstat = 0;
        // For Kuiper's statistic
        double k0 = 0;
        double k1 = 0;

        while(1-cdf0 >= step0/2 && 1-cdf1 >= step1/2) {
            if(x0.get(i0+1) <= x1.get(i1+1)) {
                i0 ++;
                x = x0.get(i0);
            } else {
                i1++;
                x = x1.get(i1);
            }
            if(i0 != -1 && x0.get(i0) == x) {
                cdf0 += step0;
            } else {
                cdf1 += step1;
            }
            KSstat = Math.max(KSstat, Math.abs(cdf0-cdf1));
            k0 = Math.max(k0, cdf0-cdf1);
            k1 = Math.max(k1, cdf1-cdf0);
        }

        double c = Math.sqrt(-0.5 * Math.log(alpha/2));
//        return KSstat > c * Math.sqrt((double)(n0+n1)/(n0*n1));
        return (k0 + k1) > c * Math.sqrt((double)(n0+n1)/(n0*n1));
    }


    private boolean SLiCK(double[] _x, double[] _y, double quantile, double alpha) {
        TetradMatrix X = new TetradMatrix(_x.length, 2);
        TetradMatrix y = new TetradMatrix(_y.length, 1);

        for(int i = 0; i < _x.length; i++) {
            X.set(i, 0, 1);
            X.set(i, 1, _x[i]);
            y.set(i, 0, _y[i]);
        }

        TetradMatrix r = y.minus(X.times(((X.transpose().times(X)).inverse()).times(X.transpose().times(y))));

        ArrayList<Double> x0 = new ArrayList<>();
        ArrayList<Double> x1 = new ArrayList<>();

        ArrayList<Double> sorted = new ArrayList();
        for(int i = 0; i < r.rows(); i++) {
            sorted.add(r.get(i,0));
        }
        Collections.sort(sorted);

        int t = (int) (Math.min(0.5, quantile) * r.rows());
        double t0 = sorted.get(r.rows()-t);
        double t1 = sorted.get(t);

        for(int i = 0; i < r.rows(); i++) {
            if(r.get(i,0) >= t0){
                x0.add(X.get(i, 1));
            } else if(r.get(i,0) <= t1){
                x1.add(X.get(i, 1));
            }
        }

        return KStest(x0, x1, alpha);
    }


    private void doTest(double quantile, double alpha, Graph graph, DataSet D1, DataSet D2, DataSet D3, DataSet D4, boolean singleEdge) {
        DataSet[] datasets = {D1, D2, D3, D4};

        List<Node> variables = graph.getNodes();

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

                final boolean linear = SLiCK(_x, _z, quantile, alpha);

                result[i][d] = linear ? 1 : 0;
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
        new TestSLiCK().test1();
    }
}


