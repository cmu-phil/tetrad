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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import org.apache.commons.math3.util.FastMath;

import javax.swing.table.AbstractTableModel;
import java.text.NumberFormat;
import java.util.List;


/**
 * Presents a covariance matrix as a table model for the SemImEditor.
 *
 * @author Donald Crimbchin
 */
final class StandardizedSemImImpliedCovTable extends AbstractTableModel {

    /**
     * The SemIm whose implied covariance matrices this model is displaying.
     */
    private final StandardizedSemIm semIm;

    /**
     * True iff the matrices for the observed variables ony should be displayed.
     */
    private final boolean measured;

    /**
     * True iff correlations (rather than covariances) should be displayed.
     */
    private final boolean correlations;

    /**
     * Formats numbers so that they have 4 digits after the decimal place.
     */
    private final NumberFormat nf;

    /**
     * The matrix being displayed. (This varies.)
     */
    private final double[][] matrix;

    /**
     * Constructs a new table for the given covariance matrix, the nodes for which are as specified (in the order they
     * appear in the matrix).
     *
     * @param semIm        a {@link edu.cmu.tetrad.sem.StandardizedSemIm} object
     * @param measured     a boolean
     * @param correlations a boolean
     */
    public StandardizedSemImImpliedCovTable(StandardizedSemIm semIm, boolean measured,
                                            boolean correlations) {
        this.semIm = semIm;
        this.measured = measured;
        this.correlations = correlations;

        this.nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (measured() && covariances()) {
            this.matrix = getSemIm().getImplCovarMeas().toArray();
        } else if (measured()) {
            this.matrix = StandardizedSemImImpliedCovTable.corr(getSemIm().getImplCovarMeas().toArray());
        } else if (covariances()) {
            Matrix implCovarC = getSemIm().getImplCovar();
            this.matrix = implCovarC.toArray();
        } else {
            Matrix implCovarC = getSemIm().getImplCovar();
            this.matrix = StandardizedSemImImpliedCovTable.corr(implCovarC.toArray());
        }
    }

    private static double[][] corr(double[][] implCovar) {
        int length = implCovar.length;
        double[][] corr = new double[length][length];

        for (int i = 1; i < length; i++) {
            for (int j = 0; j < i; j++) {
                double d1 = implCovar[i][j];
                double d2 = implCovar[i][i];
                double d3 = implCovar[j][j];
                double d4 = d1 / FastMath.pow(d2 * d3, 0.5);

                if (d4 <= 1.0 || Double.isNaN(d4)) {
                    corr[i][j] = d4;
                } else {
                    throw new IllegalArgumentException(
                            "Off-diagonal element at (" + i + ", " + j +
                            ") cannot be converted to correlation: " +
                            d1 + " <= FastMath.pow(" + d2 + " * " + d3 +
                            ", 0.5)");
                }
            }
        }

        for (int i = 0; i < length; i++) {
            corr[i][i] = 1.0;
        }

        return corr;
    }

    /**
     * <p>getRowCount.</p>
     *
     * @return the number of rows being displayed--one more than the size of the matrix, which may be different
     * depending on whether only the observed variables are being displayed or all the variables are being displayed.
     */
    public int getRowCount() {
        if (measured()) {
            return this.getSemIm().getMeasuredNodes().size() + 1;
        } else {
            return this.getSemIm().getVariableNodes().size() + 1;
        }
    }

    /**
     * <p>getColumnCount.</p>
     *
     * @return the number of columns displayed--one more than the size of the matrix, which may be different depending
     * on whether only the observed variables are being displayed or all the variables are being displayed.
     */
    public int getColumnCount() {
        if (measured()) {
            return this.getSemIm().getMeasuredNodes().size() + 1;
        } else {
            return this.getSemIm().getVariableNodes().size() + 1;
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getColumnName(int columnIndex) {
        if (columnIndex == 0) {
            return "";
        } else {
            if (measured()) {
                List nodes = getSemIm().getMeasuredNodes();
                Node node = ((Node) nodes.get(columnIndex - 1));
                return node.getName();
            } else {
                List nodes = getSemIm().getVariableNodes();
                Node node = ((Node) nodes.get(columnIndex - 1));
                return node.getName();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex == 0) {
            return getColumnName(columnIndex);
        }
        if (columnIndex == 0) {
            return getColumnName(rowIndex);
        } else if (rowIndex < columnIndex) {
            return null;
        } else {
            return this.nf.format(this.matrix[rowIndex - 1][columnIndex - 1]);
        }
    }

    private boolean covariances() {
        return !correlations();
    }

    /**
     * @return true iff only observed variables are displayed.
     */
    private boolean measured() {
        return this.measured;
    }

    /**
     * @return true iff correlations (rather than covariances) are displayed.
     */
    private boolean correlations() {
        return this.correlations;
    }

    private StandardizedSemIm getSemIm() {
        return this.semIm;
    }
}


