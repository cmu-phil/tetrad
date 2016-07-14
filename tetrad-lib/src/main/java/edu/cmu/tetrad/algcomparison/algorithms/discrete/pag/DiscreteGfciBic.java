package edu.cmu.tetrad.algcomparison.algorithms.discrete.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BicScore;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;

import java.util.ArrayList;
import java.util.List;

/**
 * GFCI using the BDEU score.
 * @author jdramsey
 */
public class DiscreteGfciBic implements Algorithm {

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        BicScore score = new BicScore(dataSet);
        score.setSamplePrior(parameters.getDouble("samplePrior"));
        score.setSamplePrior(parameters.getDouble("structurePrior"));
        GFci pc = new GFci(score);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
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
