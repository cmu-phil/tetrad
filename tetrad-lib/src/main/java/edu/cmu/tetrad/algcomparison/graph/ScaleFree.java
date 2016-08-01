package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.List;

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
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("numLatents");
        parameters.add("scaleFreeAlpha");
        parameters.add("scaleFreeBeta");
        parameters.add("scaleFreeDeltaIn");
        parameters.add("scaleFreeDeltaOut");
        return parameters;
    }
}
