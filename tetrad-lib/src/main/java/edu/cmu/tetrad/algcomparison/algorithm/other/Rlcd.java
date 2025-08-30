package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * GLASSO.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "RLCD",
//        command = "rlcd",
//        algoType = AlgType.search_for_structure_over_latents,
//        dataType = DataType.Continuous
//)
@Bootstrapping
@Experimental
public class Rlcd extends AbstractBootstrapAlgorithm implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public Rlcd() {
    }

    /**
     * Runs a search algorithm to create a graph representation of the data.
     *
     * @param dataModel  The data model containing the dataset.
     * @param parameters The parameters for the search algorithm.
     * @return The resulting graph representation of the data.
     * @throws IllegalArgumentException if the data model is not a continuous dataset or contains missing values.
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        edu.cmu.tetrad.search.Rlcd rlcd = new edu.cmu.tetrad.search.Rlcd(dataSet);
        return rlcd.search(parameters.getDouble(Params.ALPHA));
    }

    /**
     * Retrieves a comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
     *
     * @return The description of the algorithm.
     */
    public String getDescription() {
        return "GLASSO (Graphical LASSO)";
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves a list of parameters used by the algorithm.
     *
     * @return A list of parameter names.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
//        params.add(Params.VERBOSE);
        return params;
    }
}
