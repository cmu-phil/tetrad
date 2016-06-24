package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcCg implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestScore(new ConditionalGaussianScore(ds));
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC with the conditional Gaussian score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

}

