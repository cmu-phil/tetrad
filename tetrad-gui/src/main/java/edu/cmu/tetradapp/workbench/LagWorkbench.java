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

package edu.cmu.tetradapp.workbench;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetradapp.model.EditorUtils;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;


/**
 * Extends AbstractWorkbench in the ways needed to display tetrad-style graphs.
 *
 * @author Joseph Ramsey
 * @author Willie Wheeler
 * @see edu.cmu.tetradapp.workbench.AbstractWorkbench
 */
public class LagWorkbench extends AbstractWorkbench {

    //=================PUBLIC STATIC FINAL FIELDS=========================//
    public static final int MEASURED_NODE = 0;
    public static final int LATENT_NODE = 1;
    public static final int DIRECTED_EDGE = 0;
    public static final int NONDIRECTED_EDGE = 2;
    public static final int PARTIALLY_ORIENTED_EDGE = 3;
    public static final int BIDIRECTED_EDGE = 4;

    //====================PRIVATE FIELDS=================================//
    private int nodeType = MEASURED_NODE;
    private int edgeMode = DIRECTED_EDGE;

    //========================CONSTRUCTORS===============================//

    /**
     * Constructs a new workbench with an empty graph; useful if another graph
     * will be set later.
     */
    public LagWorkbench() {
        this(new EdgeListGraph());
    }

    /**
     * Constructs a new workbench workbench for the given workbench model.
     */
    public LagWorkbench(Graph graph) {
        super(graph);
        setRightClickPopupAllowed(true);
    }

    //========================PUBLIC METHODS==============================//

    /**
     * The type of edge to be drawn next.
     *
     * @return the type of edge to be drawn.
     * @see #DIRECTED_EDGE
     * @see #NONDIRECTED_EDGE
     * @see #PARTIALLY_ORIENTED_EDGE
     * @see #BIDIRECTED_EDGE
     */
    public int getEdgeMode() {
        return edgeMode;
    }

    /**
     * Creates a new model node for the workbench.
     */
    public Node getNewModelNode() {

        // select a name and create the model node
        String name;
        Node modelNode;

        switch (nodeType) {
            case MEASURED_NODE:
                name = nextVariableName("X");
                modelNode = new GraphNode(name);
                modelNode.setNodeType(NodeType.MEASURED);
                break;

            case LATENT_NODE:
                name = nextVariableName("L");
                modelNode = new GraphNode(name);
                modelNode.setNodeType(NodeType.LATENT);
                break;

            default:
                throw new IllegalStateException();
        }

        return modelNode;
    }

    /**
     * Creates a new display node for the workbench based on the given model
     * node.
     *
     * @param modelNode the model node.
     * @return the new display node.
     */
    public DisplayNode getNewDisplayNode(Node modelNode) {
        DisplayNode displayNode;

        if (modelNode.getNodeType() == NodeType.MEASURED) {
            GraphNodeMeasured nodeMeasured = new GraphNodeMeasured(modelNode);
            nodeMeasured.setEditExitingMeasuredVarsAllowed(isEditExistingMeasuredVarsAllowed());
            displayNode = nodeMeasured;
        } else if (modelNode.getNodeType() == NodeType.LATENT) {
            displayNode = new GraphNodeLatent(modelNode);
        } else if (modelNode.getNodeType() == NodeType.ERROR) {
            displayNode = new GraphNodeError(modelNode);
        } else {
            throw new IllegalStateException();
        }

        displayNode.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("resetGraph".equals(evt.getPropertyName())) {
                    setGraph(getGraph());
                } else if ("editingValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        return displayNode;
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

        return new DisplayEdge(modelEdge, displayNodeA, displayNodeB);
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
        switch (edgeMode) {
            case DIRECTED_EDGE:
                return Edges.directedEdge(node1, node2);

            case NONDIRECTED_EDGE:
                return Edges.nondirectedEdge(node1, node2);

            case PARTIALLY_ORIENTED_EDGE:
                return Edges.partiallyOrientedEdge(node1, node2);

            case BIDIRECTED_EDGE:
                return Edges.bidirectedEdge(node1, node2);

            default:
                throw new IllegalStateException();
        }
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
            case DIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.DIRECTED);

            case NONDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.NONDIRECTED);

            case PARTIALLY_ORIENTED_EDGE:
                return new DisplayEdge(node, mouseLoc,
                        DisplayEdge.PARTIALLY_ORIENTED);

            case BIDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.BIDIRECTED);

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Determines whether the next node to be constructed will be measured or
     * latent.
     *
     * @return MEASURED_NODE or LATENT_NODE
     * @see #MEASURED_NODE
     * @see #LATENT_NODE
     */
    public int getNodeMode() {
        return nodeType;
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
            case DIRECTED_EDGE:
                // Falls through!
            case NONDIRECTED_EDGE:
                // Falls through!
            case PARTIALLY_ORIENTED_EDGE:
                // Falls through!
            case BIDIRECTED_EDGE:
                this.edgeMode = edgeMode;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Sets the type of this node to the given type.
     */
    public void setNodeType(int nodeType) {
        if (nodeType == MEASURED_NODE || nodeType == LATENT_NODE) {
            this.nodeType = nodeType;
        } else {
            throw new IllegalArgumentException("The type of the node must be " +
                    "MEASURED_NODE or LATENT_NODE.");
        }
    }

    /**
     * Pastes a list of session elements (SessionNodeWrappers and SessionEdges)
     * into the workbench.
     */
    public void pasteSubgraph(List graphElements, Point upperLeft) {

        // Extract the SessionNodes from the SessionNodeWrappers
        // and pass the list of them to the Session.  Choose a unique
        // name for each of the session wrappers.
        Point oldUpperLeft = EditorUtils.getTopLeftPoint(graphElements);
        int deltaX = upperLeft.x - oldUpperLeft.x;
        int deltaY = upperLeft.y - oldUpperLeft.y;

        for (Object graphElement : graphElements) {

            if (graphElement instanceof Node) {
                Node node = (Node) graphElement;
                adjustNameAndPosition(node, deltaX, deltaY);
                getWorkbench().getGraph().addNode(node);
            } else if (graphElement instanceof Edge) {
                getWorkbench().getGraph().addEdge((Edge) graphElement);
            } else {
                throw new IllegalArgumentException("The list of session " +
                        "elements should contain only SessionNodeWrappers " +
                        "and SessionEdges: " + graphElement);
            }
        }
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Adjusts the name to avoid name conflicts in the new session and, if the
     * name is adjusted, adjusts the position so the user can see the two
     * nodes.
     *
     * @param node   The node which is being adjusted
     * @param deltaX the shift in x
     * @param deltaY the shift in y.
     */
    private void adjustNameAndPosition(Node node, int deltaX,
                                       int deltaY) {
        String originalName = node.getName();
        //String base = extractBase(originalName);
        String uniqueName = nextUniqueName(originalName);

        if (!uniqueName.equals(originalName)) {
            node.setName(uniqueName);
            node.setCenterX(node.getCenterX() + deltaX);
            node.setCenterY(node.getCenterY() + deltaY);
        }
    }

    /**
     * @return the next string in the sequence.
     *
     * @param base the string base of the name--for example, "Graph".
     * @return the next string in the sequence--for example, "Graph1".
     */
    private String nextUniqueName(String base) {
        if (base == null) {
            throw new NullPointerException("Base name must be non-null.");
        }
        List<Node> currentNodes = this.getWorkbench().getGraph().getNodes();
        if (!containsName(currentNodes, base)) {
            return base;
        }
        // otherwise fine new unique name.
        base += "_";
        int i = 1;
        while (containsName(currentNodes, base + i)) {
            i++;
        }

        return base + i;
    }

    private static boolean containsName(List<Node> nodes, String name) {
        for (Node node : nodes) {
            if (name.equals(node.getName())) {
                return true;
            }
        }
        return false;
    }
}


