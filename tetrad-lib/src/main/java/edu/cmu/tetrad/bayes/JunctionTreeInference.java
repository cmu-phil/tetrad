/// ////////////////////////////////////////////////////////////////////////////
// A fresh, self-contained junction-tree (clique tree) message passing engine
// built on the existing Tetrad BayesIm/BayesPm API.
//
// Name chosen to avoid collisions with Kevin's JunctionTreeAlgorithm.
//
// Design goals (kid gloves):
//  - Use ONLY the BayesIm passed in; never “reinitialize” CPTs.
//  - Build a *tree* even when the moral graph is disconnected by adding a
//    dummy super-root clique (empty separator edges).
//  - Avoid Node identity mismatches by canonicalizing everything to BayesIm's
//    own Node instances.
//  - Ensure every variable is assigned to exactly one clique for CPT-factor
//    placement.
//  - Evidence is handled by zeroing incompatible assignments inside clique
//    potentials (hard evidence).
//  - Two-pass message passing (collect + distribute) with safe normalization.
//
// NOTES:
//  - This is exact for discrete Bayes nets when clique sizes are feasible.
//  - getJointProbability(...) returns P(assignments AND evidence) up to a
//    global scale if you enable message normalization. Marginals are correct.
//  - If you need exact evidence probability Z, we can extend with log-scales.
//
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Robust junction-tree inference via sum-product message passing on a clique tree.
 * <p>
 * This implementation uses GraphTools (moralize, fill-in, getCliques, getSeparators, getCliqueTree)
 * but performs its own potential construction, evidence handling, and message passing.
 */
public final class JunctionTreeInference implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Represents the Bayesian network model with parameters used for inference in the
     * JunctionTreeInference class. This object encapsulates the structure of the network,
     * as well as the conditional probability distributions associated with each node.
     *
     * This variable is critical for constructing the clique tree representation and
     * facilitating various inference operations, such as marginal and conditional
     * probability calculations. It provides the underlying data required for
     * probabilistic reasoning in Bayesian networks.
     *
     * The value of this field is set at the construction of the containing
     * JunctionTreeInference instance and remains immutable thereafter.
     */
    private final BayesIm bayesIm;
    /**
     * Represents the BayesPm (Bayesian parameter model) used within the JunctionTreeInference class
     * to manage the structural aspects of the Bayesian network. This model captures the relationships
     * between variables and their conditional probability distributions, facilitating efficient
     * graphical model operations such as inference and structure queries.
     *
     * This variable is final and initialized during the construction of the JunctionTreeInference
     * instance. It serves as the backbone for defining the network's directed acyclic graph (DAG)
     * and the probabilistic dependencies among the nodes.
     */
    private final BayesPm bayesPm;

    /**
     * Canonical node list from bayesIm.getDag() (do NOT trust external graph Node instances).
     */
    private final Node[] nodes;

    /**
     * Evidence per node index; -1 means none.
     */
    private final int[] hardEvidence;
    /**
     * List of cliques that make up the junction tree structure used for inference.
     * This list includes a super-root clique at index 0 if one is created during
     * the construction of the Junction Tree. Each clique is a part of the
     * graphical representation that facilitates efficient probabilistic reasoning.
     * <p>
     * The cliques are organized based on the factorization of the probability
     * distributions in the Bayesian network, ensuring that the structure adheres
     * to the joint distribution's dependencies and independence properties.
     */
    private final List<Clique> cliques;       // includes super-root at index 0 if created
    /**
     * Represents the adjacency structure of cliques in the junction tree, mapping each {@link Clique}
     * to a list of {@link EdgeCT} objects that serve as separators or message-passing edges.
     * <p>
     * This structure encapsulates the relationships and message flow between cliques in the
     * calibrated junction tree, which is used for probabilistic inference in Bayesian networks.
     * <p>
     * The map keys are cliques, while the associated values represent the edges connecting the clique
     * with its neighbors. Each edge contains information about the separator variables and messages
     * exchanged during inference.
     */
    private final Map<Clique, List<EdgeCT>> adj;  // clique adjacency with separators/messages
    /**
     * A mapping of variable indices to their associated cliques in the junction tree.
     * Each variable is assigned to a specific clique that contains the variable and its parents.
     * This map is used to determine where to read the marginal distribution of a variable.
     */
    private final Map<Integer, Clique> homeCliqueByVar; // where to read a variable marginal
    /**
     * A mapping of variable names to their corresponding indices.
     * This map is used to facilitate quick lookup of a variable's
     * index based on its name.
     * <p>
     * This field is immutable and initialized as a HashMap.
     */
    private final Map<String, Integer> indexByName = new HashMap<>();
    /**
     * Soft evidence: allowed categories per node (null => all allowed).
     */
    private boolean[][] allowed;
    /**
     * A placeholder or dummy "super-root" clique used as a structural aid in certain
     * algorithms. This super-root may be utilized to simplify initialization or
     * ensure the algorithm operates on a unified structure by adding an artificial
     * root to the clique tree.
     * <p>
     * Typically, this clique does not correspond to any actual domain variable but
     * functions as a utility construct in managing the associated clique data structures.
     */
    private Clique superRoot;                 // dummy super-root if needed
    /**
     * Indicates whether the messages in the Junction Tree are up-to-date with the
     * current evidence provided. This flag is used to ensure that the inference process
     * operates on the correct and consistent state of the probabilistic model.
     * <p>
     * If `true`, the Junction Tree is calibrated, meaning all messages are consistent
     * with the current evidence, and queries such as marginals or conditional probabilities
     * can be accurately computed. If `false`, the Junction Tree is not calibrated, and
     * recalibration is required before performing reliable probabilistic inference.
     */
    private boolean calibrated; // whether messages are up-to-date for current evidence

    // =========================
    // Public API
    // =========================

    /**
     * Constructs a JunctionTreeInference object for performing inference on a Bayesian network.
     * This initializes the Bayesian network representation and prepares necessary structures
     * for clique tree construction and inference operations.
     *
     * @param bayesIm The BayesIm (Bayesian network model with parameters) used for inference.
     *                Must not be null.
     * @throws NullPointerException If the provided bayesIm is null.
     */
    public JunctionTreeInference(BayesIm bayesIm) {
        if (bayesIm == null) throw new NullPointerException("bayesIm");
        this.bayesIm = bayesIm;
        this.bayesPm = bayesIm.getBayesPm();

        Node[] tmp = new Node[bayesIm.getNumNodes()];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = bayesIm.getNode(i);
        }
        this.nodes = tmp;

        // Build name->index map (canonical)
        for (int i = 0; i < this.nodes.length; i++) {
            indexByName.put(this.nodes[i].getName(), i);
        }

        this.hardEvidence = new int[this.nodes.length];
        Arrays.fill(this.hardEvidence, -1);

        this.allowed = new boolean[nodes.length][];
        for (int i = 0; i < nodes.length; i++) {
            int k = bayesPm.getNumCategories(nodes[i]);
            allowed[i] = new boolean[k];
            Arrays.fill(allowed[i], true);
        }

        this.cliques = new ArrayList<>();
        this.adj = new HashMap<>();
        this.homeCliqueByVar = new HashMap<>();

        buildCliqueTreeAndPotentials();

        this.calibrated = false; // evidence is empty but we can lazily calibrate on first query
    }

    private static void incrementAssignment(int[] assign, int[] card) {
        for (int i = assign.length - 1; i >= 0; i--) {
            assign[i]++;
            if (assign[i] < card[i]) return;
            assign[i] = 0;
        }
    }

    private static double sum(double[] a) {
        double s = 0.0;
        for (double v : a) s += v;
        return s;
    }

    private static double[] zeros(int k) {
        return new double[k];
    }

    private static void normalizeInPlaceKidGloves(double[] a) {
        double s = 0.0;
        for (double v : a) {
            if (!Double.isFinite(v)) {
                Arrays.fill(a, Double.NaN);
                return;
            }
            s += v;
        }
        if (!Double.isFinite(s) || s <= 0.0) {
            // If impossible evidence, show all zeros rather than NaNs (UI-friendly),
            // but you can flip this if you prefer NaN signaling.
            Arrays.fill(a, 0.0);
            return;
        }
        for (int i = 0; i < a.length; i++) a[i] /= s;
    }

    private static void normalizeMessageKidGloves(double[] msg) {
        if (msg == null || msg.length == 0) return;
        double s = 0.0;
        for (double v : msg) {
            if (!Double.isFinite(v)) {
                Arrays.fill(msg, 0.0);
                return;
            }
            s += v;
        }
        if (!Double.isFinite(s) || s <= 0.0) {
            // If message becomes all zeros (inconsistent evidence), keep zeros.
            Arrays.fill(msg, 0.0);
            return;
        }

        for (int i = 0; i < msg.length; i++) msg[i] /= s;
    }

    private int indexOfByName(Node n) {
        if (n == null) return -1;
        Integer idx = indexByName.get(n.getName());
        return (idx == null) ? -1 : idx;
    }

    /**
     * Clear all evidence.
     */
    public void clearEvidence() {
        Arrays.fill(this.hardEvidence, -1);
        this.calibrated = false;
    }

    /**
     * Set hard evidence X_i = value.
     *
     * @param iNode The node index
     * @param value The evidence value
     */
    public void setEvidence(int iNode, int value) {
        validateNode(iNode);
        int k = bayesPm.getNumCategories(nodes[iNode]);
        if (value < 0 || value >= k) {
            throw new IllegalArgumentException("Value " + value + " out of range 0.." + (k - 1));
        }
        this.hardEvidence[iNode] = value;
        this.calibrated = false;
    }

    /**
     * Get marginal P(X_i).
     *
     * @param iNode The node index
     * @return Marginal probability of X_i
     */
    public double[] getMarginal(int iNode) {
        ensureCalibrated();
        validateNode(iNode);

        Clique c = homeCliqueByVar.get(iNode);
        if (c == null) {
            // Kid gloves fallback: pick any clique containing node
            c = findAnyCliqueContaining(iNode);
            if (c == null) throw new IllegalStateException("No clique contains node " + nodes[iNode].getName());
        }

        int pos = c.indexOfVar(iNode);
        if (pos < 0) throw new IllegalStateException("Internal: home clique missing var.");

        double[] marg = c.marginalOfSingleVar(pos);

        normalizeInPlaceKidGloves(marg);
        return marg;
    }

    // =========================
    // Building the clique tree
    // =========================

    /**
     * Get marginal P(X_i = value).
     *
     * @param iNode The node index
     * @param value The evidence value
     * @return Marginal probability of
     */
    public double getMarginal(int iNode, int value) {
        double[] m = getMarginal(iNode);
        if (value < 0 || value >= m.length) throw new IllegalArgumentException("Bad value index.");
        return m[value];
    }

    /**
     * Reset allowed-categories to tautology (all categories allowed).
     */
    public void clearAllowedCategories() {
        for (int i = 0; i < allowed.length; i++) {
            Arrays.fill(allowed[i], true);
        }
        this.calibrated = false;
    }

    /**
     * Apply allowed/disallowed categories from a Proposition (soft evidence / restrictions).
     *
     * @param p The proposition
     */
    public void setAllowedCategories(Proposition p) {
        if (p == null) throw new NullPointerException("proposition");

        for (int i = 0; i < nodes.length; i++) {
            int k = allowed[i].length;
            for (int c = 0; c < k; c++) {
                allowed[i][c] = p.isAllowed(i, c);
            }
        }
        this.calibrated = false;
    }

    /**
     * Get conditional distribution P(X_i | Parents=parentValues) under current evidence.
     * Parents must be node indices; parentValues aligned to parents[].
     *
     * @param iNode The node index
     * @param parents The parent node indices
     * @param parentValues The parent values
     * @return Conditional distribution
     */
    public double[] getConditional(int iNode, int[] parents, int[] parentValues) {
        ensureCalibrated();
        validateNode(iNode);
        validateNodesAndValues(parents, parentValues);

        // Temporarily add parent assignments as evidence, read marginal, then restore.
        int[] saved = Arrays.copyOf(hardEvidence, hardEvidence.length);

        // Apply (with overwrite) kid gloves: if contradiction, return all zeros.
        for (int j = 0; j < parents.length; j++) {
            int p = parents[j];
            int v = parentValues[j];
            if (hardEvidence[p] >= 0 && hardEvidence[p] != v) {
                return zeros(bayesPm.getNumCategories(nodes[iNode]));
            }
            hardEvidence[p] = v;
        }

        calibrated = false;
        ensureCalibrated();
        double[] m = getMarginal(iNode);

        // Restore
        System.arraycopy(saved, 0, hardEvidence, 0, saved.length);
        calibrated = false; // because we changed evidence back

        return m;
    }

    // =========================
    // Calibration (message passing)
    // =========================

    /**
     * Return P(assignments AND current evidence) *up to a global scale*.
     * <p>
     * Marginals are correct even with message normalization; exact joint/evidence normalization
     * requires tracking scaling constants (can be added if you need it).
     *
     * @param queryNodes The query node indices
     * @param queryValues The query values
     * @return Joint probability of the query assignments given the current evidence.
     */
    public double getJointProbability(int[] queryNodes, int[] queryValues) {
        ensureCalibrated();
        validateNodesAndValues(queryNodes, queryValues);

        // Treat query as additional evidence and return "probability of that evidence" proxy:
        int[] saved = Arrays.copyOf(hardEvidence, hardEvidence.length);

        for (int j = 0; j < queryNodes.length; j++) {
            int q = queryNodes[j];
            int v = queryValues[j];
            if (hardEvidence[q] >= 0 && hardEvidence[q] != v) {
                return 0.0;
            }
            hardEvidence[q] = v;
        }

        calibrated = false;
        ensureCalibrated();

        // With calibrated tree, any clique belief sum is proportional to P(evidence).
        // We use a stable clique: home clique for first query node, else first non-empty clique.
        Clique c = (queryNodes.length > 0) ? homeCliqueByVar.get(queryNodes[0]) : null;
        if (c == null) c = firstRealClique();
        if (c == null) c = superRoot;

        double z = sum(c.belief);
        if (!Double.isFinite(z) || z < 0) z = Double.NaN;

        // Restore
        System.arraycopy(saved, 0, hardEvidence, 0, saved.length);
        calibrated = false;

        return z;
    }

    private void buildCliqueTreeAndPotentials() {
        // Moralize + triangulate using canonical nodes.
        Graph moral = GraphTools.moralize(bayesIm.getDag());

        Graph undirected = canonicalizeUndirected(moral);

        // Triangulate using max cardinality ordering.
        Node[] mco = new Node[nodes.length];
        computeMaximumCardinalityOrdering(undirected, mco);
        GraphTools.fillIn(undirected, mco);

        // Recompute ordering and cliques after fill-in.
        computeMaximumCardinalityOrdering(undirected, mco);
        Map<Node, Set<Node>> cliqueMap = GraphTools.getCliques(mco, undirected);

        // Kid gloves: ensure every variable participates (singleton clique for isolates).
        for (Node v : mco) {
            if (!cliqueMap.containsKey(v) || cliqueMap.get(v) == null || cliqueMap.get(v).isEmpty()) {
                cliqueMap.put(v, new HashSet<>(Collections.singletonList(v)));
            }
        }

        Map<Node, Set<Node>> separators = GraphTools.getSeparators(mco, cliqueMap);
        Map<Node, Node> parentClique = GraphTools.getCliqueTree(mco, cliqueMap, separators);

        // Create Clique objects (one per key in cliqueMap, using the key nodes in mco order).
        Map<Node, Clique> cliqueByKey = new HashMap<>();
        for (Node key : mco) {
            Set<Node> cset = cliqueMap.get(key);
            if (cset == null || cset.isEmpty()) continue;
            Clique c = new Clique(cset);
            cliqueByKey.put(key, c);
            cliques.add(c);
            adj.put(c, new ArrayList<>());
        }

        // Link edges according to parentClique; collect roots.
        List<Clique> roots = new ArrayList<>();
        for (Node key : mco) {
            Clique child = cliqueByKey.get(key);
            if (child == null) continue;

            Node pk = parentClique.get(key);
            if (pk == null) {
                roots.add(child);
                continue;
            }
            Clique parent = cliqueByKey.get(pk);
            if (parent == null) {
                roots.add(child);
                continue;
            }

            Set<Node> sepSet = separators.get(key);
            if (sepSet == null) sepSet = Collections.emptySet();

            EdgeCT e1 = new EdgeCT(child, parent, sepSet);
            EdgeCT e2 = new EdgeCT(parent, child, sepSet);
            e1.twin = e2;
            e2.twin = e1;

            adj.get(child).add(e1);
            adj.get(parent).add(e2);
        }

        // If multiple roots (disconnected moral graph), add a dummy super-root clique.
        if (roots.size() == 1) {
            this.superRoot = roots.get(0);
        } else {
            this.superRoot = new Clique(Collections.emptySet());
            cliques.add(0, superRoot);
            adj.put(superRoot, new ArrayList<>());

            // connect superRoot to each component root with empty separator
            for (Clique r : roots) {
                EdgeCT e1 = new EdgeCT(superRoot, r, Collections.emptySet());
                EdgeCT e2 = new EdgeCT(r, superRoot, Collections.emptySet());
                e1.twin = e2;
                e2.twin = e1;
                adj.get(superRoot).add(e1);
                adj.get(r).add(e2);
            }
        }

        // Assign each BN variable to exactly one clique for CPT multiplication.
        Map<Integer, Clique> assignedToClique = assignVariablesToCliques(mco, cliqueByKey, cliqueMap);


        // Build clique base potentials = product of assigned CPT factors.
        for (Clique c : cliques) c.initializePotential();

        for (Map.Entry<Integer, Clique> ent : assignedToClique.entrySet()) {
            int v = ent.getKey();
            Clique c = ent.getValue();
            if (c == null) continue;
            c.multiplyInCptFactor(v);
        }

        // Build clique base potentials = product of assigned CPT factors.
        for (Clique c : cliques) {
            c.initializePotential();
        }

        for (Map.Entry<Integer, Clique> ent : assignedToClique.entrySet()) {
            int v = ent.getKey();
            Clique c = ent.getValue();
            if (c == null) continue;
            c.multiplyInCptFactor(v);
        }

        double[] check = new double[nodes.length];
        Arrays.fill(check, 0.0);

        // After multiplication, each v's CPT should have introduced some non-1 structure somewhere.
        // We can’t easily prove “structure,” but we can at least ensure we *attempted* to multiply in.
        for (int v = 0; v < nodes.length; v++) {
            Clique c = assignedToClique.get(v);
            if (c == null || c == superRoot) {
                throw new IllegalStateException("JT: variable " + nodes[v].getName() + " assigned to null/superRoot clique");
            }
        }

        // Select a “home clique” per variable for marginal queries.
        // IMPORTANT: use the clique where the CPT factor for v was placed.
        for (int v = 0; v < nodes.length; v++) {
            Clique c = assignedToClique.get(v);
            if (c == null) {
                // Kid gloves fallback (should not happen now that you throw earlier)
                c = findAnyCliqueContaining(v);
                if (c == null) c = superRoot;
            }
            homeCliqueByVar.put(v, c);
        }
    }

    /**
     * Assign each variable v to a clique that contains {v} U Pa(v).
     * We pick the first clique (by MCO key order) that contains all those nodes.
     */
    private Map<Integer, Clique> assignVariablesToCliques(Node[] mco, Map<Node, Clique> cliqueByKey, Map<Node, Set<Node>> cliqueMap) {
        Map<Integer, Clique> out = new HashMap<>();

        for (int v = 0; v < nodes.length; v++) {
            int[] parents = bayesIm.getParents(v);

            Clique chosen = null;

            // Find a clique that contains v and all its parents (by BN index).
            for (Clique c : cliques) {
                if (c == null) continue;
                if (c == superRoot) continue;

                if (c.indexOfVar(v) < 0) continue;

                boolean ok = true;
                for (int p : parents) {
                    if (c.indexOfVar(p) < 0) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    chosen = c;
                    break;
                }
            }

            if (chosen == null) {
                throw new IllegalStateException("JT: No clique contains family {" + nodes[v].getName() + "} ∪ Pa. " + "This indicates clique extraction / canonicalization failure.");
            }

            out.put(v, chosen);
        }

        return out;
    }

    /**
     * Canonicalize an undirected graph using the BayesIm node instances (by name).
     *
     * @param g The graph
     */
    private Graph canonicalizeUndirected(Graph g) {
        EdgeListGraph out = new EdgeListGraph();

        // Add canonical nodes (from BayesIm indices)
        Map<String, Node> canon = new HashMap<>();
        for (Node n : nodes) {
            canon.put(n.getName(), n);
            out.addNode(n);
        }

        // Rebuild edges by name
        for (Edge e : g.getEdges()) {
            Node a = canon.get(e.getNode1().getName());
            Node b = canon.get(e.getNode2().getName());
            if (a == null || b == null || a == b) continue;

            if (out.getEdge(a, b) == null && out.getEdge(b, a) == null) {
                out.addUndirectedEdge(a, b);
            }
        }

        return out;
    }

    private void computeMaximumCardinalityOrdering(Graph graph, Node[] orderingOut) {
        Set<Node> numbered = new HashSet<>();
        for (int i = 0; i < orderingOut.length; i++) {
            Node best = null;
            int bestScore = Integer.MIN_VALUE;

            for (Node v : graph.getNodes()) {
                if (numbered.contains(v)) continue;
                int score = 0;
                for (Node a : graph.getAdjacentNodes(v)) {
                    if (numbered.contains(a)) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = v;
                }
            }

            if (best == null) {
                // Kid gloves: in case graph.getNodes() differs from canonical set
                best = nodes[Math.min(i, nodes.length - 1)];
            }

            orderingOut[i] = best;
            numbered.add(best);
        }
    }

    // =========================
    // Factor ops (clique tables, separator tables)
    // =========================

    private void ensureCalibrated() {
        if (calibrated) return;

        // 1) reset all messages
        for (Clique c : cliques) {
            for (EdgeCT e : adj.getOrDefault(c, List.of())) {
                e.message = null;
            }
        }

        // 2) reset clique beliefs = base potential * evidence indicators
        for (Clique c : cliques) {
            c.resetBeliefWithEvidence();
        }

        // 3) collect + distribute
        collect(superRoot, null);
        distribute(superRoot, null);

        // 4) finalize beliefs by multiplying all incoming messages
        finalizeBeliefs();

        calibrated = true;
    }

    private void collect(Clique current, Clique parent) {
        for (EdgeCT e : adj.getOrDefault(current, List.of())) {
            Clique child = e.to;
            if (child == parent) continue;

            collect(child, current);

            // child -> current lives on e.twin
            e.twin.message = computeMessage(e.twin); // compute message from child to current
        }
    }

    /**
     * Message for a directed edge src -> dst.
     */
    private double[] computeMessage(EdgeCT edge) {
        Clique src = edge.from;
        Clique dst = edge.to;

        // Start from src's base-with-evidence table.
        double[] tmp = Arrays.copyOf(src.baseWithEvidence, src.baseWithEvidence.length);

        // Multiply in all incoming messages to src except the one from dst.
        for (EdgeCT e : adj.getOrDefault(src, List.of())) {
            Clique nb = e.to;
            if (nb == dst) continue;

            // Incoming message is nb -> src, which is stored on e.twin (since e is src -> nb).
            EdgeCT incoming = e.twin;
            double[] inMsg = incoming.message;
            if (inMsg == null) continue;

            // IMPORTANT: use separator metadata from the INCOMING edge (nb -> src),
            // because that metadata's posInSrcClique is positions in nb (its "from").
            // But we are multiplying into src, so we need positions of separator vars in *src*.
            //
            // Luckily: the canonical var order is the same both directions now.
            // So we can multiply using:
            //   - sepVars from incoming (canonical varIdx/card)
            //   - positions in CURRENT clique (src) for those vars.
            //
            // We'll use e.sepVarToPosInSrc, which for edge e (src -> nb) is positions in src.
            multiplyFactorIntoCliqueTable(tmp, src, inMsg, incoming.sepVars, e.sepVarToPosInSrc);
        }

        // Marginalize onto THIS edge's separator (src -> dst), positions are in src.
        double[] msg = marginalize(tmp, src, edge.sepVars);

        normalizeMessageKidGloves(msg);
        return msg;
    }

    // =========================
    // Internal structures
    // =========================

    private void distribute(Clique current, Clique parent) {
        for (EdgeCT e : adj.getOrDefault(current, List.of())) {
            Clique child = e.to;
            if (child == parent) continue;

            // current -> child
            e.message = computeMessage(e);

            distribute(child, current);
        }
    }

    private void finalizeBeliefs() {
        for (Clique c : cliques) {
            double[] b = Arrays.copyOf(c.baseWithEvidence, c.baseWithEvidence.length);

            for (EdgeCT e : adj.getOrDefault(c, List.of())) {
                // Incoming message is neighbor -> c, stored on e.twin
                double[] inMsg = e.twin.message;
                if (inMsg == null) continue;

                // Separator var order is canonical now, so we can use e.twin.sepVars (same varIdx/card)
                // but positions must be in CURRENT clique c, which are e.sepVarToPosInSrc (since e is c -> neighbor).
                multiplyFactorIntoCliqueTable(b, c, inMsg, e.twin.sepVars, e.sepVarToPosInSrc);
            }

            c.belief = b;
        }
    }

    /**
     * Multiply a separator factor (msg) into a clique table (cliqueTable), aligning by separator variable positions.
     *
     * @param cliqueTable    mutable table over clique.vars
     * @param clique         clique descriptor
     * @param msg            factor array over sepVars.varIdx (in order)
     * @param sepVars        separator variable metadata
     * @param sepPosInClique positions of sep vars in clique.vars (same length as sepVars.varIdx)
     */
    private void multiplyFactorIntoCliqueTable(double[] cliqueTable, Clique clique, double[] msg, SepVars sepVars, int[] sepPosInClique) {
        if (sepVars.varIdx.length == 0) {
            // empty separator => msg is scalar of length 1
            if (msg != null && msg.length == 1) {
                double s = msg[0];
                for (int i = 0; i < cliqueTable.length; i++) cliqueTable[i] *= s;
            }
            return;
        }
        if (msg == null) return;

        int n = clique.vars.length;
        int[] assign = new int[n];

        // Iterate assignments; compute msg index by extracting sep assignments.
        for (int idx = 0; idx < cliqueTable.length; idx++) {
            int mIndex = 0;
            for (int k = 0; k < sepVars.varIdx.length; k++) {
                int pos = sepPosInClique[k];
                int card = sepVars.card[k];
                mIndex = mIndex * card + assign[pos];
            }
            cliqueTable[idx] *= msg[mIndex];

            incrementAssignment(assign, clique.card);
        }
    }
    // =========================
    // Helpers / kid gloves
    // =========================

    /**
     * Marginalize a clique table over a separator var-set.
     */
    private double[] marginalize(double[] cliqueTable, Clique clique, SepVars sep) {
        if (sep.varIdx.length == 0) {
            return new double[]{sum(cliqueTable)};
        }

        int msgSize = 1;
        for (int k : sep.card) msgSize *= k;
        double[] out = new double[msgSize];

        int n = clique.vars.length;
        int[] assign = new int[n];

        for (int idx = 0; idx < cliqueTable.length; idx++) {
            int mIndex = 0;
            for (int k = 0; k < sep.varIdx.length; k++) {
                int pos = sep.posInSrcClique[k];
                int card = sep.card[k];
                mIndex = mIndex * card + assign[pos];
            }
            out[mIndex] += cliqueTable[idx];

            incrementAssignment(assign, clique.card);
        }

        return out;
    }

    private Clique firstRealClique() {
        for (Clique c : cliques) {
            if (c != null && c != superRoot) return c;
        }
        return null;
    }

    private Clique findAnyCliqueContaining(int varIdx) {
        for (Clique c : cliques) {
            if (c == null) continue;
            if (c.indexOfVar(varIdx) >= 0) return c;
        }
        return null;
    }

    private void validateNode(int iNode) {
        if (iNode < 0 || iNode >= nodes.length) {
            throw new IllegalArgumentException("Node index out of range: " + iNode);
        }
    }

    private void validateNodesAndValues(int[] ns, int[] vs) {
        if (ns == null || vs == null) throw new IllegalArgumentException("nodes/values cannot be null.");
        if (ns.length != vs.length) throw new IllegalArgumentException("nodes and values length mismatch.");
        for (int i = 0; i < ns.length; i++) {
            validateNode(ns[i]);
            int k = bayesPm.getNumCategories(nodes[ns[i]]);
            if (vs[i] < 0 || vs[i] >= k) {
                throw new IllegalArgumentException("Value " + vs[i] + " out of range for node " + ns[i]);
            }
        }
    }

    /**
     * Serializes the state of the object to the provided {@code ObjectOutputStream}.
     * This method ensures that the default serialization process is performed
     * and logs an error in case of a serialization failure.
     *
     * @param out The {@code ObjectOutputStream} to which the object state is serialized.
     *            Must not be null.
     * @throws IOException If an I/O error occurs during the serialization process.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Deserializes the state of the object from the provided {@code ObjectInputStream}.
     * This method ensures that the default deserialization process is performed and
     * logs an error in case of a deserialization failure.
     *
     * @param in The {@code ObjectInputStream} from which the object state is deserialized.
     *           Must not be null.
     * @throws IOException If an I/O error occurs during the deserialization process.
     * @throws ClassNotFoundException If the class of a serialized object could not be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Separator variable metadata.
     */
    private static final class SepVars implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        final int[] varIdx;         // BN indices in separator (canonical order)
        final int[] card;           // cardinalities in same order
        final int[] posInSrcClique; // positions in SOURCE clique assignment array

        SepVars(int[] varIdx, int[] card, int[] posInSrcClique) {
            this.varIdx = (varIdx != null) ? varIdx : new int[0];
            this.card = (card != null) ? card : new int[0];
            this.posInSrcClique = (posInSrcClique != null) ? posInSrcClique : new int[0];
        }
    }

    // =========================
    // Serialization hooks
    // =========================

    private final class Clique implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        final int[] vars;    // variable indices (into nodes[]) in clique order
        final int[] card;    // cardinalities per var
        final int size;      // table length

        double[] basePotential;   // product of CPT factors (no evidence)
        double[] baseWithEvidence; // basePotential with hard evidence applied
        double[] belief;          // calibrated belief (baseWithEvidence * incoming messages)

        Clique(Set<Node> cliqueNodes) {
            // Convert cliqueNodes -> BayesIm indices by NAME (never by Node identity).
            List<Integer> v = new ArrayList<>();
            for (Node cn : cliqueNodes) {
                int idx = indexOfByName(cn);
                if (idx >= 0) v.add(idx);
            }

            // Remove duplicates and sort by BN index.
            v = v.stream().distinct().sorted().toList();

            this.vars = v.stream().mapToInt(Integer::intValue).toArray();

            this.card = new int[this.vars.length];
            int prod = 1;
            for (int i = 0; i < vars.length; i++) {
                int k = bayesPm.getNumCategories(nodes[vars[i]]);
                card[i] = k;
                prod *= k;
            }
            this.size = prod;

            if (!cliqueNodes.isEmpty() && this.vars.length == 0) {
                TetradLogger.getInstance().log("JT: Clique constructed empty despite non-empty cliqueNodes; Node identity mismatch likely.");
            }
        }

        void initializePotential() {
            this.basePotential = new double[size];
            Arrays.fill(this.basePotential, 1.0);
            this.baseWithEvidence = new double[size];
            this.belief = new double[size];
        }

        int indexOfVar(int varIdx) {
            for (int i = 0; i < vars.length; i++) if (vars[i] == varIdx) return i;
            return -1;
        }

        /**
         * Multiply in CPT factor for BN variable v whose family is contained in this clique.
         */
        void multiplyInCptFactor(int v) {
            int xPos = indexOfVar(v);
            if (xPos < 0) return; // not in clique; kid gloves
            int[] parents = bayesIm.getParents(v);

            // Build map from BN parent indices to positions in clique.
            int[] pPos = new int[parents.length];
            for (int i = 0; i < parents.length; i++) {
                pPos[i] = indexOfVar(parents[i]);
//                if (pPos[i] < 0) {
//                    // Family not fully in clique; skip (kid gloves)
//                    return;
//                }

                if (pPos[i] < 0) {
                    throw new IllegalStateException("JT: Clique does not contain full family for " + nodes[v].getName() + ". Missing parent " + nodes[parents[i]].getName() + ". Clique vars=" + Arrays.toString(this.vars));
                }
            }

            int n = vars.length;
            int[] assign = new int[n];

            for (int idx = 0; idx < basePotential.length; idx++) {
                // Build parentValues in BN parent order:
                int[] parentValues = new int[parents.length];
                for (int i = 0; i < parents.length; i++) {
                    parentValues[i] = assign[pPos[i]];
                }

                // BayesIm row index is based on BN parent order:
                int row = bayesIm.getRowIndex(v, parentValues);
                int xVal = assign[xPos];
                double p = bayesIm.getProbability(v, row, xVal);
                basePotential[idx] *= p;
                incrementAssignment(assign, card);
            }
        }

        void resetBeliefWithEvidence() {
            if (basePotential == null) throw new IllegalStateException("Potential not initialized.");
            System.arraycopy(basePotential, 0, baseWithEvidence, 0, basePotential.length);

            int n = vars.length;
            int[] assign = new int[n];

            for (int idx = 0; idx < baseWithEvidence.length; idx++) {
                boolean ok = true;

                for (int i = 0; i < n; i++) {
                    int v = vars[i];
                    int x = assign[i];

                    // hard evidence
                    int ev = hardEvidence[v];
                    if (ev >= 0 && x != ev) {
                        ok = false;
                        break;
                    }

                    // allowed-categories restriction
                    if (!allowed[v][x]) {
                        ok = false;
                        break;
                    }
                }

                if (!ok) baseWithEvidence[idx] = 0.0;

                incrementAssignment(assign, card);
            }
        }

        /**
         * Marginal of a single clique variable at position pos in this clique.
         */
        double[] marginalOfSingleVar(int pos) {
            int k = card[pos];
            double[] out = new double[k];

            int n = vars.length;
            int[] assign = new int[n];

            double[] src = (belief != null) ? belief : baseWithEvidence;

            for (int idx = 0; idx < src.length; idx++) {
                out[assign[pos]] += src[idx];
                incrementAssignment(assign, card);
            }

            return out;
        }
    }

    /**
     * Clique-tree directed edge with separator metadata and message.
     */
    private final class EdgeCT implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        final Clique from;
        final Clique to;

        /**
         * Separator variables in CANONICAL order (ascending BN index), same both directions.
         */
        final SepVars sepVars;

        /**
         * Positions of sepVars.varIdx inside FROM clique (same length as sepVars.varIdx).
         */
        final int[] sepVarToPosInSrc;

        double[] message;
        EdgeCT twin;

        EdgeCT(Clique from, Clique to, Set<Node> sepNodes) {
            this.from = from;
            this.to = to;

            // Convert separator nodes -> BN indices BY NAME (never by Node identity).
            Set<Integer> sepIdx = new HashSet<>();
            for (Node sn : sepNodes) {
                int idx = indexOfByName(sn);
                if (idx >= 0) sepIdx.add(idx);
            }

            // CANONICAL separator order: ascending BN index.
            int[] varIdx = sepIdx.stream().sorted().mapToInt(Integer::intValue).toArray();

            int[] card = new int[varIdx.length];
            int[] posInFrom = new int[varIdx.length];

            for (int i = 0; i < varIdx.length; i++) {
                int v = varIdx[i];

                // Cardinality from the BN (not from clique arrays—safer).
                card[i] = bayesPm.getNumCategories(nodes[v]);

                int pos = from.indexOfVar(v);
                if (pos < 0) {
                    // This should not happen if sepNodes came from a proper separator set,
                    // but if it does, fail fast so we don't silently scramble messages.
                    throw new IllegalStateException("Separator var " + nodes[v].getName() + " not found in FROM clique.");
                }
                posInFrom[i] = pos;
            }

            this.sepVars = new SepVars(varIdx, card, posInFrom);

            // For THIS directed edge, the separator positions are positions in FROM clique.
            this.sepVarToPosInSrc = posInFrom;
        }
    }
}