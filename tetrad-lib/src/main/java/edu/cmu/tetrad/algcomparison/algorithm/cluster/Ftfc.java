package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.ntad_test.BollenTing;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FTFC.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FTFC",
        command = "ftfc",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class Ftfc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Ftfc.</p>
     */
    public Ftfc() {
    }

    /**
     * Runs the search algorithm to find a causal graph.
     *
     * @param dataSet    The data set or covariance matrix to search.
     * @param parameters The search parameters.
     * @return The causal graph discovered by the search algorithm.
     * @throws IllegalArgumentException if the dataSet is not a dataset or a covariance matrix.
     */
    @Override
    public Graph runSearch(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        assert dataSet instanceof DataSet;
        edu.cmu.tetrad.search.Ftfc search
                = new edu.cmu.tetrad.search.Ftfc((DataSet) dataSet,
                new BollenTing(((DataSet) dataSet).getDoubleData().getDataCopy(), false),
                alpha);
        search.setIncludeAllNodes(parameters.getBoolean(Params.INCLUDE_ALL_NODES));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return search.search();
    }

    /**
     * Retrieves the comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph, which is a completed partially directed acyclic graph (CPDAG) of the input graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "FTFC (Find Two Factor Clusters)";
    }

    /**
     * Returns the type of the data set that the search algorithm requires. The data set can be continuous, discrete, or
     * mixed.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters supported by this algorithm. The parameters include ALPHA, USE_WISHART, USE_GAP,
     * PRECOMPUTE_COVARIANCES, and VERBOSE.
     *
     * @return The list of parameters supported by this algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.INCLUDE_ALL_NODES);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Returns the knowledge associated with this algorithm.
     *
     * @return The knowledge associated with this algorithm.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this algorithm.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

}
