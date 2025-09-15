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

import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphNodeMeasured;
import org.junit.Test;

public class TestDisplayNode {

    @Test
    public void testSetLocation() {

        // When constructing a display node without a model node, must check
        // make sure its position can be set. This at one point threw a
        // null pointer exception.
        DisplayNode displayNode = new GraphNodeMeasured("X");
        displayNode.setLocation(10, 10);
    }
}






