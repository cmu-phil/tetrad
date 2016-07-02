package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.GPc;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedGpcCg implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(dataSet);
        GPc pc = new GPc(score);
        pc.setHeuristicSpeedup(true);
        pc.setFgsDepth(parameters.getInt("depth"));
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "GPC using the Conditional Gaussian score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
