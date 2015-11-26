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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;

import javax.swing.table.AbstractTableModel;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

/**
 * Wraps a covMatrix which is possibly smaller than the display window in a
 * larger AbstractTableModel which will fill the window.
 *
 * @author Joseph Ramsey
 */
class CovMatrixTable extends AbstractTableModel {

    /**
     * The wrapped CovarianceMatrix.
     */
    private ICovarianceMatrix covMatrix;

    /**
     * The matrix being edited. Since covariance matrices must be positive
     * definite, this must be kept separately while editing and only set when
     * the user clicks the accept button.
     */
    private TetradMatrix editingMatrix;

    /**
     * True iff the editing table is positive definite.
     */
    private boolean editingMatrixPositiveDefinite;

    /**
     * Fires property change events.
     */
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Constructs a new DisplayTableModel to wrap the given covMatrix.
     *
     * @param covMatrix the covMatrix.
     */
    public CovMatrixTable(ICovarianceMatrix covMatrix) {
        this.covMatrix = covMatrix;
        this.editingMatrix = covMatrix.getMatrix().copy();
        this.editingMatrixPositiveDefinite =
                MatrixUtils.isPositiveDefinite(editingMatrix);
    }

    /**
     * @return the number of rows in the wrapper table model. Guarantees that
     * this number will be at least 100.
     *
     * @return the row count of the wrapped model or 100, whichever is larger.
     */
    public int getRowCount() {
        return (getNumVariables() < 100) ? 100 : getNumVariables();
    }

    /**
     * @return the number of columns in the wrapper table model. Guarantees that
     * this number will be at least 30.
     *
     * @return the column count of the wrapped model or 30, whichever is
     *         larger.
     */
    public int getColumnCount() {
        return (getNumVariables() < 30) ? 30 : getNumVariables() + 1;
    }

    /**
     * @return the value at the given (row, column) coordinates of the table
     * as an Object.  If the variable for the column is a DiscreteVariable, the
     * String value (as opposed to the integer index value) is extracted
     * and returned.  If the coordinates are out of range of the wrapped table
     * model, 'null' is returned. Otherwise, the value stored in the wrapped
     * table model at the given coordinates is returned.
     */
    public Object getValueAt(int row, int col) {
        int firstDataRow = 4;
        int firstDataCol = 1;
        int matrixRow = row - firstDataRow;
        int matrixCol = col - firstDataCol;
        int lastDataRow = firstDataRow + getNumVariables();
        int lastDataCol = firstDataCol + getNumVariables();

        if (row == 1 && col == 0) {
            return "Sample Size";
        }

        if (row == 1 && col == 1) {
            return getSampleSize();
        }

        if ((col == firstDataCol - 1) && (row >= firstDataRow) &&
                (row < lastDataRow)) {
            return getVariableName(matrixRow);
        }

        if ((row == firstDataRow - 1) && (col >= firstDataCol) &&
                (col < lastDataCol)) {
            return getVariableName(matrixCol);
        }

        if ((row >= firstDataRow) && (row < lastDataRow) &&
                (matrixCol <= matrixRow)) {
            return getValue(matrixRow, matrixCol);
        }

        return null;
    }

    public boolean isCellEditable(int row, int col) {
        int firstDataRow = 4;
        int firstDataCol = 1;
        int matrixRow = row - firstDataRow;
        int matrixCol = col - firstDataCol;
        int lastDataRow = firstDataRow + getNumVariables();
        int lastDataCol = firstDataCol + getNumVariables();

        if (row == 1 && col == 1) {
            return true;
        }

        if ((col == firstDataCol - 1) && (row >= firstDataRow) &&
                (row < lastDataRow)) {
            return true;
        }

        if ((row == firstDataRow - 1) && (col >= firstDataCol) &&
                (col < lastDataCol)) {
            return true;
        }

        if ((row >= firstDataRow) && (row < lastDataRow) &&
                (col >= firstDataCol) && (matrixCol < matrixRow)) {
            return true;
        }

        return !(covMatrix instanceof CorrelationMatrix) &&
                (row >= firstDataRow) && (row < lastDataRow) &&
                (col >= firstDataCol) && (matrixCol == matrixRow);

    }

    public void setValueAt(Object aValue, int row, int col) {
        int firstDataRow = 4;
        int firstDataCol = 1;
        int matrixRow = row - firstDataRow;
        int matrixCol = col - firstDataCol;
        int lastDataRow = firstDataRow + getNumVariables();
        int lastDataCol = firstDataCol + getNumVariables();

        if (row == 1 && col == 1) {
            String value = (String) aValue;
            covMatrix.setSampleSize(Integer.parseInt(value));
            pcs.firePropertyChange("modelChanged", null, null);
            fireTableDataChanged();
        }

        if ((col == firstDataCol - 1) && (row >= firstDataRow) &&
                (row < lastDataRow)) {
            setVariableName(matrixRow, (String) aValue);
            fireTableDataChanged();
        }

        if ((row == firstDataRow - 1) && (col >= firstDataCol) &&
                (col < lastDataCol)) {
            setVariableName(matrixCol, (String) aValue);
            fireTableDataChanged();
        }

        if ((row >= firstDataRow) && (row < lastDataRow) &&
                (col >= firstDataCol) && (matrixCol <= matrixRow)) {
            String value = (String) aValue;
            double v = Double.parseDouble(value);
            setEditingValue(matrixRow, matrixCol, v);
            pcs.firePropertyChange("modelChanged", null, null);
            fireTableDataChanged();
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    private void setVariableName(int index, String name) {
        List variables = getCovMatrix().getVariables();

        for (int i = 0; i < variables.size(); i++) {
            ContinuousVariable _variable =
                    (ContinuousVariable) variables.get(i);
            if (name.equals(_variable.getName())) {
                return;
            }
        }

        ContinuousVariable variable = (ContinuousVariable) variables.get(index);
        variable.setName(name);
    }

    private void setEditingValue(int row, int col, double v) {
        if (row == col && v <= 0.0) {
            return;
        }

        editingMatrix.set(row, col, v);
        editingMatrix.set(col, row, v);
        editingMatrixPositiveDefinite =
                MatrixUtils.isPositiveDefinite(editingMatrix);
        if (editingMatrixPositiveDefinite) {
            getCovMatrix().setMatrix(editingMatrix.copy());
        }
    }

    private int getSampleSize() {
        return getCovMatrix().getSampleSize();
    }

    private String getVariableName(int matrixRow) {
        return getCovMatrix().getVariableName(matrixRow);
    }

    private double getValue(int matrixRow, int matrixCol) {
        return editingMatrix.get(matrixRow, matrixCol);
    }

    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

    private int getNumVariables() {
        return covMatrix.getSize();
    }

    public boolean isEditingMatrixPositiveDefinite() {
        return editingMatrixPositiveDefinite;
    }

    public void restore() {
        editingMatrix = covMatrix.getMatrix();
        editingMatrixPositiveDefinite =
                MatrixUtils.isPositiveDefinite(this.editingMatrix);
        pcs.firePropertyChange("modelChanged", null, null);
        fireTableDataChanged();
    }
}





