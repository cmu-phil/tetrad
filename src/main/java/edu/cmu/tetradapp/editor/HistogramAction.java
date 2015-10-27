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
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Launches histograms for selected variables.
 *
 * @author Tyler Gibson
 */
public class HistogramAction extends AbstractAction {


    /**
     * The data editor that action is attached to.
     */
    private DataEditor dataEditor;


    /**
     * Constructs the <code>HistogramAction</code> given the <code>DataEditor</code>
     * that its attached to.
     *
     * @param editor
     */
    public HistogramAction(DataEditor editor) {
        super("Histograms...");
        this.dataEditor = editor;
    }

    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();
        if(dataSet == null || dataSet.getNumColumns() == 0){
            JOptionPane.showMessageDialog(findOwner(), "Cannot display a histogram for an empty data set.");
            return;
        }

        int[] selected = dataSet.getSelectedIndices();

        // if more then one column is selected then open up more than one histogram
        if (selected != null && selected.length >= 1) {

            // warn user if they selected more than 10
            if(selected.length > 10){
                int option = JOptionPane.showConfirmDialog(findOwner(), "You are about to open " + selected.length +
                " histograms, are you sure you want to proceed?", "Histogram Warning", JOptionPane.YES_NO_OPTION);
                // if selected no, return
                if(option == JOptionPane.NO_OPTION){
                    return;
                }
            }

            for (int index : selected) {
                JPanel component = createHistogramPanel(dataSet.getVariable(index));
                EditorWindow editorWindow = new EditorWindow(component, "Histogram", "Close", false, dataEditor);
                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                setLocation(editorWindow, index);
                editorWindow.setVisible(true);
            }
        } else {

            // No selected variable--just show a histogram for the first variable.
            JPanel component = createHistogramPanel(null);
            EditorWindow editorWindow = new EditorWindow(component, "Histogram", "Close", false, dataEditor);
            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);
        }
    }

    //============================== Private methods ============================//

    /**
     * Sets the location on the given dialog for the given index.
     */
    private void setLocation(EditorWindow dialog, int index) {
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
    private JPanel createHistogramPanel(Node selected) {
        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();
        Histogram histogram = new Histogram(dataSet);
        histogram.setTarget(selected == null ? null : selected.getName());
        HistogramView view = new HistogramView(histogram);

        Box box = Box.createHorizontalBox();
        box.add(view);
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
                JFrame.class, dataEditor);
    }
}




