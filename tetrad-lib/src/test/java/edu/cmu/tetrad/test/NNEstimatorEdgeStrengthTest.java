package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.EdgeStrengthResult;
import edu.cmu.tetrad.sem.NNEstimator;
import edu.cmu.tetrad.sem.NNEstimatorParams;
import edu.cmu.tetrad.sem.PartialEdgeStrengthResult;
import edu.cmu.tetrad.sem.TrainedDagSimulatorGNM;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

/**
 * Tests for the intervention-strength semantics of
 * {@link NNEstimator#computeEdgeStrength} and for thread safety of
 * {@link TrainedDagSimulatorGNM} simulation.
 *
 * <p>{@link #testConcurrentSimulateMatchesSequential} fails on builds where
 * the mechanisms keep shared scratch buffers: concurrent simulations with a
 * common seed then diverge from the sequential result. The remaining tests
 * exercise the DoWhy-style definition, under which the variance difference
 * for a linear edge Y = aX + … is a²·var(X), and a parent that is redundant
 * with another parent still registers as strong while its partial strength
 * goes to zero.
 */
public class NNEstimatorEdgeStrengthTest {

    /** X, W, Y with Y = aX·X + aW·W + e. If copyNoise ≥ 0, W = X + copyNoise·N(0,1). */
    private static DataSet linear(long seed, int n, double aX, double aW, double copyNoise) {
        Random r = new Random(seed);
        List<Node> vars = List.of(new ContinuousVariable("X"),
                new ContinuousVariable("W"), new ContinuousVariable("Y"));
        DoubleDataBox box = new DoubleDataBox(n, 3);
        for (int i = 0; i < n; i++) {
            double x = r.nextGaussian();
            double w = copyNoise >= 0 ? x + copyNoise * r.nextGaussian() : r.nextGaussian();
            double y = aX * x + aW * w + r.nextGaussian();
            box.set(i, 0, x);
            box.set(i, 1, w);
            box.set(i, 2, y);
        }
        return new BoxDataSet(box, vars);
    }

    private static Graph collider(List<Node> vars) {
        Graph g = new EdgeListGraph(vars);
        g.addDirectedEdge(vars.get(0), vars.get(2));
        g.addDirectedEdge(vars.get(1), vars.get(2));
        return g;
    }

    @Test
    public void testConcurrentSimulateMatchesSequential() throws Exception {
        DataSet d = linear(3, 1500, 2.0, 1.0, -1);
        TrainedDagSimulatorGNM.Params gp = new TrainedDagSimulatorGNM.Params();
        gp.seed = 11L;
        TrainedDagSimulatorGNM sim = new TrainedDagSimulatorGNM(d, collider(d.getVariables()), gp);
        sim.fit();

        double[][] ref = sim.simulate(1500, 99L).cont;

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<double[][]>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) fs.add(pool.submit(() -> sim.simulate(1500, 99L).cont));
        int mismatches = 0;
        for (Future<double[][]> f : fs) if (!Arrays.deepEquals(f.get(), ref)) mismatches++;
        pool.shutdown();

        assertEquals("concurrent simulate() must equal sequential simulate() with the same seed",
                0, mismatches);
    }

    @Test
    public void testLinearEdgeVarianceDifference() {
        // Y = 2X + W + e with X ⊥ W, all unit variance:
        // intervention ΔVar(X→Y) = 4·var(X) = 4, ΔVar(W→Y) = 1.
        DataSet d = linear(1, 2500, 2.0, 1.0, -1);
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        NNEstimator est = new NNEstimator(d, collider(d.getVariables()), p);
        est.fit();

        EdgeStrengthResult ex = est.computeEdgeStrength("X", "Y", 200);
        EdgeStrengthResult ew = est.computeEdgeStrength("W", "Y", 200);

        assertTrue("ΔVar(X→Y) should be near 4, got " + ex.varianceDiff,
                ex.varianceDiff > 2.5 && ex.varianceDiff < 6.5);
        assertTrue("ΔVar(W→Y) should be near 1, got " + ew.varianceDiff,
                ew.varianceDiff > 0.5 && ew.varianceDiff < 2.0);
        assertTrue("ratio should be near 4, got " + ex.varianceDiff / ew.varianceDiff,
                ex.varianceDiff / ew.varianceDiff > 2.5 && ex.varianceDiff / ew.varianceDiff < 6.5);
        assertTrue("normalized ΔVar for X should exceed W's",
                ex.varianceDiffFrac > ew.varianceDiffFrac);
        assertTrue("MMD² for X should exceed W's", ex.mmd2 > ew.mmd2);
        assertTrue(Double.isNaN(ex.klDivBits));
    }

    @Test
    public void testDeterministicUnderFixedSeed() {
        DataSet d = linear(1, 1500, 2.0, 1.0, -1);
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        NNEstimator est = new NNEstimator(d, collider(d.getVariables()), p);
        est.fit();
        EdgeStrengthResult a = est.computeEdgeStrength("X", "Y", 100);
        EdgeStrengthResult b = est.computeEdgeStrength("X", "Y", 100);
        assertEquals(a.mmd2, b.mmd2, 0.0);
        assertEquals(a.varianceDiff, b.varianceDiff, 0.0);
    }

    @Test
    public void testRedundantParentContrast() {
        // W ≈ X, Y = X + W + e. Intervention strength stays positive for both
        // edges (the mechanism uses them); partial strength of X is ≈ 0
        // because X adds nothing given W.
        DataSet d = linear(2, 2500, 1.0, 1.0, 0.05);
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        NNEstimator est = new NNEstimator(d, collider(d.getVariables()), p);
        est.fit();

        EdgeStrengthResult rx = est.computeEdgeStrength("X", "Y", 200);
        EdgeStrengthResult rw = est.computeEdgeStrength("W", "Y", 200);
        PartialEdgeStrengthResult px = est.computePartialEdgeStrength("X", "Y", 5);

        assertTrue("intervention ΔVar(X→Y) should be clearly positive, got " + rx.varianceDiff,
                rx.varianceDiff > 0.15);
        assertTrue("intervention ΔVar(W→Y) should be clearly positive, got " + rw.varianceDiff,
                rw.varianceDiff > 0.15);
        assertTrue("partial R²(X→Y) should be near zero, got " + px.partialR2,
                px.partialR2 < 0.1);
    }
}
