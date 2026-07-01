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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface for suborder searches for various types of permutation algorithms. A "suborder search" is a search for
 * permutation &lt;x1a,...x1n, x2a,...,x2m, x3a,...,x3l&gt;> that searches for a good permutation of x2a,...,x2m with
 * x1a,...,x1n as a prefix. This is used by PermutationSearch to form a complete permutation search algorithm, where
 * PermutationSearch handles an optimization for tiered knowledge where each tier can be searched separately in order.
 * (See the documentation for that class.)
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 * <p>
 * This "TopK" variant extends the original SuborderSearch contract with a small number of methods that let the search
 * retain not just the single best-scoring permutation but the top <i>k</i> permutations found, along with an optional
 * "split" mechanism (parameterized by a {@code delta} threshold) that defers near-tied alternative orderings for later
 * exploration. See {@link BossTopK} for the reference implementation and a fuller description of the semantics.
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 * @see PermutationSearchTopK
 * @see BossTopK
 * @see Knowledge
 */
public interface SuborderSearchTopK {

    /**
     * Searches the suborder.
     *
     * @param prefix   The prefix of the suborder.
     * @param suborder The suborder.
     * @param gsts     The GrowShrinkTree being used to do caching of scores.
     * @throws InterruptedException If the search is interrupted.
     * @see GrowShrinkTree
     */
    void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) throws InterruptedException;

    /**
     * The knowledge being used.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * The list of all variables, in order. They should satisfy the suborder requirements.
     *
     * @return This list.
     * @see Node
     * @see edu.cmu.tetrad.data.Variable
     */
    List<Node> getVariables();

    /**
     * The map from nodes to parents resulting from the search. After a search this reflects the single best-scoring
     * (top) model, so that a PermutationSearch built on this suborder search returns the same graph it always did.
     *
     * @return This map.
     */
    Map<Node, Set<Node>> getParents();

    /**
     * The score being used.
     *
     * @return This score.
     * @see Score
     */
    Score getScore();

    // ---------------------------------------------------------------------------------------------------------------
    // Top-k extensions.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Sets the number of top-scoring permutations (models) to retain. A value of 1 recovers the ordinary single-model
     * behavior. Must be at least 1.
     *
     * @param k The number of top models to keep.
     */
    void setTopK(int k);

    /**
     * Sets the "split" threshold delta (&ge; 0). When an improving move's total score exceeds the score of an
     * alternative insertion position by an amount that is strictly positive but strictly less than delta, the two
     * orderings are treated as indistinguishable and the alternative is deferred for later exploration. A value of 0
     * disables splitting.
     *
     * @param delta The split threshold; must be &ge; 0.
     */
    void setDelta(double delta);

    /**
     * Sets a hard cap on the total number of hill-climb runs (initial restarts plus deferred continuations). This
     * guarantees termination regardless of delta. Must be at least 1.
     *
     * @param maxRuns The maximum number of runs.
     */
    void setMaxRuns(int maxRuns);

    /**
     * Sets whether top-k retention de-duplicates by Markov equivalence class rather than by permutation. When true, a
     * model's identity is its canonical CPDAG, so distinct permutations in the same equivalence class (same CPDAG, same
     * score) are counted once. When false (the default), identity is the permutation itself.
     *
     * @param dedupByCpdag True to de-duplicate by CPDAG.
     */
    void setDedupByCpdag(boolean dedupByCpdag);

    /**
     * Returns the number of top models actually available after a search. This is at most {@code k}, but may be smaller
     * if fewer distinct models were found.
     *
     * @return The number of models available (0 before a search has been run).
     */
    int getNumTopK();

    /**
     * Returns the i-th top permutation (0 = highest scoring) as an array of indices into {@link #getVariables()}. The
     * array has one entry per variable and is a full ordering (prefix followed by the searched suborder).
     *
     * @param i The rank, with 0 &le; i &lt; {@link #getNumTopK()}.
     * @return The permutation as an int array of variable indices.
     */
    int[] getTopKPermutation(int i);

    /**
     * Returns the score of the i-th top permutation (0 = highest scoring).
     *
     * @param i The rank, with 0 &le; i &lt; {@link #getNumTopK()}.
     * @return The total (higher-is-better) score of that permutation.
     */
    double getTopKScore(int i);

    /**
     * Returns the parent map of the i-th top permutation (0 = highest scoring), suitable for building the corresponding
     * graph via {@code PermutationSearchTopK.getGraph(...)}.
     *
     * @param i The rank, with 0 &le; i &lt; {@link #getNumTopK()}.
     * @return A map from each node to its set of parents for that model.
     */
    Map<Node, Set<Node>> getTopKParents(int i);
}
