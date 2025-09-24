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
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public PlotMatrixAction(DataEditor editor) {
        super("Plot Matrix...");
        this.dataEditor = editor;
    }


    /**
     * {@inheritDoc}
     */
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




