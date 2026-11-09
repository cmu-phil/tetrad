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

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.FfMlContinuous;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Pins the missing-data behavior of the FCIT wrapper. FCIT itself never touches the data: everything flows through
 * the independence test and the score, so FCIT supports the test-wise deletion policy exactly when its test and
 * score do. These tests certify the end-to-end path that the per-component declaration tests
 * ({@link TestTestwiseDeletionDeclarations}, TestMissingDataPolicyWiring) do not reach: with missing values present,
 * (1) FCIT refuses loudly when no missing-data policy has been chosen, and (2) FCIT completes and returns a graph
 * under the 'testwise' policy with Fisher Z paired with SEM BIC and with FFML-Continuous.
 *
 * @author josephramsey
 */
public class TestFcitTestwiseDeletion {

    /**
     * Constructs a new test.
     */
    public TestFcitTestwiseDeletion() {
    }

    /**
     * Linear Gaussian data from a small DAG with one latent confounder, latent column dropped, 10 percent MCAR.
     */
    private static DataSet dataWithMissing() {
        RandomUtil.getInstance().setSeed(31L);

        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) nodes.add(new ContinuousVariable("X" + i));
        Node l = new GraphNode("L1");
        l.setNodeType(NodeType.LATENT);
        nodes.add(l);

        Graph g = new EdgeListGraph(nodes);
        g.addDirectedEdge(nodes.get(0), nodes.get(1));
        g.addDirectedEdge(nodes.get(1), nodes.get(2));
        g.addDirectedEdge(nodes.get(3), nodes.get(2));
        g.addDirectedEdge(nodes.get(4), nodes.get(5));
        g.addDirectedEdge(l, nodes.get(2));
        g.addDirectedEdge(l, nodes.get(5));

        DataSet data;
        try {
            SemIm im = new SemIm(new SemPm(g));
            data = im.simulateData(500, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<Node> keep = new ArrayList<>(data.getVariables());
        keep.removeIf(n -> n.getName().equals("L1"));
        data = data.subsetColumns(keep);

        Random r = new Random(7);
        for (int i = 0; i < data.getNumRows(); i++) {
            for (int j = 0; j < data.getNumColumns(); j++) {
                if (r.nextDouble() < 0.10) data.setDouble(i, j, Double.NaN);
            }
        }

        assertTrue(data.existsMissingValue());
        return data;
    }

    /**
     * With missing values present and no policy chosen, the first gated component (the test) must refuse loudly
     * before any search work is done.
     */
    @Test
    public void fcitRefusesMissingDataWithoutAPolicy() {
        DataSet data = dataWithMissing();

        try {
            new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fcit(new FisherZ(), new SemBicScore())
                    .search(data, new Parameters());
            fail("FCIT on missing data with no missing-data policy should have thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("missing"));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * FCIT with Fisher Z and SEM BIC completes under the 'testwise' policy and returns a graph over the observed
     * variables.
     */
    @Test
    public void fcitRunsTestwiseWithFisherZAndSemBic() throws InterruptedException {
        DataSet data = dataWithMissing();

        Parameters parameters = new Parameters();
        parameters.set(Params.MISSING_DATA_POLICY, "testwise");

        Graph out = new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fcit(new FisherZ(), new SemBicScore())
                .search(data, parameters);

        assertNotNull(out);
        assertEquals(data.getNumColumns(), out.getNumNodes());
    }

    /**
     * FCIT with Fisher Z and FFML-Continuous completes under the 'testwise' policy; FFML's per-family valid-row
     * machinery is exercised through a whole search, not just a single local score.
     */
    @Test
    public void fcitRunsTestwiseWithFisherZAndFfmlContinuous() throws InterruptedException {
        DataSet data = dataWithMissing();

        Parameters parameters = new Parameters();
        parameters.set(Params.MISSING_DATA_POLICY, "testwise");
        parameters.set(Params.KML_NUM_FEATURES, 8);
        parameters.set(Params.BW_MAX_ROWS, 100);

        Graph out = new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fcit(new FisherZ(), new FfMlContinuous())
                .search(data, parameters);

        assertNotNull(out);
        assertEquals(data.getNumColumns(), out.getNumNodes());
    }
}
