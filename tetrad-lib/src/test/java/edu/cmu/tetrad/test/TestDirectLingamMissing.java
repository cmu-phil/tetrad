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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.DirectLingam;
import edu.cmu.tetrad.search.score.SemBicScore;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that DirectLiNGAM recovers a chain whose columns are stored out of causal order, and that it refuses
 * data with missing values rather than silently returning the column order.
 *
 * @author josephramsey
 */
public class TestDirectLingamMissing {

    private static double unif(Random random) {
        return 2.0 * random.nextDouble() - 1.0;
    }

    /**
     * Chain x1 -> x2 -> x3 -> x4 with uniform errors, columns stored in the order x3, x1, x4, x2.
     */
    private static DataSet chainData(int n, long seed) {
        Random random = new Random(seed);
        double[][] data = new double[n][4];

        for (int i = 0; i < n; i++) {
            double x1 = unif(random);
            double x2 = 0.8 * x1 + unif(random);
            double x3 = 0.8 * x2 + unif(random);
            double x4 = 0.8 * x3 + unif(random);

            data[i][0] = x3;
            data[i][1] = x1;
            data[i][2] = x4;
            data[i][3] = x2;
        }

        List<Node> variables = List.of(new ContinuousVariable("x3"), new ContinuousVariable("x1"),
                new ContinuousVariable("x4"), new ContinuousVariable("x2"));

        return new BoxDataSet(new DoubleDataBox(data), variables);
    }

    @Test
    public void testChainOutOfColumnOrder() {
        DataSet data = chainData(2000, 1L);
        Graph graph = new DirectLingam(data, new SemBicScore(data, true)).search();

        String[][] edges = {{"x1", "x2"}, {"x2", "x3"}, {"x3", "x4"}};

        for (String[] edge : edges) {
            Node from = graph.getNode(edge[0]);
            Node to = graph.getNode(edge[1]);
            assertTrue(edge[0] + " --> " + edge[1] + " missing in " + graph, graph.isParentOf(from, to));
            assertFalse(edge[1] + " --> " + edge[0] + " reversed in " + graph, graph.isParentOf(to, from));
        }
    }

    @Test
    public void testMissingValuesRejected() {
        DataSet data = chainData(200, 2L);
        data.setDouble(17, 2, Double.NaN);

        try {
            new DirectLingam(data, new SemBicScore(data, true));
            fail("Expected an IllegalArgumentException for data with a missing value.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missing"));
        }
    }
}
