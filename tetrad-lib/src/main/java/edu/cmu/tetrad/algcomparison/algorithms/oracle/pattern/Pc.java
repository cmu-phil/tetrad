package edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class Pc implements Algorithm, TakesInitialGraph {
    private IndependenceWrapper test;
    private Algorithm initialGraph = null;

    public Pc(IndependenceWrapper test) {
        this.test = test;
    }

    public Pc(IndependenceWrapper test, Algorithm initialGraph) {
        this.test = test;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.Pc pc = new edu.cmu.tetrad.search.Pc(test.getTest(dataSet, parameters));

        if (initial != null) {
            pc.setInitialGraph(initial);
        }

        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PC (\"Peter and Clark\") using " + test.getDescription()
                + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        return test.getParameters();
    }
}
