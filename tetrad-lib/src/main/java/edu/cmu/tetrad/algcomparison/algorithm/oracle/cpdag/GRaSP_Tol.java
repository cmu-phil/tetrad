package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Grasp;
import edu.cmu.tetrad.search.GraspTol;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * GRaSP (Greedy Relaxations of Sparsest Permutation)
 *
 * @author bryanandrews
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GRaSP-tol",
        command = "grasp-tol",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class GRaSP_Tol implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score = null;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();

    public GRaSP_Tol() {
        // Used in reflection; do not delete.
    }

    public GRaSP_Tol(ScoreWrapper score, IndependenceWrapper test) {
        this.score = score;
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            Score score = this.score.getScore(dataSet, parameters);
            IndependenceTest test = this.test.getTest(dataSet, parameters);

            test.setVerbose(parameters.getBoolean(Params.VERBOSE));
            GraspTol grasp = new GraspTol(test, score);

            grasp.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            grasp.setUncoveredDepth(parameters.getInt(Params.GRASP_UNCOVERED_DEPTH));
            grasp.setNonSingularDepth(parameters.getInt(Params.GRASP_NONSINGULAR_DEPTH));
            grasp.setOrdered(parameters.getBoolean(Params.GRASP_ORDERED_ALG));
            grasp.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
            grasp.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_VERMA_PEARL));
            grasp.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            grasp.setAllowRandomnessInsideAlgorithm(parameters.getBoolean(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM));
            grasp.setVerbose(parameters.getBoolean(Params.VERBOSE));
            grasp.setCacheScores(parameters.getBoolean(Params.CACHE_SCORES));
            grasp.setTol(parameters.getInt(Params.MAX_DEGREE));

            grasp.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            grasp.setKnowledge(knowledge);
            grasp.bestOrder(score.getVariables());
            return grasp.getGraph(parameters.getBoolean(Params.OUTPUT_CPDAG));
        } else {
            GRaSP_Tol algorithm = new GRaSP_Tol(score, test);

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(knowledge);

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }

            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

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
        return "GRaSP-tol (Greedy Relaxed Sparsest Permutation) using " + test.getDescription()
                + " or " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GRASP_UNCOVERED_DEPTH);
        params.add(Params.GRASP_NONSINGULAR_DEPTH);
        params.add(Params.GRASP_ORDERED_ALG);
//        params.add(Params.GRASP_USE_SCORE);
        params.add(Params.GRASP_USE_VERMA_PEARL);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM);
        params.add(Params.MAX_DEGREE);
//        params.add(Params.CACHE_SCORES);
//        params.add(Params.OUTPUT_CPDAG);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

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

    @Override
    public IKnowledge getKnowledge() {
        return knowledge.copy();
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge.copy();
    }
}
