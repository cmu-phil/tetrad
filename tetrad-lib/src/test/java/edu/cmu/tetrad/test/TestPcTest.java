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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;


/**
 * @author josephramsey
 */
public class TestPcTest {

    @Test
    public void test1() {
        final int numVars = 10;
        final double edgesPerNode = 1.0;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph dag = new EdgeListGraph(vars);
        dag.addDirectedEdge(vars.get(0), vars.get(2));
        dag.addDirectedEdge(vars.get(1), vars.get(2));
        dag.addDirectedEdge(vars.get(2), vars.get(3));

        // Graph dag = RandomGraph.randomGraphRandomForwardEdges(vars, 0, (int) (numVars * edgesPerNode), 30, 15, 15, false, true, -1);

        Graph cpdag = GraphTransforms.dagToCpdag(dag);
        System.out.println(cpdag);

        IndependenceTest test = new MsepTest(dag);
        PcTest pc = new PcTest(test);
        pc.setDepth(-1);

        Graph g;
        try {
            g = pc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println(g);

        // assertEquals(cpdag, g);
    }
}