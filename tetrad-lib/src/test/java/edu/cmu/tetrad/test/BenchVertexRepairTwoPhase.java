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

import java.util.*;

/**
 * Hand-run benchmark for two-phase PAG scoring in {@link VertexRepairSearch}
 * (2026-9-8). Compares wall time and fixed-point quality for single-phase versus
 * two-phase scoring on seeded PAG repair problems, with a CPDAG timing row as a
 * canonicalization-memo sanity check (two-phase scoring does not apply to CPDAGs, so
 * any CPDAG delta is the memo alone).
 *
 * <p>Run from IntelliJ as a plain main(). Each cell runs {@code RUNS} repairs per
 * mode with fresh seeds; problems are corrupted by removing and adding
 * {@code CORRUPTIONS} edges each. Sizes use the M+L:avgDeg:N notation, measured plus
 * latents, average degree, sample size.
 */
public final class BenchVertexRepairTwoPhase {

    private static final int RUNS = 3;
    private static final int SAMPLE_SIZE = 1000;
    private static final double ALPHA = 0.01;
    private static final int CORRUPTIONS = 3;
    private static final ConditioningSetType TYPE =
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY;

    public static void main(String[] args) throws Exception {
        System.out.println("Two-phase PAG scoring benchmark, type = " + TYPE
                + ", alpha = " + ALPHA + ", runs per cell = " + RUNS);
        System.out.println();

        // PAG cells: measured+latents : average degree : N
        pagCell(15, 3, 4);
        pagCell(20, 4, 4);

        // CPDAG cell: memo-only sanity (two-phase does not apply to CPDAGs).
        cpdagCell(20, 4);
    }

    private static void pagCell(int measured, int latents, int avgDegree) throws Exception {
        System.out.printf("PAG %d+%d:%d:%d%n", measured, latents, avgDegree, SAMPLE_SIZE);
        long[] tSingle = new long[RUNS], tTwo = new long[RUNS];
        int[] vSingle = new int[RUNS], vTwo = new int[RUNS];

        for (int r = 0; r < RUNS; r++) {
            long seed = 1000L * (r + 1) + 7L * measured;
            Problem prob = makeProblem(measured, latents, avgDegree, seed);

            long t0 = System.currentTimeMillis();
            Graph gSingle = repair(prob, VertexRepairSearch.AdjustmentGraphType.PAG, false, seed);
            tSingle[r] = System.currentTimeMillis() - t0;
            vSingle[r] = countViolations(gSingle, prob.test());

            t0 = System.currentTimeMillis();
            Graph gTwo = repair(prob, VertexRepairSearch.AdjustmentGraphType.PAG, true, seed);
            tTwo[r] = System.currentTimeMillis() - t0;
            vTwo[r] = countViolations(gTwo, prob.test());

            System.out.printf("  run %d: single %6d ms / %d viol   two-phase %6d ms / %d viol%n",
                    r + 1, tSingle[r], vSingle[r], tTwo[r], vTwo[r]);
        }
        summarize(tSingle, tTwo);
        System.out.println();
    }

    private static void cpdagCell(int measured, int avgDegree) throws Exception {
        System.out.printf("CPDAG %d:%d:%d (memo-only comparison: flag has no effect here)%n",
                measured, avgDegree, SAMPLE_SIZE);
        long[] t = new long[RUNS];
        for (int r = 0; r < RUNS; r++) {
            long seed = 2000L * (r + 1);
            Problem prob = makeProblem(measured, 0, avgDegree, seed);
            long t0 = System.currentTimeMillis();
            Graph g = repair(prob, VertexRepairSearch.AdjustmentGraphType.CPDAG, true, seed);
            t[r] = System.currentTimeMillis() - t0;
            System.out.printf("  run %d: %6d ms / %d viol%n", r + 1, t[r],
                    countViolations(g, prob.test()));
        }
        System.out.println();
    }

    private static void summarize(long[] tSingle, long[] tTwo) {
        double s = Arrays.stream(tSingle).average().orElse(Double.NaN);
        double t = Arrays.stream(tTwo).average().orElse(Double.NaN);
        System.out.printf("  mean: single %.0f ms, two-phase %.0f ms, speedup %.2fx%n",
                s, t, s / t);
    }

    // -------------------------------------------------------------------------

    private record Problem(Graph start, DataSet data, IndependenceTest test) {
    }

    private static Problem makeProblem(int measured, int latents, int avgDegree, long seed)
            throws Exception {
        RandomUtil.getInstance().setSeed(seed);

        int numNodes = measured + latents;
        int numEdges = (int) Math.round(avgDegree * numNodes / 2.0);
        Graph trueDag = RandomGraph.randomGraph(numNodes, latents, numEdges,
                100, 100, 100, false);

        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet fullData = im.simulateData(SAMPLE_SIZE, false);
        List<Node> measuredVars = new ArrayList<>();
        for (Node v : fullData.getVariables()) {
            Node inDag = trueDag.getNode(v.getName());
            if (inDag == null || inDag.getNodeType() != NodeType.LATENT) measuredVars.add(v);
        }
        DataSet data = fullData.subsetColumns(measuredVars);

        Graph corrupt = new EdgeListGraph(trueDag);
        List<Edge> removable = new ArrayList<>(corrupt.getEdges());
        RandomUtil.shuffle(removable);
        for (int i = 0; i < Math.min(CORRUPTIONS, removable.size()); i++) {
            corrupt.removeEdge(removable.get(i));
        }

        List<Node> measuredNodes = new ArrayList<>();
        for (Node v : corrupt.getNodes()) if (v.getNodeType() != NodeType.LATENT) measuredNodes.add(v);
        int added = 0;
        for (int attempts = 0; attempts < 500 && added < CORRUPTIONS; attempts++) {
            Node x = measuredNodes.get(RandomUtil.getInstance().nextInt(measuredNodes.size()));
            Node y = measuredNodes.get(RandomUtil.getInstance().nextInt(measuredNodes.size()));
            if (x == y || corrupt.isAdjacentTo(x, y)) continue;
            if (corrupt.paths().existsDirectedPath(y, x)) continue;
            corrupt.addDirectedEdge(x, y);
            added++;
        }

        Graph start = (latents > 0)
                ? GraphTransforms.dagToPag(corrupt, false)
                : GraphTransforms.dagToCpdag(corrupt);

        return new Problem(start, data, new IndTestFisherZ(data, ALPHA));
    }

    private static Graph repair(Problem prob, VertexRepairSearch.AdjustmentGraphType graphType,
                                boolean twoPhase, long seed) throws Exception {
        VertexRepairSearch repair = new VertexRepairSearch(
                prob.start(), new IndTestFisherZ(prob.data(), ALPHA), TYPE);
        repair.setGraphType(graphType);
        repair.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        repair.setSeed(seed);
        repair.setTwoPhasePagScoring(twoPhase);
        return repair.search();
    }

    private static int countViolations(Graph g, IndependenceTest test) throws Exception {
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
