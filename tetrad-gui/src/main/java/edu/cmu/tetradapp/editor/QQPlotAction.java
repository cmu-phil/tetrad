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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Displays a Q-Q plot for a random variable.
 * <p>
 * A lot of the code borrows heavily from HistogramAction
 *
 * @author Michael Freenor
 */

class QQPlotAction extends AbstractAction {


    /**
     * The data edtitor that action is attached to.
     */
    private final DataEditor dataEditor;


    /**
     * Constructs the <code>QQPlotAction</code> given the <code>DataEditor</code> that its attached to.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public QQPlotAction(DataEditor editor) {
        super("Q-Q Plots...");
        this.dataEditor = editor;
    }


    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();
        if (dataSet == null || dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot display a Q-Q plot for an empty data set.");
            return;
        }
        // if there are missing values warn and don't display q-q plot.

        int[] selected = dataSet.getSelectedIndices();
        // if more then one column is selected then open up more than one histogram
        if (selected != null && 0 < selected.length) {
            // warn user if they selected more than 10
            if (10 < selected.length) {
                int option = JOptionPane.showConfirmDialog(findOwner(), "You are about to open " + selected.length +
                                                                        " Q-Q plots, are you sure you want to proceed?", "Q-Q Plot Warning", JOptionPane.YES_NO_OPTION);
                // if selected no, return
                if (option == JOptionPane.NO_OPTION) {
                    return;
                }
            }
            for (int index : selected) {
                JPanel dialog = createQQPlotDialog(dataSet.getVariable(index));

                EditorWindow editorWindow =
                        new EditorWindow(dialog, "QQPlot", "Save", true, this.dataEditor);

                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);


            }
        } else {
            JPanel dialog = createQQPlotDialog(null);

            EditorWindow editorWindow =
                    new EditorWindow(dialog, "QQPlot", "Save", true, this.dataEditor);

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);

        }
    }

    //============================== Private methods ============================//


    /**
     * Creates a dialog that is showing the histogram for the given node (if null one is selected for you)
     */
    private JPanel createQQPlotDialog(Node selected) {
        JPanel panel = new JPanel(); //new JPanel(findOwner(), dialogTitle, false);
        panel.setLayout(new BorderLayout());

        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();

        QQPlot qqPlot = new QQPlot(dataSet, selected);
        QQPlotEditorPanel editorPanel = new QQPlotEditorPanel(qqPlot, dataSet);
        QQPlotDisplayPanel display = new QQPlotDisplayPanel(qqPlot);
        editorPanel.addPropertyChangeListener(new QQPlotListener(display));

        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem(new SaveComponentImage(display, "Save Q-Q Plot")));
        bar.add(menu);

        Box box = Box.createHorizontalBox();
        box.add(display);

        box.add(Box.createHorizontalStrut(3));
        box.add(editorPanel);
        box.add(Box.createHorizontalStrut(5));
        box.add(Box.createHorizontalGlue());

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(15));
        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));

        panel.add(bar, BorderLayout.NORTH);
        panel.add(vBox, BorderLayout.CENTER);

        return panel;
    }


    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, this.dataEditor);
    }

    //================================= Inner Class ======================================//


    /**
     * Glue between the editor and the display.
     */
    private static class QQPlotListener implements PropertyChangeListener {

        private final QQPlotDisplayPanel display;


        public QQPlotListener(QQPlotDisplayPanel display) {
            this.display = display;
        }


        public void propertyChange(PropertyChangeEvent evt) {
            if ("histogramChange".equals(evt.getPropertyName())) {
                this.display.updateQQPlot((QQPlot) evt.getNewValue());
            }
        }
    }


}



