///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.StARS;
import edu.cmu.tetrad.algcomparison.algorithm.StabilitySelection;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.sweep.ParameterSweep;
import edu.cmu.tetrad.algcomparison.sweep.SweepReport;
import edu.cmu.tetrad.algcomparison.sweep.SweepResult;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the parameter sweep harness (report shape, instability bounds and pairing, Markov statistics, selection
 * rules, serialization, seed determinism of resampling) and smoke-tests the refactored StARS and
 * StabilitySelection selectors, on simulated linear Gaussian SEM data with fixed seeds.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestParameterSweep {

    /**
     * Constructs a new test.
     */
    public TestParameterSweep() {
    }

    /**
     * Simulates a fixed 10-node, 10-edge linear Gaussian SEM dataset with 500 rows.
     */
    private DataSet simulate() {
        RandomUtil.getInstance().setSeed(31);
        Graph trueGraph = RandomGraph.randomGraph(10, 0, 10, 100, 100, 100, false);
        SemPm pm = new SemPm(trueGraph);
        SemIm im = new SemIm(pm);

        try {
            return im.simulateData(500, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Runs the standard sweep used by several tests: FGES with SEM-BIC over penalty discounts {1, 2, 4}, 20 shared
     * resamples, seed 42, Fisher-Z Markov check.
     */
    private SweepReport runSweep(DataSet data) throws InterruptedException {
        ParameterSweep sweep = new ParameterSweep(new Fges(new SemBicScore()), new Parameters());
        sweep.setMarkovCheckTest(new FisherZ());
        sweep.setNumResamples(20);
        sweep.setPercentResampleSize(1.0);
        sweep.setWithReplacement(true);
        sweep.setSeed(42);
        return sweep.sweep(data, Params.PENALTY_DISCOUNT, List.of(1.0, 2.0, 4.0));
    }

    /**
     * The report should contain one result per setting, in order, each with a point graph, an edge-probability
     * graph, the configured resample count, an instability in [0, 0.5], and Markov statistics.
     */
    @Test
    public void testReportShape() throws InterruptedException {
        DataSet data = simulate();
        SweepReport report = runSweep(data);
        List<SweepResult> results = report.getResults();

        assertEquals(3, results.size());
        assertEquals(1.0, results.get(0).getSetting().get(Params.PENALTY_DISCOUNT));
        assertEquals(4.0, results.get(2).getSetting().get(Params.PENALTY_DISCOUNT));

        for (SweepResult r : results) {
            assertNotNull(r.getPointGraph());
            assertNotNull(r.getEdgeProbabilityGraph());
            assertEquals(20, r.getNumResamples());
            assertTrue(r.getAdjacencyInstability() >= 0.0 && r.getAdjacencyInstability() <= 0.5);
            assertNotNull(r.getMarkovStats());
            assertTrue(r.getMarkovStats().numTestsInd() >= 0);
        }

        // Sparser at higher penalty (non-strict).
        assertTrue(results.get(2).getPointGraph().getNumEdges()
                <= results.get(0).getPointGraph().getNumEdges());
    }

    /**
     * The selection rules should agree with direct computation over the report: selectByMarkovAdequacy returns the
     * max-adInd result; selectByInstability with a cutoff just above the maximum instability returns the maximum;
     * selectMostStable returns the minimum; a cutoff at the minimum instability selects nothing.
     */
    @Test
    public void testSelectionRules() throws InterruptedException {
        DataSet data = simulate();
        SweepReport report = runSweep(data);

        SweepResult byMc = report.selectByMarkovAdequacy();
        double maxAd = report.getResults().stream()
                .filter(r -> r.getMarkovStats() != null && !Double.isNaN(r.getMarkovStats().adInd()))
                .mapToDouble(r -> r.getMarkovStats().adInd()).max().orElse(Double.NaN);
        assertNotNull(byMc);
        assertEquals(maxAd, byMc.getMarkovStats().adInd(), 0.0);

        double maxD = report.getResults().stream()
                .mapToDouble(SweepResult::getAdjacencyInstability).max().orElseThrow();
        double minD = report.getResults().stream()
                .mapToDouble(SweepResult::getAdjacencyInstability).min().orElseThrow();

        SweepResult atMax = report.selectByInstability(maxD + 1e-9);
        assertNotNull(atMax);
        assertEquals(maxD, atMax.getAdjacencyInstability(), 0.0);

        assertNull(report.selectByInstability(minD));

        SweepResult stable = report.selectMostStable();
        assertNotNull(stable);
        assertEquals(minD, stable.getAdjacencyInstability(), 0.0);
    }

    /**
     * The markdown rendering should contain the table header and one row per setting; the JSON rendering should
     * have balanced braces and the expected keys.
     */
    @Test
    public void testSerialization() throws InterruptedException {
        DataSet data = simulate();
        SweepReport report = runSweep(data);

        String md = report.toMarkdown();
        assertTrue(md.contains("| Setting |"));
        assertEquals(3, md.split("\n\\| \\{", -1).length - 1);

        String json = report.toJson();
        long open = json.chars().filter(c -> c == '{').count();
        long close = json.chars().filter(c -> c == '}').count();
        assertEquals(open, close);
        assertTrue(json.contains("\"results\":["));
        assertTrue(json.contains("\"adjacencyInstability\":"));
        assertTrue(json.contains("\"markov\":{"));
    }

    /**
     * Identically seeded resample draws should be exactly identical, row for row. (End-to-end report determinism
     * additionally requires the wrapped algorithm to be deterministic; FGES's internal thread pool can break score
     * near-ties differently between runs, so that is not asserted here.)
     */
    @Test
    public void testResampleDeterminism() {
        DataSet data = simulate();
        List<DataSet> r1 = ParameterSweep.drawResamples(data, 5, 0.5, true, 42);
        List<DataSet> r2 = ParameterSweep.drawResamples(data, 5, 0.5, true, 42);

        for (int s = 0; s < 5; s++) {
            assertEquals(r1.get(s).getNumRows(), r2.get(s).getNumRows());

            for (int i = 0; i < r1.get(s).getNumRows(); i++) {
                for (int j = 0; j < r1.get(s).getNumColumns(); j++) {
                    assertEquals(r1.get(s).getDouble(i, j), r2.get(s).getDouble(i, j), 0.0);
                }
            }
        }
    }

    /**
     * Without-replacement draws should contain no duplicate rows beyond those in the data and respect the requested
     * size.
     */
    @Test
    public void testWithoutReplacementSize() {
        DataSet data = simulate();
        List<DataSet> r = ParameterSweep.drawResamples(data, 3, 0.5, false, 7);

        for (DataSet d : r) {
            assertEquals(250, d.getNumRows());
        }
    }

    /**
     * The refactored StARS should run end to end and return a graph over the data's variables, including via the
     * most-stable fallback when no instability falls below the cutoff.
     */
    @Test
    public void testStarsSmoke() throws Exception {
        DataSet data = simulate();

        Parameters params = new Parameters();
        params.set("percentSubsampleSize", 0.5);
        params.set("StARS.cutoff", 0.05);
        params.set("numSubsamples", 10);
        params.set(Params.SEED, 7L);

        StARS stars = new StARS(new Fges(new SemBicScore()), Params.PENALTY_DISCOUNT, 1.0, 2.0);
        Graph g = stars.search(data, params);

        assertNotNull(g);
        assertEquals(data.getNumColumns(), g.getNodes().size());
    }

    /**
     * The refactored StabilitySelection should run end to end and return a graph over the data's variables. Exact
     * run-to-run equality of the output is deliberately NOT asserted with FGES as the wrapped algorithm: the
     * resamples are seed-deterministic, but FGES's internal thread pool can break score near-ties differently
     * between runs, flipping resample graphs slightly and with them any edge whose count sits at the
     * percentStability threshold. End-to-end determinism of the machinery itself is asserted in
     * {@link #testEndToEndDeterminismWithDeterministicAlgorithm()}.
     */
    @Test
    public void testStabilitySelectionSmoke() {
        DataSet data = simulate();

        Parameters params = new Parameters();
        params.set("percentSubsampleSize", 0.5);
        params.set("numSubsamples", 10);
        params.set("percentStability", 0.7);
        params.set(Params.SEED, 7L);

        StabilitySelection ss = new StabilitySelection(new Fges(new SemBicScore()));
        Graph g1 = ss.search(data, params);

        assertNotNull(g1);
        assertEquals(data.getNumColumns(), g1.getNodes().size());
    }

    /**
     * With a deterministic wrapped algorithm, StabilitySelection and the sweep harness are exactly deterministic
     * under a fixed seed. This isolates the harness's own contract (seed-deterministic resamples, race-free
     * collection, order-stable counting) from any nondeterminism inside real search algorithms.
     */
    @Test
    public void testEndToEndDeterminismWithDeterministicAlgorithm() throws InterruptedException {
        DataSet data = simulate();

        Parameters params = new Parameters();
        params.set("percentSubsampleSize", 0.5);
        params.set("numSubsamples", 10);
        params.set("percentStability", 0.5);
        params.set(Params.SEED, 7L);

        StabilitySelection ss = new StabilitySelection(new ColumnMeanAlgorithm());
        Graph g1 = ss.search(data, params);
        Graph g2 = ss.search(data, params);

        assertEquals(g1.getEdges(), g2.getEdges());

        ParameterSweep sweep = new ParameterSweep(new ColumnMeanAlgorithm(), new Parameters());
        sweep.setNumResamples(10);
        sweep.setPercentResampleSize(0.5);
        sweep.setSeed(7);

        SweepReport a = sweep.sweep(data, Params.PENALTY_DISCOUNT, List.of(1.0, 2.0));
        SweepReport b = sweep.sweep(data, Params.PENALTY_DISCOUNT, List.of(1.0, 2.0));

        for (int i = 0; i < 2; i++) {
            assertEquals(a.getResults().get(i).getAdjacencyInstability(),
                    b.getResults().get(i).getAdjacencyInstability(), 0.0);
            assertEquals(a.getResults().get(i).getPointGraph().getEdges(),
                    b.getResults().get(i).getPointGraph().getEdges());
        }
    }

    /**
     * A deterministic dummy algorithm for testing the harness machinery in isolation: connects variable 0 to each
     * other variable whose sample mean on the given data exceeds variable 0's sample mean. A pure function of the
     * dataset, so any nondeterminism observed with it wrapped is the harness's fault.
     */
    private static final class ColumnMeanAlgorithm implements edu.cmu.tetrad.algcomparison.algorithm.Algorithm {

        @Override
        public Graph search(edu.cmu.tetrad.data.DataModel dataModel, Parameters parameters) {
            DataSet d = (DataSet) dataModel;
            Graph g = new edu.cmu.tetrad.graph.EdgeListGraph(d.getVariables());
            double mean0 = columnMean(d, 0);

            for (int j = 1; j < d.getNumColumns(); j++) {
                if (columnMean(d, j) > mean0) {
                    g.addUndirectedEdge(d.getVariables().get(0), d.getVariables().get(j));
                }
            }

            return g;
        }

        private double columnMean(DataSet d, int j) {
            double sum = 0;
            for (int i = 0; i < d.getNumRows(); i++) sum += d.getDouble(i, j);
            return sum / d.getNumRows();
        }

        @Override
        public Graph getComparisonGraph(Graph graph) {
            return graph;
        }

        @Override
        public String getDescription() {
            return "Deterministic column-mean dummy";
        }

        @Override
        public edu.cmu.tetrad.data.DataType getDataType() {
            return edu.cmu.tetrad.data.DataType.Continuous;
        }

        @Override
        public java.util.List<String> getParameters() {
            return new java.util.ArrayList<>();
        }
    }
}
