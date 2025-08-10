package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * Find One Factor Clusters.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "TSC",
        command = "tsc",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class TrekSeparationClusters extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Fofc.</p>
     */
    public TrekSeparationClusters() {
    }

    /**
     * Runs the search algorithm and returns the resulting graph.
     *
     * @param dataModel  The data model containing the variables.
     * @param parameters The parameters for the search algorithm.
     * @return The resulting graph.
     * @throws IllegalArgumentException if the check type parameter is unexpected.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (parameters.getBoolean(Params.VERBOSE)) {
            System.out.println("alpha = " + parameters.getDouble(Params.FOFC_ALPHA));
            System.out.println("verbose = " + parameters.getBoolean(Params.VERBOSE));
        }

        int size = parameters.getInt(Params.TSC_CLUSTER_SIZE);
        int rank = parameters.getInt(Params.TSC_CLUSTER_RANK);
        int mode =  parameters.getInt(Params.TSC_MODE);

        int[][] specs = new int[][]{{size, rank}};

        double alpha = parameters.getDouble(Params.FOFC_ALPHA);
        boolean includeAllNodes = parameters.getBoolean(Params.INCLUDE_ALL_NODES);
        int ess = parameters.getInt(Params.EXPECTED_SAMPLE_SIZE);
        double penalty = parameters.getDouble(Params.PENALTY_DISCOUNT);
        boolean verbose = parameters.getBoolean(Params.VERBOSE);
        boolean includeStructureModel = parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL);

        CovarianceMatrix covarianceMatrix = dataModel instanceof DataSet
                ? new CorrelationMatrix((DataSet) dataModel) : new CorrelationMatrix((CovarianceMatrix) dataModel);
        List<Node> variables = dataModel.getVariables();

        edu.cmu.tetrad.search.TrekSeparationClusters search
                = new edu.cmu.tetrad.search.TrekSeparationClusters(variables, covarianceMatrix,
                ess == -1 ? covarianceMatrix.getSampleSize() : ess);
        search.setIncludeStructureModel(includeStructureModel);
        search.setIncludeAllNodes(includeAllNodes);
        search.setRegLambda(parameters.getDouble(Params.REGULARIZATION_LAMBDA));
        search.setCondThreshold(parameters.getDouble(Params.CONDITIONING_THRESHOLD));
        search.setAlpha(alpha);
        search.setPenalty(penalty);
        search.setVerbose(verbose);

        edu.cmu.tetrad.search.TrekSeparationClusters.Mode _mode;

        if (mode == 1) {
            _mode = edu.cmu.tetrad.search.TrekSeparationClusters.Mode.METALOOP;
        } else if (mode == 2) {
            _mode = edu.cmu.tetrad.search.TrekSeparationClusters.Mode.SIZE_RANK;
        } else {
            throw new IllegalArgumentException("Invalid mode, should be 1 or 2: " + mode);
        }

        return search.search(specs, _mode);
    }

    /**
     * This method returns a comparison graph that is obtained from the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph obtained by applying the CPDAG algorithm to the true directed graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToCpdag(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "TSC (Trek Separation Clusters)";
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
     * Returns a list of parameters for the search algorithm.
     *
     * @return The list of parameters for the search algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
//        parameters.add(Params.CLUSTER_SIZES);
        parameters.add(Params.TSC_MODE);
        parameters.add(Params.TSC_CLUSTER_SIZE);
        parameters.add(Params.TSC_CLUSTER_RANK);
        parameters.add(Params.FOFC_ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.REGULARIZATION_LAMBDA);
        parameters.add(Params.CONDITIONING_THRESHOLD);
        parameters.add(Params.INCLUDE_STRUCTURE_MODEL);
        parameters.add(Params.INCLUDE_ALL_NODES);
//        parameters.add(Params.MIMBUILD_TYPE);
        parameters.add(Params.EXPECTED_SAMPLE_SIZE);
//        parameters.add(Params.CLUSTER_SIZES);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Returns the knowledge associated with this object.
     *
     * @return the knowledge associated with this object
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this object.
     *
     * @param knowledge Background knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}

