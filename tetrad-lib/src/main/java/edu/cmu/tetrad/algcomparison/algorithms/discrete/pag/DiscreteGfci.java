package edu.cmu.tetrad.algcomparison.algorithms.discrete.pag;

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
public class DiscreteGfci implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        BDeuScore score = new BDeuScore(dataSet);
        score.setSamplePrior(parameters.getDouble("samplePrior"));
        score.setSamplePrior(parameters.getDouble("structurePrior"));
        GFci pc = new GFci(score);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }

    public String getDescription() {
        return "GFCI using the BDeu score";
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
