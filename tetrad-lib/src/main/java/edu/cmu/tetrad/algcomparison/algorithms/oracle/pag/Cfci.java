package edu.cmu.tetrad.algcomparison.algorithms.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Conserative FCI.
 * @author jdramsey
 */
public class Cfci implements Algorithm {
    private IndependenceWrapper test;

    public Cfci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.Cfci pc = new edu.cmu.tetrad.search.Cfci(test.getTest(dataSet, parameters));
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "CFCI, using " + test.getDescription();
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
