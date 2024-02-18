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
 * Implements common elements of a permutation search. The specific parts for each permutation search are implemented as
 * a SuborderSearch.
 * <p>
 * This class specifically handles an optimization for tiered knowledge, whereby tiers in the knowledge can be searched
 * one at a time in order from the lowest to highest, taking all variables from previous tiers as a fixed for a later
 * tier. This allows these permutation searches to search over many more variables than otherwise, so long as tiered
 * knowledge is available to organize the search.
 * <p>
 * This class is configured to respect the knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author bryanandrews
 * @version $Id: $Id
 * @see SuborderSearch
 * @see Boss
 * @see Sp
 * @see Knowledge
 */
public class PermutationSearch {
    private final SuborderSearch suborderSearch;
    private final List<Node> variables;
    private final List<Node> order;
    private final Map<Node, GrowShrinkTree> gsts;
    private Knowledge knowledge = new Knowledge();
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

    // TO DO: moved to a better place like GraphUtils

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
            rules.orientImplied(graph);
        }

        return graph;
    }

    // TO DO: moved to a better place like GraphUtils

    /**
     * Performe the search and return a CPDAG.
     *
     * @return The CPDAG.
     */
    public Graph search() {
        if (this.seed != -1) {
            RandomUtil.getInstance().setSeed(this.seed);
        }

        List<Node> prefix;
        if (!this.knowledge.isEmpty() && this.knowledge.getVariablesNotInTiers().isEmpty()) {
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

        return getGraph(this.variables, this.suborderSearch.getParents(), this.knowledge, true);
    }

    /**
     * <p>Getter for the field <code>order</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getOrder() {
        return this.order;
    }

    /**
     * <p>Setter for the field <code>order</code>.</p>
     *
     * @param order a {@link java.util.List} object
     */
    public void setOrder(List<Node> order) {
        assert new HashSet<>(order).containsAll(this.variables);
        this.order.clear();
        this.order.addAll(order);
    }

    /**
     * <p>getGST.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.search.utils.GrowShrinkTree} object
     */
    public GrowShrinkTree getGST(Node node) {
        return this.gsts.get(node);
    }

    /**
     * Returns the variables.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Sets the knowledge to be used in the search.
     *
     * @param knowledge This knowledge.
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
     * <p>Setter for the field <code>seed</code>.</p>
     *
     * @param seed a long
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }
}
