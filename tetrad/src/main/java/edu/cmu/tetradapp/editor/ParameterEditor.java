///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.Params;

/**
 * Implements an editor some specific type of parameter object. It is assumed
 * that the parameter editor implementing this class has a blank constructor,
 * that <code>setParams</code> is called first, followed by
 * <code>setParantModel</code>, then <code>setup</code>. It is also assumed
 * that the implementing class will implement JComponent.
 *
 * @author Joseph Ramsey
 */
public interface ParameterEditor {

    /**
     * Sets the parameter object to be edited.
     */
    void setParams(Params params);

    /**
     * Sets the parent models that can be exploited for information in the
     * editing process.
     */
    void setParentModels(Object[] parentModels);

    /**
     * Sets up the GUI. Preupposes that the parameter class has been set and
     * that parent models have been passed, if applicable.
     */
    void setup();

    /**
     * True if this parameter editor must be shown when available.
     */
    boolean mustBeShown();
}



