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
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;


/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class GeneralizedSemImListEditor extends JPanel {

    /**
     * Font size for parameter values in the graph.
     */
    private static Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private GeneralizedSemIm semIm;


    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay;

    /**
     * The PM being edited.
     */
    private GeneralizedSemPm semPm;

    /**
     * The set of launched editors--or rather, the nodes for the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<Object, EditorWindow>();

    /**
     * The box containing all of the formulas.
     */
    private Box formulasBox;

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public GeneralizedSemImListEditor(GeneralizedSemIm semIm, Map<Object, EditorWindow> launchedEditors) {
        System.out.println("List editor : " + semIm);

        this.semIm = semIm;
        this.launchedEditors = launchedEditors;
        this.semPm = semIm.getSemPm();
        setLayout(new BorderLayout());
        formulasBox = Box.createVerticalBox();
        refreshLabels();
        JScrollPane scroll = new JScrollPane(formulasBox);
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
    }

    //========================PUBLIC PROTECTED METHODS======================//

    public JComponent refreshLabels() {
        formulasBox.removeAll();

        for (Node node : semIm().getSemPm().getVariableNodes()) {
            Box c = Box.createHorizontalBox();

            final JLabel label = new JLabel(node + " := " + semIm.getNodeSubstitutedString(node));
            final Node _node = node;

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginNodeEdit(_node, label, label);
                    }
                }
            });

            c.add(label);
            c.add(Box.createHorizontalGlue());

            formulasBox.add(c);
            formulasBox.add(Box.createVerticalStrut(5));
        }

        for (Node node : semIm().getSemPm().getErrorNodes()) {
            Box c = Box.createHorizontalBox();

            final JLabel label = new JLabel(node + " ~ " + semIm.getNodeSubstitutedString(node));
            final Node _node = node;

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginNodeEdit(_node, label, label);
                    }
                }
            });

            c.add(label);
            c.add(Box.createHorizontalGlue());

            formulasBox.add(c);
            formulasBox.add(Box.createVerticalStrut(5));
        }

        formulasBox.add(Box.createVerticalGlue());

        formulasBox.setBorder(new CompoundBorder(new TitledBorder("Double click expressions to edit."),
                new EmptyBorder(5, 5, 5, 5)));

        formulasBox.revalidate();
        formulasBox.repaint();

        return formulasBox;
    }

    private void beginNodeEdit(final Node node, final JLabel label, JComponent centering) {
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
                new EditorWindow(panel, "Parameter Properties", "OK", true, centering);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        launchedEditors.put(node, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    semIm.setSubstitutions(paramEditor.getParameterValues());

//                    if (node.getNodeType() == NodeType.ERROR) {
//                        label.setText(node + " = " + semIm.getNodeSubstitutedString(node));
//                    }
//                    else {
//                        label.setText(node + " ~ " + semIm.getNodeSubstitutedString(node));
//                    }

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

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        this.savedTooltipDelay = savedTooltipDelay;
    }

    //=======================PRIVATE INNER CLASSES==========================//


}


