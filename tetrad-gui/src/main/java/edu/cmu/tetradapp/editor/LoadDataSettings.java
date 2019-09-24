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
import edu.cmu.tetrad.util.DataConvertUtils;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DataColumns;
import edu.pitt.dbmi.data.reader.DataReader;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.covariance.CovarianceData;
import edu.pitt.dbmi.data.reader.covariance.CovarianceDataReader;
import edu.pitt.dbmi.data.reader.covariance.LowerCovarianceDataFileReader;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.metadata.MetadataFileReader;
import edu.pitt.dbmi.data.reader.metadata.MetadataReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import edu.pitt.dbmi.data.reader.util.TextFileUtils;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import edu.pitt.dbmi.data.reader.validation.covariance.CovarianceValidation;
import edu.pitt.dbmi.data.reader.validation.covariance.LowerCovarianceDataFileValidation;
import edu.pitt.dbmi.data.reader.validation.tabular.TabularColumnFileValidation;
import edu.pitt.dbmi.data.reader.validation.tabular.TabularColumnValidation;
import edu.pitt.dbmi.data.reader.validation.tabular.TabularDataFileValidation;
import edu.pitt.dbmi.data.reader.validation.tabular.TabularDataValidation;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
public final class LoadDataSettings extends JPanel {

    private static final long serialVersionUID = -7597768949622586036L;

    private final List<File> files;

    private File metadataFile;
    private Metadata metadata;

    private JRadioButton firstRowVarNamesYesRadioButton;
    private JRadioButton firstRowVarNamesNoRadioButton;

    private JRadioButton tabularRadioButton;
    private JRadioButton covarianceRadioButton;

    private JRadioButton contRadioButton;
    private JRadioButton discRadioButton;
    private JRadioButton mixedRadioButton;
    private IntTextField maxNumOfDiscCategoriesField;

    private JRadioButton commentDoubleSlashRadioButton;
    private JRadioButton commentPondRadioButton;
    private JRadioButton commentOtherRadioButton;
    private StringTextField commentStringField;

    private JRadioButton whitespaceDelimiterRadioButton;
    private JRadioButton singleCharDelimiterRadioButton;
    private JComboBox singleCharDelimiterComboBox;

    private JRadioButton noneQuoteRadioButton;
    private JRadioButton doubleQuoteRadioButton;
    private JRadioButton singleQuoteRadioButton;

    private JButton metadataFileButton;

    private JRadioButton idNoneRadioButton;
    private JRadioButton idUnlabeledFirstColRadioButton;
    private JRadioButton idLabeledColRadioButton;
    private StringTextField idStringField;

    private final Color separatorColor;

    private JRadioButton missingValueStarRadioButton;
    private JRadioButton missingValueQuestionRadioButton;
    private JRadioButton missingValueOtherRadioButton;
    private StringTextField missingStringField;

    private final Dimension labelSize;

    //================================CONSTRUCTOR=======================//
    public LoadDataSettings(List<File> files) {
        this.files = files;

        this.metadataFile = null;
        this.metadata = null;

        // All labels should share the save size - Zhou
        this.labelSize = new Dimension(200, 30);

        this.separatorColor = new Color(221, 221, 221);
    }

    // Step 1 items
    public final Box basicSettings() throws IOException {
        // Data loading params layout
        Box basicSettingsBox = Box.createVerticalBox();

        // Variable names in first row of data?
        Box firstRowVarNamesBox = Box.createHorizontalBox();

        // Yes/No buttons
        firstRowVarNamesYesRadioButton = new JRadioButton("Yes");
        firstRowVarNamesNoRadioButton = new JRadioButton("No");

        // Button group
        ButtonGroup firstRowVarNamesBtnGrp = new ButtonGroup();
        firstRowVarNamesBtnGrp.add(firstRowVarNamesYesRadioButton);
        firstRowVarNamesBtnGrp.add(firstRowVarNamesNoRadioButton);

        // Make Yes button selected by default
        firstRowVarNamesYesRadioButton.setSelected(true);

        // Event listener
        firstRowVarNamesYesRadioButton.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();
            if (button.isSelected()) {
                // Enable metadata file upload
                metadataFileButton.setEnabled(true);

                // Enable specifying column labeled option
                if (!idLabeledColRadioButton.isEnabled()) {
                    idLabeledColRadioButton.setEnabled(true);
                }

                if (!idStringField.isEnabled()) {
                    idStringField.setEnabled(true);
                }
            }
        });

        // When there's no header, disable the metadata button and column exculsions
        firstRowVarNamesNoRadioButton.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();
            if (button.isSelected()) {
                // No need to use metadata file when no header
                metadataFileButton.setEnabled(false);

                // Disable the "Column labeled" option of ignoring column
                idLabeledColRadioButton.setEnabled(false);
                idStringField.setEnabled(false);
            }
        });

        // Add label into this label box to size
        Box firstRowVarNamesLabelBox = Box.createHorizontalBox();
        firstRowVarNamesLabelBox.setPreferredSize(labelSize);
        firstRowVarNamesLabelBox.add(new JLabel("First row variable names:"));
        // Add info icon next to label to show tooltip on mouseover
        JLabel firstRowVarNamesLabelInfoIcon = new JLabel(new ImageIcon(ImageUtils.getImage(this, "information_small_white.png")));
        // Add tooltip on mouseover the info icon
        firstRowVarNamesLabelInfoIcon.setToolTipText("Whether the column variable names are presented in the first row of data");
        firstRowVarNamesLabelBox.add(firstRowVarNamesLabelInfoIcon);

        // Option 1
        Box firstRowVarNamesOption1Box = Box.createHorizontalBox();
        firstRowVarNamesOption1Box.setPreferredSize(new Dimension(160, 30));
        firstRowVarNamesOption1Box.add(firstRowVarNamesYesRadioButton);

        // Option 2
        Box firstRowVarNamesOption2Box = Box.createHorizontalBox();
        firstRowVarNamesOption2Box.setPreferredSize(new Dimension(160, 30));
        firstRowVarNamesOption2Box.add(firstRowVarNamesNoRadioButton);

        // Add to firstRowVarNamesBox
        firstRowVarNamesBox.add(firstRowVarNamesLabelBox);
        firstRowVarNamesBox.add(Box.createRigidArea(new Dimension(10, 1)));
        firstRowVarNamesBox.add(firstRowVarNamesOption1Box);
        firstRowVarNamesBox.add(firstRowVarNamesOption2Box);
        firstRowVarNamesBox.add(Box.createHorizontalGlue());

        basicSettingsBox.add(firstRowVarNamesBox);

        // File type: Tabular/Covariance
        Box fileTypeBox = Box.createHorizontalBox();

        tabularRadioButton = new JRadioButton("Tabular data");
        covarianceRadioButton = new JRadioButton("Covariance data (lower triangle)");

        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup fileTypeBtnGrp = new ButtonGroup();
        fileTypeBtnGrp.add(tabularRadioButton);
        fileTypeBtnGrp.add(covarianceRadioButton);

        // Tabular data is selected by default
        tabularRadioButton.setSelected(true);

        // Event listener
        tabularRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                // Just enable disabled buttons, do not change the previous selections - Zhou
                if (button.isSelected()) {
                    // Enable metadata file upload when there's also column header
                    if (firstRowVarNamesYesRadioButton.isSelected()) {
                        metadataFileButton.setEnabled(true);
                    }

                    // Enable the discrete/mixed radio button if it's disabled by clicking covariance data
                    if (!discRadioButton.isEnabled()) {
                        discRadioButton.setEnabled(true);
                    }

                    if (!mixedRadioButton.isEnabled()) {
                        mixedRadioButton.setEnabled(true);
                    }

                    // Enable variable names in first row
                    if (!firstRowVarNamesYesRadioButton.isEnabled()) {
                        firstRowVarNamesYesRadioButton.setEnabled(true);
                    }

                    if (!firstRowVarNamesNoRadioButton.isEnabled()) {
                        firstRowVarNamesNoRadioButton.setEnabled(true);
                    }

                    // Enable case Id options
                    if (!idUnlabeledFirstColRadioButton.isEnabled()) {
                        idUnlabeledFirstColRadioButton.setEnabled(true);
                    }

                    if (!idLabeledColRadioButton.isEnabled()) {
                        idLabeledColRadioButton.setEnabled(true);
                    }

                    if (!idStringField.isEnabled()) {
                        idStringField.setEnabled(true);
                    }
                }
            }
        });

        // Event listener
        covarianceRadioButton.addActionListener((ActionEvent actionEvent) -> {
            JRadioButton button = (JRadioButton) actionEvent.getSource();
            if (button.isSelected()) {
                // No need metadata file
                metadataFileButton.setEnabled(false);

                // When Covariance data is selected, data type can only be Continuous,
                contRadioButton.setSelected(true);

                //will disallow the users to choose Discrete and mixed data
                discRadioButton.setEnabled(false);
                mixedRadioButton.setEnabled(false);

                // Both Yes and No of Variable names in first row need to be disabled
                // Because the first row should be number of cases
                firstRowVarNamesYesRadioButton.setEnabled(false);
                firstRowVarNamesNoRadioButton.setEnabled(false);

                // select None for Case IDs, disable other options,
                // since no Case column should be in covariance data
                idNoneRadioButton.setSelected(true);
                idUnlabeledFirstColRadioButton.setEnabled(false);
                idLabeledColRadioButton.setEnabled(false);
                idStringField.setEnabled(false);
            }
        });

        // Add label into this label box to size
        Box fileTypeLabelBox = Box.createHorizontalBox();
        fileTypeLabelBox.setPreferredSize(labelSize);
        fileTypeLabelBox.add(new JLabel("Data file type:"));

        // Option 1
        Box fileTypeOption1Box = Box.createHorizontalBox();
        fileTypeOption1Box.setPreferredSize(new Dimension(160, 30));
        fileTypeOption1Box.add(tabularRadioButton);

        // Option 2
        Box fileTypeOption2Box = Box.createHorizontalBox();
        // Make this longer since the text is long - Zhou
        fileTypeOption2Box.setPreferredSize(new Dimension(300, 30));
        fileTypeOption2Box.add(covarianceRadioButton);

        // Add to file type box
        fileTypeBox.add(fileTypeLabelBox);
        fileTypeBox.add(Box.createRigidArea(new Dimension(10, 1)));
        fileTypeBox.add(fileTypeOption1Box);
        fileTypeBox.add(fileTypeOption2Box);
        fileTypeBox.add(Box.createHorizontalGlue());

        // Add to format container
        basicSettingsBox.add(fileTypeBox);

        // Add seperator line
        JSeparator separator1 = new JSeparator(SwingConstants.HORIZONTAL);
        separator1.setForeground(separatorColor);
        basicSettingsBox.add(separator1);

        // Metadata to interventional dataset
        Box metadataFileBox = Box.createHorizontalBox();

        // Add label into this label box to size
        Box metadataFileLabelBox = Box.createHorizontalBox();
        metadataFileLabelBox.setPreferredSize(labelSize);
        metadataFileLabelBox.add(new JLabel("Metadata JSON file:"));
        // Add info icon next to label to show tooltip on mouseover
        JLabel metadataFileLabelInfoIcon = new JLabel(new ImageIcon(ImageUtils.getImage(this, "information_small_white.png")));
        // Add tooltip on mouseover the info icon
        metadataFileLabelInfoIcon.setToolTipText("Metadata file is REQUIRED for observational and interventional data");
        metadataFileLabelBox.add(metadataFileLabelInfoIcon);

        // Metadata file load button
        metadataFileButton = new JButton("Load...");

        JLabel selectedMetadataFileName = new JLabel("No metadata file slected");

        // Add file button listener
        metadataFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Show file chooser
                JFileChooser fileChooser = new JFileChooser();
                String sessionSaveLocation = Preferences.userRoot().get("fileSaveLocation", "");
                fileChooser.setCurrentDirectory(new File(sessionSaveLocation));
                fileChooser.setFileFilter(new FileNameExtensionFilter("*.json", "json"));
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                // Only allow to add one file at a time
                fileChooser.setMultiSelectionEnabled(true);
                // Customize dialog title bar text
                fileChooser.setDialogTitle("Load metadata JSON file");
                // The second argument sets both the title for the dialog window and the label for the approve button
                int _ret = fileChooser.showDialog(SwingUtilities.getWindowAncestor(metadataFileButton), "Choose");

                if (_ret == JFileChooser.CANCEL_OPTION) {
                    return;
                }

                // Now we have the interventional metadata file
                metadataFile = fileChooser.getSelectedFile();

                // Show the selected file name
                selectedMetadataFileName.setText(metadataFile.getName());
            }
        });

        // File choose button box
        Box metadataFileButtonBox = Box.createHorizontalBox();
        metadataFileButtonBox.setPreferredSize(new Dimension(680, 30));
        metadataFileButtonBox.add(metadataFileButton);
        metadataFileButtonBox.add(Box.createHorizontalStrut(10));
        metadataFileButtonBox.add(selectedMetadataFileName);

        // Put together
        metadataFileBox.add(metadataFileLabelBox);
        metadataFileBox.add(Box.createRigidArea(new Dimension(10, 1)));
        metadataFileBox.add(metadataFileButtonBox);
        metadataFileBox.add(Box.createHorizontalGlue());

        basicSettingsBox.add(metadataFileBox);

        // Add seperator line
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(separatorColor);
        basicSettingsBox.add(separator);

        // Vertical gap
        //basicSettingsBox.add(Box.createVerticalStrut(5));
        // Data type - moved from the old Fast tab - Zhou
        Box dataTypeBox = Box.createHorizontalBox();
        // Data type: continuous, discrete, or mixed
        contRadioButton = new JRadioButton("Continuous");
        discRadioButton = new JRadioButton("Discrete");
        mixedRadioButton = new JRadioButton("Mixed");

        ButtonGroup dataTypeBtnGrp = new ButtonGroup();
        dataTypeBtnGrp.add(contRadioButton);
        dataTypeBtnGrp.add(discRadioButton);
        dataTypeBtnGrp.add(mixedRadioButton);

        // Continuous radion button is selected by default
        contRadioButton.setSelected(true);

        // Add label into this label box to size
        Box dataTypeLabelBox = Box.createHorizontalBox();
        dataTypeLabelBox.setPreferredSize(labelSize);
        dataTypeLabelBox.add(new JLabel("Data type:"));

        // Option 1
        Box dataTypeOption1Box = Box.createHorizontalBox();
        dataTypeOption1Box.setPreferredSize(new Dimension(160, 30));
        dataTypeOption1Box.add(contRadioButton);

        // Option 2
        Box dataTypeOption2Box = Box.createHorizontalBox();
        dataTypeOption2Box.setPreferredSize(new Dimension(160, 30));
        dataTypeOption2Box.add(discRadioButton);

        // Option 3
        Box dataTypeOption3Box = Box.createHorizontalBox();
        dataTypeOption3Box.setPreferredSize(new Dimension(320, 30));
        dataTypeOption3Box.add(mixedRadioButton);

        // Threshold label
        JLabel maxDiscCatLabel = new JLabel(", max discrete categories: ");

        // Add info icon next to label to show tooltip on mouseover
        JLabel maxDiscCatLabelInfoIcon = new JLabel(new ImageIcon(ImageUtils.getImage(this, "information_small_white.png")));
        // Add tooltip on mouseover the info icon
        maxDiscCatLabelInfoIcon.setToolTipText("Integral columns with up to N (specify here) distinct values are discrete.");

        // Max number of discrete categories
        maxNumOfDiscCategoriesField = new IntTextField(0, 3);
        // 0 by default
        maxNumOfDiscCategoriesField.setValue(0);

        maxNumOfDiscCategoriesField.setFilter((int value, int oldValue) -> {
            if (value >= 0) {
                return value;
            } else {
                return oldValue;
            }
        });

        // Event listener
        maxNumOfDiscCategoriesField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                // Select the "Mixed" radio button when users click the input field
                if (!mixedRadioButton.isSelected()) {
                    mixedRadioButton.setSelected(true);
                }
            }
        });

        dataTypeOption3Box.add(maxDiscCatLabel);
        dataTypeOption3Box.add(maxDiscCatLabelInfoIcon);
        dataTypeOption3Box.add(maxNumOfDiscCategoriesField);

        dataTypeBox.add(dataTypeLabelBox);
        dataTypeBox.add(Box.createRigidArea(new Dimension(10, 1)));
        dataTypeBox.add(dataTypeOption1Box);
        dataTypeBox.add(dataTypeOption2Box);
        dataTypeBox.add(dataTypeOption3Box);
        dataTypeBox.add(Box.createHorizontalGlue());

        basicSettingsBox.add(dataTypeBox);

        // Add seperator line
        JSeparator separator2 = new JSeparator(SwingConstants.HORIZONTAL);
        separator2.setForeground(separatorColor);
        basicSettingsBox.add(separator2);

        //basicSettingsBox.add(Box.createVerticalStrut(5));
        // Value Delimiter box
        Box valueDelimiterBox = Box.createHorizontalBox();

        // Value Delimiter
        whitespaceDelimiterRadioButton = new JRadioButton("Whitespace");
        singleCharDelimiterRadioButton = new JRadioButton("Single character: ");

        // Dropdown options for commo box
        String[] singleCharDelimiterOptions = {"Comma", "Colon", "Tab", "Space", "Semicolon", "Pipe"};

        //Create the combo box
        singleCharDelimiterComboBox = new JComboBox(singleCharDelimiterOptions);
        singleCharDelimiterComboBox.setMaximumSize(new Dimension(90, 30));
        // select first item by default, index starts at 0
        singleCharDelimiterComboBox.setSelectedIndex(0);

        ButtonGroup delimiterBtnGrp = new ButtonGroup();
        delimiterBtnGrp.add(whitespaceDelimiterRadioButton);
        delimiterBtnGrp.add(singleCharDelimiterRadioButton);

        // Defaults to whitespace if the inferred delimiter is not in Combo box
        // We can't infer whitespcace.
        // Otherwise, select the inferred delimiter from ComboBox and
        // check the singleCharDelimiterRadioButton
        // Only infer delimiter based on the first file - Zhou
        char inferredDelimiter = getInferredDelimiter(files.get(0));

        switch (inferredDelimiter) {
            case ',':
                singleCharDelimiterRadioButton.setSelected(true);
                singleCharDelimiterComboBox.setSelectedItem("Comma");
                break;
            case '\t':
                singleCharDelimiterRadioButton.setSelected(true);
                singleCharDelimiterComboBox.setSelectedItem("Tab");
                break;
            case ' ':
                // Whitespace covers space, so we use whitespace instead of space here
                whitespaceDelimiterRadioButton.setSelected(true);
                break;
            case ':':
                singleCharDelimiterRadioButton.setSelected(true);
                singleCharDelimiterComboBox.setSelectedItem("Colon");
                break;
            case ';':
                singleCharDelimiterRadioButton.setSelected(true);
                singleCharDelimiterComboBox.setSelectedItem("Semicolon");
                break;
            case '|':
                singleCharDelimiterRadioButton.setSelected(true);
                singleCharDelimiterComboBox.setSelectedItem("Pipe");
                break;
            default:
                // Just use whitespace as default if can't infer
                whitespaceDelimiterRadioButton.setSelected(true);
                break;
        }

        // Event listener
        // ComboBox is actually a container
        singleCharDelimiterComboBox.addActionListener((ActionEvent actionEvent) -> {
            // Select the "Single character:" radio button when users click the combo box
            if (!singleCharDelimiterRadioButton.isSelected()) {
                singleCharDelimiterRadioButton.setSelected(true);
            }
        });

        // Add label into this label box to size
        Box valueDelimiterLabelBox = Box.createHorizontalBox();
        valueDelimiterLabelBox.setPreferredSize(labelSize);
        valueDelimiterLabelBox.add(new JLabel("Value delimiter:"));
        // Add info icon next to label to show tooltip on mouseover
        JLabel valueDelimiterLabelInfoIcon = new JLabel(new ImageIcon(ImageUtils.getImage(this, "information_small_white.png")));
        // Add tooltip on mouseover the info icon
        valueDelimiterLabelInfoIcon.setToolTipText("Delimiter used to seperate the variable names and data values");
        valueDelimiterLabelBox.add(valueDelimiterLabelInfoIcon);

        // Option 1
        Box valueDelimiterOption1Box = Box.createHorizontalBox();
        valueDelimiterOption1Box.setPreferredSize(new Dimension(160, 30));
        valueDelimiterOption1Box.add(whitespaceDelimiterRadioButton);

        // Option 2
        Box valueDelimiterOption2Box = Box.createHorizontalBox();
        // Make this box wider so we can see the combo box - Zhou
        valueDelimiterOption2Box.setPreferredSize(new Dimension(300, 30));
        valueDelimiterOption2Box.add(singleCharDelimiterRadioButton);
        valueDelimiterOption2Box.add(singleCharDelimiterComboBox);

        valueDelimiterBox.add(valueDelimiterLabelBox);
        valueDelimiterBox.add(Box.createRigidArea(new Dimension(10, 1)));
        valueDelimiterBox.add(valueDelimiterOption1Box);
        valueDelimiterBox.add(valueDelimiterOption2Box);
        valueDelimiterBox.add(Box.createHorizontalGlue());

        basicSettingsBox.add(valueDelimiterBox);

        // Add seperator line
        JSeparator separator3 = new JSeparator(SwingConstants.HORIZONTAL);
        separator3.setForeground(separatorColor);
        basicSettingsBox.add(separator3);

        // Use a titled border with 5 px inside padding - Zhou
        String borderTitle = "Basic Settings (apply to all files)";
        basicSettingsBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(borderTitle), new EmptyBorder(5, 5, 5, 5)));

        return basicSettingsBox;
    }

    // Step 2 items
    public final Box advancedSettings() {
        // Data loading params layout
        Box advancedSettingsBox = Box.createVerticalBox();

        // Columns to exclude/ignore
        Box caseIdProvidedBox = Box.createHorizontalBox();

        // ID radio buttons
        idNoneRadioButton = new JRadioButton("None");
        idUnlabeledFirstColRadioButton = new JRadioButton("First column");
        idLabeledColRadioButton = new JRadioButton("Column labeled: ");
        idStringField = new StringTextField("", 6);

        ButtonGroup caseIdBtnGrp = new ButtonGroup();
        caseIdBtnGrp.add(idNoneRadioButton);
        caseIdBtnGrp.add(idUnlabeledFirstColRadioButton);
        caseIdBtnGrp.add(idLabeledColRadioButton);

        // Defaults to none
        idNoneRadioButton.setSelected(true);

        // Event listener
        idStringField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                // Select the "Column labeled:" radio button when users click the text field
                if (idStringField.isEnabled() && !idLabeledColRadioButton.isSelected()) {
                    idLabeledColRadioButton.setSelected(true);
                }
            }
        });

        // Add label into this label box to size
        Box caseIdProvidedLabelBox = Box.createHorizontalBox();
        caseIdProvidedLabelBox.setPreferredSize(labelSize);
        caseIdProvidedLabelBox.add(new JLabel("Case column to exclude/ignore:"));

        // Option 1
        Box caseIdProvidedOption1Box = Box.createHorizontalBox();
        caseIdProvidedOption1Box.setPreferredSize(new Dimension(160, 30));
        caseIdProvidedOption1Box.add(idNoneRadioButton);

        // Option 2
        Box caseIdProvidedOption2Box = Box.createHorizontalBox();
        caseIdProvidedOption2Box.setPreferredSize(new Dimension(160, 30));
        caseIdProvidedOption2Box.add(idUnlabeledFirstColRadioButton);

        // Option 3
        Box caseIdProvidedOption3Box = Box.createHorizontalBox();
        // Make this box a little longer because we don't want the text field too small
        caseIdProvidedOption3Box.setPreferredSize(new Dimension(300, 30));
        caseIdProvidedOption3Box.add(idLabeledColRadioButton);
        caseIdProvidedOption3Box.add(idStringField);

        caseIdProvidedBox.add(caseIdProvidedLabelBox);
        caseIdProvidedBox.add(Box.createRigidArea(new Dimension(10, 1)));
        caseIdProvidedBox.add(caseIdProvidedOption1Box);
        caseIdProvidedBox.add(caseIdProvidedOption2Box);
        caseIdProvidedBox.add(caseIdProvidedOption3Box);
        caseIdProvidedBox.add(Box.createHorizontalGlue());

        advancedSettingsBox.add(caseIdProvidedBox);

        // Add seperator line
        JSeparator separator1 = new JSeparator(SwingConstants.HORIZONTAL);
        separator1.setForeground(separatorColor);
        advancedSettingsBox.add(separator1);

        //advancedSettingsBox.add(Box.createVerticalStrut(5));
        // Comment Marker
        Box commentMarkerBox = Box.createHorizontalBox();

        commentDoubleSlashRadioButton = new JRadioButton("//");
        commentPondRadioButton = new JRadioButton("#");
        commentOtherRadioButton = new JRadioButton("Other: ");

        ButtonGroup commentMarkerBtnGrp = new ButtonGroup();
        commentMarkerBtnGrp.add(commentDoubleSlashRadioButton);
        commentMarkerBtnGrp.add(commentPondRadioButton);
        commentMarkerBtnGrp.add(commentOtherRadioButton);

        // Comment string field
        commentStringField = new StringTextField("", 6);

        // Select double slash by default
        commentDoubleSlashRadioButton.setSelected(true);

        // Event listener
        commentStringField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                // Select the "Other:" radio button when users click the text field
                if (!commentOtherRadioButton.isSelected()) {
                    commentOtherRadioButton.setSelected(true);
                }
            }
        });

        // Add label into this label box to size
        Box commentMarkerLabelBox = Box.createHorizontalBox();
        commentMarkerLabelBox.setPreferredSize(labelSize);
        commentMarkerLabelBox.add(new JLabel("Comment marker:"));
        // Add info icon next to label to show tooltip on mouseover
        JLabel commentMarkerLabelInfoIcon = new JLabel(new ImageIcon(ImageUtils.getImage(this, "information_small_white.png")));
        // Add tooltip on mouseover the info icon
        commentMarkerLabelInfoIcon.setToolTipText("Validation and data loading will ingnore the rows started with the comment marker.");
        commentMarkerLabelBox.add(commentMarkerLabelInfoIcon);

        // Option 1
        Box commentMarkerOption1Box = Box.createHorizontalBox();
        commentMarkerOption1Box.setPreferredSize(new Dimension(160, 30));
        commentMarkerOption1Box.add(commentDoubleSlashRadioButton);

        // Option 2
        Box commentMarkerOption2Box = Box.createHorizontalBox();
        commentMarkerOption2Box.setPreferredSize(new Dimension(160, 30));
        commentMarkerOption2Box.add(commentPondRadioButton);

        // Option 3
        Box commentMarkerOption3Box = Box.createHorizontalBox();
        commentMarkerOption3Box.setPreferredSize(new Dimension(260, 30));
        commentMarkerOption3Box.add(commentOtherRadioButton);
        commentMarkerOption3Box.add(commentStringField);

        commentMarkerBox.add(commentMarkerLabelBox);
        commentMarkerBox.add(Box.createRigidArea(new Dimension(10, 1)));
        commentMarkerBox.add(commentMarkerOption1Box);
        commentMarkerBox.add(commentMarkerOption2Box);
        commentMarkerBox.add(commentMarkerOption3Box);
        commentMarkerBox.add(Box.createHorizontalGlue());

        advancedSettingsBox.add(commentMarkerBox);

        // Add seperator line
        JSeparator separator2 = new JSeparator(SwingConstants.HORIZONTAL);
        separator2.setForeground(separatorColor);
        advancedSettingsBox.add(separator2);

        //advancedSettingsBox.add(Box.createVerticalStrut(5));
        // Quote Character
        Box quoteCharBox = Box.createHorizontalBox();

        noneQuoteRadioButton = new JRadioButton("None");
        doubleQuoteRadioButton = new JRadioButton("\"");
        singleQuoteRadioButton = new JRadioButton("'");

        ButtonGroup quoteCharBtnGrp = new ButtonGroup();
        quoteCharBtnGrp.add(noneQuoteRadioButton);
        quoteCharBtnGrp.add(doubleQuoteRadioButton);
        quoteCharBtnGrp.add(singleQuoteRadioButton);

        // Select None by default
        noneQuoteRadioButton.setSelected(true);

        // Add label into this label box to size
        Box quoteCharLabelBox = Box.createHorizontalBox();
        quoteCharLabelBox.setPreferredSize(labelSize);
        quoteCharLabelBox.add(new JLabel("Quote character:"));
        // Add info icon next to label to show tooltip on mouseover
        JLabel quoteCharLabelInfoIcon = new JLabel(new ImageIcon(ImageUtils.getImage(this, "information_small_white.png")));
        // Add tooltip on mouseover the info icon
        quoteCharLabelInfoIcon.setToolTipText("If variable names or/and actual data are quoted, choose either single quote or double quotes. ");
        quoteCharLabelBox.add(quoteCharLabelInfoIcon);

        // Option 1
        Box quoteCharOption1Box = Box.createHorizontalBox();
        quoteCharOption1Box.setPreferredSize(new Dimension(160, 30));
        quoteCharOption1Box.add(noneQuoteRadioButton);

        // Option 2
        Box quoteCharOption2Box = Box.createHorizontalBox();
        quoteCharOption2Box.setPreferredSize(new Dimension(160, 30));
        quoteCharOption2Box.add(doubleQuoteRadioButton);

        // Option 3
        Box quoteCharOption3Box = Box.createHorizontalBox();
        quoteCharOption3Box.setPreferredSize(new Dimension(260, 30));
        quoteCharOption3Box.add(singleQuoteRadioButton);

        quoteCharBox.add(quoteCharLabelBox);
        quoteCharBox.add(Box.createRigidArea(new Dimension(10, 1)));
        quoteCharBox.add(quoteCharOption1Box);
        quoteCharBox.add(quoteCharOption2Box);
        quoteCharBox.add(quoteCharOption3Box);
        quoteCharBox.add(Box.createHorizontalGlue());

        advancedSettingsBox.add(quoteCharBox);

        // Add seperator line
        JSeparator separator3 = new JSeparator(SwingConstants.HORIZONTAL);
        separator3.setForeground(separatorColor);
        advancedSettingsBox.add(separator3);

        //  Missing value marker
        Box missingDataMarkerBox = Box.createHorizontalBox();

        missingValueStarRadioButton = new JRadioButton("*");
        missingValueQuestionRadioButton = new JRadioButton("?");
        missingValueOtherRadioButton = new JRadioButton("Other: ");

        ButtonGroup missingDataMarkerBtnGrp = new ButtonGroup();
        missingDataMarkerBtnGrp.add(missingValueStarRadioButton);
        missingDataMarkerBtnGrp.add(missingValueQuestionRadioButton);
        missingDataMarkerBtnGrp.add(missingValueOtherRadioButton);

        // Missing string field: other
        missingStringField = new StringTextField("", 6);
        missingStringField.setText("");

        // * is selected as the default
        missingValueStarRadioButton.setSelected(true);

        // Option 1
        Box missingDataMarkerOption1Box = Box.createHorizontalBox();
        missingDataMarkerOption1Box.setPreferredSize(new Dimension(160, 30));
        missingDataMarkerOption1Box.add(missingValueStarRadioButton);

        // Option 2
        Box missingDataMarkerOption2Box = Box.createHorizontalBox();
        missingDataMarkerOption2Box.setPreferredSize(new Dimension(160, 30));
        missingDataMarkerOption2Box.add(missingValueQuestionRadioButton);

        // Option 3
        Box missingDataMarkerOption3Box = Box.createHorizontalBox();
        missingDataMarkerOption3Box.setPreferredSize(new Dimension(260, 30));
        missingDataMarkerOption3Box.add(missingValueOtherRadioButton);
        missingDataMarkerOption3Box.add(missingStringField);

        // Add label into this label box to size
        Box missingDataMarkerLabelBox = Box.createHorizontalBox();
        missingDataMarkerLabelBox.setPreferredSize(labelSize);
        missingDataMarkerLabelBox.add(new JLabel("Missing value marker:"));

        missingDataMarkerBox.add(missingDataMarkerLabelBox);
        missingDataMarkerBox.add(Box.createRigidArea(new Dimension(10, 1)));
        missingDataMarkerBox.add(missingDataMarkerOption1Box);
        missingDataMarkerBox.add(missingDataMarkerOption2Box);
        missingDataMarkerBox.add(missingDataMarkerOption3Box);
        missingDataMarkerBox.add(Box.createHorizontalGlue());

        advancedSettingsBox.add(missingDataMarkerBox);

        advancedSettingsBox.add(Box.createVerticalStrut(5));

        // Use a titled border with 5 px inside padding - Zhou
        String borderTitle = "Advanced Settings (apply to all files)";
        advancedSettingsBox.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(borderTitle), new EmptyBorder(5, 5, 5, 5)));

        return advancedSettingsBox;
    }

    /**
     * This works for both validation(column and data) and data reading(column
     * reader and data reader)
     *
     * @param datasetReader
     */
    private void setQuoteChar(DataReader dataReader) {
        if (doubleQuoteRadioButton.isSelected()) {
            dataReader.setQuoteCharacter('"');
        }

        if (singleQuoteRadioButton.isSelected()) {
            dataReader.setQuoteCharacter('\'');
        }
    }

    private String getCommentMarker() {
        if (commentDoubleSlashRadioButton.isSelected()) {
            return "//";
        } else if (commentPondRadioButton.isSelected()) {
            return "#";
        } else if (commentOtherRadioButton.isSelected()) {
            return commentStringField.getText();
        } else {
            throw new IllegalArgumentException("Unexpected Comment Marker selection.");
        }
    }

    /**
     * Determine the delimiter for a text data file.
     *
     * @param file
     * @return
     */
    private char getInferredDelimiter(File file) throws IOException {
        System.out.println("Infer demiliter for file: " + file.getName());

        // The number of lines to read to make the inference
        int n = 20;
        // The number of lines to skip at top of file before processing
        // Here we use 2 because covariance data has total number of cases at line 1,
        // and sometimes a commented line as well
        int skip = 2;
        String comment = "//";
        char quoteCharacter = '"';
        char[] delims = {'\t', ' ', ',', ':', ';', '|'};

        // https://rdrr.io/cran/reader/man/get.delim.html
        return TextFileUtils.inferDelimiter(file, n, skip, comment, quoteCharacter, delims);
    }

    /**
     * Get delimiter character
     *
     * @return
     */
    private Delimiter getDelimiterType() {
        if (whitespaceDelimiterRadioButton.isSelected()) {
            return Delimiter.WHITESPACE;
        } else if (singleCharDelimiterRadioButton.isSelected()) {
            String singleCharDelimiter = singleCharDelimiterComboBox.getSelectedItem().toString();

            switch (singleCharDelimiter) {
                case "Comma":
                    return Delimiter.COMMA;
                case "Space":
                    return Delimiter.SPACE;
                case "Tab":
                    return Delimiter.TAB;
                case "Colon":
                    return Delimiter.COLON;
                case "Semicolon":
                    return Delimiter.SEMICOLON;
                case "Pipe":
                    return Delimiter.PIPE;
                default:
                    throw new IllegalArgumentException("Unexpected Value delimiter selection.");
            }
        } else {
            throw new IllegalArgumentException("Unexpected Value delimiter selection.");
        }
    }

    private boolean isVarNamesFirstRow() {
        if (firstRowVarNamesYesRadioButton.isSelected()) {
            return true;
        } else if (firstRowVarNamesNoRadioButton.isSelected()) {
            return false;
        } else {
            throw new IllegalArgumentException("Unexpected Variable Names in First Row selection.");
        }
    }

    /**
     * To check if the label is specified while that radio button is selected
     *
     * @return
     */
    public boolean isColumnLabelSpecified() {
        if (idLabeledColRadioButton.isSelected()) {
            return !idStringField.getText().isEmpty();
        } else {
            return true;
        }
    }

    /**
     * To check if comment marker is supplied while Other radio button is
     * selected
     *
     * @return
     */
    public boolean isOtherCommentMarkerSpecified() {
        if (commentOtherRadioButton.isSelected()) {
            return !commentStringField.getText().isEmpty();
        } else {
            return true;
        }
    }

    private String getMissingDataMarker() {
        if (missingValueStarRadioButton.isSelected()) {
            return "*";
        } else if (missingValueQuestionRadioButton.isSelected()) {
            return "?";
        } else {
            return missingStringField.getText();
        }
    }

    private int getMaxNumOfDiscCategories() {
        return maxNumOfDiscCategoriesField.getValue();
    }

    /**
     * Genearate the column header when not provided
     *
     * @param file
     * @param delimiter
     * @return
     * @throws IOException
     */
    private DataColumn[] generateTabularColumns(File file, Delimiter delimiter) throws IOException {
        DataColumn[] dataColumns = null;

        String commentMarker = getCommentMarker();

        TabularColumnReader columnFileReader = new TabularColumnFileReader(file.toPath(), delimiter);

        columnFileReader.setCommentMarker(commentMarker);
        setQuoteChar(columnFileReader);

        // Set data type for each column
        // It really doesn't matter for mixed data
        boolean isDiscrete = false;
        if (contRadioButton.isSelected()) {
            isDiscrete = false;
        } else if (discRadioButton.isSelected()) {
            isDiscrete = true;
        }

        // Generate data columns with exclusions
        // Handle case ID column based on different selections
        if (idNoneRadioButton.isSelected()) {
            dataColumns = columnFileReader.generateColumns(new int[0], isDiscrete);
        } else if (idUnlabeledFirstColRadioButton.isSelected()) {
            // Exclude the first column
            dataColumns = columnFileReader.generateColumns(new int[]{1}, isDiscrete);
        }

        return dataColumns;
    }

    /**
     * Validate each file based on the specified settings
     *
     * @param file
     * @return
     */
    public List<ValidationResult> validateDataWithSettings(File file) throws IOException {
        Delimiter delimiter = getDelimiterType();
        boolean hasHeader = isVarNamesFirstRow();
        String commentMarker = getCommentMarker();
        String missingDataMarker = getMissingDataMarker();

        if (tabularRadioButton.isSelected()) {
            DataColumn[] dataColumns;

            List<ValidationResult> tabularColumnValidationResults = new LinkedList<>();

            // Generate the columns if not present and skip the column validation
            if (!hasHeader) {
                dataColumns = generateTabularColumns(file, delimiter);
            } else {
                // Step 1: validate the columns
                TabularColumnValidation tabularColumnValidation = new TabularColumnFileValidation(file.toPath(), delimiter);

                // Specify settings for column validation
                tabularColumnValidation.setCommentMarker(commentMarker);
                setQuoteChar(tabularColumnValidation);

                // Handle case ID column based on different selections
                if (idNoneRadioButton.isSelected()) {
                    // No column exclusion
                    tabularColumnValidationResults = tabularColumnValidation.validate();
                } else if (idUnlabeledFirstColRadioButton.isSelected()) {
                    // Exclude the first column
                    tabularColumnValidationResults = tabularColumnValidation.validate(new int[]{1});
                } else if (idLabeledColRadioButton.isSelected() && !idStringField.getText().isEmpty()) {
                    // Exclude the specified labled columns
                    tabularColumnValidationResults = tabularColumnValidation.validate(new HashSet<>(Arrays.asList(new String[]{idStringField.getText()})));
                }

                // Step 2: Read in columns for later use if nothing wrong with the columns validation
                dataColumns = readInTabularColumns(file);
            }

            List<ValidationResult> validationInfos = new LinkedList<>();
            List<ValidationResult> validationWarnings = new LinkedList<>();
            List<ValidationResult> validationErrors = new LinkedList<>();

            for (ValidationResult result : tabularColumnValidationResults) {
                switch (result.getCode()) {
                    case INFO:
                        validationInfos.add(result);
                        break;
                    case WARNING:
                        validationWarnings.add(result);
                        break;
                    default:
                        validationErrors.add(result);
                }
            }

            // Stop here and return the column validation results if there's any error
            if (validationErrors.size() > 0) {
                return tabularColumnValidationResults;
            } else {
                if (mixedRadioButton.isSelected()) {
                    TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);
                    dataReader.setCommentMarker(commentMarker);
                    dataReader.setMissingDataMarker(missingDataMarker);
                    setQuoteChar(dataReader);
                    dataReader.determineDiscreteDataColumns(dataColumns, getMaxNumOfDiscCategories(), hasHeader);
                }

                if (metadataFile != null) {
                    MetadataReader metadataReader = new MetadataFileReader(metadataFile.toPath());
                    metadata = metadataReader.read();
                    dataColumns = DataColumns.update(dataColumns, metadata);
                }

                // Step 3: Data validation
                // when we at this step, it means the column validation is all good without any errors
                TabularDataValidation tabularDataValidation = new TabularDataFileValidation(file.toPath(), delimiter);

                // Specify the setting again for data validation
                tabularDataValidation.setCommentMarker(commentMarker);
                setQuoteChar(tabularDataValidation);

                // Missing data marker setting for data validaiton only, not for column validation
                tabularDataValidation.setMissingDataMarker(missingDataMarker);

                List<ValidationResult> tabularDataValidationResults = tabularDataValidation.validate(dataColumns, hasHeader);

                // At this point, no need to use the column validation results at all
                return tabularDataValidationResults;
            }
        } else if (covarianceRadioButton.isSelected()) {
            CovarianceValidation covarianceValidation = new LowerCovarianceDataFileValidation(file.toPath(), delimiter);

            // Header in first row is required
            // Cpvariance never has missing value marker
            covarianceValidation.setCommentMarker(commentMarker);
            setQuoteChar(covarianceValidation);

            // No case ID on covarianced data
            return covarianceValidation.validate();
        } else {
            throw new UnsupportedOperationException("You can only choose either tabular data or covariance data!");
        }
    }

    private DataColumn[] readInTabularColumns(File file) throws IOException {
        DataColumn[] dataColumns = null;

        Delimiter delimiter = getDelimiterType();
        String commentMarker = getCommentMarker();

        TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);

        columnReader.setCommentMarker(commentMarker);
        setQuoteChar(columnReader);

        // Set data type for each column
        // It really doesn't matter for mixed data
        boolean isDiscrete = false;
        if (contRadioButton.isSelected()) {
            isDiscrete = false;
        } else if (discRadioButton.isSelected()) {
            isDiscrete = true;
        }

        // Handle case ID column based on different selections
        if (idNoneRadioButton.isSelected()) {
            // No column exclusion
            dataColumns = columnReader.readInDataColumns(new int[0], isDiscrete);
        } else if (idUnlabeledFirstColRadioButton.isSelected()) {
            // Exclude the first column
            dataColumns = columnReader.readInDataColumns(new int[]{1}, isDiscrete);
        } else if (idLabeledColRadioButton.isSelected() && !idStringField.getText().isEmpty()) {
            // Exclude the specified labled columns
            dataColumns = columnReader.readInDataColumns(new HashSet<>(Arrays.asList(new String[]{idStringField.getText()})), isDiscrete);
        }

        return dataColumns;
    }

    /**
     * Kevin's data reader
     *
     * @param file
     * @return DataModel on success
     */
    public DataModel loadDataWithSettings(File file) throws IOException {
        DataModel dataModel = null;

        Delimiter delimiter = getDelimiterType();
        boolean hasHeader = isVarNamesFirstRow();
        String commentMarker = getCommentMarker();
        String missingDataMarker = getMissingDataMarker();

        DataColumn[] dataColumns;
        if (tabularRadioButton.isSelected()) {
            // Generate columns if no header present
            if (!hasHeader) {
                dataColumns = generateTabularColumns(file, delimiter);
                // When no header, no metadata
            } else {
                dataColumns = readInTabularColumns(file);
            }

            // Now read in the data rows
            TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

            // Need to specify commentMarker, .... again to the TabularDataFileReader
            dataReader.setCommentMarker(commentMarker);
            dataReader.setMissingDataMarker(missingDataMarker);
            setQuoteChar(dataReader);

            // When users select mixed data, we need to determine num of discrete categories before the metadata kicks in
            // It's possible that the users select mixed, but excluded either all continuous or discrete columns,
            // and as a result, the final data is either discrete or continuous instead of mixed - Zhou
            if (mixedRadioButton.isSelected()) {
                dataReader.determineDiscreteDataColumns(dataColumns, getMaxNumOfDiscCategories(), hasHeader);
            }

            // Read metadata file when provided and update the dataColumns
            if (metadataFile != null) {
                MetadataReader metadataReader = new MetadataFileReader(metadataFile.toPath());
                metadata = metadataReader.read();
                dataColumns = DataColumns.update(dataColumns, metadata);
            }

            // Now we read in the actual data with metadata object (if provided)
            Data data;
            if (metadata != null) {
                data = dataReader.read(dataColumns, hasHeader, metadata);
                // Box Data to DataModel to display in spreadsheet
                dataModel = DataConvertUtils.toDataModel(data, metadata);
            } else {
                data = dataReader.read(dataColumns, hasHeader);
                // Box Data to DataModel to display in spreadsheet
                dataModel = DataConvertUtils.toDataModel(data);
            }
        } else if (covarianceRadioButton.isSelected()) {
            // Covariance data can only be continuous
            CovarianceDataReader dataFileReader = new LowerCovarianceDataFileReader(file.toPath(), delimiter);

            dataFileReader.setCommentMarker(commentMarker);
            setQuoteChar(dataFileReader);

            CovarianceData covarianceData = dataFileReader.readInData();

            // Box Dataset to DataModel
            dataModel = DataConvertUtils.toDataModel(covarianceData);
        } else {
            throw new UnsupportedOperationException("Unsupported selection of File Type!");
        }

        return dataModel;
    }

}
