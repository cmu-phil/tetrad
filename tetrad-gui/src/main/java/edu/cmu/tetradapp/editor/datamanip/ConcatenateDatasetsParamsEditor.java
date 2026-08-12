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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.ParameterEditor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Edits the parameters for the concatenate datasets transform: whether to append a discrete
 * source column recording which data set each row came from, and the name of that column.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ConcatenateDatasetsParamsEditor extends JPanel implements ParameterEditor {

    private static final long serialVersionUID = 23L;

    /**
     * The params.
     */
    private Parameters params;

    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public ConcatenateDatasetsParamsEditor() {
        super(new BorderLayout());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the parameters.
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * Does nothing
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {

    }

    /**
     * Builds the panel.
     */
    public void setup() {
        JCheckBox addSourceColumn = new JCheckBox("Add a source column recording which data set each row came from",
                this.params.getBoolean("concatAddSourceColumn", false));

        JTextField columnName = new JTextField(this.params.getString("concatSourceColumnName", "source"), 12);
        columnName.setMaximumSize(columnName.getPreferredSize());
        columnName.setEnabled(addSourceColumn.isSelected());

        addSourceColumn.addActionListener(e -> {
            boolean selected = addSourceColumn.isSelected();
            ConcatenateDatasetsParamsEditor.this.params.set("concatAddSourceColumn", selected);
            columnName.setEnabled(selected);
        });

        columnName.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void changedUpdate(DocumentEvent e) {
                update();
            }

            private void update() {
                ConcatenateDatasetsParamsEditor.this.params.set("concatSourceColumnName", columnName.getText());
            }
        });

        Box b1 = Box.createHorizontalBox();
        b1.add(addSourceColumn);
        b1.add(Box.createHorizontalGlue());
        b1.setBorder(new EmptyBorder(10, 10, 5, 10));

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Source column name: "));
        b2.add(Box.createHorizontalGlue());
        b2.add(Box.createHorizontalStrut(15));
        b2.add(columnName);
        b2.setBorder(new EmptyBorder(5, 10, 10, 10));

        Box vert = Box.createVerticalBox();
        vert.add(b1);
        vert.add(b2);
        add(vert, BorderLayout.CENTER);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }
}
