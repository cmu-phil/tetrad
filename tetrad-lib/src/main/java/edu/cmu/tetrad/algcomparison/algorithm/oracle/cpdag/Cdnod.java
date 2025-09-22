package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
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
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CD-NOD",
        command = "cdnod",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Cdnod extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents an instance of {@link IndependenceWrapper} used within the Cdnod class. This field is utilized for
     * performing independence tests and configuring algorithm-specific behaviors based on the provided implementation.
     */
    private IndependenceWrapper test;
    /**
     * Encapsulates prior knowledge constraints to be used during the execution of the algorithm. It defines any
     * structural assumptions or constraints about the data or model, such as forbidden or required edges.
     * <p>
     * This field can be set and retrieved via the corresponding accessor methods.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Default constructor for the Cdnod class. This constructor initializes a new instance of Cdnod without any
     * specific parameters.
     */
    public Cdnod() {
    }

    /**
     * Constructs a new instance of Cdnod with the specified IndependenceWrapper.
     *
     * @param test the IndependenceWrapper instance to be associated with this Cdnod object
     */
    public Cdnod(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // Map collider style param -> search enum
        edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle colliderOrientationStyle =
                switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
                    case 1 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.SEPSETS;
                    case 2 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.CONSERVATIVE;
                    case 3 -> edu.cmu.tetrad.search.Cdnod.ColliderOrientationStyle.MAX_P;
                    default -> throw new IllegalArgumentException("Invalid collider orientation style");
                };

        // Build the IndependenceTest over this dataset
        IndependenceTest indTest = getIndependenceWrapper().getTest(dataModel, parameters);

        // Pull runtime knobs from Parameters (with sensible defaults)
        boolean stable = parameters.getBoolean(Params.STABLE_FAS, true);
        int depth = parameters.getInt(Params.DEPTH, -1);
        boolean verbose = parameters.getBoolean(Params.VERBOSE, false);
        double alpha = parameters.getDouble(Params.ALPHA, 0.05);
        double maxPMargin = 0.0;//parameters.getDouble(Params.MAXP_MARGIN, 0.0_;

        // Configure core search. We pass the dataset that ALREADY has C as the last column.
        edu.cmu.tetrad.search.Cdnod cd = new edu.cmu.tetrad.search.Cdnod.Builder()
                .test(indTest)
                .data(data)                         // << important: use already-augmented data
                .alpha(alpha)                       // kept for parity (FAS may ignore)
                .stable(stable)
                .colliderStyle(colliderOrientationStyle)
                .maxPMargin(Math.max(0.0, maxPMargin)) // 0.0 => classic MAX-P
                .depth(depth)                       // -1 => no cap
                .knowledge(knowledge)
                .verbose(verbose)
                .build();

        Graph g = cd.search();

        // If you want FDR post-processing, uncomment this block:
        /*
        double fdrQ = parameters.getDouble(Params.FDR_Q);
        if (fdrQ != 0.0) {
            boolean negativelyCorrelated = true;
            Graph fdrGraph = IndTestFdrWrapper.doFdrLoop(cd::search, negativelyCorrelated, alpha, fdrQ, verbose);
            return fdrGraph;
        }
        */

        return g;
    }

    /**
     * Generates a comparison graph by converting a given graph into its completed partially directed acyclic graph
     * (CPDAG) form.
     *
     * @param graph the input graph to be processed, represented as a Graph object
     * @return a new Graph object representing the CPDAG form of the input graph
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * Provides a description of the CD-NOD algorithm using the associated configured IndependenceWrapper test or a
     * default description if no test is configured.
     *
     * @return A string description of the CD-NOD algorithm and its associated test.
     */
    @Override
    public String getDescription() {
        return "CD-NOD using " + (this.test != null ? this.test.getDescription() : "configured test");
    }

    /**
     * Retrieves the data type associated with the current test instance.
     *
     * @return the data type required by the configured IndependenceWrapper test, which can be Continuous, Discrete,
     * Mixed, or other defined types.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves a list of parameter names associated with the CD-NOD algorithm. These parameters are used to configure
     * specific aspects of the algorithm's execution.
     *
     * @return a list of strings representing the names of parameters available for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
        parameters.add(Params.DEPTH);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Retrieves the knowledge object associated with the current instance of Cdnod.
     *
     * @return the Knowledge object representing the domain knowledge or constraints configured for this algorithm
     * instance.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object for the current instance of the Cdnod class. The knowledge object represents domain
     * knowledge or constraints used to inform the algorithm's execution.
     *
     * @param knowledge the Knowledge object to set for this instance
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the configured IndependenceWrapper instance associated with this object.
     *
     * @return the current IndependenceWrapper instance used for independence testing.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the IndependenceWrapper test instance for this object. The IndependenceWrapper is used to perform
     * statistical independence tests as part of the CD-NOD algorithm's functionality.
     *
     * @param test the IndependenceWrapper instance to be set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}