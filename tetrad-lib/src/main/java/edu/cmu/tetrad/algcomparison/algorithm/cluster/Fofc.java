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
import edu.cmu.tetrad.search.utils.BpcTestType;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

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
        name = "FOFC",
        command = "fofc",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class Fofc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
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
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        ICovarianceMatrix cov = SimpleDataLoader.getCovarianceMatrix(dataModel, precomputeCovariances);
        double alpha = parameters.getDouble(Params.ALPHA);

        boolean wishart = parameters.getBoolean(Params.USE_WISHART, true);
        BpcTestType testType;

        if (wishart) {
            testType = BpcTestType.TETRAD_WISHART;
        } else {
            testType = BpcTestType.TETRAD_DELTA;
        }

        boolean gap = parameters.getBoolean(Params.USE_GAP, true);
        edu.cmu.tetrad.search.Fofc.Algorithm algorithm;

        if (gap) {
            algorithm = edu.cmu.tetrad.search.Fofc.Algorithm.GAP;
        } else {
            algorithm = edu.cmu.tetrad.search.Fofc.Algorithm.SAG;
        }

        edu.cmu.tetrad.search.Fofc search
                = new edu.cmu.tetrad.search.Fofc(cov, testType, algorithm, alpha);
        search.setSignificanceChecked(parameters.getBoolean(Params.SIGNIFICANCE_CHECKED));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        if (parameters.getInt(Params.CHECK_TYPE) == 1) {
            search.setCheckType(ClusterSignificance.CheckType.Significance);
        } else if (parameters.getInt(Params.CHECK_TYPE) == 2) {
            search.setCheckType(ClusterSignificance.CheckType.Clique);
        } else if (parameters.getInt(Params.CHECK_TYPE) == 3) {
            search.setCheckType(ClusterSignificance.CheckType.None);
        } else {
            throw new IllegalArgumentException("Unexpected check type");
        }

        Graph graph = search.search();

        if (!parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL)) {
            return graph;
        } else {

            Clusters clusters = ClusterUtils.mimClusters(graph);

            Mimbuild mimbuild = new Mimbuild();
            mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            mimbuild.setKnowledge((Knowledge) parameters.get("knowledge", new Knowledge()));

            if (parameters.getBoolean("includeThreeClusters", true)) {
                mimbuild.setMinClusterSize(3);
            } else {
                mimbuild.setMinClusterSize(4);
            }

            List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, dataModel.getVariables());

            List<String> latentNames = new ArrayList<>();

            for (int i = 0; i < clusters.getNumClusters(); i++) {
                latentNames.add(clusters.getClusterName(i));
            }

            Graph structureGraph = mimbuild.search(partition, latentNames, cov);
            LayoutUtil.defaultLayout(structureGraph);
            LayoutUtil.fruchtermanReingoldLayout(structureGraph);

            ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

            TetradLogger.getInstance().forceLogMessage("Latent covs = \n" + latentsCov);

            Graph fullGraph = mimbuild.getFullGraph();
            LayoutUtil.defaultLayout(fullGraph);
            LayoutUtil.fruchtermanReingoldLayout(fullGraph);

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
        parameters.add(Params.ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.USE_WISHART);
        parameters.add(Params.SIGNIFICANCE_CHECKED);
        parameters.add(Params.USE_GAP);
        parameters.add(Params.INCLUDE_STRUCTURE_MODEL);
        parameters.add(Params.CHECK_TYPE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
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
     * @param knowledge a knowledge object
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}
