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
import java.util.EventObject;

/**
 * Edits a cell in a data table.
 *
 * @author Joseph Ramsey
 */
class DataCellEditor extends DefaultCellEditor {
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private final JTextField textField;

    /**
     * Constructs a new number cell editor.
     */
    public DataCellEditor() {
        super(new JTextField());

        this.textField = (JTextField) this.editorComponent;
        this.textField.setHorizontalAlignment(SwingConstants.LEFT);
        this.textField.setBorder(new LineBorder(Color.BLACK));

        this.delegate = new EditorDelegate() {
            public void setValue(final Object value) {
                if (value == null) {
                    DataCellEditor.this.textField.setText("");
                } else if (value instanceof String) {
                    DataCellEditor.this.textField.setText((String) value);
                } else if (value instanceof Integer) {
                    DataCellEditor.this.textField.setText(value.toString());
                } else if (value instanceof Double) {
                    final double doubleValue = (Double) value;

                    if (Double.isNaN(doubleValue)) {
                        DataCellEditor.this.textField.setText("");
                    } else {
                        DataCellEditor.this.textField.setText(DataCellEditor.this.nf.format(doubleValue));
                    }
                }

                DataCellEditor.this.textField.selectAll();
            }

            /**
             * Overrides delegate; gets the text value from the cell to send
             * back to the model.
             *
             * @return this text getValue.
             */
            public Object getCellEditorValue() {
                return DataCellEditor.this.textField.getText();
            }
        };

        this.textField.addActionListener(this.delegate);
    }

    /**
     * Forwards the message from the <code>CellEditor</code> to the
     * <code>delegate</code>.
     *
     * @see javax.swing.DefaultCellEditor.EditorDelegate#shouldSelectCell(java.util.EventObject)
     */
    public boolean shouldSelectCell(final EventObject anEvent) {
        return true;
    }
}



