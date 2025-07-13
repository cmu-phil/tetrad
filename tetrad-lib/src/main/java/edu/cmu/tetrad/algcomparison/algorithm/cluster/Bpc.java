package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Mimbuild;
import edu.cmu.tetrad.search.MimbuildPca;
import edu.cmu.tetrad.search.utils.BpcTestType;
import edu.cmu.tetrad.search.utils.ClusterSignificance;
import edu.cmu.tetrad.search.utils.ClusterUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.SingularMatrixException;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Build Pure Clusters.
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
public class Bpc implements Algorithm, ClusterAlgorithm,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new BPC algorithm.
     */
    public Bpc() {
    }

    /**
     * Runs the search algorithm to build a graph using the given data model and parameters.
     *
     * @param dataModel  The data model to be used for the search.
     * @param parameters The parameters for the search algorithm.
     * @return The resulting graph.
     * @throws IllegalArgumentException If the check type is unexpected.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        ICovarianceMatrix cov = SimpleDataLoader.getCovarianceMatrix(dataModel, precomputeCovariances);
        double alpha = parameters.getDouble(Params.ALPHA);

        int tetradTest = parameters.getInt(Params.TETRAD_TEST_BPC);
        BpcTestType testType;

        if (tetradTest == 1) {
            testType = BpcTestType.TETRAD_WISHART;
        } else if (tetradTest == 2) {
            testType = BpcTestType.TETRAD_DELTA;
        } else {
            throw new IllegalArgumentException("Unexpected test type: " + tetradTest);
        }

        edu.cmu.tetrad.search.Bpc bpc = new edu.cmu.tetrad.search.Bpc(new CorrelationMatrix(cov), alpha, testType);
        bpc.setVerbose(parameters.getBoolean(Params.VERBOSE));

        if (parameters.getInt(Params.CHECK_TYPE) == 1) {
            bpc.setCheckType(ClusterSignificance.CheckType.Significance);
        } else if (parameters.getInt(Params.CHECK_TYPE) == 2) {
            bpc.setCheckType(ClusterSignificance.CheckType.Clique);
        } else if (parameters.getInt(Params.CHECK_TYPE) == 3) {
            bpc.setCheckType(ClusterSignificance.CheckType.None);
        } else {
            throw new IllegalArgumentException("Unexpected check type");
        }

        Graph graph = null;
        try {
            graph = bpc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!parameters.getBoolean(Params.INCLUDE_STRUCTURE_MODEL)) {
            return graph;
        } else {

            Clusters clusters = ClusterUtils.mimClusters(graph);

            MimbuildPca mimbuild = new MimbuildPca();
            mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

            List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, dataModel.getVariables());

            List<String> latentNames = new ArrayList<>();

            for (int i = 0; i < clusters.getNumClusters(); i++) {
                latentNames.add(clusters.getClusterName(i));
            }

            Graph structureGraph = null;
            try {
                structureGraph = mimbuild.search(partition, latentNames, (DataSet) dataModel);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (SingularMatrixException e) {
                throw new RuntimeException("Singularity encountered; perhaps that was not a pure model", e);
            }

//            Mimbuild mimbuild = new Mimbuild();
//            mimbuild.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
//            List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, dataModel.getVariables());
//            List<String> latentNames = new ArrayList<>();
//
//            for (int i = 0; i < clusters.getNumClusters(); i++) {
//                latentNames.add(clusters.getClusterName(i));
//            }
//
//            Graph structureGraph = mimbuild.search(partition, latentNames, cov);
            LayoutUtil.defaultLayout(structureGraph);
            LayoutUtil.fruchtermanReingoldLayout(structureGraph);

            ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

            if (parameters.getBoolean(Params.VERBOSE)) {
                TetradLogger.getInstance().log("Latent covs = \n" + latentsCov);
            }

            Graph fullGraph = mimbuild.getFullGraph(dataModel.getVariables());
            LayoutUtil.defaultLayout(fullGraph);
            LayoutUtil.fruchtermanReingoldLayout(fullGraph);

            return fullGraph;
        }
    }

    /**
     * Returns the comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
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
        return "BPC (Build Pure Clusters)";
    }

    /**
     * Retrieves the data type of the algorithm's output.
     *
     * @return The data type of the algorithm's output.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return A list of strings representing the parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.TETRAD_TEST_BPC);
        parameters.add(Params.INCLUDE_STRUCTURE_MODEL);
        parameters.add(Params.CHECK_TYPE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.VERBOSE);

        return parameters;
    }
}
