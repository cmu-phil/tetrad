package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.OtherPermAlgs;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * BOSS (Best Order Score Search).
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "SP",
        command = "sp",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class SP implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score = null;
    private IndependenceWrapper test;

    public SP() {
        // Used in reflection; do not delete.
    }

    public SP(ScoreWrapper score, IndependenceWrapper test) {
        this.score = score;
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Score score = this.score.getScore(dataModel, parameters);

            IndependenceTest test = this.test.getTest(dataModel, parameters);

            test.setVerbose(parameters.getBoolean(Params.VERBOSE));

            OtherPermAlgs otherPermAlgs;

            otherPermAlgs = new OtherPermAlgs(test, score);

            OtherPermAlgs.Method method = OtherPermAlgs.Method.SP;

            otherPermAlgs.setMethod(method);
            otherPermAlgs.setUsePearl(parameters.getBoolean(Params.GRASP_USE_VERMA_PEARL));
            otherPermAlgs.setVerbose(parameters.getBoolean(Params.VERBOSE));

            otherPermAlgs.bestOrder(score.getVariables());
            return otherPermAlgs.getGraph(true);
        } else {
            GRaSP algorithm = new GRaSP(this.score, this.test);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "SP (Sparsest Permutation) using " + test.getDescription() + " or " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();
        params.add(Params.GRASP_USE_VERMA_PEARL);
        params.add(Params.VERBOSE);
        return params;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

}
