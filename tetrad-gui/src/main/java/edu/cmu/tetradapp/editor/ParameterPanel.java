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

import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Edits a list of parameters. Descriptions and defaults are looked up in ParamDescriptions.
 *
 * @author Joseph Ramsey
 */
public class ParameterPanel extends JPanel {
    public ParameterPanel(List<String> parametersToEdit, Parameters parameters) {
        Box a = Box.createHorizontalBox();
        Box b = Box.createVerticalBox();
        a.add(b);
//        a.add(Box.createHorizontalGlue());

        Box d = Box.createHorizontalBox();
        JLabel label = new JLabel("EDIT PARAMETERS:");
        label.setFont(new Font("Dialog", Font.BOLD, 12));
        d.add(label);
        d.add(Box.createHorizontalGlue());
        b.add(d);

        for (String parameter : parametersToEdit) {
            Object defaultValue = ParamDescriptions.instance().get(parameter).getDefaultValue();
            JComponent p;

            if (defaultValue instanceof Double) {
                p = getDoubleField(parameter, parameters, (Double) defaultValue);
            } else if (defaultValue instanceof Integer) {
                p = getIntTextField(parameter, parameters, (Integer) defaultValue);
            } else if (defaultValue instanceof Boolean) {
                p = getBooleanBox(parameter, parameters, (Boolean) defaultValue);
            } else if (defaultValue instanceof String) {
                p = getStringField(parameter, parameters, (String) defaultValue);
            } else {
                throw new IllegalArgumentException("Unexpected type: " + defaultValue.getClass());
            }

            Box c = Box.createHorizontalBox();
            c.add(new JLabel(ParamDescriptions.instance().get(parameter).getDescription()));
            c.add(Box.createHorizontalGlue());
            c.add(p);
            b.add(c);
        }

        b.add(Box.createVerticalGlue());
        setLayout(new BorderLayout());
        add(a, BorderLayout.CENTER);
    }

    private DoubleTextField getDoubleField(final String parameter, final Parameters parameters, double defaultValue) {
        final DoubleTextField field = new DoubleTextField(parameters.getDouble(parameter, defaultValue),
                8, new DecimalFormat("0.####"), new DecimalFormat("0.0#E0"), 0.001);

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

        return field;
    }

    private IntTextField getIntTextField(final String parameter, final Parameters parameters, int defaultValue) {
        final IntTextField field = new IntTextField(parameters.getInt(parameter, defaultValue), 8);

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

        return field;
    }

    private JComboBox getBooleanBox(final String parameter, final Parameters parameters, boolean defaultValue) {
        JComboBox<String> box = new JComboBox<>(new String[]{"Yes", "No"});

        boolean aBoolean = parameters.getBoolean(parameter, defaultValue);

        System.out.println(parameter + " = " + aBoolean);

        if (aBoolean) {
            box.setSelectedItem("Yes");
        } else {
            box.setSelectedItem("No");
        }

        box.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (((JComboBox) e.getSource()).getSelectedItem().equals("Yes")) {
                    parameters.set(parameter, true);
                } else {
                    parameters.set(parameter, false);
                }
            }
        });

        box.setMaximumSize(box.getPreferredSize());

        return box;
    }

    private StringTextField getStringField(final String parameter, final Parameters parameters, String defaultValue) {
        final StringTextField field = new StringTextField(parameters.getString(parameter, defaultValue), 20);

        field.setFilter(new StringTextField.Filter() {
            public String filter(String value, String oldValue) {
                if (value.equals(field.getValue().trim())) {
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

        return field;
    }
}





