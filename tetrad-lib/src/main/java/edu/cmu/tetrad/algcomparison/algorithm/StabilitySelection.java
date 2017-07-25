package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;

/**
 * Stability selection.
 *
 * @author jdramsey
 */
public class StabilitySelection implements Algorithm, TakesInitialGraph {
    static final long serialVersionUID = 23L;
    private final String parameter;
    private Algorithm algorithm;

    public StabilitySelection(Algorithm algorithm, String parameter, double low, double high) {
        if (low >= high) throw new IllegalArgumentException("Must have low < high");
        this.algorithm = algorithm;
        this.parameter = parameter;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        DataSet _dataSet = (DataSet) dataSet;

        double percentageB = parameters.getDouble("percentSubsampleSize");
        int numSubsamples = parameters.getInt("numSubsamples");

        Map<Edge, Integer> counts = new HashMap<>();

        for (int i = 0; i < numSubsamples; i++) {
            BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            DataSet sample = sampler.sample(_dataSet, (int) (percentageB * _dataSet.getNumRows()));
            Graph graph = algorithm.search(sample, parameters);

            for (Edge edge : graph.getEdges()) {
                increment(edge, counts);
            }
        }

        Graph out = new EdgeListGraph(dataSet.getVariables());
        double percentStability = parameters.getDouble("percentStability");

        for (Edge edge : counts.keySet()) {
            if (counts.get(edge) > percentStability * numSubsamples) {
                out.addEdge(edge);
            }
        }

        return out;
    }

    private void increment(Edge edge, Map<Edge, Integer> counts) {
        if (counts.get(edge) == null) {
            counts.put(edge, 0);
        }

        counts.put(edge, counts.get(edge) + 1);
    }


    @Override
    public Graph getComparisonGraph(Graph graph) {
        return algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "Stability selection for " + algorithm.getDescription();
    }

    @Override
    public DataType getDataType() {
        return algorithm.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        parameters.add("numSubsamples");
        parameters.add("percentSubsampleSize");
        parameters.add("percentStability");

        return parameters;
    }
}
