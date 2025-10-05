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


/**
 * Tags models whose parameters can be reset.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ParamsResettable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * In some cases (for instance, algorithm runners), cloned session models need to have the object-identically same
     * parameter objects as before cloning. This method lets Tetrad set that automatically.
     *
     * @param params a {@link java.lang.Object} object
     */
    void resetParams(Object params);

    /**
     * <p>getResettableParams.</p>
     *
     * @return the parameter object of a non-cloned model so that it can be set on the cloned model.
     */
    Object getResettableParams();
}






