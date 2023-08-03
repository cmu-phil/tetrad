package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.ZsbScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian Extended BIC score (Chen and Chen).
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "ZS Bound Score",
        command = "zsbound-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class ZhangShenBoundScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override

    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        ZsbScore score;

        if (dataSet instanceof DataSet) {
            score = new ZsbScore((DataSet) this.dataSet, precomputeCovariances);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new ZsbScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setRiskBound(parameters.getDouble(Params.ZS_RISK_BOUND));

        return score;
    }

    @Override
    public String getDescription() {
        return "Zhang-Shen Bound Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ZS_RISK_BOUND);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}
