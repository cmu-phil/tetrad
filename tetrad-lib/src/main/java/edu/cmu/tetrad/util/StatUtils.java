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
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    private static final double logCoshExp = logCoshExp();

    /**
     * @param array a long array.
     * @return the mean of the values in this array.
     */
    public static double mean(long[] array) {
        return mean(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the mean of the values in this array.
     */
    public static double mean(double[] array) {
        return mean(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the mean of the first N values in this array.
     */
    public static double mean(long array[], int N) {

        int i;
        long sum = 0;

        for (i = 0; i < N; i++) {
            sum += array[i];
        }

        return sum / N;
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the mean of the first N values in this array.
     */
    public static double mean(double array[], int N) {
        double sum = 0.0;

        for (int i = 0; i < N; i++) {
            sum += array[i];
        }

        return sum / N;
    }

    /**
     * @param data a column vector.
     * @param N    the number of values of array which should be considered.
     * @return the mean of the first N values in this array.
     */
    public static double mean(TetradVector data, int N) {
        double sum = 0.0;

        for (int i = 0; i < N; i++) {
            sum += data.get(i);
        }

        return sum / N;
    }

    /**
     * @param array a long array.
     * @return the median of the values in this array.
     */
    public static double median(long[] array) {
        return median(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the median of the values in this array.
     */
    public static double median(double[] array) {
        return median(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the median of the first N values in this array.
     */
    public static long median(long array[], int N) {

        long a[] = new long[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Long.MAX_VALUE;

        long v, t;
        int i, j, l = 0;
        int r = N - 1, k1 = r / 2, k2 = r - k1;

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
    public static double median(double array[], int N) {

        double a[] = new double[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Double.POSITIVE_INFINITY;

        double v, t;
        int i, j, l = 0;
        int r = N - 1, k1 = r / 2, k2 = r - k1;

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
    public static double quartile(long array[], int quartileNumber) {
        return quartile(array, array.length, quartileNumber);
    }

    /**
     * @param array          a double array.
     * @param quartileNumber 1, 2, or 3.
     * @return the requested quartile of the values in this array.
     */
    public static double quartile(double array[], int quartileNumber) {
        return quartile(array, array.length, quartileNumber);
    }

    /**
     * @param array          a long array.
     * @param N              the number of values of array which should be
     *                       considered.
     * @param quartileNumber 1, 2, or 3.
     * @return the requested quartile of the first N values in this array.
     */
    public static double quartile(long array[], int N, int quartileNumber) {

        if ((quartileNumber < 1) || (quartileNumber > 3)) {
            throw new IllegalArgumentException("StatUtils.quartile:  " +
                    "Quartile number must be 1, 2, or 3.");
        }

        long a[] = new long[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Long.MAX_VALUE;

        long v, t;
        int i, j, l = 0;
        int r = N - 1;

        // find the two indexes k1 and k2 (possibly equal) which need
        // to be interpolated to get the quartile, being careful to
        // zero-index.
        double doubleIndex = (quartileNumber / 4.0) * (N + 1.0) - 1;
        double ratio = doubleIndex - (int) (doubleIndex);
        int k1 = (int) Math.floor(doubleIndex);
        int k2 = (int) Math.ceil(doubleIndex);

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
    public static double quartile(double array[], int N, int quartileNumber) {

        if ((quartileNumber < 1) || (quartileNumber > 3)) {
            throw new IllegalArgumentException("StatUtils.quartile:  " +
                    "Quartile number must be 1, 2, or 3.");
        }

        double a[] = new double[N + 1];

        System.arraycopy(array, 0, a, 0, N);

        a[N] = Double.POSITIVE_INFINITY;

        double v, t;
        int i, j, l = 0;
        int r = N - 1;

        // find the two indexes k1 and k2 (possibly equal) which need
        // to be interpolated to get the quartile, being careful to
        // zero-index.  Also find interpolation ratio.
        double doubleIndex = (quartileNumber / 4.0) * (N + 1.0) - 1;
        double ratio = doubleIndex - (int) (doubleIndex);
        int k1 = (int) Math.floor(doubleIndex);
        int k2 = (int) Math.ceil(doubleIndex);

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
    public static double min(long[] array) {
        return min(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the minimum of the values in this array.
     */
    public static double min(double[] array) {
        return min(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the minimum of the first N values in this array.
     */
    public static double min(long array[], int N) {

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
    public static double min(double array[], int N) {

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
    public static double max(long[] array) {
        return max(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the maximum of the values in this array.
     */
    public static double max(double[] array) {
        return max(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the maximum of the first N values in this array.
     */
    public static double max(long array[], int N) {

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
    public static double max(double array[], int N) {

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
    public static double range(long array[]) {
        return (max(array, array.length) - min(array, array.length));
    }

    /**
     * @param array a double array.
     * @return the range of the values in this array.
     */
    public static double range(double array[]) {
        return (max(array, array.length) - min(array, array.length));
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the range of the first N values in this array.
     */
    public static double range(long array[], int N) {
        return (max(array, N) - min(array, N));
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the range of the first N values in this array.
     */
    public static double range(double array[], int N) {
        return (max(array, N) - min(array, N));
    }

    /**
     * @param array a long array.
     * @return the length of this array.
     */
    public static int N(long[] array) {
        return array.length;
    }

    /**
     * @param array a double array.
     * @return the length of this array.
     */
    public static int N(double[] array) {
        return array.length;
    }

    /**
     * @param array a long array.
     * @return the sum of the squared differences from the mean in array.
     */
    public static double ssx(long[] array) {
        return ssx(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the sum of the squared differences from the mean in array.
     */
    public static double ssx(double[] array) {
        return ssx(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the sum of the squared differences from the mean of the first N
     * values in array.
     */
    public static double ssx(long array[], int N) {

        int i;
        double difference;
        double meanValue = mean(array, N);
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
    public static double ssx(double array[], int N) {

        int i;
        double difference;
        double meanValue = mean(array, N);
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
    public static double sxy(long[] array1, long[] array2) {

        int N1 = array1.length;
        int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "StatUtils.SXY: Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return sxy(array1, array2, N1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a double array, same length as array1.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for array1 and array2..
     */
    public static double sxy(double[] array1, double[] array2) {

        int N1 = array1.length;
        int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "StatUtils.SXY: Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return sxy(array1, array2, N1);
    }

    /**
     * @param array1 a long array.
     * @param array2 a long array.
     * @param N      the number of values of array which should be considered.
     * @return the sum of the squared differences of the products from the
     * products of the sample means for the first N values in array1 and
     * array2..
     */
    public static double sxy(long array1[], long array2[], int N) {

        int i;
        double sum = 0.0;
        double meanX = mean(array1, N);
        double meanY = mean(array2, N);

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
    public static double sxy(double array1[], double array2[], int N) {
        double sum = 0.0;
        double meanX = mean(array1, N);
        double meanY = mean(array2, N);

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
    public static double sxy(TetradVector data1, TetradVector data2, int N) {
        double sum = 0.0;
        double meanX = mean(data1, N);
        double meanY = mean(data2, N);

        for (int i = 0; i < N; i++) {
            sum += (data1.get(i) - meanX) * (data2.get(i) - meanY);
        }

        return sum;
    }

    /**
     * @param array a long array.
     * @return the variance of the values in array.
     */
    public static double variance(long array[]) {
        return variance(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the variance of the values in array.
     */
    public static double variance(double array[]) {
        return variance(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the variance of the first N values in array.
     */
    public static double variance(long array[], int N) {
        return ssx(array, N) / (N - 1);
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the variance of the first N values in array.
     */
    public static double variance(double array[], int N) {
        return ssx(array, N) / (N - 1);
    }

    /**
     * @param array a long array.
     * @return the standard deviation of the values in array.
     */
    public static double sd(long array[]) {
        return sd(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the standard deviation of the values in array.
     */
    public static double sd(double array[]) {
        return sd(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the standard deviation of the first N values in array.
     */
    public static double sd(long array[], int N) {
        return Math.pow(ssx(array, N) / (N - 1), .5);
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the standard deviation of the first N values in array.
     */
    public static double sd(double array[], int N) {
        return Math.pow(ssx(array, N) / (N - 1), .5);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array (same length as array1).
     * @return the covariance of the values in array.
     */
    public static double covariance(long[] array1, long[] array2) {

        int N1 = array1.length;
        int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return covariance(array1, array2, N1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array (same length as array1).
     * @return the covariance of the values in array.
     */
    public static double covariance(double[] array1, double[] array2) {
        int N1 = array1.length;
        int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return covariance(array1, array2, N1);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array.
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the covariance of the first N values in array1 and array2.
     */
    public static double covariance(long array1[], long array2[], int N) {
        return sxy(array1, array2, N) / (N - 1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array (same length as array1).
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the covariance of the first N values in array1 and array2.
     */
    public static double covariance(double array1[], double array2[], int N) {
        return sxy(array1, array2, N) / (N - 1);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array (same length as array1).
     * @return the Pearson's correlation of the values in array1 and array2.
     */
    public static double correlation(long[] array1, long[] array2) {

        int N1 = array1.length;
        int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return correlation(array1, array2, N1);
    }

    /**
     * @param array1 a double array.
     * @param array2 a second double array (same length as array1).
     * @return the Pearson's correlation of the values in array1 and array2.
     */
    public static double correlation(double[] array1, double[] array2) {

        int N1 = array1.length;
        int N2 = array2.length;

        if (N1 != N2) {
            throw new IllegalArgumentException(
                    "Arrays passed (or lengths specified) of " +
                            "unequal lengths.");
        }

        return correlation(array1, array2, N1);
    }

    public static double correlation(TetradVector data1, TetradVector data2) {
        int N = data1.size();
        double covXY = sxy(data1, data2, N);
        double covXX = sxy(data1, data1, N);
        double covYY = sxy(data2, data2, N);
        return (covXY / (Math.sqrt(covXX) * Math.sqrt(covYY)));
    }

    public static short compressedCorrelation(TetradVector data1, TetradVector data2) {
        return (short) (correlation(data1, data2) * 10000);
    }

    /**
     * @param array1 a long array.
     * @param array2 a second long array.
     * @param N      the number of values to be considered in array1 and
     *               array2.
     * @return the Pearson's correlation of the first N values in array1 and
     * array2.
     */
    public static double correlation(long array1[], long array2[], int N) {
        double covXY = sxy(array1, array2, N);
        double covXX = sxy(array1, array1, N);
        double covYY = sxy(array2, array2, N);
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
    public static double correlation(double array1[], double array2[], int N) {
        double covXY = sxy(array1, array2, N);
        double covXX = sxy(array1, array1, N);
        double covYY = sxy(array2, array2, N);
        return (covXY / (Math.sqrt(covXX) * Math.sqrt(covYY)));
    }

    public static double rankCorrelation(double[] arr1, double[] arr2) {
        if (arr1.length != arr2.length) {
            throw new IllegalArgumentException("Arrays not the same length.");
        }

        double[] ranks1 = getRanks(arr1);
        double[] ranks2 = getRanks(arr2);

        return correlation(ranks1, ranks2);
    }

    public static double kendallsTau(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays not the same length.");
        }

        int numerator = 0;
        int N = x.length;

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                numerator += signum(x[i] - x[j]) * signum(y[i] - y[j]);
            }
        }

        return numerator / (0.5 * N * (N - 1));
    }

    public static double[] getRanks(double[] arr) {
        double[] arr2 = new double[arr.length];
        System.arraycopy(arr, 0, arr2, 0, arr.length);
        Arrays.sort(arr2);

        double[] ranks = new double[arr.length];

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
    public static double sSquare(long[] array) {
        return sSquare(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is unknown.
     */
    public static double sSquare(double[] array) {
        return ssx(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is unknown.
     */
    public static double sSquare(long array[], int N) {
        return ssx(array, N) / (N - 1);
    }

    /**
     * @param array a double array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is unknown.
     */
    public static double sSquare(double array[], int N) {
        return ssx(array, N) / (N - 1);
    }

    /**
     * @param array a long array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is known.
     */
    public static double varHat(long array[]) {
        return varHat(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the unbaised estimate of the variance of the distribution of the
     * values in array asuming the mean is known.
     */
    public static double varHat(double array[]) {
        return varHat(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the variance of the distribution of the
     * first N values in array asuming the mean is known.
     */
    public static double varHat(long array[], int N) {
        double sum = 0;
        double difference;
        double meanX = mean(array, N);

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
    public static double varHat(double array[], int N) {
        double sum = 0.;
        double difference;
        double meanX = mean(array, N);

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
    public static double mu(long[] array) {
        return mean(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the unbaised estimate of the mean of the distribution of the
     * values in array.
     */
    public static double mu(double[] array) {
        return mean(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the mean of the distribution of the
     * first N values in array.
     */
    public static double mu(long array[], int N) {
        return mean(array, N);
    }

    /**
     * @param array a double array.
     * @param N     the number of values to be considered in array.
     * @return the unbaised estimate of the mean of the distribution of the
     * first N values in array.
     */
    public static double mu(double array[], int N) {
        return mean(array, N);
    }

    /**
     * @param array a long array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the values in array.
     */
    public static double muHat(long[] array) {
        return muHat(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the values in array.
     */
    public static double muHat(double[] array) {
        return muHat(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the first N values in array.
     */
    public static double muHat(long array[], int N) {
        return mean(array, N);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the maximum likelihood estimate of the mean of the distribution
     * of the first N values in array.
     */
    public static double muHat(double array[], int N) {
        return mean(array, N);
    }

    /**
     * @param array a long array.
     * @return the average deviation of the values in array.
     */
    public static double averageDeviation(long array[]) {
        return averageDeviation(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the average deviation of the values in array.
     */
    public static double averageDeviation(double array[]) {
        return averageDeviation(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values to be considered in array.
     * @return the average deviation of the first N values in array.
     */
    public static double averageDeviation(long array[], int N) {
        double mean = StatUtils.mean(array, N);
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
    public static double averageDeviation(double array[], int N) {
        double mean = StatUtils.mean(array, N);
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
    public static double skewness(long[] array) {
        return skewness(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the skew of the values in array.
     */
    public static double skewness(double[] array) {
//        array = removeNaN(array);
        return skewness(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the skew of the first N values in array.
     */
    public static double skewness(long array[], int N) {
        double mean = StatUtils.mean(array, N);
        double secondMoment = 0.0; // StatUtils.variance(array, N);
        double thirdMoment = 0.0;

        for (int j = 0; j < N; j++) {
            double s = array[j] - mean;
            secondMoment += s * s;
            thirdMoment += s * s * s;
        }

        if (secondMoment == 0) {
            throw new ArithmeticException("StatUtils.skew:  There is no skew " +
                    "when the variance is zero.");
        }

        //        thirdMoment /= (N * Math.pow(secondMoment, 1.5));
        return thirdMoment / Math.pow(secondMoment, 1.5);
    }

    /**
     * @param array a double array.
     * @param N     the number of values of array which should be considered.
     * @return the skew of the first N values in array.
     */
    public static double skewness(double array[], int N) {
        double mean = StatUtils.mean(array, N);
        double variance = StatUtils.variance(array, N);
        double skew = 0.0;

        for (int j = 0; j < N; j++) {
            double s = array[j] - mean;

            skew += s * s * s;
        }

//        if (variance == 0) {
//            throw new ArithmeticException("StatUtils.skew:  There is no skew " +
//                    "when the variance is zero.");
//        }

        skew /= (N * Math.pow(variance, 1.5));

        return skew;
    }

    public static double[] removeNaN(double[] x1) {
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
    public static double kurtosis(long array[]) {
        return kurtosis(array, array.length);
    }

    /**
     * @param array a double array.
     * @return the curtosis of the values in array.
     */
    public static double kurtosis(double array[]) {
        return kurtosis(array, array.length);
    }

    /**
     * @param array a long array.
     * @param N     the number of values of array which should be considered.
     * @return the curtosis of the first N values in array.
     */
    public static double kurtosis(long array[], int N) {
        double mean = StatUtils.mean(array, N);
        double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            double s = array[j] - mean;
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

    public static double standardizedFifthMoment(double[] array) {
        return standardizedFifthMoment(array, array.length);
    }

    public static double standardizedFifthMoment(double array[], int N) {
        double mean = StatUtils.mean(array, N);
        double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            double s = array[j] - mean;
            kurt += s * s * s * s * s;
        }

        if (variance == 0) {
            throw new ArithmeticException(
                    "Kurtosis is undefined when variance is zero.");
        }

        kurt = (kurt / (N * Math.pow(variance, 5 / 2.)));

        return kurt;
    }

    public static double standardizedSixthMoment(double[] array) {
        return standardizedFifthMoment(array, array.length);
    }

    public static double standardizedSixthMoment(double array[], int N) {
        double mean = StatUtils.mean(array, N);
        double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            double s = array[j] - mean;
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
    public static double kurtosis(double array[], int N) {
        double mean = StatUtils.mean(array, N);
        double variance = StatUtils.variance(array, N);
        double kurt = 0.0;

        for (int j = 0; j < N; j++) {
            double s = array[j] - mean;
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
    public static double gamma(double z) {

        // if z is < 2 then do straight gamma
        if (z < 2.0) {
            return (Internalgamma(z));
        } else {

            // z >= 2.0, break up into N*1.5 and use Gauss
            // Multiplication formula.
            double multiplier = Math.floor(z / 1.2);
            double remainder = z / multiplier;
            double coef1 =
                    Math.pow(2.0 * Math.PI, (0.5 * (1.0 - multiplier)));
            double coef2 =
                    Math.pow(multiplier, ((multiplier * remainder) - 0.5));
            int N = (int) multiplier;
            double prod = 1.0;

            for (int k = 0; k < N; k++) {
                prod *= Internalgamma(
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
    private static double Internalgamma(double z) {
        double sum = 0.0;
        double[] c = {1.0, 0.5772156649015329, -0.6558780715202538,
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
    public static double beta(double x1, double x2) {
        return ((gamma(x1) * gamma(x2)) / gamma(x1 + x2));
    }

    /**
     * Calculates the incomplete gamma function for two doubles
     *
     * @param a first double.
     * @param x second double.
     * @return incomplete gamma of (a, x).
     */
    public static double igamma(double a, double x) {
        double coef = (Math.exp(-x) * Math.pow(x, a)) / gamma(a);
        double sum = 0.0;

        for (int i = 0; i < 100; i++) {
            sum += (gamma(a) / gamma(a + 1.0 + (double) i)) *
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
    public static double erf(double x) {
        return (igamma(0.5, Math.pow(x, 2.0)));
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
    public static double poisson(double k, double x, boolean cum) {
        if ((x < 0) || (k < 1)) {
            throw new ArithmeticException(
                    "The Poisson Distribution Function requires x>=0 and k >= 1");
        }

        k = k + 1;    // algorithm uses k+1, not k

        if (cum) {
            return (1.0 - igamma(k, x));
        } else {
            return ((Math.exp(-x) * Math.pow(x, k)) / gamma(k));
        }
    }

    /**
     * Calculates the one-tail probability of the Chi-squared distribution for
     * doubles
     *
     * @return value of Chi at x with the stated degrees of freedom.
     */
    public static double chidist(double x, int degreesOfFreedom) {
        if ((x < 0.0) || (degreesOfFreedom < 0)) {
            throw new ArithmeticException(
                    "The Chi Distribution Function requires x > 0.0 and degrees of freedom > 0");
        }

        return (1.0 - igamma((double) degreesOfFreedom / 2.0, x / 2.0));
    }


    //returns the value of a toss of an n-sided die
    public static int dieToss(int n) {
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
    public static double fdrCutoff(double alpha, List<Double> pValues, boolean negativelyCorrelated, boolean pSorted) {
        return fdrCutoff(alpha, pValues, new int[1], negativelyCorrelated, pSorted);
    }

    public static double fdrCutoff(double alpha, List<Double> pValues, boolean negativelyCorrelated) {
        return fdrCutoff(alpha, pValues, new int[1], negativelyCorrelated, false);
    }

    public static double fdrCutoff(double alpha, List<Double> pValues, int[] _k, boolean negativelyCorrelated, boolean pSorted) {
        if (_k.length != 1) {
            throw new IllegalArgumentException("k must be a length 1 int array, to return the index of q.");
        }

        if (!pSorted) {
            pValues = new ArrayList<>(pValues);
            Collections.sort(pValues);
        }

        _k[0] = fdr(alpha, pValues, negativelyCorrelated, true);
        return _k[0] == -1 ? 0 : pValues.get(_k[0]);
    }

    /**
     * @return the index, >=, in the sorted list of p values of which all p values are rejected. It
     * the index is -1, all p values are rejected.
     */
    public static int fdr(double alpha, List<Double> pValues) {
        return fdr(alpha, pValues, true, false);
    }

    public static int fdr(double alpha, List<Double> pValues, boolean negativelyCorrelated, boolean pSorted) {
        if (!pSorted) {
            pValues = new ArrayList<>(pValues);
            Collections.sort(pValues);
        }

        int m = pValues.size();

        if (negativelyCorrelated) {
            double[] c = new double[m];

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

    public static double fdrQ(List<Double> pValues, int k) {
        double high = 1.0;
        double low = 0.0;
        double q = Double.NaN;
        int lastK = -1;

        while (high - low > 0) {
            q = (high + low) / 2.0;
            int _k = fdr(q, pValues);

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
    public static double partialCovariance(TetradMatrix submatrix) {
//        submatrix = TetradAlgebra.inverse(submatrix);
//        return -1.0 * submatrix.get(0, 1);

        // Using the method in Whittacker.
        // cov(X, Y | Z) = cov(X, Y) - cov(X, Z) inverse(cov(Z, Z)) cov(Z, Y)
        double covXy = submatrix.get(0, 1);

        int[] _z = new int[submatrix.rows() - 2];
        for (int i = 0; i < submatrix.rows() - 2; i++) _z[i] = i + 2;

        TetradMatrix covXz = submatrix.getSelection(new int[]{0}, _z).copy();
        TetradMatrix covZy = submatrix.getSelection(_z, new int[]{1}).copy();
        TetradMatrix covZ = submatrix.getSelection(_z, _z).copy();

        TetradMatrix _zInverse = covZ.inverse();

        TetradMatrix temp1 = covXz.times(_zInverse);
        TetradMatrix temp2 = temp1.times(covZy);

        return covXy - temp2.get(0, 0);

    }

    /**
     * @return the partial covariance(x, y | z) where these represent the column/row indices
     * of the desired variables in <code>covariance</code>
     */
    public static double partialCovariance(TetradMatrix covariance, int x, int y, int... z) {
//        submatrix = TetradAlgebra.in                                                                                                                                 verse(submatrix);
//        return -1.0 * submatrix.get(0, 1);

        if (x > covariance.rows()) throw new IllegalArgumentException();
        if (y > covariance.rows()) throw new IllegalArgumentException();
        for (int aZ : z) if (aZ > covariance.rows()) throw new IllegalArgumentException();

        int[] selection = new int[z.length + 2];

        selection[0] = x;
        selection[1] = y;
        System.arraycopy(z, 0, selection, 2, z.length);

        return partialCovariance(covariance.getSelection(selection, selection));
    }

    public static double partialVariance(TetradMatrix covariance, int x, int... z) {
        return partialCovariance(covariance, x, x, z);
    }

    public static double partialStandardDeviation(TetradMatrix covariance, int x, int... z) {
        double var = partialVariance(covariance, x, z);
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
    public static synchronized double partialCorrelation(TetradMatrix submatrix) {
//        double cov = partialCovariance(submatrix);
//
//        int[] selection1 = new int[submatrix.rows()];
//        int[] selection2 = new int[submatrix.rows()];
//
//        selection1[0] = 0;
//        selection1[1] = 0;
//        for (int i = 2; i < selection1.length; i++) selection1[i] = i;
//
//        TetradMatrix var1Matrix = submatrix.getSelection(selection1, selection1);
//        double var1 = partialCovariance(var1Matrix);
//
//        selection2[0] = 1;
//        selection2[1] = 1;
//        for (int i = 2; i < selection2.length; i++) selection2[i] = i;
//
//        TetradMatrix var2Matrix = submatrix.getSelection(selection2, selection2);
//        double var2 = partialCovariance(var2Matrix);
//
//        return cov / Math.sqrt(var1 * var2);


        try {
            TetradMatrix inverse = submatrix.inverse();

            double a = -1.0 * inverse.get(0, 1);
            double v0 = inverse.get(0, 0);
            double v1 = inverse.get(1, 1);
            double b = Math.sqrt(v0 * v1);

            return a / b;
        } catch (Exception e) {
            e.printStackTrace();
            return Double.NaN;
        }
    }

    /**
     * @return the partial correlation(x, y | z) where these represent the column/row indices
     * of the desired variables in <code>covariance</code>
     */
    public static double partialCorrelation(TetradMatrix covariance, int x, int y, int... z) {
        if (x > covariance.rows()) throw new IllegalArgumentException();
        if (y > covariance.rows()) throw new IllegalArgumentException();
        for (int aZ : z) if (aZ > covariance.rows()) throw new IllegalArgumentException();

        int[] selection = new int[z.length + 2];

        selection[0] = x;
        selection[1] = y;
        System.arraycopy(z, 0, selection, 2, z.length);

        return partialCorrelation(covariance.getSelection(selection, selection));
    }

    public static double logCoshScore(double[] _f) {
        _f = standardizeData(_f);

        DoubleArrayList f = new DoubleArrayList(_f);

        for (int k = 0; k < _f.length; k++) {
            double v = Math.log(Math.cosh((f.get(k))));
            f.set(k, v);
        }

        double expected = Descriptive.mean(f);
        double diff = expected - logCoshExp;
        return diff * diff;
    }

    public static double meanAbsolute(double[] _f) {
        _f = standardizeData(_f);

        for (int k = 0; k < _f.length; k++) {
//            _f[k] = Math.abs(_f[k]);
//            _f[k] = Math.pow(Math.tanh(_f[k]), 2.0);
            _f[k] = Math.abs(_f[k]);
        }

        double expected = StatUtils.mean(_f);
        double diff = expected - Math.sqrt(2.0 / Math.PI);
//
//        System.out.println("ttt " + pow2 + " " + Math.sqrt(2 / Math.PI));
//        double diff = expected - pow2;
        return diff * diff;
    }

    static double pow2 = pow();

    public static double pow() {
        double sum = 0.0;

        for (int i = 0; i < 1000; i++) {
//            sum += Math.pow(Math.tanh(RandomUtil.getInstance().nextNormal(0, 1)), 2);
            sum += Math.abs(RandomUtil.getInstance().nextNormal(0, 1));
        }

        return sum / 1000;
    }

    public static double expScore(double[] _f) {
//        _f = DataUtils.standardizeData(_f);
        DoubleArrayList f = new DoubleArrayList(_f);

        for (int k = 0; k < _f.length; k++) {
            f.set(k, Math.exp(f.get(k)));
        }

        double expected = Descriptive.mean(f);

        return Math.log(expected);

//        double diff = logExpected - 0.5;
//        return Math.abs(diff);
    }

    public static double logCoshExp() {
//        return 0.3745232061467262;
        return 0.3746764078432371;
    }

    public static double entropy(int numBins, double[] _f) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;

        for (double x : _f) {
            if (x < min) min = x;
            if (x > max) max = x;
        }

        int[] v = new int[numBins];
        double width = max - min;

        for (double x : _f) {
            double x3 = (x - min) / width; // 0 to 1
            int bin = (int) (x3 * (numBins - 1));  // 0 to numBins - 1
            v[bin]++;
        }

        // Calculate entropy.
        double sum = 0.0;

        for (int aV : v) {
            if (aV != 0) {
                double p = aV / (double) (numBins - 1);
                sum += p * Math.log(p);
            }
        }

        return -sum;
    }

    public static double maxEntApprox(double[] x) {

        double xstd = sd(x);
        x = standardizeData(x);

        double k1 = 36 / (8 * sqrt(3) - 9);
        double gamma = 0.37457;
        double k2 = 79.047;
        double gaussianEntropy = (log(2.0 * PI) / 2.0) + 1.0 / 2.0;

        // This is negentropy
        double b1 = 0.0;

        for (double aX1 : x) {
            b1 += log(cosh(aX1));
        }

        b1 /= x.length;

        double b2 = 0.0;

        for (double aX : x) {
            b2 += aX * exp(Math.pow(-aX, 2) / 2);
        }

        b2 /= x.length;

        double negentropy = k2 * Math.pow(b1 - gamma, 2) + k1 * Math.pow(b2, 2);

        return gaussianEntropy - negentropy + log(xstd);
    }

    public static double[] standardizeData(double[] data) {
        double[] data2 = new double[data.length];

        double sum = 0.0;

        for (double aData : data) {
            sum += aData;
        }

        double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data2[i] = data[i] - mean;
        }

        double norm = 0.0;

        for (double v : data2) {
            norm += v * v;
        }

        norm = Math.sqrt(norm / (data2.length - 1));

        for (int i = 0; i < data2.length; i++) {
            data2[i] = data2[i] / norm;
        }

        return data2;
    }

    public static double factorial(int c) {
        if (c < 0) throw new IllegalArgumentException("Can't take the factorial of a negative number: " + c);
        if (c == 0) return 1;
        return c * factorial(c - 1);
    }

    public static double getZForAlpha(double alpha) {
        double low = 0.0;
        double high = 20.0;
        double mid = 5.0;
        NormalDistribution dist = new NormalDistribution(0, 1);

        while (high - low > 1e-4) {
            mid = (high + low) / 2.0;
            double _alpha = 2.0 * (1.0 - dist.cumulativeProbability(Math.abs(mid)));

            if (_alpha > alpha) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return mid;
    }
}




