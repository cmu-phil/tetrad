package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BssPc;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

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
        return params;
    }
}
