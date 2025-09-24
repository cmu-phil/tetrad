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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Parameters;

/**
 * Implements an editor some specific type of parameter object. It is assumed that the parameter editor implementing
 * this class has a blank constructor, that <code>setParameters</code> is called first, followed by
 * <code>setParantModel</code>, then <code>setup</code>. It is also assumed
 * that the implementing class will implement JComponent.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ParameterEditor {

    /**
     * Sets the parameter object to be edited.
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    void setParams(Parameters params);

    /**
     * Sets the parent models that can be exploited for information in the editing process.
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    void setParentModels(Object[] parentModels);

    /**
     * Sets up the GUI. Preupposes that the parameter class has been set and that parent models have been passed, if
     * applicable.
     */
    void setup();

    /**
     * True if this parameter editor must be shown when available.
     *
     * @return a boolean
     */
    boolean mustBeShown();
}




