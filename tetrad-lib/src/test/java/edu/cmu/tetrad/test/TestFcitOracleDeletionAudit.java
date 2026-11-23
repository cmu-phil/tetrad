package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.FcitOracleDeletionAudit;
import edu.cmu.tetrad.search.OracleDmagDeletionSearch;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Offline comparison; no change to FCIT's deletion policy. */
public class TestFcitOracleDeletionAudit {

    @Test
    public void testOracleFindsUnremovedEdgeAtFcitTerminalSnapshot() throws InterruptedException {
        Graph truth = GraphUtils.convert("A-->B,B-->C");
        Graph dense = GraphUtils.convert("A-->B,B-->C,A-->C");
        FcitOracleDeletionAudit audit = new FcitOracleDeletionAudit();
        Graph initialPag = GraphTransforms.dagToPag(dense, false);
        audit.recordInitialPag(initialPag);
        var report = audit.analyze(initialPag, new MsepTest(truth), new OracleDmagDeletionSearch());

        assertTrue(report.initialObserved());
        assertEquals(0, report.commitsObserved());
        assertEquals(FcitOracleDeletionAudit.Verdict.FOUND, report.finalState().verdict());
        assertEquals("A--C", report.finalState().oracleRemovablePair().orElseThrow());
        assertEquals(0, report.transitions().size());
    }

    @Test
    public void testCommitTraceCountsSingleAndBatchDeletions() throws InterruptedException {
        Graph initial = GraphUtils.convert("A-->B,B-->C,A-->C,D-->A,D-->B,D-->C");
        Graph afterSingle = GraphUtils.convert("A-->B,B-->C,D-->A,D-->B,D-->C");
        Graph afterBatch = GraphUtils.convert("A-->B,B-->C,D-->A");
        FcitOracleDeletionAudit audit = new FcitOracleDeletionAudit();
        audit.recordInitialPag(GraphTransforms.dagToPag(initial, false));
        audit.recordCommit(GraphTransforms.dagToPag(afterSingle, false));
        audit.recordCommit(GraphTransforms.dagToPag(afterBatch, false));

        var report = audit.analyze(GraphTransforms.dagToPag(afterBatch, false),
                new MsepTest(afterBatch), new OracleDmagDeletionSearch());
        assertEquals(2, report.commitsObserved());
        assertEquals(2, report.transitions().size());
        assertEquals(1, report.singleEdgeCommits());
        assertEquals(1, report.batchCommits());
        assertEquals(List.of("A--C"), report.transitions().getFirst().removed());
        assertEquals(List.of("B--D", "C--D"), report.transitions().getLast().removed());
        assertEquals(FcitOracleDeletionAudit.Verdict.NO_DELETION, report.finalState().verdict());
    }

    @Test
    public void testInvalidStartNotMisreportedAsOracleOpportunity() throws InterruptedException {
        Graph dense = GraphUtils.convert("A<--B,B-->C");
        Graph truth = GraphUtils.convert("A-->B,C-->B");
        FcitOracleDeletionAudit audit = new FcitOracleDeletionAudit();
        Graph initialPag = GraphTransforms.dagToPag(dense, false);
        audit.recordInitialPag(initialPag);

        var report = audit.analyze(initialPag, new MsepTest(truth), new OracleDmagDeletionSearch());
        assertEquals(FcitOracleDeletionAudit.Verdict.INVALID_IMAP, report.finalState().verdict());
        assertTrue(report.finalState().oracleRemovablePair().isEmpty());
    }

    @Test
    public void testWorksWithOnlyExistingFcitCommitListener() throws InterruptedException {
        Graph truth = GraphUtils.convert("A-->B,B-->C");
        Graph dense = GraphUtils.convert("A-->B,B-->C,A-->C");
        FcitOracleDeletionAudit audit = new FcitOracleDeletionAudit();
        audit.recordCommit(GraphTransforms.dagToPag(dense, false));

        var report = audit.analyze(GraphTransforms.dagToPag(dense, false),
                new MsepTest(truth), new OracleDmagDeletionSearch());
        assertFalse("Initial PAG was not supplied", report.initialObserved());
        assertEquals(1, report.commitsObserved());
        assertEquals(0, report.transitions().size());
        assertEquals(FcitOracleDeletionAudit.Verdict.FOUND, report.finalState().verdict());
    }

    @Test
    public void testDefensiveCopiesInObservers() throws InterruptedException {
        Graph original = GraphUtils.convert("A-->B,B-->C,A-->C");
        Graph truth = GraphUtils.convert("A-->B,B-->C");
        FcitOracleDeletionAudit audit = new FcitOracleDeletionAudit();
        audit.recordInitialPag(GraphTransforms.dagToPag(original, false));
        original.removeEdge(original.getNode("A"), original.getNode("C"));
        var report = audit.analyze(GraphTransforms.dagToPag(original, false),
                new MsepTest(truth), new OracleDmagDeletionSearch());

        assertEquals(1, report.transitions().size());
        assertEquals(List.of("A--C"), report.transitions().getFirst().removed());
        assertEquals(FcitOracleDeletionAudit.Verdict.FOUND, report.states().getFirst().verdict());
        assertEquals(FcitOracleDeletionAudit.Verdict.NO_DELETION, report.finalState().verdict());
    }
}
