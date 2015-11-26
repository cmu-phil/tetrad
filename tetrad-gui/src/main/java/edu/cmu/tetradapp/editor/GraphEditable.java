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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import java.awt.*;
import java.util.List;

/**
 * Inteface for graph editors, useful for situations where graph editors need to
 * be treated generally.
 *
 * @author Joseph Ramsey
 */
public interface GraphEditable {

    /**
     * Sets the name of the editor.
     * @param name The name to be set.
     */
    void setName(String name);

    /**
     * @return the selected components (display nodes and display edges) in the
     * editor.
     * @return the selected components.
     */
    List getSelectedModelComponents();

    /**
     * Pastes a list of components (display nodes and display edges) into the
     * workbench of the editor.
     * @param sessionElements The session elements.
     * @param upperLeft the upper left point of the paste area.
     */
    void pasteSubsession(List sessionElements, Point upperLeft);

    /**
     * @return the graph workbench.
     * @return the workbench.
     */
    GraphWorkbench getWorkbench();

    /**
     * @return the graph.
     * @return the graph.
     */
    Graph getGraph();

    /**
     * Sets the graph.
     * @param graph The graph to be set.
     */
    void setGraph(Graph graph);
}



