package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Sem BIC Score Linear",
        command = "sem-bic-linear",
        dataType = {DataType.Continuous}
)
public class SemBicScoreLinear implements ScoreWrapper {

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

        edu.cmu.tetrad.search.SemBicScoreLinear semBicScore
                = new edu.cmu.tetrad.search.SemBicScoreLinear((DataSet)_data);
        double penaltyDiscount = parameters.getDouble("penaltyDiscount");
        this.penaltyDiscount = penaltyDiscount;
        semBicScore.setPenaltyDiscount(penaltyDiscount);
        semBicScore.setNumInBootstrap(parameters.getInt("numInBootstrapForLinearityTest"));
        semBicScore.setNumBootstraps(parameters.getInt("numBootstrapsForLinearityTest"));
        semBicScore.setBootstrapAlpha(parameters.getDouble("alphaForLinearityTest"));

        return semBicScore;
    }

    @Override
    public String getDescription() {
        return "Sem BIC Score Linear";
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
        parameters.add("numInBootstrapForLinearityTest");
        parameters.add("numBootstrapsForLinearityTest");
        parameters.add("alphaForLinearityTest");

        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
