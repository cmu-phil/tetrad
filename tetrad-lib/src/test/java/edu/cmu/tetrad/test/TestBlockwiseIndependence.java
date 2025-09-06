package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.test.IndTestBlocksWilkes;
import edu.cmu.tetrad.search.test.IndTestBlocksLemma10;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.*;

/**
 * Compares three ways to judge X ⟂ Y | Z using data from a latent chain (L1->L2->L3; L4 disconnected),
 * each latent with m observed children. Only within-cluster cases are generated with disjoint clusters cX,cY,cZ.
 *
 * Methods:
 *   (1) Pairwise-Rank via blocks: for each (x in X, y in Y), create singleton blocks [x], [y] and one block [Z],
 *       then test [x] ⟂ [y] | [Z] using IndTestBlocksLemma10 (rank-based).
 *   (2) Blockwise over latent meta-variables: IndTestBlocks.
 *   (3) Blockwise over latent meta-variables: IndTestBlocksLemma10.
 *
 * Truth: pairwise m-separation over full DAG (latents+observeds) with Z (observed) conditioned.
 *
 * The full experiment is run under four noise regimes for both latent and observed errors:
 *   - Gaussian(0,1)  (baseline)
 *   - Exponential(1) centered (E-1)
 *   - Gumbel(0,1) centered (subtract Euler–Mascheroni constant)
 *   - Uniform(-1,1)
 */
public class TestBlockwiseIndependence {

    // ------------ Tunables ------------
    private static final long SEED = 13L;
    private static final int numObservedPerLatent = 5; // per latent
    private static final int clusterSize = 3;          // |X|=|Y|=|Z|
    private static final int nSamples = 4000;
    private static final double alpha = 0.01;
    private static final int nCases = 50;              // within-cluster cases only
    // ----------------------------------

    private static final Random rng = new Random(SEED);

    enum NoiseType { GAUSSIAN, EXPONENTIAL, GUMBEL, UNIFORM }

    // ===================== Main test =====================
    @Test
    public void test0() {
        System.out.println("=== TestBlockwiseIndependence (within-cluster; pairwise=rank over singleton blocks) ===");
        System.out.println("alpha=" + alpha + ", n=" + nSamples
                           + ", m=" + numObservedPerLatent + ", clusterSize=" + clusterSize);

        // Build latent model structure (DAG + cluster map)
        LatentModel lm = buildLatentChainModel(numObservedPerLatent);

        // Run four regimes
        runOnce(lm, NoiseType.GAUSSIAN, "Gaussian(0,1)");
        runOnce(lm, NoiseType.EXPONENTIAL, "Exponential(1) centered");
        runOnce(lm, NoiseType.GUMBEL, "Gumbel(0,1) centered");
        runOnce(lm, NoiseType.UNIFORM, "Uniform(-1,1)");
    }

    // --- helper: build random incoherent blocks from all observed variables ---
    private static List<BlockCase> buildRandomIncoherentCases(List<Node> observed, int n, int blockSize) {
        if (3 * blockSize > observed.size()) {
            throw new IllegalArgumentException("Not enough observed variables for three disjoint incoherent blocks.");
        }
        List<BlockCase> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Node> shuffled = new ArrayList<>(observed);
            Collections.shuffle(shuffled, rng);
            List<Node> X = new ArrayList<>(shuffled.subList(0, blockSize));
            List<Node> Y = new ArrayList<>(shuffled.subList(blockSize, 2 * blockSize));
            List<Node> Z = new ArrayList<>(shuffled.subList(2 * blockSize, 3 * blockSize));
            out.add(new BlockCase(X, Y, Z, "INC_X", "INC_Y", "INC_Z")); // cluster labels unused here
        }
        return out;
    }

    private void runOnce(LatentModel lm, NoiseType noise, String label) {
        // Simulate data with the requested noise type (both latent and observed errors use same distribution)
        SimResult sim = simulateLatentData(lm, nSamples, noise);

        System.out.println("\n--- " + label + " ---");

        // Pairwise Fisher-Z kept (optional baseline), but we will use rank-based pairwise below
        CovarianceMatrix cov = new CovarianceMatrix(sim.data);
        IndependenceTest fisherZ = new IndTestFisherZ(cov, alpha);

        // Latent meta-blocks (for blockwise methods)
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> metaVars = new ArrayList<>();
        List<Node> nodes = sim.observedNodes;

        List<String> latentKeys = new ArrayList<>(lm.latentToObserved.keySet());
        for (String l : latentKeys) {
            List<Node> cluster = lm.latentToObserved.get(l);
            List<Integer> idxs = new ArrayList<>();
            for (Node n : cluster) idxs.add(nodes.indexOf(n));
            blocks.add(idxs);
            ContinuousVariable mv = new ContinuousVariable(l);
            mv.setNodeType(NodeType.LATENT);
            metaVars.add(mv);
        }

        IndTestBlocksWilkes blocksTest = new IndTestBlocksWilkes(new BlockSpec(sim.data, blocks, metaVars));
        blocksTest.setAlpha(alpha);
        IndTestBlocksLemma10 lemma10Test = new IndTestBlocksLemma10(new BlockSpec(sim.data, blocks, metaVars));
        lemma10Test.setAlpha(alpha);

        // Build within-cluster cases with distinct cX, cY, cZ
        List<BlockCase> cases = buildWithinClusterCases(sim.observedNodes, lm.latentToObserved, latentKeys, nCases);

        // Evaluate metrics: (A) pairwise-rank via singleton blocks, (B) blockwise (Blocks), (C) blockwise (Lemma10)
        evaluateAndPrintBlockwise(lm.fullGraph, fisherZ, blocksTest, lemma10Test, cases, metaVars, latentKeys);
    }

    // ===================== Case builder =====================
    private static List<BlockCase> buildWithinClusterCases(List<Node> observed,
                                                           Map<String, List<Node>> latentToObserved,
                                                           List<String> latentKeys,
                                                           int n) {
        if (latentKeys.size() < 3) {
            throw new IllegalArgumentException("Need at least 3 latent clusters.");
        }
        for (String k : latentKeys) {
            List<Node> pool = latentToObserved.get(k);
            if (pool == null || pool.size() < clusterSize) {
                throw new IllegalArgumentException("Cluster " + k + " has fewer than clusterSize=" + clusterSize + " observeds.");
            }
        }

        List<BlockCase> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // choose three DISTINCT clusters
            List<String> shuffled = new ArrayList<>(latentKeys);
            Collections.shuffle(shuffled, rng);
            String cX = shuffled.get(0), cY = shuffled.get(1), cZ = shuffled.get(2);

            List<Node> X = sampleWithoutReplacement(latentToObserved.get(cX), clusterSize);
            List<Node> Y = sampleWithoutReplacement(latentToObserved.get(cY), clusterSize);
            List<Node> Z = sampleWithoutReplacement(latentToObserved.get(cZ), clusterSize);

            int guard = 0;
            while (!areDisjoint(X, Y, Z) && guard++ < 50) {
                X = sampleWithoutReplacement(latentToObserved.get(cX), clusterSize);
                Y = sampleWithoutReplacement(latentToObserved.get(cY), clusterSize);
                Z = sampleWithoutReplacement(latentToObserved.get(cZ), clusterSize);
            }
            if (!areDisjoint(X, Y, Z)) { i--; continue; }

            out.add(new BlockCase(X, Y, Z, cX, cY, cZ));
        }
        return out;
    }

    private static boolean areDisjoint(List<Node> A, List<Node> B, List<Node> C) {
        Set<Node> s = new HashSet<>(A);
        s.addAll(B); s.addAll(C);
        return s.size() == (A.size() + B.size() + C.size());
    }

    private static List<Node> sampleWithoutReplacement(List<Node> pool, int k) {
        if (k > pool.size()) throw new IllegalArgumentException("k > pool size");
        List<Node> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, rng);
        return new ArrayList<>(copy.subList(0, k));
    }

    // ===================== Evaluation: Blockwise over latent meta-variables =====================
    private static void evaluateAndPrintBlockwise(Graph truthGraph,
                                                  IndependenceTest fisherZ,
                                                  IndTestBlocksWilkes blocksTest,
                                                  IndTestBlocksLemma10 lemma10Test,
                                                  List<BlockCase> cases,
                                                  List<Node> metaVars,
                                                  List<String> latentKeys) {

        Metrics mBlocks = new Metrics("IndTestBlocks (meta)");
        Metrics mLemma = new Metrics("IndTestBlocksLemma10 (meta)");
        MsepTest dsep = new MsepTest(truthGraph);

        // latent key -> meta node
        Map<String, Node> latentToMeta = new HashMap<>();
        for (int i = 0; i < latentKeys.size(); i++) latentToMeta.put(latentKeys.get(i), metaVars.get(i));

        for (BlockCase bc : cases) {
            boolean truthIndep = allPairsMSep(dsep, bc.X, bc.Y, bc.Z);

            Node metaX = latentToMeta.get(bc.clusterX);
            Node metaY = latentToMeta.get(bc.clusterY);
            Node metaZ = latentToMeta.get(bc.clusterZ);

            boolean predBlocksIndep = false;
            try {
                predBlocksIndep = blocksTest.checkIndependence(
                        metaX, metaY, Collections.singleton(metaZ)).isIndependent();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            boolean predLemmaIndep = lemma10Test.checkIndependence(
                    metaX, metaY, Collections.singleton(metaZ)).isIndependent();

            mBlocks.addCase(truthIndep, predBlocksIndep);
            mLemma.addCase(truthIndep, predLemmaIndep);
        }

        System.out.println("\n--- Blockwise over latent meta-variables ---");
        mBlocks.print();
        mLemma.print();
    }

    // ===================== Truth via m-separation =====================
    private static boolean allPairsMSep(MsepTest dsep, List<Node> X, List<Node> Y, List<Node> Z) {
        Set<Node> Zset = new HashSet<>(Z);
        for (Node x : X) for (Node y : Y)
            if (!dsep.checkIndependence(x, y, Zset).isIndependent()) return false;
        return true;
    }

    // ===================== Latent model & simulation =====================
    private static LatentModel buildLatentChainModel(int m) {
        Node L1 = new ContinuousVariable("L1");
        Node L2 = new ContinuousVariable("L2");
        Node L3 = new ContinuousVariable("L3");
        Node L4 = new ContinuousVariable("L4");

        List<Node> latents = Arrays.asList(L1, L2, L3, L4);
        Graph g = new Dag();
        latents.forEach(g::addNode);
        g.addEdge(Edges.directedEdge(L1, L2));
        g.addEdge(Edges.directedEdge(L2, L3));
        // L4 disconnected

        Map<String, List<Node>> map = new LinkedHashMap<>();
        List<Node> observed = new ArrayList<>();
        Map<Node, Double> loadings = new HashMap<>();

        Random local = new Random(SEED + 1);

        for (Node L : latents) {
            List<Node> children = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                Node O = new ContinuousVariable(L.getName() + "_o" + (i + 1));
                children.add(O);
                observed.add(O);
                g.addNode(O);
                g.addEdge(Edges.directedEdge(L, O));
                double lambda = 0.6 + 0.4 * local.nextDouble(); // [0.6,1.0]
                loadings.put(O, lambda);
            }
            map.put(L.getName(), children);
        }

        double a21 = 0.9;
        double a32 = 0.9;

        return new LatentModel(g, latents, observed, map, a21, a32, loadings);
    }

    private static SimResult simulateLatentData(LatentModel lm, int n, NoiseType noise) {
        int pObs = lm.observedNodes.size();
        double[][] M = new double[n][pObs];

        Map<Node, Integer> colIndex = new HashMap<>();
        for (int j = 0; j < pObs; j++) colIndex.put(lm.observedNodes.get(j), j);

        Random local = new Random(SEED + 2);

        for (int i = 0; i < n; i++) {
            // latent errors
            double e1 = sampleNoise(noise, local);
            double e2 = sampleNoise(noise, local);
            double e3 = sampleNoise(noise, local);
            double e4 = sampleNoise(noise, local);

            // latent structural equations
            double L1v = e1;
            double L2v = 0.9 * L1v + e2;
            double L3v = 0.9 * L2v + e3;
            double L4v = e4;

            Map<String, Double> Lval = new HashMap<>();
            Lval.put("L1", L1v);
            Lval.put("L2", L2v);
            Lval.put("L3", L3v);
            Lval.put("L4", L4v);

            // observed children with own errors
            for (Map.Entry<String, List<Node>> e : lm.latentToObserved.entrySet()) {
                double Lv = Lval.get(e.getKey());
                for (Node O : e.getValue()) {
                    double eps = sampleNoise(noise, local);
                    double lambda = lm.loadings.get(O);
                    double val = lambda * Lv + eps;
                    int j = colIndex.get(O);
                    M[i][j] = val;
                }
            }
        }

        DoubleDataBox box = new DoubleDataBox(n, pObs);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < pObs; j++)
                box.set(i, j, M[i][j]);
        DataSet ds = new BoxDataSet(box, lm.observedNodes);

        return new SimResult(ds, lm.observedNodes);
    }

    // ===================== Noise samplers (centered where needed) =====================
    private static final double EULER_GAMMA = 0.5772156649015329;

    private static double sampleNoise(NoiseType t, Random r) {
        switch (t) {
            case GAUSSIAN:
                return r.nextGaussian(); // mean 0, sd 1
            case EXPONENTIAL: {
                // Exponential(rate=1), mean=1; center to mean 0
                double u = r.nextDouble();
                double x = -Math.log(1.0 - u); // Exp(1)
                return x - 1.0;
            }
            case GUMBEL: {
                // Gumbel(location 0, scale 1): inverse CDF: -ln(-ln(U)); mean = gamma
                double u = r.nextDouble();
                double g = -Math.log(-Math.log(u));
                return g - EULER_GAMMA; // center to mean 0
            }
            case UNIFORM:
                return -1.0 + 2.0 * r.nextDouble(); // U(-1,1), already mean 0
            default:
                throw new IllegalArgumentException("Unknown noise type");
        }
    }

    // ===================== Records & metrics =====================
    private record LatentModel(Graph fullGraph, List<Node> latentNodes, List<Node> observedNodes,
                               Map<String, List<Node>> latentToObserved, double a21, double a32,
                               Map<Node, Double> loadings) {}

    private record SimResult(DataSet data, List<Node> observedNodes) {}

    private record BlockCase(List<Node> X, List<Node> Y, List<Node> Z,
                             String clusterX, String clusterY, String clusterZ) {}

    private static class Metrics {
        final String name;
        int predIndep = 0, predDep = 0;
        int tpInd = 0, fpInd = 0, fnInd = 0;
        int tpDep = 0, fpDep = 0, fnDep = 0;

        Metrics(String name) { this.name = name; }

        void addCase(boolean truthIndep, boolean predIndepFlag) {
            if (predIndepFlag) predIndep++; else predDep++;

            if (truthIndep) {
                if (predIndepFlag) tpInd++; else fnInd++;
            } else {
                if (predIndepFlag) fpInd++; else tpDep++;
            }

            if (!truthIndep) {
                if (!predIndepFlag) { /* TP already */ } else { fpDep++; }
            } else {
                if (!predIndepFlag) fnDep++;
            }
        }

        private static double safeDiv(int a, int b) { return b == 0 ? Double.NaN : (double) a / b; }

        void print() {
            double precInd = safeDiv(tpInd, tpInd + fpInd);
            double recInd  = safeDiv(tpInd, tpInd + fnInd);
            double precDep = safeDiv(tpDep, tpDep + fpDep);
            double recDep  = safeDiv(tpDep, tpDep + fnDep);

            System.out.println("\n[" + name + "]");
            System.out.println("Predicted Independent: " + predIndep + " | Predicted Dependent: " + predDep);
            System.out.printf("Independence  — Precision: %.3f  Recall: %.3f  (TP=%d, FP=%d, FN=%d)%n",
                    precInd, recInd, tpInd, fpInd, fnInd);
            System.out.printf("Dependence    — Precision: %.3f  Recall: %.3f  (TP=%d, FP=%d, FN=%d)%n",
                    precDep, recDep, tpDep, fpDep, fnDep);
        }
    }
}