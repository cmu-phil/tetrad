package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.CorrectnessGates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only oracle diagnostic for FCIT's committed PAG trajectory.
 *
 * <p>Attach {@link #recordCommit(Graph)} to FCIT's existing commit listener.
 * Optionally call {@link #recordInitialPag(Graph)} with a seed PAG obtained
 * independently before the run; FCIT itself is not modified.
 * After FCIT returns, call {@link #analyze(Graph, IndependenceTest, OracleDmagDeletionSearch)}.
 * Every observed PAG snapshot is copied immediately; all expensive oracle tests happen
 * AFTER FCIT finishes, so they cannot influence FCIT's search decisions.
 *
 * <p>This diagnostic deliberately does not infer why FCIT rejected any specific
 * candidate. A FOUND result means an additional oracle-certified deletion is
 * possible from the snapshot's Zhang MAG representative, not that FCIT failed
 * to test that candidate or that the move respects background knowledge.
 * Multiple-edge commits (saturation) are reported, not treated as a certified
 * sequence of one-edge steps. Without an exact oracle, I-map judgments are
 * only relative to the supplied independence test.
 */
public final class FcitOracleDeletionAudit {

    /** Distinguishes a search result from a conversion or configuration limitation. */
    public enum Verdict {
        FOUND, NO_DELETION, LIMIT_REACHED, INVALID_IMAP,
        NO_ZHANG_MAG, UNSUPPORTED_MAG
    }

    /** A change of PAG skeleton between two observed states. */
    public record Transition(int fromState, int toState, List<String> removed,
                             List<String> added) {
        public boolean singleEdgeDeletion() {
            return removed.size() == 1 && added.isEmpty();
        }

        public boolean batchDeletion() {
            return removed.size() > 1 && added.isEmpty();
        }
    }

    /** The oracle's judgment on one *committed* PAG, via a Zhang MAG. */
    public record StateResult(int state, int adjacencyCount, Verdict verdict,
                              Optional<String> oracleRemovablePair,
                              Optional<OracleDmagDeletionSearch.Step> witness,
                              boolean requiresRepresentativeChange,
                              int representativesVisited, int deletionAttempts,
                              int oracleQueries) {
    }

    /** Immutable, offline comparison. State zero is the initial PAG when recorded. */
    public record Report(boolean initialObserved, int commitsObserved,
                         List<Transition> transitions, List<StateResult> states) {
        public StateResult finalState() {
            return states.getLast();
        }

        public long singleEdgeCommits() {
            return transitions.stream().filter(Transition::singleEdgeDeletion).count();
        }

        public long batchCommits() {
            return transitions.stream().filter(Transition::batchDeletion).count();
        }

        /** A compact console summary for small-graph IntelliJ oracle experiments. */
        public String summary() {
            StateResult last = finalState();
            return "FCIT oracle comparison: " + commitsObserved + " commits ("
                    + singleEdgeCommits() + " single, " + batchCommits() + " batch); "
                    + "final Zhang MAG: " + last.verdict()
                    + last.oracleRemovablePair().map(pair -> ", removable " + pair).orElse("")
                    + (last.requiresRepresentativeChange() ? " after representative change" : "")
                    + "; representatives visited=" + last.representativesVisited()
                    + ", oracle queries=" + last.oracleQueries();
        }
    }

    private Graph initial;
    private final List<Graph> commits = new ArrayList<>();

    /** Optional seed PAG, supplied externally before any commit. No oracle queries here. */
    public void recordInitialPag(Graph pag) {
        initial = new EdgeListGraph(Objects.requireNonNull(pag, "pag"));
        commits.clear();
    }

    /** Called after a successful FCIT commit. Records a defensive copy only. */
    public void recordCommit(Graph pag) {
        commits.add(new EdgeListGraph(Objects.requireNonNull(pag, "pag")));
    }

    /**
     * Audits the committed states without mutating FCIT or its graphs.
     * Without an externally supplied initial PAG, the first observed commit is
     * the first state; the first deletion cannot then be counted as a transition.
     *
     * @throws InterruptedException when oracle evaluation is interrupted
     */
    public Report analyze(Graph finalPag, IndependenceTest oracle,
                          OracleDmagDeletionSearch deletionSearch) throws InterruptedException {
        Objects.requireNonNull(finalPag, "finalPag");
        Objects.requireNonNull(oracle, "oracle");
        Objects.requireNonNull(deletionSearch, "deletionSearch");

        List<Graph> snapshots = new ArrayList<>();
        if (initial != null) snapshots.add(new EdgeListGraph(initial));
        for (Graph commit : commits) snapshots.add(new EdgeListGraph(commit));
        Graph last = new EdgeListGraph(finalPag);
        if (snapshots.isEmpty() || !snapshots.getLast().equals(last)) snapshots.add(last);

        List<Transition> transitions = new ArrayList<>();
        for (int i = 1; i < snapshots.size(); i++) {
            Set<String> before = skeleton(snapshots.get(i - 1));
            Set<String> after = skeleton(snapshots.get(i));
            List<String> removed = new ArrayList<>(before);
            removed.removeAll(after);
            List<String> added = new ArrayList<>(after);
            added.removeAll(before);
            transitions.add(new Transition(i - 1, i, List.copyOf(removed), List.copyOf(added)));
        }

        List<StateResult> states = new ArrayList<>();
        for (int i = 0; i < snapshots.size(); i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            states.add(auditState(i, snapshots.get(i), oracle, deletionSearch));
        }
        return new Report(initial != null, commits.size(),
                List.copyOf(transitions), List.copyOf(states));
    }

    private static StateResult auditState(int index, Graph pag, IndependenceTest oracle,
                                          OracleDmagDeletionSearch search) throws InterruptedException {
        Graph mag = CorrectnessGates.tryZhangMag(pag);
        if (mag == null) return unavailable(index, pag.getNumEdges(), Verdict.NO_ZHANG_MAG);
        if (!isDmagType(mag)) return unavailable(index, pag.getNumEdges(), Verdict.UNSUPPORTED_MAG);

        // The search rejects illegal DMAGs and non-I-maps itself. Its bounded
        // NO_DELETION and LIMIT_REACHED results must never be conflated.
        OracleDmagDeletionSearch.Result result = search.findDeletion(mag, oracle);
        Verdict verdict = switch (result.status()) {
            case FOUND -> Verdict.FOUND;
            case NO_DELETION -> Verdict.NO_DELETION;
            case LIMIT_REACHED -> Verdict.LIMIT_REACHED;
            case INVALID_START -> Verdict.INVALID_IMAP;
        };
        Optional<String> pair = result.step().map(step -> edgeKey(step.removed()));
        boolean changed = result.step().isPresent()
                && !result.step().orElseThrow().representative().equals(mag);
        return new StateResult(index, pag.getNumEdges(), verdict, pair, result.step(), changed,
                result.representativesVisited(), result.deletionAttempts(), result.oracleQueries());
    }

    private static StateResult unavailable(int index, int edges, Verdict verdict) {
        return new StateResult(index, edges, verdict, Optional.empty(), Optional.empty(),
                false, 0, 0, 0);
    }

    private static boolean isDmagType(Graph mag) {
        for (Node node : mag.getNodes()) {
            if (node.getNodeType() != NodeType.MEASURED) return false;
        }
        for (Edge edge : mag.getEdges()) {
            if (!Edges.isDirectedEdge(edge) && !Edges.isBidirectedEdge(edge)) return false;
        }
        return true;
    }

    private static Set<String> skeleton(Graph graph) {
        List<String> keys = graph.getEdges().stream().map(FcitOracleDeletionAudit::edgeKey)
                .sorted(Comparator.naturalOrder()).toList();
        return new LinkedHashSet<>(keys);
    }

    private static String edgeKey(Edge edge) {
        String a = edge.getNode1().getName();
        String b = edge.getNode2().getName();
        return a.compareTo(b) <= 0 ? a + "--" + b : b + "--" + a;
    }
}
