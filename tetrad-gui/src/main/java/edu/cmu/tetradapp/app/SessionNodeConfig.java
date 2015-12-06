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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.session.SessionNode;


/**
 * Represents the configuration details for a session node.
 *
 * @author Tyler Gibson
 */
public interface SessionNodeConfig {


    /**
     * @return the model confif for the model with the given class or null if there isn't
     * one.
     */
    SessionNodeModelConfig getModelConfig(Class model);


    /**
     * @return all the models for this node.
     */
    Class[] getModels();


    /**
     * @return text to use as a tooltip for the node.
     */
    String getTooltipText();


    /**
     * @return a newly created <code>ModelChooser</code> that should be utilized to select a model. If no
     * chooser was specified then the default chooser will be returned.
     *
     * @param sessionNode - The CessionNode for the getModel node.
     */
    ModelChooser getModelChooserInstance(SessionNode sessionNode);


    /**
     * @return a newly created <code>SessionDisplayComp</code> that is used to display the node
     * on the session workbench. If no display component class was specified then a default instance
     * will be used.
     */
    SessionDisplayComp getSessionDisplayCompInstance();


}




