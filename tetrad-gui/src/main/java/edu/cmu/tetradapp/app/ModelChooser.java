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

import edu.cmu.tetradapp.session.SessionNode;

import java.util.List;

/**
 * Represents a device that allows one to select between available models.  A chooser must have an empty constructor,
 * after construction the chooser's set methods will called in the following order: setId(), setTitle(), setNodeName(),
 * setModelConfigs(). After all set methods have been called the setup() method should be called.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface ModelChooser {


    /**
     * <p>getTitle.</p>
     *
     * @return the title of the chooser.
     */
    String getTitle();

    /**
     * <p>setTitle.</p>
     *
     * @param title The title to use for the chooser.
     */
    void setTitle(String title);

    /**
     * <p>getSelectedModel.</p>
     *
     * @return the model class that was selected or null if nothing was selected.
     */
    Class<?> getSelectedModel();

    /**
     * <p>setModelConfigs.</p>
     *
     * @param configs the models that this chooser should display.
     */
    void setModelConfigs(List<SessionNodeModelConfig> configs);


    /**
     * <p>setNodeId.</p>
     *
     * @param id the id for the node.
     */
    void setNodeId(String id);


    /**
     * Call after the set methods are called so that the component can build itself.
     */
    void setup();

    /**
     * <p>setSessionNode.</p>
     *
     * @param sessionNode the SessionNode for the getModel node.
     */
    void setSessionNode(SessionNode sessionNode);
}




