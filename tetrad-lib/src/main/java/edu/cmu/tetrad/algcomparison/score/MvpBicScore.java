package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.MVPScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Experimental;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for MVP BIC score.
 *
 * @author jdramsey
 */
public class MvpBicScore implements ScoreWrapper, Experimental {
    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        double structurePrior = parameters.getDouble("structurePrior");
        int fDegree = parameters.getInt("fDegree");
        return new MVPScore(DataUtils.getMixedDataSet(dataSet), structurePrior, fDegree);
    }

    @Override
    public String getDescription() {
        return "Conditional Gaussian BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("structurePrior");
        parameters.add("fDegree");
        return parameters;
    }
}
