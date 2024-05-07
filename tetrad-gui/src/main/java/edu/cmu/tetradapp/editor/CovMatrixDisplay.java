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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;

/**
 * Presents a covariance matrix as a JTable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CovMatrixDisplay extends JPanel implements DataModelContainer {

    /**
     * The JTable that displays the data.
     */
    private final CovMatrixJTable covMatrixJTable;

    /**
     * The label for the display.
     */
    private final JLabel label;

    /**
     * The restore button.
     */
    private final JButton restoreButton;

    /**
     * The property change support.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Construct a new JTable for the given CovarianceMatrix.
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @see edu.cmu.tetrad.data.CovarianceMatrix
     */
    public CovMatrixDisplay(ICovarianceMatrix covMatrix) {
        this.covMatrixJTable = new CovMatrixJTable(covMatrix);
        this.label = new JLabel(" ");
        this.restoreButton = new JButton("Restore");
        this.restoreButton.setEnabled(false);

        setLayout(new BorderLayout());
        add(new JScrollPane(getCovMatrixJTable()), BorderLayout.CENTER);

        Box b1 = Box.createHorizontalBox();

        if (covMatrix instanceof CorrelationMatrix) {
            b1.add(new JLabel("Correlation Matrix"));
        } else {
            b1.add(new JLabel("Covariance Matrix"));
        }

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.NORTH);

        Box b2 = Box.createHorizontalBox();
        b2.add(this.label);
        b2.add(Box.createHorizontalGlue());
        b2.add(this.restoreButton);
        add(b2, BorderLayout.SOUTH);

        if (!this.covMatrixJTable.isEditingMatrixPositiveDefinite()) {
            this.label.setText("Matrix not positive definite.");
            this.restoreButton.setEnabled(true);
        } else {
            this.label.setText(" ");
            this.restoreButton.setEnabled(false);
        }

        getCovMatrixJTable().addPropertyChangeListener(
                evt -> {
                    if ("modelChanged".equals(evt.getPropertyName())) {
                        firePropertyChange("modelChanged", null, null);
                    }

                    if ("tableChanged".equals(evt.getPropertyName())) {
                        CovMatrixJTable source =
                                (CovMatrixJTable) evt.getSource();

                        if (!source.isEditingMatrixPositiveDefinite()) {
                            CovMatrixDisplay.this.label.setText("Matrix not positive definite.");
                            CovMatrixDisplay.this.restoreButton.setEnabled(true);
                        } else {
                            CovMatrixDisplay.this.label.setText(" ");
                            CovMatrixDisplay.this.restoreButton.setEnabled(false);
                        }
                    }
                });

        this.restoreButton.addActionListener(e -> getCovMatrixJTable().restore());
    }

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        return getCovMatrixJTable().getDataModel();
    }

    /**
     * <p>Getter for the field <code>covMatrixJTable</code>.</p>
     *
     * @return a {@link edu.cmu.tetradapp.editor.CovMatrixJTable} object
     */
    public CovMatrixJTable getCovMatrixJTable() {
        return this.covMatrixJTable;
    }

    /**
     * <p>propertyChange.</p>
     *
     * @param evt a {@link java.beans.PropertyChangeEvent} object
     */
    public void propertyChange(PropertyChangeEvent evt) {
        this.pcs.firePropertyChange(evt);
    }
}





