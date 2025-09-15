package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FASK-PW (pairwise).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FASK-PW",
        command = "fask-pw",
        algoType = AlgType.orient_pairwise,
        dataType = DataType.Continuous
)
@Bootstrapping
public class FaskPw extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The algorithm to use for the initial graph.
     */
    private Algorithm algorithm;

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * <p>Constructor for FaskPw.</p>
     */
    public FaskPw() {
    }

    /**
     * <p>Constructor for FaskPw.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public FaskPw(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Runs the search algorithm using the given data model and parameters.
     *
     * @param dataModel  the data model to be used for the search
     * @param parameters the parameters to be used for the search
     * @return the resulting graph
     * @throws IllegalArgumentException if the data model is not a continuous dataset or if the algorithm requires both
     *                                  data and a graph source as inputs
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        if (this.externalGraph == null) {
            this.externalGraph = this.algorithm.search(dataSet, parameters);
        }

        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        if (this.externalGraph == null) {
            throw new IllegalArgumentException(
                    "This FASK-PW (pairwise) algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data");
        }

        Fask fask = new Fask(dataSet, new SemBicScore(dataSet, precomputeCovariances));
        fask.setExternalGraph(this.externalGraph);
        fask.setExtraEdgeThreshold(Double.POSITIVE_INFINITY);

        return fask.search();
    }

    /**
     * Returns a comparison graph based on the provided true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return A comparison graph based on the provided true directed graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a description of the RSkew algorithm.
     *
     * @return A description of the RSkew algorithm.
     */
    @Override
    public String getDescription() {
        return "RSkew" + (this.algorithm != null ? " with initial graph from "
                                                   + this.algorithm.getDescription() : "");
    }

    /**
     * Retrieves the data type of the dataset.
     *
     * @return the data type of the dataset
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters required for the algorithm.
     *
     * @return a list of parameter names that are used in the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);

        return parameters;
    }

    /**
     * Sets the external graph to be used by the algorithm.
     *
     * @param algorithm the algorithm object representing the external graph
     * @throws IllegalArgumentException if the algorithm parameter is null
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This FASK-PW (pairwise) algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }
}
