package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemOptimizerEm;
import edu.cmu.tetrad.sem.SemOptimizerPowell;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The SEM optimizers used to draw random starts from a fixed box regardless of the data's scale, so on data whose
 * variances are in the hundreds the EM optimizer returned after one step and the estimator handed back its starting
 * values, with implied variances of 2 to 5 against sample variances of 370 to 1700 and no warning. These tests fit
 * a two-factor measurement model to data on that scale -- a unit-variance simulation multiplied by 20 -- and require
 * that the estimate reproduce the variances, with Powell and with EM, and that the warning fires on a model that
 * does not.
 */
public class TestSemStartValues {

    private static Graph twoFactor(List<Node> latents, List<Node> indicators) {
        Graph g = new EdgeListGraph();
        for (Node l : latents) g.addNode(l);
        for (Node x : indicators) g.addNode(x);
        g.addDirectedEdge(latents.get(0), latents.get(1));
        for (int i = 0; i < 6; i++) {
            g.addDirectedEdge(latents.get(0), indicators.get(i));
            g.addDirectedEdge(latents.get(1), indicators.get(6 + i));
        }
        return g;
    }

    private static CovarianceMatrix bigScaleCovariance(long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        List<Node> latents = new ArrayList<>(), indicators = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            GraphNode n = new GraphNode("L" + i);
            n.setNodeType(NodeType.LATENT);
            latents.add(n);
        }
        for (int i = 1; i <= 12; i++) indicators.add(new GraphNode("X" + i));
        Graph g = twoFactor(latents, indicators);
        SemIm truth = new SemIm(new SemPm(g));
        for (Node x : indicators) {
            for (Node p : g.getParents(x)) truth.setEdgeCoef(p, x, 0.9);
            truth.setErrVar(x, 0.5);
        }
        DataSet data = truth.simulateData(2000, false);
        Matrix m = data.getDoubleData().copy();
        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) m.set(i, j, 20.0 * m.get(i, j));   // variances x 400
        }
        DataSet scaled = data.copy();
        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) scaled.setDouble(i, j, m.get(i, j));
        }
        return new CovarianceMatrix(scaled);
    }

    private static Graph pureModel(CovarianceMatrix cov) {
        List<Node> latents = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            GraphNode n = new GraphNode("L" + i);
            n.setNodeType(NodeType.LATENT);
            latents.add(n);
        }
        return twoFactor(latents, cov.getVariables());
    }

    private static double worstVarianceRatio(SemIm im) {
        List<Node> measured = im.getMeasuredNodes();
        double worst = 1;
        for (int i = 0; i < measured.size(); i++) {
            double s = im.getSampleCovar().get(i, i), m = im.getImplCovarMeas().get(i, i);
            worst = Math.max(worst, Math.max(s / m, m / s));
        }
        return worst;
    }

    @Test
    public void testPowellReproducesVariancesOnLargeScaleData() throws Exception {
        CovarianceMatrix cov = bigScaleCovariance(31L);
        SemEstimator est = new SemEstimator(cov, new SemPm(pureModel(cov)));
        est.setSemOptimizer(new SemOptimizerPowell());
        SemIm im = est.estimate();
        assertTrue("Powell from scale-aware starts should reproduce the variances; worst ratio "
                   + worstVarianceRatio(im), worstVarianceRatio(im) < 1.5);
        assertNull(SemEstimator.varianceMisfitWarning(im));
    }

    @Test
    public void testEmReproducesVariancesOnLargeScaleData() throws Exception {
        CovarianceMatrix cov = bigScaleCovariance(32L);
        SemEstimator est = new SemEstimator(cov, new SemPm(pureModel(cov)));
        est.setSemOptimizer(new SemOptimizerEm());
        SemIm im = est.estimate();
        assertTrue("EM from scale-aware starts should reproduce the variances; worst ratio "
                   + worstVarianceRatio(im), worstVarianceRatio(im) < 2.0);
    }

    @Test
    public void testWarningFiresOnAnUnmovedStart() throws Exception {
        CovarianceMatrix cov = bigScaleCovariance(33L);
        SemIm im = new SemIm(new SemPm(pureModel(cov)), cov);   // the model's own initialization, never optimized
        String warning = SemEstimator.varianceMisfitWarning(im);
        assertTrue("An unoptimized model at this scale must trip the variance warning.", warning != null);
    }
}
