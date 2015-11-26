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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.AbstractWorkbench;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.IDisplayEdge;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Extends AbstractWorkbench in the ways needed to display required and
 * forbidden edges and edit a Knowledge object.
 *
 * @author Joseph Ramsey
 * @see edu.cmu.tetradapp.workbench.AbstractWorkbench
 */
public class KnowledgeWorkbench extends AbstractWorkbench {

    //=================PUBLIC STATIC FINAL FIELDS=========================//
    public static final int FORBIDDEN_EDGE = 0;
    public static final int REQUIRED_EDGE = 2;

    //====================PRIVATE FIELDS=================================//
    private int edgeMode = FORBIDDEN_EDGE;

    /**
     * Constructs a new workbench workbench for the given workbench model.
     */
    public KnowledgeWorkbench(KnowledgeGraph graph) {
        super(graph);
        setNodeEdgeErrorsReported(true);
        setRightClickPopupAllowed(false);
        this.setAllowEdgeReorientations(false);
    }

    /**
     * The type of edge to be drawn next.
     *
     * @return the type of edge to be drawn.
     * @see #FORBIDDEN_EDGE
     * @see #REQUIRED_EDGE
     */
    public int getEdgeMode() {
        return edgeMode;
    }

    /**
     * Creates a new model node for the workbench.
     */
    public Node getNewModelNode() {
        throw new UnsupportedOperationException();
        //        return new Knowledge2ModelNode(nextVariableName("X"));
    }

    /**
     * Creates a new display node for the workbench based on the given model
     * node.
     *
     * @param modelNode the model node.
     * @return the new display node.
     */
    public DisplayNode getNewDisplayNode(Node modelNode) {
        DisplayNode displayNode = new KnowledgeDisplayNode(modelNode);

        displayNode.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("resetGraph".equals(evt.getPropertyName())) {
                    setGraph(getGraph());
                }
                else if ("editingValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        return displayNode;
    }

    /**
     * Creates a new model edge for the workbench connecting the two given model
     * nodes and using the edge type from #getEdgeType().
     *
     * @param node1 the one model node.
     * @param node2 the other model node.
     * @return the new model edge.
     */
    public Edge getNewModelEdge(Node node1, Node node2) {
        KnowledgeModelNode _node1 = (KnowledgeModelNode) node1;
        KnowledgeModelNode _node2 = (KnowledgeModelNode) node2;

        switch (edgeMode) {
            case FORBIDDEN_EDGE:
                return new KnowledgeModelEdge(_node1, _node2,
                        KnowledgeModelEdge.FORBIDDEN_EXPLICITLY);
            case REQUIRED_EDGE:
                return new KnowledgeModelEdge(_node1, _node2,
                        KnowledgeModelEdge.REQUIRED);
            default :
                throw new IllegalStateException();
        }
    }

    /**
     * Creates a new display edge for the workbench based on the given model
     * edge.
     *
     * @param modelEdge the model edge.
     * @return the new display edge.
     */
    public IDisplayEdge getNewDisplayEdge(Edge modelEdge) {
        Node node1 = modelEdge.getNode1();
        Node node2 = modelEdge.getNode2();

        if (node1 == node2) {
            throw new IllegalArgumentException("Edges to self not supported.");
        }

        DisplayNode displayNodeA = (DisplayNode) getModelNodesToDisplay().get(node1);
        DisplayNode displayNodeB = (DisplayNode) getModelNodesToDisplay().get(node2);

        if ((displayNodeA == null) || (displayNodeB == null)) {
            return null;
        }

        return new KnowledgeDisplayEdge(modelEdge, displayNodeA, displayNodeB);
    }

    /**
     * Gets a new "tracking edge"--that is, an edge which is anchored at one end
     * to a node but tracks the mouse at the other end.  Used for drawing new
     * edges.
     *
     * @param node     the node to anchor to.
     * @param mouseLoc the location of the mouse.
     * @return the new tracking edge (a display edge).
     */
    public IDisplayEdge getNewTrackingEdge(DisplayNode node, Point mouseLoc) {
        switch (edgeMode) {
            case FORBIDDEN_EDGE:
                return new KnowledgeDisplayEdge(node, mouseLoc,
                        KnowledgeDisplayEdge.FORBIDDEN_EXPLICITLY);
            case REQUIRED_EDGE:
                return new KnowledgeDisplayEdge(node, mouseLoc,
                        KnowledgeDisplayEdge.REQUIRED);
            default :
                throw new IllegalStateException();
        }
    }

    /**
     * Given base b (a String), returns the first node in the sequence "b1",
     * "b2", "b3", etc., which is not already the name of a node in the
     * workbench.
     *
     * @param base the base string.
     * @return the first string in the sequence not already being used.
     */
    public String nextVariableName(String base) {

        // Variable names should start with "1."
        int i = 0;

        loop:
        while (true) {
            String name = base + (++i);

            for (Node node1 : getGraph().getNodes()) {
                if (node1.getName().equals(name)) {
                    continue loop;
                }
            }

            break;
        }

        return base + i;
    }

    /**
     * Sets the edge mode to the given mode.
     */
    public void setEdgeMode(int edgeMode) {
        switch (edgeMode) {
            case FORBIDDEN_EDGE:
                // Falls through!
            case REQUIRED_EDGE:
                this.edgeMode = edgeMode;
                break;
            default :
                throw new IllegalArgumentException();
        }
    }
}





