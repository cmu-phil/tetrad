package edu.cmu.tetrad.algcomparison.algorithms.graphs;

import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 7/29/16.
 */
public class RandomForward implements GraphGenerator {

    @Override
    public edu.cmu.tetrad.graph.Graph getGraph(Parameters parameters) {
        return GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures"),
                parameters.getInt("numLatents"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getInt("connected") == 1);
    }

    @Override
    public String getDescription() {
        return "Graph constructed by adding random forward edges";
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
        parameters.add("connected");
        return parameters;
    }
}
