package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.log;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Sem BIC Score",
        command = "sem-bic",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public class SemBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;
    private double penaltyDiscount = 2.0;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        DataModel _data;

        if (parameters.getBoolean("doNonparanormalTransform")) {
            _data = DataUtils.getNonparanormalTransformed((DataSet) dataSet);
        } else {
            _data = dataSet;
        }

        edu.cmu.tetrad.search.SemBicScore semBicScore
                = new edu.cmu.tetrad.search.SemBicScore(DataUtils.getCovMatrix(_data));
        double penaltyDiscount = parameters.getDouble("penaltyDiscount");
        this.penaltyDiscount = penaltyDiscount;
        semBicScore.setPenaltyDiscount(penaltyDiscount);
        return semBicScore;
    }

    @Override
    public String getDescription() {
        return "Sem BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("doNonparanormalTransform");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
