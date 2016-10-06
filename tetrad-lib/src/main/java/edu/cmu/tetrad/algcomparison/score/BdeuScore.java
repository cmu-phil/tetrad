package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class BdeuScore implements ScoreWrapper {
    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.BDeuScore score
                = new edu.cmu.tetrad.search.BDeuScore(DataUtils.getDiscreteDataSet(dataSet));
        score.setSamplePrior(parameters.getDouble("samplePrior"));
        score.setStructurePrior(parameters.getDouble("structurePrior"));
        return score;
    }

    @Override
    public String getDescription() {
        return "BDeu Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("samplePrior");
        parameters.add("structurePrior");
        return parameters;
    }
}
