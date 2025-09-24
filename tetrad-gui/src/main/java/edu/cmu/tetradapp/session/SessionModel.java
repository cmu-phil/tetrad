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

package edu.cmu.tetradapp.session;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface implemented by all session models in Tetrad. Note that every class C that implements SessionModel MUST be
 * accompanied by a unit test that ensures (1) that C can serialized out and loaded back in, passing a roundtrip test
 * (C.equals(save(load(C))), and (2) that sample models from every published version of Tetrad in which C has changed
 * will load correctly.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SessionModel extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getName.</p>
     *
     * @return the name of the session model.
     */
    String getName();

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
     */
    void setName(String name);


}






