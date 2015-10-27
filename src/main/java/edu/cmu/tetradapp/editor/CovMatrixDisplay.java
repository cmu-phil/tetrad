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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Presents a covariance matrix as a JTable.
 *
 * @author Joseph Ramsey
 */
public class CovMatrixDisplay extends JPanel implements DataModelContainer {
    private final CovMatrixJTable covMatrixJTable;
    private final JLabel label;
    private final JButton restoreButton;
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Construct a new JTable for the given CovarianceMatrix.
     *
     * @see edu.cmu.tetrad.data.CovarianceMatrix
     */
    public CovMatrixDisplay(ICovarianceMatrix covMatrix) {
        covMatrixJTable = new CovMatrixJTable(covMatrix);
        label = new JLabel(" ");
        restoreButton = new JButton("Restore");
        restoreButton.setEnabled(false);

        setLayout(new BorderLayout());
        add(new JScrollPane(getCovMatrixJTable()), BorderLayout.CENTER);

        Box b1 = Box.createHorizontalBox();

        if (covMatrix instanceof CorrelationMatrix) {
            b1.add(new JLabel("Correlation Matrix"));
        }
        else {
            b1.add(new JLabel("Covariance Matrix"));
        }

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.NORTH);

        Box b2 = Box.createHorizontalBox();
        b2.add(label);
        b2.add(Box.createHorizontalGlue());
        b2.add(restoreButton);
        add(b2, BorderLayout.SOUTH);

        if (!covMatrixJTable.isEditingMatrixPositiveDefinite()) {
            label.setText("Matrix not positive definite.");
            restoreButton.setEnabled(true);
        }
        else {
            label.setText(" ");
            restoreButton.setEnabled(false);
        }

        getCovMatrixJTable().addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("modelChanged".equals(evt.getPropertyName())) {
                            firePropertyChange("modelChanged", null, null);
                        }

                        if ("tableChanged".equals(evt.getPropertyName())) {
                            CovMatrixJTable source =
                                    (CovMatrixJTable) evt.getSource();

                            if (!source.isEditingMatrixPositiveDefinite()) {
                                label.setText("Matrix not positive definite.");
                                restoreButton.setEnabled(true);
                            }
                            else {
                                label.setText(" ");
                                restoreButton.setEnabled(false);
                            }
                        }
                    }
                });

        restoreButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getCovMatrixJTable().restore();
            }
        });
    }

    public DataModel getDataModel() {
        return getCovMatrixJTable().getDataModel();
    }

    public CovMatrixJTable getCovMatrixJTable() {
        return covMatrixJTable;
    }

    public void propertyChange(PropertyChangeEvent evt) {
        pcs.firePropertyChange(evt);
    }
}





