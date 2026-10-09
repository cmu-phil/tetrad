///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.TrekSeparationBlocksIndependence;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests of the pooled (multiple-imputation) and rank-tester paths of {@link TrekSeparationBlocksIndependence}.
 *
 * @author josephramsey
 */
public class TestTrekSeparationBlocksPooled {

    /**
     * Three latents in a chain, L1 -&gt; L2 -&gt; L3, four pure indicators each.
     */
    private static Graph chain() {
        Graph g = new EdgeListGraph();
        Node[] l = new Node[3];
        for (int i = 0; i < 3; i++) {
            l[i] = new GraphNode("L" + (i + 1));
            l[i].setNodeType(NodeType.LATENT);
            g.addNode(l[i]);
        }
        g.addDirectedEdge(l[0], l[1]);
        g.addDirectedEdge(l[1], l[2]);
        for (int i = 0; i < 3; i++) {
            for (int j = 1; j <= 4; j++) {
                Node x = new GraphNode("X" + (4 * i + j));
                g.addNode(x);
                g.addDirectedEdge(l[i], x);
            }
        }
        return g;
    }

    private static DataSet simulate(int n, long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        Parameters p = new Parameters();
        p.set(Params.COEF_LOW, 0.8);
        p.set(Params.COEF_HIGH, 1.5);
        return new SemIm(new SemPm(chain()), p).simulateData(n, false);
    }

    private static BlockSpec spec(DataSet data) {
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            blocks.add(List.of(4 * i, 4 * i + 1, 4 * i + 2, 4 * i + 3));
            Node v = new ContinuousVariable("B" + (i + 1));
            v.setNodeType(NodeType.LATENT);
            vars.add(v);
        }
        return new BlockSpec(data, blocks, vars, List.of(1, 1, 1));
    }

    /**
     * Pooling over identical copies of one data set must reproduce the single-data decisions and p-values of the
     * same rank test: the combining rule reduces to the ordinary chi-square test when the imputations agree.
     */
    @Test
    public void testPoolingIdenticalCopiesMatchesSingle() throws Exception {
        DataSet data = simulate(1500, 11L);
        BlockSpec spec = spec(data);

        TrekSeparationBlocksIndependence single = new TrekSeparationBlocksIndependence(spec, new Chi2RankTest(data));
        TrekSeparationBlocksIndependence pooled = new TrekSeparationBlocksIndependence(spec,
                List.of(data, data.copy(), data.copy()));
        for (TrekSeparationBlocksIndependence t : List.of(single, pooled)) {
            t.setRandomizeSplits(false, 1L);
        }

        List<Node> v = spec.blockVariables();
        Node b1 = v.get(0), b2 = v.get(1), b3 = v.get(2);

        IndependenceResult s = single.checkIndependence(b1, b3, Set.of(b2));
        IndependenceResult p = pooled.checkIndependence(b1, b3, Set.of(b2));
        assertTrue("Chain: B1 and B3 should be separated by B2 (single)", s.isIndependent());
        assertTrue("Chain: B1 and B3 should be separated by B2 (pooled)", p.isIndependent());
        assertEquals(s.getPValue(), p.getPValue(), 1e-9);
        assertTrue("p-value must be reported", p.getPValue() > 0 && p.getPValue() <= 1);

        s = single.checkIndependence(b1, b3, Set.of());
        p = pooled.checkIndependence(b1, b3, Set.of());
        assertFalse(s.isIndependent());
        assertFalse(p.isIndependent());
        assertEquals(s.getPValue(), p.getPValue(), 1e-9);
    }

    /**
     * With real imputations of a data set with values missing at random, the pooled test should still find the
     * chain's separation and dependence, and must report a p-value.
     */
    @Test
    public void testPoolingOverImputations() throws Exception {
        DataSet full = simulate(2000, 23L);
        DataSet missing = full.copy();
        Random rng = new Random(5);
        for (int i = 0; i < missing.getNumRows(); i++) {
            for (int j = 0; j < missing.getNumColumns(); j++) {
                if (rng.nextDouble() < 0.1) missing.setDouble(i, j, Double.NaN);
            }
        }
        List<DataSet> imputations = new MvnImputer().impute(missing, 5, 7L);
        assertEquals(5, imputations.size());

        TrekSeparationBlocksIndependence pooled = new TrekSeparationBlocksIndependence(spec(imputations.get(0)),
                imputations);
        pooled.setAlpha(0.01);
        List<Node> v = pooled.getVariables();
        Node b1 = v.get(0), b2 = v.get(1), b3 = v.get(2);

        IndependenceResult sep = pooled.checkIndependence(b1, b3, Set.of(b2));
        IndependenceResult dep = pooled.checkIndependence(b1, b3, Set.of());
        assertTrue("B1 and B3 separated by B2: " + sep.getPValue(), sep.isIndependent());
        assertFalse("B1 and B3 dependent marginally: " + dep.getPValue(), dep.isIndependent());
        assertFalse(Double.isNaN(sep.getPValue()));
    }

    /**
     * The default engine (no rank tester) still works and now reports a p-value rather than NaN.
     */
    @Test
    public void testDefaultEngineReportsPValue() throws Exception {
        DataSet data = simulate(1500, 31L);
        TrekSeparationBlocksIndependence t = new TrekSeparationBlocksIndependence(spec(data));
        List<Node> v = t.getVariables();
        IndependenceResult r = t.checkIndependence(v.get(0), v.get(2), Set.of(v.get(1)));
        assertTrue(r.isIndependent());
        assertFalse(Double.isNaN(r.getPValue()));
        assertTrue(r.getPValue() > t.getAlpha());
    }
}
