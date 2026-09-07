/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements the FLOP (Fast Learning of Order and Parents) algorithm. The reference is this:
 * <p>
 * Wienobst, M., Henckel, L., &amp; Weichwald, S. (2026). Embracing Discrete Search: A Reasonable Approach to Causal
 * Structure Learning. International Conference on Learning Representations.
 * <p>
 * FLOP is a score-based structure learning algorithm for linear additive noise models. Like BOSS (see), it performs a
 * reinsertion-based local search over topological orders of DAGs, selecting parents for each order by grow-shrink.
 * It differs from BOSS in the following respects:
 * <ol>
 *     <li>Grow-shrink is non-greedy (any improving insertion or deletion is accepted) and is warm-started from the
 *     parent set learned for the previous prefix, which changes by at most one variable per search move. This
 *     replaces the grow-shrink tree caching used by BOSS.</li>
 *     <li>The initial order is constructed so that strongly correlated variables are adjacent (via a pivoted
 *     Cholesky decomposition of the correlation matrix), which avoids grow-shrink failures on weakly dependent
 *     far-apart ancestor-descendant pairs (path graphs are the canonical hard case).</li>
 *     <li>An iterated local search (ILS) metaheuristic perturbs the best-found order with about ln(p) random
 *     transpositions and restarts the local search, trading additional compute for better BIC optimization. More
 *     restarts can never yield a worse-scoring graph.</li>
 * </ol>
 * <p>
 * This implementation is specialized to the linear Gaussian BIC computed from the correlation matrix of the data
 * (the data are effectively standardized, so the algorithm is scale-invariant). It therefore takes a continuous
 * dataset or covariance matrix directly rather than a Score object. The penalty discount plays the role of the
 * lambda parameter of the paper, with 2 the recommended default (Foygel &amp; Drton, 2010).
 * <p>
 * Knowledge is not currently supported by this algorithm.
 * <p>
 * This class is not thread safe.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Boss
 * @see edu.cmu.tetrad.search.utils.FlopInitialOrder
 */
public class Flop {

    /**
     * The correlation matrix of the data.
     */
    private final CorrelationMatrix correlations;

    /**
     * The variables.
     */
    private final List<Node> variables;

    /**
     * The penalty discount (lambda in the paper).
     */
    private double penaltyDiscount = 2.0;

    /**
     * The number of ILS restarts.
     */
    private int numRestarts = 0;

    /**
     * The random seed for the ILS perturbations, or -1 for a nondeterministic seed.
     */
    private long seed = -1L;

    /**
     * Whether to log progress.
     */
    private boolean verbose = false;

    /**
     * Whether the grow and shrink phases visit candidates in random order.
     */
    private boolean randomizeGrowShrink = true;

    /**
     * Whether the grow and shrink phases accept ties.
     */
    private boolean acceptTies = true;

    /**
     * The index of the last ILS restart that improved on the incumbent in the most recent search, or -1 before any
     * search has been run.
     */
    private int lastImprovementRestart = -1;

    /**
     * Constructs a FLOP search over the given continuous dataset. The dataset is reduced to its correlation matrix.
     *
     * @param dataSet A continuous dataset.
     */
    public Flop(DataSet dataSet) {
        this(new CorrelationMatrix(dataSet));
    }

    /**
     * Constructs a FLOP search over the given covariance matrix. The matrix is converted to a correlation matrix, so
     * the search is invariant to the scales of the variables.
     *
     * @param covariances A covariance (or correlation) matrix.
     */
    public Flop(ICovarianceMatrix covariances) {
        this.correlations = (covariances instanceof CorrelationMatrix c) ? c : new CorrelationMatrix(covariances);
        this.variables = this.correlations.getVariables();
    }

    /**
     * Runs the search and returns the estimated CPDAG.
     *
     * @return The CPDAG of the best-scoring DAG found.
     * @throws InterruptedException If the thread is interrupted.
     */
    public Graph search() throws InterruptedException {
        int p = this.variables.size();
        int n = this.correlations.getSampleSize();

        double[][] r = new double[p][p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                r[i][j] = this.correlations.getValue(i, j);
            }
        }

        FlopCore core = new FlopCore(r, n, this.penaltyDiscount);
        core.setRandomizeGrowShrink(this.randomizeGrowShrink);
        core.setAcceptTies(this.acceptTies);

        FlopCore.Result result = core.search(this.numRestarts, this.seed,
                this.verbose ? s -> TetradLogger.getInstance().log(s) : null);

        this.lastImprovementRestart = result.lastImprovementRestart();

        Map<Node, Set<Node>> parents = new HashMap<>();

        for (int v = 0; v < p; v++) {
            Set<Node> pa = new HashSet<>();
            for (int u : result.parents().get(v)) pa.add(this.variables.get(u));
            parents.put(this.variables.get(v), pa);
        }

        if (this.verbose) {
            TetradLogger.getInstance().log("FLOP: final BIC score (smaller is better) = " + result.score());
        }

        return PermutationSearch.getGraph(this.variables, parents, true);
    }

    /**
     * Sets the penalty discount, the multiplier on the ln(n) * |parents| BIC penalty term (lambda in the FLOP
     * paper). The default is 2.
     *
     * @param penaltyDiscount The penalty discount; must be positive.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("Penalty discount must be positive.");
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets the number of iterated local search (ILS) restarts. Zero gives a single local search from the principled
     * initial order; each restart perturbs the best-found order with about ln(p) random transpositions and reruns
     * the local search, keeping the best result. The default is 0.
     *
     * @param numRestarts The number of restarts; must be nonnegative.
     */
    public void setNumRestarts(int numRestarts) {
        if (numRestarts < 0) throw new IllegalArgumentException("Number of restarts must be nonnegative.");
        this.numRestarts = numRestarts;
    }

    /**
     * Sets the random seed used for the ILS perturbations, for reproducibility.
     *
     * @param seed The seed, or -1 for a nondeterministic seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets whether progress should be logged.
     *
     * @param verbose True to log progress.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether the grow and shrink phases of the local search visit candidates in a random order, as in the
     * authors' reference implementation, rather than in the index order of the current permutation. Index order is
     * a biased scan, and it makes the local search a deterministic function of the permutation it is given, which
     * means an ILS restart whose perturbed order drains back into the incumbent's basin returns the incumbent graph
     * unchanged. The default is true.
     *
     * @param randomizeGrowShrink True to shuffle.
     */
    public void setRandomizeGrowShrink(boolean randomizeGrowShrink) {
        this.randomizeGrowShrink = randomizeGrowShrink;
    }

    /**
     * Sets whether the grow and shrink phases accept a candidate whose local score ties the incumbent, rather than
     * requiring strict improvement. This matches the authors' comparison operators, though exact ties between
     * distinct parent sets are rare in continuous data. The default is true.
     *
     * @param acceptTies True to accept ties.
     */
    public void setAcceptTies(boolean acceptTies) {
        this.acceptTies = acceptTies;
    }

    /**
     * Returns the index of the last ILS restart that improved on the incumbent in the most recent search. Zero means
     * no restart improved on the initial local search; a value well below the restart count means the restart loop
     * saturated and the remaining restarts were wasted.
     *
     * @return The last improving restart index, or -1 if no search has been run.
     */
    public int getLastImprovementRestart() {
        return this.lastImprovementRestart;
    }
}
