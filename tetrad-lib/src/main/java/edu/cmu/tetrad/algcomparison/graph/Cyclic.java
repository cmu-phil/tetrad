package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("numMeasures", 10);
        parameters.put("avgDegree", 2);
        return parameters;
    }
}
