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
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.StatUtils;
import edu.pitt.dbmi.data.ContinuousTabularDataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.DataReader;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

import static java.lang.Math.abs;

/**
 * My script.
 *
 * @author jdramsey
 */
public class TestVxx {

    @Test
    public void TestCycles_Data_fMRI_FASK() {

        int num = 100;
        double[] e1 = new double[num];
        double[] e2 = new double[num];
        double[] e3 = new double[num];
        double[] e4 = new double[num];
        double[] e5 = new double[num];

        for (int i = 0; i < num; i++) {
            Node x = new GraphNode("X");
            Node y = new GraphNode("Y");
            Node z = new GraphNode("Z");

            EdgeListGraph graph = new EdgeListGraph();
            graph.addNode(x);
            graph.addNode(y);
            graph.addNode(z);

            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, x);
            graph.addDirectedEdge(z, y);

            GeneralizedSemPm pm = new GeneralizedSemPm(graph);

            List<Node> errorNodes = pm.getErrorNodes();

            try {
                for (Node node : errorNodes) {
                    pm.setNodeExpression(node, "Beta(1, 5)");
                }

                pm.setParameterExpression("B", "Split(-.9,-.1,.1, .9)");
            } catch (ParseException e) {
                System.out.println(e);
            }

            GeneralizedSemIm im = new GeneralizedSemIm(pm);

            DataSet dataSet = im.simulateData(1000, false);

            Node dX = dataSet.getVariable("X");
            Node dY = dataSet.getVariable("Y");
            Node dZ = dataSet.getVariable("Z");
            List<Node> dVars = dataSet.getVariables();

            int iX = dVars.indexOf(dX);
            int iY = dVars.indexOf(dY);
            int iZ = dVars.indexOf(dZ);

            double[][] dd = dataSet.getDoubleData().transpose().toArray();

            double vzy = cu(dd[iZ], dd[iZ], dd[iY]);
            double vxy = cu(dd[iX], dd[iX], dd[iY]);
            double vzx = cu(dd[iZ], dd[iZ], dd[iX]);
            double vxx = cu(dd[iX], dd[iX], dd[iX]);
            double vxzy = cu(dd[iX], dd[iZ], dd[iY]);
            double vxzx = cu(dd[iX], dd[iZ], dd[iX]);

            System.out.println("\nvzx = " + vxx + " vxx = " + vxx + " vzy = " + vzy + " vxy = " + vxy);

            System.out.println(" vzy / vxy = " + (vzy / vxy) + " vzx / vxx = " + (vzx / vxx));

            e1[i] = vzy / vxy;
            e2[i] = vzx / vxx;
            e4[i] = vzx;
            e5[i] = vzy;
            e3[i] = e4[i] - e5[i];
        }

        System.out.println();

        System.out.println("mean vzx = " + StatUtils.mean(e4));
        System.out.println("variance vzx = " + StatUtils.variance(e4));
        System.out.println("mean vzy = " + StatUtils.mean(e5));
        System.out.println("variance vzy = " + StatUtils.variance(e5));

//        System.out.println("mean vzy / vxy = " + StatUtils.mean(e1));
//        System.out.println("variance vzy / vxy = " + StatUtils.variance(e1));
//        System.out.println("mean vzx / vxx = " + StatUtils.mean(e2));
//        System.out.println("variance vzx / vxx = " + StatUtils.variance(e2));
//
        System.out.println("mean diff = " + StatUtils.mean(e3));
        System.out.println("variance diff = " + StatUtils.variance(e3));
    }

    public static double cu(double[] x, double[] y, double[] condition) {
        double exy = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }

    @Test
    public void testRubenLoop() {
        try {

            double sumAdjPrec = 0.0;
            double sumAdjRec = 0.0;
            double sumAhdPrec = 0.0;
            double sumAhdRec = 0.0;

            List<DataSet> dataSets = new ArrayList<>();

            for (int i = 1; i <= 60; i++) {


                String dir = "/Users/user/Downloads/allpositive 2/";
                File file = new File(dir, "network_E_coeff" + i + "_allpos.txt");

                NumberFormat nf = new DecimalFormat("00");

//                String dir = "/Users/user/Downloads/allpositive_concat/";
//                File file = new File(dir, "concat_" + nf.format(i) + ".txt");

                DataReader reader = new ContinuousTabularDataFileReader(file, Delimiter.TAB);

                ContinuousTabularDataset data = (ContinuousTabularDataset) reader.readInData();

                List<Node> variables = new ArrayList<>();

                for (String var : data.getVariables()) {
                    variables.add(new ContinuousVariable(var));
                }

                dataSets.add(new BoxDataSet(new DoubleDataBox(data.getData()), variables));
            }

            int count = 60;

            for (int i = 1; i <= count; i++) {
                Collections.shuffle(dataSets);

                List<DataSet> toConcatenate = new ArrayList<>();

                for (int j = 0; j < 10; j++) {
                    toConcatenate.add(DataUtils.standardizeData(dataSets.get(j)));
                }

                DataSet dataSet = DataUtils.concatenate(toConcatenate);

//                File f = new File("/Users/user/Downloads/stddata.txt");
//
//                PrintStream out = new PrintStream(f);
//
//                out.println(dataSet);
//                out.close();

//                dataSet = DataUtils.standardizeData(dataSet);

                edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
                score.setPenaltyDiscount(1);

                Fask fask = new Fask(dataSet, score);
                fask.setAlpha(1e-10);
                fask.setPenaltyDiscount(2);
//                fask.setPresumePositiveCoefficients(true);

                Graph G = fask.search();

                System.out.println(G);

                Graph dag = new EdgeListGraph(dataSet.getVariables());
                Node X = dag.getNode("X");
                Node Y = dag.getNode("Y");
                Node Z = dag.getNode("Z");

                dag.addDirectedEdge(Z, X);
                dag.addDirectedEdge(X, Y);
                dag.addDirectedEdge(Y, Z);

                GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(G, dag);

                System.out.println(comparison.getAdjPrec() + " " +
                        comparison.getAdjRec() + " " +
                        comparison.getAhdPrec() + " " +
                        comparison.getAhdRec() + " ");

                sumAdjPrec += comparison.getAdjPrec();
                sumAdjRec += comparison.getAdjRec();
                sumAhdPrec += comparison.getAhdPrec();
                sumAhdRec += comparison.getAhdRec();
            }

            System.out.println("\nAverages:\n");

            System.out.println("AdjPred = " + (sumAdjPrec / count));
            System.out.println("AdjRec = " + (sumAdjRec / count));
            System.out.println("AhdPred = " + (sumAhdPrec / count));
            System.out.println("AhdRec = " + (sumAhdRec / count));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String... args) {
        new TestVxx().TestCycles_Data_fMRI_FASK();
    }
}




