package edu.cmu.tetrad.algcomparison.discrete.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteFgsBic implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        BicScore score = new BicScore(dataSet);
        score.setSamplePrior(parameters.get("samplePrior").doubleValue());
        score.setSamplePrior(parameters.get("structurePrior").doubleValue());
        Fgs fgs = new Fgs(score);
        fgs.setDepth(parameters.get("fgsDepth").intValue());
        return fgs.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "FGS using the BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }
}
