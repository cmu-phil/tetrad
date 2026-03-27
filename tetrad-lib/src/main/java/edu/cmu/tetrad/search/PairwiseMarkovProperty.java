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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Computes the <em>pairwise Markov property</em> for an acyclic directed mixed graph
 * (ADMG).  For every pair of non-adjacent vertices (α, β) the property asserts:
 * <pre>
 *   α ⊥⊥ β | ant({α, β})
 * </pre>
 * where ant({α, β}) = an({α, β}) ∖ {α, β} denotes the <em>proper ancestors</em> of the
 * pair (the union of the ancestor sets of α and β, excluding α and β themselves).
 *
 * <h2>Soundness</h2>
 * Every generated fact is a valid m-separation in the ADMG.  Intuitively: since α and β
 * are non-adjacent, any path between them must pass through at least one node.  Every
 * such intermediate node is either (a) an ancestor of α or β (and hence in the
 * conditioning set, blocking non-collider paths) or (b) a collider whose descendants
 * all lie outside the conditioning set (blocking collider paths because no collider is
 * activated).  This mirrors Richardson's moralization argument (Theorem 1 of Richardson
 * 2003).
 *
 * <h2>Completeness</h2>
 * Under the <em>compositional graphoid</em> axioms (C1)–(C5), the pairwise facts imply
 * every m-separation in the ADMG.  In particular, axiom (C5) (Composition):
 * <pre>
 *   X ⊥ Y | Z  ∧  X ⊥ W | Z  ⟹  X ⊥ YW | Z
 * </pre>
 * is needed to combine individual pairwise facts into set-level independence statements.
 * Composition holds for all strictly positive distributions (Gaussian, multinomial with
 * positive cell probabilities, etc.) but may fail for singular or degenerate cases.
 *
 * <p>By contrast, Richardson's full OLMP ({@link RichardsonOrderedLocalMarkovProperty})
 * generates set-level facts directly and achieves completeness with only (C1)–(C4).
 *
 * <h2>Complexity</h2>
 * O(|V|²) CI facts, one per non-adjacent pair.  Ancestor computation per pair is
 * O(|V| + |E|), giving O(|V|² · (|V| + |E|)) overall — polynomial in graph size.
 * This makes the pairwise property by far the cheapest of the three procedures.
 *
 * <h2>Relationship to other procedures</h2>
 * <ul>
 *   <li>{@link OrderedLocalMarkovProperty} (Andrews): sound but not complete.
 *   <li>{@link RichardsonOrderedLocalMarkovProperty}: sound and complete, no composition
 *       needed, but potentially exponential in district size.
 *   <li>This class: sound and complete under composition, polynomial cost.
 * </ul>
 *
 * <h2>References</h2>
 * Richardson, T.S. (2003). Markov properties for acyclic directed mixed graphs.
 * <em>Scandinavian Journal of Statistics</em>, 30(1), 145–157.  (The pairwise property
 * is noted implicitly; Bryan Andrews identified it as a practical complete alternative.)
 */
public class PairwiseMarkovProperty {

    private PairwiseMarkovProperty() {
    }

    /**
     * Computes the pairwise Markov property for the given ADMG.
     *
     * <p>For every non-adjacent pair (α, β) emits:
     * <pre>   α ⊥⊥ β | ant({α, β})   </pre>
     * where ant({α, β}) = an({α, β}) ∖ {α, β}.
     *
     * @param admg An acyclic directed mixed graph.  Passed as a Graph (typically a
     *             MAG in Tetrad); bidirected edges are treated as in the ADMG sense.
     * @return The set of pairwise independence facts.
     */
    public static Set<IndependenceFact> getModel(Graph admg) {
        Set<IndependenceFact> model = new HashSet<>();
        List<Node> nodes = admg.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node alpha = nodes.get(i);
                Node beta  = nodes.get(j);

                if (admg.isAdjacentTo(alpha, beta)) continue;

                // ant({alpha, beta}) = an({alpha, beta}) \ {alpha, beta}.
                Set<Node> conditioning = properAncestors(alpha, beta, admg);

                model.add(new IndependenceFact(alpha, beta, conditioning));
            }
        }

        return model;
    }

    /**
     * Returns the subset of pairwise independence facts that involve node {@code x}
     * (as either the left or right endpoint), normalised so that {@code x} is always
     * the left-hand ({@code getX()}) node.
     *
     * <p>Equivalent to filtering {@link #getModel(Graph)} for facts mentioning x, but
     * more efficient: only pairs involving x are examined.
     *
     * @param admg The ADMG.
     * @param x    The node of interest.
     * @return Independence facts of the form x ⊥⊥ y | ant({x, y}) for all y
     *         non-adjacent to x.
     */
    public static Set<IndependenceFact> getModelForNode(Graph admg, Node x) {
        Set<IndependenceFact> model = new HashSet<>();

        for (Node other : admg.getNodes()) {
            if (other.equals(x)) continue;
            if (admg.isAdjacentTo(x, other)) continue;

            Set<Node> conditioning = properAncestors(x, other, admg);
            model.add(new IndependenceFact(x, other, conditioning));
        }

        return model;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Computes ant({alpha, beta}) = an({alpha, beta}) ∖ {alpha, beta}.
     *
     * <p>Performs a BFS backwards through directed edges from both alpha and beta,
     * collecting all nodes reachable via parent pointers.  Alpha and beta themselves
     * are excluded from the returned set.
     *
     * <p>For a graph with only bidirected edges (no directed edges), every node has
     * no parents, so an({alpha,beta}) = {alpha, beta} and the conditioning set is
     * empty — correctly encoding marginal independence for non-adjacent pairs in a
     * purely bidirected graph.
     *
     * @param alpha First node of the pair.
     * @param beta  Second node of the pair.
     * @param admg  The ADMG in which to compute ancestors.
     * @return The proper ancestors of the pair; never contains alpha or beta.
     */
    private static Set<Node> properAncestors(Node alpha, Node beta, Graph admg) {
        Set<Node> result  = new HashSet<>();
        Set<Node> visited = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>();

        // Seed the BFS with both endpoints (but do not add them to result).
        visited.add(alpha);
        visited.add(beta);
        queue.add(alpha);
        queue.add(beta);

        while (!queue.isEmpty()) {
            Node v = queue.poll();
            for (Node parent : admg.getParents(v)) {
                if (visited.add(parent)) {
                    // parent is a proper ancestor of alpha or beta
                    result.add(parent);
                    queue.add(parent);
                }
            }
        }

        return result;
    }
}
