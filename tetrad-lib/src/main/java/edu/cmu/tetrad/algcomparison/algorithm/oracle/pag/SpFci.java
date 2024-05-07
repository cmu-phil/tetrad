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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * Adjusts GFCI to use a permutation algorithm (in this case SP) to do the initial steps of finding adjacencies and
 * unshielded colliders.
 * <p>
 * GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm for Latent Variable Models," JMLR 2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "SP-FCI",
        command = "spfci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class SpFci extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence wrapper used for testing the independence between variables in a dataset.
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
     * The SpFci class represents a specific algorithm for structural learning called "Conditional Independence
     * Test-based Fast Causal Inference" (SpFci). This class extends the AbstractBootstrapAlgorithm class and implements
     * the Algorithm, UsesScoreWrapper, and TakesIndependenceWrapper interfaces.
     */
    public SpFci() {
        // Used for reflection; do not delete.
    }

    /**
     * Constructor for the SpFci class.
     *
     * @param test  The IndependenceWrapper object to be used for the algorithm.
     * @param score The ScoreWrapper object to be used for the algorithm.
     */
    public SpFci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Executes a search algorithm to infer the causal graph structure from a given data model
     *
     * @param dataModel  The data model representing the observed variables and their relationships
     * @param parameters The parameters for the search algorithm
     * @return The inferred causal graph structure
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        edu.cmu.tetrad.search.SpFci search = new edu.cmu.tetrad.search.SpFci(this.test.getTest(dataModel, parameters), this.score.getScore(dataModel, parameters));
        search.setKnowledge(this.knowledge);
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
        search.setResolveAlmostCyclicPaths(parameters.getBoolean(Params.RESOLVE_ALMOST_CYCLIC_PATHS));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Object obj = parameters.get(Params.PRINT_STREAM);

        if (obj instanceof PrintStream) {
            search.setOut((PrintStream) obj);
        }

        return search.search();
    }

    /**
     * Returns the comparison graph created by converting a true directed graph into a partially directed acyclic graph
     * (PAG).
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph as a partially directed acyclic graph (PAG).
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
     *
     * @return The description of this algorithm.
     */
    @Override
    public String getDescription() {
        return "SP-FCI (SP-based FCI) using " + this.test.getDescription()
               + " or " + this.score.getDescription();
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The DataType of this algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns the list of parameters used by the method.
     *
     * @return A List of strings representing the parameters used by the method.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_RULE);
        params.add(Params.RESOLVE_ALMOST_CYCLIC_PATHS);
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);

        // Flags
        params.add(Params.VERBOSE);

        return params;
    }


    /**
     * Retrieves the knowledge object associated with this algorithm.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object associated with this algorithm.
     *
     * @param knowledge The knowledge object to be set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the IndependenceWrapper associated with this algorithm.
     *
     * @return The IndependenceWrapper object.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the IndependenceWrapper object for the algorithm.
     *
     * @param test the IndependenceWrapper object to be set.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Retrieves the ScoreWrapper object associated with this algorithm.
     *
     * @return The ScoreWrapper object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the ScoreWrapper object for the algorithm.
     *
     * @param score the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
