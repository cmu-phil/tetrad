///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import org.apache.commons.math3.distribution.*;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.*;

/**
 * Provides a common random number generator to be used throughout Tetrad, to avoid problems that happen when random
 * number generators are created more often than once per millisecond. When this happens, the generators are synced, and
 * there is less randomness than expected.
 * <p>
 * A seed can be set for the generator using the <code>setSeed</code> method. This is useful if an experiment needs to
 * be repeated under different conditions. The seed for an experiment can be printed using the <code>getSeed</code>
 * method.
 * <p>
 * The 64-bit Mersenne Twister implementation from the COLT library is used to generate random numbers.
 * <p>
 * To see what distributions are currently supported, look at the methods of the class. These many change over time.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomUtil {

    /**
     * The singleton instance.
     */
    private static final Map<Thread, RandomUtil> randomUtils = new HashMap<>();
    private static final int SHUFFLE_THRESHOLD = 5;
    private RandomGenerator randomGenerator;

    //========================================CONSTRUCTORS===================================//

    /**
     * Constructs a new random number generator based on the getModel date in milliseconds.
     */
    private RandomUtil() {
        setSeed(System.nanoTime());
    }

    /**
     * <p>getInstance.</p>
     *
     * @return the singleton instance of this class.
     */
    public static RandomUtil getInstance() {
        if (!randomUtils.containsKey(Thread.currentThread())) {
//            System.out.println("new thread");
            randomUtils.put(Thread.currentThread(), new RandomUtil());
        }
        return randomUtils.get(Thread.currentThread());
    }

    /**
     * This is just the RandomUtil.shuffle method (thanks!) but using the Tetrad RandomUtil to get random numbers. The
     * purpose of this copying is to allow shuffles to happen deterministically given the Randomutils seed.
     *
     * @param list The list to be shuffled.
     */
    public static void shuffle(List<?> list) {
        int size = list.size();
        if (size < SHUFFLE_THRESHOLD || list instanceof RandomAccess) {
            for (int i = size; i > 1; i--)
                swap(list, i - 1, getInstance().nextInt(i));
        } else {
            Object[] arr = list.toArray();

            // Shuffle array
            for (int i = size; i > 1; i--)
                swap(arr, i - 1, getInstance().nextInt(i));

            // Dump array back into list
            // instead of using a raw type here, it's possible to capture
            // the wildcard but it will require a call to a supplementary
            // private method
            ListIterator it = list.listIterator();
            for (Object e : arr) {
                it.next();
                it.set(e);
            }
        }
    }

    private static void swap(List<?> list, int i, int j) {
        // instead of using a raw type here, it's possible to capture
        // the wildcard but it will require a call to a supplementary
        // private method
        final List l = list;
        l.set(i, l.set(j, l.get(i)));
    }

    private static void swap(Object[] arr, int i, int j) {
        Object tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    //=======================================PUBLIC METHODS=================================//

    private static void testDeterminism() {
        int length = 10000000;
        long seed = 392949394L;

        RandomUtil.getInstance().setSeed(seed);
        List<Double> d1 = new ArrayList<>();
        for (int i = 0; i < length; i++) d1.add(RandomUtil.getInstance().nextDouble());

        RandomUtil.getInstance().setSeed(seed);
        List<Double> d2 = new ArrayList<>();
        for (int i = 0; i < length; i++) d2.add(RandomUtil.getInstance().nextDouble());

        boolean deterministic = d1.equals(d2);
        System.out.println(deterministic ? "Deterministic" : "Not deterministic");
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        testDeterminism();
    }

    /**
     * <p>nextInt.</p>
     *
     * @param n Ibid.
     * @return Ibid.
     */
    public int nextInt(int n) {
        return this.randomGenerator.nextInt(n);
    }

    /**
     * <p>nextDouble.</p>
     *
     * @return a double
     */
    public double nextDouble() {
        return this.randomGenerator.nextDouble();
    }

    /**
     * <p>nextUniform.</p>
     *
     * @param low  Ibid.
     * @param high Ibid.
     * @return Ibid.
     */
    public double nextUniform(double low, double high) {
        if (low == high) return low;
        else {
            return new UniformRealDistribution(this.randomGenerator, low, high).sample();
        }
    }

    /**
     * <p>nextNormal.</p>
     *
     * @param mean The mean of the Normal.
     * @param sd   The standard deviation of the Normal.
     * @return Ibid.
     */
    public double nextNormal(double mean, double sd) {
        return new NormalDistribution(randomGenerator, mean, sd).sample();
    }

    /**
     * <p>nextTruncatedNormal.</p>
     *
     * @param mean The mean of the Normal.
     * @param sd   The standard deviation of the Normal.
     * @param low  a double
     * @param high a double
     * @return Ibid.
     */
    public double nextTruncatedNormal(double mean, double sd, double low, double high) {
        if (sd < 0) {
            throw new IllegalArgumentException("Standard deviation must be non-negative: " + sd);
        }

        if (low >= high) {
            throw new IllegalArgumentException("Low must be less than high.");
        }

        double d;

        do {
            d = nextNormal(mean, sd);
        } while (!(d >= low) || !(d <= high));

        return d;
    }

    /**
     * <p>revertSeed.</p>
     *
     * @param seed a long
     */
    public void revertSeed(long seed) {
        this.randomGenerator = new Well44497b(seed);
    }

    /**
     * <p>nextPoisson.</p>
     *
     * @param lambda A positive real number equal to the expected number of occurrences during a given interval. See
     *               Wikipedia.
     * @return Ibid.
     */
    public double nextPoisson(double lambda) {
        return new PoissonDistribution(this.randomGenerator, lambda, 1.0E-12D, 100000).sample();
    }

    /**
     * <p>normalPdf.</p>
     *
     * @param mean  The mean of the normal to be used.
     * @param sd    The standard deviation of the normal to be used.
     * @param value The domain value for the PDF.
     * @return Ibid.
     */
    public double normalPdf(double mean, double sd, double value) {
        return new NormalDistribution(this.randomGenerator, mean, sd).density(value);
    }

    /**
     * <p>normalCdf.</p>
     *
     * @param mean  The mean of the normal to be used.
     * @param sd    The standard deviation of the normal to be used.
     * @param value The domain value for the CDF.
     * @return Ibid.
     */
    public double normalCdf(double mean, double sd, double value) {
        return new NormalDistribution(0, 1).cumulativeProbability((value - mean) / sd);
    }

    /**
     * <p>nextBeta.</p>
     *
     * @param alpha See Wikipedia. This is the first parameter.
     * @param beta  See Wikipedia. This is the second parameter.
     * @return Ibid.
     */
    public double nextBeta(double alpha, double beta) {
        return new BetaDistribution(this.randomGenerator, alpha, beta).sample();
    }

    /**
     * <p>nextT.</p>
     *
     * @param df The degrees of freedom. See any stats book.
     * @return Ibid.
     */
    public double nextT(double df) {
        return new TDistribution(this.randomGenerator, df).sample();
    }

    /**
     * <p>nextExponential.</p>
     *
     * @param lambda The rate parameter. See Wikipedia.
     * @return Ibid.
     */
    public double nextExponential(double lambda) {
        return new ExponentialDistribution(this.randomGenerator, lambda).sample();
    }

    /**
     * <p>nextGumbel.</p>
     *
     * @param mu   a double
     * @param beta a double
     * @return Ibid.
     */
    public double nextGumbel(double mu, double beta) {
        return new GumbelDistribution(this.randomGenerator, mu, beta).sample();
    }

    /**
     * <p>nextChiSquare.</p>
     *
     * @param df The degrees of freedom.
     * @return Ibid.
     */
    public double nextChiSquare(double df) {
        return new ChiSquaredDistribution(this.randomGenerator, df).sample();
    }

    /**
     * <p>nextGamma.</p>
     *
     * @param shape The shape parameter.
     * @param scale The scale parameter.
     * @return Ibid.
     */
    public double nextGamma(double shape, double scale) {
        return new GammaDistribution(this.randomGenerator, shape, scale).sample();
    }

    /**
     * Sets the seed to the given value.
     *
     * @param seed A long value. Once this seed is set, the behavior of the random number generator is deterministic, so
     *             setting the seed can be used to repeat previous behavior.
     */
    public void setSeed(long seed) {
        this.randomGenerator = new SynchronizedRandomGenerator(new Well44497b(seed));
//        this.randomGenerator = new SynchronizedRandomGenerator(new JDKRandomGenerator((int) seed));
    }

    /**
     * <p>Getter for the field <code>randomGenerator</code>.</p>
     *
     * @return a {@link org.apache.commons.math3.random.RandomGenerator} object
     */
    public RandomGenerator getRandomGenerator() {
        return this.randomGenerator;
    }

    /**
     * <p>nextLong.</p>
     *
     * @return a long
     */
    public long nextLong() {
        return this.randomGenerator.nextLong();
    }
}





