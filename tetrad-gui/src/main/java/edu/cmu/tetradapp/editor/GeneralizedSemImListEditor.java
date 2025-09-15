///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

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
    private static final Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemPm being edited.
     */
    private final GeneralizedSemIm semIm;
    /**
     * The box containing all of the formulas.
     */
    private final Box formulasBox;
    /**
     * The set of launched editors--or rather, the nodes for the launched editors.
     */
    private Map<Object, EditorWindow> launchedEditors = new HashMap<>();

    /**
     * Constructs a SemPm graphical editor for the given SemIm.
     *
     * @param semIm           a {@link edu.cmu.tetrad.sem.GeneralizedSemIm} object
     * @param launchedEditors a {@link java.util.Map} object
     */
    public GeneralizedSemImListEditor(GeneralizedSemIm semIm, Map<Object, EditorWindow> launchedEditors) {
        System.out.println("List editor : " + semIm);

        this.semIm = semIm;
        this.launchedEditors = launchedEditors;
        /*
      The PM being edited.
     */
        GeneralizedSemPm semPm = semIm.getSemPm();
        setLayout(new BorderLayout());
        this.formulasBox = Box.createVerticalBox();
        refreshLabels();
        JScrollPane scroll = new JScrollPane(this.formulasBox);
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
    }

    //========================PUBLIC PROTECTED METHODS======================//

    private JComponent refreshLabels() {
        this.formulasBox.removeAll();

        for (Node node : semIm().getSemPm().getVariableNodes()) {
            Box c = Box.createHorizontalBox();

            JLabel label = new JLabel(node + " := " + this.semIm.getNodeSubstitutedString(node));

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginNodeEdit(node, label, label);
                    }
                }
            });

            c.add(label);
            c.add(Box.createHorizontalGlue());

            this.formulasBox.add(c);
            this.formulasBox.add(Box.createVerticalStrut(5));
        }

        for (Node node : semIm().getSemPm().getErrorNodes()) {
            Box c = Box.createHorizontalBox();

            JLabel label = new JLabel(node + " ~ " + this.semIm.getNodeSubstitutedString(node));

            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent mouseEvent) {
                    if (mouseEvent.getClickCount() == 2) {
                        beginNodeEdit(node, label, label);
                    }
                }
            });

            c.add(label);
            c.add(Box.createHorizontalGlue());

            this.formulasBox.add(c);
            this.formulasBox.add(Box.createVerticalStrut(5));
        }

        this.formulasBox.add(Box.createVerticalGlue());

        this.formulasBox.setBorder(new CompoundBorder(new TitledBorder("Double click expressions to edit."),
                new EmptyBorder(5, 5, 5, 5)));

        this.formulasBox.revalidate();
        this.formulasBox.repaint();

        return this.formulasBox;
    }

    private void beginNodeEdit(Node node, JLabel label, JComponent centering) {
        if (this.launchedEditors.containsKey(node)) {
            this.launchedEditors.get(node).moveToFront();
            return;
        }

        GeneralizedExpressionParameterizer paramEditor = new GeneralizedExpressionParameterizer(this.semIm, node);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(paramEditor, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(5, 5, 5, 5));

        EditorWindow editorWindow =
                new EditorWindow(panel, "Parameter Properties", "OK", true, centering);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);

        this.launchedEditors.put(node, editorWindow);

        editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
            public void internalFrameClosing(InternalFrameEvent internalFrameEvent) {
                if (!editorWindow.isCanceled()) {
                    GeneralizedSemImListEditor.this.semIm.setSubstitutions(paramEditor.getParameterValues());

                    refreshLabels();

                    GeneralizedSemImListEditor.this.launchedEditors.remove(node);
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
    }


    private GeneralizedSemIm semIm() {
        return this.semIm;
    }
}



