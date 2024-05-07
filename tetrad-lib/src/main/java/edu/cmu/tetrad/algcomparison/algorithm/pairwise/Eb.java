package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
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
import java.util.LinkedList;
import java.util.List;

/**
 * EB.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@Experimental
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "EB",
//        command = "eb",
//        algoType = AlgType.orient_pairwise
//)
@Bootstrapping
public class Eb extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

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
     * <p>Constructor for Eb.</p>
     */
    public Eb() {
    }

    /**
     * <p>Constructor for Eb.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public Eb(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Runs a search algorithm to orient the edges in a graph using the given data and parameters.
     *
     * @param dataModel  the data model containing the dataset for the search algorithm
     * @param parameters the parameters for the search algorithm
     * @return the graph with oriented edges
     * @throws IllegalArgumentException if the data model is not a continuous dataset or if the algorithm is null
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        this.externalGraph = this.algorithm.search(dataSet, parameters);

        if (this.algorithm != null) {
            this.externalGraph = this.algorithm.search(dataSet, parameters);
        } else {
            throw new IllegalArgumentException("This EB algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

        Lofs lofs = new Lofs(this.externalGraph, dataSets);
        lofs.setRule(Lofs.Rule.EB);

        return lofs.orient();
    }

    /**
     * Returns a comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a description of the algorithm's orientation method.
     *
     * @return A description of the orientation method.
     */
    @Override
    public String getDescription() {
        return "EB, entropy based pairwise orientation" + (this.algorithm != null ? " with initial graph from "
                                                                                    + this.algorithm.getDescription() : "");
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the list of parameters that are used by the class. These parameters include the parameters defined in the
     * algorithm used by the class and the VERBOSE parameter.
     *
     * @return The list of parameters used by the class.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Sets the external graph to be used by the algorithm.
     *
     * @param algorithm The external graph algorithm to set.
     * @throws IllegalArgumentException If the algorithm is null.
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This EB algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }
}
