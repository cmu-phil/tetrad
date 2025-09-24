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

package edu.cmu.tetradapp.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the GraphWorkbench class.
 *
 * @author josephramsey
 */
public class TestGraphWorkbench {

    private GraphWorkbench graphWorkbench;

    public void setUp() {
        this.graphWorkbench = new GraphWorkbench(new EdgeListGraph());
    }

    @Test
    public void testNextVariableName() {
        setUp();

        // change the workbench.
        assertEquals("X1", this.graphWorkbench.nextVariableName("X"));
    }
}






