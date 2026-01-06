package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * CD-NOD wrapper for algcomparison.
 *
 * <p>Semantics: ALL Tier-0 variables in Knowledge are treated as contexts (no requirement that any context be a
 * particular column). The underlying search is {@link edu.cmu.tetrad.search.Cdnod}.</p>
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CD-NOD",
        command = "cdnod",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class Cdnod extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test wrapper used by the CD-NOD algorithm.
     */
    private IndependenceWrapper test;
    /**
     * The knowledge object encapsulating domain-specific information for the CD-NOD algorithm.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Default constructor for the Cdnod class.
     * This constructor initializes a new instance of the Cdnod algorithm
     * without any predefined configurations or dependencies.
     */
    public Cdnod() {
    }

    /**
     * Constructs a new instance of the Cdnod class using the provided
     * IndependenceWrapper instance. The IndependenceWrapper is used to
     * run independence tests required by the algorithm.
     *
     * @param test The IndependenceWrapper instance used to perform
     *             independence testing. This parameter is essential for
     *             configuring and executing the Cdnod algorithm.
     */
    public Cdnod(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // Collider orientation style.
        edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle colliderOrientationStyle =
                switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
                    case 1 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.SEPSETS;
                    case 2 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.CONSERVATIVE;
                    case 3 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.MAX_P;
                    default -> throw new IllegalArgumentException("Invalid collider orientation style");
                };

        // IndependenceTest must be built on the SAME dataset we pass into the search.
        IndependenceTest indTest = getIndependenceWrapper().getTest(data, parameters);

        boolean stable = parameters.getBoolean(Params.STABLE_FAS, true);
        int depth = parameters.getInt(Params.DEPTH, -1);
        boolean verbose = parameters.getBoolean(Params.VERBOSE, false);
        double alpha = parameters.getDouble(Params.ALPHA, 0.05);

        // If/when you add it to Params, wire it here.
        double maxPMargin = 0.0; // parameters.getDouble(Params.MAXP_MARGIN, 0.0);

        edu.cmu.tetrad.search.Cdnod cd = new edu.cmu.tetrad.search.Cdnod.Builder()
                .test(indTest)
                .data(data)                               // contexts may be anywhere; from Knowledge tier 0
                .alpha(alpha)                             // kept for parity
                .stable(stable)
                .colliderStyle(colliderOrientationStyle)
                .maxPMargin(Math.max(0.0, maxPMargin))
                .depth(depth)
                .knowledge(knowledge)                     // IMPORTANT: provides tier 0 contexts and tier constraints
                .verbose(verbose)
                .build();

        return cd.search();
    }

    /**
     * Returns a CPDAG that is the comparison graph for the given true directed graph.
     * @param graph The true directed graph, if there is one.
     * @return The CPDAG comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * Returns a description of the algorithm.
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "CD-NOD using " + (this.test != null ? this.test.getDescription() : "configured test");
    }

    /**
     * Returns the data type of the algorithm.
     * @return The data type of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns the parameters of the algorithm.
     * @return The parameters of the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
        parameters.add(Params.DEPTH);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.ALPHA);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Returns the knowledge of the algorithm.
     * @return The knowledge of the algorithm.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge of the algorithm.
     * @param knowledge a knowledge object.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the independence wrapper of the algorithm.
     * @return The independence wrapper of the algorithm.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper of the algorithm.
     * @param test the independence wrapper.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}