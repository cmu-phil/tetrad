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
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Grasp;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests the optional order refinement step of PC: the PC result supplies a starting permutation for GRaSP, run with
 * the same independence test.
 */
public class TestPcOrderRefinement {

    private static ICovarianceMatrix simulate(Graph dag, long seed) {
        RandomUtil.getInstance().setSeed(seed);
        Parameters parameters = new Parameters();
        parameters.set(Params.COEF_LOW, 0.3);
        parameters.set(Params.COEF_HIGH, 1.0);
        SemIm im = new SemIm(new SemPm(dag), parameters);
        try {
            DataSet data = im.simulateData(1000, false);
            return new CovarianceMatrix(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int missingAdjacencies(Graph trueDag, Graph est) {
        int missing = 0;
        for (Edge e : trueDag.getEdges()) {
            Node a = est.getNode(e.getNode1().getName());
            Node b = est.getNode(e.getNode2().getName());
            if (!est.isAdjacentTo(a, b)) missing++;
        }
        return missing;
    }

    /**
     * GRaSP constructed from a test alone used to fail with a NullPointerException in TeyssierScorer.
     */
    @Test
    public void testGraspFromTestAlone() throws InterruptedException {
        RandomUtil.getInstance().setSeed(3841L);
        Graph dag = RandomGraph.randomGraph(6, 0, 6, 100, 100, 100, false);
        IndTestFisherZ test = new IndTestFisherZ(simulate(dag, 3842L), 0.01);

        Grasp grasp = new Grasp(test);
        List<Node> order = grasp.bestOrder(test.getVariables());

        assertEquals(6, order.size());
        assertNotNull(grasp.getGraph(true));
    }

    /**
     * Over a fixed set of 10-node, 10-edge linear Gaussian models, refinement should lose fewer true adjacencies than
     * plain PC, should leave the test's alpha as it found it, and should be off by default.
     */
    @Test
    public void testRefinementRecoversAdjacencies() throws InterruptedException {
        int missingPlain = 0, missingRefined = 0;

        for (int r = 0; r < 40; r++) {
            RandomUtil.getInstance().setSeed(9100L + r);
            Graph dag = RandomGraph.randomGraph(10, 0, 10, 100, 100, 100, false);
            ICovarianceMatrix cov = simulate(dag, 9200L + r);

            IndTestFisherZ test = new IndTestFisherZ(cov, 0.01);

            Pc plain = new Pc(test);
            plain.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
            Graph g1 = plain.search();

            Pc again = new Pc(test);
            again.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
            again.setOrderRefinement(false);
            assertEquals("Refinement must be off by default.", g1, again.search());

            Pc refined = new Pc(test);
            refined.setColliderOrientationStyle(Pc.ColliderOrientationStyle.MAX_P);
            refined.setOrderRefinement(true);
            refined.setRefinementAlpha(0.001);
            Graph g2 = refined.search();

            assertEquals("The test's alpha must be restored.", 0.01, test.getAlpha(), 0.0);

            missingPlain += missingAdjacencies(dag, g1);
            missingRefined += missingAdjacencies(dag, g2);
        }

        assertTrue("Expected fewer missing adjacencies with refinement: plain = " + missingPlain
                + ", refined = " + missingRefined, missingRefined < missingPlain);
    }

    /**
     * Refinement must respect forbidden edges and tiers.
     */
    @Test
    public void testRefinementRespectsKnowledge() throws InterruptedException {
        for (int r = 0; r < 10; r++) {
            RandomUtil.getInstance().setSeed(9300L + r);
            Graph dag = RandomGraph.randomGraph(10, 0, 15, 100, 100, 100, false);
            ICovarianceMatrix cov = simulate(dag, 9400L + r);

            Knowledge knowledge = new Knowledge();
            for (int i = 1; i <= 10; i++) knowledge.addToTier(i <= 5 ? 1 : 0, "X" + i);
            knowledge.setForbidden("X6", "X7");
            knowledge.setForbidden("X7", "X6");

            Pc pc = new Pc(new IndTestFisherZ(cov, 0.01));
            pc.setKnowledge(knowledge);
            pc.setOrderRefinement(true);
            Graph g = pc.search();

            assertFalse(g.isAdjacentTo(g.getNode("X6"), g.getNode("X7")));

            for (Edge e : g.getEdges()) {
                if (!e.isDirected()) continue;
                String tail = Edges.getDirectedEdgeTail(e).getName();
                String head = Edges.getDirectedEdgeHead(e).getName();
                assertFalse("Forbidden orientation " + e, knowledge.isForbidden(tail, head));
            }
        }
    }
}
