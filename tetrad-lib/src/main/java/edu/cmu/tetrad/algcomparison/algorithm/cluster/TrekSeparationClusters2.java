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
        name = "TSC2",
        command = "tsc2",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class TrekSeparationClusters2 extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
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
    public TrekSeparationClusters2() {
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

        String clusterSizes = parameters.getString(Params.CLUSTER_SIZES);
        String[] tokens = clusterSizes.split(",");
        List<int[]> _specs = new ArrayList<>();

        for (String token : tokens) {
            if (token.trim().isEmpty()) {
                continue;
            }

            String[] token2 = token.trim().split(":");
            int[] spec = new int[2];

            if (token2.length == 1) {
                if (token2[0].trim().isEmpty()) {
                    continue;
                }
                try {
                    int i = Integer.parseInt(token2[0].trim());
                    spec = new int[]{i, i - 1};
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Could not parse '" + token2[0] + "'", e);
                }
            } else if (token2.length == 2) {
                if (token2[0].trim().isEmpty() || token2[1].trim().isEmpty()) {
                    continue;
                }
                try {
                    spec[0] = Integer.parseInt(token2[0].trim());
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Could not parse '" + token2[0] + "'", e);
                }
                try {
                    spec[1] = Integer.parseInt(token2[1].trim());
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Could not parse '" + token2[1] + "'", e);
                }
            } else {
                throw new RuntimeException("Could not parse '" + token + "'");
            }

            _specs.add(spec);
        }

        if (_specs.isEmpty()) {
            _specs.add(new int[]{2, 1});
        }

        int[][] specs = new int[_specs.size()][];
        for (int i = 0; i < _specs.size(); i++) {
            specs[i] = _specs.get(i);
        }

        double alpha = parameters.getDouble(Params.FOFC_ALPHA);
        boolean includeAllNodes = parameters.getBoolean(Params.INCLUDE_ALL_NODES);
        int ess = parameters.getInt(Params.EXPECTED_SAMPLE_SIZE);
        double penalty = parameters.getDouble(Params.PENALTY_DISCOUNT);
        boolean verbose = parameters.getBoolean(Params.VERBOSE);
        boolean includeStructureModel = parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL);

        CovarianceMatrix covarianceMatrix = dataModel instanceof DataSet
                ? new CovarianceMatrix((DataSet) dataModel) : (CovarianceMatrix) dataModel;
        List<Node> variables = dataModel.getVariables();

        edu.cmu.tetrad.search.TrekSeparationClusters2 search
                = new edu.cmu.tetrad.search.TrekSeparationClusters2(variables, covarianceMatrix,
                ess == -1 ? covarianceMatrix.getSampleSize() : ess);
        search.setIncludeStructureModel(includeStructureModel);
        search.setIncludeAllNodes(includeAllNodes);
        search.setAlpha(alpha);
        search.setPenalty(penalty);
        search.setVerbose(verbose);

        return search.search(specs);
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
        return "TSC2 (Trek Separation Clusters 2)";
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
        parameters.add(Params.CLUSTER_SIZES);
        parameters.add(Params.FOFC_ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.INCLUDE_STRUCTURE_MODEL);
        parameters.add(Params.INCLUDE_ALL_NODES);
        parameters.add(Params.MIMBUILD_TYPE);
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

