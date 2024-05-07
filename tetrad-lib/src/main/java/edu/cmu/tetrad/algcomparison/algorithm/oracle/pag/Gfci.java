package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
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
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The Gfci class represents the Greedy Fast Causal Inference algorithm.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GFCI",
        command = "gfci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class Gfci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, UsesScoreWrapper, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The Gfci class represents the Greedy Fast Causal Inference algorithm.
     */
    public Gfci() {
    }

    /**
     * Constructs a new instance of Gfci with the given IndependenceWrapper and ScoreWrapper.
     *
     * @param test  The IndependenceWrapper object to associate with this Gfci instance.
     * @param score The ScoreWrapper object to associate with this Gfci instance.
     */
    public Gfci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs the search algorithm to infer the causal graph given a dataset and specified parameters.
     *
     * @param dataModel  The dataset containing the observational data.
     * @param parameters The parameters to configure the search algorithm.
     * @return The inferred causal graph.
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        GFci search = new GFci(this.test.getTest(dataModel, parameters), this.score.getScore(dataModel, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
        search.setResolveAlmostCyclicPaths(parameters.getBoolean(Params.RESOLVE_ALMOST_CYCLIC_PATHS));
        search.setPossibleMsepSearchDone(parameters.getBoolean((Params.POSSIBLE_MSEP_DONE)));
        search.setNumThreads(parameters.getInt(Params.NUM_THREADS));

        Object obj = parameters.get(Params.PRINT_STREAM);
        if (obj instanceof PrintStream printStream) {
            search.setOut(printStream);
        }

        return search.search();
    }

    /**
     * Retrieves the comparison graph by transforming the true directed graph (if there is one) into a partially
     * directed acyclic graph (PAG).
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph in the form of a partially directed acyclic graph (PAG).
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /**
     * Returns a description of the GFCI (Greedy Fast Causal Inference) algorithm using the description of the
     * independence test and score associated with it.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "GFCI (Greedy Fast Causal Inference) using " + this.test.getDescription()
               + " and " + this.score.getDescription();
    }

    /**
     * Retrieves the data type required for the search algorithm.
     *
     * @return The data type required for the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns a list of parameters used to configure the search algorithm.
     *
     * @return The list of parameters used to configure the search algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.DEPTH);
        parameters.add(Params.MAX_DEGREE);
        parameters.add(Params.MAX_PATH_LENGTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.DO_DISCRIMINATING_PATH_RULE);
        parameters.add(Params.POSSIBLE_MSEP_DONE);
        parameters.add(Params.RESOLVE_ALMOST_CYCLIC_PATHS);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.NUM_THREADS);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Retrieves the Knowledge object associated with this instance.
     *
     * @return The Knowledge object associated with this instance.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the Knowledge object associated with this instance.
     *
     * @param knowledge The Knowledge object to be set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the ScoreWrapper associated with this instance.
     *
     * @return The ScoreWrapper associated with this instance.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for the algorithm.
     *
     * @param score the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Returns the independence wrapper associated with this instance.
     *
     * @return The independence wrapper.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper for the algorithm.
     *
     * @param test the independence wrapper to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
