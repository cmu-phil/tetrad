package edu.cmu.tetrad.algcomparison.algorithms.oracle.pag;

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
 * FCI using the Fisher Z independence test.
 *
 * @author jdramsey
 */
public class Fci implements Algorithm {
    private IndTestType type;
    private Algorithm initialGraph = null;

    public Fci(IndTestType type) {
        this.type = type;
    }
    public Fci(IndTestType type, Algorithm initialGraph) {
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
        edu.cmu.tetrad.search.Fci cpc = new edu.cmu.tetrad.search.Fci(test);

        if (initial != null) {
            cpc.setInitialGraph(initial);
        }

        return cpc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    public String getDescription() {
        return "FCI using the " + type + " test" + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}
