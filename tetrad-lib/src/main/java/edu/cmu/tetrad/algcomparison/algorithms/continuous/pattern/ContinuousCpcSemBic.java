package edu.cmu.tetrad.algcomparison.algorithms.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CPC using the SEM BIC score as an independence test.
 * @author jdramsey
 */
public class ContinuousCpcSemBic implements Algorithm {

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndependenceTest test = new IndTestScore(score);
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "CPC using the SEM BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        return parameters;
    }
}
