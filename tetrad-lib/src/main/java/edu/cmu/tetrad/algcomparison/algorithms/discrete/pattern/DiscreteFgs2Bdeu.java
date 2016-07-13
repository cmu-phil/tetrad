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
 * FGS2 using the BDeu Score.
 * @author jdramsey
 */
public class DiscreteFgs2Bdeu implements Algorithm {

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        BDeuScore score = new BDeuScore(dataSet);
        score.setSamplePrior(parameters.getInt("samplePrior"));
        score.setSamplePrior(parameters.getInt("structurePrior"));
        Fgs2 fgs = new Fgs2(score);
//        fgs.setDepth(parameters.get("fgsDepth"));
        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "FGS2 using the BDeu score";
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
