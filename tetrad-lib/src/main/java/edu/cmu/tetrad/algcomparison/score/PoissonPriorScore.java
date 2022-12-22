package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.LinearGaussian;
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
 * Wrapper for the Poisson prior score (Bryan)
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Poisson Prior Score",
        command = "poisson-prior-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class PoissonPriorScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.PoissonPriorScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.PoissonPriorScore((DataSet) this.dataSet, parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES));
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.PoissonPriorScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setStructurePrior(parameters.getDouble(Params.SEM_BIC_STRUCTURE_PRIOR));

        return score;
    }

    @Override
    public String getDescription() {
        return "Poisson Prior Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.SEM_BIC_STRUCTURE_PRIOR);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}