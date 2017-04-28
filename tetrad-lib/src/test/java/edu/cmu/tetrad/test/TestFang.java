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
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.correlation;
import static edu.cmu.tetrad.util.StatUtils.covariance;
import static edu.cmu.tetrad.util.StatUtils.mean;
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

//    @Test
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

                double cutoff = 0.0;

                double[] c = cov(x, y, 0, x, cutoff);
                double[] c1 = cov(x, y, 1, x, cutoff);
                double[] c2 = cov(x, y, 0, x, cutoff);

                double vxx = var(x, 1, x, cutoff)[0];
                double vyx = var(y, 1, x, cutoff)[0];
                double vzx = var(z, 1, x, cutoff)[0];

                double vxy = var(x, 1, y, cutoff)[0];
                double vyy = var(y, 1, y, cutoff)[0];
                double vzy = var(z, 1, y, cutoff)[0];

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

//                System.out.println("  vzx = " + nf.format(vzx));
////                System.out.println("  vxx = " + nf.format(vxx));
//                System.out.println("  vzy = " + nf.format(vzy));
                System.out.println("  vzy - vzx = " + nf.format(vzy - vzx));
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

    private double[] cov(double[] x, double[] y, int xInc, double[] var, double cutoff) {
        double exy = 0.0;
        double exx = 0.0;
        double eyy = 0.0;

        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (xInc == 0) {
                exy += x[k] * y[k];
                exx += x[k] * x[k];
                eyy += y[k] * y[k];
                ex += x[k];
                ey += y[k];
                n++;
            } else if (xInc == 1) {
                if (var[k] > cutoff) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (xInc == -1) {
                if (var[k] < cutoff) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    exx += y[k] * y[k];
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

        double sxy = exy - ex * ey;
        double sx = sqrt(exx - ex * ex);
        double sy = sqrt(eyy - ey * ey);

        return new double[]{sxy / (sx * sy), (double) n, ex * ey};
    }

    private double[] var(double[] x, int condition, double[] var, double cutoff) {
        double exx = 0.0;
        double ex = 0.0;
        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition == 0) {
                exx += x[k] * x[k];
                ex += x[k];
                n++;
            } else if (condition == 1) {
                if (var[k] > cutoff) {
                    exx += x[k] * x[k];
                    ex += x[k];
                    n++;
                }
            }
        }

//        n = x.length;

        exx /= n;
        ex /= n;

        return new double[]{(exx - ex * ex), (double) n};
    }

    private double[] e(double[] x, int condition, double[] var, double cutoff) {
        double exx = 0.0;
        double ex = 0.0;
        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition == 0) {
                exx += x[k] * x[k];
                ex += x[k];
                n++;
            } else if (condition == 1) {
                if (var[k] > cutoff) {
                    exx += x[k] * x[k];
                    ex += x[k];
                    n++;
                }
            }
        }

//        n = x.length;

        exx /= n;
        ex /= n;

        return new double[]{(ex), (double) n};
    }

    private double[] e(double[] x, double[] y, int condition, double[] var, double cutoff) {
        double exy = 0.0;
        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition == 0) {
                exy += x[k] * y[k];
                n++;
            } else if (condition == 1) {
                if (var[k] > cutoff) {
                    exy += x[k] * y[k];
                    n++;
                }
            }
        }

        exy /= n;

        return new double[]{(exy), (double) n};
    }

    private double[] mean(double[] x, int condition, double[] var, double cutoff) {
        double exx = 0.0;
        double ex = 0.0;
        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition == 0) {
                exx += x[k] * x[k];
                ex += x[k];
                n++;
            } else if (condition == 1) {
                if (var[k] > cutoff) {
                    exx += x[k] * x[k];
                    ex += x[k];
                    n++;
                }
            }
        }

//        n = x.length;

        exx /= n;
        ex /= n;

        return new double[]{ex, (double) n};
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

        return new double[]{(exy - ex * ey) / Math.sqrt((exx - ex * ex) * (eyy - ey * ey)), (double) n};
    }

//    private double[] cov(double[] x, double[] y, int xInc, int yInc) {
//        double exy = 0.0;
//        double exx = 0.0;
//        double eyy = 0.0;
//
//        double ex = 0.0;
//        double ey = 0.0;
//
//        int n = 0;
//
//        for (int k = 0; k < x.length; k++) {
//            if (xInc == 0 && yInc == 0) {
//                exy += x[k] * y[k];
//                exx += x[k] * x[k];
//                eyy += y[k] * y[k];
//                ex += x[k];
//                ey += y[k];
//                n++;
//            } else if (xInc == 1 && yInc == 0) {
//                if (x[k] > 0.0) {
//                    exy += x[k] * y[k];
//                    exx += x[k] * x[k];
//                    eyy += y[k] * y[k];
//                    ex += x[k];
//                    ey += y[k];
//                    n++;
//                }
//            } else if (xInc == 0 && yInc == 1) {
//                if (y[k] > 0.0) {
//                    exy += x[k] * y[k];
//                    exx += x[k] * x[k];
//                    eyy += y[k] * y[k];
//                    ex += x[k];
//                    ey += y[k];
//                    n++;
//                }
//            } else if (xInc == -1 && yInc == 0) {
//                if (x[k] < 0.0) {
//                    exy += x[k] * y[k];
//                    exx += x[k] * x[k];
//                    eyy += y[k] * y[k];
//                    ex += x[k];
//                    ey += y[k];
//                    n++;
//                }
//            } else if (xInc == 0 && yInc == -1) {
//                if (y[k] < 0.0) {
//                    exy += x[k] * y[k];
//                    exx += x[k] * x[k];
//                    eyy += y[k] * y[k];
//                    ex += x[k];
//                    ey += y[k];
//                    n++;
//                }
//            }
//        }
//
//        exx /= n;
//        eyy /= n;
//        exy /= n;
//        ex /= n;
//        ey /= n;
//
//        return new double[]{(exy - ex * ey), (double) n};/// Math.sqrt((exx - ex * ex) * (eyy - ey * ey)), (double) n};
//    }

    private double getZ(double r) {
        return 0.5 * (log(1.0 + r) - log(1.0 - r));
    }

    public void testSkeptical2() {
        int total = 20;
        int count1 = 0;
        int count2 = 0;
        int sampleSize = 10000;

        List<Double> LCxeyy = new ArrayList<>();
        List<Double> LCxyx = new ArrayList<>();
        List<Double> LCxyy = new ArrayList<>();
        List<Double> Lcxeyy = new ArrayList<>();
        List<Double> Lcxyx = new ArrayList<>();
        List<Double> Lcxyy = new ArrayList<>();

        List<Double> Lcxzx = new ArrayList<>();
        List<Double> Lvxx_cxz = new ArrayList<>();
        List<Double> Lcxzy = new ArrayList<>();
        List<Double> Lvxy_cxz = new ArrayList<>();
//                List<Double> Lcxyy = new ArrayList<>();
        List<Double> Lvyy_cxy = new ArrayList<>();


        for (int index = 0; index < total; index++) {
            SemGraph G0 = new SemGraph();

            Node X = new ContinuousVariable("X");
            Node Y = new ContinuousVariable("Y");
            Node Z = new ContinuousVariable("Z");

            G0.addNode(X);
            G0.addNode(Y);
            G0.addNode(Z);

//            G0.addDirectedEdge(Y, X);
            G0.addDirectedEdge(X, Y);
            G0.addDirectedEdge(Z, X);
            G0.addDirectedEdge(Z, Y);

            G0.setShowErrorTerms(true);

            Node e1 = G0.getExogenous(X);
            Node e2 = G0.getExogenous(Y);
            Node e3 = G0.getExogenous(Z);

            SemPm pm2 = new SemPm(G0);
            double a, b, c;

            DataSet dataSet;

//            {
//                Parameters parameters = new Parameters();
//
//                parameters.set("coefLow", 0.2);
//                parameters.set("coefHigh", 0.8);
//                parameters.set("varLow", 1);
//                parameters.set("varHigh", 3);
//                parameters.set("coefSymmetric", true);
//
//                SemIm im2 = new SemIm(pm2, parameters);
//                StandardizedSemIm im3 = new StandardizedSemIm(im2);
//                dataSet = im3.simulateData(1000, false);
//
//                a = im3.getEdgeCoef(X, Y);
//                b = im3.getEdgeCoef(Z, Y);
//                c = im3.getEdgeCoef(Z, X);
//            }

            try {
                GeneralizedSemPm pm = new GeneralizedSemPm(G0);


                pm.setNodeExpression(X, "c * Z + E_X");
                pm.setNodeExpression(Y, "a * X + b * Z + E_Y");
                pm.setNodeExpression(Z, "E_Z");

                //                pm.setNodeExpression(X, "a * Y + c * Z + E_X");
                //                pm.setNodeExpression(Y, "b * Z + E_Y");
                //                pm.setNodeExpression(Z, "E_Z");


                final String errors = "(Beta(2, 8) - (2 / (2 + 8))) / sqrt((2 * 8) / ((2 + 8) * (2 + 8) * (2 + 8 + 1)))";
                pm.setNodeExpression(e1, errors);
                pm.setNodeExpression(e2, errors);
                pm.setNodeExpression(e3, errors);

                //                final String coef = "Split(-1, -.1, .1, 1)";
                //                final String coef = "Split(-.8, -.1, .1, .8)";
                final String coef = "Split(-.9, -.05, .05, .9)";

                pm.setParameterExpression("a", coef);
                pm.setParameterExpression("b", coef);
                pm.setParameterExpression("c", coef);

                GeneralizedSemIm im = new GeneralizedSemIm(pm);

                dataSet = im.simulateDataRecursive(sampleSize, false);

                a = im.getParameterValue("a");
                b = im.getParameterValue("b");
                c = im.getParameterValue("c");
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }


            DataSet std = DataUtils.standardizeData(dataSet);

//            System.out.println(new CovarianceMatrix(std));

            List<Node> nodes = std.getVariables();
            double[][] colData = std.getDoubleData().transpose().toArray();
            int i = nodes.indexOf(std.getVariable("X"));
            int j = nodes.indexOf(std.getVariable("Y"));
            int k = nodes.indexOf(std.getVariable("Z"));
            double[] x = colData[i];
            double[] y = colData[j];
            double[] z = colData[k];

            double x0 = -5;
            double y0 = 0.0;

            double cxy = cov(x, y, 0, x, x0)[0];
            double cxz = cov(x, z, 0, y, y0)[0];

            double cxyx = cov(x, y, 1, x, x0)[0];
            double cxzx = cov(x, z, 1, x, x0)[0];
            double cxyy = cov(x, y, 1, y, y0)[0];
            double cxzy = cov(x, z, 1, y, y0)[0];

            double vxx = var(x, 1, x, x0)[0];
            double vxy = var(x, 1, y, y0)[0];
            double vyx = var(y, 1, x, y0)[0];
            double vyy = var(y, 1, y, y0)[0];

            double vzy = var(z, 1, y, 0)[0];
            double vzx = var(z, 1, x, 0)[0];

            double exx = e(x, 1, x, 0)[0];
            double eyy = e(y, 1, y, 0)[0];
            double exy = e(x, 1, y, 0)[0];
            double exz_0 = e(x, z, 0, x, 0)[0];
            double exz_y = e(x, z, 1, y, y0)[0];
            double exz_x = e(x, z, 1, x, x0)[0];

            double exy_0 = e(x, y, 0, y, 0)[0];
            double exy_x = e(x, y, 1, y, 0)[0];

            double exx_0 = e(x, x, 0, x, x0)[0];
            double exx_x = e(x, x, 1, x, x0)[0];

            double eyy_0 = e(y, y, 0, y, y0)[0];
            double eyy_x = e(y, y, 1, y, y0)[0];

            double ezy = e(z, 1, y, y0)[0];
            double ezx = e(z, 1, x, x0)[0];


            double mxy = var(x, 1, y, y0)[0];

            double vx = var(x, 0, x, x0)[0];

            double cxeyy = cxyy - a * vxy - b * cxzy;

            NumberFormat nf = new DecimalFormat("0.000");

            {
                System.out.println();

                double Cxy = cov(x, y, 0, 0)[0];
                double Cxyx = vxy * Cxy;
                double Cxeyy = vyy * Cxy - vxy * (Cxy);
                double Cxyy = vxy * Cxy + Cxeyy;

                Cxyy = vyy * Cxy;



                double q = (Cxyy - vxy * Cxy) / (vxy * Cxy);

//                System.out.println("q = " + q);
//
//                System.out.println("vxx = " + vxx + " vxy = " + vxy + " vyy = " + vyy);

                LCxeyy.add(Cxeyy);
                LCxyx.add(Cxyx);
                LCxyy.add(Cxyy);
                Lcxeyy.add(cxeyy);
                Lcxyx.add(cxyx);
                Lcxyy.add(cxyy);

//                System.out.println("Cxeyy = " + nf.format(Cxeyy) + " Cxyx = " + nf.format(Cxyx) + " Cxyy = " + nf.format(Cxyy));
//                System.out.println(" cxeyy = " + nf.format(cxeyy) + " cxyx = " + nf.format(cxyx) + " cxyy = " + nf.format(cxyy));
//                System.out.println(" (abs(Cxyy) > abs(Cxyx)) == (vxy > vxx) " + ((abs(Cxyx) > abs(Cxyy)) == (vxy > vxx)));
////
//                double r1 = cxy * vxy + b * cxz * vxy;
//                double r2 = cxyy + b * cxzy;
//
//                double r1 = vxx * cxz;
//                double r2 = cxzx;
//                System.out.println("r1 = " + r1 + " r2 = " + r2);

//                double r1 = vxx * (a * vx + b * cxz);
//                double r2 = a * vxx + b * cxzx;
//                double r3 = cxyx;
//                System.out.println("r1 = " + r1 + " r2 = " + r2 + " r3 = " + r3);


                Lcxzx.add(cxzx);
                Lvxx_cxz.add(vxx * cxz);
                Lcxzy.add(cxzy);
                Lvxy_cxz.add(vxy * cxz);
                Lvyy_cxy.add(vyy * cxy);


//                System.out.println("cxzx = " + cxzx + " vxx * cxz = " + vxx * cxz);
//                System.out.println("cxzy = " + cxzy + " vxy * cxz = " + vxy * cxz);
//                System.out.println("cxyy = " + cxyy + " vyy * cxy = " + vyy * cxy);

//                (var(Y | Y > 0) + 1/n2 ( ΣY>0Y  ΣY>0Z))
//                  / (var(X | X > 0)  + 1/n2 ( ΣX>0X  ΣX>0Z)) 		a[d, k]
//
//                System.out.println("exz_0 = " + exz_0 + " exz_x = " + exz_x);
//                System.out.println("exy_0 = " + exy_0 + " exy_x = " + exy_x);
//                System.out.println("exx_0 = " + exx_0 + " exx_x = " + exx_x);
//                System.out.println("eyy_0 = " + eyy_0 + " eyy_x = " + eyy_x);
//
//                System.out.println("cxyy = " + nf.format(cxyy));
//                System.out.println("cxyx = " + nf.format(cxyx));
//
//                System.out.println("vyy = " + nf.format(vyy) + " eyy * ezy = " + nf.format(eyy * ezy)
//                        + " vyy + eyy * ezy + eyy * exy = " + nf.format(vyy + eyy * ezy + eyy * exy));
//
//                System.out.println("vxx = " + nf.format(vxx) + " exx * ezx = " + nf.format(exx * ezx)
//                        + " vxx + exx * ezx = " + nf.format(vxx + exx * ezx));



//                System.out.println("q = " + q);
//
//                System.out.println("vxx = " + vxx + " vxy = " + vxy + " vyy = " + vyy);

                LCxeyy.add(Cxeyy);
                LCxyx.add(Cxyx);
                LCxyy.add(Cxyy);
                Lcxeyy.add(cxeyy);
                Lcxyx.add(cxyx);
                Lcxyy.add(cxyy);

//                System.out.println("Cxeyy = " + nf.format(Cxeyy) + " Cxyx = " + nf.format(Cxyx) + " Cxyy = " + nf.format(Cxyy));
//                System.out.println(" cxeyy = " + nf.format(cxeyy) + " cxyx = " + nf.format(cxyx) + " cxyy = " + nf.format(cxyy));
//                System.out.println(" (abs(Cxyy) > abs(Cxyx)) == (vxy > vxx) " + ((abs(Cxyx) > abs(Cxyy)) == (vxy > vxx)));
////
//                double r1 = cxy * vxy + b * cxz * vxy;
//                double r2 = cxyy + b * cxzy;
//
//                double r1 = vxx * cxz;
//                double r2 = cxzx;
//                System.out.println("r1 = " + r1 + " r2 = " + r2);

//                double r1 = vxx * (a * vx + b * cxz);
//                double r2 = a * vxx + b * cxzx;
//                double r3 = cxyx;
//                System.out.println("r1 = " + r1 + " r2 = " + r2 + " r3 = " + r3);


                Lcxzx.add(cxzx);
                Lvxx_cxz.add(vxx * cxz);
                Lcxzy.add(cxzy);
                Lvxy_cxz.add(vxy * cxz);
                Lvyy_cxy.add(vyy * cxy);


//                System.out.println("cxzx = " + cxzx + " vxx * cxz = " + vxx * cxz);
//                System.out.println("cxzy = " + cxzy + " vxy * cxz = " + vxy * cxz);
//                System.out.println("cxyy = " + cxyy + " vyy * cxy = " + vyy * cxy);

//                (var(Y | Y > 0) + 1/n2 ( ΣY>0Y  ΣY>0Z))
//                  / (var(X | X > 0)  + 1/n2 ( ΣX>0X  ΣX>0Z)) 		a[d, k]

                System.out.println("vxx  " + vxx + " vxy = " + vxy);
//
                System.out.println("exz_0 = " + exz_0 + " exz_x = " + exz_x + " exz_y = " + exz_y);

                System.out.println("exx = " + exx + " ezx = " + ezx);
                System.out.println("exy = " + exy + " ezy = " + ezy);

                double cxzx2 = exz_x - exx * ezx;
                double cxzy2 = exz_y - exy * ezy;

                System.out.println("cxzx = " + cxzx + " cxzy = " + cxzy);
                System.out.println("cxzx2 = " + cxzx2 + " cxzy2 = " + cxzy2);


//                System.out.println("cxyy = " + nf.format(cxyy));
//                System.out.println("cxyx = " + nf.format(cxyx));
//
//                System.out.println("vyy = " + nf.format(vyy) + " eyy * ezy = " + nf.format(eyy * ezy)
//                        + " vyy + eyy * ezy + eyy * exy = " + nf.format(vyy + eyy * ezy + eyy * exy));
//
//                System.out.println("vxx = " + nf.format(vxx) + " exx * ezx = " + nf.format(exx * ezx)
//                        + " vxx + exx * ezx = " + nf.format(vxx + exx * ezx));


//                System.out.println("a = " + (cxyy - cxyx) / (vxy - vxx));
//                System.out.println("a2 = " + (cxyy - cxyx) / (vxy - vxx + cxeyy));

//                System.out.println("cxyy - cxyx = " + (cxyy - cxyx));
//                System.out.println("a * (vxy - vxx) = " + a * (vxy - vxx)
//                        + " " + " a * (vxy - vxx) + cxeyy =" + (a * (vxy - vxx) + cxeyy));

//                System.out.println("cxz = " + cxz + " cxzx = " + cxzx + " cxzy = " + cxzy);

                //' + " " + (abs(0.5 * (vxy - vxx) + cxeyy)));

                //' + " " + (abs(0.5 * (vxy - vxx) + cxeyy)));

            }
        }

//        System.out.println();
//
//        System.out.println("mean(cxzx) = " + avg(Lcxzx) + " mean(vxx * cxz) = " + avg(Lvxx_cxz));
//        System.out.println("mean(cxzy) = " + avg(Lcxzy) + " mean(vxy * cxz) = " + avg(Lvxy_cxz));
//        System.out.println("mean(cxyy) = " + avg(Lcxyy) + " mean(vyy * cxy) = " + avg(Lvyy_cxy));
//
//        System.out.println();
//
//        System.out.println("cor(cxzx, vxx * cxz) = " + Lcor(Lcxzx, Lvxx_cxz));
//        System.out.println("cor(cxzy, vxy * cxz) = " + Lcor(Lcxzy, Lvxy_cxz));
//        System.out.println("cor(cxyy, vyy * cxy) = " + Lcor(Lcxyy, Lvyy_cxy));

        System.out.println();

//        System.out.println("mean(Cxeyy) = " + avg(LCxeyy) + " mean(Cxyx) = " + avg(LCxyx) + " mean(Cxyy) = " + avg(LCxyy));
//        System.out.println("mean(cxeyy) = " + avg(Lcxeyy) + " mean(cxyx) = " + avg(Lcxyx) + " mean(cxyy) = " + avg(Lcxyy));
//
//        System.out.println();
//
//        System.out.println("cor(Cxeyy, cxeyy) = " + Lcor(LCxeyy, Lcxeyy));
//        System.out.println("cor(Cxyx, cxyx) = " + Lcor(LCxyx, Lcxyx));
//        System.out.println("cor(Cxyy, cxyy) = " + Lcor(LCxyy, Lcxyy));
    }

    private String Lcor(List<Double> L1, List<Double> L2) {
        double[] l1 = new double[L1.size()];
        double[] l2 = new double[L2.size()];

        for (int i = 0; i < L1.size(); i++) {
            l1[i] = L1.get(i);
            l2[i] = L2.get(i);
        }

        double cor = correlation(l1, l2);

        NumberFormat nf = new DecimalFormat("0.000");

        return nf.format(cor);
    }

    private String avg(List<Double> list) {
        double sum = 0.0;

        for (double d : list) {
            sum += d;
        }

        double avg = sum / list.size();

        NumberFormat nf = new DecimalFormat("0.000");

        return nf.format(avg);
    }

    private double[] exy(double[] x, double[] y, int xInc, int yInc) {
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

        n = x.length;

        exy /= n;

        return new double[]{exy, (double) n};
    }

    public double g(double x) {
        return log(cosh(max(0, x)));
    }

    private double[] cov(double[] x, double[] y, int xInc, int yInc) {
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

        n = x.length;

        exx /= n;
        eyy /= n;
        exy /= n;
        ex /= n;
        ey /= n;

        double sxy = exy - ex * ey;
        double sx = sqrt(exx - ex * ex);
        double sy = sqrt(eyy - ey * ey);

        return new double[]{sxy, (double) n, ex * ey};
    }

    public static void main(String... args) {
        new TestFang().testSkeptical2();
    }
}




