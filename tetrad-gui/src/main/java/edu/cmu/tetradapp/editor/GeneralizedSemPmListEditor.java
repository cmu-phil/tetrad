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

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;


/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class GeneralizedSemPmListEditor extends JPanel {

    /**
     * Font size for parameter values in the graph.
     */
    private static Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private GeneralizedSemPm semPm;


    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay;


    private GeneralizedSemPmParamsEditor paramsEditor;


    /**
     * The set of launched editors--or rather, the nodes for the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors  = new HashMap<Object, EditorWindow>();
    private Box formulasBox;

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public GeneralizedSemPmListEditor(GeneralizedSemPm semPm, GeneralizedSemPmParamsEditor paramsEditor,
                                      Map<Object, EditorWindow> launchedEditors) {
        this.semPm = semPm;
        this.paramsEditor = paramsEditor;
        this.launchedEditors = launchedEditors;
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(equationPane());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
    }

    //========================PRIVATE PROTECTED METHODS======================//

    private int getSavedTooltipDelay() {
        return savedTooltipDelay;
    }

    private JComponent equationPane() {
        formulasBox = Box.createVerticalBox();
        refreshLabels();
        return formulasBox;
    }

    public void refreshLabels() {
        formulasBox.removeAll();

        for (Node node : semPm().getNodes()) {
            if (!semPm().getGraph().isParameterizable(node)) {
                continue;
            }

            Box c = Box.createHorizontalBox();
            String symbol = node.getNodeType() == NodeType.ERROR ? " ~ " : " = ";
            final JLabel label = new JLabel(node + symbol + semPm().getNodeExpressionString(node));
            c.add(label);
            c.add(Box.createHorizontalGlue());

            final Node _node = node;

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginNodeEdit(_node, label, label);
                    }
                }
            });

            formulasBox.add(c);
            formulasBox.add(Box.createVerticalStrut(5));
        }

        formulasBox.revalidate();
        formulasBox.repaint();

        formulasBox.setBorder(new CompoundBorder(new TitledBorder("Double click expressions to edit."),
                new EmptyBorder(5, 5, 5, 5)));
    }

    private void beginNodeEdit(final Node node, JComponent centering, final JLabel label) {
        if (launchedEditors.keySet().contains(node)) {
            launchedEditors.get(node).moveToFront();
            return;
        }

        final GeneralizedExpressionEditor paramEditor = new GeneralizedExpressionEditor(semPm, node);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(paramEditor, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        final EditorWindow editorWindow =
                new EditorWindow(panel, "Edit Expression", "OK", true, centering);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        launchedEditors.put(node, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    String expressionString = paramEditor.getExpressionString();
                    try {
                        semPm.setNodeExpression(node, expressionString);

                        if (node.getNodeType() == NodeType.ERROR) {
                            label.setText(node + " = " + semPm().getNodeExpressionString(node));
                        }
                        else {
                            label.setText(node + " ~ " + semPm().getNodeExpressionString(node));
                        }
                    } catch (ParseException e) {
                        // This is an expression that's been vetted by the expression editor.
                        launchedEditors.remove(node);
                        e.printStackTrace();
                        throw new RuntimeException("The expression editor returned an unparseable string: " + expressionString, e);
                    }
                    paramsEditor.refreshLabels();

                    firePropertyChange("modelChanged", null, null);
                }

                launchedEditors.remove(node);
            }
        });

//        GeneralizedExpressionEditor paramEditor = new GeneralizedExpressionEditor(semPm, node);
//
//        int ret = JOptionPane.showOptionDialog(centering, paramEditor,
//                "Edit Expression", JOptionPane.OK_CANCEL_OPTION,
//                JOptionPane.PLAIN_MESSAGE, null, null, null);
//
//        if (ret == JOptionPane.OK_OPTION) {
//            String expressionString = paramEditor.getExpressionString();
//            try {
//                semPm.setNodeExpression(node, expressionString);
//
//                if (node.getNodeType() == NodeType.ERROR) {
//                    label.setText(node + " = " + semPm().getNodeExpressionString(node));
//                }
//                else {
//                    label.setText(node + " ~ " + semPm().getNodeExpressionString(node));
//                }
//
//                paramsEditor.freshenDisplay();
//            } catch (ParseException e) {
//                // This is an expression that's been vetted by the expression editor.
//                e.printStackTrace();
//                throw new RuntimeException("The expression editor returned an unparseable string: " + expressionString, e);
//            }
//
//            firePropertyChange("modelChanged", null, null);
//        }
    }

    private GeneralizedSemPm semPm() {
        return this.semPm;
    }

    private Graph graph() {
        return semPm().getGraph();
    }

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        this.savedTooltipDelay = savedTooltipDelay;
    }

    //=======================PRIVATE INNER CLASSES==========================//


}



