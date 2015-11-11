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
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.*;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import nu.xom.ParsingException;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.*;


/**
 * Tests basic functionality of the statistical utilities.
 *
 * @author Joseph Ramsey
 */
public class TestStatUtils extends TestCase {
    public TestStatUtils(String name) {
        super(name);
    }

    /**
     * Tests that the unconditional correlations and covariances are correct,
     * at least for the unconditional tests.
     */
    public void testConditionalCorrelation() {

        RandomUtil.getInstance().setSeed(30299533L);

        // Make sure the unconditional correlations and covariances are OK.
        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 3,
                3, 3, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet dataSet = im.simulateData(1000, false);
        double[] x = dataSet.getDoubleData().getColumn(0).toArray();
        double[] y = dataSet.getDoubleData().getColumn(1).toArray();

        double r1 = StatUtils.correlation(x, y);
        double s1 = StatUtils.covariance(x, y);
        double v1 = StatUtils.variance(x);
        double sd1 = StatUtils.sd(x);

        ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
        TetradMatrix _cov = cov.getMatrix();

        System.out.println(cov);

        double r2 = StatUtils.partialCorrelation(_cov, 0, 1);
        double s2 = StatUtils.partialCovariance(_cov, 0, 1);
        double v2 = StatUtils.partialVariance(_cov, 0);
        double sd2 = StatUtils.partialStandardDeviation(_cov, 0);

        assertEquals(r1, r2, .1);
        assertEquals(s1, s2, .1);
        assertEquals(v1, v2, .1);
        assertEquals(sd1, sd2, 0.1);
    }

    public void testRankCorr() {
        double[] a1 = new double[]{2, 2, 3};
        double[] a2 = new double[]{2, 3, 4};

        double r = StatUtils.rankCorrelation(a1, a2);
        System.out.println("rank corr = " + r);
    }

    public void testChiSqCdf() {
//        for (int x = 0; x < 5; x++) {
//            for (int df = 1; df < 5; df++) {
//                System.out.println("x = " + x + " df = " + df + " cum " + ProbUtils.chisqCdf(x, df));
//            }
//        }

        ChiSquaredDistribution dist = new ChiSquaredDistribution(1);
        double log = Math.log(1000);
        System.out.println(1.0 - dist.cumulativeProbability(log));
    }

    public void testSpecial() {
        int numCases = 1000;

        double x, y, z, w;
        double _x[] = new double[numCases];
        double _y[] = new double[numCases];
        double _z[] = new double[numCases];
        double _w[] = new double[numCases];

        for (int i = 0; i < numCases; i++) {
            x = RandomUtil.getInstance().nextDouble();
            y = RandomUtil.getInstance().nextDouble();
            z = RandomUtil.getInstance().nextDouble();

            if (x + y + z > 1) {
                i--;
                continue;
            }

            w = 1.0 - x - y - z;
            NumberFormat nf = new DecimalFormat("0.0000");

            if (x * y > z * w) {
                System.out.println(nf.format(x) + " " + nf.format(y) + " " + nf.format(z) + " " + nf.format(w));
                _x[i] = x;
                _y[i] = y;
                _z[i] = z;
                _w[i] = w;
            }
        }

        System.out.println();
        System.out.println("Avg x = " + (StatUtils.mean(_x)));
        System.out.println("Avg y = " + (StatUtils.mean(_y)));
        System.out.println("Avg z = " + (StatUtils.mean(_z)));
        System.out.println("Avg w = " + (StatUtils.mean(_w)));

        System.out.println();
        System.out.println("Var x = " + (StatUtils.variance(_x)));
        System.out.println("Var y = " + (StatUtils.variance(_y)));
        System.out.println("Var z = " + (StatUtils.variance(_z)));
        System.out.println("Var w = " + (StatUtils.variance(_w)));

    }

    public void testNongaussianSums() {
        int numTrials = 500;
        int sampleSize = 1000;
        int count = 0;
        int failed = 0;

        for (int trial = 0; trial < numTrials; trial++) {
            double d1 = RandomUtil.getInstance().nextUniform(.1, 2.0);
            double d2 = RandomUtil.getInstance().nextUniform(.1, 2.0);

            double c1 = 1.0; //RandomUtil.getInstance().nextUniform(-1, 1);
            double c2 = 1.0; //RandomUtil.getInstance().nextUniform(-1, 1);

            double[] col1 = new double[sampleSize];
            double[] col2 = new double[sampleSize];
            double[] sum = new double[sampleSize];


            int dist1 = RandomUtil.getInstance().nextInt(3);
            int dist2 = RandomUtil.getInstance().nextInt(3);
            double orientation1 = RandomUtil.getInstance().nextDouble();
            double orientation2 = RandomUtil.getInstance().nextDouble();

            for (int i = 0; i < sampleSize; i++) {

                switch (dist1) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col1[i] = signum(v) * Math.pow(Math.abs(v), d1);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col1[i] = signum(v1) * Math.pow(Math.abs(v1), d1);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col1[i] = signum(v2) * Math.pow(v2, d1);
                        if (orientation1 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                switch (dist2) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col2[i] = signum(v) * Math.pow(Math.abs(v), d2);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col2[i] = signum(v1) * Math.pow(Math.abs(v1), d2);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col2[i] = signum(v2) * Math.pow(v2, d2);
                        if (orientation2 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                // These have negative kurtosis
                sum[i] = c1 * col1[i] + c2 * col2[i];
            }

            double a1 = new AndersonDarlingTest(col1).getASquaredStar();
            double a2 = new AndersonDarlingTest(col2).getASquaredStar();
            double s = new AndersonDarlingTest(sum).getASquaredStar();
//
//            double a1 = StatUtils.logCoshScore(col1);
//            double a2 = StatUtils.logCoshScore(col2);
//            double s = StatUtils.logCoshScore(sum);

            if (!(s <= a1)) { //min(a1, a2))) {
                System.out.println("dist1 = " + dist1 + " dist2 = " + dist2);
                System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " a1 = " + f(a1) + " a2 = " + f(a2)
                        + " avg = " + f((a1 + a2) / 2) + " s = " + f(s));
                failed++;
            }

            count++;
        }

        System.out.println("Percent failed = " + (failed / (double) count));
    }

    public void testNongaussianSums2() {
        int sampleSize = 1000;
        int count = 0;
        int failed = 0;

        double bound = 2.0;
        double step = 0.1;

        int dist1 = RandomUtil.getInstance().nextInt(4);
        int dist2 = RandomUtil.getInstance().nextInt(4);

        for (double d1 = -bound; d1 <= bound; d1 += step) {
            for (double d2 = -bound; d2 <= bound; d2 += step) {
                double[] col1 = new double[sampleSize];
                double[] col2 = new double[sampleSize];
                double[] sum = new double[sampleSize];

                for (int i = 0; i < sampleSize; i++) {

                    switch (dist1) {
                        case 0:
                            col1[i] = RandomUtil.getInstance().nextUniform(0, 1);
                            break;
                        case 1:
                            col1[i] = RandomUtil.getInstance().nextNormal(0, 1);
                            break;
                        case 2:
                            col1[i] = RandomUtil.getInstance().nextBeta(2, 5);
                            break;
                        case 3:
                            col1[i] = -RandomUtil.getInstance().nextBeta(2, 5);
//                            col1[i] = RandomUtil.getInstance().nextGamma(.5, .7);
                            break;
                        default:
                            throw new IllegalStateException();
                    }

                    switch (dist2) {
                        case 0:
                            col2[i] = RandomUtil.getInstance().nextUniform(0, 1);
                            break;
                        case 1:
                            col2[i] = RandomUtil.getInstance().nextNormal(0, 1);
                            break;
                        case 2:
                            col2[i] = RandomUtil.getInstance().nextBeta(2, 5);
                            break;
                        case 3:
                            col2[i] = -RandomUtil.getInstance().nextBeta(2, 5);
//                            col2[i] = RandomUtil.getInstance().nextGamma(.5, .7);
                            break;
                        default:
                            throw new IllegalStateException();
                    }

                    // These have negative kurtosis
                    sum[i] = d1 * col1[i] + d2 * col2[i];
                }

//                double a1 = new AndersonDarlingTest(col1).getASquaredStar();
//                double a2 = new AndersonDarlingTest(col2).getASquaredStar();
//                double s = new AndersonDarlingTest(sum).getASquaredStar();
//
                double a1 = StatUtils.logCoshScore(col1);
                double a2 = StatUtils.logCoshScore(col2);
                double s = StatUtils.logCoshScore(sum);

//                if (!(s <= ((a1 + a2) / 2.0))) {
                if (!(s <= max(a1, a2))) {
                    System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " a1 = " + f(a1) + " a2 = " + f(a2)
                            + " avg = " + f((a1 + a2) / 2) + " s = " + f(s));
                    failed++;
                }

                count++;
            }
        }

        System.out.println("Percent errors = " + (failed / (double) count));
    }

    public void testNongaussianSums3() {
        int n = 1000;
        int count = 0;
        int positive = 0;

        double[] col1 = new double[n];
        double[] col2 = new double[n];
        double[] sum = new double[n];

        for (int k = 0; k < 1000; k++) {
            double d1 = RandomUtil.getInstance().nextUniform(-10, 10);
            double d2 = RandomUtil.getInstance().nextUniform(-10, 10);

            double e1 = RandomUtil.getInstance().nextUniform(0, 3);
            double e2 = RandomUtil.getInstance().nextUniform(0, 3);

            for (int i = 0; i < n; i++) {
                col1[i] = Math.pow(RandomUtil.getInstance().nextNormal(0, 1), e1);
                col2[i] = Math.pow(RandomUtil.getInstance().nextNormal(0, 1), e2);
                sum[i] = d1 * col1[i] + d2 * col2[i];
            }

            double a1 = new AndersonDarlingTest(col1).getASquared();
            double a2 = new AndersonDarlingTest(col2).getASquared();
            double s = new AndersonDarlingTest(sum).getASquared();

//                double a1 = StatUtils.logCoshScore(col1);
//                double a2 = StatUtils.logCoshScore(col2);
//                double s = StatUtils.logCoshScore(sum);

//                if (!(s <= ((a1 + a2) / 2.0))) {
            if (!(s < max(a1, a2))) {
                System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " a1 = " + f(a1) + " a2 = " + f(a2)
                        + " avg = " + f((a1 + a2) / 2) + " s = " + f(s));
                positive++;
            }

            count++;
        }

        System.out.println("Percent errors = " + (positive / (double) count));
    }

    public void testNongaussianSums4() {
        int n = 1000;
        int count = 0;
        int positive = 0;

        double[] col1 = new double[n];
        double[] col2 = new double[n];
        double[] sum = new double[n];

        for (int k = 0; k < 10; k++) {
            double f1 = RandomUtil.getInstance().nextUniform(0, 5);
            double f2 = RandomUtil.getInstance().nextUniform(0, 5);
            double f3 = RandomUtil.getInstance().nextUniform(0, 5);
            double f4 = RandomUtil.getInstance().nextUniform(0, 5);
            double f5 = RandomUtil.getInstance().nextUniform(0, 5);
            double f6 = RandomUtil.getInstance().nextUniform(0, 5);

            double d1 = RandomUtil.getInstance().nextUniform(-1, 1);
            double d2 = RandomUtil.getInstance().nextUniform(-1, 1);

            for (int i = 0; i < n; i++) {
                col1[i] = RandomUtil.getInstance().nextBeta(2, 5) + f1;
                col1[i] += RandomUtil.getInstance().nextBeta(2, 5) + f2;
                col1[i] += RandomUtil.getInstance().nextBeta(2, 5) + f3;

                col2[i] = RandomUtil.getInstance().nextBeta(2, 5) + f4;
                col2[i] += RandomUtil.getInstance().nextBeta(2, 5) + f5;
                col2[i] += RandomUtil.getInstance().nextBeta(2, 5) + f6;

                sum[i] = d1 * col1[i] + d2 * col2[i];
            }

            double a1 = new AndersonDarlingTest(col1).getASquared();
            double a2 = new AndersonDarlingTest(col2).getASquared();
            double s = new AndersonDarlingTest(sum).getASquared();

//                double a1 = StatUtils.logCoshScore(col1);
//                double a2 = StatUtils.logCoshScore(col2);
//                double s = StatUtils.logCoshScore(sum);

//                if (!(s <= ((a1 + a2) / 2.0))) {
            if (!(s < 1.2 * max(a1, a2))) {
                System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " a1 = " + f(a1) + " a2 = " + f(a2)
                        + " avg = " + f((a1 + a2) / 2) + " s = " + f(s));
                positive++;
            }

            count++;
        }

        System.out.println("Percent errors = " + (positive / (double) count));
    }

    public void testNongaussianSums5() {
        int numTrials = 500;
        int sampleSize = 10;
        int count = 0;
        int failed = 0;

        for (int trial = 0; trial < numTrials; trial++) {
            double d1 = RandomUtil.getInstance().nextUniform(.1, 2.0);
            double d2 = RandomUtil.getInstance().nextUniform(.1, 2.0);
            double d3 = RandomUtil.getInstance().nextUniform(.1, 2.0);

            double c1 = RandomUtil.getInstance().nextUniform(-1, 1);
            double c2 = RandomUtil.getInstance().nextUniform(-1, 1);
            double c3 = RandomUtil.getInstance().nextUniform(-1, 1);

            double[] col1 = new double[sampleSize];
            double[] col2 = new double[sampleSize];
            double[] col3 = new double[sampleSize];
            double[] sum = new double[sampleSize];

            int dist1 = RandomUtil.getInstance().nextInt(3);
            int dist2 = RandomUtil.getInstance().nextInt(3);
            int dist3 = RandomUtil.getInstance().nextInt(3);
            double orientation1 = RandomUtil.getInstance().nextDouble();
            double orientation2 = RandomUtil.getInstance().nextDouble();
            double orientation3 = RandomUtil.getInstance().nextDouble();

            for (int i = 0; i < sampleSize; i++) {
                switch (dist1) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col1[i] = signum(v) * Math.pow(Math.abs(v), d1);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col1[i] = signum(v1) * Math.pow(Math.abs(v1), d1);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col1[i] = signum(v2) * Math.pow(v2, d1);
                        if (orientation1 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                switch (dist1) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col2[i] = signum(v) * Math.pow(Math.abs(v), d1);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col2[i] = signum(v1) * Math.pow(Math.abs(v1), d1);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col2[i] = signum(v2) * Math.pow(v2, d1);
                        if (orientation2 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                switch (dist1) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col3[i] = signum(v) * Math.pow(Math.abs(v), d1);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col3[i] = signum(v1) * Math.pow(Math.abs(v1), d1);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col3[i] = signum(v2) * Math.pow(v2, d1);
                        if (orientation3 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                // These have negative kurtosis
                sum[i] = c1 * col1[i] + c2 * col2[i] + c3 * col3[i];
            }

            double a1 = new AndersonDarlingTest(col1).getASquaredStar();
            double a2 = new AndersonDarlingTest(col2).getASquaredStar();
            double a3 = new AndersonDarlingTest(col3).getASquaredStar();
            double s = new AndersonDarlingTest(sum).getASquaredStar();
//
//            double a1 = StatUtils.logCoshScore(col1);
//            double a2 = StatUtils.logCoshScore(col2);
//            double s = StatUtils.logCoshScore(sum);

//            if (!(s <= max(max(a1, a2), a3))) {
            if (!(s <= min(min(a1, a2), a3))) {
                System.out.println("dist1 = " + dist1 + " dist2 = " + dist2 + " dist3 = " + dist3);
                System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " d3 = " + f(d3) +
                        " a1 = " + f(a1) + " a2 = " + f(a2) + " a3 = " + f(a3)
                        + " avg = " + f((a1 + a2 + a3) / 3) + " s = " + f(s));
                failed++;
            }

            count++;
        }

        System.out.println("Percent failed = " + (failed / (double) count));
    }

    public void testNongaussianSums6() {
        int numTrials = 500;
        int sampleSize = 1000;
        int count = 0;
        int failed = 0;

        for (int trial = 0; trial < numTrials; trial++) {
            double c = RandomUtil.getInstance().nextUniform(.2, 2.0);

            double c1 = RandomUtil.getInstance().nextUniform(.5, 1.5);
            double c2 = RandomUtil.getInstance().nextUniform(.5, 1.5);
            double c3 = RandomUtil.getInstance().nextUniform(.5, 1.5);

            if (RandomUtil.getInstance().nextDouble() > 0.5) c1 = -c1;
            if (RandomUtil.getInstance().nextDouble() > 0.5) c2 = -c2;
            if (RandomUtil.getInstance().nextDouble() > 0.5) c3 = -c3;

            double[] ex = new double[sampleSize];
            double[] ey = new double[sampleSize];
            double[] ez = new double[sampleSize];

            int dist1 = RandomUtil.getInstance().nextInt(3);
            double orientation = RandomUtil.getInstance().nextDouble();

            for (int i = 0; i < sampleSize; i++) {
                switch (dist1) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        ex[i] = signum(v) * Math.pow(Math.abs(v), c);

                        v = RandomUtil.getInstance().nextUniform(0, 1);
                        ey[i] = signum(v) * Math.pow(Math.abs(v), c);

                        v = RandomUtil.getInstance().nextUniform(0, 1);
                        ez[i] = signum(v) * Math.pow(Math.abs(v), c);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        ex[i] = signum(v1) * Math.pow(Math.abs(v1), c);

                        v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        ey[i] = signum(v1) * Math.pow(Math.abs(v1), c);

                        v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        ez[i] = signum(v1) * Math.pow(Math.abs(v1), c);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        ex[i] = signum(v2) * Math.pow(v2, c);
                        if (orientation < 0.5) ex[i] = -ex[i];

                        v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        ey[i] = signum(v2) * Math.pow(v2, c);
                        if (orientation < 0.5) ey[i] = -ey[i];

                        v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        ez[i] = signum(v2) * Math.pow(v2, c);
                        if (orientation < 0.5) ez[i] = -ez[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }

//            ex = DataUtils.standardizeData(ex);
//            ey = DataUtils.standardizeData(ey);
//            ez = DataUtils.standardizeData(ez);

            double[] sumx = new double[sampleSize];
            double[] sumy = new double[sampleSize];
            double[] sumz = new double[sampleSize];

            for (int i = 0; i < sampleSize; i++) {
                sumx[i] = ex[i];
                sumy[i] = c1 * sumx[i] + ey[i];
                sumz[i] = c2 * sumx[i] + c3 * sumy[i] + ez[i];
            }

            double a1 = new AndersonDarlingTest(sumx).getASquaredStar();
            double a2 = new AndersonDarlingTest(sumy).getASquaredStar();
            double a3 = new AndersonDarlingTest(sumz).getASquaredStar();

            double aex = new AndersonDarlingTest(ex).getASquaredStar();
            double aey = new AndersonDarlingTest(ey).getASquaredStar();
            double aez = new AndersonDarlingTest(ez).getASquaredStar();
//
//            double a1 = StatUtils.logCoshScore(ex);
//            double a2 = StatUtils.logCoshScore(ey);
//            double s = StatUtils.logCoshScore(sumx);

//            if (!(s <= max(max(a1, a2), a3))) {
            if (!(/*a1 > a2 && a1 > a3 &&*/ aey > a3)) {
//                System.out.println("a1 = " + a1 + " a2 = " + a2 + " a3 = " + a3);
                failed++;
            }

            count++;
        }

        System.out.println("Percent failed = " + (failed / (double) count));
    }

    public void testLogTerms() {
        double bound = 5;
        double step = 1;

        for (double x = -bound; x <= bound; x += step) {
            for (double y = -bound; y <= bound; y += step) {
                double ax = log(RandomUtil.getInstance().normalCdf(0, 1, x));
                double ay = log(RandomUtil.getInstance().normalCdf(0, 1, y));

                double z = (x + y) / 2; // / Math.sqrt(2);

                double az = log(RandomUtil.getInstance().normalCdf(0, 1, z));

                if (!(az >= min(ax, ay))) {
                    System.out.println("x = " + f(x) + " y = " + f(y) + " z = " + f(z) +
                            " ax = " + f(ax) + " ay = " + f(ay) + " az = " + f(az));
                }
            }
        }

//        for (double x = 0; x <= bound; x+=step) {
////            double ax = log(RandomUtil.getInstance().normalCdf(0, 1, x));
//
//            double xPlus = x;
//            double xMinus = -x;
//
//            double axPlus = log(RandomUtil.getInstance().normalCdf(0, 1, xPlus));
//            double axMinus = log(RandomUtil.getInstance().normalCdf(0, 1, xMinus));
//
//            if (!(axPlus >= axMinus)) {
//                System.out.println("x = " + f(x) + " axMinus = " + f(axMinus) + " axPlus = " + f(axPlus));
//            }
//        }

    }

    // Tests to make sure the Normal CDF of an average of two numbers is between the Normal CDF of either number.

    public void testAverageNormCdf() {
        for (double d1 = -3.9; d1 <= 3.0; d1 += .1) {
            for (double d2 = -3.0; d2 <= 3.0; d2 += .1) {
                double e1 = ProbUtils.normalCdf(d1);
                double e2 = ProbUtils.normalCdf(d2);
                double s = ProbUtils.normalCdf((1 / sqrt(2)) * (d1 + d2));

                if (!between(e1, e2, s)) {
                    System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " F(e1) = " + f(e1) + " F(e2) = " + f(e2) + " F((d1+d2)/sqrt(2)) = " + f(s)
                            + " (F(e1) + F(e2)) / 2" + f((e1 + e2) / 2.0));
                }
            }
        }
    }

    public void testWeightedSum() {
        int n = 100;
        double[] col = new double[n];
        for (int i = 0; i < n; i++) {
            col[i] = RandomUtil.getInstance().nextDouble();
        }
//
//        double[] col = new double[]{2, 4, 1, 3};

        System.out.println("col = " + Arrays.toString(col));

        double unsortedSum = 0;

        for (int i = 0; i < n; i++) {
            unsortedSum += i * col[i];
        }

        double sortedSum = 0;
        Arrays.sort(col);

        for (int i = 0; i < n; i++) {
            sortedSum += i * col[i];
        }

        System.out.println("Unsorted = " + unsortedSum + " sorted = " + sortedSum + " " + (sortedSum >= unsortedSum));

    }

    public void test5() {
        for (double d1 = -3.9; d1 <= 3.0; d1 += .01) {
            for (double d2 = -3.0; d2 <= 3.0; d2 += .01) {

                double s = (d1 + d2) / Math.sqrt(2);

//                if (!between(d1, d2, s)) {
                if (!(Math.abs(s) > Math.min(Math.abs(d1), Math.abs(d2)))) {
                    System.out.println("d1 = " + f(d1) + " d2 = " + f(d2) + " (d1+d2)/sqrt(2) = " + f(s));
                }
            }
        }
    }

    public void testLogCoshExp() {
        double sum = 0.0;
        int numSamples = 10000000;

        for (int i = 0; i < numSamples; i++) {
            double randNorm = RandomUtil.getInstance().nextNormal(0, 1);

            double a = Math.log(Math.cosh(randNorm));

            sum += a;
        }

        double b = sum / numSamples;

        System.out.println(b);
    }

    public void test() {
    }

    public void testMaxEnt() {
        double[] x = new double[]{1, 2, 3, 4, 5};

        System.out.println(StatUtils.maxEntApprox(x));
    }

    public void testEquals() {
        double[] x = new double[]{1, 2};
        double[] y = new double[]{1, 2};

        System.out.println(x.equals(y));
    }

    public void test11() {
        try {
            DataReader reader = new DataReader();
            reader.setDelimiter(DelimiterType.WHITESPACE);
            reader.setVariablesSupplied(false);
//            File file = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.06.29/500 Node/amatrix.txt");

//            File file = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.06.29/500 Node/dataset2/500nodesCond4/nma_rsn_data_experiment_3/model_art_1/matA.txt");
            File file = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.06.29/500 Node/dataset4/500nodes200pointsDIFF/matA.txt");
            DataSet dataSet = reader.parseTabular(file);
//            System.out.println(dataSet);
            List<Node> variables = dataSet.getVariables();
            Graph g = new EdgeListGraph(variables);

            for (int i = 0; i < dataSet.getNumColumns(); i++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    if (i == j) continue;

                    double d = dataSet.getDouble(i, j);

                    if (d > 0) {
                        g.addDirectedEdge(variables.get(j), variables.get(i));
                    }
                }
            }

            PrintWriter out = new PrintWriter(new FileWriter(new File(file.getParent(), "graph.txt")));
            out.println(g);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void rtest11a() {
        int numSubjects = 10;

        try {

            // Read true graph.
            DataReader reader = new DataReader();
            reader.setDelimiter(DelimiterType.WHITESPACE);
            reader.setVariablesSupplied(false);
//            File dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.06.29/500 Node/dataset3/500nodesCond4");
//            File file = new File(dir, "nma_rsn_data_experiment_3/model_art_1/matA.txt");
//            File dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.06.29/500 Node/dataset3/500nodes200pointsSAMEstr");
//            File dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.06.29/500 Node/dataset4/500nodes200pointsDIFF");
            File dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.09.03/50nodesALL2cycles");

            // Read in the true graph.
            File file = new File(dir, "matA.txt");
            DataSet dataSet = reader.parseTabular(file);
            List<Node> variables = dataSet.getVariables();
            Graph gTrue = new EdgeListGraph(variables);

            for (int i = 0; i < dataSet.getNumColumns(); i++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    if (i == j) continue;

                    double d = dataSet.getDouble(i, j);

                    if (d > 0) {
                        gTrue.addDirectedEdge(variables.get(j), variables.get(i));
                    }
                }
            }

            PrintWriter out = new PrintWriter(new FileWriter(new File(dir, "truegraph.txt")));
            out.println(gTrue);
            out.close();

            // Read in the data sets.
            List<DataSet> dataSets = new ArrayList<DataSet>();

            for (int i = 0; i < numSubjects; i++) {
                File _dir = new File(dir, "experiment_2");
                File _file = new File(_dir, "BOLDnoise_" + (i + 1) + ".txt");

                DataSet _dataSet = reader.parseTabular(_file);
                dataSets.add(_dataSet);
            }

            // Run PC on data sets individually, then orient

            Lofs2.Rule[] rules = new Lofs2.Rule[]{
                    Lofs2.Rule.R1,
                    Lofs2.Rule.R3,
                    Lofs2.Rule.R4,
                    Lofs2.Rule.EB,
                    Lofs2.Rule.Tanh,
                    Lofs2.Rule.Skew,
                    Lofs2.Rule.RSkew,
                    Lofs2.Rule.Patel,
                    Lofs2.Rule.SkewE,
                    Lofs2.Rule.RSkewE
            };

            double[][] results = new double[5 * (numSubjects + 1)][rules.length];

            List<Graph> pcResults = new ArrayList<Graph>();

            for (int i = 0; i < numSubjects; i++) {
                DataSet _dataSet = dataSets.get(i);
                List<DataSet> _listDataSets = Collections.singletonList(_dataSet);
                IndependenceTest test = new IndTestFisherZConcatenateResiduals(_listDataSets, 0.0001);
//                IndependenceTest test = new IndTestFisherZConcatenateResiduals(_listDataSets, 1e-15);
                Pc pc = new Pc(test);
                Graph g1 = pc.search();
                g1 = GraphUtils.replaceNodes(g1, gTrue.getNodes());
                pcResults.add(g1);
            }

//            IndependenceTest test = new IndTestFisherZConcatenateResiduals(dataSets, 0.0001);
            IndependenceTest test = new IndTestFisherZConcatenateResiduals(dataSets, 1e-15);
            Pc pc = new Pc(test);
            Graph g1 = pc.search();
            g1 = GraphUtils.replaceNodes(g1, gTrue.getNodes());
            pcResults.add(g1);

            for (Edge edge : g1.getEdges()) {
                List<Edge> edges = g1.getEdges(edge.getNode1(), edge.getNode2());
                if (edges.size() > 1) throw new IllegalArgumentException(edges + "");
            }

            for (int r = 0; r < rules.length; r++) {
                Lofs2.Rule rule = rules[r];
                System.out.println("Rule: " + rule);

                for (int i = 0; i < numSubjects; i++) {
                    DataSet _dataSet = dataSets.get(i);
                    List<DataSet> _listDataSets = Collections.singletonList(_dataSet);
                    Lofs2 lofs = new Lofs2(pcResults.get(i), _listDataSets);
                    lofs.setRule(rule);
                    lofs.setAlpha(1.0);
                    lofs.setZeta(1.0);

                    if (rule == Lofs2.Rule.R4) {
                        lofs.setOrientStrongerDirection(true);
                        lofs.setEdgeCorrected(true);
                        lofs.setEpsilon(0.2);
                        lofs.setZeta(1.0);
                    }

                    Graph g2 = lofs.orient();

//                    System.out.println(g2);

                    g2 = GraphUtils.replaceNodes(g2, gTrue.getNodes());

                    int adjFn = adjacenciesComplement(gTrue, g2);
                    int adjFp = adjacenciesComplement(g2, gTrue);
                    int arrowFn = edgesComplement(gTrue, g2);
                    int arrowFp = edgesComplement(g2, gTrue);

                    int truePosAdj = truePositivesAdj(gTrue, g2);
                    int truePosOrient = truePositivesOrient(gTrue, g2);

                    double adjPrecision = truePosAdj / (double) (truePosAdj + adjFp);
                    double adjRecall = truePosAdj / (double) (truePosAdj + adjFn);
                    double orientationPrecision = truePosOrient / (double) (truePosOrient + arrowFp);
                    double orientationRecall = truePosOrient / (double) (truePosOrient + arrowFn);
                    double pta = truePosOrient / (double) (truePosAdj);

                    System.out.println("Data set # " + (i + 1));

                    System.out.println("AP = " + adjPrecision);
                    System.out.println("AR = " + adjRecall);
                    System.out.println("OP = " + orientationPrecision);
                    System.out.println("OR = " + orientationRecall);
                    System.out.println("PTA = " + pta);

                    results[i * 5 + 0][r] = adjPrecision;
                    results[i * 5 + 1][r] = adjRecall;
                    results[i * 5 + 2][r] = orientationPrecision;
                    results[i * 5 + 3][r] = orientationRecall;
                    results[i * 5 + 4][r] = pta;
                }

                // Now put all data sets together.
                {
                    Lofs2 lofs = new Lofs2(pcResults.get(numSubjects), dataSets);
                    lofs.setRule(rule);

                    lofs.setAlpha(1.0);
                    lofs.setZeta(1.0);

                    if (rule == Lofs2.Rule.R4) {
                        lofs.setOrientStrongerDirection(true);
                        lofs.setEdgeCorrected(true);
                        lofs.setEpsilon(0.2);
                    }

                    Graph g2 = lofs.orient();
                    g2 = GraphUtils.replaceNodes(g2, gTrue.getNodes());

                    int adjFn = adjacenciesComplement(gTrue, g2);
                    int adjFp = adjacenciesComplement(g2, gTrue);
                    int arrowFn = edgesComplement(gTrue, g2);
                    int arrowFp = edgesComplement(g2, gTrue);

                    int truePosAdj = truePositivesAdj(gTrue, g2);
                    int truePosOrient = truePositivesOrient(gTrue, g2);

                    double adjPrecision = truePosAdj / (double) (truePosAdj + adjFp);
                    double adjRecall = truePosAdj / (double) (truePosAdj + adjFn);
                    double orientationPrecision = truePosOrient / (double) (truePosOrient + arrowFp);
                    double orientationRecall = truePosOrient / (double) (truePosOrient + arrowFn);
                    double pta = truePosOrient / (double) (truePosAdj);

                    System.out.println("Combining all data sets:");

                    System.out.println("AP = " + adjPrecision);
                    System.out.println("AR = " + adjRecall);
                    System.out.println("OP = " + orientationPrecision);
                    System.out.println("OR = " + orientationRecall);
                    System.out.println("PTA = " + pta);

                    results[5 * numSubjects + 0][r] = adjPrecision;
                    results[5 * numSubjects + 1][r] = adjRecall;
                    results[5 * numSubjects + 2][r] = orientationPrecision;
                    results[5 * numSubjects + 3][r] = orientationRecall;
                    results[5 * numSubjects + 4][r] = pta;
                }
            }

            System.out.println(MatrixUtils.toString(results));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int truePositivesAdj(Graph gTrue, Graph g2) {
        int n = 0;

        g2 = GraphUtils.replaceNodes(g2, gTrue.getNodes());

        List<Node> nodes = gTrue.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (gTrue.isAdjacentTo(nodes.get(i), nodes.get(j)) && g2.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    n++;
                }
            }
        }

        return n;
    }

    private int truePositivesOrient(Graph gTrue, Graph g2) {
        int n = 0;

        g2 = GraphUtils.replaceNodes(g2, gTrue.getNodes());

        for (Edge edge : gTrue.getEdges()) {
            if (g2.containsEdge(edge)) n++;
        }

        return n;
    }

    public static int adjacenciesComplement(Graph graph1, Graph graph2) {
        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int n = 0;

        List<Node> nodes = graph1.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (graph1.isAdjacentTo(nodes.get(i), nodes.get(j)) && !graph2.isAdjacentTo(nodes.get(i), nodes.get(j))) {
                    n++;
                }
            }
        }

        return n;
    }

    public static int edgesComplement(Graph graph1, Graph graph2) {
        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        int n = 0;

        for (Edge edge : graph1.getEdges()) {
            if (!graph2.containsEdge(edge)) {
                n++;
            }
        }

        return n;
    }


    public void test9() {
//        Graph gTrue = new EdgeListGraph();
//        Graph g2 = new EdgeListGraph();
//
//        Node x1 = new GraphNode("X1");
//        Node x2 = new GraphNode("X2");
//        Node x3 = new GraphNode("X3");
//
//        gTrue.addNode(x1);
//        gTrue.addNode(x2);
//        gTrue.addNode(x3);
//
//        g2.addNode(x1);
//        g2.addNode(x2);
//        g2.addNode(x3);
//
//        gTrue.addDirectedEdge(x1, x2);
////        gTrue.addDirectedEdge(x1, x3);
//
//        g2.addDirectedEdge(x1, x2);
//        g2.addDirectedEdge(x3, x2);

        Graph gTrue = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));
        Graph g2 = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));

        g2 = GraphUtils.replaceNodes(g2, gTrue.getNodes());

        int adjFn = adjacenciesComplement(gTrue, g2);
        int adjFp = adjacenciesComplement(g2, gTrue);
        int arrowFn = edgesComplement(gTrue, g2);
        int arrowFp = edgesComplement(g2, gTrue);

        int truePosAdj = truePositivesAdj(gTrue, g2);
        int truePosOrient = truePositivesOrient(gTrue, g2);

        List<Node> nodes = g2.getNodes();

        Graph g2Restricted = new EdgeListGraph(nodes);

        for (int i2 = 0; i2 < nodes.size(); i2++) {
            for (int j2 = i2 + 1; j2 < nodes.size(); j2++) {
                for (Edge edge : g2.getEdges(nodes.get(i2), nodes.get(j2))) {
                    if (gTrue.isAdjacentTo(nodes.get(i2), nodes.get(j2))) {
                        g2Restricted.addEdge(edge);
                    }
                }
            }
        }

        double adjPrecision1 = truePosAdj / (double) (truePosAdj + adjFp);
        double adjRecall1 = truePosAdj / (double) (truePosAdj + adjFn);
        double orientationPrecision1 = truePosOrient / (double) (truePosOrient + arrowFp);
        double orientationRecall1 = truePosOrient / (double) (truePosOrient + arrowFn);

        double pta = truePosOrient / (double) (truePosAdj);

        System.out.println("AP = " + adjPrecision1);
        System.out.println("AR = " + adjRecall1);
        System.out.println("OP = " + orientationPrecision1);
        System.out.println("OR = " + orientationRecall1);

        System.out.println("PTA = " + pta);

    }

    private boolean between(double e1, double e2, double s) {
        if (e1 <= s && s <= e2) return true;
        else if (e1 >= s && s >= e2) return true;
        else return false;
    }

    private String f(double d1) {
        NumberFormat f = new DecimalFormat("0.000000");
        return f.format(d1);
    }

    public static Test suite() {
        return new TestSuite(TestStatUtils.class);
    }

    public void rtest21() {

        double threshold = 0.7;

        // Need a covariance matrix.
        DataReader reader = new DataReader();
        reader.setDelimiter(DelimiterType.WHITESPACE);

        File file = new File("/Users/josephramsey/Desktop/Russ_Data_Concat/1", "ds1_sub016_task001_nuisance_6mm.concat.txt.cov");
        ICovarianceMatrix cov = null;
        Map<Integer, String> labelMap = null;
        DataSet atlas = null;
        PrintStream out = null;

        try {
            cov = reader.parseCovariance(file);
            cov = new CorrelationMatrix(cov);
            labelMap = getLabelMap();
            DataSet mask = readMask();
            atlas = readAtlas(labelMap, mask);
//            final Map<String, Integer> regionSizes = getRegionSizes(labelMap, atlas);
//            List<List<Map<Edge, Integer>>> edgeCounts = getEdgeCounts(graphPaths, minNumEdges);

            out = new PrintStream(new FileOutputStream(new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/2013.09.02" +
                    "/correlations.at.distances.6mm", "threshold.1.concat." + threshold + ".txt")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<Coord> coords = parseCoords();

        NumberFormat nf = new DecimalFormat("0.000");

        for (int i = 0; i < 5054; i++) {
            int anchorIndex = i;
            List<Coord> sortedCoords = sortByDistanceFromAnchor(coords, coords.get(anchorIndex));

            int region = (int) atlas.getDouble(anchorIndex, 3);
            out.println("anchor = " + coords.get(anchorIndex) + "\t" + labelMap.get(region));

            for (int k = 0; k < sortedCoords.size(); k++) {
                double _cov = cov.getValue(anchorIndex, sortedCoords.get(k).getIndex());

                if (abs(_cov) > threshold) {
                    double distance = distance(coords.get(anchorIndex), coords.get(sortedCoords.get(k).getIndex()));
                    int _region = (int) atlas.getDouble(sortedCoords.get(k).getIndex(), 3);
                    out.println(sortedCoords.get(k) + "\t" + nf.format(distance) + "\t" +
                            (abs(_cov) < threshold ? 0.0 : nf.format(_cov)) + "\t" +
                            labelMap.get(_region)
                    );
                }
            }

            out.println("\n\n");
        }
    }

    private List<Coord> sortByDistanceFromAnchor(List<Coord> coords, final Coord anchor) {
        List<Coord> sortedCoords = new ArrayList<Coord>(coords);

        Collections.sort(sortedCoords, new Comparator<Coord>() {
            @Override
            public int compare(Coord o1, Coord o2) {
                return (int) signum(distance(anchor, o1) - distance(anchor, o2));
            }
        });
        return sortedCoords;
    }

    private double distance(Coord o1, Coord o2) {
        double sum = 0.0;

        sum += pow((double) o1.getX() - (double) o2.getX(), 2);
        sum += pow((double) o1.getY() - (double) o2.getY(), 2);
        sum += pow((double) o1.getZ() - (double) o2.getZ(), 2);

        return sqrt(sum);
    }

    private List<Coord> parseCoords() {
        List<Coord> coords = new ArrayList<Coord>();

        String filename = "src/test/resources/coords.copy.txt";
        int maxx = 0;
        int maxy = 0;
        int maxz = 0;

        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));

            for (int i = 1; i <= 5054; i++) {
                String line = br.readLine();
                String[] tokens = line.split(" ");
                int x = Integer.parseInt(tokens[0]);
                int y = Integer.parseInt(tokens[1]);
                int z = Integer.parseInt(tokens[2]);

                Coord coord = new Coord(i - 1, x, y, z);
                coords.add(coord);

                if (x > maxx) maxx = x;
                if (y > maxy) maxy = y;
                if (z > maxz) maxz = z;

//                System.out.println(x + "," + y + "," + z);

            }

            System.out.println("maxx = " + maxx);
            System.out.println("maxy = " + maxy);
            System.out.println("maxz = " + maxz);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return coords;
    }

    private class Coord {
        private int index;
        private int x;
        private int y;
        private int z;

        public Coord(int index, int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.index = index;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public String toString() {
            return "(" + x + "," + y + "," + z + ") " + index;
        }

        public int getIndex() {
            return index;
        }
    }

    private DataSet readMask() throws IOException {

        // Read in the mask and the atlas.
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        reader.setDelimiter(DelimiterType.WHITESPACE);
        DataSet mask = reader.parseTabular(new File("src/test/resources/coords2.txt"));

        mask.setNumberFormat(new DecimalFormat("0"));
        return mask;
    }

    private Map<String, Integer> getRegionSizes(Map<Integer, String> labelMap, DataSet atlas) {
        Map<String, Integer> regionsSizes = new HashMap<String, Integer>();
        int numRegions = 111;

        for (int i = 1; i <= numRegions; i++) {
            int count = 0;
            final int numMaskVoxels = 5054;

            for (int j = 0; j < numMaskVoxels; j++) {
                double v = atlas.getDouble(j, 3);
                if (v == i) count++;
            }

            regionsSizes.put(labelMap.get(i), count);
        }

        return regionsSizes;
    }

    private Map<Integer, String> getLabelMap() throws ParsingException, IOException {
        File file = new File("test_data/HarvardOxford-combo-labels.txt");

        Map<Integer, String> labelMap = new HashMap<Integer, String>();

        BufferedReader in = new BufferedReader(new FileReader(file));
        String line;

        while ((line = in.readLine()) != null) {
            String[] tokens = line.split("\t");

            int index = Integer.parseInt(tokens[0]);
            labelMap.put(index, tokens[1]);

        }

        return labelMap;
    }

    private DataSet readAtlas(Map<Integer, String> labelMap, DataSet mask) throws IOException {
        DataReader reader2 = new DataReader();
        reader2.setDelimiter(DelimiterType.COMMA);
        reader2.setVariablesSupplied(false);
        DataSet atlas = reader2.parseTabular(new File("src/test/resources/HarvardOxford-combo-maxprob-thr25-2mm.csv"));

        // Construct a map from variable names to variable labels.
        Map<String, String> variableLabels = new HashMap<String, String>();
        Map<String, Coord> coords = new HashMap<String, Coord>();

        VAR:
        for (int i = 0; i < mask.getNumRows(); i++) {
            int x = (int) mask.getDouble(i, 0);
            int y = (int) mask.getDouble(i, 1);
            int z = (int) mask.getDouble(i, 2);

//                System.out.println(x + " " + y + " " + z);

            for (int _i = 0; _i < atlas.getNumRows(); _i++) {
                int _x = (int) atlas.getDouble(_i, 0);
                int _y = (int) atlas.getDouble(_i, 1);
                int _z = (int) atlas.getDouble(_i, 2);
                int _region = (int) atlas.getDouble(_i, 3);

                if (x == _x && y == _y && z == _z) {
//                    System.out.println(x + "\t" + y + "\t" + z + "\t" + labelMap.get(_region));
                    variableLabels.put("X" + (i + 1), labelMap.get(_region));
                    coords.put("X" + (i + 1), new Coord(i, x, y, z));
                    continue VAR;
                }
            }

//            System.out.println(x + "\t" + y + "\t" + z + "\t" + "*");
            coords.put("X" + (i + 1), new Coord(i, x, y, z));
        }

        return atlas;
    }

    private List<List<Map<Edge, Integer>>> getEdgeCounts(List<List<String>> graphPaths, int minNumEdges)
            throws IOException, ParsingException {
        List<List<Map<Edge, Integer>>> edgeCounts = new ArrayList<List<Map<Edge, Integer>>>();

        Map<Integer, String> labelMap = getLabelMap();
        DataSet mask = readMask();

        // Read in the atlas (Text listing).
        DataSet atlas = readAtlas(labelMap, mask);

        Map<String, String> variableLabels = extractVariableLabels(labelMap, mask, atlas);

        for (List<String> list : graphPaths) {
            List<Map<Edge, Integer>> countsList = new ArrayList<Map<Edge, Integer>>();

            // Within a study...?
            for (String path : list) {
                System.out.println(path);
                Graph graph = GraphUtils.loadGraph(new File(path));
                Map<Edge, Integer> edgeCount = metaEdgeGraphCounts(labelMap, variableLabels, graph, minNumEdges);
                countsList.add(edgeCount);
            }

            edgeCounts.add(countsList);

            // Remove edges from metagraphs with low means and high variances--Mike's idea.
            if (true) {
                Set<Edge> allEdges = new HashSet<Edge>();

                for (Map<Edge, Integer> edgeCount : countsList) {
                    allEdges.addAll(edgeCount.keySet());
                }

                for (Edge edge : allEdges) {
                    double[] counts = new double[countsList.size()];
                    int i = -1;

                    for (Map<Edge, Integer> edgeCount : countsList) {
                        if (edgeCount.keySet().contains(edge)) {
                            counts[++i] = edgeCount.get(edge);
                        }
                    }

                    double mean = StatUtils.mean(counts);
                    double sd = StatUtils.sd(counts);

                    if (sd > mean) {
                        for (Map<Edge, Integer> edgeCount : countsList) {
                            edgeCount.remove(edge);
                        }
                    }
                }
            }
        }

        return edgeCounts;
    }

    private Map<String, String> extractVariableLabels(Map<Integer, String> labelMap, DataSet mask, DataSet atlas) {

        // Construct a map from variable names to variable labels.

        Map<String, String> variableLabels = new HashMap<String, String>();

        VAR:
        for (int i = 0; i < mask.getNumRows(); i++) {
            int x = (int) mask.getDouble(i, 0);
            int y = (int) mask.getDouble(i, 1);
            int z = (int) mask.getDouble(i, 2);

            String variableName = "X" + (i + 1);

            for (int _i = 0; _i < atlas.getNumRows(); _i++) {
                int _x = (int) atlas.getDouble(_i, 0);
                int _y = (int) atlas.getDouble(_i, 1);
                int _z = (int) atlas.getDouble(_i, 2);
                int _region = (int) atlas.getDouble(_i, 3);

                if (x == _x && y == _y && z == _z && _region != 0) {
                    String region = labelMap.get(_region);
                    variableLabels.put(variableName, region);
                    continue VAR;
                }
            }

            variableLabels.put(variableName, "*");
        }

        return variableLabels;
    }

    private Map<Edge, Integer> metaEdgeGraphCounts(Map<Integer, String> labelMap, Map<String, String> variableLabels,
                                                   Graph graph, int minNumEdges) {
        Map<Edge, Integer> edgeCounts = new HashMap<Edge, Integer>();

        Graph metaGraph = new EdgeListGraph();
        int numRegions = 111;

        for (int g = 1; g <= numRegions; g++) {
            metaGraph.addNode(new GraphNode(labelMap.get(g)));
        }

        for (Edge edge : graph.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            String name1 = node1.getName();
            String name2 = node2.getName();

            String label1 = variableLabels.get(name1);
            String label2 = variableLabels.get(name2);

            String _label1 = null, _label2 = null;

            for (int g = 1; g <= numRegions; g++) {
                if (labelMap.get(g).equals(label1)) {
                    _label1 = labelMap.get(g);
                    break;
                }
            }

            for (int g = 1; g <= numRegions; g++) {
                if (labelMap.get(g).equals(label2)) {
                    _label2 = labelMap.get(g);
                    break;
                }
            }

            if (_label1 != null && _label2 != null) {
                Node metaNode1 = metaGraph.getNode(_label1);
                Node metaNode2 = metaGraph.getNode(_label2);
                Edge metaEdge = Edges.undirectedEdge(metaNode1, metaNode2);

                if (metaNode1 != metaNode2) {
                    if (edgeCounts.get(metaEdge) == null) {
                        edgeCounts.put(metaEdge, 1);
                    } else {
                        edgeCounts.put(metaEdge, edgeCounts.get(metaEdge) + 1);
                    }
                }

                nodes.add(edge.getNode1());
                nodes.add(edge.getNode2());
            }
        }

        for (Edge metaEdge : edgeCounts.keySet()) {
            if (edgeCounts.get(metaEdge) >= minNumEdges) {
                metaGraph.addEdge(metaEdge);
            }
        }

        for (Edge edge : new HashSet<Edge>(edgeCounts.keySet())) {
            if (!metaGraph.containsEdge(edge)) {
                edgeCounts.remove(edge);
            }
        }

        System.out.println("Num metaedges = " + metaGraph.getNumEdges());

        return edgeCounts;
    }

    List<Node> nodes = new ArrayList<Node>();

    public void rtest30() {

        // Calculates the covariance matrices for all of the Russ_Data data sets.
        for (String dir : new String[]{"1", "3"}) { //"2", "3", "4", "5", "6", "7", "8"}) {
//            File _dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Russ_Data/" + dir);
            File _dir = new File("/home/jdramsey/data/Russ_Data/" + dir);

            File[] files = _dir.listFiles();

            for (File file : files) {
                if (file.getAbsolutePath().endsWith(".txt")) {
                    File out = new File(file.getAbsolutePath() + ".cov");

                    if (out.exists()) continue;

                    DataReader reader = new DataReader();
                    reader.setVariablesSupplied(false);
                    reader.setDelimiter(DelimiterType.WHITESPACE);

                    try {
                        DataSet dataSet = reader.parseTabular(file);

                        CovarianceMatrix cov = new CovarianceMatrix(dataSet);

                        DataWriter.writeCovMatrix(cov, new PrintWriter(out), new DecimalFormat("0.0000"));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void rtest31() {
        double alpha = 1e-20;

        // Runs PC on each of the covariance matrices in the Russ_Data directory.

        for (String dir : new String[]{"1", "2", "3", "4", "5", "6", "7", "8"}) {
            File _dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Russ_Data/" + dir);

            File[] files = _dir.listFiles();

            for (File file : files) {
                if (file.getAbsolutePath().endsWith(".cov")) {
                    DataReader reader = new DataReader();
                    reader.setVariablesSupplied(false);
                    reader.setDelimiter(DelimiterType.WHITESPACE);

                    try {
                        ICovarianceMatrix cov = reader.parseCovariance(file);

                        IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

                        Pc pc = new Pc(test);
                        Graph graph = pc.search();

                        File outFile = new File(file.getAbsolutePath() + ".xml");
                        PrintWriter out = new PrintWriter(outFile);
                        out.println(graph);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    break;
                }
            }

            break;
        }

    }

    public void rtest32() {

        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        reader.setDelimiter(DelimiterType.WHITESPACE);

        String dir = "1";
        File _dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Russ_Data/" + dir);
        File file = new File(_dir, "ds1_sub001_task001_run001_nuisance_6mm.txt.cov");
        double alpha = .05;

        try {
            ICovarianceMatrix cov = reader.parseCovariance(file);

            IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

            Pc pc = new Pc(test);
            Graph graph = pc.search();

            File outFile = new File(file.getAbsolutePath() + "." + alpha + ".xml");
            PrintWriter out = new PrintWriter(outFile);
            out.println(graph);
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void rtest33() {

        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        reader.setDelimiter(DelimiterType.WHITESPACE);

        String dir = "3";
        File _dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Russ_Data/" + dir);
        File file = new File(_dir, "ds3_sub001_task001_run001_nuisance_6mm.txt");

        try {
            DataSet dataSet = reader.parseTabular(file);
            setConstColsToMissing(dataSet);

            CovarianceMatrix cov = new CovarianceMatrix(dataSet);


            File out = new File(file.getAbsolutePath() + ".cov");

            DataWriter.writeCovMatrix(cov, new PrintWriter(out), new DecimalFormat("0.0000"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    {
//        int n = tetradMatrix.rows();
//
//        double[] avg = new double[tetradMatrix.columns()];
//
//        for (int j = 0; j < tetradMatrix.columns(); j++) {
//            double sum = 0.0;
//
//            for (int i = 0; i < tetradMatrix.rows(); i++) {
//                double entry = tetradMatrix.get(i, j);
//
//                if (!Double.isNaN(entry)) {
//                    sum += entry;
//                }
//            }
//
//
//            avg[j] = sum / n;
//        }
//
//        TetradMatrix cov = new TetradMatrix(tetradMatrix.columns(), tetradMatrix.columns());
//
//        for (int j1 = 0; j1 < tetradMatrix.columns(); j1++) {
//            for (int j2 = 0; j2 < tetradMatrix.columns(); j2++) {
//                double sum = 0.0;
//
//                for (int i = 0; i < tetradMatrix.rows(); i++) {
//                    double entry1 = tetradMatrix.get(i, j1);
//                    double entry2 = tetradMatrix.get(i, j2);
//
//                    if (!Double.isNaN(entry1) && !Double.isNaN(entry2)) {
//                        sum += (entry1 - avg[j1]) * (entry2 - avg[j2]);
//                    }
//                }
//
//                cov.set(j1, j2, sum / (n - 1));
//            }
//        }
//
//        return cov;
//
//    }

    public void rtest34() {
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        reader.setDelimiter(DelimiterType.WHITESPACE);

        String dir = "3";
        File _dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Russ_Data/" + dir);
        File file = new File(_dir, "ds3_sub001_task001_run001_nuisance_6mm.txt.cov");

        try {
            for (double alpha : new double[]{.05}) {//, 1e-5, 1e-4, 1e-3, 1e-2}) {

                ICovarianceMatrix cov = reader.parseCovariance(file);

                IndTestFisherZ test = new IndTestFisherZ(cov, alpha);

                FasStable fas = new FasStable(test);
                Graph graph = fas.search();

                File outFile = new File(file.getAbsolutePath() + ".stable." + alpha + ".xml");
                PrintWriter out = new PrintWriter(outFile);
                out.println(graph);
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // Runs orientation algorithms on graph files (.xml) given data sets (.txt).
    public void rtest35() {
        DataReader reader = new DataReader();
        reader.setVariablesSupplied(false);
        reader.setDelimiter(DelimiterType.WHITESPACE);

        String dir = "1";
        File _dir = new File("/Users/josephramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Russ_Data/" + dir);

        File[] files = _dir.listFiles();

        for (File graphFile : files) {
            if (graphFile.getAbsolutePath().endsWith(".xml")) {
                String path = graphFile.getAbsolutePath();

                if (path.endsWith(".oriented.xml")) continue;

                int txtIndex = path.indexOf(".txt");
                String path2 = path.substring(0, txtIndex + 4);
                System.out.println(path2);

                File dataFile = new File(path2);

                try {
                    DataSet dataSet = reader.parseTabular(dataFile);
                    Graph graph = GraphUtils.loadGraphTxt(graphFile);

                    List<DataSet> dataSets = Collections.singletonList(dataSet);

                    Lofs2.Rule rule = Lofs2.Rule.R4;

                    Lofs2 lofs2 = new Lofs2(graph, dataSets);
                    lofs2.setRule(rule);

                    if (rule == Lofs2.Rule.R4) {
                        lofs2.setEpsilon(0.1);
                        lofs2.setZeta(2.0);
                        lofs2.setOrientStrongerDirection(false);
                        lofs2.setAlpha(1.0);
                    }

                    Graph orientedGraph = lofs2.orient();

                    File outFile = new File(graphFile.getAbsolutePath() + "." + rule + ".oriented.xml");
                    PrintWriter out = new PrintWriter(outFile);
                    out.println(orientedGraph);
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void testPartials() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sim = new StandardizedSemIm(im);

        sim.setEdgeCoefficient(x1, x2, 0.98);
        sim.setEdgeCoefficient(x1, x3, 0.4749);
        sim.setEdgeCoefficient(x2, x3, 0.4505);

//        System.out.println(sim);

        DataSet data = sim.simulateData(1000, false);
        TetradMatrix _data = data.getDoubleData();

        System.out.println("rX1X2.X3 = " + parCorr(_data, new int[]{0, 1, 2}));
        System.out.println("rX1X3 = " + parCorr(_data, new int[]{0,2}));
        System.out.println("rX2X3 = " + parCorr(_data, new int[]{1,2}));
        System.out.println("rX2X3 * rX1X3 = " + parCorr(_data, new int[]{0,2}) * parCorr(_data, new int[]{1,2}));

        System.out.println();
        System.out.println("rX1X2.X4 = " + parCorr(_data, new int[]{0, 1, 3}));
        System.out.println("rX1X4 = " + parCorr(_data, new int[]{0,3}));
        System.out.println("rX2X4 = " + parCorr(_data, new int[]{1,3}));
        System.out.println("rX2X3 * rX1X3 = " + parCorr(_data, new int[]{0,3}) * parCorr(_data, new int[]{1,3}));
    }

    private double parCorr(TetradMatrix data, int[] indices) {
        data = DataUtils.centerData(data);
        TetradMatrix cov = data.transpose().times(data);

        TetradMatrix prec = cov.getSelection(indices, indices).inverse();
        double r = - prec.get(0, 1) / sqrt(prec.get(0, 0) * prec.get(1, 1));

        return r;
    }

    private void setConstColsToMissing(DataSet dataSet) {

        COLUMN:
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            double first = dataSet.getDouble(0, j);

            for (int k = 1; k < dataSet.getNumRows(); k++) {
                if (dataSet.getDouble(k, j) != first) {
                    continue COLUMN;
                }
            }

            for (int k = 0; k < dataSet.getNumRows(); k++) {
                dataSet.setDouble(k, j, Double.NaN);
            }
        }
    }

}


