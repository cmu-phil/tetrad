package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.*;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests for the adequacy footer numbers and the simulator bookkeeping around
 * them: the training improvement must be scale-free, the cross-validation
 * MMD² must be computed on standardized data, single-mechanism refits must
 * not pollute the parent simulator's node reports, and simulation must
 * track how often it extrapolates.
 */
public class NNEstimatorAdequacyTest {

    private static DataSet linear(long seed, int n, double aX, double aW) {
        Random r = new Random(seed);
        List<Node> vars = List.of(new ContinuousVariable("X"),
                new ContinuousVariable("W"), new ContinuousVariable("Y"));
        DoubleDataBox box = new DoubleDataBox(n, 3);
        for (int i = 0; i < n; i++) {
            double x = r.nextGaussian(), w = r.nextGaussian();
            box.set(i, 0, x); box.set(i, 1, w); box.set(i, 2, aX * x + aW * w + r.nextGaussian());
        }
        return new BoxDataSet(box, vars);
    }

    private static Graph collider(List<Node> vars) {
        Graph g = new EdgeListGraph(vars);
        g.addDirectedEdge(vars.get(0), vars.get(2));
        g.addDirectedEdge(vars.get(1), vars.get(2));
        return g;
    }

    private static DataSet scaleY(DataSet d, double f) {
        DataSet c = d.copy();
        int j = c.getColumnIndex(c.getVariable("Y"));
        for (int i = 0; i < c.getNumRows(); i++) c.setDouble(i, j, c.getDouble(i, j) * f);
        return c;
    }

    private static NNEstimator fitted(DataSet d) {
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        p.edgeRepeats = 1;
        NNEstimator est = new NNEstimator(d, collider(d.getVariables()), p);
        est.fit();
        return est;
    }

    private static double improvementOf(AdequacyReport r, String node) {
        for (NodeAdequacySummary s : r.nodeSummaries) if (s.node.equals(node)) return s.improvement;
        return Double.NaN;
    }

    @Test
    public void testTrainingImprovementIsScaleFreeR2() {
        DataSet base = linear(1, 1500, 2.0, 1.0);
        NNEstimator a = fitted(base);
        a.simulate(1500);
        NNEstimator b = fitted(scaleY(base, 10.0));
        b.simulate(1500);

        double impA = improvementOf(a.getAdequacyReport(), "Y");
        double impB = improvementOf(b.getAdequacyReport(), "Y");

        // Y = 2X + W + e has R² = 5/6; training R² should be near that and
        // not depend on the scale of Y.
        assertTrue("training improvement should be a training R² near 5/6, got " + impA,
                impA > 0.6 && impA < 0.95);
        assertTrue("training improvement should be scale-free, got " + impB, impB > 0.6 && impB < 0.95);
        assertEquals(impA, impB, 0.1);
        assertTrue("Y beats its marginal baseline, so at least one node improved",
                a.getAdequacyReport().getFracImproved() > 0);
    }

    @Test
    public void testCvMmd2IsStableUnderScaling() {
        DataSet base = linear(1, 1500, 2.0, 1.0);
        CVReport a = fitted(base).crossValidate(4);
        CVReport b = fitted(scaleY(base, 10.0)).crossValidate(4);
        assertTrue(Double.isFinite(a.meanOosMmd2) && a.meanOosMmd2 > 0);
        double ratio = Math.max(a.meanOosMmd2, b.meanOosMmd2) / Math.min(a.meanOosMmd2, b.meanOosMmd2);
        assertTrue("CV MMD² should be computed on standardized data; ratio under x10 scaling was " + ratio,
                ratio < 2.5);
    }

    @Test
    public void testRefitsDoNotPolluteNodeReports() {
        DataSet d = linear(2, 1200, 2.0, 1.0);
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        p.edgeRepeats = 1;
        p.edgeNullRefits = 2;
        NNEstimator est = new NNEstimator(d, collider(d.getVariables()), p);
        est.fit();
        est.simulate(500);
        int before = est.getAdequacyReport().nodeSummaries.size();

        est.computeEdgeStrength("X", "Y", 50);        // two same-parent refits for the null
        est.computePartialEdgeStrength("X", "Y", 3);  // one reduced refit per fold
        est.simulate(500);

        assertEquals("single-mechanism refits must not add node reports to the fitted simulator",
                before, est.getAdequacyReport().nodeSummaries.size());
    }

    @Test
    public void testExtrapolationFractionIsTracked() {
        // X is log-normal, so its bootstrap-resampled root has a heavy right
        // tail; about 1% of simulated rows put X more than 4 training SDs out.
        Random r = new Random(3);
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"));
        DoubleDataBox box = new DoubleDataBox(3000, 2);
        for (int i = 0; i < 3000; i++) {
            double x = Math.exp(r.nextGaussian());
            box.set(i, 0, x); box.set(i, 1, x + r.nextGaussian());
        }
        DataSet d = new BoxDataSet(box, vars);
        Graph g = new EdgeListGraph(vars);
        g.addDirectedEdge(vars.get(0), vars.get(1));
        NNEstimatorParams p = new NNEstimatorParams();
        p.seed = 7L;
        NNEstimator est = new NNEstimator(d, g, p);
        assertTrue(Double.isNaN(est.getLastExtrapolationFraction()));
        est.fit();
        est.simulate(3000);
        double f = est.getLastExtrapolationFraction();
        assertTrue("fraction should be tracked and positive for a heavy-tailed parent, got " + f,
                f > 0.0 && f < 0.1);
    }
}
