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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintStream;
import java.util.prefs.Preferences;
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

    private JRadioButton comment1RadioButton;
    private JRadioButton comment2RadioButton;
    private JRadioButton comment3RadioButton;
    private StringTextField commentStringField;

    private JRadioButton commaDelimiterRadioButton;
    private JRadioButton tabDelimiterRadioButton;
    private JRadioButton spaceDelimiterRadioButton;

    private JRadioButton noneQuoteRadioButton;
    private JRadioButton doubleQuoteRadioButton;
    private JRadioButton singleQuoteRadioButton;

    private JCheckBox varNamesCheckBox;
    private JRadioButton idNoneRadioButton;
    private JRadioButton id1RadioButton;
    private JRadioButton id2RadioButton;
    private StringTextField idStringField;

    private JRadioButton missing1RadioButton;
    private JRadioButton missing2RadioButton;
    private JRadioButton missing3RadioButton;
    private JRadioButton missing4RadioButton;
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

    public final Box specifyFormat() {
        // Data loading params layout
        Box formatContainer = Box.createVerticalBox();

        // File type: Tabular/covariance
        Box fileTypeBox = Box.createHorizontalBox();

        tabularRadioButton = new JRadioButton("Tabular data");
        covarianceRadioButton = new JRadioButton("Covariance data");

        tabularRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataTabularPreference", "tabular");

                }
            }
        });

        // We need to group the radio buttons, otherwise all can be selected
        ButtonGroup fileTypeBtnGrp = new ButtonGroup();
        fileTypeBtnGrp.add(tabularRadioButton);
        fileTypeBtnGrp.add(covarianceRadioButton);

        String tabularPreference = Preferences.userRoot().get("loadDataTabularPreference", "tabular");

        if ("tabular".equals(tabularPreference)) {
            tabularRadioButton.setSelected(true);
        } else if ("covariance".equals(tabularPreference)) {
            covarianceRadioButton.setSelected(true);
        } else {
            throw new IllegalStateException("Unexpected preference.");
        }

        covarianceRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataTabularPreference", "covariance");

                }
            }
        });

        fileTypeBox.add(new JLabel("File type:"));
        fileTypeBox.add(Box.createRigidArea(new Dimension(10, 1)));
        fileTypeBox.add(tabularRadioButton);
        fileTypeBox.add(covarianceRadioButton);
        fileTypeBox.add(Box.createHorizontalGlue());
        formatContainer.add(fileTypeBox);

        formatContainer.add(Box.createVerticalStrut(5));

        // Data type - moved from the old Fast tab - Zhou
        Box dataTypeBox = Box.createHorizontalBox();
        // Data type: continuous, discrete, or mixed
        contRadioButton = new JRadioButton("Continuous");
        discRadioButton = new JRadioButton("Discrete");
        mixedRadioButton = new JRadioButton("Mixed");

        // Continuous radion button is selected by default
        contRadioButton.setSelected(true);

        ButtonGroup dataTypeBtnGrp = new ButtonGroup();
        dataTypeBtnGrp.add(contRadioButton);
        dataTypeBtnGrp.add(discRadioButton);
        dataTypeBtnGrp.add(mixedRadioButton);

        dataTypeBox.add(new JLabel("Data type:"));
        dataTypeBox.add(Box.createRigidArea(new Dimension(10, 1)));
        dataTypeBox.add(contRadioButton);
        dataTypeBox.add(discRadioButton);
        dataTypeBox.add(mixedRadioButton);
        dataTypeBox.add(Box.createHorizontalGlue());
        formatContainer.add(dataTypeBox);

        formatContainer.add(Box.createVerticalStrut(5));

        // Value Delimiter
        Box valueDelimiterBox = Box.createHorizontalBox();

        // Value Delimiter
        commaDelimiterRadioButton = new JRadioButton("Comma");
        tabDelimiterRadioButton = new JRadioButton("Tab");
        spaceDelimiterRadioButton = new JRadioButton("Whitespace");

        ButtonGroup delimiterBtnGrp = new ButtonGroup();
        delimiterBtnGrp.add(commaDelimiterRadioButton);
        delimiterBtnGrp.add(tabDelimiterRadioButton);
        delimiterBtnGrp.add(spaceDelimiterRadioButton);

        commaDelimiterRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataDelimiterPreference", "Comma");

                }
            }
        });

        tabDelimiterRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataDelimiterPreference", "Tab");

                }
            }
        });

        spaceDelimiterRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataDelimiterPreference", "Whitespace");

                }
            }
        });

        // Defaults to comma
        String delimiterPreference = Preferences.userRoot().get("loadDataDelimiterPreference", "Comma");

        if ("Comma".equals(delimiterPreference)) {
            commaDelimiterRadioButton.setSelected(true);
        } else if ("Tab".equals(delimiterPreference)) {
            tabDelimiterRadioButton.setSelected(true);
        } else {
            spaceDelimiterRadioButton.setSelected(true);
        }

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

        // Checkbox is on left of text by default
        varNamesCheckBox = new JCheckBox("Variable names in first row of data");

        // Listener
        varNamesCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox checkBox = (JCheckBox) actionEvent.getSource();

                if (checkBox.isSelected()) {
                    Preferences.userRoot().put("loadDataVarNames", "selected");
                } else {
                    Preferences.userRoot().put("loadDataVarNames", "deselected");
                }
            }
        });

        varNamesCheckBox.setSelected(Preferences.userRoot().get("loadDataVarNames", "selected").equals("selected"));

        firstRowVarNamesBox.add(varNamesCheckBox);
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

    public final Box selectOptions() {
        // Data loading params layout
        Box optionsContainer = Box.createVerticalBox();

        // Case ID's provided
        Box caseIdProvidedBox = Box.createHorizontalBox();

        // ID radio buttons
        idNoneRadioButton = new JRadioButton("None");
        id1RadioButton = new JRadioButton("Unlabeled 1st column");
        id2RadioButton = new JRadioButton("Column labeled: ");
        idStringField = new StringTextField("", 4);

        ButtonGroup caseIdBtnGrp = new ButtonGroup();
        caseIdBtnGrp.add(idNoneRadioButton);
        caseIdBtnGrp.add(id1RadioButton);
        caseIdBtnGrp.add(id2RadioButton);

        // Defaults to none
        idNoneRadioButton.setSelected(true);

        caseIdProvidedBox.add(new JLabel("Case IDs:"));
        caseIdProvidedBox.add(Box.createRigidArea(new Dimension(10, 1)));
        caseIdProvidedBox.add(idNoneRadioButton);
        caseIdProvidedBox.add(id1RadioButton);
        caseIdProvidedBox.add(id2RadioButton);
        caseIdProvidedBox.add(idStringField);
        caseIdProvidedBox.add(Box.createHorizontalGlue());
        optionsContainer.add(caseIdProvidedBox);

        optionsContainer.add(Box.createVerticalStrut(5));

        // Comment Marker
        Box commentMarkerBox = Box.createHorizontalBox();

        comment1RadioButton = new JRadioButton("//");
        comment2RadioButton = new JRadioButton("#");
        comment3RadioButton = new JRadioButton("Other: ");

        ButtonGroup commentMarkerBtnGrp = new ButtonGroup();
        commentMarkerBtnGrp.add(comment1RadioButton);
        commentMarkerBtnGrp.add(comment2RadioButton);
        commentMarkerBtnGrp.add(comment3RadioButton);

        comment1RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataCommentPreference", "//");

                }
            }
        });

        comment2RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataCommentPreference", "#");

                }
            }
        });

        comment3RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataCommentPreference", "Other");

                }
            }
        });

        String commentPreference = Preferences.userRoot().get("loadDataCommentPreference", "//");

        if ("//".equals(commentPreference)) {
            comment1RadioButton.setSelected(true);
        } else if ("#".equals(commentPreference)) {
            comment2RadioButton.setSelected(true);
        } else {
            comment3RadioButton.setSelected(true);
        }

        // Comment string field
        String otherCommentPreference = Preferences.userRoot().get("dataLoaderCommentString", "@");
        commentStringField = new StringTextField(otherCommentPreference, 4);

        commentStringField.setFilter(new StringTextField.Filter() {
            @Override
            public String filter(String value, String oldValue) {
                Preferences.userRoot().put("dataLoaderMaxIntegral", value);
                return value;
            }
        });

        commentStringField.setFilter(new StringTextField.Filter() {
            @Override
            public String filter(String value, String oldValue) {
                Preferences.userRoot().put("dataLoaderMaxIntegral", value);
                return value;
            }
        });

        commentMarkerBox.add(new JLabel("Comment marker:"));
        commentMarkerBox.add(Box.createRigidArea(new Dimension(10, 1)));
        commentMarkerBox.add(comment1RadioButton);
        commentMarkerBox.add(comment2RadioButton);
        commentMarkerBox.add(comment3RadioButton);
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

        noneQuoteRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataQuotePreference", "");
                }
            }
        });

        doubleQuoteRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataQuotePreference", "\"");

                }
            }
        });

        singleQuoteRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataQuotePreference", "'");
                }
            }
        });

        String quotePreference = Preferences.userRoot().get("loadDataQuotePreference", "\"");

        if ("\"".equals(quotePreference)) {
            doubleQuoteRadioButton.setSelected(true);
        } else if ("'".equals(quotePreference)) {
            singleQuoteRadioButton.setSelected(true);
        } else if ("".equals(quotePreference)) {
            noneQuoteRadioButton.setSelected(true);
        }

        quoteCharBox.add(new JLabel("Quote character:"));
        quoteCharBox.add(Box.createRigidArea(new Dimension(10, 1)));
        quoteCharBox.add(noneQuoteRadioButton);
        quoteCharBox.add(doubleQuoteRadioButton);
        quoteCharBox.add(singleQuoteRadioButton);
        quoteCharBox.add(Box.createHorizontalGlue());
        optionsContainer.add(quoteCharBox);

        optionsContainer.add(Box.createVerticalStrut(5));

        //  Missing value marker
        Box missingValueMarkerBox = Box.createHorizontalBox();

        missing1RadioButton = new JRadioButton("Blank");
        missing2RadioButton = new JRadioButton("*");
        missing3RadioButton = new JRadioButton("?");
        missing4RadioButton = new JRadioButton("Other: ");

        ButtonGroup missingValueMarkerBtnGrp = new ButtonGroup();
        missingValueMarkerBtnGrp.add(missing1RadioButton);
        missingValueMarkerBtnGrp.add(missing2RadioButton);
        missingValueMarkerBtnGrp.add(missing3RadioButton);
        missingValueMarkerBtnGrp.add(missing4RadioButton);

        missing1RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "");

                }
            }
        });

        missing2RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "*");

                }
            }
        });

        missing3RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "?");

                }
            }
        });

        missing4RadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "Other");

                }
            }
        });

        // Blank is selected as the default
        missing1RadioButton.setSelected(true);

        String missingPreference = Preferences.userRoot().get("loadDataMissingPreference", "*");

        if ("".equals(missingPreference)) {
            missing1RadioButton.setSelected(true);
        } else if ("*".equals(missingPreference)) {
            missing2RadioButton.setSelected(true);
        } else if ("?".equals(missingPreference)) {
            missing3RadioButton.setSelected(true);
        } else {
            missing4RadioButton.setSelected(true);
        }

        String otherMissingPreference = Preferences.userRoot().get("dataLoaderOtherMissingPreference", "");

        // Missing string field: other
        missingStringField = new StringTextField(otherMissingPreference, 6);
        String missingStringText = Preferences.userRoot().get("dataLoaderMissingString", "Missing");
        missingStringField.setText(missingStringText);

        missingStringField.setFilter(new StringTextField.Filter() {
            @Override
            public String filter(String value, String oldValue) {
                Preferences.userRoot().put("dataLoaderMaxIntegral", value);
                return value;
            }
        });

        missingValueMarkerBox.add(new JLabel("Missing value marker:"));
        missingValueMarkerBox.add(Box.createRigidArea(new Dimension(10, 1)));
        missingValueMarkerBox.add(missing1RadioButton);
        missingValueMarkerBox.add(missing2RadioButton);
        missingValueMarkerBox.add(missing3RadioButton);
        missingValueMarkerBox.add(missing4RadioButton);
        missingValueMarkerBox.add(missingStringField);
        missingValueMarkerBox.add(Box.createHorizontalGlue());
        optionsContainer.add(missingValueMarkerBox);

        optionsContainer.add(Box.createVerticalStrut(5));

        // Max number of disc columns
        Box maxIntegralDiscreteBox = Box.createHorizontalBox();

        maxIntegralLabel1 = new JLabel("Integral columns with up to ");
        maxIntegralLabel2 = new JLabel(" values are discrete.");

        maxIntegralDiscreteIntField = new IntTextField(0, 3);

        int maxIntegralPreference = Preferences.userRoot().getInt("dataLoaderMaxIntegral", 0);
        maxIntegralDiscreteIntField.setValue(maxIntegralPreference);

        maxIntegralDiscreteIntField.setFilter(new IntTextField.Filter() {
            @Override
            public int filter(int value, int oldValue) {
                if (value >= 0) {
                    Preferences.userRoot().putInt("dataLoaderMaxIntegral", value);
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

    public JRadioButton getComment1RadioButton() {
        return comment1RadioButton;
    }

    public JRadioButton getComment2RadioButton() {
        return comment2RadioButton;
    }

    public JRadioButton getComment3RadioButton() {
        return comment3RadioButton;
    }

    public StringTextField getCommentStringField() {
        return commentStringField;
    }

    public JCheckBox getVarNamesCheckBox() {
        return varNamesCheckBox;
    }

    public JRadioButton getId1RadioButton() {
        return id1RadioButton;
    }

    public JRadioButton getId2RadioButton() {
        return id2RadioButton;
    }

    public StringTextField getIdStringField() {
        return idStringField;
    }

    public JRadioButton getMissing1RadioButton() {
        return missing1RadioButton;
    }

    public JRadioButton getMissing2RadioButton() {
        return missing2RadioButton;
    }

    public JRadioButton getMissing3RadioButton() {
        return missing3RadioButton;
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
        if (comment1RadioButton.isSelected()) {
            return "//";
        } else if (comment2RadioButton.isSelected()) {
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
        return varNamesCheckBox.isSelected();
    }

    private String getIdLabel() {
        if (idNoneRadioButton.isSelected()) {
            return null;
        } else if (id1RadioButton.isSelected()) {
            return null;
        } else {
            return idStringField.getText();
        }
    }

    private String getMissingValue() {
        if (missing1RadioButton.isSelected()) {
            return "*";
        } else if (missing2RadioButton.isSelected()) {
            return "?";
        } else {
            return missingStringField.getText();
        }
    }

    private int getMaxDiscrete() {
        return maxIntegralDiscreteIntField.getValue();
    }

    public DataModel loadDataWithSettings(int fileIndex, JTextArea anomaliesTextArea, File[] files) {
        anomaliesTextArea.setText("");

        TextAreaOutputStream out1 = new TextAreaOutputStream(anomaliesTextArea);
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
            anomaliesTextArea.setCaretPosition(anomaliesTextArea.getText().length());

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
