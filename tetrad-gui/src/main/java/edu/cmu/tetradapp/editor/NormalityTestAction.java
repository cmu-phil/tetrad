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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
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

class NormalityTestAction extends AbstractAction {


    /**
     * The data edtitor that action is attached to.
     */
    private final DataEditor dataEditor;


    /**
     * Constructs the <code>QQPlotAction</code> given the <code>DataEditor</code>
     * that its attached to.
     */
    public NormalityTestAction(DataEditor editor) {
        super("Normality Tests...");
        this.dataEditor = editor;
    }

    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();
        if (dataSet == null || dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot run normality tests on an empty data set.");
            return;
        }
        // if there are missing values warn and don't display q-q plot.

        JPanel panel = createNormalityTestDialog();

        EditorWindow window = new EditorWindow(panel,
                "Normality Tests", "Close", false, this.dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

    }

    //============================== Private methods ============================//


    /**
     * Creates a dialog that is showing the histogram for the given node (if null
     * one is selected for you)
     */
    private JPanel createNormalityTestDialog(

    ) {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();

        QQPlot qqPlot = new QQPlot(dataSet, null);
        NormalityTestEditorPanel editorPanel = new NormalityTestEditorPanel(qqPlot, dataSet);

        JTextArea display = new JTextArea(NormalityTests.runNormalityTests(dataSet,
                (ContinuousVariable) qqPlot.getSelectedVariable()), 20, 65);
        display.setEditable(false);
        editorPanel.addPropertyChangeListener(new NormalityTestListener(display));

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

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
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
    private static class NormalityTestListener implements PropertyChangeListener {

        private final JTextArea display;


        public NormalityTestListener(JTextArea display) {
            this.display = display;
        }


        public void propertyChange(PropertyChangeEvent evt) {
            if ("histogramChange".equals(evt.getPropertyName())) {
                this.display.setText((String) evt.getNewValue());
            }
        }
    }


}


