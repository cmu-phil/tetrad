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

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits a list of parameters given their defaults.
 *
 * @author Joseph Ramsey
 */
public class ParameterPanel extends JPanel {
    java.util.List<JPanel> editors = new ArrayList<>();

    public ParameterPanel(Parameters parameters, List<String> displayParams, List<Object> defaults) {
        Box b = Box.createVerticalBox();
        b.setBorder(new TitledBorder("Edit Parameters:"));

        for (int i = 0; i < displayParams.size(); i++) {
            String parameter = displayParams.get(i);
            Object defaultValue = defaults.get(i);
            JPanel p;

            if (defaultValue instanceof Double) {
                p = new DoublePanel(parameters, parameter, ((Double) defaultValue).doubleValue());
            } else if (defaultValue instanceof Integer) {
                p = new IntPanel(parameters, parameter, ((Integer) defaultValue).intValue());
            } else if (defaultValue instanceof Boolean) {
                p = new BooleanPanel(parameters, parameter, ((Boolean) defaultValue).booleanValue());
            } else {
                throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
            }

            editors.add(p);

            Box c = Box.createHorizontalBox();
            c.add(new JLabel(parameter));
            c.add(Box.createVerticalGlue());
            c.add(p);
            b.add(c);
        }

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    private class DoublePanel extends JPanel {
        public DoublePanel(final Parameters parameters, final String parameter, double defaultValue) {
            final DoubleTextField field = new DoubleTextField(parameters.getDouble(parameter, defaultValue),
                    8, new DecimalFormat(("0.0000")));

            field.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    if (value == field.getValue()) {
                        return oldValue;
                    }

                    try {
                        parameters.set(parameter, value);
                    } catch (Exception e) {
                        // Ignore.
                    }

                    return value;
                }
            });
        }
    }

    private class IntPanel extends JPanel {
        private Parameters parameters;
        private String parameter;

        public IntPanel(final Parameters parameters, final String parameter, int defaultValue) {
            this.parameters = parameters;
            this.parameter = parameter;

            final IntTextField field = new IntTextField(parameters.getInt(parameter, defaultValue), 4);

            field.setFilter(new IntTextField.Filter() {
                public int filter(int value, int oldValue) {
                    if (value == field.getValue()) {
                        return oldValue;
                    }

                    try {
                        parameters.set(parameter, value);
                    } catch (Exception e) {
                        // Ignore.
                    }

                    return value;
                }
            });
        }
    }

    private class BooleanPanel extends JPanel {
        private Parameters parameters;
        private String parameter;

        public BooleanPanel(final Parameters parameters, final String parameter, boolean defaultValue) {
            this.parameters = parameters;
            this.parameter = parameter;

            JComboBox connectedBox = new JComboBox(new String[]{"No", "Yes"});

            if (parameters.getBoolean(parameter, defaultValue)) {
                connectedBox.setSelectedItem("Yes");
            } else {
                connectedBox.setSelectedItem("No");
            }

            connectedBox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (((JComboBox) e.getSource()).getSelectedItem().equals("YES")) {
                        parameters.set(parameter, true);
                    } else {
                        parameters.set(parameter, false);
                    }
                }
            });
        }
    }
}





