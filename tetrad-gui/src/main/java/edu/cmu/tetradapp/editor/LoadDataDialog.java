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
import edu.pitt.dbmi.data.validation.DataValidation;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class LoadDataDialog extends JPanel {

    private List<File> loadedFiles;

    private List<String> validationResults;

    private List<String> failedFiles;

    private DataLoaderSettings dataLoaderSettings;

    private DataModelList dataModelList;

    private JTextPane validationResultTextPane;

    private JTextArea filePreviewTextArea;

    private JList fileList;

    private JList validationFileList;

    private JScrollPane filesToValidateScrollPane;

    private DefaultListModel fileListModel;

    private DefaultListModel validatedFileListModel;

    private Box filePreviewBox;

    private Box validationResultsBox;

    private String previewBoxBorderTitle;

    private String validationResultsContainerBorderTitle;

    private final String defaulyPreviewBoxBorderTitle;

    private Box container;

    private Box settingsContainer;

    private Box previewContainer;

    private Box buttonsContainer;

    private Box fileListBox;

    private Box formatBox;

    private Box optionsBox;

    private Box validationResultsContainer;

    private Box filesToValidateBox;

    private Box buttonsBox;

    private JButton addFileButton;

    private JButton step1Button;

    private JButton step2ForwardButton;

    private JButton step2BackwardButton;

    private JButton step3Button;

    private JButton loadButton;

    //================================CONSTRUCTOR=======================//
    public LoadDataDialog(File... files) {
        // Add all files into the loadedFiles list - Zhou
        // Arrays.asList: Returns a fixed-size list backed by the specified array.
        // You can't add to it; you can't remove from it. You can't structurally modify the List.
        // Create a LinkedList, which supports remove().
        this.loadedFiles = new LinkedList<>(Arrays.asList(files));

        // List is an Interface, you cannot instantiate an Interface
        // ArrayList is an implementation of List which can be instantiated
        // The default size of ArrayList if 10
        // Here we define validationResults as ArrayList for quick retrival by index
        this.validationResults = new ArrayList<>();

        this.failedFiles = new ArrayList<>();

        this.filePreviewTextArea = new JTextArea();

        this.fileListModel = new DefaultListModel();

        this.validatedFileListModel = new DefaultListModel();

        this.defaulyPreviewBoxBorderTitle = "Data Preview: ";

        this.dataModelList = new DataModelList();

        this.validationResultTextPane = new JTextPane();
    }

    //==============================PUBLIC METHODS=========================//
    public void showDataLoaderDialog() {
        // Overall container
        // contains data preview panel, loading params panel, and load button
        container = Box.createVerticalBox();
        // Must set the size of container, otherwise validationResultsContainer gets shrinked
        container.setPreferredSize(new Dimension(900, 510));

        // Data loading params
        // The data loading params apply to all slected files
        // the users should know that the selected files should share these settings - Zhou
        dataLoaderSettings = new DataLoaderSettings(loadedFiles);

        // Specify Format
        formatBox = dataLoaderSettings.specifyFormat();

        formatBox.setPreferredSize(new Dimension(900, 145));
        formatBox.setMaximumSize(new Dimension(900, 145));

        // Options settings
        optionsBox = dataLoaderSettings.selectOptions();

        optionsBox.setPreferredSize(new Dimension(900, 145));
        optionsBox.setMaximumSize(new Dimension(900, 145));

        // Contains file list and format/options
        settingsContainer = Box.createVerticalBox();

        settingsContainer.add(formatBox);
        settingsContainer.add(optionsBox);
        optionsBox.setVisible(false);

        // Add some padding between settingsContainer and preview container
        settingsContainer.add(Box.createVerticalStrut(10));

        // Add to overall container
        container.add(settingsContainer);

        // Preview container, contains file list and raw data preview
        previewContainer = Box.createHorizontalBox();
        previewContainer.setPreferredSize(new Dimension(900, 310));

        // Show all chosen files in a list
        for (File file : loadedFiles) {
            // Add each file name to the list model
            fileListModel.addElement(file.getName());
        }

        fileList = new JList(fileListModel);
        // This mode specifies that only a single item can be selected at any point of time
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Default to select the first file in the list and show its preview
        fileList.setSelectedIndex(0);

        // List listener
        // use an anonymous inner class to implement the event listener interface
        fileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int fileIndex = fileList.getMinSelectionIndex();
                    if (fileIndex < 0) {
                        filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(defaulyPreviewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));
                        filePreviewTextArea.setText("");
                    } else {
                        // Update the border title and show preview
                        previewBoxBorderTitle = defaulyPreviewBoxBorderTitle + loadedFiles.get(fileIndex).getName();
                        filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(previewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));
                        setPreview(loadedFiles.get(fileIndex), filePreviewTextArea);
                    }
                }
            }
        });

        // Right click mouse on file name to show the close option
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                if (SwingUtilities.isRightMouseButton(e)) {
                    final int index = fileList.getSelectedIndex();

                    System.out.println("About to remove file of index " + index);

                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem close = new JMenuItem("Remove this selected file from the loading list");
                    menu.add(close);

                    Point point = e.getPoint();
                    menu.show(fileList, point.x, point.y);

                    close.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            // Can't remove if there's only one file left
                            if (loadedFiles.size() == 1) {
                                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                                        "You can't remove when there's only one file.");
                            } else {
                                // Close tab to show confirmation dialog
                                int selectedAction = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                                        "Are you sure you want to remove this data file from the data loading list?",
                                        "Confirm", JOptionPane.OK_CANCEL_OPTION,
                                        JOptionPane.WARNING_MESSAGE);

                                if (selectedAction == JOptionPane.OK_OPTION) {
                                    // Remove the file from list model
                                    fileListModel.remove(index);

                                    // Also need to remove it from data structure
                                    // Shifts any subsequent elements to the left in the list
                                    System.out.println("Removing file of index = " + index + " from data loading list");
                                    loadedFiles.remove(index);

                                    // Reset the default selection and corresponding preview content
                                    fileList.setSelectedIndex(0);
                                    setPreview(loadedFiles.get(0), filePreviewTextArea);
                                }
                            }
                        }
                    });
                }
            }
        });

        // Put the list in a scrollable area
        JScrollPane fileListScrollPane = new JScrollPane(fileList);
        fileListScrollPane.setAlignmentX(LEFT_ALIGNMENT);

        fileListBox = Box.createVerticalBox();
        fileListBox.setMinimumSize(new Dimension(305, 310));
        fileListBox.setMaximumSize(new Dimension(305, 310));
        fileListBox.add(fileListScrollPane);

        // Add gap between file list and add new file button
        fileListBox.add(Box.createVerticalStrut(10));

        // Add new files button
        addFileButton = new JButton("Add more files to the loading list ...");

        // Add file button listener
        addFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Show file chooser
                JFileChooser fileChooser = new JFileChooser();
                String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
                fileChooser.setCurrentDirectory(new File(sessionSaveLocation));
                fileChooser.resetChoosableFileFilters();
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                // Only allow to add one file at a time
                fileChooser.setMultiSelectionEnabled(true);
                // Customize dialog title bar text
                fileChooser.setDialogTitle("Add more files");
                // The second argument sets both the title for the dialog window and the label for the approve button
                int _ret = fileChooser.showDialog(container, "Choose");

                if (_ret == JFileChooser.CANCEL_OPTION) {
                    return;
                }

                // File array that contains only one file
                final File[] newFiles = fileChooser.getSelectedFiles();

                System.out.println("Old loadedFiles list ");
                System.out.println(loadedFiles);

                // Add newly added files to the loading list
                for (File newFile : newFiles) {
                    // Do not add the same file twice
                    if (!loadedFiles.contains(newFile)) {
                        loadedFiles.add(newFile);
                        // Also add new file name to the file list model
                        fileListModel.addElement(newFile.getName());
                    }
                }

                System.out.println("New loadedFiles list ");
                System.out.println(loadedFiles);
            }
        });

        fileListBox.add(addFileButton);

        // Use a titled border with 5 px inside padding - Zhou
        String fileListBoxBorderTitle = "File to load";
        if (loadedFiles.size() > 1) {
            fileListBoxBorderTitle = "Files (right click to remove selected file)";
        }
        fileListBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(fileListBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        previewContainer.add(fileListBox);
        // Add some gap between file list and format box
        previewContainer.add(Box.createHorizontalStrut(10), 1);

        filePreviewBox = Box.createHorizontalBox();
        filePreviewBox.setMinimumSize(new Dimension(585, 315));
        filePreviewBox.setMaximumSize(new Dimension(585, 315));

        // Setup file text area.
        // We don't want the users to edit in the preview area - Zhou
        filePreviewTextArea.setEditable(false);
        filePreviewTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Set the default preview for the default selected file
        setPreview(loadedFiles.get(0), filePreviewTextArea);

        // Add the scrollable text area in a scroller
        final JScrollPane filePreviewScrollPane = new JScrollPane(filePreviewTextArea);
        filePreviewBox.add(filePreviewScrollPane);

        // Show the default selected filename as preview border title
        previewBoxBorderTitle = defaulyPreviewBoxBorderTitle + loadedFiles.get(0).getName();

        // Use a titled border with 5 px inside padding - Zhou
        filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(previewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Add to preview container
        previewContainer.add(filePreviewBox);

        // Add to overall container
        container.add(previewContainer);

        // Validation result
        validationResultsContainer = Box.createHorizontalBox();

        // A list of files to review
        filesToValidateBox = Box.createVerticalBox();
        filesToValidateBox.setMinimumSize(new Dimension(305, 420));
        filesToValidateBox.setMaximumSize(new Dimension(305, 420));

        // Create a new list model based on validation results
        validationFileList = new JList(validatedFileListModel);
        // This mode specifies that only a single item can be selected at any point of time
        validationFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // List listener
        validationFileList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int fileIndex = validationFileList.getSelectedIndex();
                    // -1 means no selection
                    if (fileIndex != -1) {
                        setValidationResult(validationResults.get(fileIndex), validationResultTextPane);
                    }
                }
            }
        });

        // Put the list in a scrollable area
        filesToValidateScrollPane = new JScrollPane(validationFileList);
        filesToValidateScrollPane.setAlignmentX(LEFT_ALIGNMENT);

        filesToValidateBox.add(filesToValidateScrollPane);

        validationResultsContainer.add(filesToValidateBox);

        // Add gap between file list and review conent
        validationResultsContainer.add(Box.createHorizontalStrut(10), 1);

        // Review content, contains errors or summary of loading
        validationResultsBox = Box.createHorizontalBox();
        validationResultsBox.setMinimumSize(new Dimension(568, 420));
        validationResultsBox.setMaximumSize(new Dimension(568, 420));

        validationResultTextPane.setContentType("text/html");
        validationResultTextPane.setEditable(false);

        final JScrollPane summaryScrollPane = new JScrollPane(validationResultTextPane);
        validationResultsBox.add(summaryScrollPane);

        validationResultsContainer.add(validationResultsBox);

        // Show the default selected filename as preview border title
        validationResultsContainerBorderTitle = "Step 3: Validate";

        // Use a titled border with 5 px inside padding - Zhou
        validationResultsContainer.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(validationResultsContainerBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Add to overall container
        container.add(validationResultsContainer);
        // Hide by default
        validationResultsContainer.setVisible(false);

        // Buttons
        // Step 1 button to specify format
        // You'll see Step 1 button only when you are ate step 2
        step1Button = new JButton("< Step 1: Specify Format");

        // Step 1 button listener
        step1Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Hide the options settings and show format
                optionsBox.setVisible(false);
                formatBox.setVisible(true);

                // Show the step 2 forward button
                step2ForwardButton.setVisible(true);

                // Hide the step 2 backward button
                step2BackwardButton.setVisible(false);

                // Hide step 3 button
                step3Button.setVisible(false);

                // Hide finish button
                loadButton.setVisible(false);

                // Hide back button
                step1Button.setVisible(false);
            }
        });

        // Step 2 forward button to select options
        // You'll see Step 2 forward button only when you are ate step 1
        step2ForwardButton = new JButton("Step 2: Select Options >");

        // Step 2 forward button listener
        step2ForwardButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Hide format
                formatBox.setVisible(false);

                // Show options
                optionsBox.setVisible(true);

                // Show the step 1 button
                step1Button.setVisible(true);

                // Hide step 2 forward button
                step2ForwardButton.setVisible(false);

                // Show step 3 button
                step3Button.setVisible(true);

                // Hide finish button
                loadButton.setVisible(false);
            }
        });

        // Step 2 button to select options
        // You'll see Step 2 backward button only when you are ate step 3
        step2BackwardButton = new JButton("< Step 2: Select Options");

        // Step 2 backward button listener
        step2BackwardButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Show file list
                fileListBox.setVisible(true);

                // Still hide format
                formatBox.setVisible(false);

                // Show options
                optionsBox.setVisible(true);

                // Show preview
                previewContainer.setVisible(true);

                // Hide summary
                validationResultsContainer.setVisible(false);

                // Show the step 1 button
                step1Button.setVisible(true);

                // Hide step 2 backward button
                step2BackwardButton.setVisible(false);

                // Show step 3 button
                step3Button.setVisible(true);

                // Hide finish button
                loadButton.setVisible(false);

                // Reset the list model
                validatedFileListModel.clear();

                // Removes all elements for each new validation
                validationResults.clear();

                // Also reset the failedFiles list
                failedFiles.clear();
            }
        });

        // Step 3 button
        step3Button = new JButton("Step 3: Validate >");

        // Step 3 button listener
        step3Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // First we want to do some basic form validation/checks
                // to eliminate user errors
                List<String> inputErrors = new ArrayList();

                if (!dataLoaderSettings.isColumnLabelSpecified()) {
                    inputErrors.add("- Please specify the case ID column label.");
                }

                if (!dataLoaderSettings.isOtherCommentMarkerSpecified()) {
                    inputErrors.add("- Please specify the comment marker.");
                }

                // Show all errors in one popup
                if (!inputErrors.isEmpty()) {
                    String inputErrorMessages = "";
                    for (String error : inputErrors) {
                        inputErrorMessages = inputErrorMessages + error + "\n";
                    }
                    JOptionPane.showMessageDialog(container, inputErrorMessages, "Input Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Validate all files and show error messages
                validateAllFiles();

                // Show result summary
                validationResultsContainer.setVisible(true);

                // Hide all inside settingsContainer
                fileListBox.setVisible(false);
                formatBox.setVisible(false);
                optionsBox.setVisible(false);

                // Use previewContainer instead of previewBox
                // since the previewContainer also contains padding
                previewContainer.setVisible(false);

                // Hide step 1 button
                step1Button.setVisible(false);

                // Hide step 2 forward button
                step2ForwardButton.setVisible(false);

                // Show step 2 backward button
                step2BackwardButton.setVisible(true);

                // Hide step 3 button
                step3Button.setVisible(false);

                // Show finish button
                loadButton.setVisible(true);

                // Determine if enable the finish button or not
                if (failedFiles.size() > 0) {
                    // Disable it
                    loadButton.setEnabled(false);
                } else {
                    // Enable it
                    loadButton.setEnabled(true);
                }
            }
        });

        // Load button
        loadButton = new JButton("Load");

        // Load data button listener
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    // Load all data files via data reader
                    loadAllFiles();
                } catch (IOException ex) {
                    Logger.getLogger(LoadDataDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                // Close the data loader dialog
                Window w = SwingUtilities.getWindowAncestor(loadButton);
                if (w != null) {
                    w.setVisible(false);
                }
            }
        });

        // Buttons container
        buttonsContainer = Box.createVerticalBox();

        // Add some padding between preview/summary and buttons container
        buttonsContainer.add(Box.createVerticalStrut(20));

        // Buttons box
        buttonsBox = Box.createHorizontalBox();
        buttonsBox.add(step1Button);
        // Don't use Box.createHorizontalStrut(20)
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(step2BackwardButton);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(step2ForwardButton);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(step3Button);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(loadButton);

        // Default to only show step forward button
        step1Button.setVisible(false);
        step2BackwardButton.setVisible(false);
        step3Button.setVisible(false);
        loadButton.setVisible(false);
        // Add to buttons container
        buttonsContainer.add(buttonsBox);

        // Add to overall container
        container.add(buttonsContainer);

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
     * Validate files with specified settings
     *
     * @return
     */
    private void validateAllFiles() {
        for (int i = 0; i < loadedFiles.size(); i++) {
            System.out.println("Validating file index = " + i);

            // Validate each individual file
            DataValidation validation = dataLoaderSettings.validateDataWithSettings(loadedFiles.get(i));

            String output = "<p>Validation result of " + loadedFiles.get(i).getName() + ": </p>";

            // Show some file info
            if (validation.hasInfos()) {
                output = output + "<p><b>File info: </b></p>";

                List<String> validationInfos = validation.getInfos();

                for (String validationInfo : validationInfos) {
                    output = output + "<p>" + validationInfo + "</p>";
                }
            }

            // Show errors if found
            if (validation.hasErrors()) {
                List<String> validationErrors = validation.getErrors();

                int errorCount = validationErrors.size();

                String errorCountString = (errorCount > 1) ? " errors" : " error";
                output = output + "<p style=\"color: red;\"><b>Validation failed!<br>Please fix the following " + errorCount + errorCountString + " and validate again:</b></p>";

                for (String validationError : validationErrors) {
                    output = output + "<p style=\"color: red;\">" + validationError + "</p>";
                }

                // Also add the file name to failed list
                // this determines if to show the Load button
                failedFiles.add(loadedFiles.get(i).getName());
            } else {
                output = output + "<p style=\"color: green;\"><b>Validation passed with no error!</b></p>";
            }

            validationResults.add(output);
        }

        showValidationResults();
    }

    private void showValidationResults() {
        // Create the validation file list with marker prefix
        // so users know if this file passed the validation or not
        // just by looking at the file name prefix - Zhou
        for (int i = 0; i < loadedFiles.size(); i++) {
            // Default to the check mark
            String unicodePrefix = "\u2713";
            if (failedFiles.contains(loadedFiles.get(i).getName())) {
                // Cross mark
                unicodePrefix = "\u2717";
            }
            // Add the unicode marker
            validatedFileListModel.addElement("[" + unicodePrefix + "] " + loadedFiles.get(i).getName());
        }

        // Set the default selected file
        // that's why we don't set the default selection when creating the JList
        validationFileList.setSelectedIndex(0);

        // Display validation result of the first file by default
        setValidationResult(validationResults.get(0), validationResultTextPane);
    }

    /**
     * Add all files to model once all can be loaded successfully
     */
    private void loadAllFiles() throws IOException {
        // Try to load each file and store the file name for failed loading
        for (int i = 0; i < loadedFiles.size(); i++) {
            DataModel dataModel = dataLoaderSettings.loadDataWithSettings(loadedFiles.get(i));

            // Add to dataModelList for further use
            if (dataModel != null) {
                // Must setName() here, file names will be used by the spreadsheet - Zhou
                dataModel.setName(loadedFiles.get(i).getName());
                dataModelList.add(dataModel);
                System.out.println("File index = " + i + " has been loaded successfully");
            }
        }
    }

    /**
     * This is called by LoadDataAction.java
     *
     * @return
     */
    public DataModelList getDataModels() {
        return dataModelList;
    }

    /**
     * Set the validation result content
     *
     * @param output
     * @param textPane
     */
    private void setValidationResult(String output, JTextPane textPane) {
        // Wrap the output in html
        textPane.setText("<html><body style=\"font-family: Monospaced; font-size: 10px \"" + output + "</body></html>");
        // Scroll back to top left
        textPane.setCaretPosition(0);
    }

    /**
     * Set the file preview content, will use Kevin's preview later - Zhou
     *
     * @param file
     * @param textArea
     */
    private static void setPreview(File file, JTextArea textArea) {
        try {
            textArea.setText("");

            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                StringBuilder text = new StringBuilder();
                String line;
                int nLine = 0;

                // Only preview the first 20 rows
                // Will need to think about how to preview big file - Zhou
                while ((line = in.readLine()) != null) {
                    text.append(line.substring(0, line.length())).append("\n");

                    if (text.length() > 50000) {
                        textArea.append("(This file is too big to show the preview ...)\n");
                        break;
                    }

                    nLine++;
                    if (nLine >= 20) {
                        break;
                    }
                }

                textArea.append(text.toString());

                textArea.setCaretPosition(0);
            }
        } catch (IOException e) {
            textArea.append("<<<ERROR READING FILE>>>");
        }
    }

}
