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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.util.TextAreaOutputStream;
import edu.cmu.tetradapp.workbench.DisplayNodeUtils;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.prefs.Preferences;

/**
 * The area used to display log output.
 *
 * @author Tyler Gibson
 */
class TetradLogArea extends JPanel {


    /**
     * Where the actual logs are displayed.
     */
    private JTextArea textArea;


    /**
     * The output stream that is used to log to.
     */
    private TextAreaOutputStream stream;


    /**
     * The desktop
     */
    private TetradDesktop desktop;


    /**
     * Constructs the log area.
     */
    public TetradLogArea(TetradDesktop tetradDesktop) {
        super(new BorderLayout());
        if (tetradDesktop == null) {
            throw new NullPointerException("The given desktop must not be null");
        }
        this.desktop = tetradDesktop;

        // build the text area.
        this.textArea = new JTextArea();
        if (TetradLogger.getInstance().isDisplayLogEnabled()) {
            this.stream = new TextAreaOutputStream(this.textArea);
            TetradLogger.getInstance().addOutputStream(this.stream);
        }
        JScrollPane pane = new JScrollPane(this.textArea);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // finally add the components to the panel.
        add(createHeader(), BorderLayout.NORTH);
        add(pane, BorderLayout.CENTER);
    }

    //================================= Public methods =============================//


    /**
     * @return the output stream that is being used to log messages to the log area.
     */
    public OutputStream getOutputStream() {
        return this.stream;
    }

    //============================== Private Methods ============================//

    /**
     * Creates the header of the log display.
     */
    private JComponent createHeader() {
        JPanel panel = new JPanel();
        panel.setBackground(DisplayNodeUtils.getNodeFillColor());
        panel.setLayout(new BorderLayout());

        String path = TetradLogger.getInstance().getLatestFilePath();

        JLabel label = new JLabel(path == null ? "Logging to console (select Setup... from Logging menu)" : "  Logging to " + path);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBackground(DisplayNodeUtils.getNodeFillColor());
        label.setForeground(Color.WHITE);
        label.setOpaque(false);
        label.setBorder(new EmptyBorder(1, 2, 1, 2));

        Box b = Box.createHorizontalBox();

        b.add(label);
        b.add(Box.createHorizontalGlue());
        panel.add(b, BorderLayout.CENTER);

        return panel;
    }

    private JButton getStyledButton(String name, JLabel label) {
        final JButton stop = new JButton(name);
        stop.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        stop.setBackground(DisplayNodeUtils.getNodeFillColor());
        stop.setForeground(Color.WHITE);
        stop.setOpaque(true);
        stop.setBorder(new CompoundBorder(
                new EmptyBorder(2, 1, 2, 1), new MatteBorder(1, 1, 1, 1, Color.WHITE)
        ));
        return stop;
    }


    /**
     * The component used to config logging.
     */
    public static JPanel buildSetupLoggingComponent() {


        // build yes/no combo box.
        JComboBox activateCombo = new JComboBox(new String[]{"No", "Yes"});
        activateCombo.setMaximumSize(activateCombo.getPreferredSize());
        activateCombo.setSelectedItem(TetradLogger.getInstance().isLogging() ? "Yes" : "No");
        activateCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox combo = (JComboBox) e.getSource();
                TetradLogger.getInstance().setLogging("Yes".equals(combo.getSelectedItem()));
            }
        });

        String saveLocation = TetradLogger.getInstance().getLoggingDirectory();
        final JTextField saveField = new JTextField(saveLocation);
        saveField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JTextField field = (JTextField) e.getSource();
                try {
                    TetradLogger.getInstance().setLoggingDirectory(field.getText());
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), ex.getMessage());
                }
            }
        });

        JButton chooseButton = new JButton(" ... ");
        chooseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String saveLocation = TetradLogger.getInstance().getLoggingDirectory();

                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setCurrentDirectory(new File(saveLocation));

                int ret = chooser.showOpenDialog(JOptionUtils.centeringComp());

                if (ret == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = chooser.getSelectedFile();
                    Preferences.userRoot().put("loggingDirectory", selectedFile.getAbsolutePath());
                    saveField.setText(selectedFile.getAbsolutePath());
                }
            }
        });

        chooseButton.setBorder(new EtchedBorder());
        JTextField prefixField = new JTextField(TetradLogger.getInstance().getLoggingFilePrefix());

        prefixField.addCaretListener(new CaretListener() {
            public void caretUpdate(CaretEvent e) {
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
            }
        });

        // Do Layout.
        Box b1 = Box.createVerticalBox();

        b1.add(createLogToBox());
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
                "given file prefix, in the given directory." +
                "</html>"));
        b1.add(b8);

        JPanel panel = new JPanel();
        panel.add(b1, BorderLayout.CENTER);
        return panel;
    }

    //========================= Private Methods =================================//


    /**
     * Builds the output selection boxes.
     */
    private static Box createLogToBox() {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Log output to: "));
        box.add(Box.createHorizontalStrut(5));

        JCheckBox fileCheckBox = new JCheckBox(" File ");
        fileCheckBox.setSelected(TetradLogger.getInstance().isFileLoggingEnabled());
        fileCheckBox.setHorizontalTextPosition(AbstractButton.LEFT);
        fileCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                TetradLogger.getInstance().setFileLoggingEnabled(box.isSelected());
            }
        });

        JCheckBox textareaCheckBox = new JCheckBox(" Log Display ");
        textareaCheckBox.setSelected(TetradLogger.getInstance().isDisplayLogEnabled());
        textareaCheckBox.setHorizontalTextPosition(AbstractButton.LEFT);
        textareaCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                TetradLogger.getInstance().setDisplayLogEnabled(box.isSelected());
            }
        });

        box.add(fileCheckBox);
        box.add(textareaCheckBox);
        box.add(Box.createHorizontalGlue());

        return box;
    }

    private JPanel viewLogsInstructions() {
        JPanel panel = new JPanel();
        JLabel label1 = new JLabel(
                "<html>The best advise for viewing your logs is to go to the log output directory" +
                        "<br>and open them using a text editor, like WordPad, or Emacs, or gEdit, that" +
                        "<br>can handle large text files. Your output directory is..." +
                        "</html>"
        );

        JTextField field = new JTextField(TetradLogger.getInstance().getLoggingDirectory());
        field.setMaximumSize(new Dimension(500, 20));
        field.setEditable(false);
        field.setBackground(Color.WHITE);

        JLabel label = new JLabel("<html>Higher numbered files are more recent.</html>");

        Box b = Box.createVerticalBox();
        b.add(label1);
        b.add(Box.createVerticalStrut(10));

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createHorizontalGlue());
        b2.add(field);
        b2.add(Box.createHorizontalGlue());
        b.add(b2);
        b.add(Box.createVerticalStrut(10));
        b.add(label);

        panel.add(b, BorderLayout.CENTER);
        return panel;

        // The below is a nice idea for a text display but chokes on large files (by overwriting lines a little off
        // so you can't read them). Java 1.5.
//        StringBuilder buf = new StringBuilder();
//
//        try {
//            FileInputStream in = new FileInputStream(file);
//            int _char;
//
//            while ((_char = in.read()) != -1) {
//                buf.append((char) _char);
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//
//        final JTextArea textArea = new JTextArea(buf.toString());
//        JScrollPane pane = new JScrollPane(textArea);
//        pane.setPreferredSize(new Dimension(500, 600));
//
//        JPanel panel = new JPanel();
//        panel.add(pane, BorderLayout.CENTER);
//        return panel;
    }


    /**
     * Writes whatever is in the log display to the given file. Will display error messages if
     * any exceptions are thrown.
     */
    private void writeLogToFile(File file) {
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(this.textArea.getText());
            writer.close();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(desktop, "Error while trying to write to the selected file.");
        }
    }


}



