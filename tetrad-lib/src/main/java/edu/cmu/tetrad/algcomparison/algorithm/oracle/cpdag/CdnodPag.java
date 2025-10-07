package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.cdnod_pag.CdnodPagOrienter;
import edu.cmu.tetrad.search.cdnod_pag.CgLrtChangeTest;
import edu.cmu.tetrad.search.cdnod_pag.ChangeTest;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.R0R4Strategy;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * CD-NOD wrapper for algcomparison.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CD-NOD-PAG",
        command = "cdnodpag",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class CdnodPag extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, TakesScoreWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

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
    private ScoreWrapper score;

    /**
     * Default constructor for the Cdnod class. This constructor initializes a new instance of Cdnod without any
     * specific parameters.
     */
    public CdnodPag() {
    }

    /**
     * Constructs a new instance of Cdnod with the specified IndependenceWrapper.
     *
     * @param test the IndependenceWrapper instance to be associated with this Cdnod object
     */
    public CdnodPag(IndependenceWrapper test) {
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

        R0R4Strategy strategy = new R0R4StrategyTestBased(test.getTest(data, parameters));
        FciOrient fciOrient = new FciOrient(strategy);

        // Configure core search. We pass the dataset that ALREADY has C as the last column.
        // (A) FCIT builder over non-env columns
       edu.cmu.tetrad.search.cdnod_pag.CdnodPag.PagBuilder pagBuilder = (DataSet dataWithoutEnv) -> {
           Score _score = score.getScore(dataWithoutEnv, parameters);
           IndependenceTest _test = test.getTest(dataWithoutEnv, parameters);

           Fcit fcit = new Fcit(_test, _score);
           fcit.setKnowledge(knowledge);
           try {
               return fcit.search();
           } catch (InterruptedException e) {
               throw new RuntimeException(e);
           }
       };

        // (B) Propagator that runs your standard FCI rule propagation
        // TODO: call your rule propagation (R0–R10 + discriminating paths)
        // e.g., new FciRulePropagator().propagate(pag);
        CdnodPagOrienter.Propagator prop = fciOrient::finalOrientation;

        Function<Graph, Boolean> legalityCheck = PagLegalityCheck::isLegalPagFast;

        // (D) Change test (CG-LRT residual-vs-E)
        ChangeTest changeTest = new CgLrtChangeTest(); // implement its TODOs with your calls

        edu.cmu.tetrad.search.cdnod_pag.CdnodPag cdnodPag = new edu.cmu.tetrad.search.cdnod_pag.CdnodPag(
                data,
                parameters.getDouble(Params.ALPHA), changeTest,
                pagBuilder /* e.g., (ds) -> new FCIT(ds, knowledge).search() */,
                legalityCheck,
                () -> prop /* e.g., (g) -> RRules.propagate(g) */)
                .addContexts("Rings")                  // tier-1 contexts
                .putTier("Rings", 1)                   // optional tiers
                .putTier("Length", 2)
                .putTier("Whole", 3)
                .forbidArrowheadsInto("Sex")           // any extra protected nodes
                .withMaxSubsetSize(1)
                .withProxyGuard(true);

        return cdnodPag.run();
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
        return "CD-NOD-PAG using " + test.getDescription() + " and " + score.getDescription();
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

//        parameters.add(Params.STABLE_FAS);
//        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
//        parameters.add(Params.DEPTH);
//        parameters.add(Params.FDR_Q);

        parameters.add(Params.ALPHA);
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

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}