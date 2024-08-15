package edu.cmu.tetrad.algcomparison.algorithm.continuous.dag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the DAGMA algorithm. The reference is here:
 * <p>
 * Bello, K., Aragam, B., &amp; Ravikumar, P. (2022). Dagma: Learning dags via m-matrices and a log-determinant
 * acyclicity characterization. Advances in Neural Information Processing Systems, 35, 8226-8239.
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "DAGMA",
        command = "dagma",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
public class Dagma extends AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for Dagma.</p>
     */
    public Dagma() {
    }

    /**
     * Runs the DAGMA algorithm to search for a directed acyclic graph (DAG) in the given data model with the specified
     * parameters.
     *
     * @param dataModel  The data model to search.
     * @param parameters The parameters for the DAGMA algorithm.
     * @return The resulting graph, which represents a DAG.
     * @throws IllegalArgumentException If the data model is not a continuous dataset.
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        DataSet data = SimpleDataLoader.getContinuousDataSet(dataSet);

        edu.cmu.tetrad.search.Dagma search = new edu.cmu.tetrad.search.Dagma(data);
        search.setLambda1(parameters.getDouble(Params.LAMBDA1));
        search.setWThreshold(parameters.getDouble(Params.W_THRESHOLD));
        search.setCpdag(parameters.getBoolean(Params.CPDAG));
        Graph graph = search.search();
        TetradLogger.getInstance().log(graph.toString());
        LogUtilsSearch.stampWithBic(graph, dataModel);
        return graph;
    }

    /**
     * Retrieves the comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns the description of the DAGMA algorithm.
     *
     * @return the description of the DAGMA algorithm
     */
    public String getDescription() {
        return "DAGMA (DAGs via M-matrices for Acyclicity)";
    }

    /**
     * Retrieves the data type of the algorithm's output.
     *
     * @return The data type of the algorithm's output.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return A list of strings representing the parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.VERBOSE);
        parameters.add(Params.LAMBDA1);
        parameters.add(Params.W_THRESHOLD);
        parameters.add(Params.CPDAG);
        return parameters;
    }
}
