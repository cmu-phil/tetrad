package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;

public class TestUnmix {

    @Test
    public void testUnmix() {
        // somewhere in your driver:
        ResidualRegressor reg = new LinearQRRegressor(); // or your Legendre version

        Function<DataSet, Graph> pooled = ds -> {
            try {
                // e.g., PC-Max on pooled data
                // return new PcMax(ds, new IndTest...(...)).search();
//                IndTestFisherZ test = new IndTestFisherZ(ds, 0.01);
//                Pc pc = new Pc(test);
//                pc.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
//                return pc.search();

                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(2);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Function<DataSet, Graph> perCluster = ds -> {
            if (ds.getNumRows() < 20) {
                // fall back to pooled graph or a lighter variant
                return pooled.apply(ds);
            }
            try {
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(2);
                return new PermutationSearch(new Boss(score)).search();
//            return pooled.apply(ds);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 3;                    // or try selectK(...)
        cfg.doOneReassign = true;
        cfg.robustScaleResiduals = true;

        DataSet concat = getDataSet();

        UnmixResult result = UnmixCausalProcesses.run(concat, cfg, reg, pooled, perCluster);

        // Use results:
        for (int k = 0; k < result.K; k++) {
            DataSet dk = result.clusterData.get(k);
            Graph Gk = result.clusterGraphs.get(k);

            System.out.println("dataset = " + k);
            System.out.println(dk.getNumRows());
            System.out.println("graph = " + Gk);
        }
        // result.labels is length n: cluster id per original row
    }

    private static @NotNull DataSet getDataSet() {
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph g1 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);
        Graph g2 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);
        Graph g3 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);

        System.out.println("g1 = " + g1);
        System.out.println("g2 = " + g2);
        System.out.println("g3 = " + g3);

        SemPm pm1 = new SemPm(g1);
        SemPm pm2 = new SemPm(g2);
        SemPm pm3 = new SemPm(g3);

        SemIm im1 = new SemIm(pm1);
        SemIm im2 = new SemIm(pm2);
        SemIm im3 = new SemIm(pm3);

        DataSet d1 = im1.simulateData(400, false);
        DataSet d2 = im2.simulateData(600, false);
        DataSet d3 = im3.simulateData(600, false);

        DataSet concat = DataTransforms.concatenate(d1, d2, d3);
        return concat;
    }

    @Test
    public void testUnmix2() {
        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.K = 2;
        cfg.useParentSuperset = true;
        cfg.supersetCfg.topM = 12;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.PEARSON;
        cfg.supersetCfg.useBagging = true;
        cfg.supersetCfg.bags = 6;
        cfg.supersetCfg.bagFraction = 0.6;
        cfg.supersetCfg.shallowSearch = ds -> {
            // e.g., PC-Max with depth 1–2, or BOSS with very light settings
            // return new PcMax(ds, new IndTest...(...)).setDepth(1).search();
            return /* your shallow search */ null; // you can also leave null to skip bagging
        };

        ResidualRegressor reg = new LinearQRRegressor(); // or your Legendre regressor

        UnmixResult res = UnmixCausalProcesses.run(
                getDataSet(),
                cfg,
                reg,
                pooledDs -> /* pooled search if you keep that path available */ null,
                clusterDs -> /* full search on each cluster */ null
        );
    }

    @Test
    public void testUnmix_fair() {
        ResidualRegressor reg = new LinearQRRegressor(); // swap to Ridge later if you add it

        Function<DataSet, Graph> pooled = ds -> {
            try {
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(2);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) { throw new RuntimeException(e); }
        };
        Function<DataSet, Graph> perCluster = pooled; // fine for now

        // 1) data + ground truth labels (shuffled)
        var gen = genMixture(400, 600, 600); // returns {data, labelsTrue}
        DataSet concat = gen.data;
        int[] yTrue = gen.labels; // 0,1,2

        // 2) config: parent-superset + multi-pass reassignment
        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.useParentSuperset = true;
        cfg.supersetCfg.topM = 10;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        cfg.supersetCfg.useBagging = false; // keep simple first
        cfg.robustScaleResiduals = true;
        cfg.reassignMaxPasses = 3;
        cfg.reassignStopIfNoChange = true;
        cfg.kmeansIters = 100; // a bit more

        // Try K selection
        cfg.K = 3; // if you want fixed-K; otherwise call selectK(...)
        UnmixResult result = UnmixCausalProcesses.run(concat, cfg, reg, null, perCluster);

        // 3) basic reporting
        System.out.println("ARI vs truth = " + adjustedRandIndex(yTrue, result.labels));
        for (int k = 0; k < result.K; k++) {
            System.out.printf("cluster %d: n=%d, graph=%s%n",
                    k, result.clusterData.get(k).getNumRows(), result.clusterGraphs.get(k));
        }
    }

    /** Generates a shuffled 3-component mixture and true labels. */
    private static class MixOut { DataSet data; int[] labels; }
    private static MixOut genMixture(int n1, int n2, int n3) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) nodes.add(new ContinuousVariable("X"+i));

        Graph g1 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);
        Graph g2 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);
        Graph g3 = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);

        SemIm im1 = new SemIm(new SemPm(g1));
        SemIm im2 = new SemIm(new SemPm(g2));
        SemIm im3 = new SemIm(new SemPm(g3));

        DataSet d1 = im1.simulateData(n1, false);
        DataSet d2 = im2.simulateData(n2, false);
        DataSet d3 = im3.simulateData(n3, false);

        DataSet concat = DataTransforms.concatenate(d1, d2, d3);

        // Build labels then shuffle rows jointly
        int n = n1 + n2 + n3;
        int[] lab = new int[n];
        for (int i = 0; i < n1; i++) lab[i] = 0;
        for (int i = n1; i < n1+n2; i++) lab[i] = 1;
        for (int i = n1+n2; i < n; i++) lab[i] = 2;

        List<Integer> perm = new ArrayList<>(n);
        for (int i = 0; i < n; i++) perm.add(i);
        Collections.shuffle(perm, new Random(13));
        DataSet shuffled = concat.subsetRows(perm);
        int[] labShuf = new int[n];
        for (int i = 0; i < n; i++) labShuf[i] = lab[perm.get(i)];

        MixOut out = new MixOut();
        out.data = shuffled;
        out.labels = labShuf;
        return out;
    }

    // Tiny ARI for diagnostics (quick and dirty; replace with your util if you have one).
    private static double adjustedRandIndex(int[] a, int[] b) {
        int n = a.length;
        int maxA = Arrays.stream(a).max().orElse(0);
        int maxB = Arrays.stream(b).max().orElse(0);
        int[][] M = new int[maxA+1][maxB+1];
        int[] row = new int[maxA+1], col = new int[maxB+1];
        for (int i = 0; i < n; i++) { M[a[i]][b[i]]++; row[a[i]]++; col[b[i]]++; }
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
}
