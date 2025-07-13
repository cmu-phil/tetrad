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
import edu.cmu.tetrad.search.MimbuildPca;
import edu.cmu.tetrad.search.SilvaClusterFinderNtad;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
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
        name = "SCF",
        command = "scf",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class SilvaClusterFinder extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ClusterAlgorithm,
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
    public SilvaClusterFinder() {
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

        SilvaClusterFinderNtad search = new SilvaClusterFinderNtad(new Cca(dataSet.getDoubleData().getDataCopy(),
                false), new IndTestFisherZ(dataSet, alpha), dataSet.getVariableNames(), alpha);
        search.findClusters();

        List<List<String>> clusters = search.getClusters();

        System.out.println("clusters = " + clusters);

        Clusters clusters1 = new Clusters();

        List<String> varNames = dataSet.getVariableNames();

        for (int i = 0; i < clusters.size(); i++) {
            List<String> cluster = clusters.get(i);

            for (int j = 0; j < cluster.size(); j++) {
                clusters1.addToCluster(i, cluster.get(j));
            }
        }

        System.out.println("clusters1 = " + clusters1);

        MimbuildPca mimbuild = new MimbuildPca();
        mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters1, dataModel.getVariables());

        System.out.println("partition = " + partition);

        List<String> latentNames = new ArrayList<>();

        for (int i = 0; i < clusters1.getNumClusters(); i++) {
            latentNames.add(clusters1.getClusterName(i));
        }

        Graph structureGraph = null;
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

        Graph fullGraph = mimbuild.getFullGraph(dataSet.getVariables());
        LayoutUtil.defaultLayout(fullGraph);
        LayoutUtil.fruchtermanReingoldLayout(fullGraph);

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
        return "SCF (Silva Cluster Finder)";
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
