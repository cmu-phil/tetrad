package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.FindTwoFactorClusters;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import edu.pitt.dbmi.data.Dataset;
import java.util.ArrayList;
import java.util.List;

/**
 * FTFC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Ftfc",
        command = "ftfc",
        algoType = AlgType.search_for_structure_over_latents,
        description = "FTFC (Find Two Factor Clusters) is similar to FOFC, but instead of each cluster having one latent that is the parent of all of the measure in the cluster, it instead has two such latents. So each measure has two latent parents; these are two “factors.” Similarly to FOFC, constraints are checked for, but in this case, the constraints must be sextad constraints, and more of them must be satisfied for each pure cluster (see Kummerfelt et al., 2014) Thus, the number of measures in each cluster, once impure edges have been taken into account, must be at least six, preferably more.\n" +
                "\n" +
                "Input Assumptions: Continuous data over the measures with at least six variable variables in each cluster once variables involve in impure edges have been removed.\n" +
                "\n" +
                "Output Format: A clustering of measures. It may be assumed that each cluster has at least two factors and that the clusters are pure.\n" +
                "\n" +
                "Parameters:\n" +
                "- Cutoff for p-values (alpha). Conditional independence tests with p-values greater\n" +
                "than this will be judged to be independent (H0). Default 0.01.\n" +
                "- Yes if the Wishart test should be used. No if the Delta test should be used. These are two tests of whether a set of four variables constitutes a pure tetrad—that is, if all tetrads for this set of four variables vanish. For the notion of a vanishing tetrad, see Spirtes et al., 2000. Default No (Delta test).\n" +
                "- Yes if the GAP algorithm should be used. No if the SAG algorithm should be used (faster, less accurate)."
)
public class Ftfc implements Algorithm, HasKnowledge, ClusterAlgorithm {

    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public Ftfc() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (!parameters.getBoolean("bootstrapping")) {
            ICovarianceMatrix cov = null;

            if (dataSet instanceof Dataset) {
                cov = DataUtils.getCovMatrix(dataSet);
            } else if (dataSet instanceof ICovarianceMatrix) {
                cov = (ICovarianceMatrix) dataSet;
            } else {
                throw new IllegalArgumentException("Expected a dataset or a covariance matrix.");
            }

            double alpha = parameters.getDouble("alpha");

            boolean gap = parameters.getBoolean("useGap", true);
            FindTwoFactorClusters.Algorithm algorithm;

            if (gap) {
                algorithm = FindTwoFactorClusters.Algorithm.GAP;
            } else {
                algorithm = FindTwoFactorClusters.Algorithm.SAG;
            }

            FindTwoFactorClusters search
                    = new FindTwoFactorClusters(cov, algorithm, alpha);
            search.setVerbose(parameters.getBoolean("verbose"));

            return search.search();
        } else {
            Ftfc algorithm = new Ftfc();

            algorithm.setKnowledge(knowledge);
//          if (initialGraph != null) {
//      		algorithm.setInitialGraph(initialGraph);
//  		}

            DataSet data = (DataSet) dataSet;

            GeneralBootstrapTest search = new GeneralBootstrapTest(data, algorithm, parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FTFC (Find Two Factor Clusters)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("useWishart");
        parameters.add("useGap");
        parameters.add("verbose");
        // Bootstrapping
        parameters.add("bootstrapping");
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

}
