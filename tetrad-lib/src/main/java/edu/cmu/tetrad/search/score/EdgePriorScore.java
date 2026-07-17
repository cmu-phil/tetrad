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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.EdgePriors;

import java.util.List;
import java.util.Objects;

/**
 * Wraps a {@link Score} and adds an independent Bernoulli prior on each adjacency.
 *
 * <p>Placing an independent prior on each unordered pair {i, j}, with the adjacency present with
 * probability p_ij, gives (up to an additive constant that does not depend on the graph)
 *
 * <pre>
 *   log P(G) = sum over adjacencies {i,j} in G of beta_ij,   beta_ij = log(p_ij / (1 - p_ij)).
 * </pre>
 *
 * On the 2 log L scale that {@link SemBicScore} lives on, twice the log-posterior is therefore the
 * score plus 2 * sum(beta_ij) over present adjacencies, and this class adds the local share of
 * that term:
 *
 * <pre>
 *   s'(Y | S) = s(Y | S) + 2 * sum over X in S of beta_XY.
 * </pre>
 *
 * <p>The effect on the local decision is exactly a per-edge rebate on the BIC penalty: adding X to
 * the parents of Y is accepted iff
 *
 * <pre>
 *   -n log(1 - r^2_{XY.S}) &gt; lambda log(n) - 2 beta_XY,
 * </pre>
 *
 * so a prior favouring the edge (beta &gt; 0) lowers the toll and a prior against it raises the
 * toll. This is the classical structure prior of Heckerman, Geiger and Chickering (1995),
 * specialised to per-pair rather than uniform log-odds.
 *
 * <p><b>Two properties are preserved, and both matter.</b> First, decomposability: in a DAG each
 * adjacency is oriented exactly one way, so beta_XY is charged to exactly one local score, the
 * child's, and nothing is double counted. Second, score equivalence: the prior is a function of
 * the skeleton only, and all DAGs in a Markov equivalence class share a skeleton, so the prior
 * term is constant within a class. BOSS's permutation search and FGES's forward and backward
 * phases are therefore unaffected in their correctness arguments. Both properties fail for priors
 * on <i>oriented</i> edges, which is why {@link EdgePriors} refuses an asymmetric matrix.
 * Genuinely directional prior information belongs in background knowledge, not here.
 *
 * <p><b>Scale.</b> The factor of 2 assumes the delegate is on the 2 log L scale with penalty
 * lambda k log(n) per parameter, as {@link SemBicScore} is. A delegate carrying a different
 * constant multiple of log L needs beta rescaled by the same constant; the invariant to match is
 * the lambda log(n) toll per added parent.
 *
 * @author josephramsey
 * @see EdgePriors
 * @see edu.cmu.tetrad.search.test.EdgePriorTest
 */
public class EdgePriorScore implements Score {

    private final Score score;
    private final double[][] twoBeta;

    /**
     * Constructs a prior-adjusted score.
     *
     * @param score  The score to wrap; typically a {@link SemBicScore}.
     * @param priors Prior log-odds, keyed by variable name. Resolved against
     *               {@code score.getVariables()} here, once, so that the caller's matrix ordering
     *               is irrelevant.
     * @throws IllegalStateException    If {@code priors} does not hold log-odds.
     * @throws IllegalArgumentException If {@code priors} mentions variables the score does not
     *                                  have.
     */
    public EdgePriorScore(Score score, EdgePriors priors) {
        this.score = Objects.requireNonNull(score, "score");
        Objects.requireNonNull(priors, "priors");

        if (priors.getSemantics() != EdgePriors.Semantics.LOG_ODDS) {
            throw new IllegalStateException("EdgePriorScore takes prior log-odds, but was given "
                    + priors.getSemantics() + ". Weights are for the test"
                    + " (see EdgePriorTest); log-odds are for the score.");
        }

        List<Node> variables = score.getVariables();
        List<String> unmatched = priors.unmatchedNames(variables);

        if (!unmatched.isEmpty()) {
            throw new IllegalArgumentException("The prior mentions variables the score does not have: "
                    + unmatched + ". This usually means the prior was built"
                    + " against a different variable set.");
        }

        double[][] beta = priors.resolve(variables);
        int p = variables.size();
        this.twoBeta = new double[p][p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                this.twoBeta[i][j] = 2.0 * beta[i][j];
            }
        }

        for (int i = 0; i < p; i++) {
            this.twoBeta[i][i] = 0.0;
        }
    }

    /**
     * Returns the local score of a node given its parents, with the prior term added.
     *
     * @param node    The node.
     * @param parents The parents.
     * @return The score.
     */
    @Override
    public double localScore(int node, int... parents) {
        double s = this.score.localScore(node, parents);
        double[] row = this.twoBeta[node];

        for (int parent : parents) {
            s += row[parent];
        }

        return s;
    }

    /**
     * Returns the score difference for adding x to the parents of y given z.
     *
     * <p>Overridden rather than inherited so that the delegate's own optimised difference is used
     * where it has one. The result is identical to the interface default applied to
     * {@link #localScore(int, int...)}: every beta term for the parents in z cancels between the
     * two local scores, leaving exactly 2 beta_xy.
     *
     * @param x A node.
     * @param y The node.
     * @param z A set of nodes.
     * @return The score difference.
     * @throws InterruptedException If the operation is interrupted.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) throws InterruptedException {
        return this.score.localScoreDiff(x, y, z) + this.twoBeta[y][x];
    }

    /**
     * Returns the score difference for adding x to the parents of y with no other parents.
     *
     * @param x A node.
     * @param y The node.
     * @return The score difference.
     */
    @Override
    public double localScoreDiff(int x, int y) {
        return this.score.localScoreDiff(x, y) + this.twoBeta[y][x];
    }

    /**
     * Returns the variables of the score.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return this.score.getVariables();
    }

    /**
     * Returns the sample size of the data.
     *
     * @return This size.
     */
    @Override
    public int getSampleSize() {
        return this.score.getSampleSize();
    }

    /**
     * Returns true iff the edge between x and y is an effect edge.
     *
     * @param bump The bump.
     * @return True iff so.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return this.score.isEffectEdge(bump);
    }

    /**
     * Returns the max degree.
     *
     * @return The max degree.
     */
    @Override
    public int getMaxDegree() {
        return this.score.getMaxDegree();
    }

    /**
     * Returns true iff the score determines the edge between the given nodes.
     *
     * @param z The set of nodes.
     * @param y The node.
     * @return True iff so.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return this.score.determines(z, y);
    }

    /**
     * Returns the wrapped score.
     *
     * @return The delegate.
     */
    public Score getWrappedScore() {
        return this.score;
    }

    /**
     * Returns a string representation of this score.
     *
     * @return This string.
     */
    public String toString() {
        return "EdgePriorScore(" + this.score + ")";
    }
}
