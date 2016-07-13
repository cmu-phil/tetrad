package edu.cmu.tetrad.algcomparison.algorithms.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CPC-Stable using the Fisher Z test.
 * @author jdramsey
 */
public class ContinuousCpcsFz implements Algorithm {

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestFisherZ(dataSet, parameters.getDouble("alpha"));
        CpcStable pc = new CpcStable(test);
        pc.setDepth(parameters.getInt("depth"));
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "CPC-Stable using the Fisher Z test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("depth");
        return parameters;
    }}
