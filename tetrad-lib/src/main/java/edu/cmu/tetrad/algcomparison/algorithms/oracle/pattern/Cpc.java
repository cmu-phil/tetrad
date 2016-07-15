package edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndTestWrapper;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
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
    private IndTestWrapper test;
    private Algorithm initialGraph = null;

    public Cpc(IndTestWrapper type) {
        this.test = type;
    }
    public Cpc(IndTestWrapper type, Algorithm initialGraph) {
        this.test = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.Cpc cpc = new edu.cmu.tetrad.search.Cpc(test.getTest(dataSet, parameters));

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
        return "CPC using the " + test + " test" + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
