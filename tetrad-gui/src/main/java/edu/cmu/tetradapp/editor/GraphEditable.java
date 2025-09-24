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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import java.awt.*;
import java.util.List;

/**
 * Interface for graph editors, useful for situations where graph editors need to be treated generally.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface GraphEditable {

    /**
     * Sets the name of the editor.
     *
     * @param name The name to be set.
     */
    void setName(String name);

    /**
     * <p>getSelectedModelComponents.</p>
     *
     * @return the selected components (display nodes and display edges) in the editor.
     */
    List getSelectedModelComponents();

    /**
     * Pastes a list of components (display nodes and display edges) into the workbench of the editor.
     *
     * @param sessionElements The session elements.
     * @param upperLeft       the upper left point of the paste area.
     */
    void pasteSubsession(List<Object> sessionElements, Point upperLeft);

    /**
     * <p>getWorkbench.</p>
     *
     * @return the graph workbench.
     */
    GraphWorkbench getWorkbench();

    /**
     * <p>getGraph.</p>
     *
     * @return the graph.
     */
    Graph getGraph();

    /**
     * Sets the graph.
     *
     * @param graph The graph to be set.
     */
    void setGraph(Graph graph);
}




