package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BuildPureClusters;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import java.util.ArrayList;
import java.util.List;

/**
 * BPC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Bpc",
        command = "bpc",
        algoType = AlgType.search_for_structure_over_latents,
        description = "BPC (Build Pure Clusters) searches for causal structure over latent variables, where the true models are Multiple Indicator Models (MIM’s). The idea is this. There is a set of latent (unmeasured) variables over which a directed acyclic model has been defined. Then for each of these latent L there are 3 (preferably 4) or more measures of that variable—that is, measured variables that are all children of L. Under these conditions, one may define tetrad constraints (see Spirtes et al., 2000). There is a theorem to the effect that if certain patterns of these tetrad constraints hold, there must be a latent common cause of all of them (the Tetrad Representation Theorem, see Spirtes, Glymour, and Scheines (1993), where the BPC (“Build Pure Clusters”) algorithm is defined and discussed.) Moreover, once one has such a “measurement model,” once can estimate a covariance matrix over the latent variables that are parents of the measures and use some algorithm such as PC or GES to estimate a pattern over the latents. The algorithm to run PC or GES on this covariance matrix is called MimBuild (“MIM” is the the graph, Multiple Indicator Model; “Build” means build). In this way, one may recover causal structure over the latents. The more measures one has for each latent, the better the result is, generally. The larger the sample size the better. One important issue is that the algorithm is sensitive to so-called “impurities”—that is, causal edges among the measured variables, or between measured variables and unintended latent. The algorithm will in effect remove one measure in each impure pair from consideration.\n" +
                "\n" +
                "Input Assumptions: Continuous data, a collection of measurements in the above sense, excluding the latent variables (which are to be learned).\n" +
                "\n" +
                "Output Format: For BPC, a measurement model, in the above sense. This is represented as a clustering of variables; it may be inferred that there is a single latent for each output cluster. For MimBuild, a pattern over the latent variables, one for each cluster.\n" +
                "\n" +
                "Parameters: \n" +
                "- Cutoff for p-values (alpha). Conditional independence tests with p-values greater\n" +
                "than this will be judged to be independent (H0). Default 0.01.\n" +
                "- Yes if the Wishart test should be used. No if the Delta test should be used. These are two tests of whether a set of four variables constitutes a pure tetrad—that is, if all tetrads for this set of four variables vanish. For the notion of a vanishing tetrad, see Spirtes et al., 2000. For the Delta test, see ??. Default No (Delta test)."
)
public class Bpc implements Algorithm, TakesInitialGraph, HasKnowledge, ClusterAlgorithm {

    static final long serialVersionUID = 23L;
    private Algorithm algorithm = null;
    private Graph initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public Bpc() {
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (!parameters.getBoolean("bootstrapping")) {
            ICovarianceMatrix cov = DataUtils.getCovMatrix(dataSet);
            double alpha = parameters.getDouble("alpha");

            boolean wishart = parameters.getBoolean("useWishart", true);
            TestType testType;

            if (wishart) {
                testType = TestType.TETRAD_WISHART;
            } else {
                testType = TestType.TETRAD_DELTA;
            }

            TestType purifyType = TestType.TETRAD_BASED;

            BuildPureClusters bpc = new BuildPureClusters(cov, alpha, testType, purifyType);
            bpc.setVerbose(parameters.getBoolean("verbose"));

            return bpc.search();
        } else {
            Bpc algorithm = new Bpc();

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
        return "FOFC (Find One Factor Clusters)";
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

    @Override
    public Graph getInitialGraph() {
        return initialGraph;
    }

    @Override
    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    @Override
    public void setInitialGraph(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

}
