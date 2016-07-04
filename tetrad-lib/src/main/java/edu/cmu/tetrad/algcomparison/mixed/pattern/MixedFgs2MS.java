package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.MixedBicScore;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFgs2MS implements Algorithm {
    public Graph search(DataSet Dk, Parameters parameters) {
        MixedBicScore score = new MixedBicScore(Dk);
        Fgs fgs = new Fgs(score);
        fgs.setDepth(parameters.getInt("fgsDepth"));
        return fgs.search();
    }


    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    @Override
    public String getDescription() {
        return "FGS2 using a mixed BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
