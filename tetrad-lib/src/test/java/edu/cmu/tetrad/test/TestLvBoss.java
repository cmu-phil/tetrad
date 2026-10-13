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
import edu.cmu.tetrad.search.work_in_progress.LvBoss;
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
 * Oracle tests for {@link LvBoss}, LV-BOSS stage 2: the BOSS-style order search with the MAG
 * projection inside, run from RANDOM starting orders against the population covariance (the SemIm's
 * implied measured covariance at a large nominal sample size).
 *
 * <p>Status of the claims, as in the stage-1 tests. What is asserted per replication is the invariant
 * the search needs: the output never scores below the true MAG. Class recovery is asserted only as an
 * empirical floor, because a latent projection's distribution can satisfy rank-type constraints that
 * let a sparser ancestral graph contain it with fewer parameters, in which case the BIC genuinely
 * prefers that graph, and on DAG data near-unfaithfulness from signed coefficients can do the same at
 * a given nominal sample size (the probe's one DAG miss at nominal n of a million resolved at a
 * billion).</p>
 *
 * @author josephramsey
 */
public class TestLvBoss {

    /**
     * DAG-generated data, random starting orders, nominal n of a billion (large enough that the
     * probe's near-tie artifacts resolve): the search must recover the true DAG's Markov equivalence
     * class in every replication.
     */
    @Test
    public void dagRandomStartsRecoverTrueClass() throws Exception {
        RandomUtil.getInstance().setSeed(60221409L);

        for (int rep = 0; rep < 5; rep++) {
            Graph dag = RandomGraph.randomGraph(6, 0, 8, 100, 100, 100, false);

            Fixture f = fixture(dag, 1_000_000_000);
            f.lvBoss.setSeed(500L + rep);
            Graph out = f.lvBoss.search();

            assertTrue("rep " + rep + ": not a legal MAG", out.paths().isLegalMag());

            Graph truePag = new MagToPag(f.trueMagOverCov).convert(false, true);
            Graph outPag = GraphUtils.replaceNodes(new MagToPag(out).convert(false, true), truePag.getNodes());
            assertEquals("rep " + rep, truePag, outPag);
        }
    }

    /**
     * Latent projections, random starting orders. Per replication: the output never scores below the
     * true MAG. Aggregate: the true class is recovered in at least 4 of 6 replications (empirical
     * floor; the misses, when they occur, are score-preferred sparser graphs, which the per-rep
     * assertion verifies).
     */
    @Test
    public void latentRandomStartsOracle() throws Exception {
        RandomUtil.getInstance().setSeed(66260701L);

        int classMatches = 0;

        for (int rep = 0; rep < 6; rep++) {
            Graph dagL = RandomGraph.randomGraph(7, 2, 10, 100, 100, 100, false);
            Graph trueMag = GraphTransforms.dagToMag(dagL);
            if (trueMag.getNumEdges() == 0) { classMatches++; continue; }

            Fixture f = fixture(dagL, 1_000_000);
            f.lvBoss.setSeed(900L + rep);
            Graph out = f.lvBoss.search();

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

        assertTrue("Class recovered in only " + classMatches + " of 6 replications.", classMatches >= 4);
    }

    /**
     * Determinism: the same seed gives the identical graph twice.
     */
    @Test
    public void searchIsDeterministic() throws Exception {
        RandomUtil.getInstance().setSeed(29979245L);

        Graph dagL = RandomGraph.randomGraph(6, 2, 9, 100, 100, 100, false);

        Fixture f = fixture(dagL, 1_000_000);
        f.lvBoss.setSeed(7L);
        Graph out1 = f.lvBoss.search();
        Graph out2 = f.lvBoss.search();

        assertEquals(out1.getNumEdges(), out2.getNumEdges());
        for (Edge e : out1.getEdges()) {
            assertEquals(e, out2.getEdge(out2.getNode(e.getNode1().getName()), out2.getNode(e.getNode2().getName())));
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private record Fixture(MagRicfBicScorer scorer, LvBoss lvBoss, Graph trueMagOverCov) {
    }

    private static Fixture fixture(Graph dagPossiblyLatent, int nominalN) {
        Parameters params = new Parameters();
        params.set(Params.COEF_LOW, 0.3);
        params.set(Params.COEF_HIGH, 1.0);
        SemIm im = new SemIm(new SemPm(dagPossiblyLatent), params);

        Matrix implCovMeas = im.getImplCovarMeas();
        List<Node> covVars = new ArrayList<>();
        for (Node n : im.getMeasuredNodes()) covVars.add(new ContinuousVariable(n.getName()));
        ICovarianceMatrix cov = new CovarianceMatrix(covVars, implCovMeas, nominalN);

        Graph trueMag = GraphTransforms.dagToMag(dagPossiblyLatent);
        Graph magOverCov = GraphUtils.replaceNodes(trueMag, covVars);

        MagRicfBicScorer scorer = new MagRicfBicScorer(cov);
        LvBoss lvBoss = new LvBoss(scorer);
        lvBoss.setNumStarts(2);

        return new Fixture(scorer, lvBoss, magOverCov);
    }
}
