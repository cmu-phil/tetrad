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
 *
 * A lot of the code borrows heavily from HistogramAction
 *
 * @author Michael Freenor
 */

public class ScatterPlotAction extends AbstractAction {


    /**
     * The data edtitor that action is attached to.
     */
    private DataEditor dataEditor;


    /**
     * Constructs the <code>QQPlotAction</code> given the <code>DataEditor</code>
     * that its attached to.
     *
     * @param editor
     */
    public ScatterPlotAction(DataEditor editor) {
        super("Scatter Plots...");
        this.dataEditor = editor;
    }






    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();
        if(dataSet == null || dataSet.getNumColumns() == 0){
            JOptionPane.showMessageDialog(findOwner(), "Cannot display a scatter plot for an empty data set.");
            return;
        }

        JPanel panel = new ScatterPlotView(dataSet);
        EditorWindow editorWindow = new EditorWindow(panel, "Scatter Plots", "Save", true, dataEditor);

//        JPanel dialog = createScatterPlotDialog(null, null);
//        EditorWindow editorWindow = new EditorWindow(dialog, "Scatter Plots", "Save", true, dataEditor);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);
    }

    //============================== Private methods ============================//





    /**
     * Sets the location on the given dialog for the given index.
     */
    private void setLocation(JDialog dialog, int index) {
        Rectangle bounds = dialog.getBounds();
        JFrame frame = findOwner();
        Dimension dim;
        if (frame == null) {
            dim = Toolkit.getDefaultToolkit().getScreenSize();
        } else {
            dim = frame.getSize();
        }

        int x = (int) (150 * Math.cos(index * 15 * (Math.PI / 180)));
        int y = (int) (150 * Math.sin(index * 15 * (Math.PI / 180)));
        x += (dim.width - bounds.width)/2;
        y += (dim.height - bounds.height)/2;
        dialog.setLocation(x, y);
    }


    /**
     * Creates a dialog that is showing the histogram for the given node (if null
     * one is selected for you)
     */
    private JPanel createScatterPlotDialog(ContinuousVariable yVariable, ContinuousVariable xVariable) {
        String dialogTitle = "Scatter Plots";
        JPanel panel = new JPanel(); 
        panel.setLayout(new BorderLayout());

        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();

        ScatterPlotOld scatterPlot = new ScatterPlotOld(dataSet, yVariable, xVariable);
        ScatterPlotEditorPanel editorPanel = new ScatterPlotEditorPanel(scatterPlot, dataSet);
        ScatterPlotDisplayPanelOld display = new ScatterPlotDisplayPanelOld(scatterPlot);
        editorPanel.addPropertyChangeListener(new ScatterPlotListener(display));

        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem(new SaveComponentImage(display, "Save Scatter Plot")));
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

//        dialog.getContentPane().add(bar, BorderLayout.NORTH);
//        dialog.getContentPane().add(vBox, BorderLayout.CENTER);
//        return dialog;
        return panel;
    }


    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, dataEditor);
    }

    //================================= Inner Class ======================================//



    /**
     * Glue between the editor and the display.
     */
    private static class ScatterPlotListener implements PropertyChangeListener {

        private ScatterPlotDisplayPanelOld display;


        public ScatterPlotListener(ScatterPlotDisplayPanelOld display) {
            this.display = display;
        }


        public void propertyChange(PropertyChangeEvent evt) {
            if ("histogramChange".equals(evt.getPropertyName())) {
                this.display.updateScatterPlot((ScatterPlotOld) evt.getNewValue());
            }
        }
    }


}



