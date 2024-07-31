package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a discriminating path in a graph.
 */
public class DiscriminatingPath {
    private final Node e;
    private final Node a;
    private final Node b;
    private final Node c;
    private final List<Node> colliderPath;

    public DiscriminatingPath(Node e, Node a, Node b, Node c, LinkedList<Node> colliderPath) {
        this.e = e;
        this.a = a;
        this.b = b;
        this.c = c;
        this.colliderPath = colliderPath;
    }

    public Node getE() {
        return e;
    }

    public Node getA() {
        return a;
    }

    public Node getB() {
        return b;
    }

    public Node getC() {
        return c;
    }

    public List<Node> getColliderPath() {
        return colliderPath;
    }
}
