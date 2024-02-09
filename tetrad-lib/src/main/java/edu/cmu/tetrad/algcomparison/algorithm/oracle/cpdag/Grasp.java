package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * GRaSP (Greedy Relaxations of Sparsest Permutation)
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GRASP",
        command = "grasp",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Grasp implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs {
    private static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    /**
     * <p>Constructor for Grasp.</p>
     */
    public Grasp() {
        // Used in reflection; do not delete.
    }

    /**
     * <p>Constructor for Grasp.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public Grasp(IndependenceWrapper test, ScoreWrapper score) {
        this.score = score;
        this.test = test;
    }

    /** {@inheritDoc} */
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

            Score score = this.score.getScore(dataModel, parameters);
            IndependenceTest test = this.test.getTest(dataModel, parameters);

            test.setVerbose(parameters.getBoolean(Params.VERBOSE));
            edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test, score);

            grasp.setSeed(parameters.getLong(Params.SEED));
            grasp.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            grasp.setUncoveredDepth(parameters.getInt(Params.GRASP_SINGULAR_DEPTH));
            grasp.setNonSingularDepth(parameters.getInt(Params.GRASP_NONSINGULAR_DEPTH));
            grasp.setOrdered(parameters.getBoolean(Params.GRASP_ORDERED_ALG));
            grasp.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
            grasp.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_RASKUTTI_UHLER));
            grasp.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
            grasp.setAllowInternalRandomness(parameters.getBoolean(Params.ALLOW_INTERNAL_RANDOMNESS));
            grasp.setVerbose(parameters.getBoolean(Params.VERBOSE));

            grasp.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            grasp.setKnowledge(this.knowledge);
            grasp.bestOrder(score.getVariables());
            Graph graph = grasp.getGraph(parameters.getBoolean(Params.OUTPUT_CPDAG));
            LogUtilsSearch.stampWithScore(graph, score);
            LogUtilsSearch.stampWithBic(graph, dataModel);

            LogUtilsSearch.stampWithBic(graph, dataModel);

            return graph;
        } else {
            Grasp algorithm = new Grasp(this.test, this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);


            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "GRaSP (Greedy Relaxed Sparsest Permutation) using " + this.test.getDescription()
                + " or " + this.score.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GRASP_SINGULAR_DEPTH);
        params.add(Params.GRASP_NONSINGULAR_DEPTH);
        params.add(Params.GRASP_ORDERED_ALG);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.ALLOW_INTERNAL_RANDOMNESS);
        params.add(Params.TIME_LAG);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        return params;
    }

    /** {@inheritDoc} */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /** {@inheritDoc} */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /** {@inheritDoc} */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /** {@inheritDoc} */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }

    /** {@inheritDoc} */
    @Override
    public Knowledge getKnowledge() {
        return new Knowledge((Knowledge) knowledge);
    }

    /** {@inheritDoc} */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    /** {@inheritDoc} */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
