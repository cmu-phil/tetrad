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
        List<String> parameters = new ArrayList<String>();
        parameters.add("samplePrior");
        parameters.add("structurePrior");
        return parameters;
    }
}
