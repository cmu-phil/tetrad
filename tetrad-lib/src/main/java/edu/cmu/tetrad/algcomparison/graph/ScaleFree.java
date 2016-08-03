package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns a scale free graph.
 *
 * @author jdramsey
 */
public class ScaleFree implements RandomGraph {

    @Override
    public edu.cmu.tetrad.graph.Graph createGraph(Parameters parameters) {
        return GraphUtils.scaleFreeGraph(
                parameters.getInt("numMeasures", 10),
                parameters.getInt("numLatents", 0),
                parameters.getDouble("scaleFreeAlpha", .05),
                parameters.getDouble("scaleFreeBeta", .9),
                parameters.getDouble("scaleFreeDeltaIn", 3),
                parameters.getInt("scaleFreeDeltaOut", 3)
        );
    }

    @Override
    public String getDescription() {
        return "Scale-free graph using the Bollobas et al. algorithm";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("numMeasures", 10);
        parameters.put("numLatents", 0);
        parameters.put("scaleFreeAlpha", 0.05);
        parameters.put("scaleFreeBeta", 0.95);
        parameters.put("scaleFreeDeltaIn", 3);
        parameters.put("scaleFreeDeltaOut", 3);
        return parameters;
    }
}
