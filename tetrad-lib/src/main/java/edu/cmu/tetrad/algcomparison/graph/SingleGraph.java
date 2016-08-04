package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.Graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Stores a single graph for use in simulations, etc.
 *
 * @author jdramsey
 */
public class SingleGraph implements RandomGraph {

    private Graph graph;

    public SingleGraph(Graph graph) {
        this.graph = graph;
    }

    @Override
    public Graph createGraph(Parameters parameters) {
        return graph;
    }

    @Override
    public String getDescription() {
        return "Stores a single graph";
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }
}
