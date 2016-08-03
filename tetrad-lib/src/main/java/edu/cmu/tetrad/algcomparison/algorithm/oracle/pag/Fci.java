package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;

import java.util.List;
import java.util.Map;

/**
 * FCI.
 *
 * @author jdramsey
 */
public class Fci implements Algorithm, TakesInitialGraph {
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;

    public Fci(IndependenceWrapper type) {
        this.test = type;
    }

    public Fci(IndependenceWrapper type, Algorithm initialGraph) {
        this.test = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.Fci search = new edu.cmu.tetrad.search.Fci(test.getTest(dataSet, parameters));

        if (initial != null) {
            search.setInitialGraph(initial);
        }

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    public String getDescription() {
        return "FCI (Fast Causal Inference) using " + test.getDescription() +
                (initialGraph != null ? " with initial graph from " +
                        initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public Map<String, Object> getParameters() {
        return test.getParameters();
    }
}
