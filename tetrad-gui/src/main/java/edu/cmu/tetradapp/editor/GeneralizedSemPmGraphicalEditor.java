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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class GeneralizedSemPmGraphicalEditor extends JPanel {

    private static final long serialVersionUID = 3918327352162592699L;

    /**
     * Font size for parameter values in the graph.
     */
    private static final Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private final GeneralizedSemPm semPm;

    /**
     * Workbench for the graphical editor.
     */
    private GraphWorkbench workbench;

    /**
     * The set of launched editors--or rather, the nodes for the launched
     * editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<>();

    private boolean enableEditing = true;

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public GeneralizedSemPmGraphicalEditor(final GeneralizedSemPm semPm, final Map<Object, EditorWindow> launchedEditors) {
        this.semPm = semPm;
        this.launchedEditors = launchedEditors;
        setLayout(new BorderLayout());
        final JScrollPane scroll = new JScrollPane(workbench());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
        setBorder(new TitledBorder(
                "Double click expressions to edit"));
    }

    //========================PRIVATE PROTECTED METHODS======================//
    private void beginNodeEdit(final Node node) {
        if (this.launchedEditors.keySet().contains(node)) {
            this.launchedEditors.get(node).moveToFront();
            return;
        }

        final GeneralizedExpressionEditor paramEditor = new GeneralizedExpressionEditor(this.semPm, node);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(paramEditor, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        final EditorWindow editorWindow
                = new EditorWindow(panel, "Edit Expression", "OK", true, this);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        this.launchedEditors.put(node, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(final InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    final String expressionString = paramEditor.getExpressionString();
                    try {
                        GeneralizedSemPmGraphicalEditor.this.semPm.setNodeExpression(node, expressionString);
                    } catch (final ParseException e) {
                        // This is an expression that's been vetted by the expression editor.
                        e.printStackTrace();
                        GeneralizedSemPmGraphicalEditor.this.launchedEditors.remove(node);
                        throw new RuntimeException("The expression editor returned an unparseable string: " + expressionString, e);
                    }
                    refreshLabels();
                    firePropertyChange("modelChanged", null, null);
                }

                GeneralizedSemPmGraphicalEditor.this.launchedEditors.remove(node);
            }
        });
    }

    private GeneralizedSemPm semPm() {
        return this.semPm;
    }

    private Graph graph() {
        return semPm().getGraph();
    }

    private GraphWorkbench workbench() {
        if (this.getWorkbench() == null) {
            this.workbench = new GraphWorkbench(graph());
            this.getWorkbench().setAllowDoubleClickActions(false);
            refreshLabels();
            addMouseListenerToGraphNodesMeasured();
        }
        return getWorkbench();
    }

    public void refreshLabels() {
        final List nodes = graph().getNodes();

        for (final Object node : nodes) {
            resetNodeLabel((Node) node);
        }

        workbench().repaint();
    }

    private void resetNodeLabel(final Node node) {
        final int maxExpressionLength = Preferences.userRoot().getInt("maxExpressionLength", 25);

        if (this.semPm.getNodeExpression(node) == null) {
            return;
        }

        String expressionString = this.semPm.getNodeExpressionString(node);
        if (expressionString.length() > maxExpressionLength) {
            expressionString = "- long formula -";
        }

        if (expressionString == null) {
            workbench().setNodeLabel(node, null, 0, 0);
        } else {
            final JLabel label = new JLabel();
            label.setForeground(Color.BLACK);
            label.setBackground(Color.WHITE);
//            label.setFont(SMALL_FONT);
            label.setText(expressionString);
            label.addMouseListener(new NodeMouseListener(node, this));

            // Offset the nodes slightly differently depending on whether
            // they're error nodes or not.
            if (node.getNodeType() == NodeType.ERROR) {
                label.setOpaque(false);

                final Node error = this.workbench.getGraph().getNode(node.getName());

                if (error != null) {
                    workbench().setNodeLabel(error, label, -10, -10);
                }
            } else {
                label.setOpaque(false);

                if (workbench().getGraph().containsNode(node)) {
                    workbench().setNodeLabel(node, label, 0, 0);
                }
            }
        }

        firePropertyChange("modelChanged", null, null);
    }

    private void addMouseListenerToGraphNodesMeasured() {
//        List nodes = graph().getNodes();
//
//        for (Object node : nodes) {
//            Object displayNode = workbench().getModelToDisplay().get(node);
//
//            if (displayNode instanceof GraphNodeMeasured) {
//                DisplayNode _displayNode = (DisplayNode) displayNode;
//                _displayNode.setToolTipText(
//                        getEquationOfNode(_displayNode.getModelNode())
//                );
//            }
//        }
    }

    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    public boolean isEnableEditing() {
        return this.enableEditing;
    }

    public void enableEditing(final boolean enableEditing) {
        this.enableEditing = enableEditing;
        if (this.workbench != null) {
            this.workbench.enableEditing(enableEditing);
        }
    }

    //=======================PRIVATE INNER CLASSES==========================//
    private final static class NodeMouseListener extends MouseAdapter {

        private final Node node;
        private final GeneralizedSemPmGraphicalEditor editor;

        public NodeMouseListener(final Node node, final GeneralizedSemPmGraphicalEditor editor) {
            this.node = node;
            this.editor = editor;
        }

        private Node getNode() {
            return this.node;
        }

        private GeneralizedSemPmGraphicalEditor getEditor() {
            return this.editor;
        }

        public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() == 2) {
                getEditor().beginNodeEdit(getNode());
            }
        }
    }

}
