/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
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
 * <p>This "TopK" variant additionally exposes the top <i>k</i> models found by the underlying suborder search (see
 * {@link BossTopK}). After {@link #search()} has run, the number of models, and the permutation, score, and graph of
 * the i-th model, are available via {@link #getNumModels()}, {@link #getModelPermutation(int)},
 * {@link #getModelScore(int)}, and {@link #getModelGraph(int)}. These accessors are intended to be called from Python
 * via JPype.</p>
 *
 * @author bryanandrews
 * @author josephramsey
 * @see SuborderSearchTopK
 * @see BossTopK
 * @see Knowledge
 */
public class PermutationSearchTopK {

    /**
     * Private final variable representing an interface for suborder searches for various types of permutation
     * algorithms.
     * <p>
     * This variable is used in the containing class PermutationSearch to form a complete permutation search algorithm.
     * The SuborderSearch interface defines methods for searching suborders, setting knowledge, retrieving variables,
     * retrieving parents, and retrieving the score.
     *
     * @see PermutationSearchTopK
     * @see SuborderSearchTopK
     */
    private final SuborderSearchTopK suborderSearch;

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
    private boolean replicatingGraph = false;

    /**
     * The number of top models requested.
     */
    private final int k;

    /**
     * The split threshold delta passed down to the suborder search.
     */
    private final double delta;

    /**
     * Constructs a new PermutationSearchTopK using the given SuborderSearch, retaining a single (top) model. This is
     * equivalent to {@code PermutationSearchTopK(suborderSearch, 1, 0.0)} and reproduces ordinary permutation-search
     * behavior.
     *
     * @param suborderSearch The SuborderSearch (see).
     * @see SuborderSearchTopK
     */
    public PermutationSearchTopK(SuborderSearchTopK suborderSearch) {
        this(suborderSearch, 1, 0.0);
    }

    /**
     * Constructs a new PermutationSearchTopK using the given SuborderSearch, configured to retain the top {@code k}
     * models and to split the search using threshold {@code delta}. The values of {@code k} and {@code delta} are
     * pushed down into the suborder search.
     *
     * @param suborderSearch The SuborderSearch (e.g. a {@link BossTopK}).
     * @param k              The number of top models to retain (must be at least 1).
     * @param delta          The split threshold (must be &ge; 0); 0 disables splitting.
     * @see SuborderSearchTopK
     */
    public PermutationSearchTopK(SuborderSearchTopK suborderSearch, int k, double delta) {
        if (k < 1) throw new IllegalArgumentException("k must be at least 1.");
        if (delta < 0) throw new IllegalArgumentException("delta must be >= 0.");

        this.suborderSearch = suborderSearch;
        this.variables = suborderSearch.getVariables();
        this.order = new ArrayList<>();
        this.gsts = new HashMap<>();
        this.k = k;
        this.delta = delta;

        Score score = suborderSearch.getScore();
        Map<Node, Integer> index = new HashMap<>();

        int i = 0;
        for (Node _node : this.variables) {
            Node node = score.getVariable(_node.getName());

            index.put(node, i++);
            this.gsts.put(node, new GrowShrinkTree(score, index, node));
            this.order.add(node);
        }

        this.suborderSearch.setTopK(k);
        this.suborderSearch.setDelta(delta);
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
     * Constructs a graph given a specification of the parents for each node, with graph replication
     * disabled. Equivalent to {@link #getGraph(List, Map, Knowledge, boolean, boolean)} with the
     * replicating flag set to {@code false}.
     *
     * @param nodes     The list of nodes to include in the graph.
     * @param parents   A map from each node to its set of parent nodes.
     * @param knowledge The knowledge object, if any, to guide edge orientation; may be {@code null}.
     * @param cpDag     If true, a CPDAG is constructed; if false, a DAG.
     * @return The constructed graph, a CPDAG or DAG depending on {@code cpDag}.
     */
    public static Graph getGraph(List<Node> nodes,
                                 Map<Node, Set<Node>> parents,
                                 Knowledge knowledge,
                                 boolean cpDag) {
        return getGraph(nodes, parents, knowledge, cpDag, /*replicating*/ false);
    }

    /**
     * Constructs a graph given a specification of the parents for each node.
     *
     * @param nodes      The list of nodes to include in the graph.
     * @param parents    A map specifying the parents for each node. Each key is a node, and its value is the set of parent nodes.
     * @param knowledge  The knowledge object, if any, to guide the orientation of edges in the graph.
     * @param cpdag      A flag indicating whether a CPDAG (partially directed acyclic graph) is desired. If false, a DAG (directed acyclic graph) is constructed.
     * @param replicating A flag indicating whether graph replication is enabled, allowing mirrored changes in replicated graphs.
     * @return The constructed graph, which may be a DAG or CPDAG depending on the value of the cpDag flag.
     */
    public static Graph getGraph(List<Node> nodes,
                                 Map<Node, Set<Node>> parents,
                                 Knowledge knowledge,
                                 boolean cpdag,
                                 boolean replicating) {
        Graph graph = new EdgeListGraph(nodes);

        for (Node child : nodes) {
            for (Node par : parents.get(child)) {
                graph.addDirectedEdge(par, child);
            }
        }

        if (cpdag) {
            MeekRules rules = new MeekRules();
            rules.setRevertToUnshieldedColliders(true);
            if (knowledge != null) rules.setKnowledge(knowledge);
            rules.setVerbose(false);
            rules.orientImplied(graph);
        }

        if (replicating) {
            graph = GraphUtils.getReplicatingGraph(graph);
        } else if (cpdag) {
            MeekRules rules = new MeekRules();
            rules.setRevertToUnshieldedColliders(true);
            if (knowledge != null) rules.setKnowledge(knowledge);
            rules.setVerbose(false);
            rules.orientImplied(graph);
        }

        return graph;
    }

    /**
     * Performs a search for a graph using the default options. Returns the resulting graph (the top model).
     *
     * @return The constructed CPDAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        return search(true);
    }

    /**
     * Performe the search and return a CPDAG (the top model).
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

        return getGraph(this.variables, this.suborderSearch.getParents(), this.knowledge, cpdag, replicatingGraph);
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

    /**
     * Sets the flag indicating whether graph replication is enabled or not.
     *
     * @param replicatingGraph The value indicating whether graph replication is enabled.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }

    /**
     * Sets a hard cap on the total number of hill-climb runs performed by the underlying suborder search (initial
     * restarts plus deferred continuations). This guarantees termination regardless of the split threshold.
     *
     * @param maxRuns The maximum number of runs (must be at least 1).
     */
    public void setMaxRuns(int maxRuns) {
        this.suborderSearch.setMaxRuns(maxRuns);
    }

    /**
     * Sets whether top-k retention de-duplicates by Markov equivalence class (canonical CPDAG) rather than by
     * permutation. When true, distinct permutations that imply the same CPDAG with the same score are counted once.
     *
     * @param dedupByCpdag True to de-duplicate by CPDAG.
     */
    public void setDedupByCpdag(boolean dedupByCpdag) {
        this.suborderSearch.setDedupByCpdag(dedupByCpdag);
    }

    /**
     * Sets whether every ordering visited across all branches is offered to the top-k pool (including orderings that
     * are suboptimal within their own branch), rather than only each branch's converged optimum.
     *
     * @param optimalAcrossBranches True to consider all visited orderings across branches.
     */
    public void setOptimalAcrossBranches(boolean optimalAcrossBranches) {
        this.suborderSearch.setOptimalAcrossBranches(optimalAcrossBranches);
    }

    // ---- Top-k accessors (intended for use from Python via JPype) -----------------------------------------------

    /**
     * Returns the number of top models requested (the k passed to the constructor).
     *
     * @return k.
     */
    public int getK() {
        return this.k;
    }

    /**
     * Returns the split threshold delta passed to the constructor.
     *
     * @return delta.
     */
    public double getDelta() {
        return this.delta;
    }

    /**
     * Returns the number of top models actually available after a search. This is at most {@link #getK()}, but may be
     * smaller if fewer distinct models were found.
     *
     * @return The number of models available (0 before a search has been run).
     */
    public int getNumModels() {
        return this.suborderSearch.getNumTopK();
    }

    /**
     * Returns the i-th top permutation (0 = highest scoring) as an array of indices into {@link #getVariables()}. The
     * array has one entry per variable and gives a full ordering of all variables.
     *
     * @param i The rank, with 0 &le; i &lt; {@link #getNumModels()}.
     * @return The permutation as an int array of variable indices.
     */
    public int[] getModelPermutation(int i) {
        return this.suborderSearch.getTopKPermutation(i);
    }

    /**
     * Returns the total (higher-is-better) score of the i-th top permutation (0 = highest scoring).
     *
     * @param i The rank, with 0 &le; i &lt; {@link #getNumModels()}.
     * @return The score of that model.
     */
    public double getModelScore(int i) {
        return this.suborderSearch.getTopKScore(i);
    }

    /**
     * Returns the graph of the i-th top permutation (0 = highest scoring), built with the same graph-construction
     * method used for the top model, honoring the current cpdag and replicating-graph flags and the current knowledge.
     *
     * @param i The rank, with 0 &le; i &lt; {@link #getNumModels()}.
     * @return The graph for that model.
     */
    public Graph getModelGraph(int i) {
        Map<Node, Set<Node>> parents = this.suborderSearch.getTopKParents(i);
        return getGraph(this.variables, parents, this.knowledge, this.cpdag, this.replicatingGraph);
    }
}
