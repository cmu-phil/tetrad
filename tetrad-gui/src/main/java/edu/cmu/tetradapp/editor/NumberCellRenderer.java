///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.NumberFormat;

/**
 * This a renderer for a JTable cell for rending numbers in conjunction with tetrad.datatable.NumberCellEditor.
 *
 * @author josephramsey
 * @see NumberCellEditor
 */
class NumberCellRenderer extends DefaultTableCellRenderer {
    private final NumberFormat nf;
    private String emptyString = "";

    /**
     * <p>Constructor for NumberCellRenderer.</p>
     */
    public NumberCellRenderer() {
        this(NumberFormatUtil.getInstance().getNumberFormat());
    }

    /**
     * Constructs a new number cell renderer.
     *
     * @param nf a {@link java.text.NumberFormat} object
     */
    public NumberCellRenderer(NumberFormat nf) {
        if (nf == null) {
            throw new NullPointerException();
        }

        this.nf = nf;

        setHorizontalAlignment(SwingConstants.RIGHT);
        setFont(new Font("Serif", Font.PLAIN, 12));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the value to the formatted version of the stored numerical value.
     */
    public void setValue(Object value) {
        if (value == null) {
            setText(getEmptyString());
        } else if (value instanceof Integer) {
            setText(value.toString());
        } else if (value instanceof Double) {
            double doubleValue = (Double) value;
            if (Double.isNaN(doubleValue)) {
                setText(getEmptyString());
            } else {
                setText(this.nf.format(doubleValue));
            }
        } else {
            setText("");
        }
    }

    private String getEmptyString() {
        return this.emptyString;
    }

    /**
     * <p>Setter for the field <code>emptyString</code>.</p>
     *
     * @param emptyString a {@link java.lang.String} object
     */
    public void setEmptyString(String emptyString) {
        if (emptyString == null) {
            throw new NullPointerException();
        }
        this.emptyString = emptyString;
    }
}






