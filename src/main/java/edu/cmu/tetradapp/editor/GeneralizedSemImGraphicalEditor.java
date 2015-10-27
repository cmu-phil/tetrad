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
import edu.cmu.tetrad.sem.GeneralizedSemIm;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;


/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class GeneralizedSemImGraphicalEditor extends JPanel {

    /**
     * The SemPm being edited.
     */
    private GeneralizedSemIm semIm;

    /**
     * Workbench for the graphical editor.
     */
    private GraphWorkbench workbench;

    /**
     * The set of launched editors--or rather, the nodes for the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<Object, EditorWindow>();

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public GeneralizedSemImGraphicalEditor(GeneralizedSemIm semIm, Map<Object, EditorWindow> launchedEditors) {
        this.semIm = semIm;
        this.launchedEditors = launchedEditors;
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(workbench());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
        setBorder(new TitledBorder(
                "Double click expressions to edit"));
    }

    //============================================PUBLIC======================================================//

    public void refreshLabels() {
        List nodes = graph().getNodes();

        for (Object node : nodes) {
            resetNodeLabel((Node) node);
        }

        workbench().repaint();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    //============================================PRIVATE=====================================================//

    private void beginNodeEdit(final Node node) {
        if (launchedEditors.keySet().contains(node)) {
            launchedEditors.get(node).moveToFront();
            return;
        }

        final GeneralizedExpressionParameterizer paramEditor = new GeneralizedExpressionParameterizer(semIm, node);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(paramEditor, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        final EditorWindow editorWindow =
                new EditorWindow(panel, "Parameter Properties", "OK", true, workbench());

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        launchedEditors.put(node, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    semIm.setSubstitutions(paramEditor.getParameterValues());
                    refreshLabels();
                    launchedEditors.remove(node);
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
    }

    private GeneralizedSemIm semIm() {
        return this.semIm;
    }

    private Graph graph() {
        return semIm().getSemPm().getGraph();
    }

    private GraphWorkbench workbench() {
        if (this.getWorkbench() == null) {
            this.workbench = new GraphWorkbench(graph());
            this.getWorkbench().setAllowDoubleClickActions(false);
            refreshLabels();
        }

        return getWorkbench();
    }


    private void resetNodeLabel(Node node) {
        int maxExpressionLength = Preferences.userRoot().getInt("maxExpressionLength", 25);

        String expressionString = semIm.getNodeSubstitutedString(node);
        if (expressionString == null) {
            workbench().setNodeLabel(node, null, 0, 0);
            firePropertyChange("modelChanged", null, null);
            return;
        }

        if (expressionString.length() > maxExpressionLength) {
            expressionString = "- long formula -";
        }

        JLabel label = new JLabel();
        label.setForeground(Color.BLACK);
        label.setBackground(Color.WHITE);
        label.setText(expressionString);
        label.addMouseListener(new NodeMouseListener(node, this));

        // Offset the nodes slightly differently depending on whether
        // they're error nodes or not.
        if (node.getNodeType() == NodeType.ERROR) {
            label.setOpaque(false);

            Node error = workbench.getGraph().getNode(node.getName());

            if (error != null) {
                workbench().setNodeLabel(error, label, -10, -10);
            }
        } else {
            label.setOpaque(false);

            if (workbench.getGraph().containsNode(node)) {
                workbench().setNodeLabel(node, label, 0, 0);
            }
        }

        firePropertyChange("modelChanged", null, null);
    }

    private final static class NodeMouseListener extends MouseAdapter {
        private Node node;
        private GeneralizedSemImGraphicalEditor editor;

        public NodeMouseListener(Node node, GeneralizedSemImGraphicalEditor editor) {
            this.node = node;
            this.editor = editor;
        }

        private Node getNode() {
            return node;
        }

        private GeneralizedSemImGraphicalEditor getEditor() {
            return editor;
        }

        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                getEditor().beginNodeEdit(getNode());
            }
        }
    }

}


