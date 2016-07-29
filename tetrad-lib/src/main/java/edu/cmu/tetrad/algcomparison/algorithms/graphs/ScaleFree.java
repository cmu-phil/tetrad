package edu.cmu.tetrad.algcomparison.algorithms.graphs;

import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Returns a scale free graph.
 *
 * @author jdramsey
 */
public class ScaleFree implements GraphGenerator {

    @Override
    public edu.cmu.tetrad.graph.Graph getGraph(Parameters parameters) {
        return GraphUtils.scaleFreeGraph(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getDouble("scaleFreeAlpha"),
                parameters.getDouble("scaleFreeBeta"),
                parameters.getDouble("scaleFreeDeltaIn"),
                parameters.getInt("scaleFreeDeltaOut")
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
