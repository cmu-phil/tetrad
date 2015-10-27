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
    private static Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private GeneralizedSemPm semPm;

    /**
     * The set of launched editors--or rather, the objects associated with the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<Object, EditorWindow>();
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


//        this.semPm = semPm;
//        this.launchedEditors = launchedEditors;
//        setLayout(new BorderLayout());
//        JScrollPane scroll = new JScrollPane(equationPane());
//        scroll.setPreferredSize(new Dimension(450, 450));
//
//        add(scroll, BorderLayout.CENTER);
    }

    //========================PRIVATE PROTECTED METHODS======================//

//    public void freshenDisplay() {
//        removeAll();
//        setLayout(new BorderLayout());
//        JScrollPane scroll = new JScrollPane(initialValuesPane());
//        scroll.setPreferredSize(new Dimension(450, 450));
//        add(scroll, BorderLayout.CENTER);
//    }

    private JComponent initialValuesPane() {
        formulasBox = Box.createVerticalBox();
        refreshLabels();
        return formulasBox;
    }

    public void refreshLabels() {
        formulasBox.removeAll();

        java.util.List<String> parameters = new ArrayList<String>(semPm().getParameters());
        Collections.sort(parameters);

        for (String parameter : parameters) {
            Box c = Box.createHorizontalBox();
            final JLabel label = new JLabel(parameter + " ~ " + semPm().getParameterExpressionString(parameter));
            final String _parameter = parameter;

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginParamEdit(_parameter, label, label);
                    }
                }
            });

            c.add(label);
            c.add(Box.createHorizontalGlue());

            formulasBox.add(c);
            formulasBox.add(Box.createVerticalStrut(5));
        }

        formulasBox.setBorder(new CompoundBorder(new TitledBorder("Double click expressions to edit."),
                new EmptyBorder(5, 5, 5, 5)));

        formulasBox.revalidate();
        formulasBox.repaint();
    }

    private void beginParamEdit(final String parameter, final JLabel label, JComponent centering) {
        if (launchedEditors.keySet().contains(parameter)) {
            launchedEditors.get(parameter).moveToFront();
            return;
        }

        final GeneralizedExpressionEditor paramEditor = new GeneralizedExpressionEditor(semPm, parameter);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(paramEditor, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        final EditorWindow editorWindow =
                new EditorWindow(panel, "Edit Expression", "OK", true, centering);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        launchedEditors.put(parameter, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    String expressionString = paramEditor.getExpressionString();
                    try {
                        semPm.setParameterExpression(parameter, expressionString);
//                        label.setText(parameter + " ~ " + semPm().getParameterExpressionString(parameter));
                        refreshLabels();
                    } catch (ParseException e) {
                        // This is an expression that's been vetted by the expression editor.
                        e.printStackTrace();
                        launchedEditors.remove(parameter);
                        throw new RuntimeException("The expression editor returned an unparseable string: " + expressionString, e);
                    }
                    catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(panel, e.getMessage());
                        launchedEditors.remove(parameter);
                    }

                    firePropertyChange("modelChanged", null, null);
                }

                launchedEditors.remove(parameter);
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


