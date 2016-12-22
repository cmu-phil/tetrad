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
import edu.cmu.tetradapp.util.WatchedProcess;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

/**
 * Panel (to be put in a dialog) for letting the user choose how a data file
 * should be loaded.
 *
 * @author Joseph Ramsey
 */
final class LoadDataDialog extends JPanel {

    private final RegularDataPanel dataParams;
    private transient DataModel[] dataModels;

    private int fileIndex = 0;

    //================================CONSTRUCTOR=======================//
    public LoadDataDialog(final File... files) {
        // Do we need this? Since you can't get to this data loader dialog if not file is selected - Zhou
        if (files.length == 0) {
            throw new IllegalArgumentException("Must specify at least one file.");
        }

        this.dataModels = new DataModel[files.length];

        final JTabbedPane tabbedPane = new JTabbedPane();

        final JTextArea anomaliesTextArea = new JTextArea();

        // Each tab is a preview of a file - Zhou
        for (File file : files) {
            final JTextArea fileTextArea = new JTextArea();

            // Setup file text area.
            // Do we want the users to edit in the preview area? - Zhou
            // fileTextArea.setEditable(false);
            fileTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            setText(file, fileTextArea);

            final JScrollPane scroll = new JScrollPane(fileTextArea);
            scroll.setPreferredSize(new Dimension(500, 200));
            tabbedPane.addTab(file.getName(), scroll);
        }

        String loadBtnText = "Load";
        if (files.length > 1) {
            loadBtnText = "Load All";
        }
        JButton loadButton = new JButton(loadBtnText);

        // Layout.
        dataParams = new RegularDataPanel(files);

        Box c = Box.createVerticalBox();

        Box c1 = Box.createHorizontalBox();

        c1.add(tabbedPane);
        c.add(c1);

        Box c2 = Box.createHorizontalBox();
        c2.add(Box.createHorizontalGlue());

        c2.add(Box.createHorizontalStrut(10));

        c2.setBorder(new EmptyBorder(4, 4, 4, 4));
        c.add(c2);
        c.setBorder(new TitledBorder("Data File Preview"));

        Box a = Box.createHorizontalBox();
        // The data loading params apply to all slected files
        // the users should know that the selected files should share these settings - Zhou
        a.add(dataParams);
        a.add(c);
        a.add(loadButton);
        setLayout(new BorderLayout());

        Box d = Box.createHorizontalBox();

        d.add(Box.createHorizontalGlue());

        Box e = Box.createVerticalBox();
        e.add(d);
        e.add(Box.createVerticalStrut(10));
        e.add(a);

        add(e, BorderLayout.CENTER);

        // Listeners.
        loadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        Window owner = (Window) getTopLevelAncestor();

                        new WatchedProcess(owner) {
                            public void watch() {
                                for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
                                    loadDataSelect(fileIndex, anomaliesTextArea, files);
                                }
                            }
                        };
                    }
                };
            }
        });

    }

    private void loadDataSelect(int fileIndex, JTextArea anomaliesTextArea, File[] files) {
        System.out.println("File index = " + fileIndex);

        DataModel dataModel = dataParams.loadData(fileIndex, anomaliesTextArea, files);
        if (dataModel == null) {
            throw new NullPointerException("Data not loaded.");
        }
        addDataModel(dataModel, fileIndex, files[fileIndex].getName());
    }

    //==============================PUBLIC METHODS=========================//
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
        } catch (IOException e) {
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
    private void addDataModel(DataModel dataModel, int index, String name) {
        if (dataModel == null) {
            throw new NullPointerException();
        }

        dataModel.setName(name);
        this.dataModels[index] = dataModel;
    }

}
