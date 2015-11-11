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
    private JTextField textField;
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
    public NumberCellEditor(final NumberFormat nf) {
        super(new JTextField());

        if (nf == null) {
            throw new NullPointerException();
        }

        textField = (JTextField) editorComponent;
        textField.setHorizontalAlignment(JTextField.RIGHT);
        textField.setBorder(new LineBorder(Color.black));
        textField.setFont(new Font("Serif", Font.PLAIN, 12));
        setClickCountToStart(1);

        delegate = new EditorDelegate() {

            /**
             * Overrides delegate; sets the value of the textfield to the
             * value of the datum.
             *
             * @param value this value.
             */
            public void setValue(Object value) {
                if (value == null) {
                    textField.setText(emptyString);
                }
                else if (value instanceof Integer) {
                    textField.setText(value.toString());
                }
                else if (value instanceof Double) {
                    double doubleValue = (Double) value;

                    if (Double.isNaN(doubleValue)) {
                        textField.setText(emptyString);
                    }
                    else {
                        textField.setText(nf.format(doubleValue));
                    }
                }

                textField.setCaretPosition(0);
                textField.moveCaretPosition(textField.getText().length());
            }

            /**
             * Overrides delegate; gets the text value from the cell to send
             * back to the model.
             *
             * @return this text value.
             */
            public Object getCellEditorValue() {
                return textField.getText();
            }
        };

        textField.addActionListener(delegate);
    }

    public String getEmptyString() {
        return emptyString;
    }

    public void setEmptyString(String emptyString) {
        if (emptyString == null) {
            throw new NullPointerException();
        }
        this.emptyString = emptyString;
    }
}





