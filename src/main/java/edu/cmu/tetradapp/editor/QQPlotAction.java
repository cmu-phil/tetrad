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
 *
 * A lot of the code borrows heavily from HistogramAction
 *
 * @author Michael Freenor
 */

public class QQPlotAction extends AbstractAction {


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
    public QQPlotAction(DataEditor editor) {
        super("Q-Q Plots...");
        this.dataEditor = editor;
    }






    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();
        if(dataSet == null || dataSet.getNumColumns() == 0){
            JOptionPane.showMessageDialog(findOwner(), "Cannot display a Q-Q plot for an empty data set.");
            return;
        }
        // if there are missing values warn and don't display q-q plot.
//        if(DataUtils.containsMissingValue(dataSet)){
//            JOptionPane.showMessageDialog(findOwner(), new JLabel("<html>Data has missing values, " +
//                    "remove all missing values before<br>" +
//                    "displaying data in a Q-Q plot.</html>"));
//            return;
//        }

        int[] selected = dataSet.getSelectedIndices();
        // if more then one column is selected then open up more than one histogram
        if (selected != null && 0 < selected.length) {
            // warn user if they selected more than 10
            if(10 < selected.length){
                int option = JOptionPane.showConfirmDialog(findOwner(), "You are about to open " + selected.length +
                " Q-Q plots, are you sure you want to proceed?", "Q-Q Plot Warning", JOptionPane.YES_NO_OPTION);
                // if selected no, return
                if(option == JOptionPane.NO_OPTION){
                    return;
                }
            }
            for (int index : selected) {
                JPanel dialog = createQQPlotDialog(dataSet.getVariable(index));

                EditorWindow editorWindow =
                        new EditorWindow(dialog, "QQPlot", "Save", true, dataEditor);

                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);




//                dialog.pack();
//                setLocation(dialog, index);
//                dialog.setVisible(true);
            }
        } else {
            JPanel dialog = createQQPlotDialog(null);

            EditorWindow editorWindow =
                    new EditorWindow(dialog, "QQPlot", "Save", true, dataEditor);

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);

//            JDialog dialog = createQQPlotDialog(null);
//            dialog.pack();
//            dialog.setLocationRelativeTo(dialog.getOwner());
//            dialog.setVisible(true);
        }
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
    private JPanel createQQPlotDialog(Node selected) {
        String dialogTitle = "Q-Q Plots";
        JPanel panel = new JPanel(); //new JPanel(findOwner(), dialogTitle, false);
        panel.setLayout(new BorderLayout());

//        dialog.setResizable(false);
//        dialog.getContentPane().setLayout(new BorderLayout());
        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();

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
    private static class QQPlotListener implements PropertyChangeListener {

        private QQPlotDisplayPanel display;


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


