///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetradapp.app;

import edu.cmu.tetradapp.editor.ParameterEditor;

import javax.swing.*;

/**
 * Represents the configuration details for a particular model
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface SessionNodeModelConfig {

    /**
     * <p>getHelpIdentifier.</p>
     *
     * @return the identifier to use for help.
     */
    String getHelpIdentifier();


    /**
     * <p>getCategory.</p>
     *
     * @return the category that this model config belongs to or null if there isn't one. This allows you to organize
     * models into various groupings.
     */
    String category();


    /**
     * Returns the model class associated with the configuration.
     *
     * @return the model class
     */
    Class<?> model();


    /**
     * <p>getName.</p>
     *
     * @return a descriptive name for the model.
     */
    String name();


    /**
     * <p>getAcronym.</p>
     *
     * @return the acronym for the model.
     */
    String acronym();


    /**
     * <p>getEditorInstance.</p>
     *
     * @param arguments an array of {@link java.lang.Object} objects
     * @return an instance of the editor to use for the model.
     * @throws java.lang.IllegalArgumentException - Throws an exception of the arguments aren't of the right sort.
     */
    JPanel getEditorInstance(Object[] arguments);


    /**
     * <p>getParameterEditorInstance.</p>
     *
     * @return a newly created instance of the parameter editor for the params returned by
     * <code>getParametersInstance()</code> or null if there is no such
     * editor.
     */
    ParameterEditor getParameterEditorInstance();


}



