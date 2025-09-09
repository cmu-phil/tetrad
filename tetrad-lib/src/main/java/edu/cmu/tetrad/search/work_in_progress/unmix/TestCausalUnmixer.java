package edu.cmu.tetrad.search.work_in_progress.unmix;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.unmix.LinearQRRegressor;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.search.unmix.ParentSupersetBuilder;
import edu.cmu.tetrad.search.unmix.ResidualRegressor;
import edu.cmu.tetrad.search.unmix.UnmixResult;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;

public class TestCausalUnmixer {

    private static MixOut genMixture(int n1, int n2, int n3) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) nodes.add(new ContinuousVariable("X" + i));

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
        for (int i = n1; i < n1 + n2; i++) lab[i] = 1;
        for (int i = n1 + n2; i < n; i++) lab[i] = 2;

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

    @Test
    public void testUnmix_fair() {
        ResidualRegressor reg = new LinearQRRegressor(); // swap to Ridge later if you add it

        Function<DataSet, Graph> perCluster = ds -> {
            try {
                SemBicScore score = new SemBicScore(new CovarianceMatrix(ds));
                score.setPenaltyDiscount(4);
                return new PermutationSearch(new Boss(score)).search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        // 1) data + ground truth labels (shuffled)
        var gen = genMixture(400, 600, 600); // returns {data, labelsTrue}
        DataSet concat = gen.data;
        int[] yTrue = gen.labels; // 0,1,2

        // 2) config: parent-superset + multi-pass reassignment
        UnmixCausalProcesses.Config cfg = new UnmixCausalProcesses.Config();
        cfg.useParentSuperset = true;
        cfg.supersetCfg.topM = 10;
        cfg.supersetCfg.scoreType = ParentSupersetBuilder.ScoreType.KENDALL;
        cfg.supersetCfg.useBagging = false;
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

    /**
     * Generates a shuffled 3-component mixture and true labels.
     */
    private static class MixOut {
        DataSet data;
        int[] labels;
    }
}
