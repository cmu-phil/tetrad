package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link VertexRepairSearch#setTwoPhasePagScoring(boolean)} and
 * the canonicalization memo (both introduced 2026-9-8).
 *
 * <p>Two-phase PAG scoring screens candidates on their implied MAGs and defers full
 * canonicalization, knowledge checking, and exact scoring to the commitment path (poll
 * time, top-K rescoring, apply). The properties tested here are the ones the design
 * guarantees: the search terminates, any changed output is a fully certified legal
 * PAG, and the fixed point is not worse than single-phase scoring's on a seeded
 * problem where the comparison is deterministic. This class lives in the search
 * package (unlike its sibling in edu.cmu.tetrad.test) to read the package-private
 * memo-hit counter.
 *
 * <p>Runtime note: the quality test runs two full PAG repairs on a small simulated
 * problem; this is intentional, as the property is a whole-search fixed-point property
 * with no meaningful smaller surrogate.
 */
public class TestVertexRepairTwoPhase {

    private static final long SEED = 38492L;
    private static final int NUM_MEASURED = 8;
    private static final int NUM_LATENTS = 2;
    private static final int SAMPLE_SIZE = 1000;
    private static final double ALPHA = 0.01;
    private static final ConditioningSetType TYPE =
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY;

    /**
     * PAG repair with two-phase scoring must terminate, must only ever advance the
     * working graph through fully canonicalized candidates (so a changed output is a
     * legal PAG), and on this seeded problem must reach a fixed point at least as good
     * as single-phase scoring's.
     */
    @Test
    public void testTwoPhaseMatchesSinglePhasePagQuality() throws Exception {
        Problem prob = makeProblem();

        Graph single = repair(prob, false);
        Graph two = repair(prob, true);

        if (!two.equals(prob.start())) {
            assertTrue("Two-phase output that differs from the input must be a legal PAG",
                    two.paths().isLegalPag());
        }
        if (!single.equals(prob.start())) {
            assertTrue("Single-phase output that differs from the input must be a legal PAG",
                    single.paths().isLegalPag());
        }

        int violSingle = countViolations(single, prob.test());
        int violTwo = countViolations(two, prob.test());

        assertTrue("Two-phase scoring must not converge to a worse fixed point than "
                        + "single-phase scoring on this seeded problem (single=" + violSingle
                        + ", two=" + violTwo + ")",
                violTwo <= violSingle);
    }

    /**
     * The canonicalization memo must produce hits when the same graph is canonicalized
     * repeatedly: two identical applyEdit calls canonicalize the same base (and, when
     * the edit is a no-effect edit, the same post-edit graph), so at least one lookup
     * must hit. This also pins down that both canonicalizers route through the memo.
     */
    @Test
    public void testCanonicalizationMemoHits() throws Exception {
        Problem prob = makeProblem();

        VertexRepairSearch repair = new VertexRepairSearch(
                prob.start(), new IndTestFisherZ(prob.data(), ALPHA), TYPE);
        repair.setGraphType(VertexRepairSearch.AdjustmentGraphType.PAG);
        repair.setSeed(SEED);

        Edge e = prob.start().getEdges().iterator().next();
        repair.applyEdit(VertexRepairSearch.CandidateEdit.removeEdge(e));
        repair.applyEdit(VertexRepairSearch.CandidateEdit.removeEdge(e));

        assertTrue("Repeated canonicalization of the same graph must hit the memo (hits="
                        + repair.canonicalizationMemoHits + ")",
                repair.canonicalizationMemoHits >= 1);
    }

    // -------------------------------------------------------------------------

    private record Problem(Graph start, DataSet data, IndependenceTest test) {
    }

    private Problem makeProblem() throws Exception {
        RandomUtil.getInstance().setSeed(SEED);

        int numNodes = NUM_MEASURED + NUM_LATENTS;
        int numEdges = (int) Math.round(3.0 * numNodes / 2.0);
        Graph trueDag = RandomGraph.randomGraph(numNodes, NUM_LATENTS, numEdges,
                100, 100, 100, false);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet fullData = im.simulateData(SAMPLE_SIZE, false);
        List<Node> measured = new ArrayList<>();
        for (Node v : fullData.getVariables()) {
            Node inDag = trueDag.getNode(v.getName());
            if (inDag == null || inDag.getNodeType() != NodeType.LATENT) measured.add(v);
        }
        DataSet data = fullData.subsetColumns(measured);

        // Corrupt the true DAG: remove two edges, add two acyclicity-preserving edges
        // among measured variables, then project to a legal PAG start graph.
        Graph corrupt = new EdgeListGraph(trueDag);
        List<Edge> removable = new ArrayList<>(corrupt.getEdges());
        RandomUtil.shuffle(removable);
        for (int i = 0; i < Math.min(2, removable.size()); i++) corrupt.removeEdge(removable.get(i));

        List<Node> measuredNodes = new ArrayList<>();
        for (Node v : corrupt.getNodes()) if (v.getNodeType() != NodeType.LATENT) measuredNodes.add(v);
        int added = 0;
        for (int attempts = 0; attempts < 200 && added < 2; attempts++) {
            Node x = measuredNodes.get(RandomUtil.getInstance().nextInt(measuredNodes.size()));
            Node y = measuredNodes.get(RandomUtil.getInstance().nextInt(measuredNodes.size()));
            if (x == y || corrupt.isAdjacentTo(x, y)) continue;
            if (corrupt.paths().existsDirectedPath(y, x)) continue;
            corrupt.addDirectedEdge(x, y);
            added++;
        }

        Graph start = GraphTransforms.dagToPag(corrupt, false);

        return new Problem(start, data, new IndTestFisherZ(data, ALPHA));
    }

    private Graph repair(Problem prob, boolean twoPhase) throws Exception {
        VertexRepairSearch repair = new VertexRepairSearch(
                prob.start(), new IndTestFisherZ(prob.data(), ALPHA), TYPE);
        repair.setGraphType(VertexRepairSearch.AdjustmentGraphType.PAG);
        repair.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        repair.setSeed(SEED);
        repair.setTwoPhasePagScoring(twoPhase);
        return repair.search();
    }

    private int countViolations(Graph g, IndependenceTest test) throws Exception {
        Set<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, TYPE);
        Set<String> seen = new HashSet<>();
        int violations = 0;
        for (IndependenceFact f : facts) {
            if (f == null || !seen.add(VertexRepairSearch.factKey(f))) continue;
            Node x = resolve(test, f.getX());
            Node y = resolve(test, f.getY());
            if (x == null || y == null) continue;
            Set<Node> z = new LinkedHashSet<>();
            boolean ok = true;
            for (Node w : f.getZ()) {
                Node rw = resolve(test, w);
                if (rw == null) {
                    ok = false;
                    break;
                }
                z.add(rw);
            }
            if (!ok) continue;
            IndependenceResult r = test.checkIndependence(x, y, z);
            if (r != null && !r.isIndependent()) violations++;
        }
        return violations;
    }

    private static Node resolve(IndependenceTest test, Node n) {
        if (n == null || n.getName() == null) return null;
        for (Node v : test.getVariables()) {
            if (n.getName().equals(v.getName())) return v;
        }
        return null;
    }
}
