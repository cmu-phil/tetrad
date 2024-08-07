package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Represents a discriminating path; the orientation for the nodes in a Directed Acyclic Graph (DAG) based on the
 * Discriminating Path Rule. The path is &lt;X,...,W, V, Y&gt;, with nodes between X and V colliders along the path and
 * parents of Y (Zhang 2008). The question is whether there's a sepset S such that X _||_ Y | S, and whether S contains
 * V or not. If it does, then &lt;X, V, Y&gt; is a noncollider; otherwise, it is a collider. This is Zhang's rule R4,
 * discriminating paths.
 * <p>
 * Pictorially:
 * <pre>
 *      The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
 *      the dots colliders between X and V with each node on the path (except X) a parent of Y.
 *
 *               V
 *              xo           x is either an arrowhead or a circle
 *             /  \
 *            v    v
 *      X.....W--->Y
 * </pre>
 * <p>
 * The reference for this is Zhang, J. (2008), On the completeness of orientation rules for causal discovery in the
 * presence of latent confounders and selection bias, Artificial Intelligence, 172(16-17), 1873-1896.
 */
public class DiscriminatingPath {
    private final List<Node> path;

    public DiscriminatingPath(List<Node> path) {
        this.path = path;
    }

    public List<Node> getPath() {
        return path;
    }

    public String toString() {
        return "DiscriminatingPath{ path=" + path + '}';
    }
}
