package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * RFCI.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RFCI",
        command = "rfci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class Rfci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * Represents the knowledge variable. This variable stores the knowledge associated with the given algorithm. The
     * type of the variable is Knowledge.
     *
     * @see Knowledge
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Initializes a new instance of the Rfci class.
     */
    public Rfci() {
    }

    /**
     * Creates an instance of Rfci with the given IndependenceWrapper.
     *
     * @param test The IndependenceWrapper to be used.
     */
    public Rfci(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Runs the search algorithm on the given data model and parameters.
     *
     * @param dataModel  The data model to search on.
     * @param parameters The parameters for the search.
     * @return The resulting graph from the search algorithm.
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

        edu.cmu.tetrad.search.Rfci search = new edu.cmu.tetrad.search.Rfci(this.test.getTest(dataModel, parameters));
        search.setKnowledge(this.knowledge);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        return search.search();
    }

    /**
     * Returns a comparison graph based on the provided true directed graph.
     *
     * @param graph The true DAG, if there is one.
     * @return A comparison graph obtained by transforming the true graph into a partially directed acyclic graph (PAG).
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /**
     * Returns a short, one-line description of this algorithm.
     *
     * @return A short description of this algorithm.
     */
    public String getDescription() {
        return "RFCI (Really Fast Causal Inference) using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.DEPTH);
        parameters.add(Params.MAX_PATH_LENGTH);
        parameters.add(Params.TIME_LAG);

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
