package edu.cmu.tetrad.algcomparison.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousCpcCG implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(dataSet);
        IndependenceTest test = new IndTestScore(score);
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "PC using the Conditional Gaussian score";
    }


    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }
}
