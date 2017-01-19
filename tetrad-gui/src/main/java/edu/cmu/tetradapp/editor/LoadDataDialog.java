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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class LoadDataDialog extends JPanel {

    private File[] files;

    private DataLoaderSettings dataLoaderSettings;

    private transient DataModel[] dataModels;

    private JButton loadButton;

    private final JTextArea anomaliesTextArea;

    public JTextArea fileTextArea;

    private JScrollPane summaryDialogScrollPane;

    private int fileIndex;

    private JList fileList;

    private DefaultListModel fileListModel;

    private Box filePreviewBox;

    private String previewBoxBorderTitle;

    private String defaulyPreviewBoxBorderTitle;

    private Box panelContainer;

    private Box formatBox;

    private Box optionsBox;

    private Box buttonsBox;

    private JButton backButton;

    private JButton nextButton;

    //================================CONSTRUCTOR=======================//
    public LoadDataDialog(File... files) {
        this.files = files;

        this.fileTextArea = new JTextArea();

        this.fileListModel = new DefaultListModel();

        this.fileIndex = 0;

        this.defaulyPreviewBoxBorderTitle = "Data Preview (only first 20 rows): ";

        this.dataModels = new DataModel[files.length];

        this.anomaliesTextArea = new JTextArea();
    }

    //==============================PUBLIC METHODS=========================//
    public void showDataLoaderDialog() {
        // Show all chosen files in a list
        for (int i = 0; i < files.length; i++) {
            // Add each file name to the list model
            fileListModel.addElement(files[i].getName());
        }

        fileList = new JList(fileListModel);
        // This mode specifies that only a single item can be selected at any point of time
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Default to select the first file and show its preview
        fileList.setSelectedIndex(0);

        // List listener
        // use an anonymous inner class to implement the event listener interface
        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    fileIndex = fileList.getMinSelectionIndex();
                    if (fileIndex < 0) {
                        filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(defaulyPreviewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));
                        fileTextArea.setText("");
                    } else {
                        // Update the border title and show preview
                        previewBoxBorderTitle = defaulyPreviewBoxBorderTitle + files[fileIndex].getName();
                        filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(previewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));
                        setText(files[fileIndex], fileTextArea);
                    }
                }
            }
        });

        // Right click mouse on file name to show the close option
        fileList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                if (SwingUtilities.isRightMouseButton(e)) {
                    // Don't show the close option if there's only one file left
                    if (files.length == 1) {
                        return;
                    }

                    final int index = fileList.getSelectedIndex();

                    // Don't show the close option if selection is empty
                    if (index == -1) {
                        return;
                    }

                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem close = new JMenuItem("Remove this selected file from the loading list");
                    menu.add(close);

                    Point point = e.getPoint();
                    menu.show(fileList, point.x, point.y);

                    close.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            // Close tab to show confirmation dialog
                            int selectedAction = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                                    "Are you sure you want to remove this data file from the data loading list?",
                                    "Confirm", JOptionPane.OK_CANCEL_OPTION,
                                    JOptionPane.WARNING_MESSAGE);

                            if (selectedAction == JOptionPane.OK_OPTION) {
                                // Remove the file from list model
                                fileListModel.remove(index);

                                System.out.println("Removed file of index = " + index + " from data loading list");

                                // Also need to remove it from data structure
                                files = ArrayUtils.remove(files, index);
                            }
                        }
                    });
                }
            }
        });

        // Put the list in a scrollable area
        JScrollPane fileListScrollPane = new JScrollPane(fileList);
        fileListScrollPane.setAlignmentX(LEFT_ALIGNMENT);

        Box fileListBox = Box.createVerticalBox();
        fileListBox.setPreferredSize(new Dimension(315, 165));
        fileListBox.add(fileListScrollPane);
        // Use a titled border with 5 px inside padding - Zhou
        String fileListBoxBorderTitle = "Files to load (click to preview the data)";
        fileListBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(fileListBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Data loading params
        // The data loading params apply to all slected files
        // the users should know that the selected files should share these settings - Zhou
        dataLoaderSettings = new DataLoaderSettings(files);

        // Specify Format
        formatBox = dataLoaderSettings.specifyFormat();
        formatBox.setPreferredSize(new Dimension(475, 165));
        // Options settings
        optionsBox = dataLoaderSettings.selectOptions();
        optionsBox.setPreferredSize(new Dimension(475, 165));

        // Overall container
        // contains data preview panel, loading params panel, and load button
        Box container = Box.createVerticalBox();

        panelContainer = Box.createHorizontalBox();

        panelContainer.add(fileListBox);
        // Add some gap between file list and format box
        panelContainer.add(Box.createHorizontalStrut(10), 1);
        panelContainer.add(formatBox);
        panelContainer.add(optionsBox);
        optionsBox.setVisible(false);

        filePreviewBox = Box.createHorizontalBox();
        filePreviewBox.setPreferredSize(new Dimension(900, 260));

        // Setup file text area.
        // We don't want the users to edit in the preview area - Zhou
        fileTextArea.setEditable(false);
        fileTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Set the default preview for the default selected file
        setText(files[0], fileTextArea);

        // Add the scrollable text area in a scroller
        final JScrollPane filePreviewScrollPane = new JScrollPane(fileTextArea);
        filePreviewBox.add(filePreviewScrollPane);

        // Show the default selected filename as preview border title
        previewBoxBorderTitle = defaulyPreviewBoxBorderTitle + files[0].getName();

        // Use a titled border with 5 px inside padding - Zhou
        filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(previewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Next button to select options
        backButton = new JButton("< Back");

        // Back button listener
        backButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Hide the options settings and show format
                optionsBox.setVisible(false);
                formatBox.setVisible(true);

                // Show the next button
                nextButton.setVisible(true);

                // Hide load button
                loadButton.setVisible(false);

                // Hide back button
                backButton.setVisible(false);
            }
        });

        // Next button to select options
        nextButton = new JButton("Next >");

        // Next button listener
        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Hide the format settings and show options
                formatBox.setVisible(false);
                optionsBox.setVisible(true);

                // Show the back button
                backButton.setVisible(true);

                // Show load button
                loadButton.setVisible(true);

                // Hide next button
                nextButton.setVisible(false);
            }
        });

        // Load button
        // Show load button text based on the number of files - Zhou
        String loadBtnText = "Load the file with the specified settings";
        if (files.length > 1) {
            loadBtnText = "Load all files with the specified settings";
        }

        loadButton = new JButton(loadBtnText);
        loadButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Load button listener
        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                loadDataFiles();
            }
        });

        // Buttons box
        buttonsBox = Box.createHorizontalBox();
        buttonsBox.add(backButton);
        buttonsBox.add(Box.createHorizontalStrut(20), 1);
        buttonsBox.add(nextButton);
        buttonsBox.add(Box.createHorizontalStrut(20), 1);
        buttonsBox.add(loadButton);

        // Only show next button by default
        backButton.setVisible(false);
        loadButton.setVisible(false);

        // Put the panels together
        container.add(panelContainer);
        container.add(Box.createVerticalStrut(20));
        container.add(filePreviewBox);
        container.add(Box.createVerticalStrut(20));
        container.add(buttonsBox);

        /*
        setLayout(new BorderLayout());

        // Must have this section otherwise the File Loader dialog will be empty - Zhou
        Box e = Box.createVerticalBox();
        e.add(Box.createVerticalStrut(10));
        e.add(container);

        add(e, BorderLayout.CENTER);
         */
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
     * Load selected files and show the result summary dialog
     *
     * @param files
     * @return
     */
    public int loadDataFiles() {
        int summaryDialog;

        List<String> failedFiles = new ArrayList<String>();

        // Loading log info
        summaryDialogScrollPane = new JScrollPane(anomaliesTextArea);
        summaryDialogScrollPane.setPreferredSize(new Dimension(400, 200));

        // Try to load each file and store the file name for failed loadings
        for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
            System.out.println("File index = " + fileIndex);

            DataModel dataModel = dataLoaderSettings.loadDataWithSettings(fileIndex, anomaliesTextArea, files);
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
            summaryDialog = JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), summaryDialogScrollPane,
                    "Failed to load " + failedFiles.size() + " data file(s)!", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        } else {
            String[] actions = {"Done with data loading"};
            // Once done with data loading, close this logging dialog as well as the data loader dialog
            summaryDialog = JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), summaryDialogScrollPane,
                    "Data loaded successfully!", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);

            // Close the data loader dialog
            Window w = SwingUtilities.getWindowAncestor(loadButton);
            if (w != null) {
                w.setVisible(false);
            }
        }

        return summaryDialog;
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
