package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Returns a scale free graph.
 *
 * @author jdramsey
 */
public class ScaleFree implements RandomGraph {
    static final long serialVersionUID = 23L;

    @Override
    public edu.cmu.tetrad.graph.Graph createGraph(Parameters parameters) {
        return GraphUtils.scaleFreeGraph(
                parameters.getInt("numMeasures") + parameters.getInt("numLatents"),
                parameters.getInt("numLatents"),
                parameters.getDouble("scaleFreeAlpha"),
                parameters.getDouble("scaleFreeBeta"),
                parameters.getDouble("scaleFreeDeltaIn"),
                parameters.getDouble("scaleFreeDeltaOut")
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
