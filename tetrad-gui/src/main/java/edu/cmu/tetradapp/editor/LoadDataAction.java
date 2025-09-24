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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetradapp.model.DataWrapper;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * New data loading action.
 *
 * @author josephramsey
 */
final class LoadDataAction extends AbstractAction {

    private static final long serialVersionUID = 929333197876935694L;

    /**
     * The dataEditor into which data is loaded. -
     */
    private final DataEditor dataEditor;

    /**
     * Creates a new load data action for the given dataEditor.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public LoadDataAction(DataEditor editor) {
        super("Load Data...");

        if (editor == null) {
            throw new NullPointerException("Data Editor must not be null.");
        }

        this.dataEditor = editor;
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

    //======================= private methods =========================//

    /**
     * {@inheritDoc}
     * <p>
     * Performs the action of loading a session from a file.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = LoadDataAction.getJFileChooser();
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
        try {
            loadData.showDataLoaderDialog();
        } catch (IOException ex) {
            Logger.getLogger(LoadDataAction.class.getName()).log(Level.SEVERE, null, ex);
        }

        boolean keepData = false;

        if (!isDataEmpty()) {
            final String message = "Would you like to replace the model data?";
            int option = JOptionPane.showOptionDialog(this.dataEditor, message, "Data Replacement",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, new String[]{"Replace", "Keep"}, "Replace");

            keepData = option == 1;
        }

        DataModelList _dataModelList = loadData.getDataModels();

        if (_dataModelList.isEmpty()) {
            return;
        }

        if (keepData) {
            dataModelList = this.dataEditor.getDataModelList();
        } else {
            dataModelList = new DataModelList();
        }

        dataModelList.addAll(_dataModelList);

        this.dataEditor.replace(dataModelList);
        this.dataEditor.selectFirstTab();
        firePropertyChange("modelChanged", null, null);
    }

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

}

