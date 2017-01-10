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
import edu.cmu.tetrad.util.JOptionUtils;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class LoadDataDialog extends JPanel {

    private RegularDataPanel dataParamsBox;

    private transient DataModel[] dataModels;

    private JButton loadButton;

    private final JTextArea anomaliesTextArea;

    private JScrollPane loadingLogScrollPane;

    private int fileIndex = 0;

    //================================CONSTRUCTOR=======================//
    public LoadDataDialog(final File... files) {
        this.dataModels = new DataModel[files.length];

        this.anomaliesTextArea = new JTextArea();
    }

    //==============================PUBLIC METHODS=========================//
    public void showDataLoaderDialog(final File... files) {

        final JTabbedPane previewTabbedPane = new JTabbedPane();

        // Each tab is a preview of a file - Zhou
        for (File file : files) {
            final JTextArea fileTextArea = new JTextArea();

            // Setup file text area.
            // We don't want the users to edit in the preview area - Zhou
            fileTextArea.setEditable(false);
            fileTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            // Set the preview content
            setText(file, fileTextArea);

            final JScrollPane scroll = new JScrollPane(fileTextArea);
            scroll.setPreferredSize(new Dimension(500, 240));
            previewTabbedPane.addTab(file.getName(), scroll);
        }

        // Data file preview
        Box dataPreviewBox = Box.createVerticalBox();
        dataPreviewBox.add(previewTabbedPane);
        // Use a titled border with 5 px inside padding - Zhou
        String borderTitle = "Data Preview (showing the first 20 rows, right click tab to close file)";
        dataPreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(borderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Data loading params
        // The data loading params apply to all slected files
        // the users should know that the selected files should share these settings - Zhou
        dataParamsBox = new RegularDataPanel(files);

        // Load button
        // Show load button text based on the number of files - Zhou
        String loadBtnText = "Load the selected file with the specified settings";
        if (files.length > 1) {
            loadBtnText = "Load all selected files with the specified settings";
        }

        loadButton = new JButton(loadBtnText);
        loadButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Load button listener
        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                loadDataFiles(files);
            }
        });

        // Overall container
        // contains data preview panel, loading params panel, and load button
        Box container = Box.createVerticalBox();

        Box panelContainer = Box.createHorizontalBox();

        panelContainer.add(dataPreviewBox);
        // Add some gap between preview and data loading params
        panelContainer.add(Box.createHorizontalStrut(10));
        panelContainer.add(dataParamsBox);

        // Put the panels together
        container.add(panelContainer);
        container.add(Box.createVerticalStrut(20));
        container.add(loadButton);

        setLayout(new BorderLayout());

        // Must have this section otherwise the File Loader dialog will be empty - Zhou
        Box e = Box.createVerticalBox();
        e.add(Box.createVerticalStrut(10));
        e.add(container);

        add(e, BorderLayout.CENTER);

        // Dialog without dialog buttons, because we use Load button to handle data loading
        // If we use the buttons come with JOptionPane.showOptionDialog(), the data loader dialog
        // will close automatically once we click one of the buttons.
        // We don't want to do this. We want to keep the data loader dialog in the backgroud of the
        // logging info dialog and close it if all files are loaded successfully.
        // Otherwise, still keep the data loader dialog there if fail to load any files - Zhou
        // Here no need to use the returned value since we are not handling the action buttons
        JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), container,
                "Data File Loader", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{}, null);
    }

    /**
     * Load selected files and show the logging dialog
     *
     * @param files
     * @return
     */
    public int loadDataFiles(File... files) {
        int loggingDialog;

        List<String> failedFiles = new ArrayList<String>();

        // Loading log info
        loadingLogScrollPane = new JScrollPane(anomaliesTextArea);
        loadingLogScrollPane.setPreferredSize(new Dimension(400, 200));

        // Try to load each file and store the file name for failed loadings
        for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
            System.out.println("File index = " + fileIndex);

            DataModel dataModel = dataParamsBox.loadData(fileIndex, anomaliesTextArea, files);
            if (dataModel == null) {
                System.out.println("Failed to load file index = " + fileIndex);

                // Add the file name to failed list
                failedFiles.add(files[fileIndex].getName());
            } else {
                addDataModel(dataModel, fileIndex, files[fileIndex].getName());
            }
        }

        // Show the logging message in popup - Zhou
        if (failedFiles.size() > 0) {
            // Show one button only
            String[] actions = {"Close this dialog"};
            // Just close the logging dialog and keep the data loader dialog with settings there
            // so users can make changes and load the data again - Zhou
            loggingDialog = JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), loadingLogScrollPane,
                    "Failed to load " + failedFiles.size() + " data file(s)!", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        } else {
            String[] actions = {"Done with data loading"};
            // Once done with data loading, close this logging dialog as well as the data loader dialog
            loggingDialog = JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), loadingLogScrollPane,
                    "Data loaded successfully!", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);

            // Close the data loader dialog
            Window w = SwingUtilities.getWindowAncestor(loadButton);
            if (w != null) {
                w.setVisible(false);
            }
        }

        return loggingDialog;
    }

    public DataModelList getDataModels() {
        DataModelList dataModelList = new DataModelList();

        for (DataModel dataModel : dataModels) {
            if (dataModel != null) {
                dataModelList.add(dataModel);
            }
        }

        return dataModelList;
    }

    private static void setText(File file, JTextArea textArea) {
        try {
            textArea.setText("");

            BufferedReader in = new BufferedReader(new FileReader(file));
            StringBuilder text = new StringBuilder();
            String line;
            int nLine = 0;

            // Only preview the first 20 rows
            while ((line = in.readLine()) != null) {
                text.append(line.substring(0, line.length())).append("\n");

                nLine++;
                if (nLine >= 20) {
                    break;
                }
            }

            textArea.append(text.toString());

            if (!textArea.isEditable()) {
                textArea.append(". . .");
            }

            textArea.setCaretPosition(0);
            in.close();
        } catch (IOException e) {
            textArea.append("<<<ERROR READING FILE>>>");
            textArea.setEditable(false);
        }
    }

    private void addDataModel(DataModel dataModel, int index, String name) {
        if (dataModel == null) {
            throw new NullPointerException();
        }

        dataModel.setName(name);
        this.dataModels[index] = dataModel;
    }

}
