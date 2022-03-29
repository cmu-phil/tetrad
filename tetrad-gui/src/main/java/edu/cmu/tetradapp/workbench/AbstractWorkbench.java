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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.PasteLayoutAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * The functionality of the workbench which is shared between the workbench
 * workbench and the session workbench (and any other workbenches which want to
 * use this functionality).
 *
 * @author Aaron Powell
 * @author Joseph Ramsey
 * @author Willie Wheeler
 * @see DisplayNode
 * @see DisplayEdge
 */
public abstract class AbstractWorkbench extends JComponent implements WorkbenchModel, LayoutEditable {

    private static final long serialVersionUID = 6718395673225983249L;

    // ===================PUBLIC STATIC FINAL FIELDS=====================//
    /**
     * The mode in which the user is permitted to select workbench items or move
     * nodes.
     */
    public static final int SELECT_MOVE = 0;

    /**
     * The mode in which the user is permitted to select workbench items or move
     * nodes.
     */
    public static final int ADD_NODE = 1;

    /**
     * The mode in which the user is permitted to select workbench items or move
     * nodes.
     */
    public static final int ADD_EDGE = 2;

    // =========================PRIVATE FIELDS=============================//
    /**
     * The workbench which this workbench displays.
     */
    private Graph graph;

    /**
     * The map from model edges to display elements.
     */
    private Map<Edge, Object> modelEdgesToDisplay;

    /**
     * The map from model nodes to display elements.
     */
    private Map<Node, Object> modelNodesToDisplay;

    /**
     * The map from display elements to model elements.
     */
    private Map<Object, Object> displayToModel;

    /**
     * The map from edges to edge labels.
     */
    private Map<Object, Object> displayToLabels;

    /**
     * The getModel mode of the workbench.
     */
    private int workbenchMode = SELECT_MOVE;

    /**
     * When edges are being constructed, one edge is anchored to a node and the
     * other edge tracks mouse dragged events; this is the edge that does this.
     * This edge should be null unless an edge is actually being tracked.
     */
    private IDisplayEdge trackedEdge;

    /**
     * For dragging nodes, a click point is needed; this is that click point.
     */
    private Point clickPoint;

    /**
     * For dragging nodes, the set of selected nodes at the start of dragging is
     * needed, since the selected nodes need to be moved in sync during the
     * drag.
     */
    private List<DisplayNode> dragNodes;

    /**
     * For selecting multiple nodes using a rubberband, a rubberband is needed;
     * this is it.
     */
    private Rubberband rubberband;

    /**
     * Indicates whether user editing is permitted.
     */
    private boolean allowDoubleClickActions = true;

    /**
     * Indicates whether nodes may be moved by the user.
     */
    private boolean allowNodeDragging = true;

    /**
     * Indicates whether user editing is permitted.
     */
    private boolean allowNodeEdgeSelection = true;

    /**
     * Indicates whether edge reorientations are permitted.
     */
    private boolean allowEdgeReorientations = true;

    /**
     * Indicates whether multiple node selection is allowed.
     */
    private final boolean allowMultipleSelection = true;

    /**
     * True iff the user is allows to add measured variables.
     */
    private boolean addMeasuredVarsAllowed = true;

    /**
     * True iff the user is allowed to edit existing measured variables.
     */
    private final boolean editExistingMeasuredVarsAllowed = true;

    /**
     * True iff the user is allowed to delete variables.
     */
    private boolean deleteVariablesAllowed = true;

    /**
     * ` Handler for ComponentEvents.
     */
    private final ComponentHandler compHandler = new ComponentHandler(this);

    /**
     * Handler for MouseEvents.
     */
    private final MouseHandler mouseHandler = new MouseHandler(this);

    /**
     * Handler MouseMotionEvents.
     */
    private final MouseMotionHandler mouseMotionHandler = new MouseMotionHandler(this);

    /**
     * Handler for PropertyChangeEvents.
     */
    private final PropertyChangeHandler propChangeHandler = new PropertyChangeHandler(this);

    /**
     * Maximum x value (for dragging).
     */
    private int maxX = 10000;

    /**
     * Maximum y value (for dragging).
     */
    private int maxY = 10000;

    /**
     * True iff node/edge adding/removing errors should be reported to the user.
     */
    private boolean nodeEdgeErrorsReported;

    /**
     * True iff layout is permitted using a right click popup.
     */
    private boolean rightClickPopupAllowed;

    /**
     * A key dispatcher to allow pressing the control key to control whether
     * edges will be drawn in the workbench.
     */
    private KeyEventDispatcher controlDispatcher;

    /**
     * Returns the current mouse location. Needed for pasting.
     */
    private Point currentMouseLocation;

    /**
     * TEMPORARY bug fix added 4/15/2005. The bug is that in JDK 1.5.0_02
     * (without this bug fix) groups of nodes cannot be selected, because if you
     * click and drag, an extra mouseClicked event is fired when you release the
     * mouse. This is a known bug, #5039416 in Sun's bug database. To get around
     * the problem, we set this flag to true when a mouseDragged event is fired
     * and ignore the first click (and reset this flag to false) on the first
     * mouseClicked event after any mouseDragged event. When this bug is fixed
     * in JDK 1.5, this temporary bug fix shold be removed. jdramsey 4/15/2005
     */
    private boolean mouseDragging;

    /**
     * Returns the current displayed mouseover equation label. Returns null if
     * none is displayed. Used for removing the label.
     */

    private boolean enableEditing = true;

    // ==============================CONSTRUCTOR============================//

    /**
     * Constructs a new workbench workbench.
     *
     * @param graph The graph that this workbench will display.
     */
    protected AbstractWorkbench(Graph graph) {
        this.setGraph(graph);
        this.addMouseListener(mouseHandler);
        this.addMouseMotionListener(mouseMotionHandler);
        // setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        this.setBackground(new Color(254, 254, 255));
        this.setFocusable(true);

        this.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                AbstractWorkbench.this.grabFocus();
            }
        });
        this.setEnabled(enableEditing);

        new PasteLayoutAction(this).actionPerformed(null);
    }

    // ============================PUBLIC METHODS==========================//

    /**
     * Deletes all selected nodes in the workbench plus any edges that have had
     * one of their nodes deleted in the process.
     */
    public final void deleteSelectedObjects() {
        Component[] components = this.getComponents();
        List<DisplayNode> graphNodes = new ArrayList<>();
        List<IDisplayEdge> graphEdges = new ArrayList<>();

        for (Component comp : components) {
            if (comp instanceof DisplayNode) {
                if (!this.isDeleteVariablesAllowed()) {
                    continue;
                }

                DisplayNode node = (DisplayNode) comp;

                if (node.isSelected()) {
                    graphNodes.add(node);
                }
            } else if (comp instanceof IDisplayEdge) {
                IDisplayEdge edge = (IDisplayEdge) comp;

                if (edge.isSelected()) {
                    graphEdges.add(edge);
                }
            }
        }

        for (DisplayNode graphNode : graphNodes) {
            this.removeNode(graphNode);
        }

        for (IDisplayEdge displayEdge : graphEdges) {
            try {
                this.removeEdge(displayEdge);
                this.resetEdgeOffsets(displayEdge);
            } catch (Exception e) {
                if (this.isNodeEdgeErrorsReported()) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
                }
            }
        }
    }

    /**
     * Deselects all edges and nodes in the workbench.
     */
    public final void deselectAll() {
        Component[] components = this.getComponents();

        for (Component comp : components) {
            if (comp instanceof IDisplayEdge) {
                ((IDisplayEdge) comp).setSelected(false);
            } else if (comp instanceof DisplayNode) {
                ((DisplayNode) comp).setSelected(false);
            }
        }

        this.repaint();
        this.firePropertyChange("BackgroundClicked", null, null);
    }

    /**
     * Returns the workbench mode. One of SELECT_MOVE, ADD_NODE, ADD_EDGE.
     *
     * @return the workbench mode. One of SELECT_MOVE, ADD_NODE, ADD_EDGE.
     */
    public final int getWorkbenchMode() {
        return workbenchMode;
    }

    /**
     * @return the Graph this workbench displays.
     */
    public final Graph getGraph() {
        return graph;
    }

    /**
     * Returns the currently selected nodes as a list.
     *
     * @return the currently selected nodes as a list.
     */
    public final List<DisplayNode> getSelectedNodes() {
        List<DisplayNode> selectedNodes = new ArrayList<>();
        Component[] components = this.getComponents();

        for (Component comp : components) {
            if ((comp instanceof DisplayNode) && ((DisplayNode) comp).isSelected()) {
                selectedNodes.add((DisplayNode) comp);
            }
        }

        return selectedNodes;
    }

    /**
     * Returns the current selected node, if exactly one is selected; otherwise,
     * return null.
     *
     * @return the current selected node, if exactly one is selected; otherwise,
     * return null.
     */
    public final DisplayNode getSelectedNode() {
        List<DisplayNode> selectedNodes = this.getSelectedNodes();

        if (selectedNodes.size() == 1) {
            return selectedNodes.get(0);
        } else {
            return null;
        }
    }

    /**
     * @return the currently selected nodes as a vector.
     */
    public final List<Component> getSelectedComponents() {
        List<Component> selectedComponents = new ArrayList<>();
        Component[] components = this.getComponents();

        for (Component comp : components) {
            if (comp instanceof DisplayNode && ((DisplayNode) comp).isSelected()) {
                selectedComponents.add(comp);
            } else if (comp instanceof IDisplayEdge && ((IDisplayEdge) comp).isSelected()) {
                selectedComponents.add(comp);
            }
        }

        return selectedComponents;
    }

    /**
     * @param displayEdge Ibid.
     * @return the model edge for the given display edge.
     */
    public final Edge getModelEdge(IDisplayEdge displayEdge) {
        return (Edge) this.getDisplayToModel().get(displayEdge);
    }

    private boolean isAllowMultipleNodeSelection() {
        return allowMultipleSelection;
    }

    /**
     * Returns true iff nodes and edges may be added/removed by the user or
     * node/edge properties edited.
     *
     * @return Ibid.
     */
    private boolean isAllowDoubleClickActions() {
        return allowDoubleClickActions;
    }

    /**
     * Returns true iff nodes may be dragged to new locations by the user.
     *
     * @return Ibid.
     */
    private boolean isAllowNodeDragging() {
        return allowNodeDragging;
    }

    /**
     * Returns true iff nodes and edges may be selected by the user.
     *
     * @return Ibid.
     */
    private boolean isAllowNodeEdgeSelection() {
        return allowNodeEdgeSelection;
    }

    /**
     * Returns true iff edge reorientations are permitted.
     *
     * @return Ibid.
     */
    private boolean isAllowEdgeReorientation() {
        return allowEdgeReorientations;
    }

    /**
     * Returns true iff multiple nodes may be selected by the user using a
     * rubberband.
     *
     * @return Ibid.
     */
    public boolean isAllowMultipleSelection() {
        return allowMultipleSelection;
    }

    /**
     * Sets whether adding or removing of nodes or edges will be allowed, or
     * node/edge properties edited.
     *
     * @param allowDoubleClickActions Ibid.
     */
    public final void setAllowDoubleClickActions(boolean allowDoubleClickActions) {
        if (this.isAllowDoubleClickActions() && !allowDoubleClickActions) {
            // unregisterKeys();
            this.allowDoubleClickActions = false;
        } else if (!this.isAllowDoubleClickActions() && allowDoubleClickActions) {
            // registerKeys();
            this.allowDoubleClickActions = true;
        }
    }

    /**
     * Sets whether edge reorientations are permitted.
     *
     * @param allowEdgeReorientations Ibid.
     */
    public final void setAllowEdgeReorientations(boolean allowEdgeReorientations) {
        this.allowEdgeReorientations = allowEdgeReorientations;
    }

    /**
     * Sets whether nodes may be dragged by the user to new locations.
     *
     * @param allowNodeDragging Ibid.
     */
    public void setAllowNodeDragging(boolean allowNodeDragging) {
        this.allowNodeDragging = allowNodeDragging;
    }

    /**
     * Sets whether nodes and edges may be selected by the user.
     *
     * @param allowNodeEdgeSelection Ibid.
     */
    public void setAllowNodeEdgeSelection(boolean allowNodeEdgeSelection) {
        this.allowNodeEdgeSelection = allowNodeEdgeSelection;
    }

    /**
     * Sets the display workbench graph to the <code>graph</code> and then
     * notifies listeners of the change. (Called when the workbench is first
     * constructed as well as whenever the workbench model is changed.)
     *
     * @param graph Ibid.
     */
    public final void setGraph(Graph graph) {
        this.setGraphWithoutNotify(graph);

        // if this workbench is sitting inside of a scrollpane,
        // let the scrollpane know how big it is.
        this.scrollRectToVisible(this.getVisibleRect());
        this.registerKeys();
        this.firePropertyChange("graph", null, graph);
        this.firePropertyChange("modelChanged", null, null);
    }

    /**
     * Sets the label for an edge to a particular JComponent. The label will be
     * displayed halfway along the edge slightly off to the side.
     *
     * @param modelEdge the edge for the label.
     * @param label     the label for the component.
     */
    public final void setEdgeLabel(Edge modelEdge, JComponent label) {
        if (modelEdge == null) {
            throw new NullPointerException("Attempt to set a label on a " + "null model edge: " + modelEdge);
        } else if (!this.getModelEdgesToDisplay().containsKey(modelEdge)) {
            throw new IllegalArgumentException("Attempt to set a label on " + "a model edge that's not " + "in the editor: " + modelEdge);
        }

        // retrieve display edge from map, or create one if not
        // there...
        DisplayEdge displayEdge = (DisplayEdge) this.getModelEdgesToDisplay().get(modelEdge);

        GraphEdgeLabel oldLabel = this.getEdgeLabel(displayEdge);

        if (oldLabel != null) {
            this.remove(oldLabel);
        }

        if (label != null) {
            GraphEdgeLabel edgeLabel = new GraphEdgeLabel(displayEdge, label);
            edgeLabel.setSize(edgeLabel.getPreferredSize());
            this.add(edgeLabel, 0);
            this.setEdgeLabel(displayEdge, edgeLabel);
        }

        this.revalidate();
        this.repaint();
    }

    /**
     * Node tooltip to show the node attributes - Added by Kong
     *
     * @param modelNode
     * @param toolTipText
     */
    public final void setNodeToolTip(Node modelNode, String toolTipText) {
        if (modelNode == null) {
            throw new NullPointerException("Attempt to set a label on a " + "null model node: " + modelNode);
        } else if (!this.getModelNodesToDisplay().containsKey(modelNode)) {
            throw new IllegalArgumentException("Attempt to set a label on " + "a model node that's not " + "in the editor: " + modelNode);
        }

        DisplayNode displayNode = (DisplayNode) this.getModelNodesToDisplay().get(modelNode);

        displayNode.setToolTipText(toolTipText);
    }

    /**
     * Edge tooltip to show the edge type and probabilities - Added by Zhou
     *
     * @param modelEdge
     * @param toolTipText
     */
    public final void setEdgeToolTip(Edge modelEdge, String toolTipText) {
        if (modelEdge == null) {
            throw new NullPointerException("Attempt to set a label on a " + "null model edge: " + modelEdge);
        } else if (!this.getModelEdgesToDisplay().containsKey(modelEdge)) {
            throw new IllegalArgumentException("Attempt to set a label on " + "a model edge that's not " + "in the editor: " + modelEdge);
        }

        DisplayEdge displayEdge = (DisplayEdge) this.getModelEdgesToDisplay().get(modelEdge);

        displayEdge.setToolTipText(toolTipText);
    }

    /**
     * Sets the label for a node to a particular JComponent. The label will be
     * displayed slightly off to the right of the node.
     *
     * @param label Ibid.
     */
    public final void setNodeLabel(Node modelNode, JComponent label, int x, int y) {
        if (modelNode == null) {
            throw new NullPointerException("Attempt to set a label on a " + "null model node.");
        } else if (!this.getModelNodesToDisplay().containsKey(modelNode)) {
            return;
        }

        // retrieve display node from map, or create one if not
        // there...
        DisplayNode displayNode = (DisplayNode) this.getModelNodesToDisplay().get(modelNode);
        GraphNodeLabel oldLabel = this.getNodeLabel(displayNode);

        if (oldLabel != null) {
            this.remove(oldLabel);
        }

        if (label != null) {
            GraphNodeLabel nodeLabel = new GraphNodeLabel(displayNode, label, x, y);
            nodeLabel.setSize(nodeLabel.getPreferredSize());
            this.add(nodeLabel, 0);
            this.setNodeLabel(displayNode, nodeLabel);
        }

        this.revalidate();
        this.repaint();
    }

    /**
     * Sets the stoke width for an edge.
     *
     * @param edge  The edge in question.
     * @param width The stroke width. By detault this is 1.0f. 5.0f is pretty
     *              thick.
     */
    public final void setStrokeWidth(Edge edge, float width) {
        IDisplayEdge displayEdge = (IDisplayEdge) this.getModelEdgesToDisplay().get(edge);
        displayEdge.setStrokeWidth(width);
    }

    private void setEdgeLabel(IDisplayEdge displayEdge, GraphEdgeLabel edgeLabel) {
        this.getDisplayToLabels().put(displayEdge, edgeLabel);
    }

    private GraphEdgeLabel getEdgeLabel(IDisplayEdge displayEdge) {
        GraphEdgeLabel label = (GraphEdgeLabel) this.getDisplayToLabels().get(displayEdge);
        return label;
    }

    private void setNodeLabel(DisplayNode displayNode, GraphNodeLabel nodeLabel) {
        this.getDisplayToLabels().put(displayNode, nodeLabel);
    }

    private GraphNodeLabel getNodeLabel(DisplayNode displayNode) {
        GraphNodeLabel label = (GraphNodeLabel) this.getDisplayToLabels().get(displayNode);
        return label;
    }

    /**
     * Removes the label from an edge.
     *
     * @param edge the edge from which a label is to be removed.
     */
    private void removeEdgeLabel(Edge edge) {
        IDisplayEdge displayEdge = (IDisplayEdge) this.getModelEdgesToDisplay().get(edge);
        GraphEdgeLabel edgeLabel = this.getEdgeLabel(displayEdge);

        if (edgeLabel == null) {
            return;
        }

        this.remove(edgeLabel);
        this.getDisplayToLabels().remove(displayEdge);
    }

    /**
     * Sets the mode of the workbench to the indicated new mode. (Ignores
     * unrecognized modes.)
     *
     * @param workbenchMode One of SELECT_MOVE, ADD_NODE, ADD_EDGE.
     */
    public final void setWorkbenchMode(int workbenchMode) {
        if (workbenchMode == SELECT_MOVE) {
            if (this.workbenchMode != SELECT_MOVE) {
                this.workbenchMode = SELECT_MOVE;
                this.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                this.deselectAll();
            } else {
                this.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        } else if (workbenchMode == ADD_NODE) {
            if (this.workbenchMode != ADD_NODE) {
                this.workbenchMode = ADD_NODE;
                this.deselectAll();
            }
        } else if (workbenchMode == ADD_EDGE) {
            if (this.workbenchMode != ADD_EDGE) {
                this.workbenchMode = ADD_EDGE;
                this.deselectAll();
            }
        } else {
            throw new IllegalArgumentException("Must be SELECT_MOVE, " + "ADD_NODE, or ADD_EDGE.");
        }
    }

    public final Map<Edge, Object> getModelEdgesToDisplay() {
        return modelEdgesToDisplay;
    }

    public final Map getModelNodesToDisplay() {
        return modelNodesToDisplay;
    }

    private Map getDisplayToModel() {
        return displayToModel;
    }

    /**
     * Sets the maximum x value (for dragging).
     *
     * @param maxX the maximum x value (Must be greater than or equal to 100).
     */
    private void setMaxX(int maxX) {
        if (maxX < 100) {
            throw new IllegalArgumentException();
        }

        this.maxX = maxX;
    }

    /**
     * Selects the editor node corresponding to the given model node.
     */
    public final void selectNode(Node modelNode) {
        if (!this.isAllowNodeEdgeSelection()) {
            return;
        }

        DisplayNode graphNode = (DisplayNode) this.getModelNodesToDisplay().get(modelNode);

        if (graphNode != null) {
            graphNode.setSelected(true);
        }
    }

    /**
     * Selects the editor edge corresponding to the given model edge.
     */
    public final void selectEdge(Edge modelEdge) {
        IDisplayEdge graphEdge = (IDisplayEdge) this.getModelEdgesToDisplay().get(modelEdge);
        graphEdge.setSelected(true);
    }

    /**
     * Selects all and only those edges that are connecting selected nodes.
     * Should be called after every time the node selection is changed.
     */
    public final void selectConnectingEdges() {
        if (!this.isAllowNodeEdgeSelection()) {
            return;
        }

        Component[] components = this.getComponents();

        for (Component comp : components) {
            if (comp instanceof IDisplayEdge) {
                IDisplayEdge graphEdge = (IDisplayEdge) comp;
                DisplayNode node1 = graphEdge.getComp1();
                DisplayNode node2 = graphEdge.getComp2();

                if (node2 != null) {
                    boolean selected = node1.isSelected() && node2.isSelected();
                    graphEdge.setSelected(selected);
                }
            }
        }
    }

    /**
     * Selects all and only those edges that are connecting selected nodes.
     * Should be called after every time the node selection is changed.
     */
    private void selectConnectingEdges(List<DisplayNode> displayNodes) {
        if (!this.isAllowNodeEdgeSelection()) {
            return;
        }

        Component[] components = this.getComponents();

        for (Component comp : components) {
            if (comp instanceof IDisplayEdge) {
                IDisplayEdge graphEdge = (IDisplayEdge) comp;
                DisplayNode node1 = graphEdge.getComp1();
                DisplayNode node2 = graphEdge.getComp2();

                if (node1 instanceof GraphNodeError) {
                    continue;
                }

                if (node2 instanceof GraphNodeError) {
                    continue;
                }

                if (node2 != null) {
                    boolean selected = displayNodes.contains(node1) && displayNodes.contains(node2);
                    graphEdge.setSelected(selected);
                }
            }
        }
    }

    /**
     * Paints the background of the workbench.
     */
    public final void paint(Graphics g) {
        g.setColor(this.getBackground());
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
        super.paint(g);
    }

    /**
     * Scrolls the workbench image so that the given node is in view, then
     * selects that node.
     *
     * @param modelNode the model node to show.
     */
    public final void scrollWorkbenchToNode(Node modelNode) {
        Object o = this.getModelNodesToDisplay().get(modelNode);
        DisplayNode displayNode = (DisplayNode) o;

        if (displayNode != null) {
            Rectangle bounds = displayNode.getBounds();
            this.scrollRectToVisible(bounds);
            this.deselectAll();

            if (this.isAllowNodeEdgeSelection()) {
                displayNode.setSelected(true);
            }
        }
    }

    /**
     * Sets the maximum x value (for dragging).
     *
     * @param maxY the maximum Y value (Must be greater than or equal to 100).
     */
    private void setMaxY(int maxY) {
        if (maxY < 100) {
            throw new IllegalArgumentException();
        }

        this.maxY = maxY;
    }

    public void setBackground(Color color) {
        super.setBackground(color);
        this.repaint();
    }

    public Color getBackground() {
        return super.getBackground();
    }

    public void layoutByGraph(Graph layoutGraph) {
        GraphUtils.arrangeBySourceGraph(graph, layoutGraph);

        for (Node modelNode : graph.getNodes()) {
            DisplayNode displayNode = (DisplayNode) this.getModelNodesToDisplay().get(modelNode);

            if (displayNode == null) {
                continue;
            }

            Dimension dim = displayNode.getPreferredSize();

            int centerX = modelNode.getCenterX();
            int centerY = modelNode.getCenterY();

            displayNode.setSize(dim);
            displayNode.setLocation(centerX - dim.width / 2, centerY - dim.height / 2);
        }

        // setGraphWithoutNotify(graph);
    }

    public IKnowledge getKnowledge() {
        return null;
    }

    public Graph getSourceGraph() {
        return this.getGraph();
    }

    /**
     * Not implemented for the workbench.
     */
    public void layoutByKnowledge() {
        // Do nothing.
    }

    public Rectangle getVisibleRect() {
        List<Node> nodes = graph.getNodes();

        if (nodes.isEmpty()) {
            return new Rectangle();
        }

        DisplayNode displayNode = (DisplayNode) this.getModelNodesToDisplay().get(nodes.get(0));
        Rectangle rect = displayNode.getBounds();

        for (int i = 1; i < nodes.size(); i++) {
            displayNode = (DisplayNode) this.getModelNodesToDisplay().get(nodes.get(i));
            rect = rect.union(displayNode.getBounds());
        }

        rect = rect.union(super.getVisibleRect());

        return rect;

        // return super.getVisibleRect();
    }

    public void scrollNodesToVisible(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        DisplayNode displayNode = (DisplayNode) this.getModelNodesToDisplay().get(nodes.get(0));
        Rectangle rect = displayNode.getBounds();

        for (int i = 1; i < nodes.size(); i++) {
            displayNode = (DisplayNode) this.getModelNodesToDisplay().get(nodes.get(i));
            rect = rect.union(displayNode.getBounds());
        }

        this.adjustPreferredSize();
        this.scrollRectToVisible(rect);
    }

    public Component getComponent(Edge edge) {
        return (DisplayEdge) modelEdgesToDisplay.get(edge);
    }

    public Component getComponent(Node node) {
        return (DisplayNode) modelNodesToDisplay.get(node);
    }

    public abstract Node getNewModelNode();

    public abstract DisplayNode getNewDisplayNode(Node modelNode);

    public abstract IDisplayEdge getNewDisplayEdge(Edge modelEdge);

    public abstract Edge getNewModelEdge(Node node1, Node node2);

    public abstract IDisplayEdge getNewTrackingEdge(DisplayNode displayNode, Point mouseLoc);

    // ============================PRIVATE METHODS=========================//

    /**
     * Sets the display workbench model to the indicated model. (Called when the
     * workbench is first constructed as well as whenever the workbench model is
     * changed.)
     */
    private void setGraphWithoutNotify(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph model cannot be null.");
        }

        if (graph instanceof SessionWrapper) {
            this.graph = graph;
        } else {
            this.graph = graph;

            if (graph.isPag()) {
                GraphUtils.addPagColoring(new EdgeListGraph(graph));
            }
        }

        modelEdgesToDisplay = new HashMap<>();
        modelNodesToDisplay = new HashMap<>();
        displayToModel = new HashMap();
        displayToLabels = new HashMap();

        this.removeAll();
        graph.addPropertyChangeListener(propChangeHandler);

        // extract the current contents from the model...
        List<Node> nodes = graph.getNodes();
        for (Node node : nodes) {
            if (!this.getModelNodesToDisplay().containsKey(node)) {
                this.addNode(node);
            }
        }

        Set<Edge> edges = graph.getEdges();
        for (Edge edge : edges) {
            if (!this.getModelEdgesToDisplay().containsKey(edge)) {
                this.addEdge(edge);
            }
        }

        this.adjustPreferredSize();

        if (this.getPreferredSize().getWidth() > this.getMaxX()) {
            this.setMaxX((int) this.getPreferredSize().getWidth());
        }

        if (this.getPreferredSize().getHeight() > this.getMaxY()) {
            this.setMaxY((int) this.getPreferredSize().getHeight());
        }

        // Create a graph's legend
        if (graph.getAllAttributes().size() > 0) {
            int maxX = this.getMaxX();
            int maxY = this.getMaxY();

            final int margin = 5;

            DisplayLegend legend = new DisplayLegend(graph.getAllAttributes());
            legend.setLocation(margin, margin);

            // add the display node
            this.add(legend, 0);

        }

        this.revalidate();
        this.repaint();
    }

    /**
     * @return the maximum x value (for dragging).
     */
    private int getMaxX() {
        return maxX;
    }

    /**
     * Adjusts the bounds of the workbench to included the point (0, 0) and the
     * union of the bounds rectangles of all of the components in the workbench.
     * This allows for scrollbars to automatically reflect the position of a
     * component which is being dragged.
     */
    private void adjustPreferredSize() {
        Component[] components = this.getComponents();
        Rectangle r = new Rectangle(0, 0, 400, 400);

        for (Component component1 : components) {
            r = r.union(component1.getBounds());
        }

        // Apparently both of these are required to get the scrollbars to reset.
        // I'm
        // guessing the scrollbars pay attention to preferred size but the
        // setSize() e
        // call throws an event. jdramsey 1/24/2014
        this.setPreferredSize(new Dimension(r.width, r.height));
        this.setSize(new Dimension(r.width, r.height));
    }

    /**
     * Adds a session node to the workbench centered at the specified location;
     * the type of node added is determined by the mode of the workbench.
     *
     * @param loc the location of the center of the session node.
     * @return the added node.
     */
    private Node addNode(Point loc) throws IllegalArgumentException {
        Node modelNode = this.getNewModelNode();

        if (modelNode.getNodeType() == NodeType.MEASURED && !this.isAddMeasuredVarsAllowed()) {
            throw new IllegalArgumentException("Attempt to add measured variable " + "when this has been disallowed.");
        }

        // Add the model node to the display workbench model.
        modelNode.setCenterX(loc.x);
        modelNode.setCenterY(loc.y);
        this.getGraph().addNode(modelNode);
        this.firePropertyChange("modelChanged", null, null);

        return modelNode;
    }

    /**
     * Adds the given model node to the model and adds a corresponding display
     * node to the display.
     *
     * @param modelNode the model node.
     */
    private void addNode(Node modelNode) {
        if (this.getModelNodesToDisplay().containsKey(modelNode)) {
            return;
        }

        if (modelNode.getNodeType() == NodeType.MEASURED && !this.isAddMeasuredVarsAllowed()) {
            throw new IllegalArgumentException("Attempt to add measured variable " + "when this has been disallowed.");
        }

        // Pick a location for the node:
        // (1) If the node has a location in the workbench info object, use
        // that.
        // (2) If not, then if the node is an error term, pick a location
        // that's down and to the right of its associated term.
        // (3) If it's an error term that doesn't have an associated node,
        // don't add it.
        // (4) Otherwise, pick a random location.
        int centerX = modelNode.getCenterX();
        int centerY = modelNode.getCenterY();

        // Construct a display node for the model node.
        DisplayNode displayNode = this.getNewDisplayNode(modelNode);

        // Link the display node to the model node.
        this.getModelNodesToDisplay().put(modelNode, displayNode);
        this.getDisplayToModel().put(displayNode, modelNode);

        // Set the bounds of the display node.
        Dimension dim = displayNode.getPreferredSize();

        displayNode.setSize(dim);
        displayNode.setLocation(centerX - dim.width / 2, centerY - dim.height / 2);

        // add the display node
        this.add(displayNode, 0);

        this.snapNodeToGrid(displayNode);

        // Add listeners.
        displayNode.addComponentListener(compHandler);
        displayNode.addMouseListener(mouseHandler);
        displayNode.addMouseMotionListener(mouseMotionHandler);
        displayNode.addPropertyChangeListener(propChangeHandler);

        this.adjustForNewModelNodes();

        this.repaint();
        this.validate();

        // snapNodeToGrid(displayNode);
        // // Fire notification event. jdramsey 12/11/01
        this.firePropertyChange("nodeAdded", null, displayNode);
        this.firePropertyChange("allNodesAdded", null, null);
    }

    private void adjustForNewModelNodes() {
        graph.getNodes().forEach(node -> {
            if (modelNodesToDisplay.get(node) == null) {
                int centerX = node.getCenterX();
                int centerY = node.getCenterY();

                // Construct a display node for the model node.
                DisplayNode displayNode = this.getNewDisplayNode(node);

                // Link the display node to the model node.
                modelNodesToDisplay.put(node, displayNode);
                displayToModel.put(displayNode, node);

                // Set the bounds of the display node.
                Dimension dim = displayNode.getPreferredSize();

                displayNode.setSize(dim);
                displayNode.setLocation(centerX - dim.width / 2, centerY - dim.height / 2);

                // add the display node
                this.add(displayNode, 0);

                // Add listeners.
                displayNode.addComponentListener(compHandler);
                displayNode.addMouseListener(mouseHandler);
                displayNode.addMouseMotionListener(mouseMotionHandler);
                displayNode.addPropertyChangeListener(propChangeHandler);

                // Fire notification event. jdramsey 12/11/01
                this.firePropertyChange("nodeAdded", null, displayNode);
            }
        });

        Map<DisplayNode, Node> trashMap = new HashMap<>();
        displayToModel.forEach((k, v) -> {
            if (k instanceof DisplayNode && v instanceof Node) {
                DisplayNode displayNode = (DisplayNode) k;
                Node node = (Node) v;

                if (!graph.containsNode(node)) {
                    trashMap.put(displayNode, node);
                }
            }
        });

        trashMap.forEach((k, v) -> {
            displayToModel.remove(k);
            modelNodesToDisplay.remove(v);
            this.firePropertyChange("nodeRemoved", null, k);
        });

        graph.getNodes().forEach(node -> {
            int centerX = node.getCenterX();
            int centerY = node.getCenterY();
            DisplayNode displayNode = (DisplayNode) modelNodesToDisplay.get(node);

            // Set the bounds of the display node.
            Dimension dim = displayNode.getPreferredSize();

            displayNode.setSize(dim);
            displayNode.setLocation(centerX - dim.width / 2, centerY - dim.height / 2);
        });
    }

    /**
     * Adds the specified edge to the model and updates the display to match.
     *
     * @param modelEdge the mode edge.
     */
    private void addEdge(Edge modelEdge) {
        if (modelEdge == null) {
            return;
        }

        if (modelEdge.getNode1() == modelEdge.getNode2()) {
            return;
            // throw new IllegalArgumentException(
            // "Edges to self are not supported.");
        }

        if (this.getModelEdgesToDisplay().containsKey(modelEdge)) {
            return;
        }

        if (!this.getGraph().containsEdge(modelEdge)) {
            throw new IllegalArgumentException("Attempt to add edge not in model.");
        }

        // construct a display edge for the model edge
        Node modelNodeA = modelEdge.getNode1();
        Node modelNodeB = modelEdge.getNode2();

        DisplayNode displayNodeA = this.displayNode(modelNodeA);
        DisplayNode displayNodeB = this.displayNode(modelNodeB);

        if ((displayNodeA == null) || (displayNodeB == null)) {
            return;
        }

        IDisplayEdge displayEdge = this.getNewDisplayEdge(modelEdge);
        if (displayEdge == null) {
            return;
        }

        if (graph.isHighlighted(modelEdge)) {
            displayEdge.setHighlighted(true);
        }

        boolean bold = modelEdge.getProperties().contains(Property.dd) || modelEdge.isBold();

        Color lineColor = modelEdge.getProperties().contains(Property.nl) ? Color.green
                : graph.isHighlighted(modelEdge) ? displayEdge.getHighlightedColor() : modelEdge.getLineColor();

        displayEdge.setLineColor(lineColor);
        displayEdge.setBold(bold);

        // Link the display edge to the model edge.
        this.getModelEdgesToDisplay().put(modelEdge, displayEdge);
        this.getDisplayToModel().put(displayEdge, modelEdge);

        // Add the display edge to the workbench. (Add it to the "back".)
        this.add((Component) displayEdge, -1);

        // Add listeners.
        ((Component) displayEdge).addComponentListener(compHandler);
        ((Component) displayEdge).addMouseListener(mouseHandler);
        ((Component) displayEdge).addMouseMotionListener(mouseMotionHandler);
        ((Component) displayEdge).addPropertyChangeListener(propChangeHandler);

        // Reset offsets (for multiple edges between node pairs).
        this.resetEdgeOffsets(displayEdge);

        // Fire notification. jdramsey 12/11/01
        this.firePropertyChange("edgeAdded", null, displayEdge);
    }

    /**
     * Scans through all edges between two nodes, resets those edge's offset
     * values. Note that these offsets are stored in the edges themselves so
     * this does not have to be recomputed all the time
     */
    private void resetEdgeOffsets(IDisplayEdge graphEdge) {
        try {
            DisplayNode displayNode1 = graphEdge.getNode1();
            DisplayNode displayNode2 = graphEdge.getNode2();

            Node node1 = displayNode1.getModelNode();
            Node node2 = displayNode2.getModelNode();

            Graph graph = this.getGraph();

            List<Edge> edges = graph.getEdges(node1, node2);

            for (int i = 0; i < edges.size(); i++) {
                Edge edge = edges.get(i);
                Node _node1 = edge.getNode1();
                boolean awayFrom = (_node1 == node1);

                IDisplayEdge displayEdge = (IDisplayEdge) this.getModelEdgesToDisplay().get(edge);

                if (displayEdge != null) {
                    displayEdge.setOffset(calcEdgeOffset(i, edges.size(), awayFrom));
                }
            }
        } catch (UnsupportedOperationException e) {
            // This happens for the session workbench. The getEdges() method
            // is not implemented for it. Not sure if we'll ever need it to
            // be implemented. jdramsey 4/14/2004
        }
    }

    /**
     * Calculates the offset in pixels of a given edge - this could use a little
     * tweaking still.
     *
     * @param i the index of the given edge
     * @param n the number of edges
     */
    private static double calcEdgeOffset(int i, int n, boolean away_from) {
        double offset = (double) 35 * (2.0 * (double) i + 1.0 - (double) n) / 2.0 / (double) n;

        double direction = away_from ? 1.0 : -1.0;
        return direction * offset;
    }

    private DisplayNode displayNode(Node modelNodeA) {
        Object o = this.getModelNodesToDisplay().get(modelNodeA);

        if (o == null) {
            this.reconstiteMaps();
            o = this.getModelNodesToDisplay().get(modelNodeA);
        }

        return (DisplayNode) o;
    }

    /**
     * Calculates the distance between two points.
     *
     * @param p1 the 'from' point.
     * @param p2 the 'to' point.
     * @return the distance between p1 and p2.
     */
    private static double distance(Point p1, Point p2) {
        double d = (p1.x - p2.x) * (p1.x - p2.x);
        d += (p1.y - p2.y) * (p1.y - p2.y);
        d = Math.sqrt(d);
        return d;
    }

    /**
     * Finds the nearest node to a given point. More specifically, finds the
     * node whose center point is nearest to the given point. (If more than one
     * such node exists, the one with lowest z-order is returned.)
     *
     * @param p the point for which the nearest node is requested.
     * @return the nearest node to point p.
     */
    private DisplayNode findNearestNode(Point p) {
        Component[] components = this.getComponents();
        double distance, leastDistance = Double.POSITIVE_INFINITY;
        int index = -1;

        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof DisplayNode) {
                DisplayNode node = (DisplayNode) components[i];

                distance = distance(p, node.getCenterPoint());

                if (distance < leastDistance) {
                    leastDistance = distance;
                    index = i;
                }
            }
        }

        if (index != -1) {
            return (DisplayNode) (components[index]);
        } else {
            return null;
        }
    }

    /**
     * Finishes drawing a rubberband.
     *
     * @see #startRubberband
     */
    private void finishRubberband() {
        if (rubberband != null) {
            this.remove(rubberband);
            rubberband = null;
            this.repaint();
        }
    }

    /**
     * Finishes the drawing of a new edge.
     *
     * @see #startEdge
     */
    private void finishEdge() {
        if (this.getTrackedEdge() == null) {
            return;
        }

        // Retrieve the two display components this edge should connect.
        DisplayNode comp1 = this.getTrackedEdge().getComp1();
        Point p = this.getTrackedEdge().getTrackPoint();
        DisplayNode comp2 = this.findNearestNode(p);

        // Edges to self are not supported.
        if (comp1 != comp2) {

            // Construct the model edge
            try {
                Node node1 = (Node) (this.getDisplayToModel().get(comp1));
                Node node2 = (Node) (this.getDisplayToModel().get(comp2));
                Edge modelEdge = this.getNewModelEdge(node1, node2);

                // Add model edge to model; this will result in an event fired
                // back from the model to add a display edge, so we can remove
                // the tracked edge and forget about it.
                graph.addEdge(modelEdge);
                this.setGraph(graph);
                this.firePropertyChange("modelChanged", null, null);
            } catch (Exception e) {
                e.printStackTrace();

                if (this.isNodeEdgeErrorsReported()) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
                }
            }
        }

        this.remove((Component) this.getTrackedEdge());
        this.repaint();

        // reset the tracked edge to null to wait for the next attempt
        // at adding an edge.
        trackedEdge = null;
    }

    /**
     * Fires a property change event, property name = "selectedNodes", with the
     * new node selection as its new value (a List).
     */
    private void fireNodeSelection() {
        Component[] components = this.getComponents();
        List<Node> selection = new LinkedList<>();

        for (Component component : components) {
            if (component instanceof DisplayNode) {
                DisplayNode displayNode = (DisplayNode) component;

                if (displayNode.isSelected()) {
                    Node modelNode = (Node) (this.getDisplayToModel().get(displayNode));

                    selection.add(modelNode);
                }
            }
        }

        if (this.isAllowMultipleNodeSelection()) {
            this.firePropertyChange("selectedNodes", null, selection);
        } else {
            if (selection.size() == 1) {
                this.firePropertyChange("selectedNode", null, selection.get(0));
            } else {
                throw new IllegalStateException(
                        "Multiple or null selection detected " + "when single selection mode is set.");
            }
        }
    }

    /**
     * Registers the remove and backspace keys to remove selected objects.
     */
    private void registerKeys() {
        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "DELETE");
        this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DELETE");

        Action deleteAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                AbstractWorkbench workbench = (AbstractWorkbench) e.getSource();

                List<Component> components = workbench.getSelectedComponents();
                int numNodes = 0, numEdges = 0;

                for (Component c : components) {
                    if (c instanceof DisplayNode) {
                        numNodes++;
                    } else if (c instanceof DisplayEdge) {
                        numEdges++;
                    }
                }

                StringBuilder buf = new StringBuilder();

                if (AbstractWorkbench.this.isDeleteVariablesAllowed()) {
                    buf.append("Number of nodes selected = ");
                    buf.append(numNodes);
                }

                buf.append("\nNumber of edges selected = ");
                buf.append(numEdges);
                buf.append("\n\nDelete selected items?");

                int ret = JOptionPane.showConfirmDialog(workbench, buf.toString());

                if (ret != JOptionPane.YES_OPTION) {
                    return;
                }

                AbstractWorkbench.this.deleteSelectedObjects();
            }
        };

        this.getActionMap().put("DELETE", deleteAction);

        if (controlDispatcher == null) {
            controlDispatcher = this::respondToControlKey;
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(controlDispatcher);
    }

    private boolean respondToControlKey(KeyEvent e) {
        if (this.hasFocus()) {
            int keyCode = e.getKeyCode();
            int id = e.getID();

            if (keyCode == KeyEvent.VK_ALT) {
                if (id == KeyEvent.KEY_PRESSED) {
                    workbenchMode = ADD_EDGE;
                    this.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                } else if (id == KeyEvent.KEY_RELEASED) {
                    this.finishEdge();
                    workbenchMode = SELECT_MOVE;
                    this.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        }

        return false;
    }

    AbstractWorkbench getWorkbench() {
        return this;
    }

    /**
     * In response to a request from the model, removes the display node
     * corresponding to the given model node from the display. Assumes that the
     * model node was removed from the model and that this request is coming
     * from the propertyChange() method. DO NOT CALL THIS METHOD DIRECTLY;
     * RATHER, REMOVE THE MODEL NODE DIRECTLY FROM THE MODEL AND LET THE ENSUING
     * EVENTS FROM MODEL TO DISPLAY REMOVE THE NODE FROM THE DISPLAY. OTHERWISE,
     * THE DISPLAY AND MODEL WILL GET OUT OF SYNC!
     *
     * @param modelNode the model node to be removed.
     */
    private void removeNode(Node modelNode) {
        if (modelNode == null) {
            throw new NullPointerException("Attempt to remove a null model node.");
        }

        DisplayNode displayNode = (DisplayNode) (this.getModelNodesToDisplay().get(modelNode));

        if (displayNode == null) {
            this.getModelNodesToDisplay().remove(modelNode);
        } else {
            this.setNodeLabel(modelNode, null, 0, 0);
            this.remove(displayNode);
            this.getDisplayToModel().remove(displayNode);
            this.getModelEdgesToDisplay().remove(modelNode);
            displayNode.removePropertyChangeListener(propChangeHandler);
            this.repaint();

            // Fire notification.
            this.firePropertyChange("nodeRemoved", displayNode, null);
        }
    }

    /**
     * Removes the given display node from the workbench by requesting that the
     * model remove the corresponding model node.
     *
     * @param displayNode the display node.
     */
    private void removeNode(DisplayNode displayNode) {
        if (displayNode == null) {
            return;
        }

        Node modelNode = (Node) (this.getDisplayToModel().get(displayNode));

        if (modelNode == null) {
            return;
        }

        // Error nodes cannot be removed explicitly; they must be removed by
        // removing the nodes they are attached to.
        if (!(modelNode.getNodeType() == NodeType.ERROR)) {
            this.getGraph().removeNode(modelNode);
        }

        this.adjustForNewModelNodes();

        this.firePropertyChange("modelChanged", null, null);
    }

    /**
     * In response to a request from the model, removes the display edge
     * corresponding to the given model edge from the display. Assumes that the
     * model edge was removed from the model and that this request is coming
     * from the propertyChange() method. DO NOT CALL THIS METHOD DIRECTLY;
     * RATHER, REMOVE THE MODEL EDGE FROM THE MODEL AND LET THE ENSUING EVENTS
     * (FROM MODEL TO DISPLAY) REMOVE THE EDGE FROM THE DISPLAY. OTHERWISE, THE
     * DISPLAY AND MODEL WILL GET OUT OF SYNC.
     */
    private void removeEdge(Edge modelEdge) {
        if (modelEdge == null) {
            return;
        }

        IDisplayEdge displayEdge = (IDisplayEdge) (this.getModelEdgesToDisplay().get(modelEdge));

        if (displayEdge == null) {
            this.getModelEdgesToDisplay().remove(modelEdge);
        } else {
            this.removeEdgeLabel(modelEdge);
            this.remove((Component) displayEdge);
            this.getDisplayToModel().remove(displayEdge);
            this.getModelEdgesToDisplay().remove(modelEdge);

            ((Component) displayEdge).removePropertyChangeListener(propChangeHandler);
            this.repaint();
            this.firePropertyChange("edgeRemoved", displayEdge, null);
        }
    }

    /**
     * Removes the given display edge from the workbench by requesting that the
     * model remove the corresponding model edge.
     */
    private void removeEdge(IDisplayEdge displayEdge) {
        if (displayEdge == null) {
            return;
        }

        Edge modelEdge = (Edge) (this.getDisplayToModel().get(displayEdge));

        try {
            this.getGraph().removeEdge(modelEdge);
            this.firePropertyChange("modelChanged", null, null);
        } catch (Exception e) {
            if (this.isNodeEdgeErrorsReported()) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
            }
        }
    }

    /**
     * Selects all of the nodes inside the rubberband and all edges connecting
     * selected nodes.
     *
     * @param rubberband The rubberband shape appearing in the GUI.
     * @param edgesOnly  Whether the shift key is down.
     */
    private void selectAllInRubberband(Rubberband rubberband, boolean edgesOnly) {
        if (!this.isAllowNodeEdgeSelection()) {
            return;
        }

        if (!edgesOnly) {
            this.deselectAll();
        }

        Shape rubberShape = rubberband.getShape();
        Point rubberLoc = rubberband.getLocation();
        Component[] components = this.getComponents();
        List<DisplayNode> selectedNodes = new ArrayList<>();

        for (Component comp : components) {
            if (comp instanceof DisplayNode) {
                Rectangle bounds = comp.getBounds();
                bounds.translate(-rubberLoc.x, -rubberLoc.y);
                DisplayNode graphNode = (DisplayNode) comp;

                if (rubberShape.intersects(bounds)) {
                    selectedNodes.add(graphNode);
                }
            }
        }

        if (edgesOnly) {
            this.selectConnectingEdges(selectedNodes);
        } else {
            for (DisplayNode graphNode : selectedNodes) {
                graphNode.setSelected(true);
            }

            this.selectConnectingEdges();
        }
    }

    /**
     * @return the maximum y value (for dragging).
     */
    private int getMaxY() {
        return maxY;
    }

    /**
     * Starts a tracked edge by anchoring it to one node and specifying the
     * initial mouse track point.
     *
     * @param node     the initial anchored node.
     * @param mouseLoc the initial tracking mouse location.
     */
    private void startEdge(DisplayNode node, Point mouseLoc) {
        if (this.getTrackedEdge() != null) {
            this.remove((Component) this.getTrackedEdge());
            trackedEdge = null;
            this.repaint();
        }

        trackedEdge = this.getNewTrackingEdge(node, mouseLoc);
        this.add((Component) this.getTrackedEdge(), -1);
        this.deselectAll();
    }

    /**
     * Starts dragging a node.
     *
     * @param p the click point for the drag.
     */
    private void startNodeDrag(Point p) {
        if (!allowNodeDragging) {
            return;
        }

        clickPoint = p;
        dragNodes = this.getSelectedNodes();
    }

    /**
     * Starts drawing a rubberband to allow selection of multiple nodes.
     *
     * @param p the point where the rubberband begins.
     * @see #finishRubberband
     */
    private void startRubberband(Point p) {
        if (rubberband != null) {
            this.remove(rubberband);
            rubberband = null;
            this.repaint();
        }

        if (this.isAllowNodeEdgeSelection() && this.isAllowMultipleNodeSelection()) {
            rubberband = new Rubberband(p);
            this.add(rubberband, 0);
            rubberband.repaint();
        }
    }

    private void snapNodeToGrid(DisplayNode node) {
        final int gridSize = 20;

        int x = node.getCenterPoint().x;
        int y = node.getCenterPoint().y;

        x = gridSize * ((x + gridSize / 2) / gridSize);
        y = gridSize * ((y + gridSize / 2) / gridSize);

        node.setLocation(x - node.getSize().width / 2, y - node.getSize().height / 2);
    }

    private void handleMouseClicked(MouseEvent e) {
        Object source = e.getSource();

        if (!this.isAllowNodeEdgeSelection()) {
            return;
        }

        if (source instanceof DisplayNode) {
            this.nodeClicked(source, e);
        } else if (source instanceof IDisplayEdge) {
            this.edgeClicked(source, e);
        } else {

            // This shouldn't be here, but I can't get it to work higher up.
            if (e.isAltDown() && e.isControlDown() && e.isMetaDown()) {
                if (Preferences.userRoot().getBoolean("experimental", false)) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Setting to published interface on next restart.");
                    Preferences.userRoot().putBoolean("experimental", false);
                } else {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            "Setting to experimental interface on next restart.");
                    Preferences.userRoot().putBoolean("experimental", true);
                }
            }

            this.deselectAll();
        }
    }

    private void edgeClicked(Object source, MouseEvent e) {
        IDisplayEdge graphEdge = (IDisplayEdge) (source);

        if (e.getClickCount() == 2) {
            this.deselectAll();
            graphEdge.launchAssociatedEditor();
            this.firePropertyChange("edgeLaunch", graphEdge, graphEdge);
        } else {
            if (this.isAllowEdgeReorientation()) {
                this.reorientEdge(source, e);
            }

            if (graphEdge.isSelected()) {
                graphEdge.setSelected(false);
            } else if (e.isShiftDown()) {
                graphEdge.setSelected(true);
            } else {
                this.deselectAll();
                graphEdge.setSelected(true);
            }
        }
    }

    private void nodeClicked(Object source, MouseEvent e) {
        DisplayNode node = (DisplayNode) source;

        if (e.getClickCount() == 2) {
            if (this.isAllowDoubleClickActions()) {
                this.doDoubleClickAction(node);
            }
        } else {
            if (node.isSelected()) {
                node.setSelected(false);
                this.selectConnectingEdges();
                this.fireNodeSelection();
            } else {
                if (!e.isShiftDown()) {
                    this.deselectAll();
                }

                node.setSelected(true);
                this.selectConnectingEdges();
                this.fireNodeSelection();
            }
        }
    }

    private void reorientEdge(Object source, MouseEvent e) {
        IDisplayEdge graphEdge = (IDisplayEdge) (source);
        Point point = e.getPoint();
        PointPair connectedPoints = graphEdge.getConnectedPoints();
        Point pointA = connectedPoints.getFrom();
        Point pointB = connectedPoints.getTo();
        double length = distance(pointA, pointB);
        double endpointRadius = Math.min(20.0, length / 3.0);

        if (e.isShiftDown()) {
            if (distance(point, pointA) < endpointRadius) {
                this.toggleEndpoint(graphEdge, 1);
                this.firePropertyChange("modelChanged", null, null);
            } else if (distance(point, pointB) < endpointRadius) {
                this.toggleEndpoint(graphEdge, 2);
                this.firePropertyChange("modelChanged", null, null);
            }
        } else {
            if (distance(point, pointA) < endpointRadius) {
                this.directEdge(graphEdge, 1);
                this.firePropertyChange("modelChanged", null, null);
            } else if (distance(point, pointB) < endpointRadius) {
                this.directEdge(graphEdge, 2);
                this.firePropertyChange("modelChanged", null, null);
            }
        }
    }

    private void handleMousePressed(MouseEvent e) {
        this.grabFocus();

        Object source = e.getSource();
        Point loc = e.getPoint();

        if (this.isRightClickPopupAllowed() && source == this && SwingUtilities.isRightMouseButton(e)) {
            this.launchPopup(e);
            return;
        }

        switch (workbenchMode) {
            case SELECT_MOVE:
                if (source == this) {
                    this.startRubberband(loc);
                } else if (source instanceof DisplayNode) {
                    this.startNodeDrag(loc);
                }
                break;

            case ADD_NODE:
                if (!this.isAllowDoubleClickActions()) {
                    return;
                }

                if (source == this) {
                    Node node = this.addNode(loc);
                }

                break;

            case ADD_EDGE:
                // if (!isAllowDoubleClickActions()) {
                // return;
                // }

                if (source instanceof IDisplayEdge) {
                    return;
                }

                if (source instanceof DisplayNode) {
                    Point o = ((Component) (source)).getLocation();
                    loc.translate(o.x, o.y);
                }

                DisplayNode nearestNode = this.findNearestNode(loc);

                if (nearestNode != null) {
                    this.startEdge(nearestNode, loc);
                }

                break;
        }
    }

    private void launchPopup(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new LayoutMenu(this));
        popup.show(this, e.getX(), e.getY());
    }

    private void handleMouseReleased(MouseEvent e) {
        Object source = e.getSource();

        switch (workbenchMode) {
            case SELECT_MOVE:
                if (source == this) {
                    this.finishRubberband();
                } else if (source instanceof DisplayNode) {
                    List<DisplayNode> dragNodes = this.dragNodes;

                    if (dragNodes != null && dragNodes.isEmpty()) {
                        this.snapSingleNodeFromNegative(source);
                        this.snapNodeToGrid((DisplayNode) source);
                        this.scrollRectToVisible(((DisplayNode) source).getBounds());
                    } else if (dragNodes != null && !dragNodes.isEmpty()) {
                        this.snapDragGroupFromNegative();

                        Rectangle rect = dragNodes.get(0).getBounds();

                        for (int i = 1; i < dragNodes.size(); i++) {
                            rect = rect.union(dragNodes.get(i).getBounds());
                        }

                        // scrollRectToVisible(rect);
                    }
                }
                break;

            case ADD_EDGE:
                // if (!isAllowDoubleClickActions()) {
                // return;
                // }

                this.finishEdge();
                break;
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        this.setMouseDragging(true);

        Object source = e.getSource();
        Point newPoint = e.getPoint();

        switch (workbenchMode) {
            case SELECT_MOVE:
                this.dragNodes(source, newPoint, e.isShiftDown());
                break;

            case ADD_NODE:
                if (source instanceof DisplayNode && this.getSelectedComponents().isEmpty()) {
                    this.dragNodes(source, newPoint, e.isShiftDown());
                }

                break;

            case ADD_EDGE:
                // if (!isAllowDoubleClickActions()) {
                // return;
                // }

                this.dragNewEdge(source, newPoint);
                break;
        }
    }

    private void handleMouseEntered(MouseEvent e) {
        Object source = e.getSource();

        if (source instanceof DisplayEdge) {
            IDisplayEdge displayEdge = (DisplayEdge) source;
            Edge edge = displayEdge.getModelEdge();
            if (graph.containsEdge(edge)) {
                // Bootstrapping Distribution
                List<EdgeTypeProbability> edgeProb = edge.getEdgeTypeProbabilities();
                if (edgeProb != null) {
                    String endpoint1 = edge.getEndpoint1().toString();
                    switch (endpoint1) {
                        case "Tail":
                            endpoint1 = "-";
                            break;
                        case "Arrow":
                            endpoint1 = "&lt;";
                            break;
                        case "Circle":
                            endpoint1 = "o";
                            break;
                        case "Star":
                            endpoint1 = "&#42;";
                            break;
                        case "Null":
                            endpoint1 = "Null";
                            break;
                    }

                    String endpoint2 = edge.getEndpoint2().toString();
                    switch (endpoint2) {
                        case "Tail":
                            endpoint2 = "-";
                            break;
                        case "Arrow":
                            endpoint2 = "&gt;";
                            break;
                        case "Circle":
                            endpoint2 = "o";
                            break;
                        case "Star":
                            endpoint2 = "&#42;";
                            break;
                        case "Null":
                            endpoint2 = "Null";
                            break;
                    }

                    StringBuilder properties = new StringBuilder();
                    if (edge.getProperties() != null && edge.getProperties().size() > 0) {
                        for (Edge.Property property : edge.getProperties()) {
                            properties.append(" ").append(property.toString());
                        }
                    }

                    final StringBuilder text = new StringBuilder("<html>" + edge.getNode1().getName()
                            + " " + endpoint1 + "-" + endpoint2 + " "
                            + edge.getNode2().getName()
                            + properties
                            + "<br>");
                    final String n1 = edge.getNode1().getName();
                    final String n2 = edge.getNode2().getName();
                    final List<String> nodes = new ArrayList<>();
                    nodes.add(n1);
                    nodes.add(n2);
                    Collections.sort(nodes);
                    for (final EdgeTypeProbability edgeTypeProb : edgeProb) {
                        String _type = "" + edgeTypeProb.getEdgeType();
                        switch (edgeTypeProb.getEdgeType()) {
                            case nil:
                                _type = "no edge";
                                break;
                            case ta:
                                _type = "--&gt;";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            case at:
                                _type = "&lt;--";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            case ca:
                                _type = "o-&gt;";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            case ac:
                                _type = "&lt;-o";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            case cc:
                                _type = "o-o";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            case aa:
                                _type = "&lt;-&gt;";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            case tt:
                                _type = "---";
                                _type = nodes.get(0) + " " + _type + " " + nodes.get(1);
                                break;
                            default:
                                break;
                        }
                        if (edgeTypeProb.getProbability() > 0) {
                            properties = new StringBuilder();
                            if (edgeTypeProb.getProperties() != null && edgeTypeProb.getProperties().size() > 0) {
                                for (final Edge.Property property : edgeTypeProb.getProperties()) {
                                    properties.append(" ").append(property.toString());
                                }
                            }
                            text.append("[").append(_type).append(properties).append("]:").append(String.format("%.4f", edgeTypeProb.getProbability()));
                            text.append("<br>");
                        }
                    }

                    // Commented out by Zhou
//					JLabel edgeTypeDistLabel = new JLabel(text);
//					edgeTypeDistLabel.setOpaque(true);
//					edgeTypeDistLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//					setEdgeLabel(edge, edgeTypeDistLabel);
                    // Use tooltip instead of label - Added by Zhou
                    this.setEdgeToolTip(edge, text.toString());
                }
            }
        }

        if (source instanceof DisplayNode) {
            DisplayNode displayNode = (DisplayNode) source;
            Node node = displayNode.getModelNode();
            if (graph.containsNode(node)) {
                Map<String, Object> attributes = node.getAllAttributes();
                if (!attributes.isEmpty()) {
                    String attribute = "";
                    for (String key : attributes.keySet()) {
                        Object value = attributes.get(key);

                        attribute += key + ": " + value + "<br>";
                    }

                    String text = "<html>Node: " + node.getName() + "<br>" + attribute;

                    this.setNodeToolTip(node, text);
                }
            }
        }
    }

    // SInce we use tooltip to show edge type and probablitites,
    // we no longer need this call. - Commented out by Zhou
//	private void handleMouseExited(MouseEvent e) {
//		Object source = e.getSource();
//
//		if (source instanceof DisplayEdge) {
//			IDisplayEdge displayEdge = (IDisplayEdge) source;
//			Edge edge = displayEdge.getModelEdge();
//			if (graph.containsEdge(edge)) {
//				List<EdgeTypeProbability> edgeProb = edge.getEdgeTypeProbabilities();
//				if (edgeProb != null) {
//					setEdgeLabel(edge, null);
//				}
//			}
//		}
//	}
    private void snapSingleNodeFromNegative(Object source) {
        DisplayNode node = (DisplayNode) source;

        int x = node.getLocation().x;
        int y = node.getLocation().y;

        x = Math.max(x, 0);
        y = Math.max(y, 0);

        node.setLocation(x, y);
    }

    private void snapDragGroupFromNegative() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;

        List<DisplayNode> dragNodes = this.dragNodes;

        if (dragNodes == null) {
            return;
        }

        for (DisplayNode _node : dragNodes) {
            int x = _node.getLocation().x;
            int y = _node.getLocation().y;

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
        }

        minX = Math.min(minX, 0);
        minY = Math.min(minY, 0);

        for (DisplayNode _node : dragNodes) {
            int x = _node.getLocation().x;
            int y = _node.getLocation().y;

            _node.setLocation(x - minX, y - minY);
        }
    }

    private void dragNewEdge(Object source, Point newPoint) {
        if (source instanceof DisplayNode) {
            Point point = ((Component) source).getLocation();
            newPoint.translate(point.x, point.y);
        }

        if (this.getTrackedEdge() != null) {
            this.getTrackedEdge().updateTrackPoint(newPoint);
        }
    }

    private void dragNodes(Object source, Point newPoint, boolean edgesOnly) {
        if (!this.isAllowNodeDragging()) {
            return;
        }

        if (source instanceof DisplayNode) {
            List<DisplayNode> dragNodes = this.dragNodes;

            if (dragNodes == null) {
                return;
            }

            if (!dragNodes.contains(source)) {
                this.moveSingleNode(source, newPoint);
            } else {
                this.moveSelectedNodes(source, newPoint);
            }
        } else if (rubberband != null) {
            rubberband.updateTrackPoint(newPoint);
            this.selectAllInRubberband(rubberband, edgesOnly);
        }
    }

    /**
     * Move a single, unselected node.
     */
    private void moveSingleNode(Object source, Point newPoint) {
        DisplayNode node = (DisplayNode) source;
        int deltaX = newPoint.x - clickPoint.x;
        int deltaY = newPoint.y - clickPoint.y;

        int newX = node.getLocation().x + deltaX;
        int newY = node.getLocation().y + deltaY;

        node.setLocation(newX, newY);
    }

    /**
     * Move a group of selected nodes together.
     */
    private void moveSelectedNodes(Object source, Point newPoint) {
        if (!dragNodes.contains(source)) {
            return;
        }

        int deltaX = newPoint.x - clickPoint.x;
        int deltaY = newPoint.y - clickPoint.y;

        for (DisplayNode _node : dragNodes) {
            int newX = _node.getLocation().x + deltaX;
            int newY = _node.getLocation().y + deltaY;

            // if (newX > getMaxX()) {
            // newX = getMaxX();
            // }
            //
            // if (newY > getMaxY()) {
            // newY = getMaxY();
            // }
            _node.setLocation(newX, newY);
        }
    }

    /**
     * Toggles endpoint A in the following sense: if it's a "o" make it a "-";
     * if it's a "-", make it a ">"; if it's a ">", make it a "-".
     *
     * @param endpoint 1 for endpoint 1, 2 for endpoint 2.
     */
    private void directEdge(IDisplayEdge graphEdge, int endpoint) {
        Edge edge = graphEdge.getModelEdge();

        if (edge == null) {
            throw new IllegalStateException("Graph edge without model edge: " + graphEdge);
        }

        Edge newEdge;

        if (endpoint == 1) {
            newEdge = Edges.directedEdge(edge.getNode2(), edge.getNode1());
        } else if (endpoint == 2) {
            newEdge = Edges.directedEdge(edge.getNode1(), edge.getNode2());
        } else {
            throw new IllegalArgumentException("Endpoint number should be 1 or 2.");
        }

        this.getGraph().removeEdge(edge);

        try {
            boolean added = this.getGraph().addEdge(newEdge);
            if (!added) {
                this.getGraph().addEdge(edge);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Reorienting that edge would violate graph constraints.");
            }
        } catch (IllegalArgumentException e) {
            this.getGraph().addEdge(edge);
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Reorienting that edge would violate graph constraints.");
        }

        this.repaint();
    }

    private void toggleEndpoint(IDisplayEdge graphEdge, int endpointNumber) {
        Edge edge = graphEdge.getModelEdge();
        Edge newEdge;

        if (endpointNumber == 1) {
            Endpoint endpoint = edge.getEndpoint1();
            Endpoint nextEndpoint;

            if (endpoint == Endpoint.TAIL) {
                nextEndpoint = Endpoint.ARROW;
            } else if (endpoint == Endpoint.ARROW) {
                nextEndpoint = Endpoint.CIRCLE;
            } else {
                nextEndpoint = Endpoint.TAIL;
            }

            newEdge = new Edge(edge.getNode1(), edge.getNode2(), nextEndpoint, edge.getEndpoint2());
        } else if (endpointNumber == 2) {
            Endpoint endpoint = edge.getEndpoint2();
            Endpoint nextEndpoint;

            if (endpoint == Endpoint.TAIL) {
                nextEndpoint = Endpoint.ARROW;
            } else if (endpoint == Endpoint.ARROW) {
                nextEndpoint = Endpoint.CIRCLE;
            } else {
                nextEndpoint = Endpoint.TAIL;
            }

            newEdge = new Edge(edge.getNode1(), edge.getNode2(), edge.getEndpoint1(), nextEndpoint);
        } else {
            throw new IllegalArgumentException("Endpoint number should be 1 or 2.");
        }

        this.getGraph().removeEdge(edge);

        try {
            boolean added = this.getGraph().addEdge(newEdge);
            if (!added) {
                this.getGraph().addEdge(edge);
                return;
            }
        } catch (IllegalArgumentException e) {
            this.getGraph().addEdge(edge);
            return;
        }

        this.repaint();
    }

    public boolean isMouseDragging() {
        return mouseDragging;
    }

    private void setMouseDragging(boolean mouseDragging) {
        this.mouseDragging = mouseDragging;
    }

    /**
     * True if the user is allowed to add measured variables.
     */
    private boolean isAddMeasuredVarsAllowed() {
        return addMeasuredVarsAllowed;
    }

    /**
     * True if the user is allowed to add measured variables.
     */
    public void setAddMeasuredVarsAllowed(boolean addMeasuredVarsAllowed) {
        this.addMeasuredVarsAllowed = addMeasuredVarsAllowed;
    }

    /**
     * @return true if the user is allowed to edit existing meausred variables.
     */
    boolean isEditExistingMeasuredVarsAllowed() {
        return editExistingMeasuredVarsAllowed;
    }

    /**
     * @return true iff the user is allowed to delete variables.
     */
    private boolean isDeleteVariablesAllowed() {
        return deleteVariablesAllowed;
    }

    /**
     * True if the user is allowed to delete variables.
     */
    public void setDeleteVariablesAllowed(boolean deleteVariablesAllowed) {
        this.deleteVariablesAllowed = deleteVariablesAllowed;
    }

    public boolean isEnableEditing() {
        return enableEditing;
    }

    public void enableEditing(boolean enableEditing) {
        this.enableEditing = enableEditing;
        this.setEnabled(enableEditing);
    }

    /**
     * This inner class is a simple wrapper for JComponents which are to serve
     * as edge labels in the workbench. Its sole function is to make sure the
     * wrapped JComponents stay in the right place in the workbench--that is,
     * halfway along their respective edge, slightly off to the side.
     *
     * @author Joseph Ramsey
     */
    private static final class GraphEdgeLabel extends JComponent implements PropertyChangeListener {

        /**
         * The edge that this label should attach to.
         */
        private final IDisplayEdge edge;

        /**
         * The JComponent that serves as the label.
         */
        private final JComponent label;

        /**
         * The distance from the midpoint of the edge to the center of the
         * component along the normal to the edge.
         */
        private double normalDistance;

        /**
         * Constructs a new label wrapper for the given JComponent and edge.
         *
         * @param edge  the edge with which the label is associated.
         * @param label the JComponent which serves as the label.
         */
        public GraphEdgeLabel(IDisplayEdge edge, JComponent label) {
            this.label = label;
            this.edge = edge;
            this.setLayout(new BorderLayout());
            this.add(label, BorderLayout.CENTER);
            this.updateLocation(edge.getPointPair());
            ((Component) edge).addPropertyChangeListener(this);
        }

        /**
         * Listens to "newPointPair" property changes which are emitted by the
         * display edge whenever a new point pair is calculated.
         *
         * @param e the property change event.
         */
        public void propertyChange(PropertyChangeEvent e) {
            if ((e.getSource() == edge) && e.getPropertyName().equals("newPointPair")) {
                this.updateLocation((PointPair) e.getNewValue());
            }
        }

        /**
         * Updates the location of the label so that it lies halfway between the
         * two points of the point pair, slightly off to the right (if you go
         * from the "from" point to the "to" point).
         *
         * @param pp the point pair to track.
         */
        void updateLocation(PointPair pp) {
            if (pp != null) {
                this.moveCenterOutAlongNormal(pp);
                // attachCornerToMidpoint(pp, label);
            }
        }

        private void moveCenterOutAlongNormal(PointPair pp) {
            // calculate the cross outerProduct of a unit vector from
            // pp.from in the direction of pp.to with a unit vector
            // perpendicular down into the workbench. this will be a
            // unit vector normal to the vector from pp.from to pp.to.
            double dx = pp.getFrom().x - pp.getTo().x;
            double dy = pp.getFrom().y - pp.getTo().y;
            double dist = distance(pp.getFrom(), pp.getTo());
            double normalx = -dy / dist;
            double normaly = dx / dist;

            // Move the center of the label out from the midpoint in
            // the direction of this normal a distance of half the
            // diagonal of the label. (Note--a better "distance"
            // algorithm needs to be invented for this; a very wide
            // but short label gets put way too far from the edge at
            // some angles. jdramsey 10/25/01.)
            Point midPt = new Point((pp.getFrom().x + pp.getTo().x) / 2, (pp.getFrom().y + pp.getTo().y) / 2);
            Point edgeLoc = ((Component) edge).getLocation();
            int setx = edgeLoc.x + midPt.x;

            // Putting the labels directly on the line. (If this works,
            // need to modifiy the API to give the user some choice
            // in the matter.)
            setx += (int) (this.getNormalDistance() * normalx) / 2;
            setx -= this.getLabel().getWidth() / 2;

            int sety = edgeLoc.y + midPt.y;

            sety += (int) (this.getNormalDistance() * normaly) / 2;
            sety -= this.getLabel().getHeight() / 2;

            this.setLocation(setx, sety);
        }

        public double getNormalDistance() {
            return normalDistance;
        }

        public void setNormalDistance(double normalDistance) {
            this.normalDistance = normalDistance;
        }

        public JComponent getLabel() {
            return label;
        }
    }

    /**
     * This inner class is a simple wrapper for JComponents which are to serve
     * as node labels in the workbench. Its sole function is to make sure the
     * wrapped JComponents stay in the right place in the workbench--that is,
     * slightly off to the right of the display node.
     *
     * @author Joseph Ramsey
     */
    private static final class GraphNodeLabel extends JComponent {

        /**
         * The JComponent that serves as the label.
         */
        private final JComponent label;

        /**
         * The x location of the label relative to the center of the node.
         */
        private int xOffset;

        /**
         * The y location of the label relative to the center of the node.
         */
        private int yOffset;

        /**
         * Constructs a new label wrapper for the given JComponent and node.
         *
         * @param node  the node with which the label is associated.
         * @param label the JComponent which serves as the label.
         */
        public GraphNodeLabel(DisplayNode node, JComponent label, int xOffset, int yOffset) {

            this.label = label;

            Rectangle rectangle = node.getBounds();

            this.xOffset = (int) rectangle.getWidth() / 2 + xOffset;
            this.yOffset = (int) rectangle.getHeight() / 2 + yOffset;

            // this.xOffset = xOffset;
            // this.yOffset = yOffset;
            this.setLayout(new BorderLayout());
            this.add(label, BorderLayout.CENTER);
            this.updateLocation(node);
            node.addComponentListener(new ComponentAdapter() {
                public void componentMoved(ComponentEvent e) {
                    GraphNodeLabel.this.updateLocation(e.getComponent());
                }
            });
        }

        void updateLocation(Component component) {
            int x = component.getX() + component.getWidth() / 2 + xOffset;
            int y = component.getY() + component.getHeight() / 2 + yOffset;
            this.setLocation(x, y);
        }

        public JComponent getLabel() {
            return label;
        }
    }

    //
    // Event handler classes
    //

    /**
     * Handles <code>ComponentEvent</code>s. We use an inner class instead of
     * the workbench itself since we don't want to expose the handler methods on
     * the workbench's public API.
     */
    private static final class ComponentHandler extends ComponentAdapter {

        private final AbstractWorkbench workbench;

        public ComponentHandler(AbstractWorkbench workbench) {
            this.workbench = workbench;
        }

        /**
         * Adjusts scrollbars to automatically reflect the position of a
         * component which is being dragged.
         */
        @Override
        public final void componentMoved(ComponentEvent e) {
            Component source = (Component) e.getSource();
            Rectangle bounds = source.getBounds();

            if (source instanceof DisplayNode) {
                Node modelNode = (Node) (workbench.getDisplayToModel().get(source));

                // Adding a null pointer check
                if (modelNode == null) {
                    return;
                }

                int centerX = bounds.x + bounds.width / 2;
                int centerY = bounds.y + bounds.height / 2;

                modelNode.setCenterX(centerX);
                modelNode.setCenterY(centerY);

                workbench.adjustPreferredSize();

                // This causes wierdness when nodes are dragged off to the
                // right. Replacing with a scroll to rect on mouseup.
                // jdramsey 4/29/2005
                // workbench.scrollRectToVisible(bounds);
            }
        }
    }

    /**
     * Handles mouse events and mouse motion events.
     */
    private final class MouseHandler extends MouseAdapter {

        private final AbstractWorkbench workbench;

        public MouseHandler(AbstractWorkbench workbench) {
            this.workbench = workbench;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (isEnableEditing()) {
                workbench.handleMouseClicked(e);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            workbench.handleMousePressed(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (isEnableEditing()) {
                workbench.handleMouseReleased(e);
            }

            // Copy the laid out graph to the clipboard.
            new CopyLayoutAction(AbstractWorkbench.this.getWorkbench()).actionPerformed(null);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            if (isEnableEditing()) {
                workbench.handleMouseEntered(e);
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            // Commented out by Zhou
            //workbench.handleMouseExited(e);
        }
    }

    private static final class MouseMotionHandler extends MouseMotionAdapter {

        private final AbstractWorkbench workbench;

        public MouseMotionHandler(AbstractWorkbench workbench) {
            this.workbench = workbench;
        }

        @Override
        public final void mouseMoved(MouseEvent e) {
            workbench.currentMouseLocation = e.getPoint();
        }

        @Override
        public final void mouseDragged(MouseEvent e) {
            workbench.handleMouseDragged(e);
        }
    }

    private void doDoubleClickAction(DisplayNode node) {
        this.deselectAll();
        node.doDoubleClickAction(this.getGraph());
    }

    private Map<Object, Object> getDisplayToLabels() {
        return displayToLabels;
    }

    /**
     * This is necessary sometimes when the hashcodes of certain objects change
     * (e.g. a node changes its name.)
     */
    private void reconstiteMaps() {
        modelEdgesToDisplay = new HashMap<>(this.getModelEdgesToDisplay());
        modelNodesToDisplay = new HashMap<>(this.getModelNodesToDisplay());
        displayToModel = new HashMap(displayToModel);
        displayToLabels = new HashMap(displayToLabels);
    }

    private IDisplayEdge getTrackedEdge() {
        return trackedEdge;
    }

    private boolean isNodeEdgeErrorsReported() {
        return nodeEdgeErrorsReported;
    }

    protected void setNodeEdgeErrorsReported(boolean nodeEdgeErrorsReported) {
        this.nodeEdgeErrorsReported = nodeEdgeErrorsReported;
    }

    private boolean isRightClickPopupAllowed() {
        return rightClickPopupAllowed;
    }

    protected void setRightClickPopupAllowed(boolean rightClickPopupAllowed) {
        this.rightClickPopupAllowed = rightClickPopupAllowed;
    }

    /**
     * Handles <code>PropertyChangeEvent</code>s.
     */
    private final class PropertyChangeHandler implements PropertyChangeListener {

        private final AbstractWorkbench workbench;

        public PropertyChangeHandler(AbstractWorkbench workbench) {
            this.workbench = workbench;
        }

        public final void propertyChange(PropertyChangeEvent e) {
            String propName = e.getPropertyName();
            Object oldValue = e.getOldValue();
            Object newValue = e.getNewValue();

            if ("nodeAdded".equals(propName)) {
                workbench.addNode((Node) newValue);
            } else if ("nodeRemoved".equals(propName)) {
                workbench.removeNode((Node) oldValue);
            } else if ("edgeAdded".equals(propName)) {
                workbench.addEdge((Edge) newValue);
            } else if ("edgeRemoved".equals(propName)) {
                workbench.removeEdge((Edge) oldValue);
            } else if ("edgeLaunch".equals(propName)) {
                System.out.println("Attempt to launch edge.");
            } else if ("deleteNode".equals(propName)) {
                Object node = e.getSource();

                if (node instanceof DisplayNode) {
                    node = workbench.displayToModel.get(node);
                }

                if (node instanceof GraphNode) {
                    workbench.deselectAll();
                    workbench.selectNode((GraphNode) node);
                    workbench.deleteSelectedObjects();
                }
            } else if ("cloneMe".equals(propName)) {
                firePropertyChange("cloneMe", e.getOldValue(), e.getNewValue());
            }
        }
    }

}
