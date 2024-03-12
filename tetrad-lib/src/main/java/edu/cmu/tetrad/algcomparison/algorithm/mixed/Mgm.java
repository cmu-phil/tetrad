package edu.cmu.tetrad.algcomparison.algorithm.mixed;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * MGM.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "MGM",
        command = "mgm",
        algoType = AlgType.produce_undirected_graphs
)
@Bootstrapping
public class Mgm extends AbstractBootstrapAlgorithm implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for Mgm.</p>
     */
    public Mgm() {
    }

    /**
     * Runs the MGM search algorithm.
     *
     * @param dataModel  The data model containing the dataset.
     * @param parameters The parameters for running the search.
     * @return The graph obtained from the MGM search algorithm.
     * @throws IllegalArgumentException If the data model does not contain tabular data, if the dataset is not mixed
     *                                  (contains at least one discrete column and at least one continuous column), or
     *                                  if missing values are present.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("Expecting tabular data for MGM.");
        }

        if (!dataSet.isMixed()) {
            throw new IllegalArgumentException("Expecting mixed data for MGM--at least one discrete column and at least" +
                    " one continuous column.");
        }

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (dataSet.getVariables().get(j) instanceof ContinuousVariable) {
                    if (Double.isNaN(dataSet.getDouble(i, j))) {
                        throw new IllegalArgumentException("Please remove or impute missing values.");
                    }
                } else if (dataSet.getVariables().get(j) instanceof DiscreteVariable) {
                    if (dataSet.getDouble(i, j) == -99) {
                        throw new IllegalArgumentException("Please remove or impute missing values.");
                    }
                }
            }
        }

        // Notify the user that you need at least one continuous and one discrete variable to run MGM
        List<Node> variables = dataSet.getVariables();
        boolean hasContinuous = false;
        boolean hasDiscrete = false;

        for (Node node : variables) {
            if (node instanceof ContinuousVariable) {
                hasContinuous = true;
            }

            if (node instanceof DiscreteVariable) {
                hasDiscrete = true;
            }
        }

        if (!hasContinuous || !hasDiscrete) {
            throw new IllegalArgumentException("You need at least one continuous and one discrete variable to run MGM.");
        }

        DataSet _ds = SimpleDataLoader.getMixedDataSet(dataSet);

        double mgmParam1 = parameters.getDouble(Params.MGM_PARAM1);
        double mgmParam2 = parameters.getDouble(Params.MGM_PARAM2);
        double mgmParam3 = parameters.getDouble(Params.MGM_PARAM3);

        double[] lambda = {
                mgmParam1,
                mgmParam2,
                mgmParam3
        };

        edu.pitt.csb.mgm.Mgm m = new edu.pitt.csb.mgm.Mgm(_ds, lambda);

        return m.search();
    }

    /**
     * Returns the comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph, an undirected version of the true directed graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "Returns the output of the MGM (Mixed Graphical Model) algorithm (a Markov random field)";
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
     * Returns the list of parameters required by the getParameters method.
     *
     * @return List of parameters as strings.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.MGM_PARAM1);
        parameters.add(Params.MGM_PARAM2);
        parameters.add(Params.MGM_PARAM3);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
