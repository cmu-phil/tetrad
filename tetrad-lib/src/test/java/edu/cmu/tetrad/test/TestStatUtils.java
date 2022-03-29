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
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;


/**
 * Tests basic functionality of the statistical utilities.
 *
 * @author Joseph Ramsey
 */
public class TestStatUtils {

    /**
     * Tests that the unconditional correlations and covariances are correct,
     * at least for the unconditional tests.
     */
    @Test
    public void testConditionalCorrelation() {

        RandomUtil.getInstance().setSeed(30299533L);

        // Make sure the unconditional correlations and covariances are OK.
        final List<Node> nodes1 = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes1.add(new ContinuousVariable("X" + (i + 1)));
        }

        final Graph graph = new Dag(GraphUtils.randomGraph(nodes1, 0, 5,
                3, 3, 3, false));
        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);
        final DataSet dataSet = im.simulateData(1000, false);
        final double[] x = dataSet.getDoubleData().getColumn(0).toArray();
        final double[] y = dataSet.getDoubleData().getColumn(1).toArray();

        final double r1 = StatUtils.correlation(x, y);
        final double s1 = StatUtils.covariance(x, y);
        final double v1 = StatUtils.variance(x);
        final double sd1 = StatUtils.sd(x);

        final ICovarianceMatrix cov = new CovarianceMatrix(dataSet);
        final Matrix _cov = cov.getMatrix();

        final double r2 = StatUtils.partialCorrelation(_cov, 0, 1);
        final double s2 = StatUtils.partialCovariance(_cov, 0, 1);
        final double v2 = StatUtils.partialVariance(_cov, 0);
        final double sd2 = StatUtils.partialStandardDeviation(_cov, 0);

        assertEquals(r1, r2, .1);
        assertEquals(s1, s2, .1);
        assertEquals(v1, v2, .1);
        assertEquals(sd1, sd2, 0.1);
    }

    @Test
    public void testRankCorr() {
        final double[] a1 = {2, 2, 3};
        final double[] a2 = {2, 3, 4};

        final double r = StatUtils.rankCorrelation(a1, a2);
        assertEquals(0.87, r, 0.01);
    }

    @Test
    public void testChiSqCdf() {
        final ChiSquaredDistribution dist = new ChiSquaredDistribution(1);
        final double log = log(1000);
        final double p = 1.0 - dist.cumulativeProbability(log);
        assertEquals(0.008, p, 0.001);
    }

    @Test
    public void testSpecial() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int numCases = 1000;

        double x, y, z, w;
        final double[] _x = new double[numCases];
        final double[] _y = new double[numCases];
        final double[] _z = new double[numCases];
        final double[] _w = new double[numCases];

        for (int i = 0; i < numCases; i++) {
            x = RandomUtil.getInstance().nextDouble();
            y = RandomUtil.getInstance().nextDouble();
            z = RandomUtil.getInstance().nextDouble();

            if (x + y + z > 1) {
                i--;
                continue;
            }

            w = 1.0 - x - y - z;

            if (x * y > z * w) {
                _x[i] = x;
                _y[i] = y;
                _z[i] = z;
                _w[i] = w;
            }
        }

        assertEquals(0.17, StatUtils.mean(_x), 0.01);
        assertEquals(0.17, StatUtils.mean(_y), 0.01);
        assertEquals(0.09, StatUtils.mean(_z), 0.01);
        assertEquals(0.08, StatUtils.mean(_w), 0.01);

        assertEquals(0.04, StatUtils.variance(_x), 0.01);
        assertEquals(0.05, StatUtils.variance(_x), 0.01);
        assertEquals(0.05, StatUtils.variance(_x), 0.01);
        assertEquals(0.05, StatUtils.variance(_x), 0.01);
    }

    //    @Test
    public void testNongaussianSums() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int numTrials = 10;
        final int sampleSize = 1000;
        int count = 0;
        int failed = 0;

        for (int trial = 0; trial < numTrials; trial++) {
            final double d1 = RandomUtil.getInstance().nextUniform(.1, 2.0);
            final double d2 = RandomUtil.getInstance().nextUniform(.1, 2.0);

            final double c1 = 1.0; //RandomUtil.getInstance().nextUniform(-1, 1);
            final double c2 = 1.0; //RandomUtil.getInstance().nextUniform(-1, 1);

            final double[] col1 = new double[sampleSize];
            final double[] col2 = new double[sampleSize];
            final double[] sum = new double[sampleSize];


            final int dist1 = RandomUtil.getInstance().nextInt(3);
            final int dist2 = RandomUtil.getInstance().nextInt(3);
            final double orientation1 = RandomUtil.getInstance().nextDouble();
            final double orientation2 = RandomUtil.getInstance().nextDouble();

            for (int i = 0; i < sampleSize; i++) {

                switch (dist1) {
                    case 0:
                        final double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col1[i] = signum(v) * pow(abs(v), d1);
                        break;
                    case 1:
                        final double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col1[i] = signum(v1) * pow(abs(v1), d1);
                        break;
                    case 2:
                        final double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col1[i] = signum(v2) * pow(v2, d1);
                        if (orientation1 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                switch (dist2) {
                    case 0:
                        final double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col2[i] = signum(v) * pow(abs(v), d2);
                        break;
                    case 1:
                        final double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col2[i] = signum(v1) * pow(abs(v1), d2);
                        break;
                    case 2:
                        final double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col2[i] = signum(v2) * pow(v2, d2);
                        if (orientation2 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                // These have negative kurtosis
                sum[i] = c1 * col1[i] + c2 * col2[i];
            }

            final double a1 = new AndersonDarlingTest(col1).getASquaredStar();
            final double s = new AndersonDarlingTest(sum).getASquaredStar();

            if (!(s <= a1)) { //min(a1, a2))) {
                failed++;
            }

            count++;
        }

        final double percentFailed = failed / (double) count;
        assertEquals(0.2, percentFailed, 0.1);
    }

    //    @Test
    public void testNongaussianSums2() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int sampleSize = 1000;
        int count = 0;
        int failed = 0;

        final double bound = 2.0;
        final double step = 0.1;

        final int dist1 = RandomUtil.getInstance().nextInt(4);
        final int dist2 = RandomUtil.getInstance().nextInt(4);

        for (double d1 = -bound; d1 <= bound; d1 += step) {
            for (double d2 = -bound; d2 <= bound; d2 += step) {
                final double[] col1 = new double[sampleSize];
                final double[] col2 = new double[sampleSize];
                final double[] sum = new double[sampleSize];

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
                            break;
                        default:
                            throw new IllegalStateException();
                    }

                    // These have negative kurtosis
                    sum[i] = d1 * col1[i] + d2 * col2[i];
                }

                final double a1 = StatUtils.logCoshScore(col1);
                final double a2 = StatUtils.logCoshScore(col2);
                final double s = StatUtils.logCoshScore(sum);

                if (!(s <= max(a1, a2))) {
                    failed++;
                }

                count++;
            }
        }

        final double percentFailed = failed / (double) count;
        assertEquals(0.0106, percentFailed, 0.0001);
    }

    //    @Test
    public void testNongaussianSums3() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int n = 1000;
        int count = 0;
        int positive = 0;

        final double[] col1 = new double[n];
        final double[] col2 = new double[n];
        final double[] sum = new double[n];

        for (int k = 0; k < 10; k++) {
            final double d1 = RandomUtil.getInstance().nextUniform(-10, 10);
            final double d2 = RandomUtil.getInstance().nextUniform(-10, 10);

            final double e1 = RandomUtil.getInstance().nextUniform(0, 3);
            final double e2 = RandomUtil.getInstance().nextUniform(0, 3);

            for (int i = 0; i < n; i++) {
                col1[i] = pow(RandomUtil.getInstance().nextNormal(0, 1), e1);
                col2[i] = pow(RandomUtil.getInstance().nextNormal(0, 1), e2);
                sum[i] = d1 * col1[i] + d2 * col2[i];
            }

            final double a1 = new AndersonDarlingTest(col1).getASquared();
            final double a2 = new AndersonDarlingTest(col2).getASquared();
            final double s = new AndersonDarlingTest(sum).getASquared();

            if (!(s < max(a1, a2))) {
                positive++;
            }

            count++;
        }

        final double percentErrors = positive / (double) count;
        assertEquals(0.0, percentErrors, 0.01);
    }

    //    @Test
    public void testNongaussianSums4() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int n = 1000;
        int count = 0;
        int positive = 0;

        final double[] col1 = new double[n];
        final double[] col2 = new double[n];
        final double[] sum = new double[n];

        for (int k = 0; k < 10; k++) {
            final double f1 = RandomUtil.getInstance().nextUniform(0, 5);
            final double f2 = RandomUtil.getInstance().nextUniform(0, 5);
            final double f3 = RandomUtil.getInstance().nextUniform(0, 5);
            final double f4 = RandomUtil.getInstance().nextUniform(0, 5);
            final double f5 = RandomUtil.getInstance().nextUniform(0, 5);
            final double f6 = RandomUtil.getInstance().nextUniform(0, 5);

            final double d1 = RandomUtil.getInstance().nextUniform(-1, 1);
            final double d2 = RandomUtil.getInstance().nextUniform(-1, 1);

            for (int i = 0; i < n; i++) {
                col1[i] = RandomUtil.getInstance().nextBeta(2, 5) + f1;
                col1[i] += RandomUtil.getInstance().nextBeta(2, 5) + f2;
                col1[i] += RandomUtil.getInstance().nextBeta(2, 5) + f3;

                col2[i] = RandomUtil.getInstance().nextBeta(2, 5) + f4;
                col2[i] += RandomUtil.getInstance().nextBeta(2, 5) + f5;
                col2[i] += RandomUtil.getInstance().nextBeta(2, 5) + f6;

                sum[i] = d1 * col1[i] + d2 * col2[i];
            }

            final double a1 = new AndersonDarlingTest(col1).getASquared();
            final double a2 = new AndersonDarlingTest(col2).getASquared();
            final double s = new AndersonDarlingTest(sum).getASquared();

            if (!(s < 1.2 * max(a1, a2))) {
                positive++;
            }

            count++;
        }

        final double percentErrors = positive / (double) count;
        assertEquals(0.0, percentErrors, 0.01);
    }

    //    @Test
    public void testNongaussianSums5() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int numTrials = 10;
        final int sampleSize = 10;
        int count = 0;
        int failed = 0;

        for (int trial = 0; trial < numTrials; trial++) {
            final double d1 = RandomUtil.getInstance().nextUniform(.1, 2.0);

            final double c1 = RandomUtil.getInstance().nextUniform(-1, 1);
            final double c2 = RandomUtil.getInstance().nextUniform(-1, 1);
            final double c3 = RandomUtil.getInstance().nextUniform(-1, 1);

            final double[] col1 = new double[sampleSize];
            final double[] col2 = new double[sampleSize];
            final double[] col3 = new double[sampleSize];
            final double[] sum = new double[sampleSize];

            final int dist1 = RandomUtil.getInstance().nextInt(3);
            final double orientation1 = RandomUtil.getInstance().nextDouble();
            final double orientation2 = RandomUtil.getInstance().nextDouble();
            final double orientation3 = RandomUtil.getInstance().nextDouble();

            for (int i = 0; i < sampleSize; i++) {
                switch (dist1) {
                    case 0:
                        final double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col1[i] = signum(v) * pow(abs(v), d1);
                        break;
                    case 1:
                        final double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col1[i] = signum(v1) * pow(abs(v1), d1);
                        break;
                    case 2:
                        final double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col1[i] = signum(v2) * pow(v2, d1);
                        if (orientation1 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                switch (dist1) {
                    case 0:
                        final double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col2[i] = signum(v) * pow(abs(v), d1);
                        break;
                    case 1:
                        final double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col2[i] = signum(v1) * pow(abs(v1), d1);
                        break;
                    case 2:
                        final double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col2[i] = signum(v2) * pow(v2, d1);
                        if (orientation2 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                switch (dist1) {
                    case 0:
                        final double v = RandomUtil.getInstance().nextUniform(0, 1);
                        col3[i] = signum(v) * pow(abs(v), d1);
                        break;
                    case 1:
                        final double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        col3[i] = signum(v1) * pow(abs(v1), d1);
                        break;
                    case 2:
                        final double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        col3[i] = signum(v2) * pow(v2, d1);
                        if (orientation3 < 0.5) col1[i] = -col1[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }

                // These have negative kurtosis
                sum[i] = c1 * col1[i] + c2 * col2[i] + c3 * col3[i];
            }

            final double a1 = new AndersonDarlingTest(col1).getASquaredStar();
            final double a2 = new AndersonDarlingTest(col2).getASquaredStar();
            final double a3 = new AndersonDarlingTest(col3).getASquaredStar();
            final double s = new AndersonDarlingTest(sum).getASquaredStar();

            if (!(s <= min(min(a1, a2), a3))) {
                failed++;
            }

            count++;
        }

        final double percentFailed = failed / (double) count;


        // depending on OS the correct unit test result is different
        final String OS = System.getProperty("os.name").toLowerCase();
        System.out.println("OS is " + OS);
        double expectedPercentFailed = 0.6;
        if (OS.indexOf("win") >= 0) {
            expectedPercentFailed = 0.6;
        } else if (OS.indexOf("mac") >= 0) {
            expectedPercentFailed = 0.6;
        } else if (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0) {
            expectedPercentFailed = 0.7;
        } else if (OS.indexOf("sunos") >= 0) {
            expectedPercentFailed = 0.6;
        } else {
            assertFalse("Your OS is not supported for this unit test!!", true);

        }
        assertEquals(expectedPercentFailed, percentFailed, 0.01);


    }

    @Test
    public void testNongaussianSums6() {
        RandomUtil.getInstance().setSeed(3829483L);
        final int numTrials = 10;
        final int sampleSize = 1000;
        int count = 0;
        int failed = 0;

        for (int trial = 0; trial < numTrials; trial++) {
            final double c = RandomUtil.getInstance().nextUniform(.2, 2.0);

            double c1 = RandomUtil.getInstance().nextUniform(.5, 1.5);
            double c2 = RandomUtil.getInstance().nextUniform(.5, 1.5);
            double c3 = RandomUtil.getInstance().nextUniform(.5, 1.5);

            if (RandomUtil.getInstance().nextDouble() > 0.5) c1 = -c1;
            if (RandomUtil.getInstance().nextDouble() > 0.5) c2 = -c2;
            if (RandomUtil.getInstance().nextDouble() > 0.5) c3 = -c3;

            final double[] ex = new double[sampleSize];
            final double[] ey = new double[sampleSize];
            final double[] ez = new double[sampleSize];

            final int dist1 = RandomUtil.getInstance().nextInt(3);
            final double orientation = RandomUtil.getInstance().nextDouble();

            for (int i = 0; i < sampleSize; i++) {
                switch (dist1) {
                    case 0:
                        double v = RandomUtil.getInstance().nextUniform(0, 1);
                        ex[i] = signum(v) * pow(abs(v), c);

                        v = RandomUtil.getInstance().nextUniform(0, 1);
                        ey[i] = signum(v) * pow(abs(v), c);

                        v = RandomUtil.getInstance().nextUniform(0, 1);
                        ez[i] = signum(v) * pow(abs(v), c);
                        break;
                    case 1:
                        double v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        ex[i] = signum(v1) * pow(abs(v1), c);

                        v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        ey[i] = signum(v1) * pow(abs(v1), c);

                        v1 = RandomUtil.getInstance().nextNormal(0, 1);
                        ez[i] = signum(v1) * pow(abs(v1), c);
                        break;
                    case 2:
                        double v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        ex[i] = signum(v2) * pow(v2, c);
                        if (orientation < 0.5) ex[i] = -ex[i];

                        v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        ey[i] = signum(v2) * pow(v2, c);
                        if (orientation < 0.5) ey[i] = -ey[i];

                        v2 = RandomUtil.getInstance().nextBeta(2, 5);
                        ez[i] = signum(v2) * pow(v2, c);
                        if (orientation < 0.5) ez[i] = -ez[i];
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }

            final double[] sumx = new double[sampleSize];
            final double[] sumy = new double[sampleSize];
            final double[] sumz = new double[sampleSize];

            for (int i = 0; i < sampleSize; i++) {
                sumx[i] = ex[i];
                sumy[i] = c1 * sumx[i] + ey[i];
                sumz[i] = c2 * sumx[i] + c3 * sumy[i] + ez[i];
            }

            final double a3 = new AndersonDarlingTest(sumz).getASquaredStar();

            final double aey = new AndersonDarlingTest(ey).getASquaredStar();

            if (!(aey > a3)) {
                failed++;
            }

            count++;
        }

        final double percentFailed = failed / (double) count;
        assertEquals(0.0, percentFailed, 0.01);
    }

    @Test
    public void testLogCoshExp() {
        RandomUtil.getInstance().setSeed(3848293L);
        double sum = 0.0;
        final int numSamples = 100;

        for (int i = 0; i < numSamples; i++) {
            final double randNorm = RandomUtil.getInstance().nextNormal(0, 1);

            final double a = log(cosh(randNorm));

            sum += a;
        }

        final double b = sum / numSamples;

        assertEquals(.41, b, 0.01);
    }

    @Test
    public void testMaxEnt() {
        final double[] x = {1, 2, 3, 4, 5};

        final double maxEnt = StatUtils.maxEntApprox(x);
        assertEquals(1.75, maxEnt, 0.01);
    }

    public void test2() {
        final double d = .06;
        double sum = 0.0;

        for (int n = 1; n <= 1000; n++) {
//            System.out.println(n * d);
            sum += n * d;
        }

        System.out.println(sum / (60 * 60));
    }

    //    @Test
    public void test3() {
        int count = 0;
        final int total = 100000;

        for (int i = 0; i < total; i++) {
            final double v1 = RandomUtil.getInstance().nextUniform(0, 100);
            final double v2 = RandomUtil.getInstance().nextUniform(0, 100);
            final double w1 = RandomUtil.getInstance().nextUniform(0, 1);
            final double w2 = 1.0 - w1;
            final double m1 = RandomUtil.getInstance().nextUniform(-5.0, 5.0);
            final double m2 = RandomUtil.getInstance().nextUniform(-5.0, 5.0);

            final double left1 = 1.0 / v1;
            final double left2 = 1.0 / v2;
            final double m = (left1 + left2) / 2.0;

            final double denRight = w1 * v1 + w2 * v2 + w1 * (m1 - m) * (m1 - m) + w1 * (m2 - m) * (m2 - m);

            final double right = 1.0 / denRight;

            final boolean holds = left1 + left2 > right;

            if (holds) count++;

//            System.out.println(
            //x);// + " v1 = " + v1  + " v2 = " + v2  + " m1 = " + m1  + " m2 = " + m2);
        }

        System.out.println(count);
    }

    private String f(final double d1) {
        final NumberFormat f = new DecimalFormat("0.000000");
        return f.format(d1);
    }
}


