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

package edu.cmu.tetradapp.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * A text field which displays Strings (OK, not much of an accomplishment that)
 * but which also handles other GUI-related functions like keeping the text box
 * the right size and handling focus events.
 *
 * @author Joseph Ramsey
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

    //============================CONSTRUCTORS==========================//

    /**
     * Constructs a new text field displaying the given default value.
     */
    public StringTextField(String value, int size) {
        super(size);

        setHorizontalAlignment(JTextField.LEFT);
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
                }
                catch (NumberFormatException e1) {
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

    //===============================PUBLIC METHODS========================//

    /**
     * Sets the value of the text field to the given String value.
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
     * Accesses the int value currently displayed.
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets whether the given value should be accepted.
     */
    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    /**
     * Convinces the text field to stay the right size in layouts that are
     * trying to expand it like a balloon by returning the preferred size.
     */
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    //=============================PRIVATE METHODS=======================//

    private String filter(String value, String oldValue) {
        if (filter == null) {
            return value;
        }

        return filter.filter(value, oldValue);
    }

    //==============================INTERFACES===========================//

    /**
     * Filters the given value, returning the value that should actually be
     * displayed. Typical use is to return either the value or the old value,
     * depending on whether the value is in range, though more complicated
     * uses are permitted. Side effects (such as storing the value in the
     * process of filtering it) are permitted.
     */
    public static interface Filter {

        /**
         * Filters the given value, returning the new value that should be
         * displayed.
         *
         * @param value The value entered by the user.
         * @param oldValue The value previously displayed, in case it needs
         * to be reverted to.
         */
        String filter(String value, String oldValue);
    }
}





