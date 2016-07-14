package edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.independence.IndTestChooser;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Collections;
import java.util.List;

/**
 * PC using the Fisher Z test.
 * @author jdramsey
 */
public class Cpcs implements Algorithm {
    private IndTestType type;

    public Cpcs(IndTestType type) {
        this.type = type;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestChooser().getTest(type, dataSet, parameters);
        CpcStable pc = new CpcStable(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "CPC-Stable using the " + type + " test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return Collections.singletonList("alpha");
    }
}
