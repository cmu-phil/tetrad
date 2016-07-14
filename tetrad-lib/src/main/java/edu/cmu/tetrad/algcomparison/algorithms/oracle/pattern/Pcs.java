package edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.independence.IndTestChooser;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * PC-Stable using the Chi Square independence test.
 * @author jdramsey
 */
public class Pcs implements Algorithm {
    private IndTestType type;
    private Algorithm initialGraph = null;

    public Pcs(IndTestType type) {
        this.type = type;
    }
    public Pcs(IndTestType type, Algorithm initialGraph) {
        this.type = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        IndependenceTest test = new IndTestChooser().getTest(type, dataSet, parameters);
        edu.cmu.tetrad.search.PcStable pcs = new edu.cmu.tetrad.search.PcStable(test);

        if (initial != null) {
            pcs.setInitialGraph(initial);
        }

        return pcs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PCS using the " + type + " test" + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
