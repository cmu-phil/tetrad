package edu.cmu.tetrad.algcomparison.algorithms.discrete.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class DiscreteFgs2Bic implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        BicScore score = new BicScore(dataSet);
        score.setSamplePrior(parameters.getInt("samplePrior"));
        score.setSamplePrior(parameters.getInt("structurePrior"));
        Fgs2 fgs = new Fgs2(score);
//        fgs.setDepth(parameters.get("fgsDepth"));
        return fgs.search();
    }

    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    public String getDescription() {
        return "FGS2 using the BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("samplePrior");
        parameters.add("structurePrior");
        return parameters;
    }
}
