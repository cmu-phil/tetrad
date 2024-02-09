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
     * @return a new display node of type AbstractGraphNode given a model node of type modelNode.
     * @param modelNode a {@link edu.cmu.tetrad.graph.Node} object
     */
    DisplayNode getNewDisplayNode(Node modelNode);

    /**
     * <p>getNewDisplayEdge.</p>
     *
     * @return a new display edge for the given model edge.
     * @param modelEdge a {@link edu.cmu.tetrad.graph.Edge} object
     */
    IDisplayEdge getNewDisplayEdge(Edge modelEdge);

    /**
     * <p>getNewModelEdge.</p>
     *
     * @return a new model edge connecting the given nodes.
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     */
    Edge getNewModelEdge(Node node1, Node node2);

    /**
     * <p>getNewTrackingEdge.</p>
     *
     * @return a new tracking edge for the given display node at the given location.
     * @param displayNode a {@link edu.cmu.tetradapp.workbench.DisplayNode} object
     * @param mouseLoc a {@link java.awt.Point} object
     */
    IDisplayEdge getNewTrackingEdge(DisplayNode displayNode, Point mouseLoc);
}





