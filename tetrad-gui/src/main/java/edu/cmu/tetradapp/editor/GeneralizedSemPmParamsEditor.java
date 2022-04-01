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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
class GeneralizedSemPmParamsEditor extends JPanel {

    /**
     * Font size for parameter values in the graph.
     */
    private static final Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private final GeneralizedSemPm semPm;

    /**
     * The set of launched editors--or rather, the objects associated with the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<>();
    private Box formulasBox;

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     */
    public GeneralizedSemPmParamsEditor(GeneralizedSemPm semPm, Map<Object, EditorWindow> launchedEditors) {
        this.semPm = semPm;
        this.launchedEditors = launchedEditors;
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(initialValuesPane());
        scroll.setPreferredSize(new Dimension(450, 450));
        add(scroll, BorderLayout.CENTER);
        refreshLabels();


    }

    //========================PRIVATE PROTECTED METHODS======================//

    private JComponent initialValuesPane() {
        this.formulasBox = Box.createVerticalBox();
        refreshLabels();
        return this.formulasBox;
    }

    public void refreshLabels() {
        this.formulasBox.removeAll();

        java.util.List<String> parameters = new ArrayList<>(semPm().getParameters());
        Collections.sort(parameters);

        for (String parameter : parameters) {
            Box c = Box.createHorizontalBox();
            JLabel label = new JLabel(parameter + " ~ " + semPm().getParameterExpressionString(parameter));

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginParamEdit(parameter, label, label);
                    }
                }
            });

            c.add(label);
            c.add(Box.createHorizontalGlue());

            this.formulasBox.add(c);
            this.formulasBox.add(Box.createVerticalStrut(5));
        }

        this.formulasBox.setBorder(new CompoundBorder(new TitledBorder("Double click expressions to edit."),
                new EmptyBorder(5, 5, 5, 5)));

        this.formulasBox.revalidate();
        this.formulasBox.repaint();
    }

    private void beginParamEdit(String parameter, JLabel label, JComponent centering) {
        if (this.launchedEditors.containsKey(parameter)) {
            this.launchedEditors.get(parameter).moveToFront();
            return;
        }

        GeneralizedExpressionEditor paramEditor = new GeneralizedExpressionEditor(this.semPm, parameter);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(paramEditor, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        EditorWindow editorWindow =
                new EditorWindow(panel, "Edit Expression", "OK", true, centering);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        this.launchedEditors.put(parameter, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    String expressionString = paramEditor.getExpressionString();
                    try {
                        GeneralizedSemPmParamsEditor.this.semPm.setParameterExpression(parameter, expressionString);
//                        label.setText(parameter + " ~ " + semPm().getParameterExpressionString(parameter));
                        refreshLabels();
                    } catch (ParseException e) {
                        // This is an expression that's been vetted by the expression editor.
                        e.printStackTrace();
                        GeneralizedSemPmParamsEditor.this.launchedEditors.remove(parameter);
                        throw new RuntimeException("The expression editor returned an unparseable string: " + expressionString, e);
                    } catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(panel, e.getMessage());
                        GeneralizedSemPmParamsEditor.this.launchedEditors.remove(parameter);
                    }

                    firePropertyChange("modelChanged", null, null);
                }

                GeneralizedSemPmParamsEditor.this.launchedEditors.remove(parameter);
            }
        });
    }

    private GeneralizedSemPm semPm() {
        return this.semPm;
    }

    private Graph graph() {
        return semPm().getGraph();
    }

}


