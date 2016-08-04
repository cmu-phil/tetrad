package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.util.List;

/**
 * Created by jdramsey on 7/29/16.
 */
public class RandomForward implements RandomGraph {

    @Override
    public Graph createGraph(Parameters parameters) {
        return GraphUtils.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures", 10),
                parameters.getInt("numLatents", 0),
                parameters.getInt("avgDegree", 2) * parameters.getInt("numMeasures", 10) / 2,
                parameters.getInt("maxDegree", 100),
                parameters.getInt("maxIndegree", 100),
                parameters.getInt("maxOutdegree", 100),
                parameters.getBoolean("connected", false));
    }

    @Override
    public String getDescription() {
        return "Graph constructed by adding random forward edges";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<String>();
        parameters.put("numMeasures", 10);
        parameters.put("numLatents", 0);
        parameters.put("avgDegree", 2);
        parameters.put("maxDegree", 100);
        parameters.put("maxIndegree", 100);
        parameters.put("maxOutdegree", 100);
        parameters.put("connected", false);
        return parameters;
    }
}
