package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for {@link VertexRepairSearch#setAffectedOnlyInvalidation(boolean)}
 * (introduced 2026-8-13): affected-only invalidation must reach a fixed point of the
 * same quality as full invalidation. The verification sweep guarantees that convergence
 * under the flag means no single candidate anywhere improves, so on a problem where full
 * invalidation repairs to zero Markov violations, affected-only invalidation must too.
 *
 * <p>Runtime note: each case runs a full repair on a small simulated problem (~10-20s
 * total); this is intentional, as the property under test is a whole-search fixed-point
 * property that has no meaningful smaller surrogate.
 */
public class TestVertexRepairInvalidation {

    private static final long SEED = 38492L;
    private static final int NUM_NODES = 8;
    private static final int SAMPLE_SIZE = 1000;
    private static final double ALPHA = 0.01;
    private static final ConditioningSetType TYPE =
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY;

    /**
     * CPDAG repair: both invalidation modes must converge, and the affected-only mode's
     * final violation count must equal the full mode's (zero on this seeded problem).
     */
    @Test
    public void testAffectedOnlyMatchesFullCpdag() throws Exception {
        Problem prob = makeProblem(0);

        Graph full = repair(prob, VertexRepairSearch.AdjustmentGraphType.CPDAG, false);
        Graph affected = repair(prob, VertexRepairSearch.AdjustmentGraphType.CPDAG, true);

        int violFull = countViolations(full, prob.test());
        int violAffected = countViolations(affected, prob.test());

        assertEquals("Full invalidation should repair this seeded problem completely",
                0, violFull);
        assertEquals("Affected-only invalidation must reach the same fixed-point quality",
                violFull, violAffected);
    }

    /**
     * PAG repair: same fixed-point-quality property with latent confounders.
     */
    @Test
    public void testAffectedOnlyMatchesFullPag() throws Exception {
        Problem prob = makeProblem(2);

        Graph full = repair(prob, VertexRepairSearch.AdjustmentGraphType.PAG, false);
        Graph affected = repair(prob, VertexRepairSearch.AdjustmentGraphType.PAG, true);

        int violFull = countViolations(full, prob.test());
        int violAffected = countViolations(affected, prob.test());

        assertTrue("Affected-only invalidation must not converge to a worse fixed point",
                violAffected <= violFull);
    }

    // -------------------------------------------------------------------------

    private record Problem(Graph start, DataSet data, IndependenceTest test) {
    }

    private Problem makeProblem(int numLatents) throws Exception {
        RandomUtil.getInstance().setSeed(SEED);

        int numNodes = NUM_NODES + numLatents;
        int numEdges = (int) Math.round(3.0 * numNodes / 2.0);
        Graph trueDag = RandomGraph.randomGraph(numNodes, numLatents, numEdges,
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
        // among measured variables, then project to a legal CPDAG/PAG start graph.
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

        Graph start = (numLatents > 0)
                ? GraphTransforms.dagToPag(corrupt, false)
                : GraphTransforms.dagToCpdag(corrupt);

        return new Problem(start, data, new IndTestFisherZ(data, ALPHA));
    }

    private Graph repair(Problem prob, VertexRepairSearch.AdjustmentGraphType graphType,
                         boolean affectedOnly) throws Exception {
        VertexRepairSearch repair = new VertexRepairSearch(
                prob.start(), new IndTestFisherZ(prob.data(), ALPHA), TYPE);
        repair.setGraphType(graphType);
        repair.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        repair.setSeed(SEED);
        repair.setAffectedOnlyInvalidation(affectedOnly);
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
