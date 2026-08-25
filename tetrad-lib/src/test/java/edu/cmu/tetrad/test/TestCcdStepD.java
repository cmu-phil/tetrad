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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Regression tests for CCD Step D's depth semantics and parallelization.
 *
 * <p>(1) Depth caps EVERY issued conditioning set. Formerly the depth capped only the enumerated extension T of the
 * Step-D candidate sup-sepset B = Sepset(a,c) ∪ {b} ∪ T, so tests could condition on up to 2·depth + 1 variables
 * (observed in practice: conditioning sets of size 8 at depth 6). Now |B| ≤ depth for Step D, and Step F's
 * sup ∪ {d} tests are skipped when they would exceed depth.</p>
 *
 * <p>(2) The parallelized Step D produces output identical to the sequential path: same dotted underlines, same
 * graph.</p>
 */
public class TestCcdStepD {

    /**
     * Wraps a base test, recording the maximum conditioning-set size ever queried.
     */
    private static final class SizeRecordingTest implements IndependenceTest {
        private final IndependenceTest base;
        final AtomicInteger maxCond = new AtomicInteger(0);

        SizeRecordingTest(IndependenceTest base) {
            this.base = base;
        }

        @Override
        public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
            this.maxCond.accumulateAndGet(z.size(), Math::max);
            return this.base.checkIndependence(x, y, z);
        }

        @Override
        public List<Node> getVariables() {
            return this.base.getVariables();
        }

        @Override
        public edu.cmu.tetrad.data.DataModel getData() {
            return this.base.getData();
        }

        @Override
        public boolean isVerbose() {
            return this.base.isVerbose();
        }

        @Override
        public void setVerbose(boolean verbose) {
            this.base.setVerbose(verbose);
        }

        @Override
        public String toString() {
            return "SizeRecording(" + this.base + ")";
        }
    }

    /**
     * A dense-ish cyclic model that formerly provoked Step-D conditioning sets larger than depth.
     */
    private static ICovarianceMatrix simulate() throws Exception {
        RandomUtil.getInstance().setSeed(59205L);
        int p = 16;
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < p; i++) nodes.add(new GraphNode("X" + i));
        Graph g = new EdgeListGraph(nodes);
        // two 2-cycles with dedicated nonadjacent parents
        g.addDirectedEdge(nodes.get(0), nodes.get(1));
        g.addDirectedEdge(nodes.get(1), nodes.get(0));
        g.addDirectedEdge(nodes.get(2), nodes.get(3));
        g.addDirectedEdge(nodes.get(3), nodes.get(2));
        g.addDirectedEdge(nodes.get(4), nodes.get(0));
        g.addDirectedEdge(nodes.get(5), nodes.get(1));
        g.addDirectedEdge(nodes.get(6), nodes.get(2));
        g.addDirectedEdge(nodes.get(7), nodes.get(3));
        // extra acyclic density among the upper block
        Random rnd = new Random(11);
        int added = 0;
        while (added < 22) {
            int i = 4 + rnd.nextInt(p - 4), j = 4 + rnd.nextInt(p - 4);
            if (i == j) continue;
            int a = Math.min(i, j), b = Math.max(i, j);
            if (g.isAdjacentTo(nodes.get(a), nodes.get(b))) continue;
            g.addDirectedEdge(nodes.get(a), nodes.get(b));
            added++;
        }
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        im.setEdgeCoef(g.getNode("X0"), g.getNode("X1"), 0.6);
        im.setEdgeCoef(g.getNode("X1"), g.getNode("X0"), 0.5);
        im.setEdgeCoef(g.getNode("X2"), g.getNode("X3"), 0.6);
        im.setEdgeCoef(g.getNode("X3"), g.getNode("X2"), 0.5);
        DataSet data = im.simulateData(2000, false);
        return new CovarianceMatrix(data);
    }

    /**
     * No independence test may condition on more variables than depth.
     */
    @Test
    public void testDepthCapsAllConditioningSets() throws Exception {
        ICovarianceMatrix cov = simulate();
        for (int depth : new int[]{2, 3, 4}) {
            SizeRecordingTest test = new SizeRecordingTest(new IndTestFisherZ(cov, 0.01));
            Ccd ccd = new Ccd(test);
            ccd.setDepth(depth);
            ccd.search();
            assertTrue("depth " + depth + " but conditioned on " + test.maxCond.get(),
                    test.maxCond.get() <= depth);
        }
    }

    /**
     * The parallel Step D produces the same graph and the same dotted underlines as the sequential path.
     */
    @Test
    public void testParallelEqualsSequential() throws Exception {
        ICovarianceMatrix cov = simulate();

        Ccd seq = new Ccd(new IndTestFisherZ(cov, 0.01));
        seq.setDepth(4);
        Graph gSeq = seq.search();

        Ccd par = new Ccd(new IndTestFisherZ(cov, 0.01));
        par.setDepth(4);
        par.setParallelized(true);
        Graph gPar = par.search();

        assertEquals(gSeq, gPar);
        assertEquals(gSeq.getDottedUnderlines(), gPar.getDottedUnderlines());
        assertEquals(gSeq.getUnderLines(), gPar.getUnderLines());
    }
}
