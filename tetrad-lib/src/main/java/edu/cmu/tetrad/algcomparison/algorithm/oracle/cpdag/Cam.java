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

/**
 * Represents the CAM algorithm, which is a causal additive model search algorithm.
 * This class extends AbstractBootstrapAlgorithm and implements Algorithm, HasKnowledge,
 * ReturnsBootstrapGraphs, and LatentStructureAlgorithm interfaces. It provides functionality
 * for searching for optimal directed acyclic graph (DAG) structures that best fit the data.
 */
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

    /**
     * Represents the knowledge associated with the {@code Cam} class.
     * This field is used to store domain-specific background knowledge
     * that may influence the behavior of the implemented search algorithm
     * within this class. It facilitates incorporating prior knowledge
     * into the algorithm's process.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructor for the Cam class.
     *
     * Initializes a new instance of the Cam algorithm. This algorithm forms part
     * of the latent structure modeling tools and supports bootstrapping and
     * other algorithmic features. The constructor prepares the Cam instance
     * for searching graphical causal structures based on input data and modeling parameters.
     */
    public Cam() {
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // Defaults
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

        // Build search
        edu.cmu.tetrad.search.Cam cam = new edu.cmu.tetrad.search.Cam(data)
                .setPenaltyDiscount(penDiscount)
                .setRidge(ridge)
                .setRestarts(restarts)
                .setMaxForwardParents(maxParents)
                .setPnsTopK(pnsTopK)
                .setVerbose(verbose);

        return cam.search();
    }

    /**
     * Constructs and returns a comparison graph in the form of a completed partially directed acyclic graph (CPDAG).
     * This method transforms an input graph into a directed acyclic graph (DAG)
     * and subsequently converts it to a CPDAG representation.
     *
     * @param graph the input graph to be transformed into a CPDAG; must be a valid representation of a graph.
     * @return the transformed graph in CPDAG form.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * Provides a description of the CAM algorithm's functionality
     * in this implementation.
     *
     * @return a string summarizing the CAM algorithm, including its order via IncEdge
     *         with PNS and pruning via local additive BIC with a swappable scorer.
     */
    @Override
    public String getDescription() {
        return "CAM: order via IncEdge with PNS; pruning via local additive BIC (swappable scorer).";
    }

    /**
     * Retrieves the data type associated with the implementation.
     *
     * @return the data type of the dataset, which in this implementation is always {@code DataType.Continuous}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves a list of parameter names used in the configuration and execution
     * of the CAM algorithm. These parameters are integral to the setting and tuning
     * of the algorithm for specific use cases or datasets.
     *
     * @return a list of parameter names as strings, representing the configurable options
     *         and settings available for the CAM algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.NUM_STARTS);     // prefer this
        params.add(Params.NUM_STARTS);       // fallback
        params.add(Params.GIN_RIDGE);
        params.add(Params.VERBOSE);
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