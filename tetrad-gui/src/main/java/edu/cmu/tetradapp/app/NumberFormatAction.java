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
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.prefs.Preferences;

/**
 * Presents a dialog allowing the user to change the number format used to
 * render real numbers throughout Tetrad.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
final class NumberFormatAction extends AbstractAction
        implements ClipboardOwner {

    /**
     * This is a class field because it's accessed from multiple places.
     */
    private JTextField formatField = new JTextField(
            Preferences.userRoot().get("numberFormat", "0.0000"));

    //========================CONSTRUCTOR=============================//

    /**
     * Constructs a new number format action.
     */
    public NumberFormatAction() {
        super("Number Format");
    }

    //===========================PUBLIC METHODS========================//

    /**
     * Pops up a dialog that lets the user decide how to render real numbers.
     * A basic and an advanced version are available.
     */
    public void actionPerformed(ActionEvent e) {

        // Set up basic tab.
        final double sample = 23.5;
        final JTextField renderFieldBasic = new JTextField(
                new DecimalFormat(constructSimpleFormatString()).format(sample));
        renderFieldBasic.setMaximumSize(new Dimension(150, 50));
        renderFieldBasic.setEditable(false);
        renderFieldBasic.setBackground(Color.WHITE);

        JCheckBox scientific = new JCheckBox(
                "Use scientific notation",
                Preferences.userRoot().getBoolean("scientificNotation", false));

        scientific.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                Preferences.userRoot().putBoolean("scientificNotation",
                        checkBox.isSelected());
                renderFieldBasic.setText(new DecimalFormat(constructSimpleFormatString()).format(sample));
                formatField.setText(constructSimpleFormatString());
            }
        });

        SpinnerModel model = new SpinnerNumberModel(
            Preferences.userRoot().getInt("numDecimals", 4), 0, 300, 1);
        JSpinner numDecimals = new JSpinner(model);

        numDecimals.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JSpinner spinner = (JSpinner) e.getSource();
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                int value = (Integer) model.getValue();

                Preferences.userRoot().putInt("numDecimals", value);
                renderFieldBasic.setText(new DecimalFormat(constructSimpleFormatString()).format(sample));
                formatField.setText(constructSimpleFormatString());
            }
        });

        numDecimals.setMaximumSize(numDecimals.getPreferredSize());
        boolean decimalsOptional = Preferences.userRoot().getBoolean("decimalsOptimal", false);

        JRadioButton optional = new JRadioButton("Optional", decimalsOptional);
        JRadioButton fixed = new JRadioButton("Fixed", !decimalsOptional);

        ButtonGroup group = new ButtonGroup();
        group.add(optional);
        group.add(fixed);

        optional.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Preferences.userRoot().putBoolean("decimalsOptional", true);
                renderFieldBasic.setText(new DecimalFormat(constructSimpleFormatString()).format(sample));
                formatField.setText(constructSimpleFormatString());
            }
        });

        fixed.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Preferences.userRoot().putBoolean("decimalsOptional", false);
                renderFieldBasic.setText(new DecimalFormat(constructSimpleFormatString()).format(sample));
                formatField.setText(constructSimpleFormatString());
            }
        });

        if (!Preferences.userRoot().getBoolean("numFormatAdvanced", false)) {
            formatField.setText(constructSimpleFormatString());
        }

        // Set up basic panel.
        Box a = Box.createVerticalBox();
        a.setBorder(new TitledBorder("Simple Formats"));

        Box a2 = Box.createHorizontalBox();
        a2.add(scientific);
        a2.add(Box.createHorizontalGlue());
        a.add(a2);

        Box a3 = Box.createHorizontalBox();
        a3.add(new JLabel("Number of decimal places = "));
        a3.add(numDecimals);
        a3.add(Box.createHorizontalGlue());
        a.add(a3);

        Box a4 = Box.createHorizontalBox();
        a4.add(Box.createRigidArea(new Dimension(20, 0)));
        a4.add(optional);
        a4.add(Box.createHorizontalGlue());
        a.add(a4);

        Box a5 = Box.createHorizontalBox();
        a5.add(Box.createRigidArea(new Dimension(20, 0)));
        a5.add(fixed);
        a5.add(Box.createHorizontalGlue());
        a.add(a5);
        a.add(Box.createVerticalStrut(20));

        Box a6 = Box.createHorizontalBox();
        a6.add(new JLabel("Renders as: "));
        a6.add(renderFieldBasic);
        a.add(a6);

        a.add(Box.createVerticalGlue());

        Box basic = Box.createVerticalBox();
        basic.add(a);

        final JPanel basicPanel = new JPanel();
        basicPanel.setLayout(new BorderLayout());
        basicPanel.add(basic, BorderLayout.CENTER);

        // Set up advanced panel.
        final JTextField sampleFieldAdvanced = new JTextField("" + sample);
        final JTextField renderFieldAdvanced = new JTextField(getNumberFormat().format(sample));
        renderFieldAdvanced.setEditable(false);
        renderFieldAdvanced.setBackground(Color.WHITE);

        formatField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                updateAdvancedFields(sampleFieldAdvanced, renderFieldAdvanced);
            }
        });

        sampleFieldAdvanced.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                updateAdvancedFields(sampleFieldAdvanced, renderFieldAdvanced);
            }
        });

        Box z = Box.createVerticalBox();

        Box z1 = Box.createHorizontalBox();
        z1.add(new JLabel("Refer to DecimalFormat in the "));
        z1.add(Box.createHorizontalGlue());
        z.add(z1);

        Box z2 = Box.createHorizontalBox();
        z2.add(new JLabel("documentation for Java."));
        z2.add(Box.createHorizontalGlue());
        z.add(z2);
        z.add(Box.createVerticalStrut(10));

        Box advanced = Box.createVerticalBox();
        advanced.add(z);

        Box f = Box.createVerticalBox();
        f.setBorder(new TitledBorder("Format String"));

        Box f1 = Box.createHorizontalBox();
        f1.add(formatField);
        f.add(f1);

        advanced.add(f);

        Box c = Box.createVerticalBox();
        c.setBorder(new TitledBorder("Example"));

        Box c1 = Box.createHorizontalBox();
        c1.add(sampleFieldAdvanced);
        c.add(c1);

        Box c2 = Box.createHorizontalBox();
        c2.add(new JLabel("Renders as: "));
        c2.add(Box.createHorizontalGlue());
        c.add(c2);

        Box c3 = Box.createHorizontalBox();
        c3.add(renderFieldAdvanced);
        c.add(c3);

        advanced.add(c);

        final JPanel advancedPanel = new JPanel();
        advancedPanel.setLayout(new BorderLayout());
        advancedPanel.add(advanced, BorderLayout.CENTER);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Basic", basicPanel);
        tabbedPane.addTab("Advanced", advancedPanel);

        if (Preferences.userRoot().getBoolean("numFormatAdvanced", false)) {
            tabbedPane.setSelectedComponent(advancedPanel);
        }

        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JTabbedPane tabbedPane = (JTabbedPane) e.getSource();
                JPanel panel = (JPanel) tabbedPane.getSelectedComponent();
                Preferences.userRoot().putBoolean("numFormatAdvanced", panel == advancedPanel);

                if (panel == basicPanel) {
                    String format = constructSimpleFormatString();
                    formatField.setText(format);
                    renderFieldBasic.setText(new DecimalFormat(format).format(sample));
                }
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(tabbedPane, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                panel, "Formatting for All Real Numbers", JOptionPane.INFORMATION_MESSAGE);

        NumberFormatUtil.getInstance().setNumberFormat(getNumberFormat());
    }

    /**
     * @return the most recent number format, whether from the basic or from
     * the advanced tab.
     * @throws RuntimeException if the getModel format string cannot be
     * accepted by DecimalFormat.
     */
    public NumberFormat getNumberFormat() throws IllegalStateException {
        return new DecimalFormat(getFormatString());
    }

    /**
     * @return the most recent format string, whether from the basic or from
     * the advanced tab.
     * @throws RuntimeException if the stored format string cannot be
     * accepted by DecimalFormat.
     */
    public String getFormatString() throws RuntimeException {
        String format = formatField.getText();

        try {
            new DecimalFormat(format);
        } catch (Exception e) {
            throw new RuntimeException("Illegal format string: " + format);
        }

        return format;
    }

    /**
     * Required by the AbstractAction interface; does nothing.
     */
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

    //============================PRIVATE METHODS=====================//

    /**
     * This sets up the behavior in the advanced tab where when you change
     * the format or the example numnber, it updates the rendering. It also
     * sets the color of the format or example to red when it's ill-formatted.
     * @param sampleFieldAdvanced  The example field.
     * @param renderFieldAdvanced  The render field.
     */
    private void updateAdvancedFields(JTextField sampleFieldAdvanced, JTextField renderFieldAdvanced) {
        try {
            Double.parseDouble(sampleFieldAdvanced.getText());
        } catch (Exception e1) {
            sampleFieldAdvanced.setForeground(Color.RED);
            return;
        }

        String format = formatField.getText();

        try {
            new DecimalFormat(format);
        } catch (Exception e2) {
            formatField.setForeground(Color.RED);
            return;
        }

        double sample = Double.parseDouble(sampleFieldAdvanced.getText());
        NumberFormat nf = new DecimalFormat(format);
        renderFieldAdvanced.setText(nf.format(sample));
        sampleFieldAdvanced.setForeground(Color.BLACK);
        formatField.setForeground(Color.BLACK);
        Preferences.userRoot().put("numberFormat", format);
    }

    private String constructSimpleFormatString() {
        boolean scientificNotation = Preferences.userRoot()
                .getBoolean("scientificNotation", false);
        int numDecimals = Preferences.userRoot()
                .getInt("numDecimals", 4);
        boolean optional = Preferences.userRoot()
                .getBoolean("decimalsOptional", false);

        StringBuilder buf = new StringBuilder();
        buf.append("0.");

        if (optional) {
            for (int i = 0; i < numDecimals; i++) {
                buf.append("#");
            }
        } else {
            for (int i = 0; i < numDecimals; i++) {
                buf.append("0");
            }
        }

        if (scientificNotation) {
            buf.append("E0");
        }

        String formatString = buf.toString();
//        formatField.setText(formatString);
        Preferences.userRoot().put("numberFormat", formatString);
        return formatString;
    }
}



