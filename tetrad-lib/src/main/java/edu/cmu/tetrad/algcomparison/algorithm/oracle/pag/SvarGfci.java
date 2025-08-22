package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.TimeSeries;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.TsDagToPag;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * SvarGfci class is an implementation of the SVAR GFCI algorithm. It is used to learn causal relationships from time
 * series data.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "SvarGFCI",
        command = "svar-gfci",
        algoType = AlgType.allow_latent_common_causes
)
@TimeSeries
@Bootstrapping
public class SvarGfci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        TakesScoreWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

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
    private Knowledge knowledge;

    /**
     * <p>Constructor for SvarGfci.</p>
     */
    public SvarGfci() {
    }

    /**
     * <p>Constructor for SvarGfci.</p>
     *
     * @param type  a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     * @param score a {@link edu.cmu.tetrad.algcomparison.score.ScoreWrapper} object
     */
    public SvarGfci(IndependenceWrapper type, ScoreWrapper score) {
        this.test = type;
        this.score = score;
    }

    /**
     * Runs a search algorithm on the given data set using the specified parameters.
     *
     * @param dataModel  the data set containing the variables to search over
     * @param parameters the parameters specifying the search configuration
     * @return the resulting graph representing the discovered relationships
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
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

        dataModel.setKnowledge(this.knowledge);
        edu.cmu.tetrad.search.SvarGfci search = new edu.cmu.tetrad.search.SvarGfci(this.test.getTest(dataModel, parameters),
                this.score.getScore(dataModel, parameters));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return search.search();
    }

    /**
     * Returns a comparison graph based on the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    /**
     * Returns a description of this method.
     *
     * @return The description of this method.
     */
    public String getDescription() {
        return "SavrGFCI (SVAR GFCI) using " + this.test.getDescription() + " and " + this.score.getDescription();
    }

    /**
     * Returns the data type that this method requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by this method.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns the list of parameters required by this method. The parameters include: - FAITHFULNESS_ASSUMED -
     * MAX_INDEGREE - TIME_LAG - VERBOSE
     *
     * @return the list of parameters required by this method
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.MAX_INDEGREE);
        parameters.add(Params.TIME_LAG);
//        parameters.add(Params.PRINT_STREAM);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Returns the knowledge associated with this object.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this object.
     *
     * @param knowledge the knowledge object to set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the IndependenceWrapper object associated with this algorithm.
     *
     * @return The IndependenceWrapper object.
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
     * Sets the score wrapper for the algorithm.
     *
     * @param score the score wrapper to set
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}
