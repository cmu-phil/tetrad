package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Find One Factor Clusters.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FOFC",
        command = "fofc",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class Fofc implements Algorithm, HasKnowledge, ClusterAlgorithm {

    static final long serialVersionUID = 23L;
    private Knowledge knowledge = new Knowledge();

    public Fofc() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            ICovarianceMatrix cov = SimpleDataLoader.getCovarianceMatrix(dataSet);
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

                List<List<Node>> partition = ClusterUtils.clustersToPartition(clusters, dataSet.getVariables());

                List<String> latentNames = new ArrayList<>();

                for (int i = 0; i < clusters.getNumClusters(); i++) {
                    latentNames.add(clusters.getClusterName(i));
                }

                Graph structureGraph = mimbuild.search(partition, latentNames, cov);
                LayoutUtil.circleLayout(structureGraph, 200, 200, 150);
                LayoutUtil.fruchtermanReingoldLayout(structureGraph);

                ICovarianceMatrix latentsCov = mimbuild.getLatentsCov();

                TetradLogger.getInstance().log("details", "Latent covs = \n" + latentsCov);

                Graph fullGraph = mimbuild.getFullGraph();
                LayoutUtil.circleLayout(fullGraph, 200, 200, 150);
                LayoutUtil.fruchtermanReingoldLayout(fullGraph);

                return fullGraph;
            }
        } else {
            Fofc algorithm = new Fofc();

            DataSet data = (DataSet) dataSet;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);


            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtilsSearch.cpdagForDag(graph);
    }

    @Override
    public String getDescription() {
        return "FOFC (Find One Factor Clusters)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

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
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }
}
