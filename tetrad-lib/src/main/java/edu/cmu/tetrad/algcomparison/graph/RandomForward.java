package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a random graph by adding forward edges.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RandomForward implements RandomGraph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the RandomForward.
     */
    public RandomForward() {

    }

    /**
     * Creates a random graph by adding forward edges.
     *
     * @param parameters Whatever parameters are needed for the given graph. See getParameters().
     * @return The created graph.
     */
    @Override
    public Graph createGraph(Parameters parameters) {
        return edu.cmu.tetrad.graph.RandomGraph.randomGraphRandomForwardEdges(
                parameters.getInt("numMeasures") + parameters.getInt("numLatents"),
                parameters.getInt("numLatents"),
                parameters.getInt("avgDegree") * parameters.getInt("numMeasures") / 2,
                parameters.getInt("maxDegree"),
                parameters.getInt("maxIndegree"),
                parameters.getInt("maxOutdegree"),
                parameters.getBoolean("connected"));
    }

    /**
     * Returns a short, one-line description of this graph type. This will be printed in the report.
     *
     * @return The description of the graph type.
     */
    @Override
    public String getDescription() {
        return "Graph constructed by adding random forward edges";
    }

    /**
     * Returns the parameters that this graph uses.
     *
     * @return A list of String names of parameters.
     */
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
