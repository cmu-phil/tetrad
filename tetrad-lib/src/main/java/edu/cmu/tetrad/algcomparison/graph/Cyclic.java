package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Returns a cyclic graph build up from small cyclic graph components.
 *
 * @author jdramsey
 */
public class Cyclic implements RandomGraph {

    @Override
    public Graph createGraph(Parameters parameters) {
        return GraphUtils.cyclicGraph2(parameters.getInt("numMeasures", 10),
                parameters.getInt("avgDegree", 2) * parameters.getInt("numMeasures", 10) / 2);

    }

    @Override
    public String getDescription() {
        return "Cyclic graph built from small cyclic components";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("avgDegree");
        return parameters;
    }
}
