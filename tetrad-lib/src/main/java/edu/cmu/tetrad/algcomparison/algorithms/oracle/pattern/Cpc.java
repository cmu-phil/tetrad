package edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.independence.IndTestChooser;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CPC using the Chi Square independence test.
 * @author jdramsey
 */
public class Cpc implements Algorithm {
    private IndTestType type;
    private Algorithm initialGraph = null;

    public Cpc(IndTestType type) {
        this.type = type;
    }
    public Cpc(IndTestType type, Algorithm initialGraph) {
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
        edu.cmu.tetrad.search.Cpc cpc = new edu.cmu.tetrad.search.Cpc(test);

        if (initial != null) {
            cpc.setInitialGraph(initial);
        }

        return cpc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "CPC using the " + type + " test" + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return type.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
