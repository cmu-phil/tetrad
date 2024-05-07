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
import java.util.ArrayList;
import java.util.List;


/**
 * Extends AbstractWorkbench in the ways needed to display tetrad-style graphs.
 *
 * @author josephramsey
 * @author Willie Wheeler
 * @version $Id: $Id
 * @see edu.cmu.tetradapp.workbench.AbstractWorkbench
 */
public class TimeLagGraphWorkbench extends GraphWorkbench {

    //=================PUBLIC STATIC FINAL FIELDS=========================//
    private static final int MEASURED_NODE = 0;
    private static final int LATENT_NODE = 1;
    private static final int DIRECTED_EDGE = 0;
    private static final int NONDIRECTED_EDGE = 2;
    private static final int PARTIALLY_ORIENTED_EDGE = 3;
    private static final int BIDIRECTED_EDGE = 4;

    //====================PRIVATE FIELDS=================================//

    /**
     * The type of node to be drawn next.
     */
    private int nodeType = TimeLagGraphWorkbench.MEASURED_NODE;

    /**
     * The type of edge to be drawn next.
     */
    private int edgeMode = TimeLagGraphWorkbench.DIRECTED_EDGE;

    /**
     * The nodes remembered from the last layout.
     */
    private List<Node> rememberedNodes = new ArrayList<>();

    //========================CONSTRUCTORS===============================//

    /**
     * Constructs a new workbench with an empty graph; useful if another graph will be set later.
     */
    public TimeLagGraphWorkbench() {
        this(new TimeLagGraph());
    }

    /**
     * Constructs a new workbench workbench for the given workbench model.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public TimeLagGraphWorkbench(TimeLagGraph graph) {
        super(graph);
        setRightClickPopupAllowed(true);

        graph.addPropertyChangeListener(evt -> {
            if ("editingFinished".equals(evt.getPropertyName())) {
                System.out.println("EDITING FINISHED!");
                timeLagLayout();
            }
        });
    }

    private static boolean containsName(List<Node> nodes, String name) {
        for (Node node : nodes) {
            if (name.equals(node.getName())) {
                return true;
            }
        }
        return false;
    }


    //========================PUBLIC METHODS==============================//

    private void timeLagLayout() {

        TimeLagGraph graph = (TimeLagGraph) getGraph();
        this.rememberedNodes.retainAll(graph.getNodes());

        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        System.out.println(lag0Nodes);

        int[] averageY = new int[graph.getMaxLag() + 1];

        int numRememberedLag0 = 0;

        for (Node node : lag0Nodes) {
            if (!this.rememberedNodes.contains(node)) {
                continue;
            }

            numRememberedLag0++;
        }


        if (this.rememberedNodes.isEmpty() || numRememberedLag0 == 0) {
            int x = -25;

            for (Node node : lag0Nodes) {
                x += 90;
                int y = 50 - ySpace;
                TimeLagGraph.NodeId id = graph.getNodeId(node);

                for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                    y += ySpace;
                    Node _node = graph.getNode(id.getName(), lag);

                    if (_node == null) {
                        System.out.println("Couldn't find node");
                        continue;
                    }

                    _node.setCenterX(x);
                    _node.setCenterY(y);
                }
            }
        } else {
            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                for (Node node : lag0Nodes) {
                    if (!rememberedNodes.contains(node)) {
                        continue;
                    }

                    TimeLagGraph.NodeId id = graph.getNodeId(node);
                    Node _node = graph.getNode(id.getName(), lag);

                    if (_node == null) continue;

                    averageY[lag] += _node.getCenterY();
                }
            }

            System.out.println("numRememberedLag0 = " + numRememberedLag0);

            for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                averageY[lag] = averageY[lag] / numRememberedLag0;
            }

            for (Node node : lag0Nodes) {
                if (rememberedNodes.contains(node)) continue;
                int x = node.getCenterX();

                TimeLagGraph.NodeId id = graph.getNodeId(node);

                for (int lag = 0; lag <= graph.getMaxLag(); lag++) {
                    int y = averageY[lag];
                    Node _node = graph.getNode(id.getName(), lag);

                    if (_node == null) {
                        System.out.println("Couldn't find node");
                        continue;
                    }

                    _node.setCenterX(x);
                    _node.setCenterY(y);
                }
            }
        }

        layoutByGraph(graph);
        this.rememberedNodes = graph.getNodes();
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
     * {@inheritDoc}
     * <p>
     * Sets the edge mode to the given mode.
     */
    public void setEdgeMode(int edgeMode) {
        switch (edgeMode) {
            case TimeLagGraphWorkbench.DIRECTED_EDGE:
                // Falls through!
            case TimeLagGraphWorkbench.NONDIRECTED_EDGE:
                // Falls through!
            case TimeLagGraphWorkbench.PARTIALLY_ORIENTED_EDGE:
                // Falls through!
            case TimeLagGraphWorkbench.BIDIRECTED_EDGE:
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
            case TimeLagGraphWorkbench.MEASURED_NODE:
                name = nextVariableName("X");
                modelNode = new GraphNode(name);
                modelNode.setNodeType(NodeType.MEASURED);
                break;

            case TimeLagGraphWorkbench.LATENT_NODE:
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
            case TimeLagGraphWorkbench.DIRECTED_EDGE:
                return Edges.directedEdge(node1, node2);

            case TimeLagGraphWorkbench.NONDIRECTED_EDGE:
                return Edges.nondirectedEdge(node1, node2);

            case TimeLagGraphWorkbench.PARTIALLY_ORIENTED_EDGE:
                return Edges.partiallyOrientedEdge(node1, node2);

            case TimeLagGraphWorkbench.BIDIRECTED_EDGE:
                return Edges.bidirectedEdge(node1, node2);

            default:
                throw new IllegalStateException();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gets a new "tracking edge"--that is, an edge which is anchored at one end to a node but tracks the mouse at the
     * other end.  Used for drawing new edges.
     */
    public IDisplayEdge getNewTrackingEdge(DisplayNode node, Point mouseLoc) {
        switch (this.edgeMode) {
            case TimeLagGraphWorkbench.DIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.DIRECTED);

            case TimeLagGraphWorkbench.NONDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.NONDIRECTED);

            case TimeLagGraphWorkbench.PARTIALLY_ORIENTED_EDGE:
                return new DisplayEdge(node, mouseLoc,
                        DisplayEdge.PARTIALLY_ORIENTED);

            case TimeLagGraphWorkbench.BIDIRECTED_EDGE:
                return new DisplayEdge(node, mouseLoc, DisplayEdge.BIDIRECTED);

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
     * {@inheritDoc}
     * <p>
     * Given base b (a String), returns the first node in the sequence "b1", "b2", "b3", etc., which is not already the
     * name of a node in the workbench.
     */
    public String nextVariableName(String base) {

        if (base.contains(":")) {
            throw new IllegalArgumentException("Base names may not contain colons: " + base);
        }

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
     * {@inheritDoc}
     * <p>
     * Sets the type of this node to the given type.
     */
    public void setNodeType(int nodeType) {
        if (nodeType == TimeLagGraphWorkbench.MEASURED_NODE || nodeType == TimeLagGraphWorkbench.LATENT_NODE) {
            this.nodeType = nodeType;
        } else {
            throw new IllegalArgumentException("The type of the node must be " +
                                               "MEASURED_NODE or LATENT_NODE.");
        }
    }

    //===========================PRIVATE METHODS==========================//

    /**
     * {@inheritDoc}
     * <p>
     * Pastes a list of session elements (SessionNodeWrappers and SessionEdges) into the workbench.
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
                throw new IllegalArgumentException("The list of session " +
                                                   "elements should contain only SessionNodeWrappers " +
                                                   "and SessionEdges: " + graphElement);
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
     * @return the next string in the sequence.
     */
    private String nextUniqueName(String base) {
        if (base == null) {
            throw new NullPointerException("Base name must be non-null.");
        }
        List<Node> currentNodes = this.getWorkbench().getGraph().getNodes();
        if (!TimeLagGraphWorkbench.containsName(currentNodes, base)) {
            return base;
        }
        // otherwise fine new unique name.
        base += "_";
        int i = 1;
        while (TimeLagGraphWorkbench.containsName(currentNodes, base + i)) {
            i++;
        }

        return base + i;
    }
}


