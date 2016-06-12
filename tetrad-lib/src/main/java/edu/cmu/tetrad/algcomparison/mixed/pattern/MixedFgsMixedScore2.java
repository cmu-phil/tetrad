package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.MixedBicScore;
import edu.cmu.tetrad.search.MixedBicScore2;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFgsMixedScore2 implements Algorithm {
    public Graph search(DataSet Dk, Map<String, Number> parameters) {
        MixedBicScore2 score = new MixedBicScore2(Dk);
        Fgs fgs = new Fgs(score);
        return fgs.search();
    }


    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    @Override
    public String getDescription() {
        return "Runs FGS using a mixed BIC score assuming conditional Gaussian.";
    }
}
