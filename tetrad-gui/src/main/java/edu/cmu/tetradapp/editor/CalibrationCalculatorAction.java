///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Opens the Alpha/Penalty Discount Calculator for the selected data: one window with two tabs, the Alpha tab
 * ({@link AlphaCalculatorPanel}) for constraint-based searches and the Penalty Discount tab
 * ({@link PenaltyDiscountCalculatorPanel}) for score-based ones.
 *
 * <p>The two tabs answer the same question from the two sides of the same ledger. Both start from a budget on the
 * expected number of spurious edges over the whole search, stated as a fraction of the expected number of true
 * edges; both cost a pair of variables by the product of the two variables' parameter block sizes, read from the
 * data through {@link CalibrationBlockSizes} so that a score and a test on the same data are charged identically;
 * and each reports the other's equivalent setting, so that a BOSS run and a PC run can be put at comparable
 * strictness per pair. Where they differ is in how the false-positive and effect-size criteria combine: for a
 * penalty discount they push the same way and the larger wins, for a level they pull opposite ways and the Alpha
 * tab says whether both can be met at this sample size.</p>
 *
 * <p>The preflight checks here are the union of the two tabs' needs: a tabular data set or a covariance matrix, at
 * least two variables, and at least four rows (the Fisher z effective sample size N - |S| - 3 must be positive).
 * Each tab does its own work under a {@link edu.cmu.tetradapp.util.WatchedProcess} when its Compute button is
 * pressed; opening the window is instantaneous.</p>
 *
 * @author josephramsey
 * @see AlphaCalculatorPanel
 * @see PenaltyDiscountCalculatorPanel
 */
class CalibrationCalculatorAction extends AbstractAction {

    /**
     * The editor this action is attached to.
     */
    private final ISelectedModel dataEditor;

    /**
     * Constructs the action.
     *
     * @param editor The editor holding the selected data model.
     */
    public CalibrationCalculatorAction(ISelectedModel editor) {
        super("Alpha/Penalty Discount Calculator...");
        this.dataEditor = editor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        DataModel model = this.dataEditor.getSelectedDataModel();

        int p;
        int rows;

        if (model instanceof DataSet dataSet) {
            p = dataSet.getNumColumns();
            rows = dataSet.getNumRows();
        } else if (model instanceof ICovarianceMatrix cov) {
            p = cov.getDimension();
            rows = cov.getSampleSize();
        } else {
            JOptionPane.showMessageDialog(findOwner(),
                    "Need a tabular data set or a covariance matrix to calibrate a search.");
            return;
        }

        if (p < 2) {
            JOptionPane.showMessageDialog(findOwner(), "Need at least two variables to calibrate a search.");
            return;
        }

        if (rows < 4) {
            JOptionPane.showMessageDialog(findOwner(),
                    "Need a sample size of at least four to calibrate a search.");
            return;
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Alpha", AlphaCalculatorPanel.create(this.dataEditor, model));
        tabs.addTab("Penalty Discount", PenaltyDiscountCalculatorPanel.create(this.dataEditor, model));

        EditorWindow window = new EditorWindow(tabs, "Alpha/Penalty Discount Calculator", null, false,
                (JComponent) this.dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);
    }

    /**
     * The component to center message dialogs on.
     */
    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, (JComponent) this.dataEditor);
    }
}
