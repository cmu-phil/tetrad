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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.workbench.DisplayComp;

/**
 * The appearance of a session node.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SessionDisplayComp extends DisplayComp {

    /**
     * <p>setAcronym.</p>
     *
     * @param acronym the acronym (e.g. "PC") for the node.
     */
    void setAcronym(String acronym);

    /**
     * <p>setHasModel.</p>
     *
     * @param b whether the node has a model--i.e. whether it should be rendered in the "filled" color or not.
     */
    void setHasModel(boolean b);


}




