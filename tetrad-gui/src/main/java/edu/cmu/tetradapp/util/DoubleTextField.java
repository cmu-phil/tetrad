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

import org.apache.commons.math3.util.FastMath;

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
public class DoubleTextField extends JTextField {

    /**
     * The getModel value of the text field.
     */
    private double value;

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
     * @param value  the initial value to be displayed.
     * @param width  the width (in characters) of the text field.
     * @param format the number formatter, for example new Decimal("0.0000").
     */
    public DoubleTextField(double value, int width, NumberFormat format) {
        super(width);
        setup(value, format, format, 1e-4);
    }

    /**
     * <p>Constructor for DoubleTextField.</p>
     *
     * @param value             a double
     * @param width             a int
     * @param format            a {@link java.text.NumberFormat} object
     * @param smallNumberFormat a {@link java.text.NumberFormat} object
     * @param smallNumberCutoff a double
     */
    public DoubleTextField(double value, int width, NumberFormat format, NumberFormat smallNumberFormat,
                           double smallNumberCutoff) {
        super(width);
        setup(value, format, smallNumberFormat, smallNumberCutoff);
    }

    /**
     * Accesses the double value currently displayed.
     *
     * @return the getModel value.
     */
    public double getValue() {
        return this.value;
    }

    /**
     * Sets the value of the text field to the given double value. Should be overridden for more specific behavior.
     *
     * @param value the value to be set.
     */
    public void setValue(double value) {
        if (value == this.value) {
            return;
        }

        double newValue = filter(value, this.value);

        if (newValue == this.value) {
            smartSetText(this.format, this.value);
        } else {
            this.value = newValue;
            smartSetText(this.format, this.value);
            firePropertyChange("newValue", null, this.value);
        }
    }

    /**
     * Sets whether the given value should be accepted.
     *
     * @param filter a {@link edu.cmu.tetradapp.util.DoubleTextField.Filter} object
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

    private double filter(double value, double oldValue) {
        if (this.filter == null) {
            return value;
        }

        return this.filter.filter(value, oldValue);
    }

    private void setup(double value, NumberFormat nf, NumberFormat smallNumberFormat, double smallNumberCutoff) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.value = filter(value, 0.0);
        this.format = nf;
        this.smallNumberFormat = smallNumberFormat;
        this.smallNumberCutoff = smallNumberCutoff;
        smartSetText(nf, this.value);

        addActionListener(e -> {
            try {
                double value1 = Double.parseDouble(e.getActionCommand());
                setValue(value1);
            } catch (NumberFormatException e1) {
                setText(DoubleTextField.this.format.format(getValue()));
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                DoubleTextField source = (DoubleTextField) e.getSource();

                if (source.isEditable()) {
                    source.selectAll();
                }
            }

            public void focusLost(FocusEvent e) {
                try {
                    double value = Double.parseDouble(getText());
                    setValue(value);
                } catch (NumberFormatException e1) {
                    if ("".equals(getText().trim())) {
                        setValue(Double.NaN);
                    } else {
                        setValue(getValue());
                    }
                }
            }
        });
    }

    private void smartSetText(NumberFormat nf, double value) {
        if (Double.isNaN(value)) {
            setHorizontalAlignment(SwingConstants.RIGHT);
            setText("");
        } else {
            setHorizontalAlignment(SwingConstants.RIGHT);

            if (FastMath.abs(value) < this.smallNumberCutoff && value != 0.0) {
                setText(this.smallNumberFormat.format(value));
            } else {
                setText(nf.format(value));
            }
        }
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
        double filter(double value, double oldValue);
    }
}



