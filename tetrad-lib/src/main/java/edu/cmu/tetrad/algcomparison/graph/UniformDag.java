package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.UniformGraphGenerator;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a random graph by adding forward edges.
 *
 * @author jdramsey
 */
public class UniformDag implements RandomGraph {
    static final long serialVersionUID = 23L;

    @Override
    public Graph createGraph(Parameters parameters) {

        UniformGraphGenerator generator = new UniformGraphGenerator(0);
        generator.setNumNodes(parameters.getInt("numMeasures"));
        generator.setMaxDegree(parameters.getInt("maxDegree"));
        generator.setMaxInDegree(parameters.getInt("maxIndegree"));
        generator.setMaxOutDegree(parameters.getInt("maxOutdegree"));
        generator.setNumIterations(parameters.getInt("maxIterations"));
        generator.setMaxEdges(parameters.getInt("maxEdges"));
        generator.setNumNodes(parameters.getInt("numNodes"));
        generator.generate();
        return generator.getDag();
    }

    @Override
    public String getDescription() {
        return "Uniformly selected DAG";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numMeasures");
        parameters.add("maxDegree");
        parameters.add("maxIndegree");
        parameters.add("maxOutdegree");
        parameters.add("maxIterations");
        parameters.add("maxEdges");
        parameters.add("numNodes");
        return parameters;
    }
}
