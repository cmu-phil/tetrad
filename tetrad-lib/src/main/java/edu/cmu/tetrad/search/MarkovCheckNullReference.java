package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.TrainedDagSimulatorGNM;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.Arrays;
import java.util.function.Function;

/**
 * <h2>Parametric-bootstrap null reference for the Markov check</h2>
 *
 * <p>A Markov check that fails under a strict independence test is ambiguous between two very
 * different situations: (a) the <em>test</em> is miscalibrated at this sample size and data scale,
 * so its p-values are non-uniform even when the implied independencies hold, and (b) the
 * <em>model</em> is genuinely inadequate, i.e., the graph implies independencies the data violates.
 * This class separates them experimentally: it fits a simulator to (data, DAG), simulates
 * {@code numDraws} datasets that satisfy the DAG's factorization <em>by construction</em>, runs the
 * identical Markov check on each, and reports where the real data's check statistic falls in that
 * null distribution.</p>
 *
 * <h3>Simulators</h3>
 * <ul>
 *   <li>{@link SimulatorType#TRAINED_DAG_GNM} -- {@link TrainedDagSimulatorGNM}: neural local
 *       mechanisms with bootstrapped noise, anchored to the observed dataset. The preferred null:
 *       it probes the test's behavior under realistic conditionals, so a real-data failure that
 *       the null draws do not reproduce is attributable to the graph/model, not the test.</li>
 *   <li>{@link SimulatorType#LINEAR_SEM} -- a linear-Gaussian SEM fitted by {@link SemEstimator}.
 *       A clean in-class null, useful as a test-calibration baseline: if the check fails even on
 *       these draws, suspect the test before the model.</li>
 * </ul>
 *
 * <h3>Reading the result</h3>
 * <p>{@link Result#getEmpiricalP()} is the fraction of null draws whose Anderson-Darling
 * uniformity p-value (over independence facts) is at or below the real data's value; its
 * resolution is 1/numDraws. Real fails while the GNM null passes: genuine graph/model inadequacy.
 * GNM null also fails: test miscalibration under realistic conditionals, or simulator leakage --
 * compare with the linear null to separate those. Null values clustered near 1: a broken
 * simulator (lost sampling variation), since under a healthy null the statistic should be roughly
 * uniform. This class reports statistics only; decisions about the model belong to the user.</p>
 *
 * <h3>Caveats</h3>
 * <ul>
 *   <li>The reference inherits the Markov check's one-sidedness: it certifies the implied
 *       independencies, not the dependencies missing edges fail to imply. It sharpens "fails
 *       Markov" into a diagnosis; it does not turn "passes Markov" into an endorsement.</li>
 *   <li>With bootstrapped (unstratified) residuals for continuous children, parent-dependent
 *       noise scale in the real data may not be fully reproduced in the null draws, so part of a
 *       real-data failure may reflect variance structure rather than graph structure.</li>
 *   <li>If the checked graph implies no independence facts, the Anderson-Darling statistics are
 *       undefined and the empirical p is reported as NaN.</li>
 *   <li>For {@link SimulatorType#LINEAR_SEM}, draws use the shared {@link RandomUtil} stream; the
 *       base seed is applied to it once before the draws (a global side effect).</li>
 * </ul>
 */
public final class MarkovCheckNullReference {

    /**
     * The simulator used to generate null draws from the fitted (data, DAG) model.
     */
    public enum SimulatorType {

        /**
         * {@link TrainedDagSimulatorGNM}: neural local mechanisms with bootstrapped noise.
         */
        TRAINED_DAG_GNM,

        /**
         * A linear-Gaussian SEM fitted with {@link SemEstimator}.
         */
        LINEAR_SEM
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private MarkovCheckNullReference() {
    }

    /**
     * Computes the null reference using a test factory.
     *
     * @param data                The observed dataset.
     * @param graph               The graph whose Markov property is checked. A CPDAG is converted
     *                            to a member DAG via {@link GraphTransforms#dagFromCpdag(Graph)};
     *                            the DAG actually checked is available from the result.
     * @param testFactory         Creates the Markov-check independence test for a given dataset;
     *                            must produce the same test configuration used on the real data.
     * @param conditioningSetType The conditioning-set type for the Markov check.
     * @param simulator           The null-draw simulator.
     * @param numDraws            The number of null draws (empirical-p resolution is 1/numDraws).
     * @param sampleSize          Rows per simulated dataset; nonpositive means the size of
     *                            {@code data}.
     * @param baseSeed            Base seed; draw b uses seed {@code baseSeed + b} (GNM), or the
     *                            seed is applied once to {@link RandomUtil} (linear).
     * @param gnmParams           Parameters for the GNM simulator; null means defaults. Ignored
     *                            for the linear simulator.
     * @return The {@link Result}.
     */
    public static Result compute(DataSet data, Graph graph,
                                 Function<DataSet, IndependenceTest> testFactory,
                                 ConditioningSetType conditioningSetType,
                                 SimulatorType simulator, int numDraws, int sampleSize,
                                 long baseSeed, TrainedDagSimulatorGNM.Params gnmParams) {
        if (numDraws < 1) throw new IllegalArgumentException("numDraws must be >= 1: " + numDraws);

        Graph dag = graph.paths().isLegalDag() ? graph : GraphTransforms.dagFromCpdag(graph);
        int n = sampleSize > 0 ? sampleSize : data.getNumRows();

        double[] real = check(data, dag, testFactory, conditioningSetType);

        long t0 = System.currentTimeMillis();
        Function<Long, DataSet> draw;

        if (simulator == SimulatorType.TRAINED_DAG_GNM) {
            TrainedDagSimulatorGNM.Params params =
                    gnmParams != null ? gnmParams : new TrainedDagSimulatorGNM.Params();
            TrainedDagSimulatorGNM model = new TrainedDagSimulatorGNM(data, dag, params);
            model.fit();
            draw = seed -> model.simulate(n, seed).toDataSet();
        } else {
            SemIm im = new SemEstimator(data, new SemPm(dag)).estimate();
            RandomUtil.getInstance().setSeed(baseSeed);
            draw = seed -> {
                try {
                    return im.simulateData(n, false);
                } catch (java.text.ParseException e) {
                    throw new RuntimeException("Unexpected parse exception simulating from the "
                            + "fitted linear SEM.", e);
                }
            };
        }

        long fitMillis = System.currentTimeMillis() - t0;

        double[] nullAdInd = new double[numDraws];
        double[] nullFracDep = new double[numDraws];
        long simulateMillis = 0, checkMillis = 0;

        for (int b = 0; b < numDraws; b++) {
            t0 = System.currentTimeMillis();
            DataSet sim = draw.apply(baseSeed + b);
            simulateMillis += System.currentTimeMillis() - t0;

            t0 = System.currentTimeMillis();
            double[] stats = check(sim, dag, testFactory, conditioningSetType);
            checkMillis += System.currentTimeMillis() - t0;

            nullAdInd[b] = stats[0];
            nullFracDep[b] = stats[1];
        }

        return new Result(dag, simulator, numDraws, real[0], real[1], nullAdInd, nullFracDep,
                fitMillis, simulateMillis, checkMillis);
    }

    /**
     * Convenience overload taking an {@link IndependenceWrapper} and {@link Parameters}, matching
     * how the Markov-check test is configured in algcomparison and in py-tetrad's TetradSearch.
     *
     * @param data                The observed dataset.
     * @param graph               The graph whose Markov property is checked (see the primary
     *                            overload for CPDAG handling).
     * @param testWrapper         The independence-test wrapper for the Markov-check test.
     * @param parameters          The parameters used to instantiate the test.
     * @param conditioningSetType The conditioning-set type for the Markov check.
     * @param simulator           The null-draw simulator.
     * @param numDraws            The number of null draws.
     * @param sampleSize          Rows per simulated dataset; nonpositive means the size of
     *                            {@code data}.
     * @param baseSeed            Base seed for the draws.
     * @param gnmParams           Parameters for the GNM simulator; null means defaults.
     * @return The {@link Result}.
     */
    public static Result compute(DataSet data, Graph graph, IndependenceWrapper testWrapper,
                                 Parameters parameters, ConditioningSetType conditioningSetType,
                                 SimulatorType simulator, int numDraws, int sampleSize,
                                 long baseSeed, TrainedDagSimulatorGNM.Params gnmParams) {
        return compute(data, graph, d -> testWrapper.getTest(d, parameters), conditioningSetType,
                simulator, numDraws, sampleSize, baseSeed, gnmParams);
    }

    /**
     * Runs the Markov check on one dataset; returns {ad_ind, fraction of independence facts judged
     * dependent}.
     */
    private static double[] check(DataSet data, Graph dag,
                                  Function<DataSet, IndependenceTest> testFactory,
                                  ConditioningSetType conditioningSetType) {
        MarkovCheck mc = new MarkovCheck(dag, testFactory.apply(data), conditioningSetType);
        mc.setParallelized(true);
        mc.generateAllResults();
        return new double[]{mc.getAndersonDarlingP(true), mc.getFractionDependent(true)};
    }

    /**
     * The real-data check statistics and the null distributions, with empirical p-values and
     * component timings.
     */
    public static final class Result {

        private final Graph dag;
        private final SimulatorType simulator;
        private final int numDraws;
        private final double realAdInd;
        private final double realFractionDependent;
        private final double[] nullAdInd;
        private final double[] nullFractionDependent;
        private final long fitMillis;
        private final long simulateMillis;
        private final long checkMillis;

        private Result(Graph dag, SimulatorType simulator, int numDraws, double realAdInd,
                       double realFractionDependent, double[] nullAdInd,
                       double[] nullFractionDependent, long fitMillis, long simulateMillis,
                       long checkMillis) {
            this.dag = dag;
            this.simulator = simulator;
            this.numDraws = numDraws;
            this.realAdInd = realAdInd;
            this.realFractionDependent = realFractionDependent;
            this.nullAdInd = nullAdInd;
            this.nullFractionDependent = nullFractionDependent;
            this.fitMillis = fitMillis;
            this.simulateMillis = simulateMillis;
            this.checkMillis = checkMillis;
        }

        /**
         * The DAG actually checked (after any CPDAG-to-DAG conversion).
         *
         * @return The DAG.
         */
        public Graph getDag() {
            return dag;
        }

        /**
         * The simulator used for the null draws.
         *
         * @return The simulator type.
         */
        public SimulatorType getSimulator() {
            return simulator;
        }

        /**
         * The number of null draws.
         *
         * @return The number of draws.
         */
        public int getNumDraws() {
            return numDraws;
        }

        /**
         * The real data's Anderson-Darling uniformity p-value over independence facts.
         *
         * @return The value, or NaN if the graph implies no independence facts.
         */
        public double getRealAdInd() {
            return realAdInd;
        }

        /**
         * The real data's fraction of independence facts judged dependent.
         *
         * @return The fraction.
         */
        public double getRealFractionDependent() {
            return realFractionDependent;
        }

        /**
         * The null draws' Anderson-Darling uniformity p-values.
         *
         * @return A copy of the array, one value per draw.
         */
        public double[] getNullAdInd() {
            return Arrays.copyOf(nullAdInd, nullAdInd.length);
        }

        /**
         * The null draws' fractions of independence facts judged dependent.
         *
         * @return A copy of the array, one value per draw.
         */
        public double[] getNullFractionDependent() {
            return Arrays.copyOf(nullFractionDependent, nullFractionDependent.length);
        }

        /**
         * The fraction of null draws with ad_ind at or below the real ad_ind. Small values mean
         * the real data is atypically bad for the null; resolution is 1/numDraws.
         *
         * @return The empirical p, or NaN if the real ad_ind is NaN.
         */
        public double getEmpiricalP() {
            if (Double.isNaN(realAdInd)) return Double.NaN;
            int count = 0;
            for (double v : nullAdInd) if (v <= realAdInd) count++;
            return count / (double) numDraws;
        }

        /**
         * The fraction of null draws whose rejection fraction is at or above the real one (large
         * fractions are bad, so the tail is on the high side).
         *
         * @return The empirical p, or NaN if the real fraction is NaN.
         */
        public double getEmpiricalPFraction() {
            if (Double.isNaN(realFractionDependent)) return Double.NaN;
            int count = 0;
            for (double v : nullFractionDependent) if (v >= realFractionDependent) count++;
            return count / (double) numDraws;
        }

        /**
         * Time spent fitting the simulator, in milliseconds.
         *
         * @return The time.
         */
        public long getFitMillis() {
            return fitMillis;
        }

        /**
         * Total time spent simulating the null draws, in milliseconds.
         *
         * @return The time.
         */
        public long getSimulateMillis() {
            return simulateMillis;
        }

        /**
         * Total time spent running the Markov checks on the null draws, in milliseconds.
         *
         * @return The time.
         */
        public long getCheckMillis() {
            return checkMillis;
        }

        /**
         * A report in the same format as the py-tetrad prototype.
         *
         * @return The report string.
         */
        public String toString() {
            double[] sorted = Arrays.copyOf(nullAdInd, nullAdInd.length);
            Arrays.sort(sorted);
            double median = quantile(sorted, 0.5);
            double q10 = quantile(sorted, 0.1);
            double q90 = quantile(sorted, 0.9);

            return String.format(
                    "Markov-check null reference (%s, B=%d)%n"
                    + "  real:  ad_ind = %.6f   frac facts rejected = %.3f%n"
                    + "  null:  ad_ind median %.4f  [q10 %.4f, q90 %.4f]  min %.4f%n"
                    + "  empirical p (ad_ind)        = %.3f%n"
                    + "  empirical p (frac rejected) = %.3f%n"
                    + "  time (s): fit %.1f, simulate %.1f, check %.1f",
                    simulator, numDraws, realAdInd, realFractionDependent,
                    median, q10, q90, sorted[0],
                    getEmpiricalP(), getEmpiricalPFraction(),
                    fitMillis / 1000.0, simulateMillis / 1000.0, checkMillis / 1000.0);
        }

        private static double quantile(double[] sorted, double q) {
            if (sorted.length == 0) return Double.NaN;
            double pos = q * (sorted.length - 1);
            int lo = (int) Math.floor(pos);
            int hi = (int) Math.ceil(pos);
            return sorted[lo] + (pos - lo) * (sorted[hi] - sorted[lo]);
        }
    }
}
