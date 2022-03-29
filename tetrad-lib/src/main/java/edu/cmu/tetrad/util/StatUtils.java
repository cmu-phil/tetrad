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

package edu.cmu.tetrad.util;

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.*;

import static java.lang.Double.NaN;
import static java.lang.Math.*;


/**
 * Contains a number of basic statistical functions. Most methods are overloaded
 * for either long or double arrays. </p> </p> NOTE: </p> Some methods in this
 * class have been adapted from class DStat written by Michael Fanelli, and the
 * routines have been included here by permission. The methods which were
 * adapted are: <ul> <li>gamma <li>internalGamma <li>beta <li>igamma <li>erf
 * <li>poisson <li>chidist <li>contTable1 </ul> </p> These methods are protected
 * under copyright by the author. Here is the text of his copyright notice for
 * DSTAT.java: </p> "Copyright 1997 by Michael Fanelli. All Rights Reserved.
 * Unlimited use of this beta code granted for non-commercial use only subject
 * to the the expiration date. Commercial (for profit) use requires written
 * permission."
 *
 * @author Joseph Ramsey
 */
public final class StatUtils {
    private static final double logCoshExp = StatUtils.logCoshExp();

    /**
     * @param array a long array.
     * @return the mean of the values in this array.
     */
    public static double mean(final long[] array) {
        return StatUtils.mean(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the mean of the values in this array.
     */
    public static double mean(final double[] array) {
        return StatUtils.mean(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the mean of the first N values in this array.
     */
    public static double mean(final long[] array, final int N) {
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < N; i++) {
            if (!Double.isNaN(array[i])) {
                sum += array[i];
                count++;
            }
        }

        return sum / (double) count;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the mean of the first N values in this array.
     */
    public static double mean(final double[] array, final int N) {
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < N; i++) {
            if (!Double.isNaN(array[i])) {
                sum += array[i];
                count++;
            }
        }

        return sum / (double) count;
    }

    /**
     * @param data a column vector.
     * @param N    the number of values of array which should be considered.
     * @return the mean of the first N values in this array.
     */
    public static double mean(final Vector data, final int N) {
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < N; i++) {
            if (!Double.isNaN(data.get(i))) {
                sum += data.get(i);
                count++;
            }
        }

        return sum / (double) count;
    }

    /**
     * @param array a long array.
     * @return the median of the values in this array.
     */
    public static double median(final long[] array) {
        return StatUtils.median(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the median of the values in this array.
     */
    public static double median(final double[] array) {
        return StatUtils.median(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the median of the first N values in this array.
     */
    public static long median(final long[] array, final int N) {

        final long[] a = new long[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Long.MAX_VALUE;

        long v, t;
        int i, j, l = 0;
        int r = N - 1;
        final int k1 = r / 2;
        final int k2 = r - k1;

        while (r > l) {
            v = a[l];
            i = l;
            j = r + 1;

            for (; ; ) {
                while (a[++i] < v) {
                }
                while (a[--j] > v) {
                }

                if (i >= j) {
                    break;
                }

                t = a[i];
                a[i] = a[j];
                a[j] = t;
            }

            t = a[j];
            a[j] = a[l];
            a[l] = t;

            if (j <= k1) {
                l = j + 1;
            }

            if (j >= k2) {
                r = j - 1;
            }
        }

        return (a[k1] + a[k2]) / 2;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the median of the first N values in this array.
     */
    public static double median(final double[] array, final int N) {

        final double[] a = new double[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Double.POSITIVE_INFINITY;

        double v, t;
        int i, j, l = 0;
        int r = N - 1;
        final int k1 = r / 2;
        final int k2 = r - k1;

        while (r > l) {
            v = a[l];
            i = l;
            j = r + 1;

            for (; ; ) {
                while (a[++i] < v) {
                }
                while (a[--j] > v) {
                }

                if (i >= j) {
                    break;
                }

                t = a[i];
                a[i] = a[j];
                a[j] = t;
            }

            t = a[j];
            a[j] = a[l];
            a[l] = t;

            if (j <= k1) {
                l = j + 1;
            }

            if (j >= k2) {
                r = j - 1;
            }
        }

        return (a[k1] + a[k2]) / 2;
    }

    /**
     * @param array          a long array.
     * @param quartileNumber 1, 2, or 3.
     * @return the requested quartile of the values in this array.
     */
    public static double quartile(final long[] array, final int quartileNumber) {
        return StatUtils.quartile(array, array.length, quartileNumber);
    }

    /**
     * @param array          a double array.
     * @param quartileNumber 1, 2, or 3.
     * @return the requested quartile of the values in this array.
     */
    public static double quartile(final double[] array, final int quartileNumber) {
        return StatUtils.quartile(array, array.length, quartileNumber);
    }

    /**
     * @param array          a long array.
     * @param N              the number of values of array which should be
     *                       considered.
     * @param quartileNumber 1, 2, or 3.
     * @return the requested quartile of the first N values in this array.
     */
    public static double quartile(final long[] array, final int N, final int quartileNumber) {

        if ((quartileNumber < 1) || (quartileNumber > 3)) {
            throw new IllegalArgumentException("StatUtils.quartile:  " +
                    "Quartile number must be 1, 2, or 3.");
        }

        final long[] a = new long[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Long.MAX_VALUE;

        long v, t;
        int i, j, l = 0;
        int r = N - 1;

        // find the two indexes k1 and k2 (possibly equal) which need
        // to be interpolated to get the quartile, being careful to
        // zero-index.
        final double doubleIndex = (quartileNumber / 4.0) * (N + 1.0) - 1;
        final double ratio = doubleIndex - (int) (doubleIndex);
        final int k1 = (int) Math.floor(doubleIndex);
        final int k2 = (int) Math.ceil(doubleIndex);

        // partially sort array a[] to find k1 and k2
        while (r > l) {
            v = a[l];
            i = l;
            j = r + 1;

            for (; ; ) {
                while (a[++i] < v) {
                }

                while (a[--j] > v) {
                }

                if (i >= j) {
                    break;
                }

                t = a[i];
                a[i] = a[j];
                a[j] = t;
            }

            t = a[j];
            a[j] = a[l];
            a[l] = t;

            if (j <= k1) {
                l = j + 1;
            }

            if (j >= k2) {
                r = j - 1;
            }
        }

        // return the interpolated value.
        return (a[k1] + ratio * (a[k2] - a[k1]));
    }

    /**
     * @param array          a double array.
     * @param N              the number of values of array which should be
     *                       considered.
     * @param quartileNumber 1, 2, or 3.
     * @return the requested quartile of the first N values in this array.
     */
    public static double quartile(final double[] array, final int N, final int quartileNumber) {

        if ((quartileNumber < 1) || (quartileNumber > 3)) {
            throw new IllegalArgumentException("StatUtils.quartile:  " +
                    "Quartile number must be 1, 2, or 3.");
        }

        final double[] a = new double[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Double.POSITIVE_INFINITY;

        double v, t;
        int i, j, l = 0;
        int r = N - 1;

        // find the two indexes k1 and k2 (possibly equal) which need
        // to be interpolated to get the quartile, being careful to
        // zero-index.  Also find interpolation ratio.
        final double doubleIndex = (quartileNumber / 4.0) * (N + 1.0) - 1;
        final double ratio = doubleIndex - (int) (doubleIndex);
        final int k1 = (int) Math.floor(doubleIndex);
        final int k2 = (int) Math.ceil(doubleIndex);

        // partially sort array a[] to find k1 and k2
        while (r > l) {
            v = a[l];
            i = l;
            j = r + 1;

            for (; ; ) {
                while (a[++i] < v) {
                }
                while (a[--j] > v) {
                }

                if (i >= j) {
                    break;
                }

                t = a[i];
                a[i] = a[j];
                a[j] = t;
            }

            t = a[j];
            a[j] = a[l];
            a[l] = t;

            if (j <= k1) {
                l = j + 1;
            }

            if (j >= k2) {
                r = j - 1;
            }
        }

        // return the interpolated value.
        return (a[k1] + ratio * (a[k2] - a[k1]));
    }

    /**
     * @param array a long array.
     * @return the minimum of the values in this array.
     */
    public static double min(final long[] array) {
        return StatUtils.min(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the minimum of the values in this array.
     */
    public static double min(final double[] array) {
        return StatUtils.min(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the minimum of the first N values in this array.
     */
    public static double min(final long[] array, final int N) {

        double min = array[0];

        for (int i = 1; i < N; i++) {
            if (array[i] < min) {
                min = array[i];
            }
        }

        return min;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the minimum of the first N values in this array.
     */
    public static double min(final double[] array, final int N) {

        double min = array[0];

        for (int i = 1; i < N; i++) {
            if (array[i] < min) {
                min = array[i];
            }
        }

        return min;
    }

    /**
     * @param array a long array.
     * @return the maximum of the values in this array.
     */
    public static double max(final long[] array) {
        return StatUtils.max(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the maximum of the values in this array.
     */
    public static double max(final double[] array) {
        return StatUtils.max(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the maximum of the first N values in this array.
     */
    public static double max(final long[] array, final int N) {

        double max = array[0];

        for (int i = 0; i < N; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }

        return max;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the maximum of the first N values in this array.
     */
    public static double max(final double[] array, final int N) {

        double max = array[0];

        for (int i = 0; i < N; i++) {
            if (array[i] > max) {
                max = array[i];
            }
        }

        return max;
    }

    /**
     * @param array a long array.
     * @return the range of the values in this array.
     */
    public static double range(final long[] array) {
        return (StatUtils.max(array, array.length) - StatUtils.min(array, array.length));
    }

    /**
     * @param array a double array.
     * @return the range of the values in this array.
     */
    public static double range(final double[] array) {
        return (StatUtils.max(array, array.length) - StatUtils.min(array, array.length));
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the range of the first N values in this array.
     */
    public static double range(final long[] array, final int N) {
        return (StatUtils.max(array, N) - StatUtils.min(array, N));
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the range of the first N values in this array.
     */
    public static double range(final double[] array, final int N) {
        return (StatUtils.max(array, N) - StatUtils.min(array, N));
    }

    /**
     * @param array a long array.
     * @return the length of this array.
     */
    public static int N(final long[] array) {
        return array.length;
    }

    /**
     * @param array a double array.
     * @return the length of this array.
     */
    public static int N(final double[] array) {
        return array.length;
    }

    /**
     * @param array a long array.
     * @return the sum of the squared differences from the mean in array.
     */
    public static double ssx(final long[] array) {
        return StatUtils.ssx(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the sum of the squared differences from the mean in array.
     */
    public static double ssx(final double[] array) {
        return StatUtils.ssx(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the sum of the squared differences from the mean of the first N
     * values in array.
     */
    public static double ssx(final long[] array, final int N) {

        int i;
        double difference;
        final double meanValue = StatUtils.mean(array, N);
        double sum = 0.0;

        for (i = 0; i < N; i++) {
            difference = array[i] - meanValue;
            sum += difference * difference;
        }

        return sum;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the sum of the squared differences from the mean of the first N
     * values in array.
     */
    public static double ssx(final double[] array, final int N) {

        int i;
        double difference;
        final double meanValue = StatUtils.mean(array, N);
        double sum = 0.0;

        for (i = 0; i < N; i++) {
            difference = array[i] - meanValue;
            sum += difference * difference;
        }

        return sum;
    }

    /**
     * @param array1 a long array.
     * @param array2 a long array, same length as array1.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for array1 and array2..
     */
    public static double sxy(final long[] array1, final long[] array2) {

        final int N1 = array1.length;
        final int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "StatUtils.SXY: Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return StatUtils.sxy(array1, array2, N1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a double array, same length as array1.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for array1 and array2..
     */
    public static double sxy(final double[] array1, final double[] array2) {

        final int N1 = array1.length;
        final int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "StatUtils.SXY: Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return StatUtils.sxy(array1, array2, N1);
    }

    /**
     * @param array1 a long array.
     * @param array2 a long array.
     * @param N      the number of values of array which should be considered.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for the first N values in array1 and
     * array2..
     */
    public static double sxy(final long[] array1, final long[] array2, final int N) {

        int i;
        double sum = 0.0;
        final double meanX = StatUtils.mean(array1, N);
        final double meanY = StatUtils.mean(array2, N);

        for (i = 0; i < N; i++) {
            sum += (array1[i] - meanX) * (array2[i] - meanY);
        }

        return sum;
    }

    /**
     * @param array1 a double array.
     * @param array2 a double array.
     * @param N      the number of values of array which should be considered.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for the first N values in array1 and
     * array2..
     */
    public static double sxy(final double[] array1, final double[] array2, final int N) {
        double sum = 0.0;
        final double meanX = StatUtils.mean(array1, N);
        final double meanY = StatUtils.mean(array2, N);

        for (int i = 0; i < N; i++) {
            sum += (array1[i] - meanX) * (array2[i] - meanY);
        }

        return sum;
    }

    /**
     * @param data1 a column vector of doubles.
     * @param data2 a column vector of doubles.
     * @param N     the number of values of array which should be considered.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for the first N values in array1 and
     * array2..
     */
    public static double sxy(final Vector data1, final Vector data2, final int N) {
        double sum = 0.0;
        final double meanX = StatUtils.mean(data1, N);
        final double meanY = StatUtils.mean(data2, N);

        for (int i = 0; i < N; i++) {
            sum += (data1.get(i) - meanX) * (data2.get(i) - meanY);
        }

        return sum;
    }

    /**
     * @param array a long array.
     * @return the variance of the values in array.
     */
    public static double variance(final long[] array) {
        return StatUtils.variance(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the variance of the values in array.
     */
    public static double variance(final double[] array) {
        return StatUtils.variance(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the variance of the first N values in array.
     */
    public static double variance(final long[] array, final int N) {
        return StatUtils.ssx(array, N) / (N - 1);
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the variance of the first N values in array.
     */
    public static double variance(final double[] array, final int N) {
        return StatUtils.ssx(array, N) / (N - 1);
    }

    /**
     * @param array a long array.
     * @return the standard deviation of the values in array.
     */
    public static double sd(final long[] array) {
        return StatUtils.sd(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the standard deviation of the values in array.
     */
    public static double sd(final double[] array) {
        return StatUtils.sd(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the standard deviation of the first N values in array.
     */
    public static double sd(final long[] array, final int N) {
        return Math.pow(StatUtils.ssx(array, N) / (N - 1), .5);
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the standard deviation of the first N values in array.
     */
    public static double sd(final double[] array, final int N) {
        return Math.pow(StatUtils.ssx(array, N) / (N - 1), .5);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array (same length as array1).
     * @return the covariance of the values in array.
     */
    public static double covariance(final long[] array1, final long[] array2) {

        final int N1 = array1.length;
        final int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return StatUtils.covariance(array1, array2, N1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array (same length as array1).
     * @return the covariance of the values in array.
     */
    public static double covariance(final double[] array1, final double[] array2) {
        final int N1 = array1.length;
        final int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return StatUtils.covariance(array1, array2, N1);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array.
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the covariance of the first N values in array1 and array2.
     */
    public static double covariance(final long[] array1, final long[] array2, final int N) {
        return StatUtils.sxy(array1, array2, N) / (N - 1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array (same length as array1).
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the covariance of the first N values in array1 and array2.
     */
    public static double covariance(final double[] array1, final double[] array2, final int N) {
        return StatUtils.sxy(array1, array2, N) / (N - 1);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array (same length as array1).
     * @return the Pearson's correlation of the values in array1 and array2.
     */
    public static double correlation(final long[] array1, final long[] array2) {

        final int N1 = array1.length;
        final int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return StatUtils.correlation(array1, array2, N1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array (same length as array1).
     * @return the Pearson's correlation of the values in array1 and array2.
     */
    public static double correlation(final double[] array1, final double[] array2) {

        final int N1 = array1.length;
        final int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return StatUtils.correlation(array1, array2, N1);
    }

    public static double correlation(final Vector data1, final Vector data2) {
        final int N = data1.size();
        final double covXY = StatUtils.sxy(data1, data2, N);
        final double covXX = StatUtils.sxy(data1, data1, N);
        final double covYY = StatUtils.sxy(data2, data2, N);
        return (covXY / (Math.sqrt(covXX) * Math.sqrt(covYY)));
    }

    public static short compressedCorrelation(final Vector data1, final Vector data2) {
        return (short) (StatUtils.correlation(data1, data2) * 10000);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array.
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the Pearson's correlation of the first N values in array1 and
     * array2.
     */
    public static double correlation(final long[] array1, final long[] array2, final int N) {
        final double covXY = StatUtils.sxy(array1, array2, N);
        final double covXX = StatUtils.sxy(array1, array1, N);
        final double covYY = StatUtils.sxy(array2, array2, N);
        return (covXY / (Math.pow(covXX, .5) * Math.pow(covYY, .5)));
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array.
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the Pearson correlation of the first N values in array1 and
     * array2.
     */
    public static double correlation(final double[] array1, final double[] array2, final int N) {
//        array1 = DataUtils.center(array1);
//        array2 = DataUtils.center(array2);

        final double covXY = StatUtils.sxy(array1, array2, N);
        final double covXX = StatUtils.sxy(array1, array1, N);
        final double covYY = StatUtils.sxy(array2, array2, N);
        return (covXY / (Math.sqrt(covXX) * Math.sqrt(covYY)));
    }

    public static double rankCorrelation(final double[] arr1, final double[] arr2) {
        if (arr1.length != arr2.length) {
            throw new IllegalArgumentException("Arrays not the same length.");
        }

        final double[] ranks1 = StatUtils.getRanks(arr1);
        final double[] ranks2 = StatUtils.getRanks(arr2);

        return StatUtils.correlation(ranks1, ranks2);
    }

    public static double kendallsTau(final double[] x, final double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays not the same length.");
        }

        int numerator = 0;
        final int N = x.length;

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                numerator += signum(x[i] - x[j]) * signum(y[i] - y[j]);
            }
        }

        return numerator / (0.5 * N * (N - 1));
    }

    public static double[] getRanks(final double[] arr) {
        final double[] arr2 = new double[arr.length];
        System.arraycopy(arr, 0, arr2, 0, arr.length);
        Arrays.sort(arr2);

        final double[] ranks = new double[arr.length];

        for (int i = 0; i < arr.length; i++) {
            double sum = 0;
            int n = 0;

            for (int j = 0; j < arr2.length; j++) {
                if (arr2[j] == arr[i]) {
                    sum += j + 1;
                    n++;
                }
            }

            ranks[i] = sum / (double) n;
        }

        return ranks;
    }

    /**
     * @param array a long array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is unknown.
     */
    public static double sSquare(final long[] array) {
        return StatUtils.sSquare(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is unknown.
     */
    public static double sSquare(final double[] array) {
        return StatUtils.ssx(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is unknown.
     */
    public static double sSquare(final long[] array, final int N) {
        return StatUtils.ssx(array, N) / (N - 1);
    }

    /**
     * @param array a double array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is unknown.
     */
    public static double sSquare(final double[] array, final int N) {
        return StatUtils.ssx(array, N) / (N - 1);
    }

    /**
     * @param array a long array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is known.
     */
    public static double varHat(final long[] array) {
        return StatUtils.varHat(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is known.
     */
    public static double varHat(final double[] array) {
        return StatUtils.varHat(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is known.
     */
    public static double varHat(final long[] array, final int N) {
        double sum = 0;
        double difference;
        final double meanX = StatUtils.mean(array, N);

        for (int i = 0; i < N; i++) {
            difference = array[i] - meanX;
            sum += difference * difference;
        }

        return sum / (N - 1);
    }

    /**
     * @param array a double array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is known.
     */
    public static double varHat(final double[] array, final int N) {
        double sum = 0.;
        double difference;
        final double meanX = StatUtils.mean(array, N);

        for (int i = 0; i < N; i++) {
            difference = array[i] - meanX;
            sum += difference * difference;
        }

        return sum / (N - 1);
    }

    /**
     * @param array a long array.
     * @return the unbaised estimate of the mean of the distribution of the
     * values in array.
     */
    public static double mu(final long[] array) {
        return StatUtils.mean(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the unbaised estimate of the mean of the distribution of the
     * values in array.
     */
    public static double mu(final double[] array) {
        return StatUtils.mean(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the mean of the distribution of the
     * first N values in array.
     */
    public static double mu(final long[] array, final int N) {
        return StatUtils.mean(array, N);
    }

    /**
     * @param array a double array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the mean of the distribution of the
     * first N values in array.
     */
    public static double mu(final double[] array, final int N) {
        return StatUtils.mean(array, N);
    }

    /**
     * @param array a long array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the values in array.
     */
    public static double muHat(final long[] array) {
        return StatUtils.muHat(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the values in array.
     */
    public static double muHat(final double[] array) {
        return StatUtils.muHat(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the first N values in array.
     */
    public static double muHat(final long[] array, final int N) {
        return StatUtils.mean(array, N);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the first N values in array.
     */
    public static double muHat(final double[] array, final int N) {
        return StatUtils.mean(array, N);
    }

    /**
     * @param array a long array.
     * @return the average deviation of the values in array.
     */
    public static double averageDeviation(final long[] array) {
        return StatUtils.averageDeviation(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the average deviation of the values in array.
     */
    public static double averageDeviation(final double[] array) {
        return StatUtils.averageDeviation(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the average deviation of the first N values in array.
     */
    public static double averageDeviation(final long[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        double adev = 0.0;

        for (int j = 0; j < N; j++) {
            adev += (Math.abs(array[j] - mean));
        }

        adev /= N;

        return adev;
    }

    /**
     * @param array a double array.
     * @param N     the number of values to be considered in array.
     * @return the average deviation of the first N values in array.
     */
    public static double averageDeviation(final double[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        double adev = 0.0;

        for (int j = 0; j < N; j++) {
            adev += (Math.abs(array[j] - mean));
        }

        adev /= N;

        return adev;
    }

    /**
     * @param array a long array.
     * @return the skew of the values in array.
     */
    public static double skewness(final long[] array) {
        return StatUtils.skewness(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the skew of the values in array.
     */
    public static double skewness(final double[] array) {
//        array = removeNaN(array);
        return StatUtils.skewness(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the skew of the first N values in array.
     */
    public static double skewness(final long[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        double secondMoment = 0.0; // StatUtils.variance(array, N);
        double thirdMoment = 0.0;

        for (int j = 0; j < N; j++) {
            final double s = array[j] - mean;
            secondMoment += s * s;
            thirdMoment += s * s * s;
        }

        final double ess = secondMoment / (N - 1);
        final double esss = thirdMoment / (N);

        if (secondMoment == 0) {
            throw new ArithmeticException("StatUtils.skew:  There is no skew " +
                    "when the variance is zero.");
        }

        return esss / Math.pow(ess, 1.5);
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the skew of the first N values in array.
     */
    public static double skewness(final double[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        double secondMoment = 0.0;
        double thirdMoment = 0.0;

        for (int j = 0; j < N; j++) {
            final double s = array[j] - mean;
            secondMoment += s * s;
            thirdMoment += s * s * s;
        }

        final double ess = secondMoment / N;
        final double esss = thirdMoment / N;

        if (secondMoment == 0) {
            throw new ArithmeticException("StatUtils.skew:  There is no skew " +
                    "when the variance is zero.");
        }

        return esss / Math.pow(ess, 1.5);
    }

    public static double[] removeNaN(final double[] x1) {
        int i;

        for (i = 0; i < x1.length; i++) {
            if (Double.isNaN(x1[i])) {
                break;
            }
        }

        i = i > x1.length ? x1.length : i;

        return Arrays.copyOf(x1, i);
    }

    /**
     * @param array a long array.
     * @return the kurtosis of the values in array.
     */
    public static double kurtosis(final long[] array) {
        return StatUtils.kurtosis(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the curtosis of the values in array.
     */
    public static double kurtosis(final double[] array) {
        return StatUtils.kurtosis(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the curtosis of the first N values in array.
     */
    public static double kurtosis(final long[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        final double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            final double s = array[j] - mean;
            kurt += s * s * s * s;
        }

        if (variance == 0) {
            throw new ArithmeticException(
                    "Kurtosis is undefined when variance is zero.");
        }

        kurt = kurt / N;

        kurt = kurt / (variance * variance) - 3.0;

        return kurt;
    }

    public static double standardizedFifthMoment(final double[] array) {
        return StatUtils.standardizedFifthMoment(array, array.length);
    }

    public static double standardizedFifthMoment(final double[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        final double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            final double s = array[j] - mean;
            kurt += s * s * s * s * s;
        }

        if (variance == 0) {
            throw new ArithmeticException(
                    "Kurtosis is undefined when variance is zero.");
        }

        kurt = (kurt / (N * Math.pow(variance, 5 / 2.)));

        return kurt;
    }

    public static double standardizedSixthMoment(final double[] array) {
        return StatUtils.standardizedFifthMoment(array, array.length);
    }

    public static double standardizedSixthMoment(final double[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        final double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            final double s = array[j] - mean;
            kurt += s * s * s * s * s * s;
        }

        if (variance == 0) {
            throw new ArithmeticException(
                    "Kurtosis is undefined when variance is zero.");
        }

        kurt = (kurt / (N * Math.pow(variance, 6 / 2.)));

        return kurt;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the curtosis of the first N values in array.
     */
    public static double kurtosis(final double[] array, final int N) {
        final double mean = StatUtils.mean(array, N);
        final double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            final double s = array[j] - mean;
            kurt += s * s * s * s;
        }

//        if (variance == 0) {
////            return kurt;
//            throw new ArithmeticException(
//                    "There is no kurtosis when the variance is zero.");
//        }

        kurt = kurt / N;

        kurt = kurt / (variance * variance) - 3.0;

//        kurt = (((N + 1) * N)/(double)((N-1)*(N-2)*(N-3))) * kurt - 3 * (N-1)*(N-1)/(double)((N-2)*(N-3));

        return kurt;
    }

    /**
     * GAMMA FUNCTION  (From DStat, used by permission).
     * <p>
     * Calculates the value of gamma(double z) using Handbook of Mathematical
     * Functions AMS 55 by Abromowitz page 256.
     *
     * @param z nonnegative double value.
     * @return the gamma value of z.
     */
    public static double gamma(final double z) {

        // if z is < 2 then do straight gamma
        if (z < 2.0) {
            return (StatUtils.Internalgamma(z));
        } else {

            // z >= 2.0, break up into N*1.5 and use Gauss
            // Multiplication formula.
            final double multiplier = Math.floor(z / 1.2);
            final double remainder = z / multiplier;
            final double coef1 =
                    Math.pow(2.0 * Math.PI, (0.5 * (1.0 - multiplier)));
            final double coef2 =
                    Math.pow(multiplier, ((multiplier * remainder) - 0.5));
            final int N = (int) multiplier;
            double prod = 1.0;

            for (int k = 0; k < N; k++) {
                prod *= StatUtils.Internalgamma(
                        remainder + ((double) k / multiplier));
            }

            return coef1 * coef2 * prod;
        }
    }

    /**
     * An internal method for finding gamma for a restricted range of reals.
     *
     * @param z argument
     * @return gamma of argument.
     */
    private static double Internalgamma(final double z) {
        double sum = 0.0;
        final double[] c = {1.0, 0.5772156649015329, -0.6558780715202538,
                -0.0420026350340952, 0.1665386113822915, -0.0421977345555443,
                -0.0096219715278770, 0.0072189432466630, -0.0011651675918591,
                -0.0002152416741149, 0.0001280502823882, -0.0000201348547807,
                -0.0000012504934821, 0.0000011330272320, -0.0000002056338417,
                0.0000000061160950, 0.0000000050020075, -0.0000000011812746,
                0.0000000001043427, 0.0000000000077823, -0.0000000000036968,
                0.0000000000005100, -0.0000000000000206, -0.0000000000000054,
                0.0000000000000014, 0.0000000000000001};

        for (int i = 0; i < c.length; i++) {
            sum += c[i] * Math.pow(z, (double) (i + 1));
        }

        return (1.0 / sum);
    }

    /**
     * Calculates the value of beta for doubles
     *
     * @param x1 the first double
     * @param x2 the second double.
     * @return beta(x1, x2).
     */
    public static double beta(final double x1, final double x2) {
        return ((StatUtils.gamma(x1) * StatUtils.gamma(x2)) / StatUtils.gamma(x1 + x2));
    }

    /**
     * Calculates the incomplete gamma function for two doubles
     *
     * @param a first double.
     * @param x second double.
     * @return incomplete gamma of (a, x).
     */
    public static double igamma(final double a, final double x) {
        final double coef = (Math.exp(-x) * Math.pow(x, a)) / StatUtils.gamma(a);
        double sum = 0.0;

        for (int i = 0; i < 100; i++) {
            sum += (StatUtils.gamma(a) / StatUtils.gamma(a + 1.0 + (double) i)) *
                    Math.pow(x, (double) i);
        }

        return (coef * sum);
    }

    /**
     * Calculates the error function for a double
     *
     * @param x argument.
     * @return error function of this argument.
     */
    public static double erf(final double x) {
        return (StatUtils.igamma(0.5, Math.pow(x, 2.0)));
    }

    /**
     * Calculates the Poisson Distribution for mean x and k events for doubles.
     * If third parameter is boolean true, the cumulative Poisson function is
     * returned.
     *
     * @param k   # events
     * @param x   mean
     * @param cum true if the cumulative Poisson is desired.
     * @return the value of the Poisson (or cumPoisson) at x.
     */
    public static double poisson(double k, final double x, final boolean cum) {
        if ((x < 0) || (k < 1)) {
            throw new ArithmeticException(
                    "The Poisson Distribution Function requires x>=0 and k >= 1");
        }

        k = k + 1;    // algorithm uses k+1, not k

        if (cum) {
            return (1.0 - StatUtils.igamma(k, x));
        } else {
            return ((Math.exp(-x) * Math.pow(x, k)) / StatUtils.gamma(k));
        }
    }

    /**
     * Calculates the one-tail probability of the Chi-squared distribution for
     * doubles
     *
     * @return value of Chi at x with the stated degrees of freedom.
     */
    public static double chidist(final double x, final int degreesOfFreedom) {
        if ((x < 0.0) || (degreesOfFreedom < 0)) {
            throw new ArithmeticException(
                    "The Chi Distribution Function requires x > 0.0 and degrees of freedom > 0");
        }

        return (1.0 - StatUtils.igamma((double) degreesOfFreedom / 2.0, x / 2.0));
    }


    //returns the value of a toss of an n-sided die
    public static int dieToss(final int n) {
        return (int) java.lang.Math.floor(n * java.lang.Math.random());
    }

    /**
     * Calculates the cutoff value for p-values using the FDR method. Hypotheses
     * with p-values less than or equal to this cutoff should be rejected
     * according to the test.
     *
     * @param alpha                The desired effective significance level.
     * @param pValues              An list containing p-values to be tested in
     *                             positions 0, 1, ..., n. (The rest of the
     *                             array is ignored.) <i>Note:</i> This array
     *                             will not be changed by this class. Its values
     *                             are copied into a separate array before
     *                             sorting.
     * @param negativelyCorrelated Whether the p-values in the array
     *                             <code>pValues </code> are negatively correlated (true if
     *                             yes, false if no). If they are uncorrelated, or positively correlated,
     *                             a level of alpha is used; if they are not
     *                             correlated, a level of alpha / SUM_i=1_n(1 /
     *                             i) is used.
     * @return the FDR alpha, which is the first p-value sorted high to low to
     * fall below a line from (1.0, level) to (0.0, 0.0). Hypotheses
     * less than or equal to this p-value should be rejected.
     */
    public static double fdrCutoff(final double alpha, final List<Double> pValues, final boolean negativelyCorrelated, final boolean pSorted) {
        return StatUtils.fdrCutoff(alpha, pValues, new int[1], negativelyCorrelated, pSorted);
    }

    public static double fdrCutoff(final double alpha, final List<Double> pValues, final boolean negativelyCorrelated) {
        return StatUtils.fdrCutoff(alpha, pValues, new int[1], negativelyCorrelated, false);
    }

    public static double fdrCutoff(final double alpha, final List<Double> pValues, final int[] _k, final boolean negativelyCorrelated, final boolean pSorted) {
        if (_k.length != 1) {
            throw new IllegalArgumentException("k must be a length 1 int array, to return the index of q.");
        }

        if (!pSorted) {
//            pValues = new ArrayList<>(pValues);
            Collections.sort(pValues);
        }

        _k[0] = StatUtils.fdr(alpha, pValues, negativelyCorrelated, true);
        return _k[0] == -1 ? 0 : pValues.get(_k[0]);
    }

    /**
     * @return the index, >=, in the sorted list of p values of which all p values are rejected. It
     * the index is -1, all p values are rejected.
     */
    public static int fdr(final double alpha, final List<Double> pValues) {
        return StatUtils.fdr(alpha, pValues, true, false);
    }

    public static int fdr(final double alpha, List<Double> pValues, final boolean negativelyCorrelated, final boolean pSorted) {
        if (!pSorted) {
            pValues = new ArrayList<>(pValues);
            Collections.sort(pValues);
        }

        final int m = pValues.size();

        if (negativelyCorrelated) {
            final double[] c = new double[m];

            double _c = 0;

            for (int i = 0; i < m; i++) {
                _c += 1. / (i + 1);
                c[i] = _c;
            }

            int _k = -1;

            for (int k = 0; k < m; k++) {
                if (pValues.get(k) <= ((k + 1) / (c[k] * (m + 1))) * alpha) {
                    _k = k;
                }
            }

            // Return the largest k such that P(k) <= (k / m) * alpha.
            return _k;

        } else {
            int _k = -1;

            for (int k = 0; k < m; k++) {
                if (pValues.get(k) <= ((k + 1) / (double) (m + 1)) * alpha) {
                    _k = k;
                }
            }

            // Return the largest k such that P(k) <= (k / m) * alpha.
            return _k;

        }
    }

    public static double fdrQ(final List<Double> pValues, final int k) {
        double high = 1.0;
        double low = 0.0;
        double q = NaN;
        int lastK = -1;

        while (high - low > 0) {
            q = (high + low) / 2.0;
            final int _k = StatUtils.fdr(q, pValues);

            if (_k == lastK) {
                high = q;
                low = q;
            } else if (_k > k) {
                high = q;
            } else if (_k < k) {
                low = q;
            }

            lastK = _k;
        }

        return q;

    }

    /**
     * Assumes that the given covariance matrix was extracted in such a way that the order
     * of the variables (in either direction) is X, Y, Z1, ..., Zn, where the partial
     * covariance one wants is covariance(X, Y | Z1,...,Zn). This may be extracted
     * using DataUtils.submatrix().
     *
     * @return the given partial covariance.
     */
    public static double partialCovariance(final Matrix submatrix) {

        // Using the method in Whittacker.
        // cov(X, Y | Z) = cov(X, Y) - cov(X, Z) inverse(cov(Z, Z)) cov(Z, Y)
        final double covXy = submatrix.get(0, 1);

        final int[] _z = new int[submatrix.rows() - 2];
        for (int i = 0; i < submatrix.rows() - 2; i++) _z[i] = i + 2;

        final Matrix covXz = submatrix.getSelection(new int[]{0}, _z);
        final Matrix covZy = submatrix.getSelection(_z, new int[]{1});
        final Matrix covZ = submatrix.getSelection(_z, _z);

        final Matrix _zInverse = covZ.inverse();

        final Matrix temp1 = covXz.times(_zInverse);
        final Matrix temp2 = temp1.times(covZy);

        return covXy - temp2.get(0, 0);

    }

    /**
     * @return the partial covariance(x, y | z) where these represent the column/row indices
     * of the desired variables in <code>covariance</code>
     */
    public static double partialCovariance(final Matrix covariance, final int x, final int y, final int... z) {
//        submatrix = TetradAlgebra.in                                                                                                                                 verse(submatrix);
//        return -1.0 * submatrix.get(0, 1);

        if (x > covariance.rows()) throw new IllegalArgumentException();
        if (y > covariance.rows()) throw new IllegalArgumentException();
        for (final int aZ : z) if (aZ > covariance.rows()) throw new IllegalArgumentException();

        final int[] selection = new int[z.length + 2];

        selection[0] = x;
        selection[1] = y;
        System.arraycopy(z, 0, selection, 2, z.length);

        return StatUtils.partialCovariance(covariance.getSelection(selection, selection));
    }

    public static double partialVariance(final Matrix covariance, final int x, final int... z) {
        return StatUtils.partialCovariance(covariance, x, x, z);
    }

    public static double partialStandardDeviation(final Matrix covariance, final int x, final int... z) {
        final double var = StatUtils.partialVariance(covariance, x, z);
        return Math.sqrt(var);
    }

    /**
     * Assumes that the given covariance matrix was extracted in such a way that the order
     * of the variables (in either direction) is X, Y, Z1, ..., Zn, where the partial
     * correlation one wants is correlation(X, Y | Z1,...,Zn). This may be extracted
     * using DataUtils.submatrix().
     *
     * @return the given partial correlation.
     */
    public static synchronized double partialCorrelation(final Matrix submatrix) {
        try {
            return StatUtils.partialCorrelationPrecisionMatrix(submatrix);
        } catch (final SingularMatrixException e) {
            return NaN;
        }
    }

    public static double partialCorrelationPrecisionMatrix(final Matrix submatrix) {
        final Matrix inverse = submatrix.inverse();
        return (-inverse.get(0, 1)) / sqrt(inverse.get(0, 0) * inverse.get(1, 1));
    }

//    public static synchronized double partialCorrelationWhittaker(Matrix submatrix) {
//        double cov = partialCovariance(submatrix);
//
//        int[] selection1 = new int[submatrix.rows()];
//        int[] selection2 = new int[submatrix.rows()];
//
//        selection1[0] = 0;
//        selection1[1] = 0;
//        for (int i = 2; i < selection1.length; i++) selection1[i] = i;
//
//        Matrix var1Matrix = submatrix.getSelection(selection1, selection1);
//        double var1 = partialCovariance(var1Matrix);
//
//        selection2[0] = 1;
//        selection2[1] = 1;
//        for (int i = 2; i < selection2.length; i++) selection2[i] = i;
//
//        Matrix var2Matrix = submatrix.getSelection(selection2, selection2);
//        double var2 = partialCovariance(var2Matrix);
//
//        return cov / Math.sqrt(var1 * var2);
//    }

    /**
     * @return the partial correlation(x, y | z) where these represent the column/row indices
     * of the desired variables in <code>covariance</code>
     */
    public static double partialCorrelation(final Matrix covariance, final int x, final int y, final int... z) {
        if (x > covariance.rows()) throw new IllegalArgumentException();
        if (y > covariance.rows()) throw new IllegalArgumentException();
        for (final int aZ : z) if (aZ > covariance.rows()) throw new IllegalArgumentException();

        final int[] selection = new int[z.length + 2];

        selection[0] = x;
        selection[1] = y;
        System.arraycopy(z, 0, selection, 2, z.length);

        return StatUtils.partialCorrelation(covariance.getSelection(selection, selection));
    }

    public static double logCoshScore(double[] _f) {
        _f = StatUtils.standardizeData(_f);

        final DoubleArrayList f = new DoubleArrayList(_f);

        for (int k = 0; k < _f.length; k++) {
            final double v = Math.log(Math.cosh((f.get(k))));
            f.set(k, v);
        }

        final double expected = Descriptive.mean(f);
        final double diff = expected - StatUtils.logCoshExp;
        return diff * diff;
    }

    public static double meanAbsolute(double[] _f) {
        _f = StatUtils.standardizeData(_f);

        for (int k = 0; k < _f.length; k++) {
//            _f[k] = Math.abs(_f[k]);
//            _f[k] = Math.pow(Math.tanh(_f[k]), 2.0);
            _f[k] = Math.abs(_f[k]);
        }

        final double expected = StatUtils.mean(_f);
        final double diff = expected - Math.sqrt(2.0 / Math.PI);
//
//        System.out.println("ttt " + pow2 + " " + Math.sqrt(2 / Math.PI));
//        double diff = expected - pow2;
        return diff * diff;
    }

    static double pow2 = StatUtils.pow();

    public static double pow() {
        double sum = 0.0;

        for (int i = 0; i < 1000; i++) {
//            sum += Math.pow(Math.tanh(RandomUtil.getInstance().nextNormal(0, 1)), 2);
            sum += Math.abs(RandomUtil.getInstance().nextNormal(0, 1));
        }

        return sum / 1000;
    }

    public static double expScore(final double[] _f) {
//        _f = DataUtils.standardizeData(_f);
        final DoubleArrayList f = new DoubleArrayList(_f);

        for (int k = 0; k < _f.length; k++) {
            f.set(k, Math.exp(f.get(k)));
        }

        final double expected = Descriptive.mean(f);

        return Math.log(expected);

//        double diff = logExpected - 0.5;
//        return Math.abs(diff);
    }

    public static double logCoshExp() {
//        return 0.3745232061467262;
        return 0.3746764078432371;
    }

    public static double entropy(final int numBins, final double[] _f) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        for (final double x : _f) {
            if (x < min) min = x;
            if (x > max) max = x;
        }

        final int[] v = new int[numBins];
        final double width = max - min;

        for (final double x : _f) {
            final double x3 = (x - min) / width; // 0 to 1
            final int bin = (int) (x3 * (numBins - 1));  // 0 to numBins - 1
            v[bin]++;
        }

        // Calculate entropy.
        double sum = 0.0;

        for (final int aV : v) {
            if (aV != 0) {
                final double p = aV / (double) (numBins - 1);
                sum += p * Math.log(p);
            }
        }

        return -sum;
    }

    public static double maxEntApprox(double[] x) {

        final double xstd = StatUtils.sd(x);
        x = StatUtils.standardizeData(x);

        final double k1 = 36 / (8 * sqrt(3) - 9);
        final double gamma = 0.37457;
        final double k2 = 79.047;
        final double gaussianEntropy = (log(2.0 * PI) / 2.0) + 1.0 / 2.0;

        // This is negentropy
        double b1 = 0.0;

        for (final double aX1 : x) {
            b1 += log(cosh(aX1));
        }

        b1 /= x.length;

        double b2 = 0.0;

        for (final double aX : x) {
            b2 += aX * exp(Math.pow(-aX, 2) / 2);
        }

        b2 /= x.length;

        final double negentropy = k2 * Math.pow(b1 - gamma, 2) + k1 * Math.pow(b2, 2);

        return gaussianEntropy - negentropy + log(xstd);
    }

    public static double[] standardizeData(final double[] data) {
        final double[] data2 = new double[data.length];

        double sum = 0.0;

        for (final double aData : data) {
            sum += aData;
        }

        final double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data2[i] = data[i] - mean;
        }

        double norm = 0.0;

        for (final double v : data2) {
            norm += v * v;
        }

        norm = Math.sqrt(norm / (data2.length - 1));

        for (int i = 0; i < data2.length; i++) {
            data2[i] = data2[i] / norm;
        }

        return data2;
    }

    public static double factorial(final int c) {
        if (c < 0) throw new IllegalArgumentException("Can't take the factorial of a negative number: " + c);
        if (c == 0) return 1;
        return c * StatUtils.factorial(c - 1);
    }

    public static double getZForAlpha(final double alpha) {
        final NormalDistribution dist = new NormalDistribution(0, 1);
        return dist.inverseCumulativeProbability(1.0 - alpha / 2.0);
    }

    public static double getChiSquareCutoff(final double alpha, final int df) {
        double low = 0.0;
        double high = 50.0;
        double mid = 25.0;
        final ChiSquaredDistribution dist = new ChiSquaredDistribution(df);

        while (high - low > 1e-4) {
            mid = (high + low) / 2.0;
            final double _alpha = 2.0 * (1.0 - dist.cumulativeProbability(Math.abs(mid)));

            if (_alpha > alpha) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return mid;
    }

    // Calculates the log of a list of terms, where the argument consists of the logs of the terms.
    public static double logsum(final List<Double> logs) {

        Collections.sort(logs, new Comparator<Double>() {
            @Override
            public int compare(final Double o1, final Double o2) {
                return -Double.compare(o1, o2);
            }
        });

        double sum = 0.0;
        final int N = logs.size() - 1;
        final double loga0 = logs.get(0);

        for (int i = 1; i <= N; i++) {
            sum += exp(logs.get(i) - loga0);
        }

        sum += 1;

        return loga0 + log(sum);
    }

    public static double sum(final double[] x) {
        double sum = 0.0;
        for (final double xx : x) sum += xx;
        return sum;
    }

    public static double[] cov(final double[] x, final double[] y, final double[] condition, final double threshold, final double direction) {
        double exy = 0.0;
        double exx = 0.0;
        double eyy = 0.0;

        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (direction > threshold) {
                if (condition[k] > threshold) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            } else if (direction < threshold) {
                if (condition[k] > threshold) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    ex += x[k];
                    ey += y[k];
                    n++;
                }
            }
        }

        exy /= n;
        exx /= n;
        eyy /= n;
        ex /= n;
        ey /= n;

        final double sxy = exy - ex * ey;
        final double sx = exx - ex * ex;
        final double sy = eyy - ey * ey;

        return new double[]{sxy, sxy / sqrt(sx * sy), sx, sy, (double) n, ex, ey, sxy / sx};
    }

    public static double[][] covMatrix(final double[] x, final double[] y, final double[][] z, final double[] condition, final double threshold, final double direction) {
        final List<Integer> rows = StatUtils.getRows(condition, threshold, direction);

        final double[][] allData = new double[z.length + 2][];

        allData[0] = x;
        allData[1] = y;

        for (int i = 0; i < z.length; i++) allData[i + 2] = z[i];

        final double[][] subdata = new double[allData.length][rows.size()];

        for (int c = 0; c < allData.length; c++) {
            for (int i = 0; i < rows.size(); i++) {
                try {
                    subdata[c][i] = allData[c][rows.get(i)];
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }

        final double[][] cov = new double[z.length + 2][z.length + 2];

        for (int i = 0; i < z.length + 2; i++) {
            for (int j = i; j < z.length + 2; j++) {
//                double c = StatUtils.sxy(subdata[i], subdata[j]);
                final double c = StatUtils.covariance(subdata[i], subdata[j]);
                cov[i][j] = c;
                cov[j][i] = c;
            }
        }

        return cov;
    }

    public static List<Integer> getRows(final double[] x, final double threshold, final double direction) {
        final List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < x.length; k++) {
            if (direction > threshold) {
                if (x[k] > threshold) {
                    rows.add(k);
                }
            } else if (direction < threshold) {
                if (x[k] > threshold) {
                    rows.add(k);
                }
            } else {
                if (x[k] > threshold) {
                    rows.add(k);
                }
            }
        }

        return rows;
    }

    public static List<Integer> getRows(final double[] x, final double[] condition, final double threshold, final double direction) {
        final List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < x.length; k++) {
            if (direction > threshold) {
                if (condition[k] > threshold) {
                    rows.add(k);
                }
            } else if (direction < threshold) {
                if (condition[k] > threshold) {
                    rows.add(k);
                }
            }
        }
        return rows;
    }

    public static double[] E(final double[] x, final double[] y, final double[] condition, final double threshold, final double direction) {
        double exy = 0.0;
        double exx = 0.0;
        double eyy = 0.0;
        double exm = 0.0;
        double eym = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (direction > threshold) {
                if (condition[k] > threshold) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    exm += x[k];
                    eym += y[k];
                    n++;
                }
            } else if (direction < threshold) {
                if (condition[k] > threshold) {
                    exy += x[k] * y[k];
                    exx += x[k] * x[k];
                    eyy += y[k] * y[k];
                    exm += x[k];
                    eym += y[k];
                    n++;
                }
            }
        }

        exx /= n;
        eyy /= n;
        exy /= n;

        double exye = 0.0;
        double exxe = 0.0;
        double eyye = 0.0;

        for (int k = 0; k < x.length; k++) {
            if (direction > threshold) {
                if (condition[k] > threshold) {
                    exye += (x[k] * y[k] - exy) * (x[k] * y[k] - exy);
                    exxe += (x[k] * x[k] - exx) * (x[k] * x[k] - exx);
                    eyye += (y[k] * y[k] - eyy) * (y[k] * y[k] - eyy);
                }
            } else if (direction < threshold) {
                if (condition[k] > threshold) {
                    exye += (x[k] * y[k] - exy) * (x[k] * y[k] - exy);
                    exxe += (x[k] * x[k] - exx) * (x[k] * x[k] - exx);
                    eyye += (y[k] * y[k] - eyy) * (y[k] * y[k] - eyy);
                }
            }
        }

        exye /= n;
        exxe /= n;
        eyye /= n;

        final double exyv = sqrt(exye / sqrt(exxe * eyye)) / sqrt(n - 1);

        return new double[]{exy, exy / sqrt(exx * eyy), exx, eyy, (double) n, exyv};
    }
}




