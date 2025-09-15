package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

import java.util.LinkedList;

/**
 * Stores a history of graph objects.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphHistory {

    /**
     * The history.
     */
    private final LinkedList<Graph> graphs;

    /**
     * The index of the getModel graph.
     */
    private int index;

    /**
     * Constructs a graph history.
     */
    public GraphHistory() {
        this.graphs = new LinkedList<>();
        this.index = -1;
    }

    /**
     * <p>add.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void add(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        if (this.graphs.size() > this.index + 1) {
            this.graphs.subList(this.index + 1, this.graphs.size()).clear();
        }

        this.graphs.addLast(new EdgeListGraph(graph));
        this.index++;
    }

    /**
     * <p>next.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph next() {
        if (this.index == -1) {
            throw new IllegalArgumentException("Graph history has not been " +
                                               "initialized yet.");
        }

        if (this.index < this.graphs.size() - 1) {
            this.index++;
        }

        return this.graphs.get(this.index);
    }

    /**
     * <p>previous.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph previous() {
        if (this.index == -1) {
            throw new IllegalArgumentException("Graph history has not been " +
                                               "initialized yet.");
        }

        if (this.index > 0) {
            this.index--;
        }

        return this.graphs.get(this.index);
    }

    /**
     * <p>clear.</p>
     */
    public void clear() {
        this.graphs.clear();
        this.index = -1;
    }
}



