package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.Fgs2;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Map;

/**
 * @author jdramsey
 */
public class MixedFgsCG implements Algorithm {
    public Graph search(DataSet Dk, Map<String, Number> parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(Dk);
        Fgs2 fgs = new Fgs2(score);
//        fgs.setHeuristicSpeedup(false);
//        fgs.setDepth(parameters.get("fgsDepth").intValue());
        return fgs.search();
    }


    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    @Override
    public String getDescription() {
        return "FGS using the conditional Gaussian BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
