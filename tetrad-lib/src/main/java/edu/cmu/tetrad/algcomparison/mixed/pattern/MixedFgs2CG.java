package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.Fgs2;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * @author jdramsey
 */
public class MixedFgs2CG implements Algorithm {
    public Graph search(DataSet Dk, Parameters parameters) {
        ConditionalGaussianScore score = new ConditionalGaussianScore(Dk);
        Fgs2 fgs = new Fgs2(score);
//        fgs.setHeuristicSpeedup(false);
//        fgs.setDepth(parameters.get("fgsDepth"));
        return fgs.search();
    }


    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    @Override
    public String getDescription() {
        return "FGS2 with the conditional Gaussian score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
