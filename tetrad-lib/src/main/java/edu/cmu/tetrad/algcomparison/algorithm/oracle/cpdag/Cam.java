package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.LatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.AdditiveLocalScorer;
import edu.cmu.tetrad.search.score.CamAdditivePsplineBic;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@edu.cmu.tetrad.annotation.Algorithm(
        name = "CAM",
        command = "cam",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Cam extends AbstractBootstrapAlgorithm implements Algorithm,
        HasKnowledge, ReturnsBootstrapGraphs, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    private Knowledge knowledge = new Knowledge();

    public Cam() {
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // Defaults
        final int degree = 3; // for polynomial/basis scorers
        final double ridgeDefault = 1e-6;
        final double penDefault = 1.0;

        // Read params (with robust fallbacks)
        final double penDiscount = parameters.getDouble(Params.PENALTY_DISCOUNT, penDefault);
        final boolean verbose = parameters.getBoolean(Params.VERBOSE, false);

        // NUM_RESTARTS is the common key in Tetrad; fall back to NUM_STARTS if that’s what your spec uses
        final int restarts = parameters.getInt(Params.NUM_STARTS,
                parameters.getInt(Params.NUM_STARTS, 10));

        final int maxParents = 20;//parameters.getInt(Params.MAX_PARENTS, 20);

        // PNS strength: keep top-K univariate candidates per target (set to large value to effectively disable)
        final int pnsTopK = Math.min(10, Math.max(1, data.getNumColumns() - 1));//parameters.getInt(Params.PNS_TOP_K,
//                Math.min(10, Math.max(1, data.getNumColumns() - 1)));

        // Ridge: use a dedicated param if present; otherwise tiny default
        final double ridge = parameters.getDouble(Params.GIN_RIDGE, ridgeDefault);

        // Optional toggle: choose scorer family
        final boolean useBasisScorer = parameters.getBoolean("CAM_USE_BASIS_SCORER", true);
        // ^ If you don’t have custom params, set this constant true/false as you like.

        // Build search
        edu.cmu.tetrad.search.Cam cam = new edu.cmu.tetrad.search.Cam(data, degree)
                .setPenaltyDiscount(penDiscount)
                .setRidge(ridge)
                .setRestarts(restarts)
                .setMaxForwardParents(maxParents)
                .setPnsTopK(pnsTopK)
                .setVerbose(verbose);

        // Plug in scorer
        AdditiveLocalScorer scorer = new CamAdditivePsplineBic(data);
//                useBasisScorer
//                        ? new CamAdditiveBic(data, degree)                  // basis-block BIC (fast, stable)
//                        : new CamBasisFunctionBicScorer(data, degree);      // adapter to your BlocksBicScore (identical family)

        scorer.setPenaltyDiscount(penDiscount).setRidge(ridge);
        cam.setScorer(scorer);

        Graph dag = cam.search();
        return dag;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    @Override
    public String getDescription() {
        return "CAM: order via IncEdge with PNS; pruning via local additive BIC (swappable scorer).";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.NUM_STARTS);     // prefer this
        params.add(Params.NUM_STARTS);       // fallback
//        params.add(Params.MAX_PARENTS);
//        params.add(Params.PNS_TOP_K);
        params.add(Params.GIN_RIDGE);
        params.add(Params.VERBOSE);
        // params.add("CAM_USE_BASIS_SCORER"); // if you register custom keys
        return params;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}