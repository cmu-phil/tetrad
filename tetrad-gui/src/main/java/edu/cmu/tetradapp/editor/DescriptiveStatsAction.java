/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.util.DesktopController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Displays descriptive statistics for a random variable.
 *
 * @author Michael Freenor
 */

class DescriptiveStatsAction extends AbstractAction {

    /**
     * The data editor that action is attached to.
     */
    private final DataEditor dataEditor;


    /**
     * Constructs the <code>DescriptiveStatsAction</code> given the <code>DataEditor</code> that it's attached to.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public DescriptiveStatsAction(DataEditor editor) {
        super("Descriptive Statistics...");
        this.dataEditor = editor;
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        if (!(this.dataEditor.getSelectedDataModel() instanceof DataSet dataSet)) {
            JOptionPane.showMessageDialog(findOwner(), "Need a tabular dataset to generate descriptive statistics.");
            return;
        }

        if (dataSet == null || dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot generate descriptive statistics on an empty data set.");
            return;
        }

        Box panel = createDescriptiveStatsDialog();

        EditorWindow window = new EditorWindow(panel,
                "Descriptive Statistics", "Close", false, this.dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

    }

    //============================== Private methods ============================//

    /**
     * Creates a dialog that is showing the histogram for the given node (if null one is selected for you)
     */
    private Box createDescriptiveStatsDialog() {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();

        String coonstantColumnsString = "Constant Columns: ";
        assert dataSet != null;
        java.util.List<Node> constantColumns = DataTransforms.getConstantColumns(dataSet);
        coonstantColumnsString += constantColumns.isEmpty() ? "None" : constantColumns.toString();
        String nonsingularString = null;

        if (dataSet.isContinuous()) {
            nonsingularString = "Example Nonsingular (2 vars): ";
            CovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
            List<Node> exampleNonsingular = DataUtils.getExampleNonsingular(covarianceMatrix, 2);
            nonsingularString += exampleNonsingular == null ? "None" : exampleNonsingular.toString();
        }

        Box box = Box.createVerticalBox();

        DescriptiveStatisticsJTable jTable = new DescriptiveStatisticsJTable(dataSet);
        jTable.setTransferHandler(new DescriptiveStatisticsTransferHandler());

        JMenuBar bar = new JMenuBar();
        JMenuItem copyCells = new JMenuItem("Copy Cells");
        copyCells.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyCells.addActionListener(e -> {
            Action copyAction = TransferHandler.getCopyAction();
            ActionEvent actionEvent = new ActionEvent(jTable,
                    ActionEvent.ACTION_PERFORMED, "copy");
            copyAction.actionPerformed(actionEvent);
        });

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(copyCells);
        bar.add(editMenu);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(bar, BorderLayout.NORTH);
        panel.add(new JScrollPane(jTable), BorderLayout.CENTER);

        box.add(panel);

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel(coonstantColumnsString));
        b1.add(Box.createHorizontalGlue());
        box.add(b1);

        Box b2 = Box.createHorizontalBox();
        if (nonsingularString != null) {
            b2.add(new JLabel(nonsingularString));
        }

//        b2.add(new JLabel(nonsingularString));
        b2.add(Box.createHorizontalGlue());
        box.add(b2);

        return box;
    }

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, this.dataEditor);
    }
}


