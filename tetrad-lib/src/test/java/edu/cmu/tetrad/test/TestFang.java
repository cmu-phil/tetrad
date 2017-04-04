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

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fang;
import edu.cmu.tetrad.algcomparison.algorithm.multi.FasLofs;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDataAndSingleGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestFang {

    public void TestRuben() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 3);
        parameters.set("depth", -1);

        parameters.set("numRuns", 10);
        parameters.set("randomSelectionSize", 10);
        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive2());
        statistics.add(new TwoCycleFalseNegative2());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure1_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure1_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure3_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure3_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure3_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure4_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure4_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure4_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure5_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure5_contr"));

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_amp_c4"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr_c4"));

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr_p2n6"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr_p6n2"));


        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/ComplexMatrix_1"));

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Diamond"));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Fang());
//        algorithms.add(new CcdMax(new SemBicTest()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(false);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    @Test
    public void TestSmith() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 1);
        parameters.set("depth", -1);
        parameters.set("twoCycleAlpha", .001);

        parameters.set("numRuns", 10);
        parameters.set("randomSelectionSize", 1);
        parameters.set("Structure", "Placeholder");


        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive2());
        statistics.add(new TwoCycleFalseNegative2());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        String path = "/Users/jdramsey/Downloads/smithsim.algcomp/";

        simulations.add(new LoadContinuousDataSmithSim(path + "1"));
        simulations.add(new LoadContinuousDataSmithSim(path + "2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "3"));
        simulations.add(new LoadContinuousDataSmithSim(path + "4"));
        simulations.add(new LoadContinuousDataSmithSim(path + "5"));
        simulations.add(new LoadContinuousDataSmithSim(path + "6"));
        simulations.add(new LoadContinuousDataSmithSim(path + "7"));
        simulations.add(new LoadContinuousDataSmithSim(path + "8"));
        simulations.add(new LoadContinuousDataSmithSim(path + "9"));
        simulations.add(new LoadContinuousDataSmithSim(path + "10"));
        simulations.add(new LoadContinuousDataSmithSim(path + "11"));
        simulations.add(new LoadContinuousDataSmithSim(path + "12"));
        simulations.add(new LoadContinuousDataSmithSim(path + "13"));
        simulations.add(new LoadContinuousDataSmithSim(path + "14"));
        simulations.add(new LoadContinuousDataSmithSim(path + "15"));
        simulations.add(new LoadContinuousDataSmithSim(path + "16"));
        simulations.add(new LoadContinuousDataSmithSim(path + "17"));
        simulations.add(new LoadContinuousDataSmithSim(path + "18"));
        simulations.add(new LoadContinuousDataSmithSim(path + "19"));
        simulations.add(new LoadContinuousDataSmithSim(path + "20"));
        simulations.add(new LoadContinuousDataSmithSim(path + "21"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22_2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "23"));
        simulations.add(new LoadContinuousDataSmithSim(path + "24"));
        simulations.add(new LoadContinuousDataSmithSim(path + "25"));
        simulations.add(new LoadContinuousDataSmithSim(path + "26"));
        simulations.add(new LoadContinuousDataSmithSim(path + "27"));
        simulations.add(new LoadContinuousDataSmithSim(path + "28"));

        Algorithms algorithms = new Algorithms();
//        algorithms.add(new ImagesSemBic());
        algorithms.add(new Fang());

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparisonsmith", simulations, algorithms, statistics, parameters);
//        comparison.compareFromFiles("comparison", algorithms, statistics, parameters);
//        comparison.saveToFiles("comparison", new LinearFisherModel(new RandomForward()), parameters);

    }

    @Test
    public void TestPwwd7() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 1);
        parameters.set("depth", -1);
        parameters.set("twoCycleAlpha", 1e-9);

        parameters.set("numRuns", 10);
        parameters.set("randomSelectionSize", 1);
        parameters.set("Structure", "Placeholder");

        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive2());
        statistics.add(new TwoCycleFalseNegative2());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        String path = "/Users/jdramsey/Downloads/pwdd7.algcomp/";

        simulations.add(new LoadContinuousDataSmithSim(path + "1"));
        simulations.add(new LoadContinuousDataSmithSim(path + "2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "3"));
        simulations.add(new LoadContinuousDataSmithSim(path + "4"));
        simulations.add(new LoadContinuousDataSmithSim(path + "5"));
        simulations.add(new LoadContinuousDataSmithSim(path + "6"));
        simulations.add(new LoadContinuousDataSmithSim(path + "7"));
        simulations.add(new LoadContinuousDataSmithSim(path + "8"));
        simulations.add(new LoadContinuousDataSmithSim(path + "9"));
        simulations.add(new LoadContinuousDataSmithSim(path + "10"));
        simulations.add(new LoadContinuousDataSmithSim(path + "11"));
        simulations.add(new LoadContinuousDataSmithSim(path + "12"));
//        simulations.add(new LoadContinuousDataSmithSim(path + "13"));
        simulations.add(new LoadContinuousDataSmithSim(path + "14"));
        simulations.add(new LoadContinuousDataSmithSim(path + "15"));
        simulations.add(new LoadContinuousDataSmithSim(path + "16"));
        simulations.add(new LoadContinuousDataSmithSim(path + "17"));
        simulations.add(new LoadContinuousDataSmithSim(path + "18"));
        simulations.add(new LoadContinuousDataSmithSim(path + "19"));
        simulations.add(new LoadContinuousDataSmithSim(path + "20"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22_2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "23"));
        simulations.add(new LoadContinuousDataSmithSim(path + "24"));
        simulations.add(new LoadContinuousDataSmithSim(path + "25"));
        simulations.add(new LoadContinuousDataSmithSim(path + "26"));
        simulations.add(new LoadContinuousDataSmithSim(path + "27"));
        simulations.add(new LoadContinuousDataSmithSim(path + "28"));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Fang());
        algorithms.add(new FasLofs(Lofs2.Rule.RSkew));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparisonpwwd", simulations, algorithms, statistics, parameters);
    }

    @Test
    public void testSkeptical() {
        int misoriented = 0;
        int total = 200;
        double minRatio3 = Double.POSITIVE_INFINITY;

        List<Double> ratios = new ArrayList<>();
        double sumdiff1 = 0.0;
        double sumdiff2 = 0.0;
        double sumvzx = 0.0;
        double sumvzy = 0.0;

        final BetaDistribution betaDistribution = new BetaDistribution(2, 10);


        for (int index = 0; index < total; index++) {
            SemGraph G0 = new SemGraph();

            Node X = new ContinuousVariable("X");
            Node Y = new ContinuousVariable("Y");
            Node Z = new ContinuousVariable("Z");

            G0.addNode(X);
            G0.addNode(Y);
            G0.addNode(Z);

            G0.addDirectedEdge(X, Y);
            G0.addDirectedEdge(Z, X);
            G0.addDirectedEdge(Z, Y);

            G0.setShowErrorTerms(true);

            Node e1 = G0.getExogenous(X);
            Node e2 = G0.getExogenous(Y);
            Node e3 = G0.getExogenous(Z);


            GeneralizedSemPm pm = new GeneralizedSemPm(G0);


            try {
//                pm.setNodeExpression(X, "E_X");
                pm.setNodeExpression(X, "c * Z + E_X");
//                pm.setNodeExpression(Y, "a * X + E_Y");
                pm.setNodeExpression(Y, "a * X + b * Z + E_Y");
                pm.setNodeExpression(Z, "E_Z");

                final String errors = "Beta(2, 10)";
                pm.setNodeExpression(e1, errors);
                pm.setNodeExpression(e2, errors);
                pm.setNodeExpression(e3, errors);

//                final String coef = "Split(-1, -.1, .1, 1)";
                final String coef = "Split(-.8, -.1, .1, .8)";
                pm.setParameterExpression("a", coef);
                pm.setParameterExpression("b", coef);
                pm.setParameterExpression("c", coef);

                GeneralizedSemIm im = new GeneralizedSemIm(pm);

                double _a = im.getParameterValue("a");
                double _b = im.getParameterValue("b");
                double _c = im.getParameterValue("c");

                DataSet dataSet = im.simulateDataRecursive(1000, false);
                dataSet = DataUtils.standardizeData(dataSet);

                List<Node> nodes = dataSet.getVariables();
                double[][] colData = dataSet.getDoubleData().transpose().toArray();
                int i = nodes.indexOf(dataSet.getVariable("X"));
                int j = nodes.indexOf(dataSet.getVariable("Y"));
                int k = nodes.indexOf(dataSet.getVariable("Z"));
                double[] x = colData[i];
                double[] y = colData[j];
                double[] z = colData[k];

                double[] c = cor(x, y, 0, 0);
                double[] c1 = cor(x, y, 1, 0);
                double[] c2 = cor(x, y, 0, 1);

                double vxx = var(x, x, 1)[0];
                double vyx = var(y, x, 1)[0];
                double vzx = var(z, x, 1)[0];

                double vxy = var(x, y, 1)[0];
                double vyy = var(y, y, 1)[0];
                double vzy = var(z, y, 1)[0];

                double ratiozy_x = vzx / vyx;
                double ratiozy_y = vzy / vyy;

                double ratiozx_x = vzx / vxx;
                double ratiozx_y = vzy / vxy;


//                double ratio3 = (ratiozx_y - 1.0) / (ratiozx_x - 1.0);
//
//                if (ratio3 < minRatio3) {
//                    minRatio3 = ratio3;
//                }
//
//                ratios.add(ratio3);

                double z0 = getZ(c[0]);
                double z1 = getZ(c1[0]);
                double z2 = getZ(c2[0]);

                double diff1 = z0 - z1; // 0.5 * total amount of confounding.
                double diff2 = z0 - z2; // ?? * total amount of confounding + cov(X, eY) / var(X)

                final double t1 = diff1 / (sqrt(1.0 / c[1] + 1.0 / c1[1]));
                final double t2 = diff2 / (sqrt(1.0 / c[1] + 1.0 / c2[1]));

                double p1 = 1.0 - new TDistribution(2 * (c[1] + c1[1]) - 2).cumulativeProbability(abs(t1 / 2.0));
                double p2 = 1.0 - new TDistribution(2 * (c[1] + c2[1]) - 2).cumulativeProbability(abs(t2 / 2.0));

                NumberFormat nf = new DecimalFormat("0.000000");

//            System.out.println();
                System.out.print(((index + 1) + ". "));
//                System.out.print("cor(X, Y) = " + nf.format(c[0]));
//                System.out.print(" cor(X, Y | X > 0) = " + nf.format(c1[0]));
//                System.out.println(" cor(X, Y | Y > 0) = " + nf.format(c2[0]));
//                System.out.println("  p value for cor(X, Y) - cor(X, Y | X > 0) = " + nf.format(p1));
//                System.out.println("  p value for cor(X, Y) - cor(X, Y | Y > 0) = " + nf.format(p2));
//                System.out.println("  diff1 = " + nf.format(diff1));
//                System.out.println("  diff2 = " + nf.format(diff2));
//                System.out.println("  bc = " + nf.format(_b * _c));

                System.out.println();
                System.out.println("  vxx = " + nf.format(vxx));
                System.out.println("  vxy = " + nf.format(vxy));

                System.out.println("  vzx = " + nf.format(vzx));
//                System.out.println("  vxx = " + nf.format(vxx));
                System.out.println("  vzy = " + nf.format(vzy));
//                System.out.println("  vzy - vzx = " + nf.format(vzy - vzx));
//                System.out.println("  X->Y " + (p1 < p2));
//                System.out.println("  (vzx / vxx) = " + nf.format(vzx / vxx));
//                System.out.println("  (vzx / vxx) = " + nf.format(vzx / vyx));
//                System.out.println("  bc * (vzx / vxx) = " + nf.format(_b * _c * (vzx / vxx)));
//                System.out.println("  bc * (vzx / vyx) = " + nf.format(_b * _c * (vzx / vyx)));
//                System.out.println("  c^2 = " + nf.format(_c * _c));
//                System.out.println("  (vxx / vzx) - c^2 = " + nf.format((vxx / vzx) - _c * _c));

//                double varex = vxx - _c * _c - _c * (-_c);
//                double varey = (_a * _a) * vxy + (_b * _b) - 2 * _a * _b * (_c - _a * _b);

                double varerror = .5;

                double varx1 = (_c * _c) + varerror;
                double varx2 = (1.0 / (_a * _a)) * (varerror - (_b * _b) + 2 * _a * _b * (_c - _a * _b));

                System.out.println("...varx1 = " + varx1);
                System.out.println("...varx2 = " + varx2);

                final double d1 = c[0] - c1[0];
                final double d2 = c[0] - c2[0];

                sumdiff1 += diff1;
                sumdiff2 += diff2;
                sumvzx += vzx;
                sumvzy += vzy;

                final double f = 1;

                boolean orientxy = abs(d2) > f * abs(d1);
                boolean orientyx = abs(d1) > f * abs(d2);
                if (!(orientxy && !orientyx)) misoriented++;

                System.out.println("Orient " + (orientxy && !orientyx));
            } catch (ParseException e) {
                e.printStackTrace();
            }

        }

//        Collections.sort(ratios);
//
//        for (int i = 0; i < ratios.size(); i++) {
//            System.out.println((i + 1) + ". " + ratios.get(i));
//        }
//
        System.out.println("% misoriented = " + (misoriented / (double) total));
//        System.out.println("min ratio3 = " + minRatio3);

        System.out.println("Avg diff1 = " + (sumdiff1 / total));
        System.out.println("Avg diff2 = " + (sumdiff2 / total));
        System.out.println("Avg vzx = " + (sumvzx / total));
        System.out.println("Avg vzy = " + (sumvzy / total));
    }

    private double[] cor(double[] x, double[] y, int xInc, int yInc) {

        double exy = 0.0;
        double exx = 0.0;
        double eyy = 0.0;

        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (xInc == 0 && yInc == 0) {
                exy += x[k] * y[k];
                exx += x[k] * x[k];
                eyy += y[k] * y[k];
                ex += x[k];
                ey += y[k];
                n++;
            } else if (xInc == 1 && yInc == 0) {
                if (x[k] > 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == 1) {
                if (y[k] > 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == -1 && yInc == 0) {
                if (x[k] < 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == 0 && yInc == -1) {
                if (y[k] < 0.0) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            }
        }

        exx /= n;
        eyy /= n;
        exy /= n;
        ex /= n;
        ey /= n;

//        return new double[]{(exy - ex * ey) / (exx - ex * ex), (double) n};// / Math.sqrt((exx - ex * ex) * (eyy - ey * ey)), (double) n};


        return new double[]{(exy - ex * ey) / (exx - ex * ex), (double) n};// / Math.sqrt((exx - ex * ex) * (eyy - ey * ey)), (double) n};
    }

    private double[] var(double[] x, double[] y, int yInc) {

        double exx = 0.0;

        double ex = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (yInc == 0) {
                exx += x[k] * x[k];
                ex += x[k];
                n++;
            } else if (yInc == 1) {
                if (y[k] > 0.0) {
                    exx += x[k] * x[k];
                    ex += x[k];
                    n++;
                }
            }
        }

        exx /= n;
        ex /= n;

        return new double[]{(exx - ex * ex), (double) n};
    }

    private double getZ(double r) {
        return 0.5 * (log(1.0 + r) - log(1.0 - r));
    }

    public static void main(String... args) {
        new TestFang().TestPwwd7();
    }
}




