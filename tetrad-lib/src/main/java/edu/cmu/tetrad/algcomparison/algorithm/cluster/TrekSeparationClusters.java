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
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Mimbuild;
import edu.cmu.tetrad.search.MimbuildPca;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.SingularMatrixException;

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

        double alpha = parameters.getDouble(Params.FOFC_ALPHA);
        boolean includeAllNodes = parameters.getBoolean(Params.INCLUDE_ALL_NODES);
        int ess = parameters.getInt(Params.EXPECTED_SAMPLE_SIZE);
        boolean includeStructure = parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL);


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


        edu.cmu.tetrad.search.TrekSeparationClusters search;
        double penalty = parameters.getDouble(Params.PENALTY_DISCOUNT);

        if (dataModel instanceof CovarianceMatrix) {
            if (ess == -1) {
                search = new edu.cmu.tetrad.search.TrekSeparationClusters((CovarianceMatrix) dataModel, alpha, specs, penalty);
            } else {
                search = new edu.cmu.tetrad.search.TrekSeparationClusters((CovarianceMatrix) dataModel, alpha, penalty, specs,
                        ess);
            }
        } else {
            if (ess == -1) {
                search = new edu.cmu.tetrad.search.TrekSeparationClusters((DataSet) dataModel, specs, alpha, penalty);
            } else {
                search = new edu.cmu.tetrad.search.TrekSeparationClusters((DataSet) dataModel, alpha, penalty, specs, ess);
            }
        }

        search.setIncludeAllNodes(includeAllNodes);
        search.setIncludeStructureModel(includeStructure);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        Graph graph = search.search();

        if (true) {//!parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL)) {
            return graph;
        } else {

            Clusters clusters = ClusterUtils.mimClusters(graph);
            Graph structureGraph;
            Graph fullGraph;


            Fofc.MimbuildType mimbuildType = switch (parameters.getInt(Params.MIMBUILD_TYPE)) {
                case 1 -> Fofc.MimbuildType.PCA;
                case 2 -> Fofc.MimbuildType.BOLLEN;
                default -> Fofc.MimbuildType.PCA;
            };

            if (mimbuildType == Fofc.MimbuildType.PCA) {
                MimbuildPca mimbuild = new MimbuildPca();
                mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

                List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, dataModel.getVariables());

                List<String> latentNames = new ArrayList<>();

                for (int i = 0; i < clusters.getNumClusters(); i++) {
                    latentNames.add(clusters.getClusterName(i));
                }

                if (!(dataModel instanceof DataSet)) {
                    throw new IllegalArgumentException("Mimbuild requires tabular data.");
                }

                try {
                    structureGraph = mimbuild.search(partition, latentNames, (DataSet) dataModel);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (SingularMatrixException e) {
                    throw new RuntimeException("Singularity encountered; perhaps that was not a pure model", e);
                }

                LayoutUtil.defaultLayout(structureGraph);
                LayoutUtil.fruchtermanReingoldLayout(structureGraph);

                ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

                TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);

                fullGraph = mimbuild.getFullGraph(includeAllNodes ? ((DataSet) dataModel).getVariables() : new ArrayList<>());
                LayoutUtil.defaultLayout(fullGraph);
                LayoutUtil.fruchtermanReingoldLayout(fullGraph);
            } else {
                Mimbuild mimbuild = new Mimbuild();
                mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

                List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, dataModel.getVariables());

                List<String> latentNames = new ArrayList<>();

                for (int i = 0; i < clusters.getNumClusters(); i++) {
                    latentNames.add(clusters.getClusterName(i));
                }

                if (!(dataModel instanceof DataSet)) {
                    throw new IllegalArgumentException("Mimbuild requires tabular data.");
                }

                try {
                    structureGraph = mimbuild.search(partition, latentNames, new CovarianceMatrix((DataSet) dataModel));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (SingularMatrixException e) {
                    throw new RuntimeException("Singularity encountered; perhaps that was not a pure model", e);
                }

                LayoutUtil.defaultLayout(structureGraph);
                LayoutUtil.fruchtermanReingoldLayout(structureGraph);

                ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

                TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);

                fullGraph = mimbuild.getFullGraph(includeAllNodes ? ((DataSet) dataModel).getVariables() : new ArrayList<>());
                LayoutUtil.defaultLayout(fullGraph);
                LayoutUtil.fruchtermanReingoldLayout(fullGraph);
            }

            return fullGraph;
        }
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
        parameters.add(Params.FOFC_ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.INCLUDE_STRUCTURE_MODEL);
        parameters.add(Params.INCLUDE_ALL_NODES);
        parameters.add(Params.MIMBUILD_TYPE);
        parameters.add(Params.EXPECTED_SAMPLE_SIZE);
        parameters.add(Params.CLUSTER_SIZES);
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

    /**
     * The MimbuildType enum represents the different types of model construction strategies available for specific use
     * in algorithms within the Fofc class. It specifies selectable methods for building models based on input data.
     * <p>
     * The enum includes the following types: - PCA: Principal Component Analysis-based model building. - BOLLEN: Model
     * building method inspired by Bollen's statistical techniques.
     */
    public enum MimbuildType {

        /**
         * Represents the Principal Component Analysis (PCA)-based model building strategy within the MimbuildType enum.
         * PCA is a dimensionality reduction technique commonly used in statistical and machine learning algorithms to
         * transform input data into a set of uncorrelated features, reducing complexity while preserving key
         * information.
         */
        PCA,

        /**
         * Represents the model building method inspired by Bollen's statistical techniques within the MimbuildType
         * enum. This method is often employed to develop models that utilize sophisticated statistical principles,
         * aiming to analyze and interpret structural relationships between variables in complex datasets.
         */
        BOLLEN
    }
}
