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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.work_in_progress.MagOrderProjection;
import edu.cmu.tetrad.search.work_in_progress.MagRicfBicScorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Oracle tests for {@link MagOrderProjection}, LV-BOSS stage 1: project a true causal order to the
 * best-scoring ancestral graph, working from the POPULATION covariance (the SemIm's implied measured
 * covariance at a large nominal sample size), so what is tested is the procedure, not sampling noise.
 *
 * <p>Status of the claims, stated plainly. On DAG-generated data at a true order, recovery of the
 * exact DAG is asserted for every replication (the directed-only first phase is BOSS's grow-shrink).
 * On latent projections, two things are asserted: the output never scores below the true MAG (no
 * search failure), and the true MAG's Markov equivalence class is recovered in at least a stated
 * fraction of replications. The fraction is an empirical floor, not a theorem: in the remaining
 * replications the projection returns a SPARSER ancestral graph that the BIC genuinely prefers,
 * because the latent projection's distribution satisfies constraints beyond its MAG's independence
 * constraints (rank-type constraints), so the true MAG is not always the BIC-optimal ancestral graph
 * even in the population. Those outputs score at or above the truth and are counted, not hidden.</p>
 *
 * @author josephramsey
 */
public class TestMagOrderProjection {

    /**
     * DAG-generated data, true order, population covariance: the projection must return the true DAG
     * exactly, every time. The directed-only first phase is BOSS's grow-shrink projection; the later
     * typed phases must then find no improving bidirected move.
     */
    @Test
    public void dagTrueOrderRecoversTrueDagExactly() throws Exception {
        RandomUtil.getInstance().setSeed(31418596L);

        for (int rep = 0; rep < 8; rep++) {
            Graph dag = RandomGraph.randomGraph(7, 0, 10, 100, 100, 100, false);

            Fixture f = fixture(dag, 0.3, 1_000_000);
            Graph out = f.projection.project(f.order);

            Graph truth = GraphUtils.replaceNodes(dag, out.getNodes());
            assertEquals("rep " + rep, new ArrayList<>(truth.getEdges()).size(), out.getNumEdges());
            for (Edge e : truth.getEdges()) {
                assertEquals("rep " + rep + ", edge " + e, e, out.getEdge(out.getNode(e.getNode1().getName()),
                        out.getNode(e.getNode2().getName())));
            }
        }
    }

    /**
     * Latent projections, true order, population covariance. Asserted for every replication: the
     * output never scores below the true MAG (zero search failures). Asserted in aggregate: the true
     * class is recovered in at least 9 of 12 replications (empirical floor; see the class Javadoc for
     * why 12 of 12 would be too strong a claim).
     */
    @Test
    public void latentTrueOrderOracle() throws Exception {
        RandomUtil.getInstance().setSeed(27182818L);

        int classMatches = 0;

        for (int rep = 0; rep < 12; rep++) {
            Graph dagL = RandomGraph.randomGraph(7, 2, 10, 100, 100, 100, false);
            Graph trueMag = GraphTransforms.dagToMag(dagL);
            if (trueMag.getNumEdges() == 0) { classMatches++; continue; }

            Fixture f = fixture(dagL, 0.3, 1_000_000);
            Graph out = f.projection.project(f.order);

            double outScore = f.scorer.score(out);
            double trueScore = f.scorer.score(f.trueMagOverCov);

            assertTrue("rep " + rep + ": search failure, output scored " + (trueScore - outScore)
                       + " below the true MAG", outScore >= trueScore - 1e-6);

            if (out.paths().isLegalMag()) {
                Graph truePag = new MagToPag(f.trueMagOverCov).convert(false, true);
                Graph outPag = GraphUtils.replaceNodes(new MagToPag(out).convert(false, true), truePag.getNodes());
                if (outPag.equals(truePag)) classMatches++;
            }
        }

        assertTrue("Class recovered in only " + classMatches + " of 12 replications.", classMatches >= 9);
    }

    /**
     * The plateau escape earns its keep: with it effectively disabled (one plateau state), the same
     * replication stream produces at least one search failure that the default configuration does not.
     */
    @Test
    public void plateauEscapeRescuesLocalOptima() throws Exception {
        int failuresDisabled = countSearchFailures(true);
        int failuresDefault = countSearchFailures(false);

        assertEquals("Default configuration should have no search failures.", 0, failuresDefault);
        assertTrue("Disabling the plateau escape should cost at least one search failure; the test "
                   + "stream no longer exercises it.", failuresDisabled >= 1);
    }

    private int countSearchFailures(boolean disableEscape) throws Exception {
        RandomUtil.getInstance().setSeed(16180339L);
        int failures = 0;

        for (int rep = 0; rep < 12; rep++) {
            Graph dagL = RandomGraph.randomGraph(7, 2, 11, 100, 100, 100, false);
            Graph trueMag = GraphTransforms.dagToMag(dagL);
            if (trueMag.getNumEdges() == 0) continue;

            Fixture f = fixture(dagL, 0.3, 1_000_000);
            if (disableEscape) f.projection.setMaxPlateauStates(1);

            Graph out = f.projection.project(f.order);

            if (f.scorer.score(out) < f.scorer.score(f.trueMagOverCov) - 1e-6) failures++;
        }

        return failures;
    }

    /**
     * Determinism: the same order projects to the identical graph twice.
     */
    @Test
    public void projectionIsDeterministic() throws Exception {
        RandomUtil.getInstance().setSeed(14142135L);

        Graph dagL = RandomGraph.randomGraph(7, 2, 11, 100, 100, 100, false);

        Fixture f = fixture(dagL, 0.3, 1_000_000);
        Graph out1 = f.projection.project(f.order);
        Graph out2 = f.projection.project(f.order);

        assertEquals(out1.getNumEdges(), out2.getNumEdges());
        for (Edge e : out1.getEdges()) {
            assertEquals(e, out2.getEdge(out2.getNode(e.getNode1().getName()), out2.getNode(e.getNode2().getName())));
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private record Fixture(MagRicfBicScorer scorer, MagOrderProjection projection, List<Node> order,
                           Graph trueMagOverCov) {
    }

    /**
     * Builds the population-covariance fixture for a (possibly latent) DAG: the implied measured
     * covariance at the nominal sample size, the scorer and projection over it, an order consistent
     * with the latent projection's MAG, and that MAG over the covariance variables.
     */
    private static Fixture fixture(Graph dagPossiblyLatent, double coefLow, int nominalN) {
        Parameters params = new Parameters();
        params.set(Params.COEF_LOW, coefLow);
        params.set(Params.COEF_HIGH, 1.0);
        SemIm im = new SemIm(new SemPm(dagPossiblyLatent), params);

        Matrix implCovMeas = im.getImplCovarMeas();
        List<Node> covVars = new ArrayList<>();
        for (Node n : im.getMeasuredNodes()) covVars.add(new ContinuousVariable(n.getName()));
        ICovarianceMatrix cov = new CovarianceMatrix(covVars, implCovMeas, nominalN);

        Graph trueMag = GraphTransforms.dagToMag(dagPossiblyLatent);
        Graph magOverCov = GraphUtils.replaceNodes(trueMag, covVars);
        List<Node> order = magOverCov.paths().getValidOrderMag(magOverCov.getNodes(), true);

        MagRicfBicScorer scorer = new MagRicfBicScorer(cov);
        return new Fixture(scorer, new MagOrderProjection(scorer), order, magOverCov);
    }
}
