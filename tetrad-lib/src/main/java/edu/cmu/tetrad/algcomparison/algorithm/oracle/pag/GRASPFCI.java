package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GraspFci;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Adjusts GFCI to use a permutation algorithm (such as GRaSP) to do the initial
 * steps of finding adjacencies and unshielded colliders.
 * <p>
 * GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm
 * for Latent Variable Models," JMLR 2016.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GRaSP-FCI",
        command = "graspfci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class GRASPFCI implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public GRASPFCI() {
        // Used for reflection; do not delete.
    }

    public GRASPFCI(ScoreWrapper score, IndependenceWrapper test) {
        this.test = test;
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            GraspFci search = new GraspFci(this.test.getTest(dataSet, parameters), this.score.getScore(dataSet, parameters));
            search.setKnowledge(this.knowledge);
            search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));

            search.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            search.setUncoveredDepth(parameters.getInt(Params.GRASP_UNCOVERED_DEPTH));
            search.setNonSingularDepth(parameters.getInt(Params.GRASP_NONSINGULAR_DEPTH));
            search.setOrdered(parameters.getBoolean(Params.GRASP_ORDERED_ALG));
            search.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
            search.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_VERMA_PEARL));
            search.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            search.setAllowRandomnessInsideAlgorithm(parameters.getBoolean(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setCacheScores(parameters.getBoolean(Params.CACHE_SCORES));

            search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            search.setKnowledge(search.getKnowledge());

            Object obj = parameters.get(Params.PRINT_STREAM);

            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            GRASPFCI algorithm = new GRASPFCI(this.score, this.test);
            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(data.getKnowledge());

            ResamplingEdgeEnsemble edgeEnsemble;

            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1));
            }
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "GRASP-FCI (GRaSP-based FCI) using " + this.test.getDescription()
                + " or " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.MAX_PATH_LENGTH);

        // Flags
        params.add(Params.GRASP_UNCOVERED_DEPTH);
        params.add(Params.GRASP_NONSINGULAR_DEPTH);
        params.add(Params.GRASP_ORDERED_ALG);
//        params.add(Params.GRASP_USE_SCORE);
        params.add(Params.GRASP_USE_VERMA_PEARL);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);
        params.add(Params.GRASP_DEPTH);

        return params;
    }


    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

}
