package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.VertexRepairSearch.CandidateEdit;
import edu.cmu.tetrad.search.VertexRepairSearch.RepairListener;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the {@link RepairListener#editApplied} defensive-copy contract
 * (fixed 2026-9-8). The listener Javadoc has always promised "a safe copy", but the
 * firing site passed the live working graph, so a listener reading the graph on
 * another thread -- as the GUI's live graph view now does -- could observe it
 * mid-mutation. This test registers a listener that checks, at callback time, that the
 * delivered graph is not the very object the search is editing.
 */
public class TestVertexRepairListenerCopy {

    private static final long SEED = 91824L;
    private static final int NUM_NODES = 10;
    private static final int SAMPLE_SIZE = 1000;
    private static final double ALPHA = 0.01;

    @Test
    public void testEditAppliedReceivesDefensiveCopy() throws Exception {
        RandomUtil.getInstance().setSeed(SEED);

        int numEdges = (int) Math.round(3.0 * NUM_NODES / 2.0);
        Graph trueDag = RandomGraph.randomGraph(NUM_NODES, 0, numEdges,
                100, 100, 100, false);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(SAMPLE_SIZE, false);

        // Corrupt the true DAG so the repair has real work to do (three removals and
        // three acyclicity-preserving additions), then start from its CPDAG.
        Graph corrupt = new EdgeListGraph(trueDag);
        List<Edge> removable = new ArrayList<>(corrupt.getEdges());
        RandomUtil.shuffle(removable);
        for (int i = 0; i < Math.min(3, removable.size()); i++) corrupt.removeEdge(removable.get(i));

        List<Node> nodes = new ArrayList<>(corrupt.getNodes());
        int added = 0;
        for (int attempts = 0; attempts < 500 && added < 3; attempts++) {
            Node x = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));
            Node y = nodes.get(RandomUtil.getInstance().nextInt(nodes.size()));
            if (x == y || corrupt.isAdjacentTo(x, y)) continue;
            if (corrupt.paths().existsDirectedPath(y, x)) continue;
            corrupt.addDirectedEdge(x, y);
            added++;
        }

        Graph start = GraphTransforms.dagToCpdag(corrupt);

        VertexRepairSearch repair = new VertexRepairSearch(
                start, new IndTestFisherZ(data, ALPHA),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        repair.setGraphType(VertexRepairSearch.AdjustmentGraphType.CPDAG);
        repair.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        repair.setSeed(SEED);

        AtomicInteger events = new AtomicInteger();
        AtomicBoolean sharedIdentity = new AtomicBoolean(false);

        repair.addRepairListener(new RepairListener() {
            @Override
            public void editApplied(CandidateEdit edit, Graph currentGraph) {
                events.incrementAndGet();
                // The contract says currentGraph is a safe copy; sharing identity with
                // the live working graph is exactly the bug being pinned down.
                if (currentGraph == repair.getGraph()) sharedIdentity.set(true);
            }
        });

        repair.search();

        assertTrue("The corrupted problem must cause at least one applied edit for this "
                + "test to be meaningful (events=" + events.get() + ")", events.get() > 0);
        assertFalse("editApplied must deliver a defensive copy, never the live working graph",
                sharedIdentity.get());
    }
}
