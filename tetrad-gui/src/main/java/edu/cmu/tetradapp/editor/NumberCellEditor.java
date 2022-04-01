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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.text.NumberFormat;

/**
 * This is an editor for a JTable cell for editing numbers in conjunction with
 * tetrad.datatable.NumberCellRenderer.
 *
 * @author Joseph Ramsey
 * @see NumberCellRenderer
 */
class NumberCellEditor extends DefaultCellEditor {
    private final JTextField textField;
    private String emptyString = "";

    /**
     * Constructs a new number cell editor with default number format,
     * Decimalformat("0.0000").
     */
    public NumberCellEditor() {
        this(NumberFormatUtil.getInstance().getNumberFormat());
    }

    /**
     * Constructs a new number cell editor.
     */
    public NumberCellEditor(NumberFormat nf) {
        super(new JTextField());

        if (nf == null) {
            throw new NullPointerException();
        }

        this.textField = (JTextField) this.editorComponent;
        this.textField.setHorizontalAlignment(SwingConstants.RIGHT);
        this.textField.setBorder(new LineBorder(Color.black));
        this.textField.setFont(new Font("Serif", Font.PLAIN, 12));
        setClickCountToStart(1);

        this.delegate = new EditorDelegate() {

            /**
             * Overrides delegate; sets the value of the textfield to the
             * value of the datum.
             *
             * @param value this value.
             */
            public void setValue(Object value) {
                if (value == null) {
                    NumberCellEditor.this.textField.setText(NumberCellEditor.this.emptyString);
                } else if (value instanceof Integer) {
                    NumberCellEditor.this.textField.setText(value.toString());
                } else if (value instanceof Double) {
                    double doubleValue = (Double) value;

                    if (Double.isNaN(doubleValue)) {
                        NumberCellEditor.this.textField.setText(NumberCellEditor.this.emptyString);
                    } else {
                        NumberCellEditor.this.textField.setText(nf.format(doubleValue));
                    }
                }

                NumberCellEditor.this.textField.setCaretPosition(0);
                NumberCellEditor.this.textField.moveCaretPosition(NumberCellEditor.this.textField.getText().length());
            }

            /**
             * Overrides delegate; gets the text value from the cell to send
             * back to the model.
             *
             * @return this text value.
             */
            public Object getCellEditorValue() {
                return NumberCellEditor.this.textField.getText();
            }
        };

        this.textField.addActionListener(this.delegate);
    }

    public String getEmptyString() {
        return this.emptyString;
    }

    public void setEmptyString(String emptyString) {
        if (emptyString == null) {
            throw new NullPointerException();
        }
        this.emptyString = emptyString;
    }
}





