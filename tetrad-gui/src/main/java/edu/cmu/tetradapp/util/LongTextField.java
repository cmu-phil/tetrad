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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * A text field which is specialized for displaying longs. Handles otherwise annoying GUI-related functions like keeping
 * the textbox the right size and listening to itself. A filter may be specified as a way of, e.g., forcing variables to
 * be within a certain range; see the <code>setFilter</code> method.
 *
 * @author Kevin Bui
 * @version $Id: $Id
 */
public final class LongTextField extends JTextField {

    /**
     * The getModel value of the text field.
     */
    private long value;

    /**
     * If set, checks whether the given value should be accepted; otherwise, the old value will be reinstated. May be
     * null.
     */
    private Filter filter;

    /**
     * Constructs a new long text field displaying the given default value, restricting the value to [lowerBound,
     * upperBound].
     *
     * @param value The initial value. Must be between lowerBound and upperBound.
     * @param size  the number of columns in the textfield.
     */
    public LongTextField(long value, int size) {
        super(size);

        setValue(value);
        setText(Long.toString(value));

        setHorizontalAlignment(SwingConstants.RIGHT);
        addActionListener(new ActionListener() {

            /**
             * Reacts to somebody pressing the return key in this field by
             * attempting to set the value displayed. If the value displayed
             * cannot be set, the set value is reinstated.
             */
            public void actionPerformed(ActionEvent e) {
                try {
                    long n = Long.parseLong(e.getActionCommand());
                    setValue(n);
                } catch (NumberFormatException e1) {
                    setText(Long.toString(getValue()));
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            /**
             * Nothing need be done when focus is gained, but this method is
             * required by the FocusListener interface.
             *
             * @param e the event.
             */
            public void focusGained(FocusEvent e) {
                LongTextField source = (LongTextField) e.getSource();
                source.selectAll();
            }

            /**
             * If focus is lost, attempt to store the text value being displayed
             * as long; if this cannot be done, restore the previous value.
             */
            public void focusLost(FocusEvent e) {
                try {
                    long n = Long.parseLong(getText());
                    setValue(n);
                } catch (NumberFormatException e1) {
                    setText(Long.toString(getValue()));
                }
            }
        });
    }

    /**
     * <p>setUnfilteredValue.</p>
     *
     * @param value a long
     */
    public void setUnfilteredValue(long value) {
        setText(String.valueOf(value));
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return the long value currently displayed.
     */
    public long getValue() {
        return this.value;
    }

    /**
     * Sets the value of the text field to the given long value.
     *
     * @param value a long
     */
    public void setValue(long value) {
        if (value == this.value) {
            return;
        }

        long newValue = filter(value, this.value);

        if (newValue == this.value) {
            setText(Long.toString(this.value));
        } else {
            this.value = newValue;
            setText(Long.toString(this.value));
            firePropertyChange("newValue", null, this.value);
        }
    }

    /**
     * Sets whether the given value should be accepted.
     *
     * @param filter a {@link edu.cmu.tetradapp.util.LongTextField.Filter} object
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

    /**
     * Determines whether the given value is a legal value for this text field. The default behavior is to constrain the
     * value to be within a certain range--in other words, in the range [lower bound, upper bound]. For any other
     * behavior, this method should be overridden. This method is called by default by the setLabel() method; it may
     * become irrelevant if setLabel() is overridden in a way that doesn't make a call to checkValue().
     */
    private long filter(long value, long oldValue) {
        if (this.filter == null) {
            return value;
        }

        return this.filter.filter(value, oldValue);
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
        long filter(long value, long oldValue);
    }
}
