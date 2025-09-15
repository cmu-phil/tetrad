package edu.cmu.tetradapp.util;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;


/**
 * A custom text field for displaying and editing a list of integer values.
 *
 * @author josephramsey
 */
public class ListIntTextField extends JTextField {

    /**
     * The list of integer values.
     */
    private Integer[] values;

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
    public ListIntTextField(Integer[] values, int width) {
        super(width);
        setup(values);
    }

    /**
     * Retrieves a list of numbers from the given action command.
     *
     * @param actionCommand the action command containing the numbers separated by commas
     * @return an array of Integers representing the numbers extracted from the action command
     */
    @NotNull
    private static Integer[] getNumbers(String actionCommand) {
        String[] split = actionCommand.split(",");
        java.util.List<Integer> values = new ArrayList<>();

        for (String s : split) {
            String string = s.trim();

            try {
                values.add(Integer.parseInt(string));
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        return values.toArray(new Integer[0]);
    }

    /**
     * Accesses the int values currently displayed.
     *
     * @return the getModel value.
     */
    public Integer[] getValues() {
        return this.values;
    }

    /**
     * Sets the values for the object.
     *
     * @param values an array of Integer values to be set
     */
    public void setValues(Integer[] values) {
        Integer[] newValues = filter(values, this.values);

        if (newValues.length > 0) {
            this.values = newValues;
        }

        smartSetText(this.values);
        firePropertyChange("newValue", null, this.values);
    }

    /**
     * Sets the filter for the ListIntTextField. The filter is used to determine which values should be displayed in the
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
     * Filters an array of integers based on a provided filter.
     *
     * @param values    the array of integers to be filtered
     * @param oldValues the previous array of integers before applying the filter
     * @return the filtered array of integers
     */
    private Integer[] filter(Integer[] values, Integer[] oldValues) {
        if (this.filter == null) {
            return values;
        }

        return this.filter.filter(values, oldValues);
    }

    /**
     * Sets up the ListIntTextField with the given values.
     *
     * @param values an array of Integers representing the initial values
     */
    private void setup(Integer[] values) {

        int _default = -99;
        Integer[] defaultValues = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            defaultValues[i] = _default;
        }

        this.values = filter(values, defaultValues);
        smartSetText(this.values);

        addActionListener(e -> {
            Integer[] values1 = getNumbers(getText());
            Integer[] filtered = filter(values1, values);

            if (filtered.length > 0) {
                setValues(filtered);
            } else {
                setValues(values);
            }
        });

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                ListIntTextField source = (ListIntTextField) e.getSource();

                if (source.isEditable()) {
                    source.selectAll();
                }
            }

            public void focusLost(FocusEvent e) {
                Integer[] values1 = getNumbers(getText());
                Integer[] filtered = filter(values1, values);

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
    private void smartSetText(Integer[] values) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> valueStrings = new ArrayList<>();

        for (Integer anInt : values) {
            if (anInt != null) {
                int value = anInt;
                valueStrings.add(Integer.toString(value));
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
        Integer[] filter(Integer[] value, Integer[] oldValue);
    }
}



