package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Better-targeted unmixing tests with ground-truth labels + ARI.
 */
public class TestUnmixBetter {

    // ---------- utilities ----------

    /**
     * Shuffle dataset rows and apply same permutation to labels.
     */
    private static MixOut shuffleWithLabels(DataSet concat, int[] labels, long seed) {
        int n = concat.getNumRows();
        List<Integer> perm = new ArrayList<>(n);
        for (int i = 0; i < n; i++) perm.add(i);
        Collections.shuffle(perm, new Random(seed));
        DataSet shuffled = concat.subsetRows(perm);
        int[] y = new int[n];
        for (int i = 0; i < n; i++) y[i] = labels[perm.get(i)];
        MixOut out = new MixOut();
        out.data = shuffled;
        out.labels = y;
        return out;
    }

    /**
     * Quick ARI for diagnostics.
     */
    private static double adjustedRandIndex(int[] a, int[] b) {
        int n = a.length;
        int maxA = Arrays.stream(a).max().orElse(0);
        int maxB = Arrays.stream(b).max().orElse(0);
        int[][] M = new int[maxA + 1][maxB + 1];
        int[] row = new int[maxA + 1], col = new int[maxB + 1];
        for (int i = 0; i < n; i++) {
            M[a[i]][b[i]]++;
            row[a[i]]++;
            col[b[i]]++;
        }
        double sumComb = 0, rowComb = 0, colComb = 0;
        for (int i = 0; i <= maxA; i++) for (int j = 0; j <= maxB; j++) sumComb += comb2(M[i][j]);
        for (int i = 0; i <= maxA; i++) rowComb += comb2(row[i]);
        for (int j = 0; j <= maxB; j++) colComb += comb2(col[j]);
        double totalComb = comb2(n);
        double exp = rowComb * colComb / totalComb;
        double max = 0.5 * (rowComb + colComb);
        return (sumComb - exp) / (max - exp + 1e-12);
    }

    private static double comb2(int m) {
        return m < 2 ? 0 : m * (m - 1) / 2.0;
    }

    private static Function<DataSet, Graph> bossPerm() {
        return ds -> {
            try {
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(2);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static @NotNull MixOut getMixOut() {
        int p = 12, n1 = 800, n2 = 800;
        long seed = 7;

        // Base graph and a slightly modified version (same skeleton; flip a few directions)
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph gA = RandomGraph.randomGraph(vars, 0, 14, 100, 100, 100, false);
        Graph gB = copyWithFlippedDirections(gA, 4, new Random(seed)); // flip ~4 edges

        Parameters params = new Parameters();
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm imA = new SemIm(new SemPm(gA), params);
        SemIm imB = new SemIm(new SemPm(gB), params);

        DataSet dA = imA.simulateData(n1, false);
        DataSet dB = imB.simulateData(n2, false);

        DataSet concat = DataTransforms.concatenate(dA, dB);
        int[] labels = new int[n1 + n2];
        Arrays.fill(labels, 0, n1, 0);
        Arrays.fill(labels, n1, n1 + n2, 1);

        MixOut mix = shuffleWithLabels(concat, labels, seed);
        return mix;
    }

    // ---------- Test 1: same topology, different parameters (easiest) ----------

    private static Graph copyWithFlippedDirections(Graph g, int flips, Random rnd) {
        Graph h = new EdgeListGraph(g.getNodes());
        List<Edge> dir = h.getEdges().stream().filter(e -> e.isDirected()).collect(Collectors.toList());
        if (dir.size() == 0) return h;
        Collections.shuffle(dir, rnd);
        int done = 0;
        for (Edge e : dir) {
            if (done >= flips) break;
            Node a = e.getNode1(), b = e.getNode2();
            if (h.isAdjacentTo(a, b)) {
                h.removeEdge(e);
                // try to flip direction if it doesn't create a duplicate
                Edge rev = Edges.directedEdge(b, a);
                if (!h.isAdjacentTo(b, a)) {
                    h.addEdge(rev);
                    done++;
                } else {
                    // if reverse exists, just put back original to preserve skeleton
                    h.addEdge(e);
                }
            }
        }
        return h;
    }

    // ---------- Test 2: small topology differences (moderate) ----------

    @Test
    public void unmix_paramsOnly_twoRegimes() {
        int p = 12, n1 = 700, n2 = 700;
        long seed = 42;

        // Variables and a single random DAG
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph g = RandomGraph.randomGraph(vars, 0, 12, 100, 100, 100, false);

        // Same DAG, but FORCE large parameter differences for regime B
        Parameters params = new Parameters();
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm imA = new SemIm(new SemPm(g), params);           // regime A (baseline)
        SemIm imB = new SemIm(new SemPm(g), params);           // regime B (we'll modify)

        // Scale all edge coefficients up and error variances up (stronger differences)
        double coefScale = 1.8;    // try 1.5–2.0
        double noiseScale = 2.0;   // try 1.5–3.0
        g.getEdges().forEach(e -> {
            try {
                double b = imB.getEdgeCoef(e);
                imB.setEdgeCoef(e.getNode1(), e.getNode2(), coefScale * b);
            } catch (Exception ignore) {
            }
        });
        for (Node v : vars) {
            double v0 = imB.getErrVar(v);
            imB.setErrVar(v, noiseScale * v0);
        }

        DataSet d1 = imA.simulateData(n1, false);
        DataSet d2 = imB.simulateData(n2, false);

        DataSet concat = DataTransforms.concatenate(d1, d2);
        int[] labels = new int[n1 + n2];
        Arrays.fill(labels, 0, n1, 0);
        Arrays.fill(labels, n1, n1 + n2, 1);

        // shuffle rows + labels together
        var mix = shuffleWithLabels(concat, labels, seed);

        // Config: pooled initializer is fine here; TURN OFF scaling
        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 2;
        cfg.useParentSuperset = false;
//        cfg.robustScaleResiduals = false;     // <-- critical

        cfg.useLaplaceReassign = true;
        cfg.robustScaleResiduals = false;   // keep scale info when regimes differ by noise/coeff magnitudes

        cfg.kmeansIters = 100;
        cfg.reassignMaxPasses = 3;
        cfg.reassignStopIfNoChange = true;

        ResidualRegressor reg = new LinearQRRegressor();

        Function<DataSet, Graph> bossPerm = ds -> {
            try {
                var score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(2);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        UnmixResult res = UnmixCausalProcesses.run(mix.data, cfg, reg, bossPerm, bossPerm);

        double ari = adjustedRandIndex(mix.labels, res.labels);
        System.out.println("Params-only ARI = " + ari);
        for (int k = 0; k < res.K; k++) {
            System.out.printf("cluster %d: n=%d%n", k, res.clusterData.get(k).getNumRows());
        }
    }

    @Test
    public void unmix_smallTopoDiff_twoRegimes() {
        MixOut mix = getMixOut();

        // Config: parent-superset initializer helps when topologies differ
        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 2;
        cfg.useParentSuperset = true;
        cfg.supersetCfg.topM = 10;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL; // robust
        cfg.supersetCfg.useBagging = false; // start simple
        cfg.robustScaleResiduals = true;
        cfg.kmeansIters = 100;
        cfg.reassignMaxPasses = 3;
        cfg.reassignStopIfNoChange = true;

        ResidualRegressor reg = new LinearQRRegressor();
        UnmixResult res = UnmixCausalProcesses.run(
                mix.data, cfg, reg, null, bossPerm()
        );

        double ari = adjustedRandIndex(mix.labels, res.labels);
        System.out.println("Small-topology-diff ARI = " + ari);
        for (int k = 0; k < res.K; k++) {
            System.out.printf("cluster %d: n=%d%n", k, res.clusterData.get(k).getNumRows());
        }
        // Expect ARI ~0.4–0.8 depending on flip count and sample size; tune topM / reg if low.
    }

    @Test
    public void testEuUnmix() {
        MixOut mix = getMixOut();

        DataSet mixedData = mix.data;

        // Build residuals using pooled graph (params-only) or superset (topology differences)
        EmUnmix.Config cfg = new EmUnmix.Config();
        cfg.K = 2;
        cfg.useParentSuperset = false;      // true if graphs differ a lot
        cfg.robustScaleResiduals = false;   // keep scale info for params-only mixtures
        cfg.covType = GaussianMixtureEM.CovarianceType.FULL;
        cfg.emMaxIters = 200;
        cfg.kmeansRestarts = 5;

        ResidualRegressor reg = new LinearQRRegressor();

        UnmixResult res = EmUnmix.run(
                mixedData,
                cfg,
                reg,
                pooledDs -> {
                    try {
                        return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(pooledDs)))).search();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                clusterDs -> {
                    try {
                        return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(clusterDs)))).search();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        System.out.println("res graphs = " + res.clusterGraphs);

        // Or pick K by BIC:
        UnmixResult best = EmUnmix.selectK(
                mixedData, 1, 5, reg,
                pooledDs -> {
                    try {
                        return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(pooledDs)))).search();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                clusterDs -> {
                    try {
                        return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(clusterDs)))).search();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                },
                cfg
        );

        System.out.println("best graphs = " + best.clusterGraphs);
    }


    // ---------- helpers to flip edge orientations while keeping skeleton ----------

    private static class MixOut {
        DataSet data;
        int[] labels;
    }

}