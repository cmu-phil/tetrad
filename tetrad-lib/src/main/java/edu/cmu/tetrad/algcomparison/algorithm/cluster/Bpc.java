package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Mimbuild;
import edu.cmu.tetrad.search.MimbuildPca;
import edu.cmu.tetrad.search.ntad_test.*;
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
        name = "BPC",
        command = "bpc",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class Bpc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
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
    public Bpc() {
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
            System.out.println("penaltyDiscount = " + parameters.getDouble(Params.PENALTY_DISCOUNT));
            System.out.println("includeStructureModel = " + parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL));
            System.out.println("verbose = " + parameters.getBoolean(Params.VERBOSE));
        }

        DataSet dataSet = (DataSet) dataModel;
        double alpha = parameters.getDouble(Params.FOFC_ALPHA);

        int testType = parameters.getInt(Params.TETRAD_TEST_FOFC);
        NtadTest test = switch (testType) {
            case 1 -> new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
            case 2 -> new BollenTing(dataSet.getDoubleData().getSimpleMatrix(), false);
            case 3 -> new Wishart(dataSet.getDoubleData().getSimpleMatrix(), false);
            case 4 -> new Ark(dataSet.getDoubleData().getSimpleMatrix(), 1.0);
            default -> new Cca(dataSet.getDoubleData().getSimpleMatrix(), false);
        };

        edu.cmu.tetrad.search.Bpc search = new edu.cmu.tetrad.search.Bpc(
                test,
                dataSet,
                alpha);
        search.findClusters();

        boolean includeAllNodes = parameters.getBoolean(Params.INCLUDE_ALL_NODES);

        List<List<String>> _clusters = search.getClusters();

        int latentCount = 0;
        Graph graph = new EdgeListGraph(includeAllNodes ? dataSet.getVariables() : new ArrayList<>());

        for (List<String> cluster : _clusters) {
            Node latent = new ContinuousVariable("L" + (++latentCount));
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);

            for (String name : cluster) {
                Node measure = new ContinuousVariable(name);
                graph.addNode(measure);
                graph.addDirectedEdge(latent, measure);
            }
        }

        if (!parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL)) {
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

                try {
                    structureGraph = mimbuild.search(partition, latentNames, dataSet);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (SingularMatrixException e) {
                    throw new RuntimeException("Singularity encountered; perhaps that was not a pure model", e);
                }

                LayoutUtil.defaultLayout(structureGraph);
                LayoutUtil.fruchtermanReingoldLayout(structureGraph);

                ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

                TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);

                fullGraph = mimbuild.getFullGraph(includeAllNodes ? dataSet.getVariables() : new ArrayList<>());
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

                try {
                    structureGraph = mimbuild.search(partition, latentNames, new CovarianceMatrix(dataSet));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (SingularMatrixException e) {
                    throw new RuntimeException("Singularity encountered; perhaps that was not a pure model", e);
                }

                LayoutUtil.defaultLayout(structureGraph);
                LayoutUtil.fruchtermanReingoldLayout(structureGraph);

                ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

                TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);

                fullGraph = mimbuild.getFullGraph(includeAllNodes ? dataSet.getVariables() : new ArrayList<>());
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
        return "BPC (Build Pure Clusters)";
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
        parameters.add(Params.TETRAD_TEST_FOFC);
        parameters.add(Params.INCLUDE_STRUCTURE_MODEL);
        parameters.add(Params.INCLUDE_ALL_NODES);
        parameters.add(Params.MIMBUILD_TYPE);
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
