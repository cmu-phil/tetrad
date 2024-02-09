package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a single graph for use in simulations, etc.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SingleGraph implements RandomGraph {
    private static final long serialVersionUID = 23L;

    private final Graph graph;

    /**
     * <p>Constructor for SingleGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public SingleGraph(Graph graph) {
        this.graph = graph;
    }

    /** {@inheritDoc} */
    @Override
    public Graph createGraph(Parameters parameters) {
        return this.graph;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Graph supplied by user";
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }
}
