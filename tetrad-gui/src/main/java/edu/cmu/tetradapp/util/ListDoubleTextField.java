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

package edu.cmu.tetradapp.util;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.NumberFormat;


/**
 * A text field which is specialized for displaying and editing doubles. Handles otherwise annoying GUI-related
 * functions like keeping the textbox the right size and listening to itself.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ListDoubleTextField extends JTextField {

    /**
     * The getModel value of the text field.
     */
    private Double[] values;

    /**
     * The number formatter for the number displayed.
     */
    private NumberFormat format;

    /**
     * The number formatter for the number displayed (for small numbers).
     */
    private NumberFormat smallNumberFormat;

    /**
     * The cutoff below which (in absolute value) values will be displayed using the small number format.
     */
    private double smallNumberCutoff = 1e-4;

    /**
     * If set, filters the value input by the user. (Side effects are allowed.)
     */
    private Filter filter;

    /**
     * Constructs a new text field to display double values and allow them to be edited. The initial value and character
     * width of the text field can be specified, along with the format with which the numbers should be displayed. To
     * accept only certain values, set a value filter using the
     * <code>setValueChecker</code> method.
     *
     * @param values the initial values to be displayed.
     * @param width  the width (in characters) of the text field.
     * @param format the number formatter, for example new Decimal("0.0000").
     */
    public ListDoubleTextField(Double[] values, int width, NumberFormat format) {
        super(width);
        setup(values, format, format, 1e-4);
    }

    /**
     * <p>Constructor for DoubleTextField.</p>
     *
     * @param values            a Double[] array
     * @param width             a int
     * @param format            a {@link NumberFormat} object
     * @param smallNumberFormat a {@link NumberFormat} object
     * @param smallNumberCutoff a double
     */
    public ListDoubleTextField(Double[] values, int width, NumberFormat format, NumberFormat smallNumberFormat,
                               double smallNumberCutoff) {
        super(width);
        setup(values, format, smallNumberFormat, smallNumberCutoff);
    }

    @NotNull
    private static Double[] getNumbers(String actionCommand) {
        String[] split = actionCommand.split(",");
        Double[] values1 = new Double[split.length];

        for (int i = 0; i < split.length; i++) {
            values1[i] = Double.parseDouble(split[i].trim());
        }
        return values1;
    }

    /**
     * Accesses the double value currently displayed.
     *
     * @return the getModel value.
     */
    public Double[] getValues() {
        return this.values;
    }

    /**
     * Sets the value of the text field to the given double value. Should be overridden for more specific behavior.
     *
     * @param values the values to be set.
     */
    public void setValues(Double[] values) {
        if (values == this.values) {
            return;
        }

        Double[] newValues = filter(values, this.values);

        // check if the values are the same
        if (newValues.length == this.values.length) {
            boolean same = true;
            for (int i = 0; i < newValues.length; i++) {
                if (!newValues[i].equals(this.values[i])) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }

        this.values = newValues;
        smartSetText(this.format, this.values);
        firePropertyChange("newValue", null, this.values);
    }

    /**
     * Sets whether the given value should be accepted.
     *
     * @param filter a {@link ListDoubleTextField.Filter} object
     */
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /**
     * Convinces the text field to stay the right size in layouts that are trying to expand it like a balloon by
     * returning the preferred size.
     *
     * @return the maximum size.
     */
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /**
     * Convinces the text field to stay the right size in layouts that are trying to shrink it.
     *
     * @return the maximum size.
     */
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    private Double[] filter(Double[] values, Double[] oldValues) {
        if (this.filter == null) {
            return values;
        }

        return this.filter.filter(values, oldValues);
    }

    private void setup(Double[] values, NumberFormat nf, NumberFormat smallNumberFormat, double smallNumberCutoff) {
        if (nf == null) {
            throw new NullPointerException();
        }

        Double _default = Double.NaN;
        Double[] defaultValues = new Double[values.length];
        for (int i = 0; i < values.length; i++) {
            defaultValues[i] = _default;
        }

        this.values = filter(values, defaultValues);
        this.format = nf;
        this.smallNumberFormat = smallNumberFormat;
        this.smallNumberCutoff = smallNumberCutoff;
        smartSetText(nf, this.values);

        addActionListener(e -> {
            try {
                String actionCommand = e.getActionCommand();
                Double[] values1 = getNumbers(actionCommand);
                setValues(values1);
            } catch (NumberFormatException e1) {
                setText(ListDoubleTextField.this.format.format(getValues()));
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                ListDoubleTextField source = (ListDoubleTextField) e.getSource();

                if (source.isEditable()) {
                    source.selectAll();
                }
            }

            public void focusLost(FocusEvent e) {
                try {
                    Double[] values1 = getNumbers(getText());
                    setValues(values1);
                } catch (NumberFormatException e1) {
                    if (getText().trim().isEmpty()) {
                        setValues(new Double[]{});
                    } else {
                        setValues(getValues());
                    }
                }
            }
        });
    }

    private void smartSetText(NumberFormat nf, Number[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(nf.format(values[i]));
        }

        setText(sb.toString());


//        if (Double.isNaN(values)) {
//            setHorizontalAlignment(SwingConstants.RIGHT);
//            setText("");
//        } else {
//            setHorizontalAlignment(SwingConstants.RIGHT);
//
//            if (FastMath.abs(value) < this.smallNumberCutoff && value != 0.0) {
//                setText(this.smallNumberFormat.format(value));
//            } else {
//                setText(nf.format(value));
//            }
//        }
    }

    /**
     * Filters the given value, returning the value that should actually be displayed. Typical use is to return either
     * the value or the old value, depending on whether the value is in range, though more complicated uses are
     * permitted. Side effects (such as storing the value in the process of filtering it) are permitted.
     */
    public interface Filter {

        /**
         * Filters the given value, returning the new value that should be displayed.
         *
         * @param value    The value entered by the user.
         * @param oldValue The value previously displayed, in case it needs to be reverted to.
         * @return The value that should be displayed.
         */
        Double[] filter(Double[] value, Double[] oldValue);
    }
}



