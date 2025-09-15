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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author josephramsey
 */
public final class TestUniformGraphGenerator {

    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(3848283L);

        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) nodes.add(new ContinuousVariable("X" + i));

        Graph dag = RandomGraph.randomGraphUniform(nodes, 3, 30, 3, 3, 3, false, 50000);

        assertEquals(27, dag.getNumEdges());
    }
}






