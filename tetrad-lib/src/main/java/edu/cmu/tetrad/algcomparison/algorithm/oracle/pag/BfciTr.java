package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.SearchGraphUtils.dagToPag;


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
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BFCI-TR",
//        command = "bfci-tr",
//        algoType = AlgType.allow_latent_common_causes
//)
//@Bootstrapping
//@Experimental
public class BfciTr implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    public BfciTr() {
        // Used for reflection; do not delete.
    }

    public BfciTr(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (parameters.getInt(Params.TIME_LAG) > 0) {
                DataSet dataSet = (DataSet) dataModel;
                DataSet timeSeries = TimeSeriesUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                dataModel = timeSeries;
                knowledge = timeSeries.getKnowledge();
            }

            edu.cmu.tetrad.search.BfciTr search = new edu.cmu.tetrad.search.BfciTr(this.test.getTest(dataModel, parameters), this.score.getScore(dataModel, parameters));

            search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            dataModel.setKnowledge(this.knowledge);

            Object obj = parameters.get(Params.PRINT_STREAM);

            if (obj instanceof PrintStream) {
                search.setOut((PrintStream) obj);
            }

            return search.search();
        } else {
            BfciTr algorithm = new BfciTr(this.test, this.score);
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
        return "BFCI-TR (Best-order FCI with triangle reduce rule using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.BOSS_ALG);
        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_RULE);
        params.add(Params.GRASP_USE_SCORE);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.POSSIBLE_DSEP_DONE);
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

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
