package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.FcitOracleDeletionAudit;
import edu.cmu.tetrad.search.OracleDmagDeletionSearch;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Small, deterministic oracle probes for the FCIT / DMAG representative-deletion
 * comparison. No production code is changed by this test.
 *
 * <p>The JUnit tests check only the known seed-witness behavior. To run FCIT and
 * print its trajectory comparison, run {@link #main(String[])} from IntelliJ.
 * Default: {@code four}; choices: {@code four}, {@code five}, {@code cone},
 * {@code all}. Add {@code --no-saturation} to isolate FCIT's one-edge phase.
 *
 * <p>FCIT is started from its COMPLETE_GRAPH option, not from the diagnostic
 * seed DMAG: the public FCIT API does not inject an arbitrary DMAG seed. Thus
 * the seed result and the FCIT terminal result are separate observations.
 */
public class TestFcitOracleConnectedCases {

    private record Case(String name, String truth, String denseSeed) {
        Graph truthGraph() {
            return GraphUtils.convert(truth);
        }

        Graph seedGraph() {
            return GraphUtils.convert(denseSeed);
        }
    }

    private static final Case FOUR = new Case(
            "four",
            "X1-->X2,X2<->X0,X3-->X0",
            "X2-->X1,X2-->X0,X1-->X0,X3-->X0");

    private static final Case FIVE = new Case(
            "five",
            "X2<->X1,X3-->X1,X1-->X0,X2<->X0,X4-->X0",
            "X2-->X1,X3-->X1,X1-->X0,X2-->X0,X3-->X0,X4-->X0");

    private static final Case CONE = new Case(
            "cone",
            "A-->T,T<->B,P1<->A,P1<->B,P2<->A,P2<->B",
            "A-->B,A-->T,T-->B,P1-->A,P1-->B,P2-->A,P2-->B");

    private static final int MAX_REPRESENTATIVES = 2_000;

    /** Fast regression: the example from the first representative-deletion test. */
    @Test
    public void testFourVariableSeedNeedsRepresentativeChange() throws InterruptedException {
        var result = calibration(FOUR);
        assertEquals(OracleDmagDeletionSearch.Status.FOUND, result.status());
        assertFalse(FOUR.seedGraph().equals(result.step().orElseThrow().representative()));
    }

    /** The other two examples are still small, but are not FCIT benchmarks. */
    @Test
    public void testConnectedFiveVariableSeedsHaveOracleWitnesses() throws InterruptedException {
        for (Case c : List.of(FIVE, CONE)) {
            var result = calibration(c);
            assertEquals(c.name(), OracleDmagDeletionSearch.Status.FOUND, result.status());
            assertTrue(c.name(), result.step().orElseThrow().after().getNumEdges()
                    == c.seedGraph().getNumEdges() - 1);
        }
    }

    private static OracleDmagDeletionSearch.Result calibration(Case c) throws InterruptedException {
        return new OracleDmagDeletionSearch(8, MAX_REPRESENTATIVES)
                .findDeletion(c.seedGraph(), new MsepTest(c.truthGraph()));
    }

    /**
     * Manual research runner. Use the IntelliJ main-method action to see progress.
     * Never makes assertions about whether FCIT should or should not stall.
     */
    public static void main(String[] args) throws InterruptedException {
        boolean saturate = Arrays.stream(args).noneMatch("--no-saturation"::equals);
        String selected = Arrays.stream(args)
                .filter(arg -> !arg.startsWith("--"))
                .findFirst().orElse("four");
        List<Case> cases = switch (selected) {
            case "four" -> List.of(FOUR);
            case "five" -> List.of(FIVE);
            case "cone" -> List.of(CONE);
            case "all" -> List.of(FOUR, FIVE, CONE);
            default -> throw new IllegalArgumentException(
                    "Case must be four, five, cone, or all: " + selected);
        };
        for (Case c : cases) run(c, saturate);
    }

    private static void run(Case c, boolean saturate) throws InterruptedException {
        Graph truth = c.truthGraph();
        Graph dense = c.seedGraph();
        System.out.println("\n=== DMAG oracle case: " + c.name() + " ===");
        System.out.println("Truth H: " + truth);
        System.out.println("Diagnostic seed G: " + dense);

        OracleDmagDeletionSearch search = new OracleDmagDeletionSearch(8, MAX_REPRESENTATIVES);
        OracleDmagDeletionSearch.Result baseline = search.findDeletion(dense, new MsepTest(truth));
        System.out.printf("Seed deletion: %s; representatives=%d; attempts=%d; oracle queries=%d%n",
                baseline.status(), baseline.representativesVisited(),
                baseline.deletionAttempts(), baseline.oracleQueries());
        baseline.step().ifPresent(step -> printWitness("Seed", step, truth));
        if (baseline.status() != OracleDmagDeletionSearch.Status.FOUND) {
            throw new IllegalStateException("Case seed has no certified one-edge witness: "
                    + c.name() + " (" + baseline.status() + ")");
        }

        FcitOracleDeletionAudit audit = new FcitOracleDeletionAudit();
        Fcit fcit = new Fcit(new MsepTest(truth), new GraphScore(truth));
        fcit.setStartWith(Fcit.START_WITH.COMPLETE_GRAPH);
        fcit.setRequireImapStates(true);
        fcit.setUseSaturatingStep(saturate);
        fcit.setDepth(-1);
        fcit.setTimeout(-1L);
        fcit.setMaxTestsPerPair(-1L);
        fcit.setCommitListener(audit::recordCommit);

        System.out.println("Running FCIT (COMPLETE_GRAPH start, exact MsepTest,"
                + " requireImapStates=true, saturation=" + saturate + ")...");
        Graph finalPag = fcit.search();
        System.out.println("FCIT terminal PAG: " + finalPag);
        var report = audit.analyze(finalPag, new MsepTest(truth), search);
        System.out.println(report.summary());
        if (!report.initialObserved()) {
            System.out.println("Initial PAG not observed; the first commit's edge count"
                    + " cannot be inferred from this trace.");
        }
        for (var transition : report.transitions()) {
            System.out.printf("Transition %d -> %d: removed=%s added=%s%s%n",
                    transition.fromState(), transition.toState(), transition.removed(),
                    transition.added(), transition.batchDeletion() ? " [BATCH]" : "");
        }
        for (var state : report.states()) {
            System.out.printf("State %d: adj=%d verdict=%s pair=%s reps=%d attempts=%d queries=%d%n",
                    state.state(), state.adjacencyCount(), state.verdict(),
                    state.oracleRemovablePair().orElse("none"),
                    state.representativesVisited(), state.deletionAttempts(),
                    state.oracleQueries());
        }
        var terminal = report.finalState();
        terminal.witness().ifPresent(step -> {
            System.out.println("TERMINAL OPPORTUNITY: certified I-map deletion");
            printWitness("Terminal", step, truth);
            System.out.println("  Representative change needed: "
                    + terminal.requiresRepresentativeChange());
        });
        if (terminal.verdict() == FcitOracleDeletionAudit.Verdict.LIMIT_REACHED) {
            System.out.println("INCONCLUSIVE: representative budget exhausted.");
        } else if (terminal.verdict() == FcitOracleDeletionAudit.Verdict.NO_ZHANG_MAG
                   || terminal.verdict() == FcitOracleDeletionAudit.Verdict.UNSUPPORTED_MAG) {
            System.out.println("INCONCLUSIVE: terminal PAG was not converted to an auditable DMAG.");
        } else if (terminal.verdict() == FcitOracleDeletionAudit.Verdict.INVALID_IMAP) {
            System.out.println("WARNING: terminal graph failed oracle I-map certification.");
        } else if (terminal.verdict() == FcitOracleDeletionAudit.Verdict.NO_DELETION) {
            System.out.println("No extra deletion found within this search's equivalence class.");
        }
        System.out.println("A FOUND result is not evidence of a failure in FCIT's PAG machinery:"
                + " the oracle operates on a selected DMAG representative and ignores knowledge.");
    }

    private static void printWitness(String label, OracleDmagDeletionSearch.Step step, Graph truth) {
        System.out.println(label + " witness representative: " + step.representative());
        System.out.println(label + " witness removed: " + step.removed());
        System.out.println(label + " witness after: " + step.after());
        Edge edge = step.removed();
        List<String> ancestors = new ArrayList<>();
        for (Node node : step.representative().getNodes()) {
            if (node.getName().equals(edge.getNode1().getName())
                    || node.getName().equals(edge.getNode2().getName())) continue;
            if (step.representative().isAncestorOf(node, edge.getNode1())
                    || step.representative().isAncestorOf(node, edge.getNode2())) {
                ancestors.add(node.getName());
            }
        }
        ancestors.sort(String::compareTo);
        System.out.println("  Canonical ancestral conditioning set (before deletion): " + ancestors);
        System.out.println("  All oracle separating sets: " + separatingSets(truth, edge));
    }

    private static List<Set<String>> separatingSets(Graph truth, Edge edge) {
        Node x = truth.getNode(edge.getNode1().getName());
        Node y = truth.getNode(edge.getNode2().getName());
        List<Node> others = new ArrayList<>(truth.getNodes());
        others.remove(x);
        others.remove(y);
        others.sort(Comparator.comparing(Node::getName));
        List<Set<String>> results = new ArrayList<>();
        for (int mask = 0; mask < (1 << others.size()); mask++) {
            Set<Node> z = new LinkedHashSet<>();
            Set<String> labels = new LinkedHashSet<>();
            for (int j = 0; j < others.size(); j++) {
                if ((mask & (1 << j)) != 0) {
                    z.add(others.get(j));
                    labels.add(others.get(j).getName());
                }
            }
            if (truth.paths().isMSeparatedFrom(x, y, z, false)) {
                results.add(Set.copyOf(labels));
            }
        }
        return List.copyOf(results);
    }

}
