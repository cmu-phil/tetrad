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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;

import javax.swing.table.AbstractTableModel;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

/**
 * Wraps a covMatrix which is possibly smaller than the display window in a larger AbstractTableModel which will fill
 * the window.
 *
 * @author josephramsey
 */
class CovMatrixTable extends AbstractTableModel {

    /**
     * The wrapped CovarianceMatrix.
     */
    private final ICovarianceMatrix covMatrix;
    /**
     * Fires property change events.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    /**
     * The matrix being edited. Since covariance matrices must be positive definite, this must be kept separately while
     * editing and only set when the user clicks the accept button.
     */
    private Matrix editingMatrix;
    /**
     * True iff the editing table is positive definite.
     */
    private boolean editingMatrixPositiveDefinite;

    /**
     * Constructs a new DisplayTableModel to wrap the given covMatrix.
     *
     * @param covMatrix the covMatrix.
     */
    public CovMatrixTable(ICovarianceMatrix covMatrix) {
        this.covMatrix = covMatrix;
        this.editingMatrix = covMatrix.getMatrix().copy();
        this.editingMatrixPositiveDefinite =
                MatrixUtils.isPositiveDefinite(this.editingMatrix);
    }

    /**
     * Returns the number of rows in the wrapper table model. Guarantees that this number will be at least 100.
     *
     * @return the row count of the wrapped model or 100, whichever is larger.
     */
    public int getRowCount() {
        return (getNumVariables() < 100) ? 100 : getNumVariables() + 4;
    }

    /**
     * Returns the number of columns in the wrapper table model. Guarantees that this number will be at least 30.
     *
     * @return the column count of the wrapped model or 30, whichever is larger.
     */
    public int getColumnCount() {
        return (getNumVariables() < 30) ? 30 : getNumVariables() + 1;
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int row, int col) {
        final int firstDataRow = 4;
        final int firstDataCol = 1;
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

    /**
     * {@inheritDoc}
     */
    public boolean isCellEditable(int row, int col) {
        final int firstDataRow = 4;
        final int firstDataCol = 1;
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

        return !(this.covMatrix instanceof CorrelationMatrix) &&
               (row >= firstDataRow) && (row < lastDataRow) &&
               (col >= firstDataCol) && (matrixCol == matrixRow);

    }

    /**
     * {@inheritDoc}
     */
    public void setValueAt(Object aValue, int row, int col) {
        final int firstDataRow = 4;
        final int firstDataCol = 1;
        int matrixRow = row - firstDataRow;
        int matrixCol = col - firstDataCol;
        int lastDataRow = firstDataRow + getNumVariables();
        int lastDataCol = firstDataCol + getNumVariables();

        if (row == 1 && col == 1) {
            String value = (String) aValue;
            this.covMatrix.setSampleSize(Integer.parseInt(value));
            this.pcs.firePropertyChange("modelChanged", null, null);
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
            this.pcs.firePropertyChange("modelChanged", null, null);
            fireTableDataChanged();
        }
    }

    /**
     * <p>addPropertyChangeListener.</p>
     *
     * @param listener a {@link java.beans.PropertyChangeListener} object
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    private void setVariableName(int index, String name) {
        List variables = getCovMatrix().getVariables();

        for (Object o : variables) {
            ContinuousVariable _variable =
                    (ContinuousVariable) o;
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

        this.editingMatrix.set(row, col, v);
        this.editingMatrix.set(col, row, v);
        this.editingMatrixPositiveDefinite =
                MatrixUtils.isPositiveDefinite(this.editingMatrix);
        if (this.editingMatrixPositiveDefinite) {
            getCovMatrix().setMatrix(this.editingMatrix.copy());
        }
    }

    private int getSampleSize() {
        return getCovMatrix().getSampleSize();
    }

    private String getVariableName(int matrixRow) {
        try {
            return getCovMatrix().getVariableName(matrixRow);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double getValue(int matrixRow, int matrixCol) {
        return this.editingMatrix.get(matrixRow, matrixCol);
    }

    /**
     * <p>Getter for the field <code>covMatrix</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

    private int getNumVariables() {
        return this.covMatrix.getSize();
    }

    /**
     * <p>isEditingMatrixPositiveDefinite.</p>
     *
     * @return a boolean
     */
    public boolean isEditingMatrixPositiveDefinite() {
        return this.editingMatrixPositiveDefinite;
    }

    /**
     * <p>restore.</p>
     */
    public void restore() {
        this.editingMatrix = this.covMatrix.getMatrix();
        this.editingMatrixPositiveDefinite =
                MatrixUtils.isPositiveDefinite(this.editingMatrix);
        this.pcs.firePropertyChange("modelChanged", null, null);
        fireTableDataChanged();
    }
}






