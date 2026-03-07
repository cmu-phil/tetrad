///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import org.apache.commons.math3.distribution.*;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

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
 * Uses Apache Commons RNG XoRoShiRo128++ as the underlying generator.
 * <p>
 * To see what distributions are currently supported, look at the methods of the class. These may change over time.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomUtil {

    /**
     * Per-thread singleton instances.
     */
    private static final Map<Thread, RandomUtil> randomUtils = new HashMap<>();

    private static final int SHUFFLE_THRESHOLD = 5;

    /**
     * Underlying RNG used everywhere in this class.
     */
    private UniformRandomProvider randomGenerator;

    /**
     * Adapter for Commons Math distributions that still require a RandomGenerator.
     * All randomness is delegated to the underlying UniformRandomProvider above.
     */
    private final RandomGenerator math3RandomGenerator = new RandomGenerator() {

        @Override
        public void setSeed(int seed) {
            RandomUtil.this.setSeed(seed);
        }

        @Override
        public void setSeed(int[] seed) {
            long s = 0L;
            if (seed != null) {
                for (int x : seed) {
                    s = 31L * s + x;
                }
            }
            RandomUtil.this.setSeed(s);
        }

        @Override
        public void setSeed(long seed) {
            RandomUtil.this.setSeed(seed);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (bytes == null) {
                throw new NullPointerException("bytes");
            }
            randomGenerator.nextBytes(bytes);
        }

        @Override
        public int nextInt() {
            return randomGenerator.nextInt();
        }

        @Override
        public int nextInt(int n) {
            return randomGenerator.nextInt(n);
        }

        @Override
        public long nextLong() {
            return randomGenerator.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return randomGenerator.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return randomGenerator.nextFloat();
        }

        @Override
        public double nextDouble() {
            return randomGenerator.nextDouble();
        }

        @Override
        public double nextGaussian() {
            // Box-Muller transform.
            double u1 = randomGenerator.nextDouble();
            while (u1 <= 0.0) {
                u1 = randomGenerator.nextDouble();
            }

            double u2 = randomGenerator.nextDouble();

            return TMath.sqrt(-2.0 * TMath.log(u1)) *
                    TMath.cos(2.0 * TMath.PI * u2);
        }
    };

    //========================================CONSTRUCTORS===================================//

    /**
     * Constructs a new random number generator based on current time in nanoseconds.
     */
    private RandomUtil() {
        setSeed(System.nanoTime());
    }

    /**
     * Retrieves the singleton instance of RandomUtil associated with the current thread.
     * If no instance exists for the current thread, a new one is created and stored.
     *
     * @return the RandomUtil instance associated with the current thread
     */
    public static RandomUtil getInstance() {
        Thread thread = Thread.currentThread();

        if (!randomUtils.containsKey(thread)) {
            randomUtils.put(thread, new RandomUtil());
        }

        return randomUtils.get(thread);
    }

    /**
     * This is just the Collections.shuffle logic but using Tetrad RandomUtil to get random numbers.
     * The purpose is to allow shuffles to happen deterministically given the RandomUtil seed.
     *
     * @param list The list to be shuffled.
     */
    public static synchronized void shuffle(List<?> list) {
        int size = list.size();

        if (size < SHUFFLE_THRESHOLD || list instanceof RandomAccess) {
            for (int i = size; i > 1; i--) {
                swap(list, i - 1, getInstance().nextInt(i));
            }
        } else {
            Object[] arr = list.toArray();

            for (int i = size; i > 1; i--) {
                swap(arr, i - 1, getInstance().nextInt(i));
            }

            ListIterator<?> it = list.listIterator();
            for (Object e : arr) {
                it.next();
                ((ListIterator) it).set(e);
            }
        }
    }

    private static void swap(List<?> list, int i, int j) {
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
        int length = 10_000_000;
        long seed = 392949394L;

        RandomUtil.getInstance().setSeed(seed);
        List<Double> d1 = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            d1.add(RandomUtil.getInstance().nextDouble());
        }

        RandomUtil.getInstance().setSeed(seed);
        List<Double> d2 = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            d2.add(RandomUtil.getInstance().nextDouble());
        }

        boolean deterministic = d1.equals(d2);
        System.out.println(deterministic ? "Deterministic" : "Not deterministic");
    }

    public static void main(String[] args) {
        testDeterminism();
    }

    /**
     * Generates a random integer between 0 (inclusive) and the specified value (exclusive).
     *
     * @param n the upper bound (exclusive) for the random number; must be greater than 0
     * @return a random integer between 0 (inclusive) and n (exclusive)
     * @throws IllegalArgumentException if n is less than or equal to 0
     */
    public int nextInt(int n) {
        return this.randomGenerator.nextInt(n);
    }

    /**
     * Generates the next pseudorandom integer.
     *
     * @return A pseudorandom integer produced by the internal random number generator.
     */
    public int nextInt() {
        return this.randomGenerator.nextInt();
    }

    /**
     * Generates the next pseudorandom double value between 0.0 (inclusive) and 1.0 (exclusive)
     * from the underlying random number generator.
     *
     * @return a pseudorandom double value between 0.0 (inclusive) and 1.0 (exclusive)
     */
    public double nextDouble() {
        return this.randomGenerator.nextDouble();
    }

    /**
     * Generates a random float value uniformly distributed in the range [0, 1).
     *
     * @return a random float value in the range [0, 1)
     */
    public float nextFloat() {
        return this.randomGenerator.nextFloat();
    }

    /**
     * Generates a random boolean value.
     *
     * @return a randomly generated boolean value. The result is either {@code true} or {@code false}.
     */
    public boolean nextBoolean() {
        return this.randomGenerator.nextBoolean();
    }

    /**
     * Generates a random double value uniformly distributed within the specified range [low, high].
     *
     * @param low the lower bound of the range (inclusive).
     * @param high the upper bound of the range (inclusive when low equals high).
     *             Must be greater than or equal to {@code low}.
     * @return a random double value within the range [low, high].
     * @throws IllegalArgumentException if {@code low > high}.
     */
    public double nextUniform(double low, double high) {
        if (low > high) {
            throw new IllegalArgumentException("Low must be <= high: low=" + low + ", high=" + high);
        }

        if (low == high) {
            return low;
        }

        return this.randomGenerator.nextDouble() * (high - low) + low;
    }

    /**
     * Generates a random value from a normal (Gaussian) distribution with the specified mean and standard deviation.
     *
     * @param mean the mean (or expectation) of the normal distribution.
     * @param sd the standard deviation of the normal distribution. Must be non-negative.
     * @return a randomly generated double value from the normal distribution with the given parameters.
     * @throws IllegalArgumentException if {@code sd} is negative.
     */
    public double nextGaussian(double mean, double sd) {
        if (sd < 0) {
            throw new IllegalArgumentException("Standard deviation must be non-negative: " + sd);
        }

        if (sd == 0) {
            return mean;
        }

        return new NormalDistribution(this.math3RandomGenerator, mean, sd).sample();
    }

    /**
     * Generates a random value from a truncated normal (Gaussian) distribution
     * with the specified mean, standard deviation, lower bound, and upper bound.
     * The generated value will be within the range [low, high].
     *
     * @param mean the mean (or expectation) of the normal distribution.
     * @param sd the standard deviation of the normal distribution. Must be non-negative.
     * @param low the lower bound of the truncation interval. Must be less than {@code high}.
     * @param high the upper bound of the truncation interval. Must be greater than {@code low}.
     * @return a randomly generated double value from the truncated normal distribution
     *         with the given parameters, constrained to the interval [low, high].
     * @throws IllegalArgumentException if {@code sd} is negative, or if {@code low >= high},
     *                                  or if {@code mean} is outside the interval [low, high]
     *                                  when {@code sd} is zero.
     */
    public double nextTruncatedNormal(double mean, double sd, double low, double high) {
        if (sd < 0) {
            throw new IllegalArgumentException("Standard deviation must be non-negative: " + sd);
        }

        if (low >= high) {
            throw new IllegalArgumentException("Low must be less than high.");
        }

        if (sd == 0) {
            if (mean < low || mean > high) {
                throw new IllegalArgumentException("Degenerate normal mean is outside truncation interval.");
            }
            return mean;
        }

        double d;
        do {
            d = nextGaussian(mean, sd);
        } while (d < low || d > high);

        return d;
    }

    /**
     * Resets the internal random number generator with the specified seed value,
     * ensuring deterministic behavior for subsequent random operations starting from this seed.
     *
     * @param seed the seed value to initialize the random generator. Must be a long value.
     */
    public void revertSeed(long seed) {
        this.randomGenerator = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
    }

    /**
     * Generates a random value sampled from a Poisson distribution with the specified mean (lambda).
     *
     * @param lambda the mean (expected value) of the Poisson distribution. Must be greater than 0.
     * @return a random double value sampled from the Poisson distribution with the specified mean (lambda).
     * @throws IllegalArgumentException if {@code lambda} is not greater than 0.
     */
    public double nextPoisson(double lambda) {
        if (!(lambda > 0.0)) {
            throw new IllegalArgumentException("Lambda must be > 0: " + lambda);
        }

        return new PoissonDistribution(this.math3RandomGenerator, lambda, 1.0E-12D, 100000).sample();
    }

    /**
     * Computes the value of the Probability Density Function (PDF) of a normal (Gaussian)
     * distribution with the specified mean and standard deviation at the given value.
     *
     * @param mean the mean (or expectation) of the normal distribution
     * @param sd the standard deviation of the normal distribution. Must be greater than 0
     * @param value the point at which the PDF is evaluated
     * @return the value of the normal distribution's PDF at the given point
     * @throws IllegalArgumentException if {@code sd} is not greater than 0
     */
    public double normalPdf(double mean, double sd, double value) {
        if (!(sd > 0.0)) {
            throw new IllegalArgumentException("Standard deviation must be > 0: " + sd);
        }

        return new NormalDistribution(this.math3RandomGenerator, mean, sd).density(value);
    }

    /**
     * Computes the cumulative distribution function (CDF) of a normal (Gaussian) distribution
     * with the specified mean and standard deviation at the given value.
     *
     * @param mean the mean (or expectation) of the normal distribution.
     * @param sd the standard deviation of the normal distribution. Must be greater than 0.
     * @param value the point at which the CDF is evaluated.
     * @return the cumulative probability of the normal distribution at the given point.
     * @throws IllegalArgumentException if {@code sd} is not greater than 0.
     */
    public double normalCdf(double mean, double sd, double value) {
        if (!(sd > 0.0)) {
            throw new IllegalArgumentException("Standard deviation must be > 0: " + sd);
        }

        return new NormalDistribution(0, 1).cumulativeProbability((value - mean) / sd);
    }

    /**
     * Generates a random value sampled from a Beta distribution with the specified shape parameters.
     *
     * @param alpha the first shape parameter of the Beta distribution. Must be greater than 0.
     * @param beta the second shape parameter of the Beta distribution. Must be greater than 0.
     * @return a random double value sampled from the Beta(alpha, beta) distribution.
     * @throws IllegalArgumentException if {@code alpha <= 0} or {@code beta <= 0}.
     */
    public double nextBeta(double alpha, double beta) {
        if (!(alpha > 0.0) || !(beta > 0.0)) {
            throw new IllegalArgumentException("Alpha and beta must both be > 0: alpha=" + alpha + ", beta=" + beta);
        }

        return new BetaDistribution(this.math3RandomGenerator, alpha, beta).sample();
    }

    /**
     * Generates a random value sampled from a Student's t-distribution with the specified degrees of freedom.
     *
     * @param df the degrees of freedom for the t-distribution. Must be greater than 0.
     * @return a random double value sampled from the t-distribution with the given degrees of freedom.
     * @throws IllegalArgumentException if {@code df <= 0}.
     */
    public double nextT(double df) {
        if (!(df > 0.0)) {
            throw new IllegalArgumentException("Degrees of freedom must be > 0: " + df);
        }

        return new TDistribution(this.math3RandomGenerator, df).sample();
    }

    /**
     * Generates a random value following an exponential distribution with the specified scale parameter.
     *
     * @param scale the scale parameter of the exponential distribution; must be greater than 0
     * @return a randomly generated value from the exponential distribution
     * @throws IllegalArgumentException if the scale parameter is not greater than 0
     */
    public double nextExponential(double scale) {
        if (!(scale > 0.0)) {
            throw new IllegalArgumentException("Scale must be > 0: " + scale);
        }

        return new ExponentialDistribution(this.math3RandomGenerator, scale).sample();
    }

    /**
     * Generates a random sample from the Gumbel distribution with the specified location and scale parameters.
     *
     * @param mu the location parameter of the Gumbel distribution
     * @param beta the scale parameter of the Gumbel distribution; must be greater than 0
     * @return a random sample from the Gumbel distribution
     * @throws IllegalArgumentException if the scale parameter beta is not greater than 0
     */
    public double nextGumbel(double mu, double beta) {
        if (!(beta > 0.0)) {
            throw new IllegalArgumentException("Beta must be > 0: " + beta);
        }

        return new GumbelDistribution(this.math3RandomGenerator, mu, beta).sample();
    }

    /**
     * Generates a random value from a chi-squared distribution with the specified degrees of freedom.
     *
     * @param df The degrees of freedom for the chi-squared distribution. Must be greater than zero.
     * @return A random value sampled from the chi-squared distribution.
     * @throws IllegalArgumentException If the degrees of freedom (df) is less than or equal to zero.
     */
    public double nextChiSquare(double df) {
        if (!(df > 0.0)) {
            throw new IllegalArgumentException("Degrees of freedom must be > 0: " + df);
        }

        return new ChiSquaredDistribution(this.math3RandomGenerator, df).sample();
    }

    /**
     * Generates a random value following a Gamma distribution parameterized
     * by the given shape and scale.
     *
     * @param shape the shape parameter of the Gamma distribution; must be greater than 0
     * @param scale the scale parameter of the Gamma distribution; must be greater than 0
     * @return a random value sampled from the Gamma distribution
     * @throws IllegalArgumentException if the shape or scale parameter is not greater than 0
     */
    public double nextGamma(double shape, double scale) {
        if (!(shape > 0.0) || !(scale > 0.0)) {
            throw new IllegalArgumentException("Shape and scale must both be > 0: shape=" + shape + ", scale=" + scale);
        }

        return new GammaDistribution(this.math3RandomGenerator, shape, scale).sample();
    }

    /**
     * Sets the seed to the given value.
     *
     * @param seed A long value. Once this seed is set, behavior is deterministic.
     */
    public void setSeed(long seed) {
        this.randomGenerator = RandomSource.XO_RO_SHI_RO_128_PP.create(seed);
    }

    /**
     * Retrieves the RandomGenerator instance being used.
     *
     * @return the RandomGenerator instance maintained by this class
     */
    public RandomGenerator getRandomGenerator() {
        return this.math3RandomGenerator;
    }

    /**
     * Generates and returns the next pseudorandom long value from the underlying random number generator.
     *
     * @return a pseudorandom long value generated by the random number generator.
     */
    public long nextLong() {
        return this.randomGenerator.nextLong();
    }

    /**
     * Fills the given byte array with random bytes.
     *
     * @param bytes target array
     */
    public void nextBytes(byte[] bytes) {
        this.randomGenerator.nextBytes(bytes);
    }

    /**
     * Generates the next pseudorandom, Gaussian ("normally") distributed
     * double value with mean 0.0 and standard deviation 1.0 from this random
     * number generator's sequence.
     *
     * @return a pseudorandom, Gaussian-distributed double value with mean 0.0
     *         and standard deviation 1.0
     */
    public double nextGaussian() {
        return this.math3RandomGenerator.nextGaussian();
    }
}