///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see the GNU General Public License v3+.   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.graph.*;
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

        this.variables  = new ArrayList<>(variables);
        this.s          = s;
        this.sampleSize = sampleSize;
        this.alpha      = alpha;
    }

    // -------------------------------------------------------------------------
    // Public operations
    // -------------------------------------------------------------------------

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

            if (estimateRankConditioned(childrenX, childrenY, cond) == 0) {
                graph.removeEdge(edge);
            }
        }
    }

    /**
     * Orients undirected latent-latent edges using asymmetric correlations
     * between measured parents and measured children.
     *
     * <p>For an undirected edge between latents X and Y:
     * <ul>
     *   <li>Orient X → Y if every (parentX, childY) pair is significantly
     *       correlated but not every (parentY, childX) pair is.</li>
     *   <li>Orient Y → X in the symmetric case.</li>
     *   <li>Leave the edge unoriented if both directions give the same
     *       verdict (including when neither latent has measured parents).</li>
     * </ul>
     *
     * <p>Only undirected edges are examined; directed latent-latent edges
     * produced by earlier steps are left unchanged.
     *
     * @param graph the graph to orient; mutated in place
     */
    public void orientLatentEdges(Graph graph) {
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

            // Only attempt to orient undirected edges.
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            List<Node> parentsx = measuredOnly(graph.getParents(x));
            List<Node> parentsy = measuredOnly(graph.getParents(y));
            List<Node> childrenx = measuredOnly(graph.getChildren(x));
            List<Node> childreny = measuredOnly(graph.getChildren(y));

            boolean orientXtoY = allPairsCorrelated(parentsx, childreny);
            boolean orientYtoX = allPairsCorrelated(parentsy, childrenx);

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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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

        double z      = 0.5 * Math.log((1.0 + r) / (1.0 - r))
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

        int[] xIndices    = indicesToArray(x);
        int[] yIndices    = indicesToArray(y);
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
}