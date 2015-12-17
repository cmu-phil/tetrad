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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.model.EditorUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;

/**
 * Saves data to disk.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class SaveDataAction extends AbstractAction {

    /**
     * The dataEditor.                          -
     */
    private DataEditor dataEditor;


    /***
     * Search editor to get data from.
     */
    private MarkovBlanketSearchEditor searchEditor;


    /**
     * Formats all numbers.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Creates a new action to save data.
     */
    public SaveDataAction(DataEditor editor) {
        super("Save Data...");

        if (editor == null) {
            throw new NullPointerException("Data Editor must not be null.");
        }

        this.setDataEditor(editor);
    }


    public SaveDataAction(MarkovBlanketSearchEditor editor) {
        super("Save Data...");
        if (editor == null) {
            throw new NullPointerException("Editor must not be null");
        }
        this.searchEditor = editor;
    }


    /**
     * Performs the action of loading a session from a file.
     */
    public void actionPerformed(ActionEvent e) {
        try {
            saveData();
        } catch (IOException e1) {
            e1.printStackTrace();
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Error in saving: " + e1.getMessage());
        }
    }

    /**
     * Saves data in the selected data set to a file.
     */
    private void saveData() throws IOException {


        DataModel dataModel;
        if (this.dataEditor != null) {
            dataModel = getDataEditor().getDataModelList();
        } else {
            dataModel = searchEditor.getDataModel();
        }

        if (dataModel == null) {
            return;
        }

        if (dataModel instanceof DataSet) {
            File file = EditorUtils.getSaveFile("data", "txt", getDataEditor(), false, "Save Data...");

            if (file == null) {
                return;
            }

            char delimiter = '\t';

            if (file.getName().endsWith(".csv")) {
                delimiter = ',';
            }

            PrintWriter out;

            try {
                out = new PrintWriter(new FileOutputStream(file));
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Output file could not be opened: " + file);
            }

            DataSet dataSet = (DataSet) dataModel;

            if (dataSet.isContinuous()) {
                DataWriter.writeRectangularData(dataSet, out, delimiter);
            } else if (dataSet.isDiscrete()) {
                DataWriter.writeRectangularData(dataSet, out, delimiter);
            } else {
                DataWriter.writeRectangularData(dataSet, out, delimiter);
            }

            out.close();
        } else if (dataModel instanceof ICovarianceMatrix) {
            File file = EditorUtils.getSaveFile("data", "txt", getDataEditor(), false, "Save Data...");

            if (file == null) {
                return;
            }

            PrintWriter out;

            try {
                out = new PrintWriter(new FileOutputStream(file));
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Output file could not be opened: " + file);
            }

            DataWriter.writeCovMatrix((ICovarianceMatrix) dataModel, out, nf);

            out.close();
        } else if (dataModel instanceof DataModelList) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "Pick the name of the first file, e.g. data.txt." +
                    " A series of files will be generated based on this name, e.g. data1.txt, data2.txt, etc.");

            File file = EditorUtils.getSaveFile("data", "txt", getDataEditor(), false, "Save Data...");

            if (file == null) {
                return;
            }

            char delimiter = '\t';

            if (file.getName().endsWith(".csv")) {
                delimiter = ',';
            }

            DataModelList list = (DataModelList) dataModel;

            String[] tokens = file.getName().split(".txt");
            String base = tokens[0];

            for (int i = 0; i < list.size(); i++) {

                DataModel _dataModel = list.get(i);

                if (_dataModel == null) throw new NullPointerException("Null data model.");

                if (_dataModel instanceof DataSet) {
                    PrintWriter out;

                    try {
                        File file1 = new File(file.getParent() + "/" + base + "." + (i + 1) + ".txt");
                        System.out.println(file1);
                        out = new PrintWriter(new FileOutputStream(file1));
                    } catch (IOException e) {
                        throw new IllegalArgumentException(
                                "Output file could not be opened: " + file);
                    }

                    DataSet dataSet = (DataSet) ((DataModelList) dataModel).get(i);

                    if (dataSet.isContinuous()) {
                        DataWriter.writeRectangularData(dataSet, out, delimiter);
                    } else if (dataSet.isDiscrete()) {
                        DataWriter.writeRectangularData(dataSet, out, delimiter);
                    } else {
                        DataWriter.writeRectangularData(dataSet, out, delimiter);
                    }

                    out.close();
                }
            }
        } else {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Sorry, don't know how to save that.");
        }
    }

    private DataEditor getDataEditor() {
        return dataEditor;
    }

    private void setDataEditor(DataEditor dataEditor) {
        this.dataEditor = dataEditor;
    }
}





