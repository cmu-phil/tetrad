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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;

import java.awt.*;

/**
 * Interface for a workbench model.
 */
interface WorkbenchModel {
    /**
     * <p>getNewModelNode.</p>
     *
     * @return a new model node of type GraphNode.
     */
    Node getNewModelNode();

    /**
     * <p>getNewDisplayNode.</p>
     *
     * @param modelNode a {@link edu.cmu.tetrad.graph.Node} object
     * @return a new display node of type AbstractGraphNode given a model node of type modelNode.
     */
    DisplayNode getNewDisplayNode(Node modelNode);

    /**
     * <p>getNewDisplayEdge.</p>
     *
     * @param modelEdge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a new display edge for the given model edge.
     */
    IDisplayEdge getNewDisplayEdge(Edge modelEdge);

    /**
     * <p>getNewModelEdge.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a new model edge connecting the given nodes.
     */
    Edge getNewModelEdge(Node node1, Node node2);

    /**
     * <p>getNewTrackingEdge.</p>
     *
     * @param displayNode a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param mouseLoc    a {@link java.awt.Point} object
     * @return a new tracking edge for the given display node at the given location.
     */
    IDisplayEdge getNewTrackingEdge(DisplayNode displayNode, Point mouseLoc);
}






