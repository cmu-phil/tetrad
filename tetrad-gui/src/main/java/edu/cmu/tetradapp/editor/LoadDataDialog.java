///////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.pitt.dbmi.data.reader.preview.BasicDataPreviewer;
import edu.pitt.dbmi.data.reader.preview.DataPreviewer;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file should be loaded.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class LoadDataDialog extends JPanel {

    @Serial
    private static final long serialVersionUID = 2299304318793152418L;

    /**
     * The files to be loaded.
     */
    private final List<File> loadedFiles;

    /**
     * The validation results.
     */
    private final List<String> validationResults;

    /**
     * The files that failed to load.
     */
    private final List<String> failedFiles;

    /**
     * The data model list.
     */
    private final DataModelList dataModelList;

    /**
     * The text pane for the validation results.
     */
    private final JTextPane validationResultTextPane;

    /**
     * The text area for the file preview.
     */
    private final JTextArea filePreviewTextArea;

    /**
     * The line to start the preview from.
     */
    private final int previewFromLine;

    /**
     * The line to end the preview at.
     */
    private final int previewToLine;

    /**
     * The number of characters to show per line in the preview.
     */
    private final int previewNumOfCharactersPerLine;

    /**
     * The list model for the file list.
     */
    private final DefaultListModel fileListModel;

    /**
     * The list model for the validated file list.
     */
    private final DefaultListModel validatedFileListModel;

    /**
     * The default border title for the preview box.
     */
    private final String defaulyPreviewBoxBorderTitle;

    /**
     * The load data settings.
     */
    private LoadDataSettings loadDataSettings;

    /**
     * The file list.
     */
    private JList fileList;

    /**
     * The validation file list.
     */
    private JList validationFileList;

    /**
     * The loading indicator dialog.
     */
    private JDialog loadingIndicatorDialog;

    /**
     * The preview box border title.
     */
    private Box filePreviewBox;

    /**
     * The preview box border title.
     */
    private String previewBoxBorderTitle;

    /**
     * The container for the dialog.
     */
    private Box container;

    /**
     * The container for the preview.
     */
    private Box previewContainer;

    /**
     * The container for the file list.
     */
    private Box fileListBox;

    /**
     * The container for the validation results.
     */
    private Box basicSettingsBox;

    /**
     * The container for the validation results.
     */
    private Box advancedSettingsBox;

    /**
     * The container for the validation results.
     */
    private Box validationResultsContainer;

    /**
     * The settings button.
     */
    private JButton settingsButton;

    /**
     * The validate button.
     */
    private JButton validateButton;

    /**
     * The load button.
     */
    private JButton loadButton;

    //================================CONSTRUCTOR=======================//

    /**
     * <p>Constructor for LoadDataDialog.</p>
     *
     * @param files a {@link java.io.File} object
     */
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

        // Show preview from the first line to line 20,
        // only display up to 100 chars per line,
        // apend ... if longer than that
        this.previewFromLine = 1;
        this.previewToLine = 20;
        this.previewNumOfCharactersPerLine = 100;

        this.fileListModel = new DefaultListModel();

        this.validatedFileListModel = new DefaultListModel();

        this.defaulyPreviewBoxBorderTitle = "Data Preview: ";

        this.dataModelList = new DataModelList();

        this.validationResultTextPane = new JTextPane();

        this.loadingIndicatorDialog = new JDialog();
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * <p>showDataLoaderDialog.</p>
     *
     * @throws java.io.IOException if any.
     */
    public void showDataLoaderDialog() throws IOException {
        // Overall container
        // contains data preview panel, loading params panel, and load button
        this.container = Box.createVerticalBox();
        // Must set the size of container, otherwise validationResultsContainer gets shrinked
        this.container.setPreferredSize(new Dimension(900, 620));

        // Data loading params
        // The data loading params apply to all slected files
        // the users should know that the selected files should share these settings - Zhou
        this.loadDataSettings = new LoadDataSettings(this.loadedFiles);

        // Basic settings
        this.basicSettingsBox = this.loadDataSettings.basicSettings();

        // Advanced settings
        this.advancedSettingsBox = this.loadDataSettings.advancedSettings();

        // Contains file list and format/options
        Box settingsContainer = Box.createVerticalBox();

        settingsContainer.add(this.basicSettingsBox);
        settingsContainer.add(Box.createVerticalStrut(10));
        settingsContainer.add(this.advancedSettingsBox);
        //advancedSettingsBox.setVisible(false);

        // Add some padding between settingsContainer and preview container
        settingsContainer.add(Box.createVerticalStrut(10));

        // Add to overall container
        this.container.add(settingsContainer);

        // Preview container, contains file list and raw data preview
        this.previewContainer = Box.createHorizontalBox();
        this.previewContainer.setPreferredSize(new Dimension(900, 250));

        // Show all chosen files in a list
        for (File file : this.loadedFiles) {
            // Add each file name to the list model
            this.fileListModel.addElement(file.getName());
        }

        this.fileList = new JList(this.fileListModel);
        // This mode specifies that only a single item can be selected at any point of time
        this.fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Default to select the first file in the list and show its preview
        this.fileList.setSelectedIndex(0);

        // List listener
        // use an anonymous inner class to implement the event listener interface
        this.fileList.addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                int fileIndex = this.fileList.getMinSelectionIndex();
                if (fileIndex < 0) {
                    this.filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(this.defaulyPreviewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));
                    this.filePreviewTextArea.setText("");
                } else {
                    // Update the border title and show preview
                    this.previewBoxBorderTitle = this.defaulyPreviewBoxBorderTitle + this.loadedFiles.get(fileIndex).getName();
                    this.filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(this.previewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));
                    setPreview(this.loadedFiles.get(fileIndex), this.filePreviewTextArea);
                }
            }
        });

        // Right click mouse on file name to show the close option
        this.fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                if (SwingUtilities.isRightMouseButton(e) || e.isControlDown()) {
                    int index = LoadDataDialog.this.fileList.getSelectedIndex();

                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem close = new JMenuItem("Remove this selected file from the loading list");
                    menu.add(close);

                    Point point = e.getPoint();
                    menu.show(LoadDataDialog.this.fileList, point.x, point.y);

                    close.addActionListener((evt) -> {
                        // Can't remove if there's only one file left
                        if (LoadDataDialog.this.loadedFiles.size() == 1) {
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
                                LoadDataDialog.this.fileListModel.remove(index);

                                // Also need to remove it from data structure
                                // Shifts any subsequent elements to the left in the list
                                System.out.println("Removing file of index = " + index + " from data loading list");
                                LoadDataDialog.this.loadedFiles.remove(index);

                                // Reset the default selection and corresponding preview content
                                LoadDataDialog.this.fileList.setSelectedIndex(0);
                                setPreview(LoadDataDialog.this.loadedFiles.get(0), LoadDataDialog.this.filePreviewTextArea);
                            }
                        }
                    });
                }
            }
        });

        // Put the list in a scrollable area
        JScrollPane fileListScrollPane = new JScrollPane(this.fileList);
        fileListScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        this.fileListBox = Box.createVerticalBox();
        this.fileListBox.setMinimumSize(new Dimension(305, 250));
        this.fileListBox.setMaximumSize(new Dimension(305, 250));
        this.fileListBox.add(fileListScrollPane);

        // Add gap between file list and add new file button
        this.fileListBox.add(Box.createVerticalStrut(10));

        // Add new files button
        JButton addFileButton = new JButton("Add more files to the loading list ...");

        // Add file button listener
        addFileButton.addActionListener((e) -> {
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
            int _ret = fileChooser.showDialog(this.container, "Choose");

            if (_ret == JFileChooser.CANCEL_OPTION) {
                return;
            }

            // File array that contains only one file
            File[] newFiles = fileChooser.getSelectedFiles();

            // Add newly added files to the loading list
            for (File newFile : newFiles) {
                // Do not add the same file twice
                if (!this.loadedFiles.contains(newFile)) {
                    this.loadedFiles.add(newFile);
                    // Also add new file name to the file list model
                    this.fileListModel.addElement(newFile.getName());
                }
            }
        });

        this.fileListBox.add(addFileButton);

        // Use a titled border with 5 px inside padding - Zhou
        final String fileListBoxBorderTitle = "Files (right click to remove selected file)";
        this.fileListBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(fileListBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        this.previewContainer.add(this.fileListBox);
        // Add some gap between file list and preview box
        this.previewContainer.add(Box.createHorizontalStrut(10), 1);

        this.filePreviewBox = Box.createVerticalBox();
        this.filePreviewBox.setMinimumSize(new Dimension(585, 250));
        this.filePreviewBox.setMaximumSize(new Dimension(585, 250));

        // Setup file text area.
        // We don't want the users to edit in the preview area - Zhou
        this.filePreviewTextArea.setEditable(false);
        this.filePreviewTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Set the default preview for the default selected file
        setPreview(this.loadedFiles.get(0), this.filePreviewTextArea);

        // Add the scrollable text area in a scroller
        JScrollPane filePreviewScrollPane = new JScrollPane(this.filePreviewTextArea);
        this.filePreviewBox.add(filePreviewScrollPane);

        // Add gap between preview text area and the help instruction
        this.filePreviewBox.add(Box.createVerticalStrut(10));

        JLabel previewInstructionText = new JLabel(String.format("Showing from line %d to line %d, up to %d characters per line", this.previewFromLine, this.previewToLine, this.previewNumOfCharactersPerLine));

        // Add the instruction
        this.filePreviewBox.add(previewInstructionText);

        // Show the default selected filename as preview border title
        this.previewBoxBorderTitle = this.defaulyPreviewBoxBorderTitle + this.loadedFiles.get(0).getName();

        // Use a titled border with 5 px inside padding - Zhou
        this.filePreviewBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(this.previewBoxBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Add to preview container
        this.previewContainer.add(this.filePreviewBox);

        // Add to overall container
        this.container.add(this.previewContainer);

        // Validation result
        this.validationResultsContainer = Box.createVerticalBox();

        Box validationSummaryBox = Box.createHorizontalBox();

        JLabel validationSummaryText = new JLabel("Please review. You can change the settings or add/remove files by clicking the Settings button.");

        validationSummaryBox.add(validationSummaryText);

        Box validationResultsBox = Box.createHorizontalBox();

        // A list of files to review
        Box filesToValidateBox = Box.createVerticalBox();
        filesToValidateBox.setMinimumSize(new Dimension(305, 430));
        filesToValidateBox.setMaximumSize(new Dimension(305, 430));

        // Create a new list model based on validation results
        this.validationFileList = new JList(this.validatedFileListModel);
        // This mode specifies that only a single item can be selected at any point of time
        this.validationFileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // List listener
        this.validationFileList.addListSelectionListener((e) -> {
            if (!e.getValueIsAdjusting()) {
                int fileIndex = this.validationFileList.getSelectedIndex();
                // -1 means no selection
                if (fileIndex != -1) {
                    setValidationResult(this.validationResults.get(fileIndex), this.validationResultTextPane);
                }
            }
        });

        // Put the list in a scrollable area
        JScrollPane filesToValidateScrollPane = new JScrollPane(this.validationFileList);
        filesToValidateScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        filesToValidateBox.add(filesToValidateScrollPane);

        validationResultsBox.add(filesToValidateBox);

        // Add gap between file list and message content
        validationResultsBox.add(Box.createHorizontalStrut(10), 1);

        // Review content, contains errors or summary of loading
        Box validationMessageBox = Box.createVerticalBox();
        validationMessageBox.setMinimumSize(new Dimension(560, 430));
        validationMessageBox.setMaximumSize(new Dimension(560, 430));

        this.validationResultTextPane.setContentType("text/html");
        this.validationResultTextPane.setEditable(false);

        JScrollPane summaryScrollPane = new JScrollPane(this.validationResultTextPane);
        validationMessageBox.add(summaryScrollPane);

        validationResultsBox.add(validationMessageBox);

        // Put things into container
        this.validationResultsContainer.add(validationSummaryBox);
        this.validationResultsContainer.add(Box.createVerticalStrut(10));
        this.validationResultsContainer.add(validationResultsBox);

        // Show the default selected filename as preview border title
        String validationResultsContainerBorderTitle = "Validate";

        // Use a titled border with 5 px inside padding - Zhou
        this.validationResultsContainer.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(validationResultsContainerBorderTitle), new EmptyBorder(5, 5, 5, 5)));

        // Add to overall container
        this.container.add(this.validationResultsContainer);
        // Hide by default
        this.validationResultsContainer.setVisible(false);

        // Buttons
        // Settings button
        this.settingsButton = new JButton("< Settings");

        // Step 2 backward button listener
        this.settingsButton.addActionListener((e) -> {
            // Show file list
            this.fileListBox.setVisible(true);

            this.basicSettingsBox.setVisible(true);

            // Show options
            this.advancedSettingsBox.setVisible(true);

            // Show preview
            this.previewContainer.setVisible(true);

            // Hide summary
            this.validationResultsContainer.setVisible(false);

            // Hide step 1 backward button
            this.settingsButton.setVisible(false);

            // Show validate button
            this.validateButton.setVisible(true);

            // Hide finish button
            this.loadButton.setVisible(false);

            // Reset the list model
            this.validatedFileListModel.clear();

            // Removes all elements for each new validation
            this.validationResults.clear();

            // Also reset the failedFiles list
            this.failedFiles.clear();
        });

        // Validate button
        this.validateButton = new JButton("Validate >");

        // Step 3 button listener
        this.validateButton.addActionListener((e) -> {
            // First we want to do some basic form validation/checks
            // to eliminate user errors
            List<String> inputErrors = new ArrayList();

            if (!this.loadDataSettings.isColumnLabelSpecified()) {
                inputErrors.add("- Please specify the column labels to ignore.");
            }

            if (!this.loadDataSettings.isOtherCommentMarkerSpecified()) {
                inputErrors.add("- Please specify the comment marker.");
            }

            // Show all errors in one popup
            if (!inputErrors.isEmpty()) {
                StringBuilder inputErrorMessages = new StringBuilder();
                for (String error : inputErrors) {
                    inputErrorMessages.append(error).append("\n");
                }
                JOptionPane.showMessageDialog(this.container, inputErrorMessages.toString(), "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Disable the button and change the button text
            this.validateButton.setEnabled(false);
            this.validateButton.setText("Validating ...");

            // New thread to run the validation and hides the loading indicator
            // and shows the validation results once the validation process is done - Zhou
            new Thread(() -> {
                // Validate all files and set error messages
                validateAllFiles();

                // Schedule a Runnable which will be executed on the Event Dispatching Thread
                // SwingUtilities.invokeLater means that this call will return immediately
                // as the event is placed in Event Dispatcher Queue,
                // and run() method will run asynchronously
                SwingUtilities.invokeLater(() -> {
                    // Hide the loading indicator
                    hideLoadingIndicator();

                    // Show result summary
                    this.validationResultsContainer.setVisible(true);

                    // Hide all inside settingsContainer
                    this.fileListBox.setVisible(false);
                    this.basicSettingsBox.setVisible(false);
                    this.advancedSettingsBox.setVisible(false);

                    // Use previewContainer instead of previewBox
                    // since the previewContainer also contains padding
                    this.previewContainer.setVisible(false);

                    // Show step 2 backward button
                    this.settingsButton.setVisible(true);

                    // Hide validate button
                    this.validateButton.setVisible(false);

                    // Show finish button
                    this.loadButton.setVisible(true);

                    // Determine if enable the finish button or not
                    // Disable it
                    // Enable it
                    this.loadButton.setEnabled(this.failedFiles.size() <= 0);

                    // Enable the button and hange back the button text
                    this.validateButton.setEnabled(true);
                    this.validateButton.setText("Validate >");
                });
            }).start();

            // Create the loading indicator dialog and show
            showLoadingIndicator("Validating...", e);
        });

        // Load button
        this.loadButton = new JButton("Load");

        // Load data button listener
        this.loadButton.addActionListener((e) -> {
            // Change button text
            this.loadButton.setEnabled(false);
            this.loadButton.setText("Loading ...");

            // Load all data files and hide the loading indicator once done
            new Thread(() -> {
                try {
                    // Load all data files via data reader
                    loadAllFiles();
                } catch (IOException ex) {
                    Logger.getLogger(LoadDataDialog.class.getName()).log(Level.SEVERE, null, ex);
                }

                // Schedule a Runnable which will be executed on the Event Dispatching Thread
                // SwingUtilities.invokeLater means that this call will return immediately
                // as the event is placed in Event Dispatcher Queue,
                // and run() method will run asynchronously
                SwingUtilities.invokeLater(() -> {
                    // Hide the loading indicator
                    hideLoadingIndicator();

                    // Close the data loader dialog
                    Window w = SwingUtilities.getWindowAncestor(this.loadButton);
                    if (w != null) {
                        w.setVisible(false);
                    }
                });
            }).start();

            // Create the loading indicator dialog and show
            showLoadingIndicator("Loading...", e);
        });

        // Buttons container
        Box buttonsContainer = Box.createVerticalBox();

        // Add some padding between preview/summary and buttons container
        buttonsContainer.add(Box.createVerticalStrut(20));

        // Buttons box
        Box buttonsBox = Box.createHorizontalBox();
        buttonsBox.add(this.settingsButton);
        // Don't use Box.createHorizontalStrut(20)
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(this.validateButton);
        buttonsBox.add(Box.createRigidArea(new Dimension(20, 0)));
        buttonsBox.add(this.loadButton);

        // Default to only show step forward button
        this.settingsButton.setVisible(false);
        this.loadButton.setVisible(false);
        // Add to buttons container
        buttonsContainer.add(buttonsBox);

        // Add to overall container
//        container.add(buttonsContainer);
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(this.container, BorderLayout.CENTER);
        mainPanel.add(buttonsContainer, BorderLayout.SOUTH);

        // Dialog without dialog buttons, because we use Load button to handle data loading
        // If we use the buttons come with JOptionPane.showOptionDialog(), the data loader dialog
        // will close automatically once we click one of the buttons.
        // We don't want to do this. We want to keep the data loader dialog in the backgroud of the
        // logging info dialog and close it if all files are loaded successfully.
        // Otherwise, still keep the data loader dialog there if fail to load any files - Zhou
        // Here no need to use the returned value since we are not handling the action buttons
        JOptionPane.showOptionDialog(JOptionUtils.centeringComp(), mainPanel,
                "Data File Loader", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{}, null);
    }

    /**
     * Create the loading indicator dialog and show
     */
    private void showLoadingIndicator(String message, ActionEvent evt) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        // An indeterminate progress bar continuously displays animation
        progressBar.setIndeterminate(true);

        Box dataLoadingIndicatorBox = Box.createVerticalBox();
        dataLoadingIndicatorBox.setPreferredSize(new Dimension(200, 60));

        JLabel label = new JLabel(message);
        // JLabel label = new JLabel(message, SwingConstants.CENTER); doesn't
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        Box progressBarBox = Box.createHorizontalBox();
        progressBarBox.add(Box.createRigidArea(new Dimension(10, 1)));
        progressBarBox.add(progressBar);
        progressBarBox.add(Box.createRigidArea(new Dimension(10, 1)));

        // Put the label on top of progress bar
        dataLoadingIndicatorBox.add(Box.createVerticalStrut(10));
        dataLoadingIndicatorBox.add(label);
        dataLoadingIndicatorBox.add(Box.createVerticalStrut(10));
        dataLoadingIndicatorBox.add(progressBarBox);

        Frame ancestor = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, (Component) evt.getSource());

        // Set modal true to block user input to other top-level windows when shown
        this.loadingIndicatorDialog = new JDialog(ancestor, true);
        // Remove the whole dialog title bar
        this.loadingIndicatorDialog.setUndecorated(true);
        this.loadingIndicatorDialog.getContentPane().add(dataLoadingIndicatorBox);
        this.loadingIndicatorDialog.pack();
        this.loadingIndicatorDialog.setLocationRelativeTo(JOptionUtils.centeringComp());

        this.loadingIndicatorDialog.setVisible(true);
    }

    /**
     * Hide the loading indicator
     */
    private void hideLoadingIndicator() {
        this.loadingIndicatorDialog.setVisible(false);
        // Also release all of the native screen resources used by this dialog
        this.loadingIndicatorDialog.dispose();
    }

    /**
     * Validate files with specified settings
     */
    private void validateAllFiles() {
        for (File loadedFile : this.loadedFiles) {
            try {
                StringBuilder strBuilder = new StringBuilder();
                strBuilder.append("<p>Validation result of ");
                strBuilder.append(loadedFile.getName());
                strBuilder.append(":&gt; 0");

                List<ValidationResult> results = this.loadDataSettings.validateDataWithSettings(loadedFile);

                List<ValidationResult> infos = new LinkedList<>();
                List<ValidationResult> warnings = new LinkedList<>();
                List<ValidationResult> errors = new LinkedList<>();
                for (ValidationResult result : results) {
                    switch (result.getCode()) {
                        case INFO:
                            infos.add(result);
                            break;
                        case WARNING:
                            warnings.add(result);
                            break;
                        default:
                            errors.add(result);
                    }
                }

                // Show some file info
                if (!infos.isEmpty()) {
                    strBuilder.append("<p><b>File info:</b><br />");
                    infos.forEach(e -> {
                        strBuilder.append(e.getMessage());
                        strBuilder.append("<br />");
                    });
                    strBuilder.append("&gt; 0");
                }

                // Show warning messages
                int validationWarnErrMsgThreshold = 10;
                if (!warnings.isEmpty()) {
                    int warnCount = warnings.size();

                    strBuilder.append("<p style=\"color: orange;\"><b>Warning (total ");
                    strBuilder.append(warnCount);

                    if (warnCount > validationWarnErrMsgThreshold) {
                        strBuilder.append(", showing the first ");
                        strBuilder.append(validationWarnErrMsgThreshold);
                        strBuilder.append("): </b><br />");

                        warnings.subList(0, validationWarnErrMsgThreshold).forEach(e -> {
                            strBuilder.append(e.getMessage());
                            strBuilder.append("<br />");
                        });
                    } else {
                        strBuilder.append("): </b><br />");

                        warnings.forEach(e -> {
                            strBuilder.append(e.getMessage());
                            strBuilder.append("<br />");
                        });
                    }

                    strBuilder.append("&gt; 0");
                }

                // Show errors if found
                if (!errors.isEmpty()) {
                    int errorCount = errors.size();

                    String errorCountString = (errorCount > 1) ? " errors" : " error";

                    strBuilder.append("<p style=\"color: red;\"><b>Validation failed!<br>Please fix the following ");

                    if (errorCount > validationWarnErrMsgThreshold) {
                        strBuilder.append(validationWarnErrMsgThreshold);
                        strBuilder.append(errorCountString);
                        strBuilder.append(" (total ");
                        strBuilder.append(errorCount);
                        strBuilder.append(") and validate again:</b><br />");

                        errors.subList(0, validationWarnErrMsgThreshold).forEach(e -> {
                            // Remember to excape the html tags if the data file contains any
                            strBuilder.append(escapeHtml4(e.getMessage()));
                            strBuilder.append("<br />");
                        });
                    } else {
                        strBuilder.append(errorCount);
                        strBuilder.append(errorCountString);
                        strBuilder.append(" and validate again:</b><br />");

                        errors.forEach(e -> {
                            // Remember to excape the html tags if the data file contains any
                            strBuilder.append(escapeHtml4(e.getMessage()));
                            strBuilder.append("<br />");
                        });
                    }

                    strBuilder.append("&gt; 0");

                    // Also add the file name to failed list
                    // this determines if to show the Load button
                    this.failedFiles.add(loadedFile.getName());
                } else if (loadedFile.length() == 0) {
                    // We don't allow users to load empty file
                    strBuilder.append("<p style=\"color: red;\"><b>This is an empty data file!</b>&gt; 0");
                    // Also add the file name to failed list
                    // this determines if to show the Load button
                    this.failedFiles.add(loadedFile.getName());
                } else {
                    strBuilder.append("<p style=\"color: green;\"><b>Validation passed with no error!</b>&gt; 0");
                }

                this.validationResults.add(strBuilder.toString());
            } catch (IOException ex) {
                Logger.getLogger(LoadDataDialog.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        showValidationResults();
    }

    private void showValidationResults() {
        // Create the validation file list with marker prefix
        // so users know if this file passed the validation or not
        // just by looking at the file name prefix - Zhou
        for (File loadedFile : this.loadedFiles) {
            // Default to the check mark
            String unicodePrefix = "\u2713";
            if (this.failedFiles.contains(loadedFile.getName())) {
                // Cross mark
                unicodePrefix = "\u2717";
            }
            // Add the unicode marker
            this.validatedFileListModel.addElement("[" + unicodePrefix + "] " + loadedFile.getName());
        }

        // Set the default selected file
        // that's why we don't set the default selection when creating the JList
        this.validationFileList.setSelectedIndex(0);

        // Display validation result of the first file by default
        setValidationResult(this.validationResults.get(0), this.validationResultTextPane);
    }

    /**
     * Add all files to model once all can be loaded successfully
     */
    private void loadAllFiles() throws IOException {
        // Try to load each file and store the file name for failed loading
        for (int i = 0; i < this.loadedFiles.size(); i++) {
            DataModel dataModel = this.loadDataSettings.loadDataWithSettings(this.loadedFiles.get(i));

            // Add to dataModelList for further use
            if (dataModel != null) {
                // Must setName() here, file names will be used by the spreadsheet - Zhou
                dataModel.setName(this.loadedFiles.get(i).getName());
                this.dataModelList.add(dataModel);
                System.out.println("File index = " + i + " has been loaded successfully");
            }
        }
    }

    /**
     * This is called by LoadDataAction.java
     *
     * @return a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public DataModelList getDataModels() {
        return this.dataModelList;
    }

    /**
     * Set the validation result content
     */
    private void setValidationResult(String output, JTextPane textPane) {
        // Wrap the output in html
        textPane.setText("<html><body style=\"font-family: Monospaced; font-size: 10px; white-space:nowrap; \"" + output + "</body></html>");
        // Scroll back to top left
        textPane.setCaretPosition(0);
    }

    /**
     * Set the file preview content
     */
    private void setPreview(File file, JTextArea textArea) {
        try {
            textArea.setText("");
            DataPreviewer dataPreviewer = new BasicDataPreviewer(file.toPath());
            List<String> linePreviews = dataPreviewer.getPreviews(this.previewFromLine, this.previewToLine, this.previewNumOfCharactersPerLine);
            for (String line : linePreviews) {
                textArea.append(line + "\n");
            }
            textArea.setCaretPosition(0);
        } catch (IOException ex) {
            Logger.getLogger(LoadDataDialog.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
