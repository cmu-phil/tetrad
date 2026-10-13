///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Projects a total order of the observed variables to a high-scoring ancestral graph consistent with
 * that order: the latent-variable analog of BOSS's grow-shrink projection, and the inner step of the
 * planned LV-BOSS. Directed edges only ever run from earlier to later in the order; each ordered pair
 * may carry a directed edge, a bidirected edge, or no edge; ancestrality (no almost-directed cycles)
 * is enforced on every move, and acyclicity is automatic from the order.
 *
 * <p><b>Coverage of the space.</b> Every MAG's directed part is acyclic, so every MAG is consistent
 * with some total order; the order space with this projection therefore reaches all of MAG space, up
 * to the projection finding the best member of M(order), the ancestral graphs whose directed edges
 * respect the order.</p>
 *
 * <p><b>Procedure.</b> Two phases, both hill climbs on {@link MagRicfBicScorer#score} (exact Gaussian
 * BIC; the scorer's district cache makes repeated evaluations cheap):</p>
 * <ol>
 *   <li><i>Forward pass:</i> for each variable v in order, best-first single moves restricted to v's
 *       incoming pairs (add a directed or bidirected edge from a predecessor, delete, or retype),
 *       until none improves — a typed grow-shrink.</li>
 *   <li><i>Polish:</i> the same best-first single moves over all order-respecting pairs, until none
 *       improves. This repairs choices the forward pass made before later structure existed, since a
 *       bidirected edge couples the fit of both endpoints' districts and per-node choices are not
 *       separable as they are for DAGs.</li>
 * </ol>
 *
 * <p>Deterministic: moves are enumerated in a fixed order (later endpoint, then earlier endpoint,
 * then move type with deletions first and directed before bidirected), and the first move achieving
 * the best strict improvement wins ties.</p>
 *
 * <p><b>Honest status.</b> The hill climbs come with no reachability guarantee: there is no theorem
 * that the best-scoring member of M(order) is found, nor (even at the true order, in the population)
 * that the true MAG's equivalence class is reached — that is exactly what the oracle tests probe
 * empirically. The output is an ancestral graph but is not forced to be maximal; in the population,
 * under faithfulness, a non-maximal output would fit strictly worse than its completion wherever the
 * missing inducing-path edge's parameter is needed, so maximality is expected rather than enforced,
 * and the tests check it.</p>
 *
 * @author josephramsey
 * @see MagRicfBicScorer
 */
@Experimental
public class MagOrderProjection {

    /**
     * The move types, in tie-break order: deletions first (sparser), then directed before bidirected.
     */
    private enum MoveType {DELETE, RETYPE_TO_DIRECTED, ADD_DIRECTED, RETYPE_TO_BIDIRECTED, ADD_BIDIRECTED}

    /**
     * The scorer; also fixes the variable set.
     */
    private final MagRicfBicScorer scorer;

    /**
     * Minimum score improvement for a move to be accepted.
     */
    private double improvementEpsilon = 1e-6;

    /**
     * Cap on accepted moves in the polish phase, as a safety net; a warning is kept if it is hit.
     */
    private int maxPolishMoves = 10000;

    /**
     * Cap on distinct plateau states visited per escape attempt. Together with the depth cap this
     * bounds the cost of a FRUITLESS escape attempt, which is the dominant cost on dense bidirected
     * graphs whose plateaus are large; the escapes observed in the oracle probe all needed depth 2 to
     * 3.
     */
    private int maxPlateauStates = 200;

    /**
     * Cap on the length of a plateau path (chain of neutral retypes) explored per escape attempt.
     */
    private int maxPlateauDepth = 3;

    /**
     * Whether the last call hit the polish cap.
     */
    private boolean polishCapHit = false;

    /**
     * Whether some plateau-escape attempt in the last call hit its state cap.
     */
    private boolean plateauCapHit = false;

    /**
     * Number of successful plateau escapes in the last call.
     */
    private int plateauEscapes = 0;

    /**
     * Number of candidate moves whose scoring threw (singular subproblem) in the last call.
     */
    private int unscorableMoves = 0;

    /**
     * Constructs the projection over the given scorer.
     *
     * @param scorer the MAG BIC scorer; its covariance matrix fixes the variable set.
     */
    public MagOrderProjection(MagRicfBicScorer scorer) {
        this.scorer = Objects.requireNonNull(scorer, "Scorer not specified.");
    }

    /**
     * Projects the order to a high-scoring ancestral graph consistent with it.
     *
     * @param order a total order of the scorer's variables (matched by name; must be a permutation of
     *              all of them).
     * @return the projected graph, over the given order's nodes.
     */
    public Graph project(List<Node> order) {
        List<Node> nodes = checkOrder(order);
        int p = nodes.size();

        this.polishCapHit = false;
        this.plateauCapHit = false;
        this.plateauEscapes = 0;
        this.unscorableMoves = 0;

        Graph g = new EdgeListGraph(nodes);
        double score = this.scorer.score(g);

        // Phase 0: forward grow-shrink with DIRECTED moves only -- BOSS's projection under the order.
        // On DAG-generated data at a true order this recovers the true DAG, and the later phases then
        // find no improving bidirected move; without this phase, the typed grow drifts into
        // over-complete bidirected representations that fit exactly with extra edges and sit on
        // plateaus too long to walk back.
        for (int t = 1; t < p; t++) {
            List<int[]> pairs = new ArrayList<>();
            for (int s = 0; s < t; s++) pairs.add(new int[]{s, t});
            score = bestMoveLoop(g, nodes, pairs, score, Integer.MAX_VALUE, true);
        }

        // Phase 1: forward typed grow-shrink, one node at a time over its predecessors.
        for (int t = 1; t < p; t++) {
            List<int[]> pairs = new ArrayList<>();
            for (int s = 0; s < t; s++) pairs.add(new int[]{s, t});
            score = bestMoveLoop(g, nodes, pairs, score, Integer.MAX_VALUE, false);
        }

        // Phase 2: polish over all order-respecting pairs, with plateau escapes between hill climbs.
        List<int[]> allPairs = new ArrayList<>();
        for (int t = 1; t < p; t++) {
            for (int s = 0; s < t; s++) allPairs.add(new int[]{s, t});
        }

        while (true) {
            score = bestMoveLoop(g, nodes, allPairs, score, this.maxPolishMoves, false);
            Double escaped = plateauEscape(g, nodes, allPairs, score);
            if (escaped == null) break;
            score = escaped;
        }

        return g;
    }

    /**
     * Escapes a strict local optimum by walking the score plateau. A single retype (directed to
     * bidirected or back) preserves the edge count, so a score-neutral retype moves to an equally
     * scoring representation — when it preserves the Markov equivalence class, exactly so, by score
     * equivalence. This breadth-first search visits plateau states reachable by chains of neutral
     * retypes (up to {@code maxPlateauStates}) and, at each, scans for a strictly improving single
     * move; the first one found is taken, and the hill climb resumes from there. Deterministic: states
     * are expanded in insertion order and moves enumerated in the fixed order.
     *
     * @return the new score if an escape was found and applied (the graph is mutated to the improved
     * state), or null if the plateau offers no exit within the budget.
     */
    private Double plateauEscape(Graph g, List<Node> nodes, List<int[]> pairs, double score) {
        Set<String> visited = new HashSet<>();
        visited.add(stateKey(g, nodes));

        List<List<MoveRecord>> queue = new ArrayList<>();
        queue.add(new ArrayList<>()); // the start state, an empty path

        for (int qi = 0; qi < queue.size() && visited.size() <= this.maxPlateauStates; qi++) {
            List<MoveRecord> path = queue.get(qi);

            // Replay the path from the start state.
            for (MoveRecord r : path) {
                Node u = nodes.get(r.s), v = nodes.get(r.t);
                applyMove(g, u, v, g.getEdge(u, v), r.type);
            }

            boolean escaped = false;

            try {
                // From this plateau state: does any strictly improving move exist? (The start state is
                // a local optimum, so for it this scan finds nothing; the cache keeps it cheap.)
                if (!path.isEmpty()) {
                    double improved = tryBestMove(g, nodes, pairs, score);
                    if (!Double.isNaN(improved)) {
                        this.plateauEscapes++;
                        escaped = true; // g holds the improved state; keep the replayed path.
                        return improved;
                    }
                }

                // Expand: all legal score-neutral retypes, up to the depth cap.
                if (path.size() >= this.maxPlateauDepth) continue;

                for (int[] pair : pairs) {
                    Node u = nodes.get(pair[0]);
                    Node v = nodes.get(pair[1]);
                    Edge current = g.getEdge(u, v);
                    if (current == null) continue;

                    MoveType retype = Edges.isDirectedEdge(current) ? MoveType.RETYPE_TO_BIDIRECTED : MoveType.RETYPE_TO_DIRECTED;
                    if (!legal(g, u, v, retype)) continue;

                    applyMove(g, u, v, current, retype);
                    String key = stateKey(g, nodes);

                    if (!visited.contains(key)) {
                        double neutralScore;
                        try {
                            neutralScore = this.scorer.score(g);
                        } catch (RuntimeException e) {
                            this.unscorableMoves++;
                            neutralScore = Double.NaN;
                        }

                        if (Math.abs(neutralScore - score) <= this.improvementEpsilon) {
                            visited.add(key);
                            List<MoveRecord> next = new ArrayList<>(path);
                            next.add(new MoveRecord(pair[0], pair[1], retype));
                            queue.add(next);
                        }
                    }

                    undoMove(g, u, v, current, retype);
                }
            } finally {
                if (!escaped) {
                    // Un-replay the path, restoring the start state.
                    for (int i = path.size() - 1; i >= 0; i--) {
                        MoveRecord r = path.get(i);
                        Node u = nodes.get(r.s), v = nodes.get(r.t);
                        Edge installed = g.getEdge(u, v);
                        g.removeEdge(installed);
                        g.addEdge(r.type == MoveType.RETYPE_TO_BIDIRECTED
                                ? Edges.directedEdge(u, v) : Edges.bidirectedEdge(u, v));
                    }
                }
            }
        }

        if (visited.size() > this.maxPlateauStates) this.plateauCapHit = true;
        return null;
    }

    /**
     * Scans all moves once; if a strictly improving one exists, applies the best and returns the new
     * score, else returns NaN and leaves the graph unchanged.
     */
    private double tryBestMove(Graph g, List<Node> nodes, List<int[]> pairs, double score) {
        double bestDelta = this.improvementEpsilon;
        int[] bestPair = null;
        MoveType bestType = null;
        double bestScore = Double.NaN;

        for (int[] pair : pairs) {
            Node u = nodes.get(pair[0]);
            Node v = nodes.get(pair[1]);
            Edge current = g.getEdge(u, v);

            for (MoveType type : MoveType.values()) {
                if (!applicable(current, type)) continue;
                if (!legal(g, u, v, type)) continue;

                applyMove(g, u, v, current, type);
                double candidateScore;
                try {
                    candidateScore = this.scorer.score(g);
                } catch (RuntimeException e) {
                    this.unscorableMoves++;
                    candidateScore = Double.NEGATIVE_INFINITY;
                } finally {
                    undoMove(g, u, v, current, type);
                }

                double delta = candidateScore - score;
                if (delta > bestDelta) {
                    bestDelta = delta;
                    bestPair = pair;
                    bestType = type;
                    bestScore = candidateScore;
                }
            }
        }

        if (bestPair == null) return Double.NaN;

        Node u = nodes.get(bestPair[0]);
        Node v = nodes.get(bestPair[1]);
        applyMove(g, u, v, g.getEdge(u, v), bestType);
        return bestScore;
    }

    /**
     * A canonical string key for the current edge configuration.
     */
    private static String stateKey(Graph g, List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (int t = 1; t < nodes.size(); t++) {
            for (int s = 0; s < t; s++) {
                Edge e = g.getEdge(nodes.get(s), nodes.get(t));
                sb.append(e == null ? '0' : Edges.isDirectedEdge(e) ? 'd' : 'b');
            }
        }
        return sb.toString();
    }

    /**
     * One retype along a plateau path.
     */
    private record MoveRecord(int s, int t, MoveType type) {
    }

    /**
     * Repeatedly applies the best strictly improving single move on the given pairs until none
     * improves or the move cap is reached. Mutates the graph; returns the final score.
     */
    private double bestMoveLoop(Graph g, List<Node> nodes, List<int[]> pairs, double score, int maxMoves,
                                boolean directedOnly) {
        int moves = 0;

        while (moves < maxMoves) {
            double bestDelta = this.improvementEpsilon;
            int[] bestPair = null;
            MoveType bestType = null;
            double bestScore = Double.NaN;

            for (int[] pair : pairs) {
                Node u = nodes.get(pair[0]);
                Node v = nodes.get(pair[1]);
                Edge current = g.getEdge(u, v);

                for (MoveType type : MoveType.values()) {
                    if (directedOnly && (type == MoveType.ADD_BIDIRECTED || type == MoveType.RETYPE_TO_BIDIRECTED
                                         || type == MoveType.RETYPE_TO_DIRECTED)) continue;
                    if (!applicable(current, type)) continue;
                    if (!legal(g, u, v, type)) continue;

                    applyMove(g, u, v, current, type);
                    double candidateScore;
                    try {
                        candidateScore = this.scorer.score(g);
                    } catch (RuntimeException e) {
                        this.unscorableMoves++;
                        candidateScore = Double.NEGATIVE_INFINITY;
                    } finally {
                        undoMove(g, u, v, current, type);
                    }

                    double delta = candidateScore - score;

                    // Strict > keeps the first-found move among ties, so the pass is deterministic.
                    if (delta > bestDelta) {
                        bestDelta = delta;
                        bestPair = pair;
                        bestType = type;
                        bestScore = candidateScore;
                    }
                }
            }

            if (bestPair == null) break;

            Node u = nodes.get(bestPair[0]);
            Node v = nodes.get(bestPair[1]);
            applyMove(g, u, v, g.getEdge(u, v), bestType);
            score = bestScore;
            moves++;
        }

        if (moves >= maxMoves && maxMoves == this.maxPolishMoves) {
            this.polishCapHit = true;
        }

        return score;
    }

    /**
     * Whether the move type applies to the pair's current edge state.
     */
    private static boolean applicable(Edge current, MoveType type) {
        if (current == null) {
            return type == MoveType.ADD_DIRECTED || type == MoveType.ADD_BIDIRECTED;
        } else if (Edges.isDirectedEdge(current)) {
            return type == MoveType.DELETE || type == MoveType.RETYPE_TO_BIDIRECTED;
        } else {
            return type == MoveType.DELETE || type == MoveType.RETYPE_TO_DIRECTED;
        }
    }

    /**
     * Whether the move keeps the graph ancestral. Acyclicity of the directed part is automatic, since
     * directed edges always run forward in the order; what must be policed is the almost-directed
     * cycle: no bidirected pair may be ancestrally related.
     *
     * <ul>
     *   <li>Introducing u &lt;-&gt; v: illegal iff u is an ancestor of v without that pair's edge (v
     *       cannot be an ancestor of u, being later in the order).</li>
     *   <li>Introducing u -&gt; v: the new ancestries are ancestors-of-u to descendants-of-v; illegal
     *       iff some existing bidirected pair becomes ancestrally related.</li>
     *   <li>Deletions are always legal.</li>
     * </ul>
     */
    private static boolean legal(Graph g, Node u, Node v, MoveType type) {
        Edge current = g.getEdge(u, v);

        switch (type) {
            case DELETE:
                return true;

            case ADD_BIDIRECTED:
                return !g.paths().isAncestorOf(u, v);

            case RETYPE_TO_BIDIRECTED: {
                // Ancestry of u over v must not survive the removal of the u -> v edge itself.
                g.removeEdge(current);
                boolean anc = g.paths().isAncestorOf(u, v);
                g.addEdge(current);
                return !anc;
            }

            case ADD_DIRECTED:
            case RETYPE_TO_DIRECTED: {
                // Tentatively install the directed edge and check every bidirected pair.
                if (current != null) g.removeEdge(current);
                Edge directed = Edges.directedEdge(u, v);
                g.addEdge(directed);

                boolean ok = true;
                outer:
                for (Edge e : g.getEdges()) {
                    if (!Edges.isBidirectedEdge(e)) continue;
                    Node x = e.getNode1(), y = e.getNode2();
                    if (g.paths().isAncestorOf(x, y) || g.paths().isAncestorOf(y, x)) {
                        ok = false;
                        break outer;
                    }
                }

                g.removeEdge(directed);
                if (current != null) g.addEdge(current);
                return ok;
            }

            default:
                throw new IllegalStateException("Unknown move type: " + type);
        }
    }

    private static void applyMove(Graph g, Node u, Node v, Edge current, MoveType type) {
        if (current != null) g.removeEdge(current);

        switch (type) {
            case DELETE:
                break;
            case ADD_DIRECTED:
            case RETYPE_TO_DIRECTED:
                g.addEdge(Edges.directedEdge(u, v));
                break;
            case ADD_BIDIRECTED:
            case RETYPE_TO_BIDIRECTED:
                g.addEdge(Edges.bidirectedEdge(u, v));
                break;
            default:
                throw new IllegalStateException("Unknown move type: " + type);
        }
    }

    private static void undoMove(Graph g, Node u, Node v, Edge current, MoveType type) {
        Edge installed = g.getEdge(u, v);
        if (installed != null) g.removeEdge(installed);
        if (current != null) g.addEdge(current);
    }

    /**
     * Validates the order and maps it onto the scorer's variables by name.
     */
    private List<Node> checkOrder(List<Node> order) {
        if (order == null) throw new NullPointerException("Order not specified.");

        List<Node> scorerVars = new ArrayList<>();
        // The scorer scores by name against its covariance; use the order's own nodes, but insist
        // they are a permutation of the covariance variables by name.
        List<String> covNames = new ArrayList<>();
        for (Node v : order) {
            if (v == null) throw new IllegalArgumentException("Null node in the order.");
            covNames.add(v.getName());
        }

        if (covNames.size() != covNames.stream().distinct().count()) {
            throw new IllegalArgumentException("The order repeats a variable.");
        }

        List<String> expected = new ArrayList<>();
        for (Node v : this.scorer.getVariables()) expected.add(v.getName());

        if (!(covNames.containsAll(expected) && expected.containsAll(covNames))) {
            throw new IllegalArgumentException("The order must be a permutation of the scorer's variables; got "
                                               + covNames + " expecting a permutation of " + expected + ".");
        }

        for (Node v : order) scorerVars.add(v);
        return scorerVars;
    }

    /**
     * Sets the minimum score improvement for a move to be accepted.
     *
     * @param improvementEpsilon a positive threshold on score deltas.
     */
    public void setImprovementEpsilon(double improvementEpsilon) {
        if (!(improvementEpsilon > 0.0)) throw new IllegalArgumentException("Epsilon must be positive.");
        this.improvementEpsilon = improvementEpsilon;
    }

    /**
     * Sets the safety cap on accepted moves in the polish phase.
     *
     * @param maxPolishMoves a positive cap.
     */
    public void setMaxPolishMoves(int maxPolishMoves) {
        if (maxPolishMoves < 1) throw new IllegalArgumentException("Cap must be at least 1.");
        this.maxPolishMoves = maxPolishMoves;
    }

    /**
     * Sets the cap on distinct plateau states visited per escape attempt.
     *
     * @param maxPlateauStates a positive cap.
     */
    public void setMaxPlateauStates(int maxPlateauStates) {
        if (maxPlateauStates < 1) throw new IllegalArgumentException("Cap must be at least 1.");
        this.maxPlateauStates = maxPlateauStates;
    }

    /**
     * Sets the cap on the length of a plateau path (chain of neutral retypes) per escape attempt.
     *
     * @param maxPlateauDepth a positive cap.
     */
    public void setMaxPlateauDepth(int maxPlateauDepth) {
        if (maxPlateauDepth < 1) throw new IllegalArgumentException("Cap must be at least 1.");
        this.maxPlateauDepth = maxPlateauDepth;
    }

    /**
     * Whether the last projection hit the polish-phase move cap (it should not; if it did, the result
     * may not be at a local optimum).
     *
     * @return true iff the cap was hit.
     */
    public boolean isPolishCapHit() {
        return this.polishCapHit;
    }

    /**
     * Whether some plateau-escape attempt in the last projection hit its state cap (if so, an exit
     * from the plateau may have been missed).
     *
     * @return true iff the cap was hit.
     */
    public boolean isPlateauCapHit() {
        return this.plateauCapHit;
    }

    /**
     * The number of successful plateau escapes in the last projection.
     *
     * @return the count.
     */
    public int getPlateauEscapes() {
        return this.plateauEscapes;
    }

    /**
     * The number of candidate moves in the last projection whose scoring threw (singular subproblem)
     * and were treated as non-improving.
     *
     * @return the count.
     */
    public int getUnscorableMoves() {
        return this.unscorableMoves;
    }
}
