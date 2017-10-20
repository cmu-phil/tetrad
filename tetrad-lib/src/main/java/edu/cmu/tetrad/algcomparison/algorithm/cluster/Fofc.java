package edu.cmu.tetrad.algcomparison.algorithm.cluster;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.FindOneFactorClusters;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;
import java.util.ArrayList;
import java.util.List;

/**
 * FOFC.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Fofc",
        command = "fofc",
        algoType = AlgType.search_for_structure_over_latents,
        description = "The FOFC (Find One Factor Clusters) is an alternative method that achieves the same goal as BPC; in testing, it seems to scale better with somewhat better accuracy (Kummerfeld and Ramsey, 2016). The basic idea is to build up clusters one at a time by adding variables that keep them pure, in the sense that all relevant tetrad constraints still hold. There are different ways of going about this. One could try to build one cluster up as far as possible, then remove all of those variables from the set, and try to make a another cluster using the remaining variables (SAG, i.e., Seed and Grow). Or one can try in parallel to grow all possible clusters and then choose among the grown clusters using some criterion such as cluster size (GAP, Grow and Pick). In general, GAP is more accurate. The result is a clustering of variables. Similarly to BPC, MimBuild may be run on a covariance matrix estimated over the latents for the resulting clusters to find a pattern over the latents that represents the causal structure over the latents.\n" +
                "\n" +
                "Input Assumptions: Continuous data containing as many measures as are available.\n" +
                "\n" +
                "Output Format: For FOFC, a clustering of variables. For MimBuild, a pattern over latents. \n" +
                "\n" +
                "Parameters:\n" +
                "- Cutoff for p-values (alpha). Conditional independence tests with p-values greater than this will be judged to be independent (H0). Default 0.01.\n" +
                "- Yes if the Wishart test should be used. No if the Delta test should be used. These are two tests of whether a set of four variables constitutes a pure tetrad—that is, if all tetrads for this set of four variables vanish. For the notion of a vanishing tetrad, see Spirtes et al., 2000. For the Delta test, see ??. Default No (Delta test).\n" +
                "- Yes if the GAP algorithm should be used. No if the SAG algorithm should be used."
)
public class Fofc implements Algorithm, TakesInitialGraph, HasKnowledge, ClusterAlgorithm {

    static final long serialVersionUID = 23L;
    private Graph initialGraph = null;
    private Algorithm algorithm = null;
    private IKnowledge knowledge = new Knowledge2();

    public Fofc() {
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

            boolean gap = parameters.getBoolean("useGap", true);
            FindOneFactorClusters.Algorithm algorithm;

            if (gap) {
                algorithm = FindOneFactorClusters.Algorithm.GAP;
            } else {
                algorithm = FindOneFactorClusters.Algorithm.SAG;
            }

            edu.cmu.tetrad.search.FindOneFactorClusters search
                    = new edu.cmu.tetrad.search.FindOneFactorClusters(cov, testType, algorithm, alpha);
            search.setVerbose(parameters.getBoolean("verbose"));

            return search.search();
        } else {
            Fofc algorithm = new Fofc();

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
