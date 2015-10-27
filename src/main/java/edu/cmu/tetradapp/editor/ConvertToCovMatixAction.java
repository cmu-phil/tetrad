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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.JOptionUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Ricardo Silva
 */
final class ConvertToCovMatixAction extends AbstractAction {

    /**
     * The data editor.                         -
     */
    private DataEditor dataEditor;

    /**
     * Creates a new action to split by collinear columns.
     */
    public ConvertToCovMatixAction(DataEditor editor) {
        super("Covariance Matrix");

        if (editor == null) {
            throw new NullPointerException();
        }

        this.dataEditor = editor;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        DataModel dataModel = getDataEditor().getSelectedDataModel();

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            if (!(dataSet.isContinuous())) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Must be a continuous data set " +
                                "or a covariance (or correlation) matrix.");
                return;
            }

            CovarianceMatrix corrMatrix = new CovarianceMatrix(dataSet);

            DataModelList list = new DataModelList();
            list.add(corrMatrix);
            getDataEditor().reset(list);
            getDataEditor().selectFirstTab();
        }
        else if (dataModel instanceof ICovarianceMatrix) {
            CovarianceMatrix covMatrix1 = (CovarianceMatrix) dataModel;
            CovarianceMatrix covMatrix2 = new CovarianceMatrix(covMatrix1);

            DataModelList list = new DataModelList();
            list.add(covMatrix2);
            getDataEditor().reset(list);
            getDataEditor().selectFirstTab();
        }
        else {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Must be a continuous data set " +
                            "or a covariance (or correlation) matrix.");
        }
    }

    private DataEditor getDataEditor() {
        return dataEditor;
    }
}





