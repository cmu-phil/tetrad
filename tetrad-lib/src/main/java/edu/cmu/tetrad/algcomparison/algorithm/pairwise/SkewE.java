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
 * SkewE.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@Experimental
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "SkewE",
//        command = "skew-e",
//        algoType = AlgType.orient_pairwise
//)
@Bootstrapping
public class SkewE extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

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
     * <p>Constructor for SkewE.</p>
     */
    public SkewE() {
    }

    /**
     * <p>Constructor for SkewE.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public SkewE(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Executes the SkewE search algorithm.
     *
     * @param dataModel   The data model containing the dataset.
     * @param parameters  The parameters to be used for the search.
     * @return The oriented graph.
     * @throws IllegalArgumentException if the data model is not a continuous dataset or the graph is null.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        Graph graph = this.algorithm.search(dataSet, parameters);

        if (graph != null) {
            this.externalGraph = graph;
        } else {
            throw new IllegalArgumentException("This SkewE algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

        Lofs lofs = new Lofs(this.externalGraph, dataSets);
        lofs.setRule(Lofs.Rule.SkewE);

        return lofs.orient();
    }

    /**
     * Returns a comparison graph based on the provided true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return A comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a description of the algorithm used. If an initial graph
     * is provided to the algorithm, it appends the initial graph description
     * to the description. The format of the description is "SkewE with
     * initial graph from [initial graph description]".
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "SkewE" + (this.algorithm != null ? " with initial graph from "
                + this.algorithm.getDescription() : "");
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the list of parameters for the current instance of the class.
     *
     * @return The list of parameters, including algorithm parameters and additional parameters.
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
     * @param algorithm The algorithm that can take an external graph as input.
     * @throws IllegalArgumentException if the algorithm is null.
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This SkewE algorithm needs both data and a graph source as inputs; it \n"
                    + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}
