package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * <p>Implements common elements of a permutation search. The specific parts
 * for each permutation search are implemented as a SuborderSearch.</p>
 *
 * <p>This class specifically handles an optimization for tiered knowledge, whereby
 * tiers in the knowledge can be searched one at a time in order from the lowest to highest, taking all variables from
 * previous tiers as a fixed for a later tier. This allows these permutation searches to search over many more variables
 * than otherwise, so long as tiered knowledge is available to organize the search.</p>
 *
 * <p>This class is configured to respect the knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author bryanandrews
 * @see SuborderSearch
 * @see Boss
 * @see Sp
 * @see Knowledge
 */
public class PermutationSearch {

    /**
     * Private final variable representing an interface for suborder searches for various types of permutation
     * algorithms.
     * <p>
     * This variable is used in the containing class PermutationSearch to form a complete permutation search algorithm.
     * The SuborderSearch interface defines methods for searching suborders, setting knowledge, retrieving variables,
     * retrieving parents, and retrieving the score.
     *
     * @see PermutationSearch
     * @see SuborderSearch
     */
    private final SuborderSearch suborderSearch;

    /**
     * Represents a list of Node variables.
     * <p>
     * This list is immutable and cannot be modified once initialized. It contains nodes used in graph construction and
     * search algorithms.
     */
    private final List<Node> variables;

    /**
     * Represents an ordered list of Node objects.
     */
    private final List<Node> order;

    /**
     * The gsts variable represents a mapping between Nodes and GrowShrinkTree objects. It is a private final instance
     * variable of type Map. The Map is used to associate each Node with its corresponding GrowShrinkTree.
     */
    private final Map<Node, GrowShrinkTree> gsts;

    /**
     * Represents a private instance variable {@code knowledge} of type Knowledge in the class PermutationSearch. This
     * variable is used to store knowledge for the search.
     */
    private Knowledge knowledge = new Knowledge();

    private boolean cpdag = true;

    private long seed = -1;

    /**
     * Constructs a new PermutationSearch using the given SuborderSearch.
     *
     * @param suborderSearch The SuborderSearch (see).
     * @see SuborderSearch
     */
    public PermutationSearch(SuborderSearch suborderSearch) {
        this.suborderSearch = suborderSearch;
        this.variables = suborderSearch.getVariables();
        this.order = new ArrayList<>();
        this.gsts = new HashMap<>();

        Score score = suborderSearch.getScore();
        Map<Node, Integer> index = new HashMap<>();

        int i = 0;
        for (Node node : this.variables) {
            index.put(node, i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
            this.order.add(node);
        }
    }

    /**
     * Construct a graph given a specification of the parents for each node.
     *
     * @param nodes   The nodes.
     * @param parents A map from each node to its parents.
     * @param cpDag   Whether a CPDAG is wanted, if false, a DAG.
     * @return The constructed graph.
     */
    public static Graph getGraph(List<Node> nodes, Map<Node, Set<Node>> parents, boolean cpDag) {
        return getGraph(nodes, parents, null, cpDag);
    }

    /**
     * Constructs a graph given a specification of the parents for each node.
     *
     * @param nodes     The nodes.
     * @param parents   A map from each node to its parents.
     * @param knowledge the knowledge to use to construct the graph.
     * @param cpDag     Whether a CPDAG is wanted, if false, a DAG.
     * @return The construted graph.
     */
    public static Graph getGraph(List<Node> nodes, Map<Node, Set<Node>> parents, Knowledge knowledge, boolean cpDag) {
        Graph graph = new EdgeListGraph(nodes);

        for (Node a : nodes) {
            for (Node b : parents.get(a)) {
                graph.addDirectedEdge(b, a);
            }
        }

        if (cpDag) {
            MeekRules rules = new MeekRules();
            if (knowledge != null) rules.setKnowledge(knowledge);
            rules.setVerbose(false);
            rules.orientImplied(graph);
        }

        return graph;
    }

    /**
     * Performs a search for a graph using the default options. Returns the resulting graph.
     *
     * @return The constructed CPDAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        return search(true);
    }

    /**
     * Performe the search and return a CPDAG.
     *
     * @param cpdag True a CPDAG is wanted, if false, a DAG.
     * @return The CPDAG.
     * @throws InterruptedException if any
     */
    public Graph search(boolean cpdag) throws InterruptedException {
        if (this.seed != -1) {
            RandomUtil.getInstance().setSeed(this.seed);
        }

        List<String> notInTier = new ArrayList<>();
        for (Node node : variables) {
            notInTier.add(node.getName());
        }

        for (int i = 0; i < this.knowledge.getNumTiers(); i++) {
            List<String> tier = this.knowledge.getTier(i);
            notInTier.removeAll(tier);
        }

        List<Node> prefix;
        if (!this.knowledge.isEmpty() && notInTier.isEmpty()) {
//        if (!this.knowledge.isEmpty() && this.knowledge.getVariablesNotInTiers().isEmpty()) {
            List<Node> order = new ArrayList<>(this.order);
            this.order.clear();
            int start = 0;
            List<Node> suborder;

            for (int i = 0; i < this.knowledge.getNumTiers(); i++) {
                prefix = new ArrayList<>(this.order);
                List<String> tier = this.knowledge.getTier(i);

                for (Node node : order) {
                    String name = node.getName();
                    if (!tier.contains(name)) continue;
                    this.order.add(node);
                    if (!this.knowledge.isTierForbiddenWithin(i)) continue;
                    suborder = this.order.subList(start++, this.order.size());
                    this.suborderSearch.searchSuborder(prefix, suborder, this.gsts);
                }

                if (this.knowledge.isTierForbiddenWithin(i)) continue;
                suborder = this.order.subList(start, this.order.size());
                this.suborderSearch.searchSuborder(prefix, suborder, this.gsts);
                start = this.order.size();
            }
        } else {
            prefix = Collections.emptyList();
            this.suborderSearch.searchSuborder(prefix, this.order, this.gsts);
        }

        return getGraph(this.variables, this.suborderSearch.getParents(), this.knowledge, cpdag);
    }

    /**
     * Retrieves the order list.
     *
     * @return The order list.
     */
    public List<Node> getOrder() {
        return this.order;
    }

    /**
     * Sets the order list for the search.
     *
     * @param order The order list to set. Must contain all variables.
     * @throws AssertionError If the order list does not contain all variables.
     */
    public void setOrder(List<Node> order) {
        assert new HashSet<>(order).containsAll(this.variables);
        this.order.clear();
        this.order.addAll(order);
    }

    /**
     * Retrieves the GrowShrinkTree (GST) associated with the given Node.
     *
     * @param node The Node whose GST is to be retrieved.
     * @return The GrowShrinkTree associated with the given Node.
     */
    public GrowShrinkTree getGST(Node node) {
        return this.gsts.get(node);
    }

    /**
     * Retrieves the list of variables.
     *
     * @return The list of variables.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Sets the knowledge to be used for the search.
     *
     * @param knowledge The knowledge to be set.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
        this.suborderSearch.setKnowledge(knowledge);

        for (Node node : this.variables) {
            List<Node> required = new ArrayList<>();
            List<Node> forbidden = new ArrayList<>();
            for (Node parent : this.variables) {
                if (knowledge.isRequired(parent.getName(), node.getName())) required.add(parent);
                if (knowledge.isForbidden(parent.getName(), node.getName())) forbidden.add(parent);
            }
            if (required.isEmpty() && forbidden.isEmpty()) continue;
            this.gsts.get(node).setKnowledge(required, forbidden);
        }
    }

    /**
     * Retrieves the value of cpdag.
     *
     * @return The value of the cpdag flag.
     */
    public boolean getCpdag() {
        return cpdag;
    }

    /**
     * Sets the flag indicating whether a CPDAG (partially directed acyclic graph) is wanted or not.
     *
     * @param cpdag The value indicating whether a CPDAG is wanted or not.
     */
    public void setCpdag(boolean cpdag) {
        this.cpdag = cpdag;
    }

    /**
     * Sets the seed value used for generating random numbers.
     *
     * @param seed The seed value to set.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }
}
