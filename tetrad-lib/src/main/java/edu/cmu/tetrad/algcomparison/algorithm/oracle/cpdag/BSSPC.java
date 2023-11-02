package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BssPc;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "BSSPC",
        command = "bsspc",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
public class BSSPC implements Algorithm{
    private static final long serialVersionUID = 23L;


    public BSSPC() {
        // Used in reflection; do not delete.
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        DataSet data = SimpleDataLoader.getContinuousDataSet(dataModel);
        BssPc search = new BssPc(data);
        search.setReps(parameters.getInt(Params.NUMBER_RESAMPLING, 10));
        search.setDoubleThreshold(parameters.getDouble(Params.THRESHOLD_B, 0.8));
        search.setTripleThreshold(parameters.getDouble(Params.W_THRESHOLD, 0.2));
        search.setLambda(parameters.getDouble(Params.PENALTY_DISCOUNT, 2));
        search.setBes(parameters.getBoolean(Params.USE_BES, false));
        search.setRestarts(parameters.getInt(Params.NUM_STARTS, 1));
        search.setThreads(parameters.getInt(Params.NUM_THREADS, 1));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() { return "BSS-PC"; }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();
        // Parameters

        params.add(Params.NUMBER_RESAMPLING);
        params.add(Params.THRESHOLD_B);
        params.add(Params.W_THRESHOLD);
        params.add(Params.PENALTY_DISCOUNT);

        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.NUM_THREADS);

        return params;
    }
}
