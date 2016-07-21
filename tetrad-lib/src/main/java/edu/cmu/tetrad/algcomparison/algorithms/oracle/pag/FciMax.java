package edu.cmu.tetrad.algcomparison.algorithms.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * FciMax Max.
 * @author jdramsey
 */
public class FciMax implements Algorithm {
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;

    public FciMax(IndependenceWrapper test) {
        this.test = test;
    }
    public FciMax(IndependenceWrapper test, Algorithm initialGraph) {
        this.test = test;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.FciMax fci = new edu.cmu.tetrad.search.FciMax(test.getTest(dataSet, parameters));

        if (initial != null) {
            fci.setInitialGraph(initial);
        }

        return fci.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    public String getDescription() {
        return "FCI-MAX using " + test.getDescription() +
                (initialGraph != null ? " with initial graph from " +
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
