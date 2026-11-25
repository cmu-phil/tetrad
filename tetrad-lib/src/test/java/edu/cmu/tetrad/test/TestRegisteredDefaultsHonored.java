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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.IsFges;
import edu.cmu.tetrad.algcomparison.graph.RandomSingleFactorMim;
import edu.cmu.tetrad.algcomparison.graph.RandomTwoFactorMim;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Two places where code and the parameter definitions disagreed about a default in a way that changing a number did
 * not settle.
 * <p>
 * The instance row of the instance-specific searches is registered as counting from 1, with 1 the default and the
 * least value allowed. The code counted from 0. So the interface, which fills in the registered default, searched
 * for the second row, and could not be asked for the first.
 * <p>
 * The number of children for each group of a measurement model was registered with a default of 0, with which the
 * graph cannot be made at all. And the registered number of structural edges, 5, was more than can be drawn among the
 * registered number of structural nodes, then 3.
 *
 * @author Claude (Anthropic)
 */
public class TestRegisteredDefaultsHonored {

    private static DataSet discreteData() {
        RandomUtil.getInstance().setSeed(31L);
        Graph graph = RandomGraph.randomGraph(5, 0, 5, 100, 100, 100, false);
        BayesPm pm = new BayesPm(graph, 2, 3);
        return new MlBayesIm(pm, MlBayesIm.InitializationMethod.RANDOM).simulateData(300, false);
    }

    private static Graph search(DataSet data, Integer instanceRow) throws InterruptedException {
        Parameters parameters = new Parameters();

        if (instanceRow != null) {
            parameters.set(Params.INSTANCE_ROW, instanceRow);
        }

        return new IsFges().search(data, parameters);
    }

    /**
     * The instance row counts from 1: the first row is 1, the last is the number of rows, and 0 is refused with a
     * message that says what is allowed.
     */
    @Test
    public void testInstanceRowCountsFromOne() throws InterruptedException {
        DataSet data = discreteData();
        int rows = data.getNumRows();

        // Left alone, it is the registered default, the first row.
        assertEquals(search(data, 1), search(data, null));

        // The last row is allowed.
        search(data, rows);

        for (int refused : new int[]{0, rows + 1}) {
            try {
                search(data, refused);
                fail("An instance row of " + refused + " should be refused.");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(),
                        e.getMessage().contains("Instance row out of range: " + refused + ". It must be from 1 to "
                                                + rows + "."));
            }
        }
    }

    /**
     * A measurement model graph can be made with nothing set, which takes the registered defaults.
     */
    @Test
    public void testMeasurementModelGraphsWithRegisteredDefaults() {
        assertEquals(3, ParamDescriptions.getInstance().get("mimNumChildrenPerGroup").getDefaultValue());
        assertEquals(1, ParamDescriptions.getInstance().get("mimNumChildrenPerGroup").getLowerBoundInt());
        assertEquals(5, ParamDescriptions.getInstance().get("mimNumStructuralNodes").getDefaultValue());
        assertEquals(5, ParamDescriptions.getInstance().get("mimNumStructuralEdges").getDefaultValue());

        for (Graph graph : new Graph[]{new RandomSingleFactorMim().createGraph(new Parameters()),
                new RandomTwoFactorMim().createGraph(new Parameters())}) {
            long measured = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.MEASURED).count();
            long latent = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.LATENT).count();

            assertTrue("The graph has no latent variables.", latent > 0);
            assertTrue("The graph has no measured variables.", measured > 0);
        }
    }
}
