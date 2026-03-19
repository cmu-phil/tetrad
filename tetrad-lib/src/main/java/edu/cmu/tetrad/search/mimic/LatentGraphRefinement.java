/// ////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see the GNU General Public License v3+.   //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Shared post-processing refinements applied to the latent-variable graph
 * produced by Trek-MIMIC-style searches.
 *
 * <p>Two operations are provided:
 * <ol>
 *   <li><b>Pruning.</b> Removes a latent-latent edge when the cross-correlation
 *       block between the two latents' measured children has rank zero after
 *       conditioning on all recovered measured parents of both latents. A rank
 *       of zero means the edge is fully explained by those shared parents and
 *       carries no additional latent-to-latent information.</li>
 *   <li><b>Orientation.</b> Orients an undirected latent-latent edge X -- Y as
 *       X -> Y when every measured parent of X is significantly correlated with
 *       every measured child of Y, but not vice versa. The asymmetry acts as a
 *       proxy for the direction of latent influence.</li>
 * </ol>
 *
 * <p>Both operations take the graph as an argument and mutate it in place,
 * so the same {@code LatentGraphRefinement} instance can be applied to
 * successive graph states if needed.
 *
 * <p>Typical use:
 * <pre>
 * LatentGraphRefinement refinement =
 *     new LatentGraphRefinement(variables, correlationMatrix, sampleSize, alpha);
 * refinement.pruneLatentLatentEdges(graph);
 * refinement.orientLatentEdges(graph);
 * </pre>
 *
 * @author josephramsey
 */
public final class LatentGraphRefinement {

    /**
     * Measured variables in the same order as the rows/columns of {@link #s}.
     */
    private final List<Node> variables;

    /**
     * Correlation matrix over the measured variables.
     */
    private final SimpleMatrix s;

    /**
     * Sample size used for significance tests.
     */
    private final int sampleSize;

    /**
     * Alpha level for correlation and rank tests.
     */
    private final double alpha;
    /**
     * Minimum proportion of (parentX, childY) pairs that must be
     * significantly correlated before orienting X -> Y.
     * Default 1.0 requires all pairs (original behaviour).
     * Lower values are more robust at finite N.
     */
    private double orientationProportion = 1.0;

    // -------------------------------------------------------------------------
    // Public operations
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code LatentGraphRefinement} with the correlation context
     * needed to run both operations.
     *
     * @param variables  measured variables in matrix order; must not be null
     * @param s          correlation matrix over those variables; must not be null
     * @param sampleSize sample size; must be positive
     * @param alpha      significance level; must be in (0, 1)
     */
    public LatentGraphRefinement(List<Node> variables,
                                 SimpleMatrix s,
                                 int sampleSize,
                                 double alpha) {
        if (variables == null) {
            throw new NullPointerException("variables must not be null.");
        }
        if (s == null) {
            throw new NullPointerException("correlation matrix must not be null.");
        }
        if (sampleSize < 1) {
            throw new IllegalArgumentException("sampleSize must be positive.");
        }
        if (alpha <= 0.0 || alpha >= 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1).");
        }

        this.variables = new ArrayList<>(variables);
        this.s = s;
        this.sampleSize = sampleSize;
        this.alpha = alpha;
    }

    /**
     * Returns the measured (non-latent) children of the given node.
     */
    private static List<Node> getMeasuredChildren(Graph graph, Node node) {
        List<Node> children = new ArrayList<>();
        for (Node child : graph.getChildren(node)) {
            if (child.getNodeType() != NodeType.LATENT) {
                children.add(child);
            }
        }
        children.sort(Comparator.comparing(Node::getName));
        return children;
    }

    /**
     * Returns the measured (non-latent) parents of the given node.
     */
    private static List<Node> getMeasuredParents(Graph graph, Node node) {
        List<Node> parents = new ArrayList<>();
        for (Node parent : graph.getParents(node)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                parents.add(parent);
            }
        }
        parents.sort(Comparator.comparing(Node::getName));
        return parents;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Filters a list of nodes to measured (non-latent) nodes only.
     */
    private static List<Node> measuredOnly(List<Node> nodes) {
        List<Node> result = new ArrayList<>();
        for (Node n : nodes) {
            if (n.getNodeType() != NodeType.LATENT) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * Removes latent-latent edges whose cross-block dependence is fully
     * explained by the recovered measured parents of the two endpoint latents.
     *
     * <p>For each undirected or directed latent-latent edge (X, Y), the method
     * collects the union of measured parents of X and Y as the conditioning set.
     * If that set is non-empty and the conditioned rank of the cross-block
     * (measured children of X) × (measured children of Y) is zero, the edge
     * is removed.
     *
     * @param graph the graph to prune; mutated in place
     */
    public void pruneLatentLatentEdges(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null.");
        }

        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x.getNodeType() != NodeType.LATENT
                    || y.getNodeType() != NodeType.LATENT) {
                continue;
            }

            List<Node> childrenX = getMeasuredChildren(graph, x);
            List<Node> childrenY = getMeasuredChildren(graph, y);

            if (childrenX.isEmpty() || childrenY.isEmpty()) {
                continue;
            }

            // Union of measured parents of both latents.
            List<Node> cond = new ArrayList<>(getMeasuredParents(graph, x));
            for (Node parent : getMeasuredParents(graph, y)) {
                if (!cond.contains(parent)) {
                    cond.add(parent);
                }
            }

            if (cond.isEmpty()) {
                continue;
            }

            int rank = estimateRankConditioned(childrenX, childrenY, cond);

            if (rank == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    public void pruneInputLatentEdges(Graph graph) throws InterruptedException {
        if (graph == null) {
            throw new NullPointerException("graph must not be null.");
        }

        EDGE:
        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node x = edge.getNode1();
            Node L = edge.getNode2();

            List<Node> otherChildrenOfX = graph.getChildren(x);

            for (Node child : otherChildrenOfX) {
                if (child.getNodeType() != NodeType.LATENT) {
                    continue EDGE;
                }
            }

            otherChildrenOfX.remove(L);

            if (otherChildrenOfX.isEmpty()) {
                continue EDGE;
            }

            List<Node> childrenL = getMeasuredChildren(graph, L);

            Set<Node> _C = new HashSet<>();
            for (Node child : otherChildrenOfX) {
                _C.addAll(getMeasuredChildren(graph, child));
            }

            List<Node> C = new ArrayList<>(_C);

            Set<Node> _P = new HashSet<>();
            for (Node child : otherChildrenOfX) {
                _P.addAll(getMeasuredParents(graph, child));
            }

            List<Node> P = new ArrayList<>(_P);

            int rank = estimateRankConditioned(C, childrenL, P);

            if (x.getNodeType() != NodeType.MEASURED
                    || L.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (!edge.pointsTowards(L)) {
                continue;
            }

            List<Node> allLatents = ReidentifyVariables.getLatents(graph);

            for (Node n : allLatents) {
                n.setNodeType(NodeType.MEASURED);
            }

            Set<Node> blocking = RecursiveBlocking.blockPathsRecursively(graph, x, L, Set.of(), Set.of(), -1);

            for (Node n : allLatents) {
                n.setNodeType(NodeType.LATENT);
            }

            if (blocking == null || blocking.isEmpty()) {
                continue;
            }

            for (Node n : blocking) {
                if (n.getNodeType() != NodeType.LATENT) {
                    continue EDGE;
                }
            }

            Set<Node> childrenBlocking = new HashSet<>();

            for (Node n : blocking) {
                childrenBlocking.addAll(getMeasuredChildren(graph, n));
            }

            if (childrenBlocking.isEmpty()) {
                continue;
            }

            int rank2 = estimateRankConditioned(List.of(x), childrenL, new ArrayList<>(childrenBlocking));

            if (rank2 == 1) {
                graph.removeEdge(edge);
            }
        }
    }

    /**
     * Orients undirected latent-latent edges using asymmetric correlations
     * between measured parents and measured children.
     *
     * <p>For an undirected edge between latents X and Y:
     * <ul>f
     *   <li>Orient X → Y if every (parentX, childY) pair is significantly
     *       correlated but not every (parentY, childX) pair is.</li>
     *   <li>Orient Y → X in the symmetric case.</li>
     *   <li>Leave the edge unoriented if both directions give the same
     *       verdict (including when neither latent has measured parents).</li>
     * </ul>
     *
     * <p>This overwrites any existing directed edges to avoid confusion (and cycles).
     *
     * @param graph the graph to orient; mutated in place
     */
    public void orientLatentEdges(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null.");
        }

        // Override existing directed edges to avoid confusion (and cycles).
        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x.getNodeType() != NodeType.LATENT
                    || y.getNodeType() != NodeType.LATENT) {
                continue;
            }

            graph.removeEdge(edge);
            graph.addUndirectedEdge(x, y);
        }

        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (x.getNodeType() != NodeType.LATENT
                    || y.getNodeType() != NodeType.LATENT) {
                continue;
            }

            List<Node> parentsx = measuredOnly(graph.getParents(x));
            List<Node> parentsy = measuredOnly(graph.getParents(y));
            List<Node> childrenx = measuredOnly(graph.getChildren(x));
            List<Node> childreny = measuredOnly(graph.getChildren(y));

//            boolean orientXtoY = allPairsCorrelated(parentsx, childreny);
//            boolean orientYtoX = allPairsCorrelated(parentsy, childrenx);

            boolean orientXtoY = sufficientPairsCorrelated(
                    parentsx, childreny, orientationProportion);
            boolean orientYtoX = sufficientPairsCorrelated(
                    parentsy, childrenx, orientationProportion);

            // No asymmetry, or neither side has evidence: leave undirected.
            if (orientXtoY == orientYtoX) {
                continue;
            }

            graph.removeEdge(edge);

            if (orientXtoY) {
                graph.addDirectedEdge(x, y);
            } else {
                graph.addDirectedEdge(y, x);
            }
        }
    }

    /**
     * Returns true iff at least {@code minProportion} of the cross-pairs
     * between left and right are significantly correlated.
     *
     * <p>Replacing the all-or-nothing check with a proportion threshold
     * makes orientation more robust at finite N: a single noisy pair no
     * longer causes abstention when the overwhelming majority of pairs
     * support a direction.</p>
     *
     * @param left          the first list of nodes
     * @param right         the second list of nodes
     * @param minProportion the minimum fraction of pairs that must be
     *                      correlated; 1.0 recovers the original behaviour
     * @return true if at least minProportion of cross-pairs are correlated
     */
    private boolean sufficientPairsCorrelated(List<Node> left,
                                              List<Node> right,
                                              double minProportion) {
        if (left.isEmpty() || right.isEmpty()) return false;

        int total = left.size() * right.size();
        int correlated = 0;

        for (Node a : left) {
            for (Node b : right) {
                if (correlated(a, b)) correlated++;
            }
        }

        return (double) correlated / total >= minProportion;
    }

    public void setOrientationProportion(double proportion) {
        if (proportion <= 0.0 || proportion > 1.0) {
            throw new IllegalArgumentException(
                    "orientationProportion must be in (0, 1].");
        }
        this.orientationProportion = proportion;
    }

    /**
     * Returns true iff every (a, b) pair across the two lists is significantly
     * correlated, AND at least one pair was tested. Returns false immediately
     * when no pairs exist (empty list on either side).
     */
    private boolean allPairsCorrelated(List<Node> left, List<Node> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }

        for (Node a : left) {
            for (Node b : right) {
                if (!correlated(a, b)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Fisher z-test for a non-zero Pearson correlation.
     *
     * @param a first variable
     * @param b second variable
     * @return true if the correlation is significant at {@link #alpha}
     */
    private boolean correlated(Node a, Node b) {
        int i = variables.indexOf(a);
        int j = variables.indexOf(b);

        double r = s.get(i, j);

        if (Math.abs(r) >= 1.0) {
            return true;
        }

        double z = 0.5 * Math.log((1.0 + r) / (1.0 - r))
                * Math.sqrt(sampleSize - 3.0);
        double cutoff = StatUtils.getZForAlpha(alpha);

        return Math.abs(z) > cutoff;
    }

    /**
     * Estimates the rank of the cross-correlation block (xSet × ySet) after
     * partialling out {@code cond}, using a Wilks LRT.
     *
     * <p>Returns {@link Integer#MAX_VALUE} when either residual set is empty
     * after removing overlap, indicating the test cannot be applied.
     */
    private int estimateRankConditioned(List<Node> xSet,
                                        List<Node> ySet,
                                        List<Node> cond) {
        List<Node> x = new ArrayList<>(xSet);
        List<Node> y = new ArrayList<>(ySet);

        // Remove variables that appear on both sides; they carry no cross-block
        // information and would make the index arrays non-distinct.
        x.removeAll(y);

        if (x.isEmpty() || y.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int[] xIndices = indicesToArray(x);
        int[] yIndices = indicesToArray(y);
        int[] condIndices = indicesToArray(cond);

        return RankTests.estimateWilksRankConditioned(
                s, xIndices, yIndices, condIndices, sampleSize, alpha);
    }

    /**
     * Returns the indices of the given nodes in {@link #variables}.
     */
    private int[] indicesToArray(List<Node> nodes) {
        int[] indices = new int[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            indices[i] = variables.indexOf(nodes.get(i));
        }
        return indices;
    }

    /**
     * Optionally removes input edges X -> L that are explained by a directed
     * latent path X -> La -> ... -> L.
     *
     * <p>After latent edges are oriented, the latent subgraph is a DAG (or
     * close to one). For each measured input X and each latent L that X is
     * attached to, this method checks whether X is already attached to some
     * latent ancestor La of L. If so, X -> L is a transitive closure edge:
     * X reaches L through the latent path X -> La -> ... -> L, and the direct
     * edge X -> L carries no additional causal information.</p>
     *
     * <p>This is applied after orientation because it requires knowing the
     * direction of latent-latent edges. It is optional because the transitive
     * edges are not strictly wrong — they are unidentifiable from direct edges
     * by any observed-variable test — but removing them produces a sparser
     * graph that is closer to the true causal structure when the model is
     * correctly specified.</p>
     *
     * <p>Note: this method does not use any statistical tests. It is a purely
     * structural operation on the directed latent graph.</p>
     *
     * @param graph the working graph, mutated in place
     */
    public void pruneTransitiveInputEdgesByLatentAncestry(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null.");
        }

        // Collect latents and build the latent ancestor sets once.
        List<Node> latents = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        // For each latent L, compute the set of latent ancestors of L
        // in the directed latent subgraph.
        Map<Node, Set<Node>> latentAncestors = new LinkedHashMap<>();
        for (Node latent : latents) {
            latentAncestors.put(latent, getLatentAncestors(latent, graph));
        }

        // For each latent L, find its measured parents.
        // Remove X -> L if X is already a measured parent of some
        // latent ancestor La of L.
        for (Node l : latents) {
            Set<Node> ancestorsOfL = latentAncestors.get(l);
            if (ancestorsOfL.isEmpty()) continue;

            List<Node> measuredParents = new ArrayList<>();
            for (Node parent : graph.getParents(l)) {
                if (parent.getNodeType() != NodeType.LATENT) {
                    measuredParents.add(parent);
                }
            }

            for (Node x : new ArrayList<>(measuredParents)) {
                // Check if X is already a measured parent of any latent
                // ancestor of L.
                boolean transitiveViaAncestor = false;

                for (Node la : ancestorsOfL) {
                    if (graph.isParentOf(x, la)) {
                        transitiveViaAncestor = true;
                        break;
                    }
                }

                if (transitiveViaAncestor) {
                    Edge edge = graph.getEdge(x, l);
                    if (edge != null) {
                        graph.removeEdge(edge);
                    }
                }
            }
        }
    }

    /**
     * Returns the set of latent ancestors of the given latent node,
     * following directed edges among latent nodes only.
     *
     * @param latent the latent node
     * @param graph  the graph
     * @return the set of latent ancestors
     */
    private Set<Node> getLatentAncestors(Node latent, Graph graph) {
        Set<Node> ancestors = new LinkedHashSet<>();
        Queue<Node> queue = new LinkedList<>();

        // Seed with the latent parents of this node.
        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() == NodeType.LATENT) {
                queue.add(parent);
            }
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (!ancestors.add(current)) continue;

            for (Node parent : graph.getParents(current)) {
                if (parent.getNodeType() == NodeType.LATENT) {
                    queue.add(parent);
                }
            }
        }

        return ancestors;
    }

    /**
     * Removes transitive latent-latent edges after orientation.
     *
     * <p>A directed edge La -> Lc is removed if there exists a directed path
     * La -> Lb -> ... -> Lc through other latent nodes. Such an edge is a
     * transitive closure of the latent DAG and carries no additional causal
     * information beyond what is already represented by the intermediate path.</p>
     *
     * <p>This is applied after latent edge orientation because it requires
     * directed edges to determine ancestry. It is a purely structural operation
     * — no statistical tests are performed.</p>
     *
     * <p>Note: if the latent subgraph contains undirected or partially oriented
     * edges, those are left unchanged. Only directed edges are candidates for
     * removal.</p>
     *
     * @param graph the working graph, mutated in place
     */
    public void pruneTransitiveLatentEdges(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null.");
        }

        // Collect directed latent-latent edges as candidates.
        List<Edge> candidates = new ArrayList<>();
        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();
            if (x.getNodeType() == NodeType.LATENT
                    && y.getNodeType() == NodeType.LATENT
                    && Edges.isDirectedEdge(edge)) {
                candidates.add(edge);
            }
        }

        // For each directed latent edge La -> Lc, check whether there is
        // a directed path La -> Lb -> ... -> Lc of length >= 2.
        // If so, the direct edge is a transitive closure and is removed.
        for (Edge edge : candidates) {
            Node la = Edges.getDirectedEdgeTail(edge);
            Node lc = Edges.getDirectedEdgeHead(edge);

            if (hasDirectedPathOfLengthAtLeastTwo(la, lc, graph)) {
                graph.removeEdge(edge);
            }
        }
    }

    /**
     * Returns true iff there is a directed path from {@code from} to
     * {@code to} of length at least two, following directed edges among
     * latent nodes only.
     *
     * <p>This is a breadth-first search that deliberately skips the direct
     * edge from -> to; it looks for any path that goes through at least
     * one intermediate latent node.</p>
     *
     * @param from  the source latent
     * @param to    the target latent
     * @param graph the graph
     * @return true if an indirect directed path exists
     */
    private boolean hasDirectedPathOfLengthAtLeastTwo(Node from,
                                                      Node to,
                                                      Graph graph) {
        // BFS over latent children of 'from', excluding 'to' at depth 1.
        // If we reach 'to' at depth >= 2 we have found an indirect path.
        Set<Node> visited = new LinkedHashSet<>();
        Queue<Node> queue = new LinkedList<>();

        // Seed with latent children of 'from' other than 'to'.
        // These are the first-hop intermediates — any path through them
        // to 'to' has length >= 2.
        for (Node child : graph.getChildren(from)) {
            if (child.getNodeType() != NodeType.LATENT) continue;
            if (child == to) continue;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (!visited.add(current)) continue;

            for (Node child : graph.getChildren(current)) {
                if (child.getNodeType() != NodeType.LATENT) continue;

                if (child == to) {
                    return true;
                }

                queue.add(child);
            }
        }

        return false;
    }

    /**
     * Adds all transitive input-to-latent edges implied by the current
     * directed latent structure.
     *
     * <p>For each measured input X and each latent L, if there exists a
     * directed path X -> La -> ... -> L through the latent subgraph, and
     * X -> La is already in the graph, then X -> L is added.</p>
     *
     * <p>This is the inverse operation of
     * {@link #pruneTransitiveInputEdgesByLatentAncestry(Graph)}. Applied
     * after orientation and optional transitive pruning, it recovers the
     * superset graph that includes all edges whose existence cannot be
     * ruled out by rank considerations. The difference between the graph
     * with and without these edges is exactly the set of input-latent
     * connections that are unidentifiable from the data.</p>
     *
     * <p>This operation requires directed latent edges and should be
     * called after {@link #orientLatentEdges(Graph)}.</p>
     *
     * @param graph the working graph, mutated in place
     */
    public void addTransitiveInputEdges(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("graph must not be null.");
        }

        // Collect latents and their measured parents once.
        List<Node> latents = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                latents.add(node);
            }
        }

        // For each latent L, find all latent ancestors.
        Map<Node, Set<Node>> ancestorsByLatent = new LinkedHashMap<>();
        for (Node latent : latents) {
            ancestorsByLatent.put(latent, getLatentAncestors(latent, graph));
        }

        // For each latent L, for each latent ancestor La of L,
        // add X -> L for every measured parent X of La.
        for (Node l : latents) {
            Set<Node> ancestors = ancestorsByLatent.get(l);
            if (ancestors.isEmpty()) continue;

            for (Node la : ancestors) {
                for (Node x : graph.getParents(la)) {
                    if (x.getNodeType() == NodeType.LATENT) continue;

                    if (!graph.isParentOf(x, l)) {
                        graph.addDirectedEdge(x, l);
                    }
                }
            }
        }
    }

    /**
     * Orients and prunes the latent structure of the given graph.
     *
     * <p>The conditional rank pruning step is expected to have already
     * been applied before calling this method. This method performs
     * orientation of latent edges followed by structural pruning of
     * transitive input and latent edges.</p>
     *
     * <p>The caller's graph is not modified.</p>
     *
     * @param graph the graph to process; not modified
     * @return an unmodifiable list of two graphs: the oriented graph
     *         (before transitive pruning) and the pruned graph (after).
     *         The difference between the two is the set of edges in the
     *         unidentifiable region.
     */
    public List<Graph> pruneEdges(Graph graph) {
        graph = new EdgeListGraph(graph);
        orientLatentEdges(graph);
        Graph oriented = new EdgeListGraph(graph);
        pruneTransitiveInputEdgesByLatentAncestry(graph);
        pruneTransitiveLatentEdges(graph);
        Graph pruned = new EdgeListGraph(graph);
        return List.of(oriented, pruned);
    }
}