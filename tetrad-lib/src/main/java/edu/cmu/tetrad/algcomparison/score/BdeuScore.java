package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class BdeuScore implements ScoreWrapper {

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.BDeuScore score
                = new edu.cmu.tetrad.search.BDeuScore(dataSet);
        score.setSamplePrior(parameters.getDouble("samplePrior", 1));
        score.setStructurePrior(parameters.getDouble("structurePrior", 1));
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
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("samplePrior", 1);
        parameters.put("structurePrior", 1);
        return parameters;
    }
}
