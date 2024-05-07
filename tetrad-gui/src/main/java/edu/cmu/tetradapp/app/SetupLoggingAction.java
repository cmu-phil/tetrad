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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;

/**
 * Prompts and allows them to config general logging features.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class SetupLoggingAction extends AbstractAction {

    private static final long serialVersionUID = -3792483383186114713L;

    /**
     * <p>Constructor for SetupLoggingAction.</p>
     */
    public SetupLoggingAction() {
        super("Logging");
    }


    /**
     * The component used to config logging.
     */
    private static JComponent buildSetupLoggingComponent() {


        // build yes/no combo box.
        JComboBox activateCombo = new JComboBox(new String[]{"No", "Yes"});
        activateCombo.setMaximumSize(activateCombo.getPreferredSize());
        activateCombo.setSelectedItem(TetradLogger.getInstance().isLogging() ? "Yes" : "No");
        activateCombo.addActionListener(e -> {
            JComboBox combo = (JComboBox) e.getSource();
            TetradLogger.getInstance().setLogging("Yes".equals(combo.getSelectedItem()));
        });

        String saveLocation = TetradLogger.getInstance().getLoggingDirectory();
        JTextField saveField = new JTextField(saveLocation);
        saveField.addActionListener(e -> {
            JTextField field = (JTextField) e.getSource();
            try {
                TetradLogger.getInstance().setLoggingDirectory(field.getText());
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), ex.getMessage());
            }
        });

        JButton chooseButton = new JButton(" ... ");
        chooseButton.addActionListener(e -> {
            String saveLocation1 = TetradLogger.getInstance().getLoggingDirectory();

            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(new File(saveLocation1));

            int ret = chooser.showOpenDialog(JOptionUtils.centeringComp());

            if (ret == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                Preferences.userRoot().put("loggingDirectory", selectedFile.getAbsolutePath());
                saveField.setText(selectedFile.getAbsolutePath());
            }
        });

        chooseButton.setBorder(new EtchedBorder());
        JTextField prefixField = new JTextField(TetradLogger.getInstance().getLoggingFilePrefix());

        prefixField.addCaretListener(e -> {
            JTextField field = (JTextField) e.getSource();
            String text = field.getText();

            if (!text.matches("[a-zA-Z_]*")) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Spaces, numbers, and special characters (" +
                        "except underlines) in filenames will be " +
                        "ignored. You might want to delete them.",
                        "Friendly Detail Message",
                        JOptionPane.WARNING_MESSAGE);
            }

            TetradLogger.getInstance().setLoggingFilePrefix(text);
        });

//        JCheckBox automatic = new JCheckBox("Automatically display log output.");
//        Boolean b = TetradLogger.getInstance().isAutomaticLogDisplayEnabled();
//        automatic.setSelected(b != null && b);
//        automatic.setHorizontalTextPosition(AbstractButton.LEFT);
//        automatic.addActionListener(new ActionListener(){
//            public void actionPerformed(ActionEvent e) {
//                JCheckBox box = (JCheckBox)e.getSource();
//                TetradLogger.getInstance().setAutomaticLogDisplayEnabled(box.isSelected());
//            }
//        });
//        Box automaticBox = Box.createHorizontalBox();
//        automaticBox.add(automatic);
//        automaticBox.add(Box.createHorizontalGlue());

        // Do Layout.
        Box b1 = Box.createVerticalBox();

        b1.add(SetupLoggingAction.createLogToBox());
        b1.add(Box.createVerticalStrut(5));

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Output Directory:"));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);

        Box b5 = Box.createHorizontalBox();
        b5.add(saveField);
        b5.add(chooseButton);
        b1.add(b5);
        b1.add(Box.createVerticalStrut(5));

        Box b6 = Box.createHorizontalBox();
        b6.add(new JLabel("File Prefix:"));
        b6.add(Box.createHorizontalGlue());
        b1.add(b6);

        Box b7 = Box.createHorizontalBox();
        b7.add(prefixField);
        b1.add(b7);
        b1.add(Box.createVerticalStrut(5));

        Box b8 = Box.createHorizontalBox();
        b8.add(new JLabel("<html>" +
                          "Output will be written to sequentially numbered files, using the<br>" +
                          "given file prefix, in the given directory, with one output file<br>" +
                          "generated per search." + "</html>"));
        b1.add(b8);

        return b1;
    }

    /**
     * Builds the output selection boxes.
     */
    private static Box createLogToBox() {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Log output to: "));
        box.add(Box.createHorizontalStrut(5));

        JCheckBox fileCheckBox = new JCheckBox(" File ");
        fileCheckBox.setSelected(TetradLogger.getInstance().isFileLoggingEnabled());
        fileCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        fileCheckBox.addActionListener(e -> {
            JCheckBox box1 = (JCheckBox) e.getSource();
            TetradLogger.getInstance().setFileLoggingEnabled(box1.isSelected());
        });

        JCheckBox textareaCheckBox = new JCheckBox(" Log Display ");
        textareaCheckBox.setSelected(TetradLogger.getInstance().isDisplayLogEnabled());
        textareaCheckBox.setHorizontalTextPosition(SwingConstants.LEFT);
        textareaCheckBox.addActionListener(e -> {
            JCheckBox box12 = (JCheckBox) e.getSource();
            TetradLogger.getInstance().setDisplayLogEnabled(box12.isSelected());
        });

        box.add(fileCheckBox);
        box.add(textareaCheckBox);
        box.add(Box.createHorizontalGlue());

        return box;
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        JComponent comp = SetupLoggingAction.buildSetupLoggingComponent();
        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), comp,
                "File Logging Setup", JOptionPane.PLAIN_MESSAGE);
    }


}



