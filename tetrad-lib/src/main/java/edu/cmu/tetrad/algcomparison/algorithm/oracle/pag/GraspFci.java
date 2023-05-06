package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.GraphUtilsSearch.dagToPag;


/**
 * Adjusts GFCI to use a permutation algorithm (such as BOSS-Tuck) to do the initial
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
        name = "GRASP-FCI",
        command = "grasp-fci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class GraspFci implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    public GraspFci() {
        // Used for reflection; do not delete.
    }

    public GraspFci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (parameters.getInt(Params.TIME_LAG) > 0) {
                DataSet dataSet = (DataSet) dataModel;
                DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                dataModel = timeSeries;
                knowledge = timeSeries.getKnowledge();
            }

            IndependenceTest test = this.test.getTest(dataModel, parameters);
            Score score = this.score.getScore(dataModel, parameters);

            test.setVerbose(parameters.getBoolean(Params.VERBOSE));
            edu.cmu.tetrad.search.GraspFci search = new edu.cmu.tetrad.search.GraspFci(test, score);

            // GRaSP
            search.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            search.setSingularDepth(parameters.getInt(Params.GRASP_SINGULAR_DEPTH));
            search.setNonSingularDepth(parameters.getInt(Params.GRASP_NONSINGULAR_DEPTH));
            search.setOrdered(parameters.getBoolean(Params.GRASP_ORDERED_ALG));
            search.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
            search.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_RASKUTTI_UHLER));
            search.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            search.setNumStarts(parameters.getInt(Params.NUM_STARTS));

            // FCI
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));

            // General
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            search.setKnowledge(this.knowledge);

            Object obj = parameters.get(Params.PRINT_STREAM);

            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            GraspFci algorithm = new GraspFci(this.test, this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return dagToPag(graph);
    }

    @Override
    public String getDescription() {
        return "GRASP-FCI (GRaSP FCI) using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // GRaSP
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GRASP_SINGULAR_DEPTH);
        params.add(Params.GRASP_NONSINGULAR_DEPTH);
        params.add(Params.GRASP_ORDERED_ALG);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);

        // FCI
        params.add(Params.DEPTH);
        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_RULE);
        params.add(Params.POSSIBLE_DSEP_DONE);

        // General
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);

        return params;
    }


    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
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

    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
