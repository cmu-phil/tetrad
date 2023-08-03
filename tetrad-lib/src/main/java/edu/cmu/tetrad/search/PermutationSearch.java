package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;
import edu.cmu.tetrad.search.utils.MeekRules;

import java.util.*;

/**
 * <p>Implements common elements of a permutation search. The specific parts
 * for each permutation search are implemented as a SuborderSearch.</p>
 *
 * <p>This class specifically handles an optimization for tiered knowledge, whereby
 * tiers in the knowledge can be searched one at a time in order from the lowest to highest, taking all variables from
 * previous tiers as a fixed for a later tier. This allows these permutation searches to search over many more
 * variables than otherwise, so long as tiered knowledge is available to organize the search.</p>
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
    private final SuborderSearch suborderSearch;
    private final List<Node> variables;
    private final List<Node> order;
    private final Map<Node, GrowShrinkTree> gsts;
    private final Map<String, Node> nodeMap;
    private Knowledge knowledge = new Knowledge();

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
        this.nodeMap = new HashMap<>();

        int i = 0;
        Score score = suborderSearch.getScore();
        Map<Node, Integer> index = new HashMap<>();
        for (Node node : this.variables) {
            index.put(node, i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
            this.nodeMap.put(node.getName(), node);
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
     * Construct a graph given a specification of the parents for each node.
     *
     * @param nodes     The nodes.
     * @param parents   A map from each node to its parents.
     * @param knowledge the knoweldge to use to construct the graph.
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
        List<Node> prefix;
        if (!this.knowledge.isEmpty() && this.knowledge.getVariablesNotInTiers().isEmpty()) {
            int start = 0;
            List<Node> suborder;
            for (int i = 0; i < this.knowledge.getNumTiers(); i++) {
                prefix = new ArrayList<>(this.order);
                List<String> tier = this.knowledge.getTier(i);
                for (String name : tier) {
                    this.order.add(this.nodeMap.get(name));
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
            this.order.addAll(this.variables);
            this.suborderSearch.searchSuborder(prefix, this.order, this.gsts);
        }

        return getGraph(this.variables, this.suborderSearch.getParents(), this.knowledge, true);
    }

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
}