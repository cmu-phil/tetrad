package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * RSkew.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RSkew",
        command = "r-skew",
        algoType = AlgType.orient_pairwise,
        dataType = DataType.Continuous
)
@Bootstrapping
public class Rskew extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

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
     * <p>Constructor for Rskew.</p>
     */
    public Rskew() {
    }

    /**
     * <p>Constructor for Rskew.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public Rskew(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Runs the search algorithm using the provided data model and parameters.
     *
     * @param dataModel  the data model to be used for the search
     * @param parameters the parameters for the search algorithm
     * @return the resulting graph from the search algorithm
     * @throws IllegalArgumentException if the data model is not a continuous dataset or if the search algorithm
     *                                  requires both data and a graph source as inputs
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        Graph graph = this.algorithm.search(dataSet, parameters);

        if (graph != null) {
            this.externalGraph = graph;
        } else {
            throw new IllegalArgumentException("This RSkew algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

        Lofs lofs = new Lofs(this.externalGraph, dataSets);
        lofs.setRule(Lofs.Rule.RSkew);

        return lofs.orient();
    }

    /**
     * Returns a comparison graph based on the true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return A comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a description of the algorithm being used, including the initial graph if available.
     *
     * @return A description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "RSkew" + (this.algorithm != null ? " with initial graph from "
                                                   + this.algorithm.getDescription() : "");
    }

    /**
     * Retrieves the data type required by the algorithm.
     *
     * @return The data type required by the algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves a list of parameters required for the current instance of the class.
     *
     * @return A list of parameter names.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Sets the external graph for this algorithm.
     *
     * @param algorithm The algorithm object representing the external graph.
     * @throws IllegalArgumentException if the algorithm object is null.
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This RSkew algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}
