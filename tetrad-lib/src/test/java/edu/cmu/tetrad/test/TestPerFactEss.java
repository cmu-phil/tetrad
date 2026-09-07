package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.utils.PerFactEss;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Tests for per-fact effective sample sizes from block structure, and for the Fisher-Z fix that
 * makes an explicitly set effective sample size survive row setting (previously the row count
 * silently replaced it, so the parameter was a no-op through the Markov Checker).
 */
public class TestPerFactEss {

    private static final int K = 60;    // blocks
    private static final int M = 10;    // rows per block
    private static final int N = K * M;

    /**
     * Block-structured data: A and B are block-constant with a weak block-level linear relation;
     * W varies freely within blocks, independent of both.
     */
    private static DataSet blockData(long seed) {
        Random rng = new Random(seed);
        double[][] d = new double[N][3];
        int row = 0;
        for (int k = 0; k < K; k++) {
            double a = rng.nextGaussian();
            double b = 0.25 * a + Math.sqrt(1 - 0.25 * 0.25) * rng.nextGaussian();
            for (int m = 0; m < M; m++) {
                d[row][0] = a;
                d[row][1] = b;
                d[row][2] = rng.nextGaussian();
                row++;
            }
        }
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("A"));
        vars.add(new ContinuousVariable("B"));
        vars.add(new ContinuousVariable("W"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static int[] ids() {
        int[] ids = new int[N];
        for (int i = 0; i < N; i++) ids[i] = i / M;
        return ids;
    }

    @Test
    public void testBlockConstantFactGetsBlockCountEss() {
        DataSet data = blockData(31L);
        Node a = data.getVariable("A"), b = data.getVariable("B");

        int nEff = PerFactEss.effectiveSampleSize(data, ids(), a, b, new HashSet<>());

        // Both variables block-constant: n_eff should be about the number of blocks.
        assertTrue("expected ~" + K + " for a block-constant fact, got " + nEff,
                nEff >= K - 10 && nEff <= K + 15);
    }

    @Test
    public void testWithinBlockFactKeepsNominalEss() {
        DataSet data = blockData(31L);
        Node a = data.getVariable("A"), w = data.getVariable("W");

        int nEff = PerFactEss.effectiveSampleSize(data, ids(), a, w, new HashSet<>());

        // W varies freely within blocks (ICC ~ 0): n_eff should be close to n.
        assertTrue("expected ~" + N + " for a within-block fact, got " + nEff,
                nEff >= (int) (0.8 * N));
    }

    @Test
    public void testMarkovCheckPerFactEssDeflatesBlockLevelRejections() {
        DataSet data = blockData(31L);

        // The empty graph implies A _||_ B among its facts; the weak block-level relation
        // rejects it decisively at n = 600 but should not at n_eff ~ 60.
        Graph empty = new EdgeListGraph(data.getVariables());

        MarkovCheck plain = new MarkovCheck(empty, new IndTestFisherZ(data, 0.01),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        plain.setParallelized(false);
        plain.generateAllResults();
        double pPlain = pFor(plain, "A", "B");

        MarkovCheck ess = new MarkovCheck(empty, new IndTestFisherZ(data, 0.01),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
        ess.setEssBlockIds(ids());
        ess.setPerFactEss(true);
        ess.generateAllResults();
        double pEss = pFor(ess, "A", "B");

        assertTrue("per-fact ESS should raise the block-level fact's p-value: plain=" + pPlain
                + ", ess=" + pEss, pEss > pPlain);
        assertTrue("block-level fact should reject at nominal n: p=" + pPlain, pPlain < 0.01);
        assertTrue("block-level fact should not reject at block-count ESS: p=" + pEss,
                pEss > 0.01);
    }

    /**
     * Regression pin for the Fisher-Z fix: with rows set (as the Markov Checker always does), an
     * explicitly set effective sample size must change the p-value. Fails on unpatched builds,
     * where the row count silently replaced the explicit value.
     */
    @Test
    public void testFisherZHonorsExplicitEssWhenRowsAreSet() {
        DataSet data = blockData(31L);
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) rows.add(i);
        test.setRows(rows);

        Node a = test.getVariable("A"), b = test.getVariable("B");
        double pNominal = test.getPValue(a, b, new HashSet<>());

        test.setEffectiveSampleSize(K);
        double pEss = test.getPValue(a, b, new HashSet<>());

        assertTrue("explicit ESS should raise the p-value with rows set: nominal=" + pNominal
                + ", ess=" + pEss, pEss > pNominal);
    }

    private static double pFor(MarkovCheck mc, String x, String y) {
        for (var r : mc.getResults(true)) {
            IndependenceFact f = r.getFact();
            String fx = f.getX().getName(), fy = f.getY().getName();
            if ((fx.equals(x) && fy.equals(y)) || (fx.equals(y) && fy.equals(x))) {
                return r.getPValue();
            }
        }
        throw new IllegalStateException("Fact " + x + " _||_ " + y + " not found among results.");
    }
}
