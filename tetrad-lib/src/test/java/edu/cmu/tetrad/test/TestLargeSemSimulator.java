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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard Scheines. The diagnostics are described in
 * the Javadocs, below.
 *
 * @author josephramsey
 */
public class TestLargeSemSimulator {

    @Test
    public void test1() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) nodes.add(new ContinuousVariable("X" + i));

        Graph graph = RandomGraph.randomGraph(nodes, 0, 10, 5, 5, 5, false);

        LargeScaleSimulation simulator = new LargeScaleSimulation(graph);
        DataSet dataset = simulator.simulateDataFisher(1000);

        assertEquals(1000, dataset.getNumRows());
    }
}






