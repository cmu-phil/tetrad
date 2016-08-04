package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.CpcStable;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class Cpcs implements Algorithm {
    private IndependenceWrapper test;

    public Cpcs(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        CpcStable search = new CpcStable(test.getTest(dataSet, parameters));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "CPC-Stable (Conservative \"Peter and Clark\" Stable) using " + test.getDescription();
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
