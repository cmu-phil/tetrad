package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
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
//        name = "GLASSO",
//        command = "glasso",
//        algoType = AlgType.produce_undirected_graphs,
//        dataType = DataType.Continuous
//)
@Bootstrapping
@Experimental
public class Glasso extends AbstractBootstrapAlgorithm implements Algorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public Glasso() {
    }

    /**
     * Runs a search algorithm to create a graph representation of the data.
     *
     * @param dataModel   The data model containing the dataset.
     * @param parameters  The parameters for the search algorithm.
     * @return The resulting graph representation of the data.
     * @throws IllegalArgumentException if the data model is not a continuous dataset or contains missing values.
     */
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (Double.isNaN(dataSet.getDouble(i, j))) {
                    throw new IllegalArgumentException("Please remove or impute missing values.");
                }
            }
        }

        Matrix cov = new Matrix(SimpleDataLoader.getContinuousDataSet(dataSet)
                .getCovarianceMatrix().toArray());

        edu.cmu.tetrad.search.work_in_progress.Glasso glasso = new edu.cmu.tetrad.search.work_in_progress.Glasso(cov);
        glasso.setMaxit(parameters.getInt(Params.MAXIT));
        glasso.setIa(parameters.getBoolean(Params.IA));
        glasso.setIs(parameters.getBoolean(Params.IS));
        glasso.setItr(parameters.getBoolean(Params.ITR));
        glasso.setIpen(parameters.getBoolean(Params.IPEN));
        glasso.setThr(parameters.getDouble(Params.THR));
        glasso.setRhoAllEqual(1.0);

        edu.cmu.tetrad.search.work_in_progress.Glasso.Result result = glasso.search();
        Matrix wwi = new Matrix(result.getWwi().toArray());

        List<Node> variables = dataSet.getVariables();
        Graph resultGraph = new EdgeListGraph(variables);

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                if (wwi.get(i, j) != 0.0 && wwi.get(i, j) != 0.0) {
                    resultGraph.addUndirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        return resultGraph;
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
     *
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
        params.add(Params.MAXIT);
        params.add(Params.IA);
        params.add(Params.IS);
        params.add(Params.ITR);
        params.add(Params.IPEN);
        params.add(Params.THR);

        params.add(Params.VERBOSE);
        return params;
    }
}
