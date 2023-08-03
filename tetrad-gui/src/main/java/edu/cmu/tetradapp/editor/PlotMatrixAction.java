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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Displays a Q-Q plot for a random variable.
 * <p>
 * A lot of the code borrows heavily from HistogramAction
 *
 * @author Michael Freenor
 */

class PlotMatrixAction extends AbstractAction {


    /**
     * The data editor that action is attached to.
     */
    private final DataEditor dataEditor;


    /**
     * Constructs the <code>QQPlotAction</code> given the <code>DataEditor</code> that It's attached to.
     */
    public PlotMatrixAction(DataEditor editor) {
        super("Plot Matrix...");
        this.dataEditor = editor;
    }


    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();
        if (dataSet == null || dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot display a scatter plot for an empty data set.");
            return;
        }

        JPanel panel = new PlotMatrix(dataSet);
        EditorWindow editorWindow = new EditorWindow(panel, "Plot Matrix", "Save", true, this.dataEditor);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);
    }

    //============================== Private methods ============================//

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, this.dataEditor);
    }
}



