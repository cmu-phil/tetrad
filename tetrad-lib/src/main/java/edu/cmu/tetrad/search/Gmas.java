///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.sem.RicfEjml;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GMAS: a latent-variable search that uses NO independence tests at all. A BOSS CPDAG is projected to a PAG and
 * then to its Zhang MAG, and that MAG is improved by steepest-ascent greedy search under the Gaussian MAG BIC
 * (RICF likelihood), moving only among legal MAGs. The final MAG is projected back to a PAG.
 * <p>
 * MOTIVATION. The GFCI/BFCI template starts from a CPDAG and can only DELETE, using tests to propose deletions and
 * to supply the sepsets that drive collider orientation. That template inherits two limits: it cannot restore an
 * adjacency the initializer missed, and a DAG over measured variables cannot express pure confounding, so a
 * latent common cause with no causal edge must be rendered as a directed edge. This search drops the tests and
 * works directly in MAG space, where both limits are liftable: the move set includes ADDITION and includes
 * changing an edge's TYPE (directed to bidirected and back), which is exactly the move BOSS structurally cannot
 * make.
 * <p>
 * MOVES. From the current MAG, for every ordered pair the search considers: deleting an existing edge; reversing a
 * directed edge; converting a directed edge to bidirected and a bidirected edge to directed (either orientation);
 * and, when {@link #setAllowAdditions(boolean)} is on (the default), adding a directed edge (either orientation)
 * or a bidirected edge between a non-adjacent pair. Every candidate is required to be a legal MAG; the highest
 * scoring improving candidate is applied, and the sweep repeats until no move improves the score.
 * <p>
 * SCORE. bic = 2 * logLik - c * k * ln(n), with logLik from RICF and k = p error variances plus one parameter per
 * edge (each directed or bidirected edge carries exactly one). Note that this score is INVARIANT across a Markov
 * equivalence class of MAGs -- equivalent MAGs impose the same constraints with the same parameter count -- so the
 * search cannot distinguish members of a class, and its output should be read as a class, which is what the final
 * projection to a PAG reports.
 * <p>
 * WHAT THIS IS NOT. This is a local search with no consistency guarantee: the move set is not known to connect an
 * arbitrary starting MAG to the true MAG's class, so the search can and will stop at local optima. It is offered
 * as a prototype for measuring whether score-only moves find anything the test-driven template misses, not as a
 * correct latent-variable search. Related published approaches include Claassen and Bucur (2022) and the
 * score-based MAG searches of Triantafillou and Tsamardinos, and of Ali and Richardson.
 *
 * @author josephramsey
 */
public final class Gmas implements IGraphSearch {

    /**
     * The score used by BOSS for the seed CPDAG.
     */
    private final Score score;
    /**
     * The covariance matrix used by RICF; resolved lazily from the score if not set.
     */
    private ICovarianceMatrix covarianceMatrix = null;
    /**
     * Penalty discount c in bic = 2 * logLik - c * k * ln(n) for the MAG score. Independent of the penalty
     * discount inside the BOSS score.
     */
    private double magPenaltyDiscount = 1.0;
    /**
     * Whether addition moves are considered. Off reduces the search to deletions and re-typings.
     */
    private boolean allowAdditions = true;
    /**
     * Maximum number of greedy sweeps, as a guard against pathological cycling.
     */
    private int maxSweeps = 100;
    /**
     * How many moves deep the search looks when the single-move sweep stalls. 1 is plain steepest ascent; 2 adds
     * a paired-move escape from single-move local optima. Higher values are not implemented.
     */
    private int lookaheadDepth = 2;
    /**
     * Cap on the number of FIRST moves expanded per stall, so the quadratic escape cannot run away on a large
     * graph. A cap on first moves rather than on composed pairs keeps the truncation deterministic under parallel
     * scoring: the first moves are expanded in the fixed order the generator produces them.
     */
    private int maxLookaheadFirstMoves = 500;
    /**
     * BOSS knobs.
     */
    private boolean useBes = false;
    private int numStarts = 1;
    private boolean useDataOrder = true;
    private Knowledge knowledge = new Knowledge();
    private boolean excludeSelectionBias = false;
    private boolean verbose = false;

    /**
     * Per-run tallies, reported unconditionally at the end of the search.
     */
    private final AtomicInteger tallyCandidates = new AtomicInteger();
    private final AtomicInteger tallyIllegal = new AtomicInteger();
    private final AtomicInteger tallyUnscorable = new AtomicInteger();
    private int tallyMovesApplied = 0;
    private int tallyPairEscapes = 0;
    private final AtomicInteger tallyPairsExamined = new AtomicInteger();

    /**
     * Constructor.
     *
     * @param score The score for BOSS. For the MAG score to be available this should be a score carrying a
     *              covariance matrix (e.g. SemBicScore), or the covariance should be set explicitly.
     */
    public Gmas(Score score) {
        if (score == null) {
            throw new NullPointerException("Score was null.");
        }

        this.score = score;
    }

    /**
     * Runs the search.
     *
     * @return The estimated PAG.
     * @throws InterruptedException If the thread is interrupted.
     */
    @Override
    public Graph search() throws InterruptedException {
        long t0 = System.currentTimeMillis();

        this.tallyCandidates.set(0);
        this.tallyIllegal.set(0);
        this.tallyUnscorable.set(0);
        this.tallyMovesApplied = 0;
        this.tallyPairEscapes = 0;
        this.tallyPairsExamined.set(0);

        List<Node> nodes = new ArrayList<>(score.getVariables());

        // ---- Seed: BOSS CPDAG -> PAG -> Zhang MAG. This is exactly LV-Heuristic's output, one step further on.
        Boss boss = new Boss(score);
        boss.setUseBes(useBes);
        boss.setNumStarts(numStarts);
        boss.setUseDataOrder(useDataOrder);
        boss.setVerbose(verbose);
        boss.setNumThreads(Runtime.getRuntime().availableProcessors());

        PermutationSearch permutationSearch = new PermutationSearch(boss);
        permutationSearch.setKnowledge(knowledge);

        // NOTE the argument: search(false) returns the DAG, search() the CPDAG. dagToPag requires a DAG -- it
        // projects through MagToPag, which rejects anything that is not a legal MAG, and a CPDAG's undirected
        // component can fail that check ("Not legal mag"). Passing the CPDAG happens to work on sparse problems
        // and throws on denser ones. This is also the idiom FcitSl uses for its own BOSS seed, so the seed PAG
        // here is exactly LV-Heuristic's output, which keeps the two comparable.
        Graph dag = permutationSearch.search(false);
        Graph seedPag = GraphTransforms.dagToPag(dag, excludeSelectionBias);
        Graph mag = GraphTransforms.zhangMagFromPag(seedPag);

        long tSeed = System.currentTimeMillis();

        Double currentBic = magBic(mag);

        if (currentBic == null) {
            TetradLogger.getInstance().log("GMAS: the seed MAG could not be scored (no covariance, a selection "
                                           + "edge, or a singular fit); returning the seed PAG unchanged.");
            return GraphUtils.replaceNodes(seedPag, nodes);
        }

        if (verbose) {
            TetradLogger.getInstance().log("GMAS: seed MAG has " + mag.getNumEdges() + " edge(s), BIC "
                                           + currentBic + ".");
        }

        // ---- Steepest-ascent greedy search in MAG space.
        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            Scored winner = bestOf(candidates(mag, nodes), currentBic);

            Graph bestGraph = winner == null ? null : winner.graph();
            double bestBic = winner == null ? currentBic : winner.bic();
            String bestMove = winner == null ? null : describe(mag, winner.graph());

            if (bestGraph == null && lookaheadDepth >= 2) {
                // Single-move stall. Some corrections need TWO moves that are individually non-improving --
                // adding a confounded pair's edge and then re-orienting around it, for instance -- so the
                // single-move optimum is not the end of the story. Search pairs before giving up.
                Graph pair = bestImprovingPair(mag, nodes, currentBic);

                if (pair != null) {
                    Double pairBic = magBic(pair);

                    if (pairBic != null && pairBic > currentBic) {
                        if (verbose) {
                            TetradLogger.getInstance().log("GMAS sweep " + (sweep + 1)
                                                           + ": two-move escape; BIC " + currentBic + " -> "
                                                           + pairBic + " (+" + (pairBic - currentBic) + ").");
                        }

                        mag = pair;
                        currentBic = pairBic;
                        tallyMovesApplied += 2;
                        tallyPairEscapes++;
                        continue;
                    }
                }
            }

            if (bestGraph == null) {
                if (verbose) {
                    TetradLogger.getInstance().log("GMAS: no improving move at sweep " + (sweep + 1)
                                                   + "; local optimum reached.");
                }
                break;
            }

            if (verbose) {
                TetradLogger.getInstance().log("GMAS sweep " + (sweep + 1) + ": " + bestMove
                                               + "; BIC " + currentBic + " -> " + bestBic
                                               + " (+" + (bestBic - currentBic) + ").");
            }

            mag = bestGraph;
            currentBic = bestBic;
            tallyMovesApplied++;
        }

        Graph pag = new MagToPag(mag).convert(false, excludeSelectionBias);

        long t1 = System.currentTimeMillis();

        TetradLogger.getInstance().log("GMAS summary: seed MAG " + seedPag.getNumEdges()
                                       + " edge(s); moves applied = " + tallyMovesApplied
                                       + "; final MAG " + mag.getNumEdges() + " edge(s), BIC " + currentBic
                                       + "; two-move escapes = " + tallyPairEscapes
                                       + "; candidates examined = " + tallyCandidates.get()
                                       + ", pairs examined = " + tallyPairsExamined.get()
                                       + " (illegal " + tallyIllegal.get() + ", unscorable " + tallyUnscorable.get() + ")"
                                       + "; BOSS " + (tSeed - t0) + " ms, MAG search " + (t1 - tSeed) + " ms.");

        return GraphUtils.replaceNodes(pag, nodes);
    }

    /**
     * One candidate move: the resulting graph and the pair of nodes whose edge the move touched. The pair is what
     * lets the two-move lookahead pair a move only with moves that share a node with it.
     *
     * @param graph The graph resulting from the move.
     * @param a     One endpoint of the touched pair.
     * @param b     The other endpoint of the touched pair.
     */
    private record Move(Graph graph, Node a, Node b) {
        boolean touches(Node x, Node y) {
            return a == x || a == y || b == x || b == y;
        }
    }

    /**
     * The candidate neighbours of the current MAG under the move set: for each adjacent pair, deletion, reversal,
     * and type change; for each non-adjacent pair, addition as directed (either orientation) or bidirected. Each
     * candidate is a fresh graph; legality is checked by the caller.
     */
    private List<Move> candidates(Graph mag, List<Node> nodes) {
        return candidates(mag, nodes, null, null);
    }

    /**
     * As {@link #candidates(Graph, List)}, but when {@code restrictA} is non-null only moves TOUCHING
     * {@code restrictA} or {@code restrictB} are generated.
     * <p>
     * The two-move escape only ever uses touching moves, and building the full neighbourhood and then filtering
     * was the dominant cost of that search: at 20 nodes the full set is about 570 candidates, of which roughly
     * 120 touch a given pair, so more than three quarters of the graph copies were built only to be discarded.
     * Generating the restricted set directly is exact -- the same moves, in the same order.
     */
    private List<Move> candidates(Graph mag, List<Node> nodes, @Nullable Node restrictA, @Nullable Node restrictB) {
        List<Move> out = new ArrayList<>();

        for (Edge e : mag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (restrictA != null && x != restrictA && x != restrictB && y != restrictA && y != restrictB) {
                continue;
            }

            // Delete.
            Graph g = new EdgeListGraph(mag);
            g.removeEdge(e);
            out.add(new Move(g, x, y));

            if (Edges.isDirectedEdge(e)) {
                Node tail = Edges.getDirectedEdgeTail(e);
                Node head = Edges.getDirectedEdgeHead(e);

                // Reverse.
                g = new EdgeListGraph(mag);
                g.removeEdge(e);
                g.addDirectedEdge(head, tail);
                out.add(new Move(g, x, y));

                // Directed -> bidirected: the move BOSS cannot make, and the only way a pure latent common
                // cause can be expressed at all.
                g = new EdgeListGraph(mag);
                g.removeEdge(e);
                g.addBidirectedEdge(tail, head);
                out.add(new Move(g, x, y));
            } else if (Edges.isBidirectedEdge(e)) {
                // Bidirected -> directed, either orientation.
                g = new EdgeListGraph(mag);
                g.removeEdge(e);
                g.addDirectedEdge(x, y);
                out.add(new Move(g, x, y));

                g = new EdgeListGraph(mag);
                g.removeEdge(e);
                g.addDirectedEdge(y, x);
                out.add(new Move(g, x, y));
            }
        }

        if (allowAdditions) {
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node x = nodes.get(i);
                    Node y = nodes.get(j);

                    if (mag.isAdjacentTo(x, y)) {
                        continue;
                    }

                    if (restrictA != null && x != restrictA && x != restrictB && y != restrictA && y != restrictB) {
                        continue;
                    }

                    Graph g = new EdgeListGraph(mag);
                    g.addDirectedEdge(x, y);
                    out.add(new Move(g, x, y));

                    g = new EdgeListGraph(mag);
                    g.addDirectedEdge(y, x);
                    out.add(new Move(g, x, y));

                    g = new EdgeListGraph(mag);
                    g.addBidirectedEdge(x, y);
                    out.add(new Move(g, x, y));
                }
            }
        }

        return out;
    }

    /**
     * A scored candidate. The index is carried so that ties are broken the same way a sequential scan would break
     * them -- by the earliest candidate -- which keeps the parallel search deterministic.
     */
    private record Scored(Graph graph, double bic, int index) {
    }

    /**
     * Scores the given moves in PARALLEL and returns the best one strictly above {@code floor}, or null if none
     * is. Candidates are independent -- each owns its graph, the covariance matrix is read-only, and a fresh RICF
     * instance is used per fit -- so this is a pure speedup with no change in result: ties are broken by the
     * lowest index, which is exactly what a sequential scan keeping the first strict improvement would pick.
     */
    private @Nullable Scored bestOf(List<Move> moves, double floor) {
        return java.util.stream.IntStream.range(0, moves.size())
                .parallel()
                .mapToObj(i -> {
                    Graph g = moves.get(i).graph();

                    tallyCandidates.incrementAndGet();

                    if (!g.paths().isLegalMag()) {
                        tallyIllegal.incrementAndGet();
                        return null;
                    }

                    Double bic = magBic(g);

                    if (bic == null) {
                        tallyUnscorable.incrementAndGet();
                        return null;
                    }

                    return bic > floor ? new Scored(g, bic, i) : null;
                })
                .filter(Objects::nonNull)
                .max(Comparator.comparingDouble(Scored::bic)
                        .thenComparing(Comparator.comparingInt(Scored::index).reversed()))
                .orElse(null);
    }

    /**
     * The best graph reachable by TWO moves from {@code mag} that scores above {@code currentBic}, or null if no
     * such pair is found. Called only when the single-move sweep has stalled.
     * <p>
     * Two restrictions keep this affordable and are the reason it is a heuristic escape rather than an exhaustive
     * depth-2 search. First, the second move must SHARE A NODE with the first: the corrections this is meant to
     * reach are local repairs around one edge -- add the edge a confounded pair needs, then fix the orientation it
     * forces on a neighbour -- and unrelated pairs of moves would each have to improve the score on their own,
     * which the single-move sweep has already ruled out. Second, the number of composed pairs is capped by
     * {@link #setMaxLookaheadPairs(int)}.
     * <p>
     * NOTE that the INTERMEDIATE graph need not be a legal MAG. Requiring it would defeat the purpose: the useful
     * pairs are exactly those whose first move is not on its own a step to a better legal model. Only the composed
     * result is required to be legal and scorable.
     */
    private @Nullable Graph bestImprovingPair(Graph mag, List<Node> nodes, double currentBic)
            throws InterruptedException {
        List<Move> firsts = candidates(mag, nodes);
        int limit = Math.min(firsts.size(), maxLookaheadFirstMoves);

        if (verbose && limit < firsts.size()) {
            TetradLogger.getInstance().log("GMAS: two-move escape considering the first " + limit
                                           + " of " + firsts.size() + " first moves.");
        }

        Graph best = null;
        double bestBic = currentBic;

        for (int i = 0; i < limit; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            Move m1 = firsts.get(i);

            // Only moves touching m1's pair are wanted, so generate only those rather than building the whole
            // neighbourhood and discarding most of it.
            List<Move> seconds = candidates(m1.graph(), nodes, m1.a(), m1.b());
            tallyPairsExamined.addAndGet(seconds.size());

            Scored winner = bestOf(seconds, bestBic);

            if (winner != null) {
                bestBic = winner.bic();
                best = winner.graph();
            }
        }

        return best;
    }

    /**
     * A short description of how {@code after} differs from {@code before}, for logging.
     */
    private String describe(Graph before, Graph after) {
        for (Edge e : after.getEdges()) {
            Edge e0 = before.getEdge(e.getNode1(), e.getNode2());

            if (e0 == null) {
                return "added " + e;
            }

            if (!e0.equals(e)) {
                return "changed " + e0 + " to " + e;
            }
        }

        for (Edge e : before.getEdges()) {
            if (after.getEdge(e.getNode1(), e.getNode2()) == null) {
                return "deleted " + e;
            }
        }

        return "no change";
    }

    /**
     * The Gaussian BIC of a MAG, 2 * logLik - c * k * ln(n), with logLik from RICF and k = p error variances plus
     * one parameter per edge. Returns null rather than throwing when the model cannot be fit.
     */
    private @Nullable Double magBic(Graph mag) {
        ICovarianceMatrix cov = resolveCovariance();

        if (cov == null) {
            return null;
        }

        int numEdges = 0;

        for (Edge e : mag.getEdges()) {
            if (Edges.isDirectedEdge(e) || Edges.isBidirectedEdge(e)) {
                numEdges++;
            } else {
                return null;   // RICF fits directed + bidirected only.
            }
        }

        double logLik;

        try {
            logLik = new RicfEjml().ricf(mag, cov).getLogLik();
        } catch (Exception e) {
            return null;
        }

        if (Double.isNaN(logLik) || Double.isInfinite(logLik)) {
            return null;
        }

        int p = cov.getDimension();
        int k = p + numEdges;
        int n = cov.getSampleSize();

        return 2.0 * logLik - magPenaltyDiscount * k * Math.log(n);
    }

    /**
     * The covariance matrix for RICF, from the explicit setting or from the score.
     */
    private @Nullable ICovarianceMatrix resolveCovariance() {
        if (covarianceMatrix != null) {
            return covarianceMatrix;
        }

        if (score instanceof SemBicScore semBic) {
            try {
                covarianceMatrix = semBic.getCovariances();
            } catch (Exception e) {
                covarianceMatrix = null;
            }
        }

        return covarianceMatrix;
    }

    /**
     * Sets the covariance matrix used by the MAG score explicitly.
     *
     * @param covarianceMatrix The covariance matrix.
     */
    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    /**
     * Sets the penalty discount c for the MAG score. Default 1 (classical BIC). Independent of the penalty
     * discount inside the BOSS score.
     *
     * @param magPenaltyDiscount The penalty discount; must be positive.
     */
    public void setMagPenaltyDiscount(double magPenaltyDiscount) {
        if (magPenaltyDiscount <= 0) {
            throw new IllegalArgumentException("MAG penalty discount must be positive: " + magPenaltyDiscount);
        }

        this.magPenaltyDiscount = magPenaltyDiscount;
    }

    /**
     * Sets whether addition moves are considered. Default true. Additions are what allow the search to restore an
     * adjacency BOSS missed; turning them off reduces the search to deletions and re-typings, which is the
     * comparison that isolates their contribution.
     *
     * @param allowAdditions True to consider additions.
     */
    public void setAllowAdditions(boolean allowAdditions) {
        this.allowAdditions = allowAdditions;
    }

    /**
     * Sets how many moves deep the search looks when the single-move sweep stalls. 1 is plain steepest ascent; 2
     * (the default) adds a paired-move escape, which is what allows corrections whose two halves are individually
     * non-improving -- adding a confounded pair's edge and re-orienting around it, for instance.
     *
     * @param lookaheadDepth 1 or 2.
     */
    public void setLookaheadDepth(int lookaheadDepth) {
        if (lookaheadDepth < 1 || lookaheadDepth > 2) {
            throw new IllegalArgumentException("Lookahead depth must be 1 or 2: " + lookaheadDepth);
        }

        this.lookaheadDepth = lookaheadDepth;
    }

    /**
     * Sets the cap on the number of first moves expanded per stall by the two-move escape.
     *
     * @param maxLookaheadFirstMoves The cap.
     */
    public void setMaxLookaheadFirstMoves(int maxLookaheadFirstMoves) {
        this.maxLookaheadFirstMoves = maxLookaheadFirstMoves;
    }

    /**
     * Superseded by {@link #setMaxLookaheadFirstMoves(int)}, which caps first moves rather than composed pairs so
     * that truncation stays deterministic under parallel scoring. Retained so existing callers still compile; the
     * value is not used.
     *
     * @param maxLookaheadPairs Ignored.
     * @deprecated Use {@link #setMaxLookaheadFirstMoves(int)}.
     */
    @Deprecated
    public void setMaxLookaheadPairs(int maxLookaheadPairs) {
        // No longer used; see setMaxLookaheadFirstMoves.
    }

    /**
     * Sets the maximum number of greedy sweeps.
     *
     * @param maxSweeps The maximum.
     */
    public void setMaxSweeps(int maxSweeps) {
        this.maxSweeps = maxSweeps;
    }

    /**
     * Sets whether BES is used in BOSS.
     *
     * @param useBes True to use BES.
     */
    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }

    /**
     * Sets the number of BOSS starts.
     *
     * @param numStarts The number of starts.
     */
    public void setNumStarts(int numStarts) {
        this.numStarts = numStarts;
    }

    /**
     * Sets whether BOSS uses the data order.
     *
     * @param useDataOrder True to use the data order.
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * Sets the background knowledge. NOTE that knowledge is passed to BOSS for the seed only; the MAG-space moves
     * are knowledge-neutral in this prototype.
     *
     * @param knowledge The knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets whether selection bias is excluded in the projections.
     *
     * @param excludeSelectionBias True to exclude it.
     */
    public void setExcludeSelectionBias(boolean excludeSelectionBias) {
        this.excludeSelectionBias = excludeSelectionBias;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True for verbose output.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
