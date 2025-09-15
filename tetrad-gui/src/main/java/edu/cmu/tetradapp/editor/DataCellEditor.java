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
import javax.swing.border.LineBorder;
import java.awt.*;
import java.text.NumberFormat;
import java.util.EventObject;

/**
 * Edits a cell in a data table.
 *
 * @author josephramsey
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
            public void setValue(Object value) {
                if (value == null) {
                    DataCellEditor.this.textField.setText("");
                } else if (value instanceof String) {
                    DataCellEditor.this.textField.setText((String) value);
                } else if (value instanceof Integer) {
                    DataCellEditor.this.textField.setText(value.toString());
                } else if (value instanceof Double) {
                    double doubleValue = (Double) value;

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
     * {@inheritDoc}
     * <p>
     * Forwards the message from the <code>CellEditor</code> to the
     * <code>delegate</code>.
     *
     * @see javax.swing.DefaultCellEditor.EditorDelegate#shouldSelectCell(java.util.EventObject)
     */
    public boolean shouldSelectCell(EventObject anEvent) {
        return true;
    }
}




