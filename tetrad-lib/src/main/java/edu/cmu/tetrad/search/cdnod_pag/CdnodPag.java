package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;

/**
 * CD-NOD-PAG runner where ALL Tier-0 variables are treated as contexts. No requirement that a context be the last
 * column.
 */
public final class CdnodPag {

    /**
     * Represents the input dataset used in the construction and operation of the causal discovery algorithm within the
     * {@code CdnodPag} class. This dataset serves as the primary data source for analyzing and discovering graphical
     * structures or dependencies. It must be a valid instance of {@code DataSet}, containing appropriate variables and
     * measurements needed for the algorithm.
     * <p>
     * The {@code dataAll} variable is immutable and required during instantiation of the {@code CdnodPag} object. It
     * provides the foundation for all causal analysis and manipulations performed by this class.
     */
    private final DataSet dataAll;
    /**
     * Significance level for statistical tests applied in the causal discovery process. Typically used to determine
     * whether a relationship or dependency is statistically significant in the context of the algorithm.
     * <p>
     * A lower alpha value indicates a stricter threshold for determining significance, reducing the likelihood of Type
     * I errors (false positives), whereas a higher alpha value increases sensitivity at the expense of potentially more
     * false positives.
     */
    private final double alpha;
    /**
     * The test for detecting changes or instabilities in conditional probabilities, used specifically by CD-NOD-PAG
     * procedures. The ChangeTest instance provides methods for determining whether relationships between variables are
     * context-dependent, which is essential for causal discovery in the presence of multiple contexts.
     * <p>
     * This variable is immutable and is initialized during the creation of the CdnodPag object. It represents the
     * implementation of the change detection logic required for the CdnodPag algorithm.
     */
    private final ChangeTest changeTest;
    /**
     * A functional interface used to construct or search a PAG (Partially Oriented Graph) based on a given dataset.
     * Instances of this interface encapsulate the logic for constructing the graph using the provided data and
     * algorithmic constraints.
     * <p>
     * This variable is a core component of the CD-NOD-PAG class, responsible for generating the underlying graph
     * structure, which enables further processing and analysis such as orientation of edges or validating causal
     * relationships within the dataset.
     * <p>
     * In the CD-NOD-PAG context, this encapsulates the user-specified or default graph-building logic, and it interacts
     * with other components such as change tests, legality checks, and propagation mechanisms to work cohesively in the
     * search process.
     */
    private final PagBuilder pagBuilder;
    /**
     * A {@link Function} used to perform a legality check on a given {@link Graph}. This function determines whether a
     * specific {@link Graph} configuration is valid under the implemented constraints and rules.
     * <p>
     * The {@code legalityCheck} is typically invoked during the execution of graph processing to ensure the structure
     * adheres to specific criteria.
     */
    private final Function<Graph, Boolean> legalityCheck;
    /**
     * Names for Tier-0 contexts (set these from your Knowledge / UI)
     */
    private final List<String> contextNames = new ArrayList<>();
    /**
     * Optional: extra protected nodes (no arrowheads into these)
     */
    private final Set<String> forbidHeadsIntoByName = new LinkedHashSet<>();
    /**
     * Represents prior knowledge or assumptions used during the construction or analysis of a graph. This field holds
     * immutable information that may influence the structure or constraints applied to a graph during processing.
     */
    private final Knowledge knowledge;
    /**
     * A functional component responsible for propagating changes or transformations on a {@code Graph} object,
     * returning a potentially modified {@code Graph}.
     * <p>
     * This function is central to the process of applying specific graph-related logic during the execution of the
     * associated algorithm. It acts as a transformation operation that encapsulates rules or constraints which ensure
     * the validity or correctness of the graph based on the desired outcome or use case.
     */
    private final Function<Graph, Graph> propagator;
    /**
     * Denotes the maximum allowable size of subsets to be considered in subset-based operations.
     * <p>
     * This variable is utilized to limit the number of nodes included in a conditioning set or other computations
     * involving subsets, ensuring computational feasibility. Default value is 1.
     */
    private int maxSubsetSize = 1;
    /**
     * A flag indicating whether the use of the "proxy guard" mechanism is enabled.
     * <p>
     * When enabled, the proxy guard enforces specific constraints or validations during the execution of the algorithm.
     * This could involve protecting or ensuring correctness against unintended behaviors in the processing or
     * manipulation of graph structures within the CD-NOD-PAG orientation procedure.
     * <p>
     * Default value is {@code true}.
     */
    private boolean useProxyGuard = true;

    /**
     * Constructs an instance of the CdnodPag class with the specified parameters.
     *
     * @param dataAll       The dataset to be analyzed.
     * @param alpha         The significance level used in statistical tests.
     * @param changeTest    The statistical change test to assess dependencies among variables.
     * @param pagBuilder    The PAG builder used to construct the initial PAG.
     * @param legalityCheck A function that determines the legality of a given graph.
     * @param propagator    A function to propagate structural changes in the graph.
     * @param knowledge     Prior background knowledge to inform the graph structure.
     */
    public CdnodPag(DataSet dataAll,
                    double alpha,
                    ChangeTest changeTest,
                    PagBuilder pagBuilder,
                    Function<Graph, Boolean> legalityCheck,
                    Function<Graph, Graph> propagator,
                    Knowledge knowledge
    ) {
        this.dataAll = Objects.requireNonNull(dataAll);
        this.alpha = alpha;
        this.changeTest = Objects.requireNonNull(changeTest);
        this.pagBuilder = Objects.requireNonNull(pagBuilder);
        this.legalityCheck = Objects.requireNonNull(legalityCheck);
        this.propagator = Objects.requireNonNull(propagator);
        this.knowledge = Objects.requireNonNull(knowledge);
    }

    private static List<Node> resolveNodes(Graph g, Collection<String> names) {
        List<Node> out = new ArrayList<>();
        for (String name : names) {
            Node n = g.getNode(name);
            if (n != null) out.add(n);
        }
        return out;
    }

    // ---- Configuration API ----

    private static Set<Node> resolveNodes(Graph g, Set<String> names) {
        return new LinkedHashSet<>(resolveNodes(g, (Collection<String>) names));
    }

    /**
     * Remove any arrowheads INTO target: for any U *-> target, set endpoint at target to CIRCLE.
     */
    private static void stripHeadsInto(Graph pag, Node target) {
        for (Node u : new ArrayList<>(pag.getAdjacentNodes(target))) {
            if (pag.getEndpoint(u, target) == Endpoint.ARROW) pag.setEndpoint(u, target, Endpoint.CIRCLE);
            if (pag.getEndpoint(target, u) == Endpoint.ARROW) pag.setEndpoint(target, u, Endpoint.CIRCLE);
        }
    }

    /**
     * Sets the maximum subset size to the specified value if it is non-negative. If a negative value is provided, the
     * maximum subset size is set to 0. This method is used for configuring the object and returns the current
     * instance.
     *
     * @param k the desired maximum subset size. If negative, it will default to 0.
     * @return the current instance of {@code CdnodPag} with the updated maximum subset size.
     */
    public CdnodPag withMaxSubsetSize(int k) {
        this.maxSubsetSize = Math.max(0, k);
        return this;
    }

    /**
     * Configures whether the proxy guard feature is enabled or disabled. The proxy guard feature is used as part of the
     * configuration of this instance. This method returns the current instance of {@code CdnodPag}, allowing method
     * chaining.
     *
     * @param on a boolean value indicating whether to enable (true) or disable (false) the proxy guard feature.
     * @return the current instance of {@code CdnodPag} with updated proxy guard configuration.
     */
    public CdnodPag withProxyGuard(boolean on) {
        this.useProxyGuard = on;
        return this;
    }

    /**
     * <p>
     * Executes the algorithm to construct and orient a causal PAG (Partial Ancestral Graph) using the provided data,
     * context variables, and prior knowledge.
     * </p>
     *
     * <p>The method performs the following operations in order:</p>
     *
     * <ol>
     *   <li>Clears current context names and updates forbidden nodes.</li>
     *   <li>Builds a baseline PAG based on all variables and propagates any necessary changes.</li>
     *   <li>Resolves context nodes and applies constraints to remove specific arrowheads in the graph.</li>
     *   <li>Constructs a change oracle for assessing effects related to context changes.</li>
     *   <li>Protects specified nodes, including context and other user-specified nodes, from orientation changes.</li>
     *   <li>Optionally associates nodes with tiers based on prior knowledge to enforce tier-based orientation rules.</li>
     *   <li>Runs the orientation mechanism to refine the PAG structure based on causal and tier constraints.</li>
     * </ol>
     *
     * <p>
     * <b>Returns:</b> the resulting PAG (Partial Ancestral Graph) after applying change-based orientation
     * and enforcing constraints informed by contexts and prior knowledge.
     * </p>
     *
     * @return the resulting PAG (Partial Ancestral Graph) after applying change-based orientation and enforcing
     * nforcing constraints informed by contexts and prior knowledge
     */
    public Graph run() {

        contextNames.clear();

        for (String name : knowledge.getTier(0)) {
            contextNames.add(name);
            forbidHeadsIntoByName.add(name);
        }

        // 1) Build baseline PAG on ALL variables (contexts included)
        Graph pag = pagBuilder.search(dataAll);
        pag = propagator.apply(pag);

        // Resolve Node handles
        List<Node> contexts = resolveNodes(pag, contextNames);
        if (contexts.isEmpty()) {
            System.out.println("[CD-NOD-PAG] No context variables provided; skipping change-based orientation.");
            return pag;
        }

        // 1a) Post-hoc safeguard: remove arrowheads INTO any context
        boolean stripArrowheadsIntoContexts = true;
        if (stripArrowheadsIntoContexts) {
            for (Node c : contexts) stripHeadsInto(pag, c);
        }

        // 2) Make the change oracle over ALL contexts
        ChangeOracle oracle = new ChangeOracle(dataAll, contexts, alpha, changeTest);

        // 3) Protected nodes: contexts + extras
        Set<Node> protectedNodes = new LinkedHashSet<>(contexts);
        protectedNodes.addAll(resolveNodes(pag, forbidHeadsIntoByName));

        // 4) Optional: build a Node->tier map
        Map<Node, Integer> tiers = new HashMap<>();

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            List<String> tier = knowledge.getTier(i);
            for (String nodeName : tier) {
                Node n = pag.getNode(nodeName);
                if (n != null) tiers.put(n, i);
            }
        }

        CdnodPagOrienter orienter = new CdnodPagOrienter(pag, oracle, legalityCheck, propagator)
                .withMaxSubsetSize(maxSubsetSize)
                .withProxyGuard(useProxyGuard)
                .withExcludeContextsFromS(true)      // exclude all contexts from S
                .forbidArrowheadsInto(protectedNodes)
                .withTiers(tiers);                   // only orient X->Y if tier(X) < tier(Y) when both known

        orienter.run();
        return pag;
    }

    /**
     * Functional interface representing a builder for constructing a Partial Ancestral Graph (PAG).
     * <p>
     * This interface provides a method to define the construction of a PAG by using a dataset to infer relationships
     * while adhering to specific constraints or methodologies defined by its implementation.
     */
    @FunctionalInterface
    public interface PagBuilder {

        /**
         * Performs a search operation on the provided dataset to build a graph
         * representing relationships or dependencies within the data.
         *
         * @param fullData the dataset on which the search operation is performed
         * @return the resulting graph constructed from the dataset
         */
        Graph search(DataSet fullData);
    }
}