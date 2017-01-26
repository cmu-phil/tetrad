package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.Collections;
import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
public class Fang implements Algorithm {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;

    public Fang(IndependenceWrapper type) {
        this.test = type;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        dataSet = TimeSeriesUtils.createLagData((DataSet) dataSet, parameters.getInt("numLags"));
        IndependenceTest test = this.test.getTest(dataSet, parameters);
        IKnowledge knowledge = dataSet.getKnowledge();
        edu.cmu.tetrad.search.Fang search = new edu.cmu.tetrad.search.Fang(test,
                Collections.singletonList((DataSet) dataSet));
        search.setKnowledge(knowledge);
        search.setDepth(parameters.getInt("depth"));
        search.setR3Cutoff(parameters.getDouble("r3Cutoff"));
        search.setCollapseTiers(parameters.getBoolean("collapseTiers"));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "FANG (Fast Adjacency search followed by non-Gaussian orienttion) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("numLags");
        parameters.add("depth");
        parameters.add("r3Cutoff");
        parameters.add("collapseTiers");
        return parameters;
    }
}
