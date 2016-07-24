package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class DiscreteBicScore implements ScoreWrapper {
    private DataSet dataSet = null;
    private Score score = null;

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        if (dataSet != this.dataSet) {
            this.dataSet = dataSet;
            edu.cmu.tetrad.search.BicScore score
                    = new edu.cmu.tetrad.search.BicScore(dataSet);
            score.setSamplePrior(parameters.getDouble("samplePrior"));
            score.setStructurePrior(parameters.getDouble("structurePrior"));
            this.score = score;
        }
        return score;
    }

    @Override
    public String getDescription() {
        return "Disrete BIC Score";
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
