package edu.cmu.tetrad.search.work_in_progress.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.unmix.*;
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
 * - params-only test (pooled initializer, no residual scaling, Laplace reassignment)
 * - small topology difference test (parent-superset initializer, residual scaling)
 * - EM-on-residuals baseline (parent-superset, diagonal covariance), with ARI
 */
public class TestCausalUnmixerBetter {

    // ---------- utilities ----------

    /** Shuffle dataset rows and apply same permutation to labels. */
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

    /** Quick ARI for diagnostics. */
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
    private static double comb2(int m) { return m < 2 ? 0 : m * (m - 1) / 2.0; }

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

    /** Make a two-regime mixture with small topology differences and non-Gaussian errors. */
    private static @NotNull MixOut getMixOutTopoDiff() {
        int p = 12, n1 = 800, n2 = 800;
        long seed = 7;

        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph gA = RandomGraph.randomGraph(vars, 0, 14, 100, 100, 100, false);
        Graph gB = copyWithFlippedDirections(gA, 4, new Random(seed)); // flip ~4 edges

        // Non-Gaussian errors (e.g., Laplace): SIMULATION_ERROR_TYPE = 3
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

        return shuffleWithLabels(concat, labels, seed);
    }

    /** Correctly copy g and flip a few directions while preserving skeleton. */
    private static Graph copyWithFlippedDirections(Graph g, int flips, Random rnd) {
        Graph h = new EdgeListGraph(g); // copy nodes + edges (or: new EdgeListGraph(g))
        List<Edge> dir = h.getEdges().stream().filter(Edge::isDirected).collect(Collectors.toList());
        if (dir.isEmpty()) return h;
        Collections.shuffle(dir, rnd);
        int done = 0;
        for (Edge e : dir) {
            if (done >= flips) break;
            Node a = e.getNode1(), b = e.getNode2();
            if (!h.isAdjacentTo(a, b)) continue;
            h.removeEdge(e);
            Edge rev = Edges.directedEdge(b, a);
            if (!h.isAdjacentTo(b, a)) { h.addEdge(rev); done++; }
            else { h.addEdge(e); }
        }
        return h;
    }

    // ---------- Tests ----------

    /** Params-only difference: same DAG, different parameters; pooled init; no residual scaling; Laplace reassignment. */
    @Test
    public void unmix_paramsOnly_twoRegimes() {
        int p = 12, n1 = 700, n2 = 700;
        long seed = 42;

        // Single DAG
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < p; i++) vars.add(new ContinuousVariable("X" + i));
        Graph g = RandomGraph.randomGraph(vars, 0, 12, 100, 100, 100, false);

        // Non-Gaussian errors to ease separation (still fine if Gaussian)
        Parameters params = new Parameters();
        params.set(Params.SIMULATION_ERROR_TYPE, 3);
        params.set(Params.SIMULATION_PARAM1, 1);

        SemIm imA = new SemIm(new SemPm(g), params);  // baseline params
        SemIm imB = new SemIm(new SemPm(g), params);  // will be scaled

        // Force parameter differences in regime B
        double coefScale = 2.2, noiseScale = 2.5;
        for (Edge e : g.getEdges()) {
            try {
                double b = imB.getEdgeCoef(e);
                imB.setEdgeCoef(e.getNode1(), e.getNode2(), coefScale * b);
            } catch (Exception ignore) {}
        }
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
        MixOut mix = shuffleWithLabels(concat, labels, seed);

        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 2;
        cfg.useParentSuperset = false;     // pooled is fine for params-only
        cfg.robustScaleResiduals = false;  // keep magnitude signal
        cfg.kmeansIters = 100;
        cfg.reassignMaxPasses = 3;
        cfg.reassignStopIfNoChange = true;
        cfg.useLaplaceReassign = true;     // non-Gaussian scoring helps

        ResidualRegressor reg = new LinearQRRegressor();

        Function<DataSet, Graph> pooled = bossPerm();
        UnmixResult res = UnmixCausalProcesses.run(mix.data, cfg, reg, pooled, pooled);

        double ari = adjustedRandIndex(mix.labels, res.labels);
        System.out.println("Params-only ARI = " + ari);
        for (int k = 0; k < res.K; k++) {
            System.out.printf("cluster %d: n=%d graph=%s%n", k, res.clusterData.get(k).getNumRows(), res.clusterGraphs.get(k));
        }
    }

    /** Small topology differences: parent-superset initializer; residual scaling on; Gaussian reassignment OK. */
    @Test
    public void unmix_smallTopoDiff_twoRegimes() {
        MixOut mix = getMixOutTopoDiff();

        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 2;
        cfg.useParentSuperset = true;
        cfg.supersetCfg.topM = 10;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        cfg.supersetCfg.useBagging = false;
        cfg.robustScaleResiduals = true; // emphasize geometry vs magnitude
        cfg.kmeansIters = 100;
        cfg.reassignMaxPasses = 3;
        cfg.reassignStopIfNoChange = true;
        cfg.useLaplaceReassign = false;   // Gaussian scoring is fine here

        ResidualRegressor reg = new LinearQRRegressor();
        UnmixResult res = UnmixCausalProcesses.run(mix.data, cfg, reg, null, bossPerm());

        double ari = adjustedRandIndex(mix.labels, res.labels);
        System.out.println("Small-topology-diff ARI = " + ari);
        for (int k = 0; k < res.K; k++) {
            System.out.printf("cluster %d: n=%d graph=%s%n", k, res.clusterData.get(k).getNumRows(), res.clusterGraphs.get(k));
        }
    }

    /** EM-on-residuals baseline on the same topo-diff mixture; parent-superset + diagonal cov; reports ARI. */
    @Test
    public void testEmUnmix() {
        MixOut mix = getMixOutTopoDiff();
        DataSet mixedData = mix.data;

        EmUnmix.Config cfg = new EmUnmix.Config();
        cfg.K = 2;
        cfg.useParentSuperset = true;             // topo difference → superset init
        cfg.supersetCfg.topM = 10;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        cfg.robustScaleResiduals = true;          // geometry over magnitude
        cfg.covType = GaussianMixtureEM.CovarianceType.DIAGONAL; // fast & robust
        cfg.emMaxIters = 200;
        cfg.kmeansRestarts = 10;                  // stabler init

        ResidualRegressor reg = new LinearQRRegressor();

        UnmixResult res = EmUnmix.run(
                mixedData,
                cfg,
                reg,
                ds -> { // pooled path unused when useParentSuperset=true, but keep for completeness
                    try { return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(ds)))).search(); }
                    catch (InterruptedException e) { throw new RuntimeException(e); }
                },
                ds -> {
                    try { return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(ds)))).search(); }
                    catch (InterruptedException e) { throw new RuntimeException(e); }
                }
        );

        double ari = adjustedRandIndex(mix.labels, res.labels);
        System.out.println("EM-on-residuals ARI = " + ari);
        System.out.println("EM graphs = " + res.clusterGraphs);

        // Optional: pick K by BIC on residuals
        UnmixResult best = EmUnmix.selectK(
                mixedData, 1, 5, reg,
                ds -> { try { return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(ds)))).search(); }
                catch (InterruptedException e) { throw new RuntimeException(e); } },
                ds -> { try { return new PermutationSearch(new Boss(new SemBicScore(new CovarianceMatrix(ds)))).search(); }
                catch (InterruptedException e) { throw new RuntimeException(e); } },
                cfg
        );
        System.out.println("EM best-K graphs = " + best.clusterGraphs);
    }

    // ---------- helpers ----------

    private static class MixOut {
        DataSet data;
        int[] labels;
    }
}