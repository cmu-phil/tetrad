package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs2;

import java.util.ArrayList;
import java.util.List;

/**
 * Tanh.
 *
 * @author jdramsey
 */
public class Tanh implements Algorithm, TakesInitialGraph {
    static final long serialVersionUID = 23L;
    private Algorithm initialGraph = null;

    public Tanh(Algorithm initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Graph initial = initialGraph.search(dataSet, parameters);

        if (initial != null) {
            initial = initialGraph.search(dataSet, parameters);
        } else {
            throw new IllegalArgumentException("This algorithm needs both data and a graph source as inputs; it \n" +
                    "will orient the edges in the input graph using the data");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(DataUtils.getContinuousDataSet(dataSet));

        Lofs2 lofs = new Lofs2(initial, dataSets);
        lofs.setRule(Lofs2.Rule.Tanh);

        return lofs.orient();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "Tahn" + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return initialGraph.getParameters();
    }
}
