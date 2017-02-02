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
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetradapp.model.DataWrapper;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.*;

/**
 * New data loading action.
 *
 * @author Joseph Ramsey
 */
final class LoadDataAction extends AbstractAction {

    /**
     * The dataEditor into which data is loaded. -
     */
    private DataEditor dataEditor;

    /**
     * Creates a new load data action for the given dataEditor.
     */
    public LoadDataAction(DataEditor editor) {
        super("Load Data...");

        if (editor == null) {
            throw new NullPointerException("Data Editor must not be null.");
        }

        this.dataEditor = editor;
    }

    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {

        // first warn user about other datasets being removed.
//        if (!isDataEmpty()) {
//            String message = "Loading data from a file will remove all existing data in the data editor. " +
//                    "Do you want to continue?";
//            int option = JOptionPane.showOptionDialog(this.dataEditor, message, "Data Removal Warning",
//                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, null, null);
//            // if not yes, cancelAll action.
//            if (option != JOptionPane.YES_OPTION) {
//                return;
//            }
//        }
        JFileChooser chooser = getJFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        // Sets the file chooser to allow multiple file selections
        chooser.setMultiSelectionEnabled(true);
        // Customize dialog title bar text
        chooser.setDialogTitle("Choose data files (choose multiple files with Ctrl or Shift key)");
        // The second argument sets both the title for the dialog window and the label for the approve button
        int _ret = chooser.showDialog(this.dataEditor, "Choose");

        if (_ret == JFileChooser.CANCEL_OPTION) {
            return;
        }

        // Files array
        File[] files = chooser.getSelectedFiles();

        Preferences.userRoot().put("fileSaveLocation", files[0].getParent());

        DataModelList dataModelList;

        // Show the data loader dialog to preview data ata and set their parameters
        LoadDataDialog loadData = new LoadDataDialog(files);
        loadData.showDataLoaderDialog();

        boolean keepData = false;

        if (!isDataEmpty()) {
            String message = "Would you like to replace the model data?";
            int option = JOptionPane.showOptionDialog(this.dataEditor, message, "Data Replacement",
                    0, JOptionPane.QUESTION_MESSAGE,
                    null, new String[]{"Replace", "Keep"}, "Replace");

            keepData = option == 1;
        }

        DataModelList _dataModelList = loadData.getDataModels();

        if (_dataModelList.isEmpty()) {
//            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                    "No files were loaded.");
            return;
        }

        if (keepData) {
            dataModelList = dataEditor.getDataModelList();
        } else {
            dataModelList = new DataModelList();
        }

        dataModelList.addAll(_dataModelList);

        dataEditor.replace(dataModelList);
        dataEditor.selectFirstTab();
        firePropertyChange("modelChanged", null, null);
    }

    //======================= private methods =========================//
    /**
     * States whether the data is empty.
     */
    private boolean isDataEmpty() {
        DataWrapper wrapper = this.dataEditor.getDataWrapper();
        DataModelList dataModels = wrapper.getDataModelList();
        for (DataModel model : dataModels) {
            if (model instanceof DataSet) {
                return ((DataSet) model).getNumRows() == 0;
            } else {
                // how do you know in this case? Just say false
                return false;
            }
        }
        return true;
    }

    private static JFileChooser getJFileChooser() {
        JFileChooser chooser = new JFileChooser();
        String sessionSaveLocation
                = Preferences.userRoot().get("fileSaveLocation", "");
        chooser.setCurrentDirectory(new File(sessionSaveLocation));
        chooser.resetChoosableFileFilters();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        return chooser;
    }
}
