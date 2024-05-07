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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetradapp.model.EditorUtils;

import java.awt.*;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractWorkbench in the ways needed to display tetrad-style graphs.
 *
 * @author josephramsey
 * @author Willie Wheeler
 * @version $Id: $Id
 * @see AbstractWorkbench
 */
public class GraphWorkbench extends AbstractWorkbench implements TripleClassifier {

    //=================PUBLIC STATIC FINAL FIELDS=========================//
    /**
     * Constant <code>MEASURED_NODE=0</code>
     */
    public static final int MEASURED_NODE = 0;
    /**
     * Constant <code>LATENT_NODE=1</code>
     */
    public static final int LATENT_NODE = 1;
    /**
     * Constant <code>DIRECTED_EDGE=0</code>
     */
    public static final int DIRECTED_EDGE = 0;
    /**
     * Constant <code>NONDIRECTED_EDGE=2</code>
     */
    public static final int NONDIRECTED_EDGE = 2;
    /**
     * Constant <code>PARTIALLY_ORIENTED_EDGE=3</code>
     */
    public static final int PARTIALLY_ORIENTED_EDGE = 3;
    /**
     * Constant <code>BIDIRECTED_EDGE=4</code>
     */
    public static final int BIDIRECTED_EDGE = 4;
    /**
     * Constant <code>UNDIRECTED_EDGE=5</code>
     */
    public static final int UNDIRECTED_EDGE = 5;
    @Serial
    private static final long serialVersionUID = 938742592547332849L;
    //====================PRIVATE FIELDS=================================//

    /**
     * The type of node to be drawn next.
     */
    private int nodeType = GraphWorkbench.MEASURED_NODE;

    /**
     * The type of edge to be drawn next.
     */
    private int edgeMode = GraphWorkbench.DIRECTED_EDGE;

    //========================CONSTRUCTORS===============================//

    /**
     * Constructs a new workbench with an empty graph; useful if another graph will be set later.
     */
    public GraphWorkbench() {
        this(new EdgeListGraph());
    }

    /**
     * Constructs a new workbench for the given graph model.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public GraphWorkbench(Graph graph) {
        super(graph);
        setRightClickPopupAllowed(true);
    }

    //========================PUBLIC METHODS==============================//

    private static boolean containsName(List<Node> nodes, String name) {
        for (Node node : nodes) {
            if (name.equals(node.getName())) {
                return true;
            }
        }
        return false;
    }

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
        return this.edgeMode;
    }

    /**
     * Sets the edge mode to the given mode.
     *
     * @param edgeMode a int
     */
    public void setEdgeMode(int edgeMode) {
        switch (edgeMode) {
            case GraphWorkbench.DIRECTED_EDGE:
                // Falls through!
            case GraphWorkbench.NONDIRECTED_EDGE:
                // Falls through!
            case GraphWorkbench.UNDIRECTED_EDGE:
                // Falls through!
            case GraphWorkbench.PARTIALLY_ORIENTED_EDGE:
                // Falls through!
            case GraphWorkbench.BIDIRECTED_EDGE:
                this.edgeMode = edgeMode;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Creates a new model node for the workbench.
     *
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public Node getNewModelNode() {

        // select a name and create the model node
        String name;
        Node modelNode;

        switch (this.nodeType) {
            case GraphWorkbench.MEASURED_NODE:
                name = nextVariableName("X");
                modelNode = new GraphNode(name);
                modelNode.setNodeType(NodeType.MEASURED);
                break;

            case GraphWorkbench.LATENT_NODE:
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
     * {@inheritDoc}
     * <p>
     * Creates a new display node for the workbench based on the given model node.
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

        displayNode.addPropertyChangeListener(evt -> {
            if ("resetGraph".equals(evt.getPropertyName())) {
                setGraph(getGraph());
            } else if ("editingValueChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        return displayNode;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Creates a new display edge for the workbench based on the given model edge.
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
     * {@inheritDoc}
     * <p>
     * Creates a new model edge for the workbench connecting the two given model nodes and using the edge type from
     * #getEdgeType().
     */
    public Edge getNewModelEdge(Node node1, Node node2) {
        switch (this.edgeMode) {
            case GraphWorkbench.DIRECTED_EDGE:
                return Edges.directedEdge(node1, node2);

            case GraphWorkbench.NONDIRECTED_EDGE:
                return Edges.nondirectedEdge(node1, node2);

            case GraphWorkbench.UNDIRECTED_EDGE:
                return Edges.undirectedEdge(node1, node2);

            case GraphWorkbench.PARTIALLY_ORIENTED_EDGE:
                return Edges.partiallyOrientedEdge(node1, node2);

            case GraphWorkbench.BIDIRECTED_EDGE:
                return Edges.bidirectedEdge(node1, node2);

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gets a new "tracking edge"--that is, an edge which is anchored at one end to a node but tracks the mouse at the
     * other end. Used for drawing new edges.
     */
    public IDisplayEdge getNewTrackingEdge(DisplayNode node, Point mouseLoc) {
        Color color = null;

        switch (this.edgeMode) {
            case GraphWorkbench.DIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.DIRECTED, color);

            case GraphWorkbench.NONDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.NONDIRECTED, color);

            case GraphWorkbench.UNDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.UNDIRECTED, color);

            case GraphWorkbench.PARTIALLY_ORIENTED_EDGE:
                return new DisplayEdge(node, mouseLoc,
                        DisplayEdge.PARTIALLY_ORIENTED, color);

            case GraphWorkbench.BIDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.BIDIRECTED, color);

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Determines whether the next node to be constructed will be measured or latent.
     *
     * @return MEASURED_NODE or LATENT_NODE
     * @see #MEASURED_NODE
     * @see #LATENT_NODE
     */
    public int getNodeMode() {
        return this.nodeType;
    }

    /**
     * Given base b (a String), returns the first node in the sequence "b1", "b2", "b3", etc., which is not already the
     * name of a node in the workbench.
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
     * Sets the type of this node to the given type.
     *
     * @param nodeType a int
     */
    public void setNodeType(int nodeType) {
        if (nodeType == GraphWorkbench.MEASURED_NODE || nodeType == GraphWorkbench.LATENT_NODE) {
            this.nodeType = nodeType;
        } else {
            throw new IllegalArgumentException("The type of the node must be "
                                               + "MEASURED_NODE or LATENT_NODE.");
        }
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * Pastes a list of session elements (SessionNodeWrappers and SessionEdges) into the workbench.
     *
     * @param graphElements a {@link java.util.List} object
     * @param upperLeft     a {@link java.awt.Point} object
     */
    public void pasteSubgraph(List graphElements, Point upperLeft) {

        // Extract the SessionNodes from the SessionNodeWrappers
        // and pass the list of them to the Session.  Choose a unique
        // name for each of the session wrappers.
        Point oldUpperLeft = EditorUtils.getTopLeftPoint(graphElements);
        int deltaX = upperLeft.x - oldUpperLeft.x;
        int deltaY = upperLeft.y - oldUpperLeft.y;

        for (Object graphElement : graphElements) {

            if (graphElement instanceof Node node) {
                adjustNameAndPosition(node, deltaX, deltaY);
                getWorkbench().getGraph().addNode(node);
            } else if (graphElement instanceof Edge) {
                getWorkbench().getGraph().addEdge((Edge) graphElement);
            } else {
                throw new IllegalArgumentException("The list of session "
                                                   + "elements should contain only SessionNodeWrappers "
                                                   + "and SessionEdges: " + graphElement);
            }
        }
    }

    /**
     * Adjusts the name to avoid name conflicts in the new session and, if the name is adjusted, adjusts the position so
     * the user can see the two nodes.
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
     * @param base the string base of the name--for example, "Graph".
     * @return the next string in the sequence--for example, "Graph1".
     */
    private String nextUniqueName(String base) {
        if (base == null) {
            throw new NullPointerException("Base name must be non-null.");
        }
        List<Node> currentNodes = this.getWorkbench().getGraph().getNodes();
        if (!GraphWorkbench.containsName(currentNodes, base)) {
            return base;
        }
        // otherwise fine new unique name.
        base += "_";
        int i = 1;
        while (GraphWorkbench.containsName(currentNodes, base + i)) {
            i++;
        }

        return base + i;
    }

    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return the names of the triple classifications. Coordinates with
     * <code>getTriplesList</code>
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Ambiguous");
        names.add("Underlines");
        names.add("Dotted Underlines");
        return names;
    }

    /**
     * {@inheritDoc}
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, graph));
        return triplesList;
    }

    /**
     * <p>pasteSubgraph.</p>
     *
     * @param sessionElements a {@link java.util.List} object
     * @param upperLeft       a {@link edu.cmu.tetrad.util.Point} object
     */
    public void pasteSubgraph(List sessionElements, edu.cmu.tetrad.util.Point upperLeft) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
