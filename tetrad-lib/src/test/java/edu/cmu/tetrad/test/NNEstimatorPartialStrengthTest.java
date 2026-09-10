package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.CVReport;
import edu.cmu.tetrad.sem.NNEstimator;
import edu.cmu.tetrad.sem.NNEstimatorParams;
import edu.cmu.tetrad.sem.NodeCVSummary;
import edu.cmu.tetrad.sem.PartialEdgeStrengthResult;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests for the unified partial edge strength of
 * {@link NNEstimator#computePartialEdgeStrength}: held-out score of the full
 * model minus held-out score of the model with the parent removed, on the
 * same folds as {@link NNEstimator#crossValidate(int)}.
 *
 * <p>For Y = 2X + W + e with independent unit-variance X, W, e, the
 * population R² of Y is 5/6 and the partial ΔR² values are 4/6 for X and
 * 1/6 for W, which sum to the CV table's R² for Y. That additivity holds
 * only for independent parents and is the property the old
 * residual-regression definition did not have.
 */
public class NNEstimatorPartialStrengthTest {

    private static DataSet linear(long seed, int n, double aX, double aW, double copyNoise) {
        Random r = new Random(seed);
        List<Node> vars = List.of(new ContinuousVariable("X"),
                new ContinuousVariable("W"), new ContinuousVariable("Y"));
        DoubleDataBox box = new DoubleDataBox(n, 3);
        for (int i = 0; i < n; i++) {
            double x = r.nextGaussian();
            double w = copyNoise >= 0 ? x + copyNoise * r.nextGaussian() : r.nextGaussian();
            double y = aX * x + aW * w + r.nextGaussian();
            box.set(i, 0, x); box.set(i, 1, w); box.set(i, 2, y);
        }
        return new BoxDataSet(box, vars);
    }

    private static Graph collider(List<Node> vars) {
        Graph g = new EdgeListGraph(vars);
        g.addDirectedEdge(vars.get(0), vars.get(2));
        g.addDirectedEdge(vars.get(1), vars.get(2));
        return g;
    }

    private static NNEstimator fitted(DataSet d, Graph g) {
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        NNEstimator est = new NNEstimator(d, g, p);
        est.fit();
        return est;
    }

    @Test
    public void testPartialSumsToCvR2ForIndependentParents() {
        DataSet d = linear(1, 1500, 2.0, 1.0, -1);
        NNEstimator est = fitted(d, collider(d.getVariables()));

        CVReport cv = est.crossValidate(5);
        double r2Y = Double.NaN;
        for (NodeCVSummary s : cv.nodeSummaries) if (s.node.equals("Y")) r2Y = s.oosR2;

        PartialEdgeStrengthResult px = est.computePartialEdgeStrength("X", "Y", 5);
        PartialEdgeStrengthResult pw = est.computePartialEdgeStrength("W", "Y", 5);

        assertEquals("ΔR²(X→Y) should be near 4/6", 4.0 / 6.0, px.partialR2, 0.10);
        assertEquals("ΔR²(W→Y) should be near 1/6", 1.0 / 6.0, pw.partialR2, 0.08);
        assertEquals("partials of independent parents should sum to Y's CV R²",
                r2Y, px.partialR2 + pw.partialR2, 0.06);
        assertTrue(Double.isNaN(px.partialXentImprovement));
        assertTrue("reduced-model OOS MSE for X removed should be near 5 (var of 2X + e)",
                px.residualVariance > 3.5 && px.residualVariance < 6.5);
    }

    @Test
    public void testFoldModelsAreCachedAndShared() {
        DataSet d = linear(1, 1500, 2.0, 1.0, -1);
        NNEstimator est = fitted(d, collider(d.getVariables()));

        assertFalse(est.hasFoldModels(5));
        PartialEdgeStrengthResult a = est.computePartialEdgeStrength("X", "Y", 5);
        assertTrue("computePartialEdgeStrength should build and cache the fold models",
                est.hasFoldModels(5));

        est.crossValidate(5);
        assertTrue(est.hasFoldModels(5));
        PartialEdgeStrengthResult b = est.computePartialEdgeStrength("X", "Y", 5);
        assertEquals("same folds, same seed: identical result before and after crossValidate",
                a.partialR2, b.partialR2, 0.0);

        est.crossValidate(4);
        assertTrue(est.hasFoldModels(4));
        assertFalse("changing k replaces the cache", est.hasFoldModels(5));
    }

    @Test
    public void testRedundantParentHasNearZeroPartial() {
        DataSet d = linear(2, 1500, 1.0, 1.0, 0.05);
        NNEstimator est = fitted(d, collider(d.getVariables()));
        PartialEdgeStrengthResult px = est.computePartialEdgeStrength("X", "Y", 5);
        assertTrue("X is redundant given W ≈ X, got " + px.partialR2,
                Math.abs(px.partialR2) < 0.1);
    }

    @Test
    public void testSingleParentPartialEqualsNodeR2() {
        // Chain X → Y with Y = 2X + e. Removing X leaves a root mechanism, so
        // the partial ΔR² should equal Y's CV R² (≈ 0.8).
        Random r = new Random(5);
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"));
        DoubleDataBox box = new DoubleDataBox(1200, 2);
        for (int i = 0; i < 1200; i++) {
            double x = r.nextGaussian();
            box.set(i, 0, x); box.set(i, 1, 2 * x + r.nextGaussian());
        }
        DataSet d = new BoxDataSet(box, vars);
        Graph g = new EdgeListGraph(vars);
        g.addDirectedEdge(vars.get(0), vars.get(1));
        NNEstimator est = fitted(d, g);

        CVReport cv = est.crossValidate(5);
        double r2Y = cv.nodeSummaries.get(0).oosR2;
        PartialEdgeStrengthResult px = est.computePartialEdgeStrength("X", "Y", 5);
        assertEquals(r2Y, px.partialR2, 0.05);
        assertTrue(px.partialR2 > 0.6);
    }

    @Test
    public void testDiscreteChildPartial() {
        // X, W continuous; Y binary with P(Y=1) = logistic(2X). W is a parent
        // in the DAG but has no effect, so its partial should be near zero.
        Random r = new Random(9);
        DiscreteVariable y = new DiscreteVariable("Y", 2);
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("W"), y);
        MixedDataBox box = new MixedDataBox(vars, 1500);
        for (int i = 0; i < 1500; i++) {
            double x = r.nextGaussian(), w = r.nextGaussian();
            double p1 = 1.0 / (1.0 + Math.exp(-2 * x));
            box.set(i, 0, x); box.set(i, 1, w); box.set(i, 2, r.nextDouble() < p1 ? 1 : 0);
        }
        DataSet d = new BoxDataSet(box, vars);
        NNEstimator est = fitted(d, collider(vars));

        PartialEdgeStrengthResult px = est.computePartialEdgeStrength("X", "Y", 5);
        PartialEdgeStrengthResult pw = est.computePartialEdgeStrength("W", "Y", 5);
        assertTrue(px.discreteChild);
        assertTrue(Double.isNaN(px.partialR2));
        assertTrue("X→Y xent improvement should be clearly positive, got " + px.partialXentImprovement,
                px.partialXentImprovement > 0.1);
        assertTrue("W→Y xent improvement should be near zero, got " + pw.partialXentImprovement,
                Math.abs(pw.partialXentImprovement) < 0.05);
    }
}
