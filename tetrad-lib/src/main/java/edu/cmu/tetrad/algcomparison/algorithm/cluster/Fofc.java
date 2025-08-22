package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.MimbuildBollen;
import edu.cmu.tetrad.search.MimbuildPca;
import edu.cmu.tetrad.search.blocks.BlockDiscoverer;
import edu.cmu.tetrad.search.blocks.BlockDiscoverers;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.ntad_test.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

///**
// * Find One Factor Clusters.
// *
// * @author josephramsey
// * @version $Id: $Id
// */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "FOFC",
//        command = "fofc",
//        algoType = AlgType.search_for_structure_over_latents
//)
//@Bootstrapping
public class Fofc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();
    private BlockSpec blockSpec;

    /**
     * <p>Constructor for Fofc.</p>
     */
    public Fofc() {
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

        boolean includeAllNodes = parameters.getBoolean(Params.INCLUDE_ALL_NODES);

        // === NEW: use the unified FOFC BlockDiscoverer ===
        BlockDiscoverer discoverer = BlockDiscoverers.fofc(dataSet, test, alpha);
        BlockSpec spec = discoverer.discover();
        List<List<Integer>> blocks = new ArrayList<>(spec.blocks());
        this.blockSpec = spec;

        // Build the measurement graph from blocks + latents
        Graph graph = new EdgeListGraph(dataModel.getVariables());
        List<Node> observed = dataSet.getVariables();
        for (int i = 0; i < blocks.size(); ++i) {
            Node latent = spec.blockVariables().get(i);
            graph.addNode(latent);
            for (Integer j : blocks.get(i)) {
                graph.addDirectedEdge(latent, observed.get(j));
            }
        }

        // If the user only wants the measurement graph, return here.
        if (!parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL)) {
            return graph;
        }

        // Otherwise, run MIMBUILD on the discovered blocks/latents.
        Fofc.MimbuildType mimbuildType = switch (parameters.getInt(Params.MIMBUILD_TYPE)) {
            case 1 -> Fofc.MimbuildType.PCA;
            case 2 -> Fofc.MimbuildType.BOLLEN;
            default -> Fofc.MimbuildType.PCA;
        };

        Graph structureGraph;
        Graph fullGraph;

        if (mimbuildType == Fofc.MimbuildType.PCA) {
            MimbuildPca mimbuild = new MimbuildPca(dataSet, blocks, blockSpec.blockVariables());
            mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

            try {
                structureGraph = mimbuild.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (org.ejml.data.SingularMatrixException e) {
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
            MimbuildBollen mimbuildBollen = new MimbuildBollen(dataSet, blocks, blockSpec.blockVariables());
            mimbuildBollen.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

            try {
                structureGraph = mimbuildBollen.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (org.ejml.data.SingularMatrixException e) {
                throw new RuntimeException("Singularity encountered; perhaps that was not a pure model", e);
            }

            LayoutUtil.defaultLayout(structureGraph);
            LayoutUtil.fruchtermanReingoldLayout(structureGraph);

            ICovarianceMatrix latentsCov = mimbuildBollen.getLatentsCov();
            TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);

            fullGraph = mimbuildBollen.getFullGraph(includeAllNodes ? dataSet.getVariables() : new ArrayList<>());
            LayoutUtil.defaultLayout(fullGraph);
            LayoutUtil.fruchtermanReingoldLayout(fullGraph);
        }

        return fullGraph;
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
        return "FOFC (Find One Factor Clusters)";
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


    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
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
