package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import org.apache.commons.math3.analysis.function.Sin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jdramsey on 7/29/16.
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
    public Map<String, Object> getParameters() {
        return new LinkedHashMap<>();
    }
}
