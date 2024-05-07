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
import java.util.ArrayList;

/**
 * A custom text field for displaying and editing a list of Long values.
 *
 * @author josephramsey
 */
public class ListLongTextField extends JTextField {

    /**
     * The list of Long values.
     */
    private Long[] values;

    /**
     * Filters the value input by the user.
     */
    private Filter filter;

    /**
     * Constructs a new text field to display int values and allow them to be edited. The initial value and character
     * width of the text field can be specified, along with the format with which the numbers should be displayed. To
     * accept only certain values, set a value filter using the
     * <code>setValueChecker</code> method.
     *
     * @param values the initial values to be displayed.
     * @param width  the width (in characters) of the text field.
     */
    public ListLongTextField(Long[] values, int width) {
        super(width);
        setup(values);
    }

    /**
     * Retrieves a list of numbers from the given action command.
     *
     * @param actionCommand the action command containing the numbers separated by commas
     * @return an array of Longs representing the numbers extracted from the action command
     */
    @NotNull
    private static Long[] getNumbers(String actionCommand) {
        String[] split = actionCommand.split(",");
        java.util.List<Long> values = new ArrayList<>();

        for (String s : split) {
            String string = s.trim();

            try {
                values.add(Long.parseLong(string));
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        return values.toArray(new Long[0]);
    }

    /**
     * Accesses the int values currently displayed.
     *
     * @return the getModel value.
     */
    public Long[] getValues() {
        return this.values;
    }

    /**
     * Sets the values for the object.
     *
     * @param values an array of Long values to be set
     */
    public void setValues(Long[] values) {
        Long[] newValues = filter(values, this.values);

        if (newValues.length > 0) {
            this.values = newValues;
        }

        smartSetText(this.values);
        firePropertyChange("newValue", null, this.values);
    }

    /**
     * Sets the filter for the ListLongTextField. The filter is used to determine which values should be displayed in the
     * text field.
     *
     * @param filter the filter to be set
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
     * Filters an array of Longs based on a provided filter.
     *
     * @param values    the array of Longs to be filtered
     * @param oldValues the previous array of Longs before applying the filter
     * @return the filtered array of Longs
     */
    private Long[] filter(Long[] values, Long[] oldValues) {
        if (this.filter == null) {
            return values;
        }

        return this.filter.filter(values, oldValues);
    }

    /**
     * Sets up the ListLongTextField with the given values.
     *
     * @param values an array of Longs representing the initial values
     */
    private void setup(Long[] values) {

        long _default = -99;
        Long[] defaultValues = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            defaultValues[i] = _default;
        }

        this.values = filter(values, defaultValues);
        setValues(this.values);

        addActionListener(e -> {
            Long[] values1 = getNumbers(getText());
            Long[] filtered = filter(values1, values);

            if (filtered.length > 0) {
                setValues(filtered);
            } else {
                setValues(values);
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                ListLongTextField source = (ListLongTextField) e.getSource();

                if (source.isEditable()) {
                    source.selectAll();
                }
            }

            public void focusLost(FocusEvent e) {
                Long[] values1 = getNumbers(getText());
                Long[] filtered = filter(values1, values);

                if (filtered.length > 0) {
                    setValues(filtered);
                } else {
                    setValues(values);
                }
            }
        });
    }

    /**
     * Sets the text of the component with the provided values. The values are converted to a comma-separated string and
     * appended to the component's text.
     *
     * @param values the values to set as text
     */
    private void smartSetText(Long[] values) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> valueStrings = new ArrayList<>();

        for (Long aLong : values) {
            if (aLong != null) {
                long value = aLong;
                valueStrings.add(Long.toString(value));
            }
        }

        for (int i = 0; i < valueStrings.size(); i++) {
            sb.append(valueStrings.get(i));

            if (i < valueStrings.size() - 1) {
                sb.append(", ");
            }
        }

        setText(sb.toString());
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
        Long[] filter(Long[] value, Long[] oldValue);
    }
}



