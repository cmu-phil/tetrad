package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;

import java.util.List;
import java.util.Map;

/**
 * Conserative FCI.
 *
 * @author jdramsey
 */
public class Cfci implements Algorithm {
    private IndependenceWrapper test;

    public Cfci(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.Cfci search = new edu.cmu.tetrad.search.Cfci(test.getTest(dataSet, parameters));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "CFCI (Conservative Fast Causal Inference), using " + test.getDescription();
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
