package edu.cmu.tetrad.algcomparison.continuous.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.mb.Mmhc;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousMmhc implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        SemBicScore score = new SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        score.setPenaltyDiscount(parameters.get("alpha").doubleValue());
        IndependenceTest test = new IndTestScore(score);
        Mmhc pc = new Mmhc(test, dataSet);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "MMHC using the SEM BIC score. (Not optimized.)";
    }
}
