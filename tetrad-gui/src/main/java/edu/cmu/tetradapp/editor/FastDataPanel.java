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

import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;
import edu.cmu.tetradapp.util.TextAreaOutputStream;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class FastDataPanel extends JPanel {
    private transient DataModel[] dataModels;

    private JRadioButton tabularRadioButton;

    private JRadioButton discrete;
    private JRadioButton continuous;

    private JRadioButton delimiter2RadioButton;
    private JRadioButton delimiter3RadioButton;

    private int fileIndex = 0;

    //================================CONSTRUCTOR=======================//

    public FastDataPanel(final File... files) {
        if (files.length == 0) {
            throw new IllegalArgumentException("Must specify at least one file.");
        }

        this.dataModels = new DataModel[files.length];

        // Tabular/covariance.
        tabularRadioButton = new JRadioButton("Tabular Data");
//        covarianceRadioButton = new JRadioButton("Covariance Data");

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
//        group1.add(covarianceRadioButton);

        String tabularPreference = Preferences.userRoot().get("loadDataTabularPreference", "tabular");

        if ("tabular".equals(tabularPreference)) {
            tabularRadioButton.setSelected(true);
        }
        else {
//            throw new IllegalStateException("Unexpected preference.");
        }

        // Tabular/covariance.
        continuous = new JRadioButton("Continuous");
        discrete = new JRadioButton("Discrete");

        continuous.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("continuousDiscrete", "continuous");
                }
            }
        });

        discrete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("continuousDiscrete", "discrete");
                }
            }
        });

        ButtonGroup group1a = new ButtonGroup();
        group1a.add(continuous);
        group1a.add(discrete);

        String continuousPreference = Preferences.userRoot().get("continuousDiscrete", "continuous");

        if ("continuous".equals(continuousPreference)) {
            continuous.setSelected(true);
        }
        else if ("discrete".equals(continuousPreference)) {
            discrete.setSelected(true);
        }
        else {
            throw new IllegalStateException("Unexpected preference.");
        }

        continuous.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("continuousDiscrete", "continuous");
                }
            }
        });

        discrete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    Preferences.userRoot().put("continuousDiscrete", "discrete");
                }
            }
        });

        delimiter2RadioButton = new JRadioButton("Tab");
        delimiter3RadioButton = new JRadioButton("Comma");

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
        group3.add(delimiter2RadioButton);
        group3.add(delimiter3RadioButton);

        String delimiterPreference = Preferences.userRoot().get("loadDataDelimiterPreference", "Whitespace");

        if ("Tab".equals(delimiterPreference)) {
            delimiter2RadioButton.setSelected(true);
        } else {
            delimiter3RadioButton.setSelected(true);
        }

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

        final JLabel fileNameLabel = new JLabel("File: " + files[fileIndex].getName());
        fileNameLabel.setFont(new Font("Dialog", Font.BOLD, 12));

        // Construct button groups.

        // Layout.
        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("File Type:"));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createRigidArea(new Dimension(20, 1)));
        b2.add(tabularRadioButton);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);

        Box b2a = Box.createHorizontalBox();
        b2a.add(new JLabel("Data Type:"));
        b2a.add(Box.createHorizontalGlue());
        b.add(b2a);

        Box b2b = Box.createHorizontalBox();
        b2b.add(Box.createRigidArea(new Dimension(20, 1)));
        b2b.add(continuous);
        b2b.add(discrete);
        b2b.add(Box.createHorizontalGlue());
        b.add(b2b);

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Delimiter"));
        b5.add(Box.createHorizontalGlue());
        b.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createRigidArea(new Dimension(20, 1)));
        b6.add(delimiter2RadioButton);
        b6.add(delimiter3RadioButton);
        b6.add(Box.createHorizontalGlue());
        b.add(b6);

        b.add(Box.createVerticalGlue());
        b.setBorder(new TitledBorder("Data Loading Parameters"));

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
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

    public JRadioButton getDelimiter2RadioButton() {
        return delimiter2RadioButton;
    }

    public JRadioButton getDelimiter3RadioButton() {
        return delimiter3RadioButton;
    }

    public int getFileIndex() {
        return fileIndex;
    }

    public DataModel loadData(int fileIndex, JTextArea anomaliesTextArea, JTabbedPane tabbedPane, File[] files, JLabel progressLabel) {
        anomaliesTextArea.setText("");

        TextAreaOutputStream out1
                = new TextAreaOutputStream(anomaliesTextArea);
        PrintStream out = new PrintStream(out1);

        TetradLogger.getInstance().addOutputStream(out);
        TetradLogger.getInstance().setForceLog(true);

        try {

            // Select the "Anomalies" tab.eede
            tabbedPane.setSelectedIndex(1);

            char delim = getDelimiter2RadioButton().isSelected() ? '\t' : ',';
            Set<String> excluded = new HashSet<String>();
            excluded.add("MULT");

            DataSet dataSet;

            if (continuous.isSelected()) {
                dataSet = BigDataSetUtility.readInContinuousData(files[fileIndex], delim, excluded);
            }
            else {
                dataSet = BigDataSetUtility.readInDiscreteData(files[fileIndex], delim, excluded);
            }

            anomaliesTextArea.setCaretPosition(
                    anomaliesTextArea.getText().length());

            progressLabel.setText(getProgressString(fileIndex, files.length, dataModels));

            out.println("Data loaded.");

            return dataSet;
        }
        catch (Exception e1) {
            out.println(e1.getMessage());
            out.println();
            e1.printStackTrace(out);
        }

        TetradLogger.getInstance().removeOutputStream(out);
        TetradLogger.getInstance().setForceLog(false);

        return null;
    }
}


