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
import java.util.Objects;

/**
 * A JSpinner that uses a <code>IntTextField</code> as its editor.  When changes are made from the
 * editor or the spinner a property change event is invoked with the property name "changedIntValue" and
 * the new value (old value is not given).
 *
 * @author Tyler Gibson
 */
public class IntSpinner extends JSpinner {


    /**
     * The eidtor
     */
    private final IntTextField editor;

    /**
     * Filter to use.
     */
    private Filter filter;


    /**
     * Min value
     */
    private Integer min;

    /**
     * Max value
     */
    private Integer max;

    /**
     * Constructs the int spinner given the initial value, the min value to accept,
     * the max value to accept and the step.
     *
     * @param value - Initial value to dispaly
     * @param step  - The step (the amount that is "jumped" when the spinner is activated)
     * @param size  - The size of the int text field.
     */
    public IntSpinner(Integer value, Integer step, Integer size) {
        super(new SpinnerNumberModel(value, null, null, step));
        this.editor = new IntTextField(value, size);
        // make the spinner a bit bigger than the text field (looks better)
        this.setPreferredSize(IntSpinner.increment(this.editor.getPreferredSize()));
        this.setMaximumSize(IntSpinner.increment(this.editor.getMaximumSize()));
        this.setMinimumSize(IntSpinner.increment(this.editor.getMinimumSize()));
        this.setSize(IntSpinner.increment(this.editor.getSize()));

        this.editor.setFilter((value1, oldValue) -> {
            if (IntSpinner.this.min != null && value1 < IntSpinner.this.min) {
                value1 = IntSpinner.this.min;
            } else if (IntSpinner.this.max != null && IntSpinner.this.max < value1) {
                value1 = IntSpinner.this.max;
            }
            return value1;
        });

        // Can't do this in the filter, due to other events calling the filter
        this.editor.addActionListener(e -> {
            IntTextField field = (IntTextField) e.getSource();
            String text = field.getText();
            try {
                // parse value and let the field filter it.
                int value12 = Integer.parseInt(text);
                field.setValue(value12);
                value12 = field.getValue();
                if (!IntSpinner.this.getValue().equals(value12)) {
                    IntSpinner.this.setValue(value12);
                }
            } catch (Exception ex) {
                // do nothing in this case
            }
        });

        this.setEditor(this.editor);
    }

    //=========================== Public Methods ============================//

    public void setMin(Integer min) {
        if (!Objects.equals(this.min, min)) {
            this.min = min;
            SpinnerNumberModel model = (SpinnerNumberModel) this.getModel();
            model.setMinimum(min);
            // update the text filed by resetting value
            this.editor.setValue(this.editor.getValue());
        }
    }


    /**
     * Sets the max value for the spinner.
     */
    public void setMax(Integer max) {
        if (!Objects.equals(this.max, max)) {
            this.max = max;
            SpinnerNumberModel model = (SpinnerNumberModel) this.getModel();
            model.setMaximum(max);
            // update the text filed by resetting value
            this.editor.setValue(this.editor.getValue());
        }
    }


    public void setFilter(Filter filter) {
        this.filter = filter;
    }


    public void setValue(Object object) {
        if (object == null) {
            throw new NullPointerException();
        }

        int value = filter((Integer) object);
        if (!getValue().equals(value)) {
            super.setValue(value);
            this.editor.setUnfilteredValue(value);
        }
    }


    private static Dimension increment(Dimension dim) {
        return new Dimension(dim.width + 2, dim.height + 2);
    }

    //=========================== private methods ======================//


    private int filter(int value) {
        if (this.filter == null) {
            return value;
        }
        if (getValue().equals(value)) {
            return value;
        }
        return this.filter.filter((Integer) getValue(), value);
    }

    //============================ Inner classer ====================================//


    public interface Filter {

        int filter(int oldValue, int newValue);

    }

}



