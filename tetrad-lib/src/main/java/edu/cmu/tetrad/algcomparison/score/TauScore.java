package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian SEM BIC score.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Tau Score",
        command = "tauscore",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public class TauScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.TauScore tauScore;

        if (dataSet instanceof DataSet) {
            tauScore = new edu.cmu.tetrad.search.TauScore((DataSet) this.dataSet);
        } else if (dataSet instanceof ICovarianceMatrix) {
            tauScore = new edu.cmu.tetrad.search.TauScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        tauScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        tauScore.setStructurePrior(parameters.getDouble("structurePrior"));
        return tauScore;
    }

    @Override
    public String getDescription() {
        return "Tau Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
//        parameters.add("structurePrior");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
