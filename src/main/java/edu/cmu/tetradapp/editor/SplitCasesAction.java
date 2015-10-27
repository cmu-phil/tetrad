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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.LinkedList;
import java.util.List;

/**
 * Discretizes selected columns in a data set.
 *
 * @author Joseph Ramsey
 */
public final class SplitCasesAction extends AbstractAction {

    /**
     * The data editor.                         -
     */
    private DataEditor dataEditor;

    /**
     * Creates new action to discretize columns.
     */
    public SplitCasesAction(DataEditor editor) {
        super("Split Data by Cases");

        if (editor == null) {
            throw new NullPointerException();
        }

        this.dataEditor = editor;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        DataModel selectedDataModel = getDataEditor().getSelectedDataModel();

        if (!(selectedDataModel instanceof DataSet)) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Requires a tabular data set.");
        }

        List<Node> selectedVariables = new LinkedList<Node>();

        DataSet dataSet = (DataSet) selectedDataModel;
        int numColumns = dataSet.getNumColumns();

        for (int i = 0; i < numColumns; i++) {
            Node variable = dataSet.getVariable(i);

            if (dataSet.isSelected(variable)) {
                selectedVariables.add(variable);
            }
        }

        if (dataSet.getNumRows() == 0) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Data set is empty.");
            return;
        }

        if (selectedVariables.isEmpty()) {
            selectedVariables.addAll(dataSet.getVariables());
        }
//
//        SplitCasesParamsEditor editor = new SplitCasesParamsEditor(dataSet, 3);
//
//        int ret = JOptionPane.showOptionDialog(JOptionUtils.centeringComp(),
//                editor, "Split Data by Cases", JOptionPane.OK_CANCEL_OPTION,
//                JOptionPane.PLAIN_MESSAGE, null, null, null);
//
//        if (ret == JOptionPane.CANCEL_OPTION) {
//            return;
//        }
//
//        DataModelList list = editor.getSplits();
//        getDataEditor().reset(list);
//        getDataEditor().selectLastTab();

    }

    private DataEditor getDataEditor() {
        return dataEditor;
    }
}



