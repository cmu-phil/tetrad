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

import edu.cmu.tetrad.algcomparison.algorithm.other.LcdNod;
import edu.cmu.tetrad.algcomparison.algorithm.other.Rlcd;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.harness.LcdNodPhase2Harness;
import edu.cmu.tetrad.search.lcdnod.LcdNodPhase2;
import edu.cmu.tetrad.search.lcdnod.OneFactorFit;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests of LCD-NOD: the per-domain one-factor fit, the Phase 2 change decisions on simulated nonstationary
 * one-factor data, and the top-level algorithm (stratified FOFC, then Phase 2) run from the data alone.
 *
 * @author josephramsey
 */
public class TestLcdNodPhase2 {

    /**
     * The one-factor fit recovers loadings, residual variances, and latent correlations on a large sample.
     */
    @Test
    public void testOneFactorFitRecovery() {
        Random rng = new Random(11);
        int[][] blocks = {{0, 1, 2}, {3, 4}, {5, 6, 7, 8}};
        int p = 9, k = 3, n = 20000;
        double[][] psi = {{1, .5, .3}, {.5, 1, .4}, {.3, .4, 1}};
        double[] lam = {1, .8, 1.2, 1, .9, 1, .7, 1.1, .6};
        double[] th = {.3, .4, .5, .2, .6, .3, .3, .4, .5};
        int[] blockOf = {0, 0, 0, 1, 1, 2, 2, 2, 2};

        double[][] chol = {{1, 0, 0}, {.5, Math.sqrt(.75), 0}, {.3, (.4 - .15) / Math.sqrt(.75), 0}};
        chol[2][2] = Math.sqrt(1 - chol[2][0] * chol[2][0] - chol[2][1] * chol[2][1]);

        double[][] data = new double[n][p];
        for (int i = 0; i < n; i++) {
            double[] z = new double[k];
            for (int j = 0; j < k; j++) z[j] = rng.nextGaussian();
            double[] eta = new double[k];
            for (int u = 0; u < k; u++) for (int v = 0; v <= u; v++) eta[u] += chol[u][v] * z[v];
            for (int a = 0; a < p; a++) data[i][a] = lam[a] * eta[blockOf[a]] + Math.sqrt(th[a]) * rng.nextGaussian();
        }
        SimpleMatrix s = new SimpleMatrix(p, p);
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                double sum = 0;
                for (int i = 0; i < n; i++) sum += data[i][a] * data[i][b];
                s.set(a, b, sum / (n - 1));
            }
        }

        OneFactorFit fit = OneFactorFit.fit(s, blocks);
        assertTrue(fit.isConverged());
        double[] loadings = fit.getLoadings();
        double[] uniques = fit.getUniques();
        SimpleMatrix r = fit.getLatentCorrelation();
        for (int a = 0; a < p; a++) {
            assertEquals("loading " + a, lam[a], loadings[a], 0.05);
            assertEquals("unique " + a, th[a], uniques[a], 0.05);
        }
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                assertEquals("corr " + i + "," + j, psi[i][j], r.get(i, j), 0.03);
            }
        }
        assertTrue("discrepancy should be near zero: " + fit.getDiscrepancy(), fit.getDiscrepancy() < 0.01);

        // Conditional variance helper against a direct formula.
        double[][] cov = {{2, 1, 0.5}, {1, 3, 0.2}, {0.5, 0.2, 1.5}};
        double direct = 2 - 1 * 1 / 3.0;
        assertEquals(direct, OneFactorFit.conditionalVariance(cov, 0, new int[]{1}), 1e-12);
        assertEquals(2.0, OneFactorFit.conditionalVariance(cov, 0, new int[]{}), 1e-12);
    }

    /**
     * With the bootstrap rule, the changing latent and the changing indicators are found and nothing else is.
     */
    @Test
    public void testChangesRecoveredWithBootstrapRule() throws InterruptedException {
        Random rng = new Random(2026);
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1500, 4, latentChanges,
                indicatorChanges, rng);

        LcdNodPhase2 phase2 = new LcdNodPhase2(sim.dataSet, "domain", sim.blocks, sim.latents);
        phase2.setNumBootstraps(100);
        phase2.setAlpha(0.05);
        phase2.setSeed(7);
        Graph graph = phase2.search();

        Node domain = graph.getNode("domain");
        assertNotNull(domain);
        List<Node> indicators = sim.indicators();
        for (int a = 0; a < indicators.size(); a++) {
            assertEquals("indicator " + indicators.get(a), indicatorChanges[a],
                    graph.isParentOf(domain, graph.getNode(indicators.get(a).getName())));
        }

        // The changing latent is found and the invariant sink L3 is not. L1 may be marked as well: the latent
        // CPDAG is complete, so a change in L2 given L1 and a change in L1 given L2 are competing one-change
        // explanations when L2's marginal variance happens not to move much; the report shows such a tie.
        assertTrue(graph.isParentOf(domain, graph.getNode("L2")));
        assertFalse(graph.isParentOf(domain, graph.getNode("L3")));
        LcdNodPhase2.Result result = phase2.getResult();
        assertFalse(result.minimalExplanations.isEmpty());
        for (List<Integer> explanation : result.minimalExplanations) {
            assertEquals("each minimal explanation has one changing latent", 1, explanation.size());
        }
        assertTrue(result.minimalExplanations.contains(List.of(1)));

        // Measurement edges are present, and the latents are connected (the true latent DAG is complete).
        for (int i = 0; i < sim.blocks.size(); i++) {
            for (int c : sim.blocks.get(i)) {
                assertTrue(graph.isParentOf(graph.getNode(sim.latents.get(i).getName()),
                        graph.getNode(sim.dataSet.getVariable(c).getName())));
            }
        }
        assertTrue(graph.isAdjacentTo(graph.getNode("L1"), graph.getNode("L2")));

        assertEquals(4, result.domainLabels.size());
        assertTrue(result.latentEvidence[1].decision.changes);
        assertTrue(result.report.contains("CHANGES"));
    }

    /**
     * With nothing changing, the bootstrap rule at alpha = 0.01 declares (almost) nothing.
     */
    @Test
    public void testNoChangesGivesNoEdges() throws InterruptedException {
        Random rng = new Random(99);
        boolean[] latentChanges = {false, false, false};
        boolean[] indicatorChanges = new boolean[12];
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1000, 3, latentChanges,
                indicatorChanges, rng);

        LcdNodPhase2 phase2 = new LcdNodPhase2(sim.dataSet, "domain", sim.blocks, sim.latents);
        phase2.setNumBootstraps(100);
        phase2.setAlpha(0.01);
        phase2.setSeed(3);
        Graph graph = phase2.search();

        Node domain = graph.getNode("domain");
        int edges = graph.getChildren(domain).size();
        assertTrue("expected at most one false change at alpha 0.01, got " + edges, edges <= 1);
    }

    /**
     * The fallback rule (Theorem 8, all subsets, used when the latent structure is not learned) runs and leaves the
     * latents unconnected.
     */
    @Test
    public void testSubsetRuleFallback() throws InterruptedException {
        Random rng = new Random(2026);
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1500, 4, latentChanges,
                indicatorChanges, rng);

        LcdNodPhase2 phase2 = new LcdNodPhase2(sim.dataSet, "domain", sim.blocks, sim.latents);
        phase2.setNumBootstraps(60);
        phase2.setSeed(7);
        phase2.setLearnLatentStructure(false);
        Graph graph = phase2.search();

        assertFalse(graph.isAdjacentTo(graph.getNode("L1"), graph.getNode("L2")));
        assertTrue(phase2.getResult().latentRule.contains("Theorem 8"));
        assertTrue(phase2.getResult().minimalExplanations.isEmpty());
        Node domain = graph.getNode("domain");
        assertTrue(graph.isParentOf(domain, graph.getNode("X3")));
        assertFalse(graph.isParentOf(domain, graph.getNode("L3")));
    }

    /**
     * The ratio rule runs without a bootstrap and finds the large indicator changes.
     */
    @Test
    public void testRatioRule() throws InterruptedException {
        Random rng = new Random(2026);
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1500, 4, latentChanges,
                indicatorChanges, rng);

        LcdNodPhase2 phase2 = new LcdNodPhase2(sim.dataSet, "domain", sim.blocks, sim.latents);
        phase2.setNumBootstraps(0);
        phase2.setChangeRatio(1.5);
        Graph graph = phase2.search();

        Node domain = graph.getNode("domain");
        assertTrue(graph.isParentOf(domain, graph.getNode("X3")));
        assertTrue(graph.isParentOf(domain, graph.getNode("X9")));
        assertFalse(graph.isParentOf(domain, graph.getNode("X1")));
        assertTrue(Double.isNaN(phase2.getResult().indicatorDecisions[0].pValue));
    }

    /**
     * The top-level algorithm runs from the data set alone: stratified RLCD finds the latent structure, and Phase 2,
     * on its one-factor part, finds the changing indicators and the changing latent. The domain column is discrete.
     */
    @Test
    public void testTopLevelAlgorithmFromDataAlone() {
        Random rng = new Random(5);
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1500, 4, latentChanges,
                indicatorChanges, rng);
        DataSet data = sim.dataSet;

        LcdNod alg = new LcdNod();
        Parameters parameters = new Parameters();
        parameters.set(Params.DOMAIN_COLUMN, "domain");
        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.LCD_NOD_BOOTSTRAPS, 60);
        parameters.set(Params.SEED, 11L);
        Graph graph = alg.search(data, parameters);

        assertNotNull(alg.getRlcdGraph());
        LcdNodPhase2.Result r = alg.getPhase2Result();
        assertNotNull(r);
        assertEquals("all three latents assessed", 3, r.latentChanges.length);
        assertEquals("all twelve indicators pure", 12, r.indicatorNames.size());

        Node domain = graph.getNode("domain");
        assertNotNull(domain);
        assertTrue(graph.isParentOf(domain, graph.getNode("X3")));
        assertTrue(graph.isParentOf(domain, graph.getNode("X9")));
        assertFalse(graph.isParentOf(domain, graph.getNode("X1")));

        // The latent measured by X5..X8 is marked as changing.
        Node l2 = graph.getParents(graph.getNode("X6")).stream()
                .filter(n -> !n.getName().equals("domain")).findFirst().orElseThrow();
        assertTrue(graph.isParentOf(domain, l2));
        assertTrue(alg.getReport().contains("stratified RLCD"));
        assertTrue(alg.getReport().contains("Domains (n)"));
    }

    /**
     * With missing values and the EM policy, the per-domain covariances come from EM within each domain, RLCD runs on
     * the pooled rank tests built from them, and Phase 2 runs with its parametric bootstrap; the clear indicator
     * changes are still found. Without a policy the run is refused with a message naming the options.
     */
    @Test
    public void testTopLevelWithMissingValuesAndEm() {
        Random rng = new Random(5);
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1500, 4, latentChanges,
                indicatorChanges, rng);
        DataSet data = sim.dataSet.copy();
        Random holes = new Random(3);
        for (int r = 0; r < data.getNumRows(); r++) {
            for (int c = 0; c < data.getNumColumns() - 1; c++) {
                if (holes.nextDouble() < 0.08) data.setDouble(r, c, Double.NaN);
            }
        }

        Parameters parameters = new Parameters();
        parameters.set(Params.DOMAIN_COLUMN, "domain");
        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.LCD_NOD_BOOTSTRAPS, 60);
        parameters.set(Params.SEED, 11L);

        try {
            new LcdNod().search(data, parameters);
            fail("missing values without a policy should be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missingDataPolicy"));
        }

        parameters.set(Params.MISSING_DATA_POLICY, "em");
        parameters.set(Params.MISSING_ESS_MODE, "meanPairwise");
        LcdNod alg = new LcdNod();
        Graph graph = alg.search(data, parameters);

        LcdNodPhase2.Result r = alg.getPhase2Result();
        assertNotNull(r);
        assertTrue(r.parametricBootstrap);
        assertTrue(r.report.contains("parametric bootstrap"));
        Node domain = graph.getNode("domain");
        assertTrue(graph.isParentOf(domain, graph.getNode("X3")));
        assertTrue(graph.isParentOf(domain, graph.getNode("X9")));
        assertTrue(alg.getReport().contains("ess"));
    }

    /**
     * Ceiling censoring within domains: values are clipped at a common ceiling, rlcdCensorCeiling is on, and the run
     * completes with Phase 2 on RLCD's one-factor part. The censored estimator is stochastic, so only completion and
     * the strongest indicator change are asserted.
     */
    @Test
    public void testTopLevelWithCensoring() {
        Random rng = new Random(5);
        boolean[] latentChanges = {false, true, false};
        boolean[] indicatorChanges = {false, false, true, false, false, false, false, false, true, false, false, false};
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1500, 3, latentChanges,
                indicatorChanges, rng);
        DataSet data = sim.dataSet.copy();
        // Standardize each indicator over the whole data set, then clip at a common ceiling (about the top tenth).
        int p = data.getNumColumns() - 1;
        for (int c = 0; c < p; c++) {
            double sum = 0, sumSq = 0;
            for (int r = 0; r < data.getNumRows(); r++) {
                double x = data.getDouble(r, c);
                sum += x;
                sumSq += x * x;
            }
            double mean = sum / data.getNumRows();
            double sd = Math.sqrt(sumSq / data.getNumRows() - mean * mean);
            for (int r = 0; r < data.getNumRows(); r++) data.setDouble(r, c, (data.getDouble(r, c) - mean) / sd);
        }
        double ceiling = 1.3;
        int clipped = 0;
        for (int r = 0; r < data.getNumRows(); r++) {
            for (int c = 0; c < p; c++) {
                if (data.getDouble(r, c) > ceiling) {
                    data.setDouble(r, c, ceiling);
                    clipped++;
                }
            }
        }
        assertTrue("the test needs some censoring to mean anything", clipped > 100);

        Parameters parameters = new Parameters();
        parameters.set(Params.DOMAIN_COLUMN, "domain");
        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.LCD_NOD_BOOTSTRAPS, 40);
        parameters.set(Params.SEED, 11L);
        parameters.set(Params.RLCD_CENSOR_CEILING, true);
        parameters.set(Params.RLCD_CEILING_VALUE, ceiling);

        LcdNod alg = new LcdNod();
        Graph graph = alg.search(data, parameters);
        assertNotNull(alg.getRlcdGraph());
        assertTrue(alg.getReport().contains("censored"));
        Node domain = graph.getNode("domain");
        assertNotNull(domain);
        if (alg.getPhase2Result() != null) {
            assertTrue(graph.isParentOf(domain, graph.getNode("X3"))
                       || graph.isParentOf(domain, graph.getNode("X9")));
        }
    }

    /**
     * The stratified RLCD wrapper itself now accepts the EM policy, building the pooled test from per-domain EM
     * covariances, and still refuses testwise deletion with stratification.
     */
    @Test
    public void testStratifiedRlcdWithEm() {
        Random rng = new Random(9);
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(1200, 3, new boolean[3],
                new boolean[12], rng);
        DataSet data = sim.dataSet;
        // Integer-coded continuous domain column, as RLCD requires.
        List<Node> vars = new ArrayList<>(data.getVariables());
        int dc = vars.size() - 1;
        double[][] m = new double[data.getNumRows()][vars.size()];
        Random holes = new Random(4);
        for (int r = 0; r < data.getNumRows(); r++) {
            for (int c = 0; c < vars.size(); c++) {
                m[r][c] = c == dc ? data.getInt(r, c)
                        : holes.nextDouble() < 0.05 ? Double.NaN : data.getDouble(r, c);
            }
        }
        vars.set(dc, new edu.cmu.tetrad.data.ContinuousVariable("domain"));
        DataSet cont = new edu.cmu.tetrad.data.BoxDataSet(new edu.cmu.tetrad.data.DoubleDataBox(m), vars);

        Parameters parameters = new Parameters();
        parameters.set(Params.STRATIFY_BY_DOMAIN, true);
        parameters.set(Params.DOMAIN_COLUMN, "domain");
        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.MISSING_DATA_POLICY, "testwise");
        try {
            new Rlcd().search(cont, parameters);
            fail("testwise with stratification should be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("testwise"));
        }

        parameters.set(Params.MISSING_DATA_POLICY, "em");
        Graph g = new Rlcd().search(cont, parameters);
        assertNotNull(g);
        int latents = 0;
        for (Node n : g.getNodes()) if (n.getNodeType() == edu.cmu.tetrad.graph.NodeType.LATENT) latents++;
        assertEquals(3, latents);
        assertNull(g.getNode("domain"));

        // The same with the discrete domain column: the mixed data set is accepted in stratified mode.
        DataSet mixed = sim.dataSet.copy();
        Random holes2 = new Random(4);
        for (int r = 0; r < mixed.getNumRows(); r++) {
            for (int c = 0; c < dc; c++) if (holes2.nextDouble() < 0.05) mixed.setDouble(r, c, Double.NaN);
        }
        assertTrue(mixed.isMixed());
        Graph g2 = new Rlcd().search(mixed, parameters);
        latents = 0;
        for (Node n : g2.getNodes()) if (n.getNodeType() == edu.cmu.tetrad.graph.NodeType.LATENT) latents++;
        assertEquals(3, latents);

        // Without stratification, a mixed data set is still refused.
        Parameters plain = new Parameters(parameters);
        plain.set(Params.STRATIFY_BY_DOMAIN, false);
        try {
            new Rlcd().search(mixed, plain);
            fail("mixed data without stratification should be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("stratifyByDomain"));
        }
    }

    /**
     * The top-level algorithm refuses a discrete column other than the domain column.
     */
    @Test
    public void testTopLevelRefusesOtherDiscreteColumns() {
        Random rng = new Random(6);
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(300, 2, new boolean[3],
                new boolean[12], rng);
        Parameters parameters = new Parameters();
        parameters.set(Params.DOMAIN_COLUMN, "X1");
        try {
            new LcdNod().search(sim.dataSet, parameters);
            fail("a discrete column other than the domain column should be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("discrete") || e.getMessage().contains("domain"));
        }
    }

    /**
     * Refusals: the domain column in a block, and too few domains.
     */
    @Test
    public void testRefusals() {
        Random rng = new Random(8);
        LcdNodPhase2Harness.Simulation sim = LcdNodPhase2Harness.simulate(300, 1, new boolean[3],
                new boolean[12], rng);

        List<List<Integer>> bad = new ArrayList<>(sim.blocks);
        bad.set(0, List.of(0, 1, 2, 3, 12));
        try {
            new LcdNodPhase2(sim.dataSet, "domain", bad, sim.latents);
            fail("domain column in a block should be refused");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domain"));
        }

        try {
            new LcdNodPhase2(sim.dataSet, "domain", sim.blocks, sim.latents).search();
            fail("a single domain should be refused");
        } catch (IllegalArgumentException | InterruptedException e) {
            assertTrue(e.getMessage().contains("two domains"));
        }
    }
}
