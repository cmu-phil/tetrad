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
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
import edu.cmu.tetradapp.util.TextAreaOutputStream;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.prefs.Preferences;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class RegularDataPanel extends JPanel {
    private DataModel[] dataModels;

    private JRadioButton tabularRadioButton;
    private JRadioButton covarianceRadioButton;

    private JRadioButton comment1RadioButton;
    private JRadioButton comment2RadioButton;
    private JRadioButton comment3RadioButton;
    private StringTextField commentStringField;

    private JRadioButton delimiter1RadioButton;
    private JRadioButton delimiter2RadioButton;
    private JRadioButton delimiter3RadioButton;

    //    private JRadioButton delimiter4RadioButton;
    //    private StringTextField delimiterStringField;
    private JRadioButton quote1RadioButton;
    private JRadioButton quote2RadioButton;

    private JCheckBox varNamesCheckBox;
    private JCheckBox idsSupplied;
    private JRadioButton id1RadioButton;
    private JRadioButton id2RadioButton;
    private StringTextField idStringField;

    private JRadioButton missing1RadioButton;
    private JRadioButton missing2RadioButton;
    private JRadioButton missing3RadioButton;
    private StringTextField missingStringField;

    private JCheckBox logEmptyTokens;
    private IntTextField maxIntegralDiscreteIntField;
    private JLabel maxIntegralLabel1;
    private JLabel maxIntegralLabel2;

    private int fileIndex = 0;

    //================================CONSTRUCTOR=======================//

    public RegularDataPanel(final File... files) {
        if (files.length == 0) {
            throw new IllegalArgumentException("Must specify at least one file.");
        }

        this.dataModels = new DataModel[files.length];

        // Tabular/covariance.
        tabularRadioButton = new JRadioButton("Tabular Data");
        covarianceRadioButton = new JRadioButton("Covariance Data");

        tabularRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataTabularPreference", "tabular");

                }
            }
        });

        ButtonGroup group1 = new ButtonGroup();
        group1.add(tabularRadioButton);
        group1.add(covarianceRadioButton);

        String tabularPreference = Preferences.userRoot().get("loadDataTabularPreference", "tabular");

        if ("tabular".equals(tabularPreference)) {
            tabularRadioButton.setSelected(true);
        } else if ("covariance".equals(tabularPreference)) {
            covarianceRadioButton.setSelected(true);
        } else {
            throw new IllegalStateException("Unexpected preference.");
        }


        covarianceRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataTabularPreference", "covariance");

                }
            }
        });

        // Comment prefix.
        comment1RadioButton = new JRadioButton("//");
        comment2RadioButton = new JRadioButton("#");
        comment3RadioButton = new JRadioButton("Other: ");

        comment1RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataCommentPreference", "//");

                }
            }
        });

        comment2RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataCommentPreference", "#");

                }
            }
        });

        comment3RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataCommentPreference", "Other");

                }
            }
        });

        ButtonGroup group2 = new ButtonGroup();
        group2.add(comment1RadioButton);
        group2.add(comment2RadioButton);
        group2.add(comment3RadioButton);

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
            public String filter(String value, String oldValue) {
                Preferences.userRoot().put("dataLoaderMaxIntegral", value);
                return value;
            }
        });

        commentStringField.setFilter(new StringTextField.Filter() {
            public String filter(String value, String oldValue) {
                Preferences.userRoot().put("dataLoaderMaxIntegral", value);
                return value;
            }
        });

        // Delimiter
        delimiter1RadioButton = new JRadioButton("Whitespace");
        delimiter2RadioButton = new JRadioButton("Tab");
        delimiter3RadioButton = new JRadioButton("Comma");
//        delimiter4RadioButton = new JRadioButton("Other: ");
//        delimiterStringField = new StringTextField("", 4);


        delimiter1RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataDelimiterPreference", "Whitespace");

                }
            }
        });

        delimiter2RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataDelimiterPreference", "Tab");

                }
            }
        });

        delimiter3RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataDelimiterPreference", "Comma");

                }
            }
        });

        ButtonGroup group3 = new ButtonGroup();
        group3.add(delimiter1RadioButton);
        group3.add(delimiter2RadioButton);
        group3.add(delimiter3RadioButton);

        String delimiterPreference = Preferences.userRoot().get("loadDataDelimiterPreference", "Whitespace");

        if ("Whitespace".equals(delimiterPreference)) {
            delimiter1RadioButton.setSelected(true);
        } else if ("Tab".equals(delimiterPreference)) {
            delimiter2RadioButton.setSelected(true);
        } else {
            delimiter3RadioButton.setSelected(true);
        }

        // Quote char
        quote1RadioButton = new JRadioButton("\"");
        quote2RadioButton = new JRadioButton("'");

        quote1RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataQuotePreference", "\"");

                }
            }
        });

        quote2RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataQuotePreference", "'");
                }
            }
        });

        ButtonGroup group4 = new ButtonGroup();
        group4.add(quote1RadioButton);
        group4.add(quote2RadioButton);

        String quotePreference = Preferences.userRoot().get("loadDataQuotePreference", "\"");

        if ("\"".equals(quotePreference)) {
            quote1RadioButton.setSelected(true);
        } else if ("'".equals(quotePreference)) {
            quote2RadioButton.setSelected(true);
        }

        // Log empty tokens
        logEmptyTokens = new JCheckBox("Log Empty Tokens");
        logEmptyTokens.setHorizontalTextPosition(SwingConstants.LEFT);

        logEmptyTokens.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox checkBox = (JCheckBox) actionEvent.getSource();

                if (checkBox.isSelected()) {
                    Preferences.userRoot().put("loadDataLogEmptyTokens", "selected");
                } else {
                    Preferences.userRoot().put("loadDataLogEmptyTokens", "deselected");
                }
            }
        });

        String logEmptyTokensPreference = Preferences.userRoot().get("loadDataLogEmptyTokens", "\"");

        if ("selected".equals(logEmptyTokensPreference)) {
            logEmptyTokens.setSelected(true);
        } else {
            logEmptyTokens.setSelected(false);
        }

        // Var names checkbox.
        varNamesCheckBox = new JCheckBox("Variable names in first row of data");
        varNamesCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);

        varNamesCheckBox.addActionListener(new ActionListener() {
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

        // Ids Supplied.
        idsSupplied = new JCheckBox("Case ID's provided");
        idsSupplied.setHorizontalTextPosition(SwingConstants.LEFT);

        idsSupplied.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox checkBox = (JCheckBox) actionEvent.getSource();

                if (checkBox.isSelected()) {
                    Preferences.userRoot().put("loadDataIdsSuppliedPreference", "selected");
                } else {
                    Preferences.userRoot().put("loadDataIdsSuppliedPreference", "deselected");
                }
            }
        });

        boolean idsSuppliedPreference = "selected".equals(Preferences.userRoot().get("loadDataIdsSuppliedPreference", "deselected"));
        idsSupplied.setSelected(idsSuppliedPreference);

        // ID radio buttons
        id1RadioButton = new JRadioButton("Unlabeled first column");
        id2RadioButton = new JRadioButton("Column labeled: ");
        idStringField = new StringTextField("", 4);

        id1RadioButton.setEnabled(idsSuppliedPreference);
        id2RadioButton.setEnabled(idsSuppliedPreference);
        idStringField.setEditable(idsSuppliedPreference);

        idsSupplied.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox button = (JCheckBox) e.getSource();
                boolean selected = button.isSelected();

                id1RadioButton.setEnabled(selected);
                id2RadioButton.setEnabled(selected);
                idStringField.setEditable(selected);
            }
        });

        id1RadioButton.setEnabled(idsSuppliedPreference);
        id2RadioButton.setEnabled(idsSuppliedPreference);
        idStringField.setEditable(idsSuppliedPreference);

        ButtonGroup group5 = new ButtonGroup();
        group5.add(id1RadioButton);
        group5.add(id2RadioButton);
        id1RadioButton.setSelected(true);

//
//        varNamesCheckBox.setSelected(true);

//        Missing value marker
        missing1RadioButton = new JRadioButton("*");
        missing2RadioButton = new JRadioButton("?");
        missing3RadioButton = new JRadioButton("Other: ");

        missing1RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "*");

                }
            }
        });

        missing2RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "?");

                }
            }
        });

        missing3RadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("loadDataMissingPreference", "Other");

                }
            }
        });

        ButtonGroup group6 = new ButtonGroup();
        group6.add(missing1RadioButton);
        group6.add(missing2RadioButton);
        group6.add(missing3RadioButton);
        missing1RadioButton.setSelected(true);

        String missingPreference = Preferences.userRoot().get("loadDataMissingPreference", "*");

        if ("*".equals(missingPreference)) {
            missing1RadioButton.setSelected(true);
        } else if ("?".equals(missingPreference)) {
            missing2RadioButton.setSelected(true);
        } else {
            missing3RadioButton.setSelected(true);
        }

        String otherMissingPreference = Preferences.userRoot().get("dataLoaderOtherMissingPreference", "");

        // Missing string field
        missingStringField = new StringTextField(otherMissingPreference, 6);
        String missingStringText = Preferences.userRoot().get("dataLoaderMissingString", "Missing");
        missingStringField.setText(missingStringText);

        missingStringField.setFilter(new StringTextField.Filter() {
            public String filter(String value, String oldValue) {
                Preferences.userRoot().put("dataLoaderMaxIntegral", value);
                return value;
            }
        });


        maxIntegralDiscreteIntField = new IntTextField(0, 3);

        int maxIntegralPreference = Preferences.userRoot().getInt("dataLoaderMaxIntegral", 0);
        maxIntegralDiscreteIntField.setValue(maxIntegralPreference);

        maxIntegralDiscreteIntField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                if (value >= 0) {
                    Preferences.userRoot().putInt("dataLoaderMaxIntegral", value);
                    return value;
                }
                else {
                    return oldValue;
                }
            }
        });

        final JTextArea fileTextArea = new JTextArea();
        final JTextArea anomaliesTextArea = new JTextArea();
        final JTabbedPane tabbedPane = new JTabbedPane();
        JScrollPane scroll1 = new JScrollPane(fileTextArea);
        scroll1.setPreferredSize(new Dimension(500, 400));
        tabbedPane.addTab("File", scroll1);
        JScrollPane scroll2 = new JScrollPane(anomaliesTextArea);
        scroll2.setPreferredSize(new Dimension(500, 400));
        tabbedPane.addTab("Loading Log", scroll2);

        final JLabel progressLabel = new JLabel(getProgressString(0, files.length, dataModels));
        progressLabel.setFont(new Font("Dialog", Font.BOLD, 12));
        JButton previousButton = new JButton("Previous");
        JButton nextButton = new JButton("Next");
        JButton loadButton = new JButton("Load");
        JButton loadAllButton = new JButton("Load All");

        final JLabel fileNameLabel = new JLabel("File: " + files[fileIndex].getName());
        fileNameLabel.setFont(new Font("Dialog", Font.BOLD, 12));

        // Construct button groups.

        idStringField.setText("ID");
//        delimiterStringField.setText(";");

        maxIntegralLabel1 = new JLabel("Integral columns with up to ");
        maxIntegralLabel2 = new JLabel(" values are discrete.");

        if (tabularRadioButton.isSelected()) {
            enableTabularObjects();
        }
        else if (covarianceRadioButton.isSelected()) {
            enableCovarianceObjects();
        }

        // Layout.
        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("File Type:"));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createRigidArea(new Dimension(20, 1)));
        b2.add(tabularRadioButton);
        b2.add(covarianceRadioButton);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Delimiter"));
        b5.add(Box.createHorizontalGlue());
        b.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createRigidArea(new Dimension(20, 1)));
        b6.add(delimiter1RadioButton);
        b6.add(delimiter2RadioButton);
        b6.add(delimiter3RadioButton);
//        b6.add(delimiter4RadioButton);
//        b6.add(delimiterStringField);
        b6.add(Box.createHorizontalGlue());
        b.add(b6);

        Box b9 = Box.createHorizontalBox();
//        b9.add(new JLabel("Variable names in first row of data"));
        b9.add(varNamesCheckBox);
        b9.add(Box.createHorizontalGlue());
        b.add(b9);

        Box b10 = Box.createHorizontalBox();
//        b10.add(new JLabel("Case ID's provided"));
        b10.add(idsSupplied);
        b10.add(Box.createHorizontalGlue());
        b.add(b10);

        Box b11 = Box.createHorizontalBox();
        b11.add(Box.createRigidArea(new Dimension(20, 1)));
        b11.add(id1RadioButton);
        b11.add(id2RadioButton);
        b11.add(idStringField);
        b11.add(Box.createHorizontalGlue());
        b.add(b11);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Comment Marker"));
        b3.add(Box.createHorizontalGlue());
        b.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createRigidArea(new Dimension(20, 1)));
        b4.add(comment1RadioButton);
        b4.add(comment2RadioButton);
        b4.add(comment3RadioButton);
        b4.add(commentStringField);
        b4.add(Box.createHorizontalGlue());
        b.add(b4);

        Box b7 = Box.createHorizontalBox();
        b7.add(new JLabel("Quote Character"));
        b7.add(Box.createHorizontalGlue());
        b.add(b7);

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createRigidArea(new Dimension(20, 1)));
        b8.add(quote1RadioButton);
        b8.add(quote2RadioButton);
        b8.add(Box.createHorizontalGlue());
        b.add(b8);

        Box b12 = Box.createHorizontalBox();
        b12.add(new JLabel("Missing value marker (other than blank field):"));
        b12.add(Box.createHorizontalGlue());
        b.add(b12);

        Box b13 = Box.createHorizontalBox();
        b13.add(Box.createRigidArea(new Dimension(20, 1)));
        b13.add(missing1RadioButton);
        b13.add(missing2RadioButton);
        b13.add(missing3RadioButton);
        b13.add(missingStringField);
        b13.add(Box.createHorizontalGlue());
        b.add(b13);
        b.add(Box.createVerticalStrut(5));

        Box b14 = Box.createHorizontalBox();
        b14.add(maxIntegralLabel1);
        b14.add(maxIntegralDiscreteIntField);
        b14.add(maxIntegralLabel2);
        b14.add(Box.createHorizontalGlue());

        b.add(b14);
        b.add(Box.createVerticalStrut(5));

        Box b16 = Box.createHorizontalBox();
        b16.add(this.logEmptyTokens);
        b16.add(Box.createHorizontalGlue());


        b.add(b16);


        b.add(Box.createVerticalGlue());
//        b.setBorder(new EmptyBorder(0, 0, 0, 3));
        b.setBorder(new TitledBorder("Data Loading Parameters"));


        tabularRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enableTabularObjects();
            }
        });

        covarianceRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                enableCovarianceObjects();
            }
        });

        b.add(Box.createVerticalGlue());
        b.setBorder(new EmptyBorder(0, 0, 0, 3));
        b.setBorder(new TitledBorder("Data Loading Parameters"));

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    private void enableCovarianceObjects() {
        idsSupplied.setEnabled(false);
        id1RadioButton.setEnabled(false);
        id2RadioButton.setEnabled(false);
        idStringField.setEnabled(false);
        maxIntegralLabel1.setEnabled(false);
        maxIntegralLabel2.setEnabled(false);
        maxIntegralDiscreteIntField.setEnabled(false);
        varNamesCheckBox.setEnabled(false);
    }

    private void enableTabularObjects() {
        idsSupplied.setEnabled(true);
        idStringField.setEnabled(true);
        maxIntegralLabel1.setEnabled(true);
        maxIntegralLabel2.setEnabled(true);
        maxIntegralDiscreteIntField.setEnabled(true);
        varNamesCheckBox.setEnabled(true);


        if (idsSupplied.isSelected()) {
            id1RadioButton.setEnabled(true);
            id2RadioButton.setEnabled(true);
            idStringField.setEnabled(true);
        }
    }

    public String getProgressString(int fileIndex, int numFiles, DataModel[] dataModels) {
        return (dataModels[fileIndex] == null ? "" : "*") + (fileIndex + 1) + " / " + numFiles;
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

    public JRadioButton getDelimiter1RadioButton() {
        return delimiter1RadioButton;
    }

    public JRadioButton getDelimiter2RadioButton() {
        return delimiter2RadioButton;
    }

    public JRadioButton getDelimiter3RadioButton() {
        return delimiter3RadioButton;
    }

    public JRadioButton getQuote1RadioButton() {
        return quote1RadioButton;
    }

    public JRadioButton getQuote2RadioButton() {
        return quote2RadioButton;
    }

    public JCheckBox getVarNamesCheckBox() {
        return varNamesCheckBox;
    }

    public JCheckBox getIdsSupplied() {
        return idsSupplied;
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

    public JCheckBox getLogEmptyTokens() {
        return logEmptyTokens;
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
        if (delimiter1RadioButton.isSelected()) {
            return DelimiterType.WHITESPACE;
        } else if (delimiter2RadioButton.isSelected()) {
            return DelimiterType.TAB;
        } else if (delimiter3RadioButton.isSelected()) {
            return DelimiterType.COMMA;
        } else {
//            return delimiterStringField.getText();
            throw new IllegalArgumentException("Unexpected delimiter selection.");
        }
    }

    private char getQuoteChar() {
        if (quote1RadioButton.isSelected()) {
            return '"';
        } else {
            return '\'';
        }
    }

    private boolean isVarNamesFirstRow() {
        return varNamesCheckBox.isSelected();
    }

    private boolean isIdsSupplied() {
        return idsSupplied.isSelected();
    }

    private String getIdLabel() {
        if (id1RadioButton.isSelected()) {
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



    public DataModel loadData(int fileIndex, JTextArea anomaliesTextArea, JTabbedPane tabbedPane, File[] files, JLabel progressLabel) {
        anomaliesTextArea.setText("");

        TextAreaOutputStream out1
                = new TextAreaOutputStream(anomaliesTextArea);
        PrintStream out = new PrintStream(out1);

        TetradLogger.getInstance().addOutputStream(out);
        TetradLogger.getInstance().setForceLog(true);

        try {

            // Select the "Anomalies" tab.
            tabbedPane.setSelectedIndex(1);

            DataReader reader = new DataReader();
            reader.setLogEmptyTokens(logEmptyTokens.isSelected());

            reader.setCommentMarker(getCommentString());
            reader.setDelimiter(getDelimiterType());
            reader.setQuoteChar(getQuoteChar());
            reader.setVariablesSupplied(isVarNamesFirstRow());
            reader.setIdsSupplied(isIdsSupplied());
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

//            addDataModel(dataModel, fileIndex, files[fileIndex].getName());

            anomaliesTextArea.setCaretPosition(
                    anomaliesTextArea.getText().length());

            progressLabel.setText(getProgressString(fileIndex, files.length, dataModels));

            return dataModel;
        }
        catch (Exception e1) {
            out.println(e1.getMessage());
            out.println("\nIf that message was unhelpful, " +
                    "\nplease copy and paste the (Java) " +
                    "\nerror below to Joe Ramsey, " +
                    "\njdramsey@andrew.cmu.edu, " +
                    "\nso a better error message " +
                    "\ncan be put at that location." +
                    "\nThanks!");

            out.println();
            e1.printStackTrace(out);
        }

        TetradLogger.getInstance().removeOutputStream(out);
        TetradLogger.getInstance().setForceLog(false);

        return null;
    }

}


