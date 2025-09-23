/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper class for {@link IndependenceTest} that enforces False Discovery Rate (FDR) control on independence
 * decisions. This class uses either Benjamini-Hochberg (BH) or Benjamini-Yekutieli (BY) FDR control methods and
 * supports both global and stratified FDR control across conditioning sets.
 * <p>
 * The workflow is divided into two phases: 1. Recording Epoch: Raw p-values for independence tests are cached from the
 * underlying test. 2. Decision Epoch: Enforces FDR-controlled cutoffs based on cached p-values.
 * <p>
 * The cutoffs can be computed globally or within groups based on the cardinality of the conditioning set (|Z|).
 * <p>
 * This class also maintains a mechanism to track "mind-changes," i.e., decisions that change between subsequent
 * algorithm passes.
 * <p>
 * The wrapper allows for integrating FDR-controlled independence testing into iterative search algorithms without
 * re-computing p-values across epochs, ensuring reproducibility and efficiency.
 */
public final class IndTestFdrWrapper implements IndependenceTest {
    private final IndependenceTest base;
    private final double alpha;
    private final double fdrQ;             // target FDR level, e.g., 0.05
    private final Map<IndependenceFact, Double> pvals = new ConcurrentHashMap<>();
    private Map<IndependenceFact, Boolean> lastDecisions = new HashMap<>();
    private double alphaStar = Double.NaN;
    private boolean negativelyCorrelated = false;

    /**
     * Constructs an instance of IndTestFdrWrapper, which wraps around an existing {@link IndependenceTest} to apply
     * False Discovery Rate (FDR) control during independence testing. The wrapper enforces an FDR threshold using the
     * given parameters, allowing for control over the proportion of false positives in the testing process.
     *
     * @param base                 The underlying {@link IndependenceTest} object to be wrapped, which performs the
     *                             actual independence tests.
     * @param negativelyCorrelated A flag indicating whether to focus on negatively correlated variables during the FDR
     *                             process.
     * @param alpha                The base significance level for the independence tests. Must be in the range [0, 1].
     * @param fdrQ                 The FDR threshold parameter, representing the desired upper bound on the proportion
     *                             of false discoveries. Must be in the range [0, 1].
     * @throws NullPointerException     If the base independence test is null.
     * @throws IllegalArgumentException If the alpha is not in the range [0, 1] or the fdrQ is not in the range [0, 1].
     */
    public IndTestFdrWrapper(IndependenceTest base, boolean negativelyCorrelated, double alpha, double fdrQ) {
        this.base = Objects.requireNonNull(base);
        if (!(alpha >= 0 && alpha <= 1)) throw new IllegalArgumentException("Alpha must be in (0,1)");
        if (!(fdrQ >= 0 && fdrQ <= 1)) throw new IllegalArgumentException("FDR q must be in (0,1)");
        this.alpha = alpha;
        this.fdrQ = fdrQ;
        this.negativelyCorrelated = negativelyCorrelated;
        base.setVerbose(false);
    }

    /**
     * Executes a loop for controlling the false discovery rate (FDR) as part of a graph search process. The method
     * iteratively adjusts the FDR threshold (alphaStar) based on accumulated p-values from independence tests and
     * continues until the number of changes (new facts or flips) between epochs falls below a specified threshold or
     * the maximum number of epochs is reached.
     *
     * @param search               The graph search instance used for discovering dependencies and independencies.
     * @param negativelyCorrelated A flag indicating whether to consider only negatively correlated variables.
     * @param alpha                The base significance level for independence tests.
     * @param fdrQ                 The false discovery rate (FDR) threshold parameter.
     * @param verbose              A flag to control whether detailed logs should be displayed during the process.
     * @return The resulting graph discovered after applying the FDR control mechanism.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    public static Graph doFdrLoop(IGraphSearch search, boolean negativelyCorrelated, double alpha, double fdrQ, boolean verbose) throws InterruptedException {
        IndependenceTest test = null;
        try {
            test = search.getTest();
        } catch (Exception e) {
            throw new IllegalStateException("For FDR to work, the search must be able to return a test. If this\n" + "is a constraint-based algroithm, please complain to the developers\n" + "because they did not complete their work. Be very mean about it :-).");
        }

        IndTestFdrWrapper wrap = new IndTestFdrWrapper(test, negativelyCorrelated, alpha, fdrQ);
        wrap.setVerbose(verbose);
        search.setTest(wrap);

        final int maxEpochs = 5;
        final int tauChanges = 0;      // stop when â¤ this many changes

        Graph g = null;

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            // Epoch 0: alphaStar is NaN â wrapper uses base alpha
            // Epoch â¥1: wrapper uses the alphaStar computed in the previous iteration
            g = search.search();

            // Recompute Î±* from ALL p-values gathered so far
            wrap.computeAlphaStar();

            // Count changes vs. last epoch (new facts or flips both count)
            int changes = wrap.countMindChanges();
            String s = String.format("FDR epoch %d: %s | mind-changes=%d", epoch, wrap.cutoffsSummary(), changes);
            TetradLogger.getInstance().log(s);

            if (changes <= tauChanges) break;
        }
        return g;
    }

    /* ===== Epoch control ===== */

    /**
     * Computes the adjusted alpha threshold (alphaStar) for controlling the False Discovery Rate (FDR) during
     * independence tests. This method collects the p-values for all tested hypotheses, applies an FDR cutoff algorithm
     * using the specified FDR threshold (fdrQ) and the negatively correlated parameter, and updates the alphaStar field
     * with the newly computed value.
     * <p>
     * This adjustment ensures that the proportion of false discoveries among rejected hypotheses is controlled
     * according to the specified FDR threshold.
     * <p>
     * The p-values are retrieved from the `pvals` field, which maps independence facts to their p-values. The computed
     * alphaStar value is stored for subsequent use in decision-making processes.
     */
    public void computeAlphaStar() {

        // Allow duplicate p-values.
        List<Double> pValues = new ArrayList<>();

        for (var fact : pvals.keySet()) {
            pValues.add(pvals.get(fact));
        }

        this.alphaStar = StatUtils.fdrCutoff(fdrQ, pValues, negativelyCorrelated);
    }

    /**
     * Returns the number of mind-changes between the previous decision epoch and the current one. Call this AFTER you
     * complete an algorithm pass using this wrapper in decision mode.
     *
     * @return This number.
     */
    public int countMindChanges() {
        if (Double.isNaN(alphaStar)) return 0;

        int changes = 0;
        Map<IndependenceFact, Boolean> current = new HashMap<>();

        for (var e : pvals.entrySet()) {
            var fact = e.getKey();
            boolean indep = decideIndependenceFromCutoff(e.getValue());
            current.put(fact, indep);

            Boolean prev = (lastDecisions == null) ? null : lastDecisions.get(fact);
            if (prev == null || prev != indep) changes++;   // count new & flipped
        }
        lastDecisions = current;   // snapshot for next epoch
        return changes;
    }

    /**
     * Checks the independence of two nodes given a conditioning set of nodes. This method uses cached p-values to
     * optimize redundant independence tests. If a p-value is not available in the cache, it computes it using the
     * underlying independence test and then stores the result for future use.
     *
     * @param x The first node being tested for independence.
     * @param y The second node being tested for independence.
     * @param z The set of nodes conditioned upon, representing the known variables.
     * @return An {@link IndependenceResult} object containing the independence determination, the corresponding
     * p-value, and the difference between the alpha threshold and the p-value.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceFact fact = new IndependenceFact(x, y, z);

        // get (or compute) raw p
        Double p = pvals.get(fact);
        if (p == null) {
            IndependenceResult r = base.checkIndependence(x, y, z);
            p = r.getPValue();
            pvals.put(fact, p);
        }

        double alphaUsed = Double.isNaN(alphaStar) ? alpha : alphaStar;

        boolean indep = p > alphaUsed;
        return new IndependenceResult(fact, indep, p, alphaUsed - p);
    }

    /* ===== IndependenceTest implementation ===== */

    /**
     * Retrieves a list of variables involved in the independence tests.
     *
     * @return A list of {@link Node} objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    /**
     * Retrieves the data model associated with the wrapped {@link IndependenceTest}.
     *
     * @return A {@link DataModel} object representing the data model used in the underlying independence test.
     */
    @Override
    public DataModel getData() {
        return base.getData();
    }

    /**
     * Determines whether verbose output is enabled in the underlying independence test wrapped by this instance.
     *
     * @return true if verbose output is enabled; false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return base.isVerbose();
    }

    /**
     * Sets the verbose output flag for the underlying independence test.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        base.setVerbose(verbose);
    }

    /**
     * Retrieves the sample size used in the underlying independence test.
     *
     * @return The sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    /**
     * Retrieves the data sets used in the underlying independence test.
     *
     * @return A list of {@link DataSet} objects representing the data sets.
     */
    @Override
    public List<DataSet> getDataSets() {
        return base.getDataSets();
    }

    /**
     * Determines whether the given p-value indicates independence based on the computed alphaStar threshold.
     *
     * @param p The p-value to evaluate for independence.
     * @return True if the p-value is greater than or equal to the alphaStar threshold, indicating independence; false otherwise.
     */
    private boolean decideIndependenceFromCutoff(double p) {
        return p >= (Double.isNaN(alphaStar) ? alpha : alphaStar);
    }

    /**
     * Provides a summary of the current FDR cutoff parameters in a formatted string.
     *
     * @return A string summarizing the false discovery rate (FDR) parameters including whether the data is negatively
     * or positively correlated, the FDR q-value, the number of variables (m), and the computed alpha threshold (α*).
     */
    public String cutoffsSummary() {
        int m = pvals.size();
        return String.format(Locale.US, "FDR[%s] q=%.3g  m=%d  â  Î±* = %.6f", negativelyCorrelated ? "negatively correlated" : "positively correlated", fdrQ, m, alphaStar);
    }

    /* ===== Helpers ===== */

    private double getAlphaStarFor(IndependenceFact fact) {
        return (Double.isNaN(alphaStar) ? 1.0 : alphaStar);
    }
}
