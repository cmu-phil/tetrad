package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian Extended BIC score (Chen and Chen).
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "EBIC Score",
        command = "ebic-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public class EbicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.EbicScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.EbicScore((DataSet) this.dataSet);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.EbicScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setGamma(parameters.getDouble(Params.EBIC_GAMMA));
        score.setCorrelationThreshold(parameters.getDouble(Params.CORRELATION_THRESHOLD));
        return score;
    }

    @Override
    public String getDescription() {
        return "EBIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.EBIC_GAMMA);
//        parameters.add(Params.CORRELATION_THRESHOLD);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}
