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
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
import edu.cmu.tetradapp.util.TextAreaOutputStream;
import java.awt.*;
import java.io.File;
import java.io.PrintStream;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class DataLoaderSettings extends JPanel {

    private File[] files;

    private transient DataModel[] dataModels;

    private JRadioButton tabularRadioButton;
    private JRadioButton covarianceRadioButton;

    private JRadioButton contRadioButton;
    private JRadioButton discRadioButton;
    private JRadioButton mixedRadioButton;

    private JRadioButton commentDoubleSlashRadioButton;
    private JRadioButton commentPondRadioButton;
    private JRadioButton commentOtherRadioButton;
    private StringTextField commentStringField;

    private JRadioButton commaDelimiterRadioButton;
    private JRadioButton tabDelimiterRadioButton;
    private JRadioButton spaceDelimiterRadioButton;

    private JRadioButton noneQuoteRadioButton;
    private JRadioButton doubleQuoteRadioButton;
    private JRadioButton singleQuoteRadioButton;

    private JRadioButton firstRowVarNamesYesRadioButton;
    private JRadioButton firstRowVarNamesNoRadioButton;

    private JRadioButton idNoneRadioButton;
    private JRadioButton idUnlabeledFirstColRadioButton;
    private JRadioButton idLabeledColRadioButton;
    private StringTextField idStringField;

    private JRadioButton missingValueBlankRadioButton;
    private JRadioButton missingValueStarRadioButton;
    private JRadioButton missingValueQuestionRadioButton;
    private JRadioButton missingValueOtherRadioButton;
    private StringTextField missingStringField;

    private IntTextField maxIntegralDiscreteIntField;
    private JLabel maxIntegralLabel1;
    private JLabel maxIntegralLabel2;

    private final int fileIndex = 0;

    //================================CONSTRUCTOR=======================//
    public DataLoaderSettings(final File... files) {
        this.files = files;

        if (files.length == 0) {
            throw new IllegalArgumentException("Must specify at least one file.");
        }

        this.dataModels = new DataModel[files.length];
    }

    // Step 1 items
    public final Box specifyFormat() {
        // Data loading params layout
        Box formatContainer = Box.createVerticalBox();

        // File type: Tabular/covariance
        Box fileTypeBox = Box.createHorizontalBox();

        tabularRadioButton = new JRadioButton("Tabular data");
        covarianceRadioButton = new JRadioButton("Covariance data");

        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup fileTypeBtnGrp = new ButtonGroup();
        fileTypeBtnGrp.add(tabularRadioButton);
        fileTypeBtnGrp.add(covarianceRadioButton);

        // Tabular data is selected by default
        tabularRadioButton.setSelected(true);

        // Add to file type box
        fileTypeBox.add(new JLabel("File type:"));
        fileTypeBox.add(Box.createRigidArea(new Dimension(10, 1)));
        fileTypeBox.add(tabularRadioButton);
        fileTypeBox.add(covarianceRadioButton);
        fileTypeBox.add(Box.createHorizontalGlue());
        formatContainer.add(fileTypeBox);

        // Add to format container
        formatContainer.add(Box.createVerticalStrut(5));

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

        dataTypeBox.add(new JLabel("Data type:"));
        dataTypeBox.add(Box.createRigidArea(new Dimension(10, 1)));
        dataTypeBox.add(contRadioButton);
        dataTypeBox.add(discRadioButton);
        // Hide mixed option for now until we have that in fast data reader - Zhou
        //dataTypeBox.add(mixedRadioButton);
        dataTypeBox.add(Box.createHorizontalGlue());

        formatContainer.add(dataTypeBox);

        formatContainer.add(Box.createVerticalStrut(5));

        // Value Delimiter box
        Box valueDelimiterBox = Box.createHorizontalBox();

        // Value Delimiter
        commaDelimiterRadioButton = new JRadioButton("Comma");
        tabDelimiterRadioButton = new JRadioButton("Tab");
        spaceDelimiterRadioButton = new JRadioButton("Whitespace");

        ButtonGroup delimiterBtnGrp = new ButtonGroup();
        delimiterBtnGrp.add(commaDelimiterRadioButton);
        delimiterBtnGrp.add(tabDelimiterRadioButton);
        delimiterBtnGrp.add(spaceDelimiterRadioButton);

        // Defaults to comma
        commaDelimiterRadioButton.setSelected(true);

        valueDelimiterBox.add(new JLabel("Value delimiter:"));
        valueDelimiterBox.add(Box.createRigidArea(new Dimension(10, 1)));
        valueDelimiterBox.add(commaDelimiterRadioButton);
        valueDelimiterBox.add(tabDelimiterRadioButton);
        valueDelimiterBox.add(spaceDelimiterRadioButton);
        valueDelimiterBox.add(Box.createHorizontalGlue());
        formatContainer.add(valueDelimiterBox);

        formatContainer.add(Box.createVerticalStrut(5));

        // Var names in first row of data
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

        // Add to firstRowVarNamesBox
        firstRowVarNamesBox.add(new JLabel("Variable names in first row of data:"));
        valueDelimiterBox.add(Box.createRigidArea(new Dimension(10, 1)));
        firstRowVarNamesBox.add(firstRowVarNamesYesRadioButton);
        firstRowVarNamesBox.add(firstRowVarNamesNoRadioButton);
        firstRowVarNamesBox.add(Box.createHorizontalGlue());

        formatContainer.add(firstRowVarNamesBox);

        // Use a titled border with 5 px inside padding - Zhou
        String borderTitle = "Step 1: Specify Format";
        if (files.length > 1) {
            borderTitle = borderTitle + " (apply to all files)";
        }
        formatContainer.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(borderTitle), new EmptyBorder(5, 5, 5, 5)));

        return formatContainer;
    }

    // Step 2 items
    public final Box selectOptions() {
        // Data loading params layout
        Box optionsContainer = Box.createVerticalBox();

        // Case ID's provided
        Box caseIdProvidedBox = Box.createHorizontalBox();

        // ID radio buttons
        idNoneRadioButton = new JRadioButton("None");
        idUnlabeledFirstColRadioButton = new JRadioButton("Unlabeled 1st column");
        idLabeledColRadioButton = new JRadioButton("Column labeled: ");
        idStringField = new StringTextField("", 4);

        ButtonGroup caseIdBtnGrp = new ButtonGroup();
        caseIdBtnGrp.add(idNoneRadioButton);
        caseIdBtnGrp.add(idUnlabeledFirstColRadioButton);
        caseIdBtnGrp.add(idLabeledColRadioButton);

        // Defaults to none
        idNoneRadioButton.setSelected(true);

        caseIdProvidedBox.add(new JLabel("Case IDs:"));
        caseIdProvidedBox.add(Box.createRigidArea(new Dimension(10, 1)));
        caseIdProvidedBox.add(idNoneRadioButton);
        caseIdProvidedBox.add(idUnlabeledFirstColRadioButton);
        caseIdProvidedBox.add(idLabeledColRadioButton);
        caseIdProvidedBox.add(idStringField);
        caseIdProvidedBox.add(Box.createHorizontalGlue());
        optionsContainer.add(caseIdProvidedBox);

        optionsContainer.add(Box.createVerticalStrut(5));

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
        commentStringField = new StringTextField("@", 4);

        // Select double slash by default
        commentDoubleSlashRadioButton.setSelected(true);

        commentMarkerBox.add(new JLabel("Comment marker:"));
        commentMarkerBox.add(Box.createRigidArea(new Dimension(10, 1)));
        commentMarkerBox.add(commentDoubleSlashRadioButton);
        commentMarkerBox.add(commentPondRadioButton);
        commentMarkerBox.add(commentOtherRadioButton);
        commentMarkerBox.add(commentStringField);
        commentMarkerBox.add(Box.createHorizontalGlue());
        optionsContainer.add(commentMarkerBox);

        optionsContainer.add(Box.createVerticalStrut(5));

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

        quoteCharBox.add(new JLabel("Quote character:"));
        quoteCharBox.add(Box.createRigidArea(new Dimension(10, 1)));
        quoteCharBox.add(noneQuoteRadioButton);
        quoteCharBox.add(doubleQuoteRadioButton);
        quoteCharBox.add(singleQuoteRadioButton);
        quoteCharBox.add(Box.createHorizontalGlue());
        optionsContainer.add(quoteCharBox);

        optionsContainer.add(Box.createVerticalStrut(5));

        /* Hide missing value marker for now - Zhou

        //  Missing value marker
        Box missingValueMarkerBox = Box.createHorizontalBox();

        missingValueBlankRadioButton = new JRadioButton("Blank");
        missingValueStarRadioButton = new JRadioButton("*");
        missingValueQuestionRadioButton = new JRadioButton("?");
        missingValueOtherRadioButton = new JRadioButton("Other: ");

        ButtonGroup missingValueMarkerBtnGrp = new ButtonGroup();
        missingValueMarkerBtnGrp.add(missingValueBlankRadioButton);
        missingValueMarkerBtnGrp.add(missingValueStarRadioButton);
        missingValueMarkerBtnGrp.add(missingValueQuestionRadioButton);
        missingValueMarkerBtnGrp.add(missingValueOtherRadioButton);

        // Missing string field: other
        missingStringField = new StringTextField("", 6);
        missingStringField.setText("Missing");

        // Blank is selected as the default
        missingValueBlankRadioButton.setSelected(true);

        missingValueMarkerBox.add(new JLabel("Missing value marker:"));
        missingValueMarkerBox.add(Box.createRigidArea(new Dimension(10, 1)));
        missingValueMarkerBox.add(missingValueBlankRadioButton);
        missingValueMarkerBox.add(missingValueStarRadioButton);
        missingValueMarkerBox.add(missingValueQuestionRadioButton);
        missingValueMarkerBox.add(missingValueOtherRadioButton);
        missingValueMarkerBox.add(missingStringField);
        missingValueMarkerBox.add(Box.createHorizontalGlue());
        optionsContainer.add(missingValueMarkerBox);

        optionsContainer.add(Box.createVerticalStrut(5));
         */

 /* Hide this since mixed data is not ready - Zhou

        // Max number of disc columns
        Box maxIntegralDiscreteBox = Box.createHorizontalBox();

        maxIntegralLabel1 = new JLabel("Integral columns with up to ");
        maxIntegralLabel2 = new JLabel(" distinct values are discrete.");

        maxIntegralDiscreteIntField = new IntTextField(0, 3);

        // 0 by default
        maxIntegralDiscreteIntField.setValue(0);

        maxIntegralDiscreteIntField.setFilter(new IntTextField.Filter() {
            @Override
            public int filter(int value, int oldValue) {
                if (value >= 0) {
                    return value;
                } else {
                    return oldValue;
                }
            }
        });

        maxIntegralDiscreteBox.add(maxIntegralLabel1);
        maxIntegralDiscreteBox.add(maxIntegralDiscreteIntField);
        maxIntegralDiscreteBox.add(maxIntegralLabel2);
        maxIntegralDiscreteBox.add(Box.createHorizontalGlue());
        optionsContainer.add(maxIntegralDiscreteBox);
         */
        // Use a titled border with 5 px inside padding - Zhou
        String borderTitle = "Step 2: Select Options";
        if (files.length > 1) {
            borderTitle = borderTitle + " (apply to all files)";
        }
        optionsContainer.setBorder(new CompoundBorder(BorderFactory.createTitledBorder(borderTitle), new EmptyBorder(5, 5, 5, 5)));

        return optionsContainer;
    }

    public DataModel[] getDataModels() {
        return dataModels;
    }

    public JRadioButton getTabularRadioButton() {
        return tabularRadioButton;
    }

    public JRadioButton getCovarianceRadioButton() {
        return covarianceRadioButton;
    }

    public StringTextField getCommentStringField() {
        return commentStringField;
    }

    public StringTextField getIdStringField() {
        return idStringField;
    }

    public StringTextField getMissingStringField() {
        return missingStringField;
    }

    public IntTextField getMaxIntegralDiscreteIntField() {
        return maxIntegralDiscreteIntField;
    }

    public JLabel getMaxIntegralLabel1() {
        return maxIntegralLabel1;
    }

    public JLabel getMaxIntegralLabel2() {
        return maxIntegralLabel2;
    }

    public int getFileIndex() {
        return fileIndex;
    }

    private String getCommentString() {
        if (commentDoubleSlashRadioButton.isSelected()) {
            return "//";
        } else if (commentPondRadioButton.isSelected()) {
            return "#";
        } else {
            return commentStringField.getText();
        }
    }

    private DelimiterType getDelimiterType() {
        if (commaDelimiterRadioButton.isSelected()) {
            return DelimiterType.COMMA;
        } else if (tabDelimiterRadioButton.isSelected()) {
            return DelimiterType.TAB;
        } else if (spaceDelimiterRadioButton.isSelected()) {
            return DelimiterType.WHITESPACE;
        } else {
//            return delimiterStringField.getText();
            throw new IllegalArgumentException("Unexpected Value delimiter selection.");
        }
    }

    private char getQuoteChar() {
        if (doubleQuoteRadioButton.isSelected()) {
            return '"';
        } else {
            return '\'';
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

    private String getIdLabel() {
        if (idNoneRadioButton.isSelected()) {
            return null;
        } else if (idUnlabeledFirstColRadioButton.isSelected()) {
            return null;
        } else {
            return idStringField.getText();
        }
    }

    private String getMissingValue() {
        if (missingValueStarRadioButton.isSelected()) {
            return "*";
        } else if (missingValueQuestionRadioButton.isSelected()) {
            return "?";
        } else {
            return missingStringField.getText();
        }
    }

    private int getMaxDiscrete() {
        return maxIntegralDiscreteIntField.getValue();
    }

    public DataModel loadDataWithSettings(int fileIndex, JTextArea summaryTextArea, File[] files) {
        summaryTextArea.setText("");

        TextAreaOutputStream out1 = new TextAreaOutputStream(summaryTextArea);
        PrintStream out = new PrintStream(out1);

        TetradLogger.getInstance().addOutputStream(out);
        TetradLogger.getInstance().setForceLog(true);

        try {
            DataReader reader = new DataReader();

            reader.setCommentMarker(getCommentString());
            reader.setDelimiter(getDelimiterType());
            reader.setQuoteChar(getQuoteChar());
            reader.setVariablesSupplied(isVarNamesFirstRow());
            reader.setIdLabel(getIdLabel());
            reader.setMissingValueMarker(getMissingValue());
            reader.setMaxIntegralDiscrete(getMaxDiscrete());

            DataModel dataModel;

            if (tabularRadioButton.isSelected()) {
                // dataModel = parser.parseTabular(string.toCharArray());
                dataModel = reader.parseTabular(files[fileIndex]);
            } else {
                // String string = fileTextArea.getText();
                // dataModel = reader.parseCovariance(string.toCharArray());
                dataModel = reader.parseCovariance(files[fileIndex]);
            }

//            addDataModel(dataModel, fileIndex, files[fileIndex].getNode());
            summaryTextArea.setCaretPosition(summaryTextArea.getText().length());

            return dataModel;
        } catch (Exception e1) {
            out.println(e1.getMessage());
            out.println("\nIf that message was unhelpful, "
                    + "\nplease copy and paste the (Java) "
                    + "\nerror below to Joe Ramsey, "
                    + "\njdramsey@andrew.cmu.edu, "
                    + "\nso a better error message "
                    + "\ncan be put at that location."
                    + "\nThanks!");

            out.println();
            e1.printStackTrace(out);
        }

        TetradLogger.getInstance().removeOutputStream(out);
        TetradLogger.getInstance().setForceLog(false);

        return null;
    }

}
