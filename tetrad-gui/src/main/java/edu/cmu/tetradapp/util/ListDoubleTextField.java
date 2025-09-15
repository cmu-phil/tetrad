package edu.cmu.tetradapp.util;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.NumberFormat;
import java.util.ArrayList;


/**
 * ListDoubleTextField is a custom JTextField class that is designed to display and edit comma-separated lists of double
 * values. It supports formatting and filtering of the input values.
 *
 * @author josephramsey
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
     * Creates a ListDoubleTextField component with the given parameters.
     *
     * @param values            the array of double values to be displayed and edited
     * @param width             the width of the text field in characters
     * @param format            the NumberFormat object used to format the values
     * @param smallNumberFormat the NumberFormat object used to format small values
     * @param smallNumberCutoff the cutoff value below which smallNumberFormat is used
     */
    public ListDoubleTextField(Double[] values, int width, NumberFormat format, NumberFormat smallNumberFormat,
                               double smallNumberCutoff) {
        super(width);
        setup(values, format, smallNumberFormat, smallNumberCutoff);
    }

    /**
     * Parses a comma-separated string of numbers and returns an array of Double values.
     *
     * @param actionCommand a String containing comma-separated numbers
     * @return an array of Double values parsed from the actionCommand string
     * @throws NullPointerException if the actionCommand is null
     */
    @NotNull
    public static Double[] getNumbers(String actionCommand) {
        String[] split = actionCommand.split(",");
        java.util.List<Double> values = new ArrayList<>();

        for (String s : split) {
            try {
                values.add(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                // Skip.
            }
        }

        return values.toArray(new Double[0]);
    }

    /**
     * Retrieves the array of double values stored in the ListDoubleTextField.
     *
     * @return the array of double values
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
        Double[] newValues = filter(values, this.values);

        if (newValues.length > 0) {
            this.values = newValues;
        }

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

    /**
     * Filters the given value, returning the value that should actually be displayed. Typical use is to return either
     * the value or the old value, depending on whether the value is in range, though more complicated uses are
     * permitted. Side effects (such as storing the value in the process of filtering it) are permitted.
     *
     * @param values    The value entered by the user.
     * @param oldValues The value previously displayed, in case it needs to be reverted to.
     * @return The value that should be displayed.
     */
    private Double[] filter(Double[] values, Double[] oldValues) {
        if (this.filter == null) {
            return values;
        }

        return this.filter.filter(values, oldValues);
    }

    /**
     * Sets up the ListDoubleTextField with the given values and formats.
     *
     * @param values            the initial values for the ListDoubleTextField
     * @param nf                the format for displaying the values
     * @param smallNumberFormat the format for displaying small numbers
     * @param smallNumberCutoff the cutoff value for determining small numbers
     * @throws NullPointerException     if nf is null
     * @throws IllegalArgumentException if smallNumberCutoff is negative
     */
    private void setup(Double[] values, NumberFormat nf, NumberFormat smallNumberFormat, double smallNumberCutoff) {
        if (nf == null) {
            throw new NullPointerException();
        }

        if (smallNumberCutoff < 0.0) {
            throw new IllegalArgumentException("smallNumberCutoff must be non-negative");
        }

        double _default = Double.NaN;
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
            Double[] values1 = getNumbers(getText());
            Double[] filtered = filter(values1, values);

            if (filtered.length > 0) {
                setValues(filtered);
            } else {
                setValues(values);
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
                Double[] values1 = getNumbers(getText());
                Double[] filtered = filter(values1, values);

                if (filtered.length > 0) {
                    setValues(filtered);
                } else {
                    setValues(values);
                }
            }
        });
    }

    /**
     * Sets the text of the component by formatting an array of Double values with the given NumberFormat. If a value is
     * null or NaN, it is skipped in the formatting process. Values smaller than the smallNumberCutoff are formatted
     * using the smallNumberFormat, while values equal or larger than the smallNumberCutoff are formatted using the
     * provided NumberFormat.
     *
     * @param nf     The NumberFormat to use for formatting the values equal or larger than the smallNumberCutoff
     * @param values The array of Double values to format
     */
    private void smartSetText(NumberFormat nf, Double[] values) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> valueStrings = new ArrayList<>();

        for (Double aDouble : values) {
            if (aDouble != null) {
                double value = aDouble;

                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    if (Math.abs(value) < this.smallNumberCutoff && value != 0.0) {
                        valueStrings.add(this.smallNumberFormat.format(value));
                    } else {
                        valueStrings.add(nf.format(value));
                    }
                }
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
        Double[] filter(Double[] value, Double[] oldValue);
    }
}



