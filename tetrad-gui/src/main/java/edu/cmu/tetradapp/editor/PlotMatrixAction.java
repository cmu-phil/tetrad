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
 * Displays a plot matrix for a random variable.
 */
class PlotMatrixAction extends AbstractAction {

    /**
     * The data editor that action is attached to.
     */
    private final ISelectedModel dataEditor;


    /**
     * Constructs the <code>PlotMatrixAction</code> given the <code>DataEditor</code> that It's attached to.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public PlotMatrixAction(ISelectedModel editor) {
        super("Plot Matrix...");

        if (!(editor instanceof JComponent)) {
            throw new IllegalArgumentException("Editor must be a JComponent");
        }

        this.dataEditor = editor;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Object model = this.dataEditor.getSelectedDataModel();

        if (!(model instanceof DataSet dataSet)) {
            String kind = (model == null) ? "nothing" : model.getClass().getSimpleName();
            JOptionPane.showMessageDialog(
                    findOwner(),
                    "Plot Matrix requires a DataSet (tabular data).\nSelected: " + kind
            );
            return;
        }

        if (dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(),
                    "Cannot display a plot matrix for an empty data set.");
            return;
        }

        JPanel panel = new PlotMatrix(dataSet);
        EditorWindow editorWindow = new EditorWindow(panel, "Plot Matrix", null, false,
                (JComponent) this.dataEditor);

        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
        editorWindow.pack();
        editorWindow.setVisible(true);
    }

    //============================== Private methods ============================//

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, (JComponent) this.dataEditor);
    }
}




