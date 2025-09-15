package edu.cmu.tetradapp.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * A text field which displays Strings (OK, not much of an accomplishment that) but which also handles other GUI-related
 * functions like keeping the text box the right size and handling focus events.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class StringTextField extends JTextField {

    /**
     * The getModel value of the text field.
     */
    private String value;

    /**
     * If set, filters the value input by the user. (Side effects are allowed.)
     */
    private Filter filter;

    /**
     * Constructs a new text field displaying the given default value.
     *
     * @param value a {@link java.lang.String} object
     * @param size  a int
     */
    public StringTextField(String value, int size) {
        super(size);

        setHorizontalAlignment(SwingConstants.LEFT);
        setValue(value);
        addActionListener(new ActionListener() {

            /**
             * Reacts to somebody pressing the return key in this field by
             * attempting to set the value displayed. If the value
             * displayed cannot be set, the set value is reinstated.
             */
            public void actionPerformed(ActionEvent e) {
                try {
                    setValue(e.getActionCommand());
                } catch (NumberFormatException e1) {
                    setValue(StringTextField.this.value);
                }
            }
        });

        addFocusListener(new FocusAdapter() {

            /**
             * Nothing need be done when focus is gained, but this method is
             * required by the FocusListener interface.
             */
            public void focusGained(FocusEvent e) {
                if (isEditable()) {
                    selectAll();
                }
            }

            /**
             * If focus is lost, attempt to store the text value being
             * displayed as String; if this cannot be done, restore the previous
             * value.
             *
             * @param e the event.
             */
            public void focusLost(FocusEvent e) {

                // Dehighlight text.
                setCaretPosition(0);
                moveCaretPosition(0);

                setValue(getText());
            }
        });
    }

    /**
     * Accesses the int value currently displayed.
     *
     * @return a {@link java.lang.String} object
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets the value of the text field to the given String value.
     *
     * @param value a {@link java.lang.String} object
     */
    public void setValue(String value) {
        if (value.equals(this.value)) {
            return;
        }

        String newValue = filter(value, this.value);

        if (!newValue.equals(this.value)) {
            this.value = newValue;
            setText(this.value);
            firePropertyChange("newValue", null, this.value);
        }
    }

    /**
     * Sets whether the given value should be accepted.
     *
     * @param filter a {@link edu.cmu.tetradapp.util.StringTextField.Filter} object
     */
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /**
     * Convinces the text field to stay the right size in layouts that are trying to expand it like a balloon by
     * returning the preferred size.
     *
     * @return a {@link java.awt.Dimension} object
     */
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private String filter(String value, String oldValue) {
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
        String filter(String value, String oldValue);
    }
}





