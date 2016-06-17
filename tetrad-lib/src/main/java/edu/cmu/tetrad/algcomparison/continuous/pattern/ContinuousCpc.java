package edu.cmu.tetrad.algcomparison.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousCpc implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestFisherZ(dataSet, parameters.get("alpha").doubleValue());
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC using the Fisher Z test";
    }}
