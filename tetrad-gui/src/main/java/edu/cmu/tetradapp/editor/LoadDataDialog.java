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
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
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
final class LoadDataDialog extends JPanel {
    private final JTabbedPane pane;
    private transient DataModel[] dataModels;

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

    public LoadDataDialog(final File... files) {
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

        // Setup file text area.
//        fileTextArea.setEditable(false);
        fileTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        setText(files[fileIndex], fileTextArea);

        maxIntegralLabel1 = new JLabel("Integral columns with up to ");
        maxIntegralLabel2 = new JLabel(" values are discrete.");

        if (tabularRadioButton.isSelected()) {
            enableTabularObjects();
        }
        else if (covarianceRadioButton.isSelected()) {
            enableCovarianceObjects();
        }

        // Layout.
        RegularDataPanel r1 = new RegularDataPanel(files);
        FastDataPanel r2 = new FastDataPanel(files);

        pane = new JTabbedPane();
        pane.add("Regular", r1);
        pane.add("Fast", r2);

        Box c = Box.createVerticalBox();

        Box c1 = Box.createHorizontalBox();
//        JScrollPane scrollPane = new JScrollPane(tabbedPane);
//        scrollPane.setPreferredSize(new Dimension(500, 400));
//        c1.add(scrollPane);
        c1.add(tabbedPane);
        c.add(c1);

        Box c2 = Box.createHorizontalBox();
        c2.add(Box.createHorizontalGlue());

        if (files.length > 1) {
            c2.add(progressLabel);
        }

        c2.add(Box.createHorizontalStrut(10));

        if (files.length > 1) {
            c2.add(previousButton);
            c2.add(nextButton);
        }

        c2.add(loadButton);

        if (files.length > 1) {
            c2.add(loadAllButton);
        }

        c2.setBorder(new EmptyBorder(4, 4, 4, 4));
        c.add(c2);
        c.setBorder(new TitledBorder("Source File and Loading Log"));

        Box a = Box.createHorizontalBox();
        a.add(pane);
        a.add(c);
        setLayout(new BorderLayout());

        Box d = Box.createHorizontalBox();
        d.add(fileNameLabel);
        d.add(Box.createHorizontalGlue());

        Box e = Box.createVerticalBox();
        e.add(d);
        e.add(Box.createVerticalStrut(10));
        e.add(a);

        add(e, BorderLayout.CENTER);

        // Listeners.

        previousButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fileTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

                if (fileIndex > 0) {
                    fileIndex--;
                }

                setText(files[fileIndex], fileTextArea);
                progressLabel.setText(getProgressString(fileIndex, files.length, dataModels));

                tabbedPane.setSelectedIndex(0);
                fileNameLabel.setText("File: " + files[fileIndex].getName());
            }
        });

        nextButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fileTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

                if (fileIndex < files.length - 1) {
                    fileIndex++;
                }

                setText(files[fileIndex], fileTextArea);
                progressLabel.setText(getProgressString(fileIndex, files.length, dataModels));

                tabbedPane.setSelectedIndex(0);
                fileNameLabel.setText("File: " + files[fileIndex].getName());
            }
        });

        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        Window owner = (Window) getTopLevelAncestor();

                        new WatchedProcess(owner) {
                            public void watch() {
                                loadDataSelect(fileIndex, anomaliesTextArea, tabbedPane, files, progressLabel);
//                                DataModel dataModel = loadDataSelect(anomaliesTextArea, tabbedPane, files, progressLabel);
//                                if (dataModel == null) throw new NullPointerException("Data not loaded.");
//                                addDataModel(dataModel, fileIndex, files[fileIndex].getName());
                            }
                        };
                    }
                };
            }
        });

        loadAllButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        Window owner = (Window) getTopLevelAncestor();

                        new WatchedProcess(owner) {
                            public void watch() {
                                for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
                                    loadDataSelect(fileIndex, anomaliesTextArea, tabbedPane, files, progressLabel);
                                }
                            }
                        };
                    }
                };
            }
        });

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
    }

    public void loadDataSelect(int fileIndex, JTextArea anomaliesTextArea, JTabbedPane tabbedPane, File[] files, JLabel progressLabel) {
        System.out.println("File index = " + fileIndex);

        Component selectedComponent = pane.getSelectedComponent();

        if (selectedComponent instanceof RegularDataPanel) {
            DataModel dataModel = ((RegularDataPanel) selectedComponent).loadData(fileIndex, anomaliesTextArea, tabbedPane, files,
                    progressLabel);
            if (dataModel == null) throw new NullPointerException("Data not loaded.");
            addDataModel(dataModel, fileIndex, files[fileIndex].getName());
        }
        else if (selectedComponent instanceof FastDataPanel) {
            DataModel dataModel = ((FastDataPanel) selectedComponent).loadData(fileIndex, anomaliesTextArea, tabbedPane, files,
                    progressLabel);
            if (dataModel == null) throw new NullPointerException("Data not loaded.");
            addDataModel(dataModel, fileIndex, files[fileIndex].getName());
        }
        else {
            throw new IllegalStateException("Just regular and fast data loaders.");
        }
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

    //==============================PUBLIC METHODS=========================//

    public DataModelList getDataModels() {
        DataModelList dataModelList = new DataModelList();

        for (DataModel dataModel : dataModels) {
            if (dataModel != null) dataModelList.add(dataModel);
        }

        return dataModelList;
    }

    private static void setText(File file, JTextArea textArea) {
        try {
            textArea.setText("");

            BufferedReader in = new BufferedReader(new FileReader(file));
            StringBuilder text = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                text.append(line.substring(0, line.length())).append("\n");

                if (text.length() > 50000) {
                    textArea.append("(This is a large file that begins as follows...)\n");
                    textArea.setEditable(false);
                    break;
                }
            }

            textArea.append(text.toString());

            if (!textArea.isEditable()) {
                textArea.append(". . .");
            }

            textArea.setCaretPosition(0);
            in.close();
        }
        catch (IOException e) {
            textArea.append("<<<ERROR READING FILE>>>");
            textArea.setEditable(false);
        }
    }

//    private static void setText(File file, JTextArea textArea) {
//        try {
//            FileReader in = new FileReader(file);
//            CharArrayWriter out = new CharArrayWriter();
//            int c;
//
//            while ((c = in.read()) != -1) {
//                out.write(c);
//            }
//
//            textArea.setText(out.toString());
//
//            textArea.setCaretPosition(0);
//            in.close();
//        }
//        catch (IOException e) {
//            textArea.append("<<<ERROR READING FILE>>>");
//        }
//    }


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

    private void addDataModel(DataModel dataModel, int index, String name) {
        if (dataModel == null) throw new NullPointerException();

        dataModel.setName(name);
        this.dataModels[index] = dataModel;
    }

    public String getProgressString(int fileIndex, int numFiles, DataModel[] dataModels) {
        return (dataModels[fileIndex] == null ? "" : "*") + (fileIndex + 1) + " / " + numFiles;
    }
}


