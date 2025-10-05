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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Implements some tests of the FDR (False Discovery Rate) test.
 *
 * @author josephramsey
 */
public class TestIndependenceFact {

    @Test
    public void testSimpleCase() {

        Node x = new GraphNode("X");
        Node y = new GraphNode("Y");
        Node w = new GraphNode("W");

        IndependenceFact fact1 = new IndependenceFact(x, y);
        IndependenceFact fact2 = new IndependenceFact(y, x);

        assertEquals(fact1, fact2);

        IndependenceFact fact3 = new IndependenceFact(x, w);

        assertNotEquals(fact1, fact3);

        List<IndependenceFact> facts = new ArrayList<>();

        facts.add(fact1);

        assertTrue(facts.contains(fact2));
    }
}



