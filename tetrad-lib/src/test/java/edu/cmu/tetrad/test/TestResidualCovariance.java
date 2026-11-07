package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.ResidualCovariance;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of {@link ResidualCovariance}: on a pure two-factor measurement model the residual correlations are at
 * the level of sampling noise among error terms and the diagonal is the model's error variances; an impurity
 * edge inside a cluster of ten indicators, which the fitted pure model lacks, stands out in the residual and
 * nothing else does; the same edge inside a cluster of five is absorbed by the fit and does not stand out, which
 * is a documented limit of the method rather than a defect, pinned here so that a change in the estimator that
 * moved it would be noticed; and the spectrum floor behaves.
 */
public class TestResidualCovariance {

    /**
     * Two latents, k indicators each, L1 -> L2.
     */
    private static Graph pureGraph(int k, List<Node> latents, List<Node> indicators) {
        Graph g = new EdgeListGraph();
        for (Node l : latents) g.addNode(l);
        for (Node x : indicators) g.addNode(x);
        g.addDirectedEdge(latents.get(0), latents.get(1));
        for (int i = 0; i < k; i++) {
            g.addDirectedEdge(latents.get(0), indicators.get(i));
            g.addDirectedEdge(latents.get(1), indicators.get(k + i));
        }
        return g;
    }

    private static List<Node> latents() {
        List<Node> l = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            GraphNode n = new GraphNode("L" + i);
            n.setNodeType(NodeType.LATENT);
            l.add(n);
        }
        return l;
    }

    private static List<Node> indicators(int k) {
        List<Node> x = new ArrayList<>();
        for (int i = 1; i <= 2 * k; i++) x.add(new GraphNode("X" + i));
        return x;
    }

    private static SemIm strongLoadings(Graph g) {
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        for (Node x : g.getNodes()) {
            if (x.getNodeType() != NodeType.MEASURED) continue;
            for (Node parent : g.getParents(x)) {
                if (parent.getNodeType() == NodeType.LATENT) im.setEdgeCoef(parent, x, 0.9);
            }
            im.setErrVar(x, 0.5);
        }
        for (Node l : g.getNodes()) {
            if (l.getNodeType() == NodeType.LATENT) im.setErrVar(l, 1.0);
        }
        return im;
    }

    /**
     * The residual correlation of the named pair and the largest absolute residual correlation among all other
     * pairs, after fitting the pure k-per-cluster model to data from that model plus one impurity edge of 0.6.
     */
    private static double[] impurityContrast(int k, int from, int to, long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        List<Node> latents = latents(), indicators = indicators(k);
        Graph impure = pureGraph(k, latents, indicators);
        impure.addDirectedEdge(indicators.get(from), indicators.get(to));
        SemIm truth = strongLoadings(impure);
        truth.setEdgeCoef(indicators.get(from), indicators.get(to), 0.6);
        DataSet data = truth.simulateData(5000, false);

        SemIm fit = new SemEstimator(data, new SemPm(pureGraph(k, latents(), indicators(k)))).estimate();
        CovarianceMatrix residual = ResidualCovariance.residual(fit);
        assertTrue(MatrixUtils.isPositiveDefinite(residual.getMatrix()));
        Matrix corr = MatrixUtils.convertCovToCorr(residual.getMatrix());

        List<Node> measured = fit.getMeasuredNodes();
        int a = -1, b = -1;
        for (int i = 0; i < measured.size(); i++) {
            if (measured.get(i).getName().equals("X" + (from + 1))) a = i;
            if (measured.get(i).getName().equals("X" + (to + 1))) b = i;
        }
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return new double[]{Math.abs(corr.get(lo, hi)), maxOffDiagonal(corr, lo, hi)};
    }

    private static double maxOffDiagonal(Matrix corr, int skipI, int skipJ) {
        double max = 0;
        for (int i = 0; i < corr.getNumRows(); i++) {
            for (int j = i + 1; j < corr.getNumRows(); j++) {
                if (i == skipI && j == skipJ) continue;
                max = Math.max(max, Math.abs(corr.get(i, j)));
            }
        }
        return max;
    }

    /**
     * Pure data, pure model: the residual correlations are at the level of sampling noise among error terms
     * (with loadings 0.9 and error variances 0.5 that noise is about 0.07 at n = 5000, so a maximum over 45 pairs
     * near 0.14 is expected), and the diagonal is the error variances.
     */
    @Test
    public void testPureModelLeavesNearDiagonalResidual() throws Exception {
        RandomUtil.getInstance().setSeed(1001L);
        List<Node> latents = latents(), indicators = indicators(5);
        Graph g = pureGraph(5, latents, indicators);
        SemIm truth = strongLoadings(g);
        DataSet data = truth.simulateData(5000, false);

        SemIm fit = new SemEstimator(data, new SemPm(g)).estimate();
        CovarianceMatrix residual = ResidualCovariance.residual(fit);
        Matrix corr = MatrixUtils.convertCovToCorr(residual.getMatrix());

        assertTrue("Pure model: residual correlations should be at noise level, max was "
                   + maxOffDiagonal(corr, -1, -1), maxOffDiagonal(corr, -1, -1) < 0.2);

        List<Node> measured = fit.getMeasuredNodes();
        for (int i = 0; i < measured.size(); i++) {
            assertEquals("Diagonal is the error variance at a fit that reproduces the variances.",
                    fit.getErrVar(measured.get(i)), residual.getMatrix().get(i, i), 0.05);
        }
        assertTrue(MatrixUtils.isPositiveDefinite(residual.getMatrix()));
    }

    /**
     * An impurity X1 -> X2 inside a cluster of ten, which the fitted pure model lacks: the pair's residual
     * correlation stands well above every other pair's, which is what a search on the residual is for.
     */
    @Test
    public void testImpurityInLargeClusterShowsInResidual() throws Exception {
        double[] s = impurityContrast(10, 0, 1, 1002L);
        assertTrue("Ten per cluster: the impure pair (" + s[0] + ") must stand well above the rest (" + s[1] + ").",
                s[0] > 0.25 && s[0] > 2.0 * s[1]);
    }

    /**
     * The same impurity inside a cluster of five is absorbed into the two loadings and error variances by the
     * maximum-likelihood fit, and the residual does not localize it: the pair sits below the noise of the other
     * pairs. This pins a limit of the method, documented in {@link ResidualCovariance}; if an estimator change
     * made small clusters localize, this test would fail and the documentation should be revised.
     */
    @Test
    public void testImpurityInSmallClusterIsAbsorbedByTheFit() throws Exception {
        double[] s = impurityContrast(5, 0, 1, 1002L);
        assertTrue("Five per cluster: the impure pair (" + s[0] + ") is expected not to stand out from the rest ("
                   + s[1] + ").", s[0] < 1.5 * s[1]);
    }

    /**
     * An impurity between clusters, with ten indicators per cluster, is the largest residual by a clear margin
     * (with five per cluster it is in about seven seeds of ten, since the edge also distorts the fitted latent
     * covariance and spills into the other cross-cluster pairs; that case is documented, not pinned).
     */
    @Test
    public void testCrossClusterImpurityShowsInResidualForLargeClusters() throws Exception {
        double[] s = impurityContrast(10, 0, 10, 1003L);
        assertTrue("Cross-cluster, ten per cluster: the impure pair (" + s[0] + ") must exceed the rest (" + s[1]
                   + ").", s[0] > 0.4 && s[0] > 1.3 * s[1]);
    }

    /**
     * The ridge: a symmetric matrix with a negative eigenvalue comes back positive definite with every variance
     * raised by the same amount, and a well-conditioned one comes back as itself.
     */
    @Test
    public void testRidgeOnIndefiniteResidual() {
        List<Node> names = new ArrayList<>();
        for (String n : new String[]{"A", "B", "C"}) names.add(new GraphNode(n));
        Matrix bad = new Matrix(new double[][]{{1.0, 0.99, 0.99}, {0.99, 1.0, -0.99}, {0.99, -0.99, 1.0}});
        assertTrue(!MatrixUtils.isPositiveDefinite(bad));
        Matrix fixed = ResidualCovariance.ridgeIfIndefinite(bad, names);
        assertTrue(MatrixUtils.isPositiveDefinite(fixed));
        assertEquals("Off-diagonals are untouched by the ridge.", 0.99, fixed.get(0, 1), 1e-12);
        assertEquals("Every variance is raised by the same ridge.", fixed.get(0, 0) - 1.0, fixed.get(2, 2) - 1.0,
                1e-12);

        Matrix good = new Matrix(new double[][]{{1.0, 0.3}, {0.3, 1.0}});
        assertTrue(ResidualCovariance.ridgeIfIndefinite(good, names.subList(0, 2)) == good);
    }
}
