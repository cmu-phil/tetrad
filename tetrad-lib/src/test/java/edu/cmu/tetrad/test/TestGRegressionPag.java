/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.GRegression;
import edu.cmu.tetrad.search.GRegressionPag;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link GRegressionPag}, the PAG-case counterpart of {@link GRegression}.
 * <p>
 * The tests are organized around the claims in that class's documentation. The first two are the ones that would
 * catch a wrong idea rather than a wrong line of code: on a DAG the estimator must reproduce G-regression, and at
 * the population covariance of a model with latent confounders it must recover every amenable total effect
 * exactly. The adjustment-invariance test checks the property the efficiency argument rests on.
 *
 * @author josephramsey
 */
public class TestGRegressionPag {

    /**
     * Undirected edges in a PAG encode selection bias, which the RICF fit does not model, so they must be
     * refused rather than silently ignored.
     */
    @Test
    public void testRefusesSelectionBias() {
        Graph g = new EdgeListGraph(List.of(new GraphNode("X1"), new GraphNode("X2")));
        g.addUndirectedEdge(g.getNode("X1"), g.getNode("X2"));
        assertNotNull(GRegressionPag.pagProblem(g));

        try {
            new GRegressionPag(g, new CovarianceMatrix(g.getNodes(), Matrix.identity(2), 100));
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * A DAG is a MAG with no bidirected edges, and under the causally sufficient reading (every directed edge
     * taken as visible) the adjustment functional evaluated at the RICF fit must agree with G-regression, which
     * computes the same quantity in closed form. RICF converges in one sweep here, so agreement should be to
     * near machine precision; a failure would mean the two routes do not in fact compute the same functional.
     */
    @Test
    public void testDagAgreesWithGRegression() {
        RandomUtil.getInstance().setSeed(4820913L);
        int checked = 0;

        for (int rep = 0; rep < 10; rep++) {
            Graph dag = RandomGraph.randomDag(10, 0, 18, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            List<Node> vars = im.getVariableNodes();
            ICovarianceMatrix cov = new CovarianceMatrix(vars, im.getImplCovar(vars), 1000);

            GRegression greg = new GRegression(dag, cov);
            GRegressionPag gpag = new GRegressionPag(dag, cov);
            gpag.setForceVisibility(new HashSet<>(gpag.getVariableOrder()));

            for (Node x : vars) {
                for (Node y : vars) {
                    if (x.equals(y)) continue;

                    double expected = greg.totalEffect(x, y);
                    double actual = gpag.totalEffect(x, y);

                    assertEquals("rep " + rep + " " + x + " -> " + y, expected, actual, 1e-7);
                    checked++;
                }
            }
        }

        assertTrue("Too few effects checked: " + checked, checked > 500);
    }

    /**
     * With the population covariance of a linear SEM whose latent variables have been marginalized out, the
     * estimator must recover every amenable total effect exactly. This is the test that distinguishes the
     * estimator implemented here from a path-product reading of the fitted MAG, which gets these wrong whenever
     * an edge on the causal path is invisible.
     */
    @Test
    public void testLatentPopulationRecovery() {
        RandomUtil.getInstance().setSeed(771231L);
        int checked = 0;

        for (int rep = 0; rep < 12; rep++) {
            Graph dag = RandomGraph.randomDag(12, 3, 20, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            List<Node> all = im.getVariableNodes();
            List<Node> measured = im.getMeasuredNodes();
            Matrix total = Matrix.identity(all.size()).minus(new Matrix(im.getEdgeCoef())).inverse();

            Graph pag = GraphTransforms.dagToPag(dag, false);
            if (GRegressionPag.pagProblem(pag) != null) continue;

            ICovarianceMatrix cov = new CovarianceMatrix(measured, im.getImplCovar(measured), 1000);
            GRegressionPag gpag = new GRegressionPag(pag, cov);

            assertTrue("RICF did not converge at the population covariance", gpag.converged());

            for (Node x : measured) {
                for (Node y : measured) {
                    if (x.equals(y)) continue;
                    if (!gpag.isPossibleAncestor(x, y)) continue;
                    if (!gpag.isAmenable(x, y)) continue;
                    if (gpag.adjustmentSet(x, y) == null) continue;

                    double truth = total.get(all.indexOf(dag.getNode(x.getName())),
                            all.indexOf(dag.getNode(y.getName())));

                    assertEquals("rep " + rep + " " + x + " -> " + y, truth, gpag.totalEffect(x, y), 1e-7);
                    checked++;
                }
            }
        }

        assertTrue("Too few amenable effects checked: " + checked, checked > 20);
    }

    /**
     * The estimate must not depend on which valid adjustment set is used, since the fitted Sigma-hat lies in the
     * model and every valid set is valid throughout the model. This is checked here at a finite sample, where
     * the corresponding spread for adjustment on the sample covariance is generally nonzero; the contrast is
     * printed.
     */
    @Test
    public void testAdjustmentSetInvariance() throws Exception {
        RandomUtil.getInstance().setSeed(99231L);
        Graph dag = RandomGraph.randomDag(12, 3, 20, 100, 100, 100, false);
        SemIm im = new SemIm(new SemPm(dag));
        DataSet data = im.simulateData(5000, false);
        Graph pag = GraphTransforms.dagToPag(dag, false);

        GRegressionPag gpag = new GRegressionPag(pag, new CovarianceMatrix(data));
        double worst = 0.0;
        int checked = 0;

        for (Node x : pag.getNodes()) {
            for (Node y : pag.getNodes()) {
                if (x.equals(y)) continue;
                if (!gpag.isPossibleAncestor(x, y) || !gpag.isAmenable(x, y)) continue;
                if (gpag.adjustmentSets(x, y, 5).size() < 2) continue;

                worst = Math.max(worst, gpag.adjustmentSpread(x, y, 5));
                checked++;
            }
        }

        System.out.printf(Locale.ROOT, "Pairs with 2+ adjustment sets: %d, worst spread at Sigma-hat: %.3g%n",
                checked, worst);

        assertTrue("Estimates differ across valid adjustment sets: " + worst, worst < 1e-6);
    }

    /**
     * Finite-sample sanity check and an informal efficiency comparison: the same adjustment set evaluated at
     * Sigma-hat versus at the sample covariance. Both are consistent, so this asserts only that both are close
     * to the truth and prints the root-mean-square errors. For a real comparison run
     * {@code PagSigmaHatHarness}, which is built for it; this is deliberately small so the test stays fast.
     */
    @Test
    public void testFiniteSampleComparison() throws Exception {
        RandomUtil.getInstance().setSeed(50505L);
        Graph dag = RandomGraph.randomDag(10, 2, 16, 100, 100, 100, false);
        SemIm im = new SemIm(new SemPm(dag));
        List<Node> all = im.getVariableNodes();
        Matrix total = Matrix.identity(all.size()).minus(new Matrix(im.getEdgeCoef())).inverse();
        Graph pag = GraphTransforms.dagToPag(dag, false);

        // Find one amenable pair with a nonempty adjustment set, at the population covariance.
        List<Node> measured = im.getMeasuredNodes();
        ICovarianceMatrix popCov = new CovarianceMatrix(measured, im.getImplCovar(measured), 1000);
        GRegressionPag probe = new GRegressionPag(pag, popCov);

        Node treatment = null, outcome = null;

        for (Node x : measured) {
            for (Node y : measured) {
                if (x.equals(y) || !probe.isPossibleAncestor(x, y) || !probe.isAmenable(x, y)) continue;
                Set<Node> z = probe.adjustmentSet(x, y);
                if (z == null || z.isEmpty()) continue;
                treatment = x;
                outcome = y;
                break;
            }
            if (treatment != null) break;
        }

        if (treatment == null) return;   // nothing to compare in this draw

        double truth = total.get(all.indexOf(dag.getNode(treatment.getName())),
                all.indexOf(dag.getNode(outcome.getName())));

        List<Double> fitted = new ArrayList<>();
        List<Double> plain = new ArrayList<>();

        for (int rep = 0; rep < 20; rep++) {
            DataSet data = im.simulateData(1000, false);
            GRegressionPag gpag = new GRegressionPag(pag, new CovarianceMatrix(data));
            fitted.add(gpag.totalEffect(treatment, outcome));
            plain.add(gpag.totalEffectSampleCov(treatment, outcome));
        }

        double rmseFitted = rmse(fitted, truth);
        double rmsePlain = rmse(plain, truth);

        System.out.printf(Locale.ROOT, "%s -> %s, truth %.4f: RMSE at Sigma-hat %.4f, at S %.4f (ratio %.3f)%n",
                treatment, outcome, truth, rmseFitted, rmsePlain, rmseFitted / rmsePlain);

        assertTrue("Sigma-hat estimator is far from the truth: " + rmseFitted, rmseFitted < 0.3);
        assertTrue("Adjustment estimator is far from the truth: " + rmsePlain, rmsePlain < 0.3);
    }

    /**
     * A non-amenable pair must be reported, not estimated, and the witness path must start at the treatment and
     * end at the outcome.
     */
    @Test
    public void testNonAmenableIsReported() {
        RandomUtil.getInstance().setSeed(31337L);

        for (int rep = 0; rep < 20; rep++) {
            Graph dag = RandomGraph.randomDag(10, 3, 16, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            List<Node> measured = im.getMeasuredNodes();
            Graph pag = GraphTransforms.dagToPag(dag, false);
            if (GRegressionPag.pagProblem(pag) != null) continue;

            GRegressionPag gpag = new GRegressionPag(pag,
                    new CovarianceMatrix(measured, im.getImplCovar(measured), 1000));

            for (Node x : pag.getNodes()) {
                for (Node y : pag.getNodes()) {
                    if (x.equals(y) || !gpag.isPossibleAncestor(x, y)) continue;

                    if (gpag.isAmenable(x, y)) {
                        assertNull(gpag.nonAmenabilityWitness(x, y));
                        continue;
                    }

                    List<Node> witness = gpag.nonAmenabilityWitness(x, y);

                    if (witness != null) {
                        assertEquals(x, witness.getFirst());
                        assertEquals(y, witness.getLast());
                    }

                    try {
                        gpag.totalEffect(x, y);
                        throw new AssertionError("Expected a non-amenability exception for " + x + " -> " + y);
                    } catch (IllegalArgumentException e) {
                        // expected
                    }

                    return;
                }
            }
        }
    }

    private static double rmse(List<Double> values, double truth) {
        double s = 0.0;
        for (double v : values) s += (v - truth) * (v - truth);
        return Math.sqrt(s / values.size());
    }
}
