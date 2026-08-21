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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for FASK's BOSS-adjacency option: with skew-implied extra edges disabled, the output adjacencies
 * must lie within the BOSS CPDAG skeleton computed from the same score, and the option must leave FASK's pairwise
 * orientation machinery otherwise untouched (the search runs and orients on non-Gaussian data).
 */
public class TestFaskBossAdjacencies {

    /**
     * Linear SEM with exponential (skewed) noise: X0 -> X1 -> X2, X0 -> X3.
     */
    private static DataSet simulate(int n) {
        Random rnd = new Random(48302L);
        double[][] d = new double[n][4];
        for (int i = 0; i < n; i++) {
            double e0 = expo(rnd), e1 = expo(rnd), e2 = expo(rnd), e3 = expo(rnd);
            double x0 = e0;
            double x1 = 0.8 * x0 + e1;
            double x2 = 0.8 * x1 + e2;
            double x3 = 0.8 * x0 + e3;
            d[i][0] = x0;
            d[i][1] = x1;
            d[i][2] = x2;
            d[i][3] = x3;
        }
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static double expo(Random rnd) {
        return -Math.log(1.0 - rnd.nextDouble()) - 1.0;  // centered exponential, skewed
    }

    @Test
    public void testBossAdjacenciesRestrictSkeleton() throws Exception {
        DataSet data = simulate(2000);

        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(2.0);

        // Reference BOSS skeleton from the same score.
        Boss boss = new Boss(score);
        Graph cpdag = new PermutationSearch(boss).search();
        Graph skeleton = GraphUtils.undirectedGraph(cpdag);

        Fask fask = new Fask(data, score);
        fask.setUseBossAdjacencies(true);
        fask.setUseSkewAdjacencies(false);  // isolate the skeleton source
        Graph out = fask.search();

        for (Edge e : out.getEdges()) {
            Node a = skeleton.getNode(e.getNode1().getName());
            Node b = skeleton.getNode(e.getNode2().getName());
            assertTrue("Edge " + e + " not in BOSS skeleton", skeleton.isAdjacentTo(a, b));
        }

        // On this easy model the skeletons should in fact coincide.
        assertEquals(skeleton.getNumEdges(), out.getNumEdges());
    }
}
