package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a DAG where the underlying undirected graph has E edges (unless that's
 * not possible) and places equal probability over all graphs with E edges, subject
 * to constraints on maximum degree, maximum indegree, and maximum outdegree.
 * @author jdramsey
 */
public class ErdosRenyi implements RandomGraph {
    static final long serialVersionUID = 23L;

    @Override
    public Graph createGraph(Parameters parameters) {
        return GraphUtils.erdosRenyiDag(
                parameters.getInt("numMeasures") + parameters.getInt("numLatents"),
                parameters.getInt("numLatents"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                true);
    }

    @Override
    public String getDescription() {
        return "Erdos-Renyi graph constructed by selecting graphs with a fixed number of edges, where these graphs " +
                "have equal probability";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("numLatents");
        parameters.add("avgDegree");
        parameters.add("maxDegree");
        parameters.add("maxIndegree");
        parameters.add("maxOutdegree");
        return parameters;
    }
}
