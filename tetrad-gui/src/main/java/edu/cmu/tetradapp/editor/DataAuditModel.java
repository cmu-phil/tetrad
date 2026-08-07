///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;

import javax.swing.table.AbstractTableModel;
import java.text.NumberFormat;
import java.util.List;

/**
 * A table of per-variable missingness facts for the Data Audit dialog, backed by a
 * {@link edu.cmu.tetrad.data.missing.MissingDataAudit}. One row per variable, in dataset column order (so that rows
 * correspond to the audit's column indices). Dataset-level facts and advice are not in this table; the dialog
 * presents those as text.
 *
 * @author josephramsey
 */
class DataAuditModel extends AbstractTableModel {
    private static final long serialVersionUID = 23L;

    /**
     * The column headers, in order.
     */
    private static final String[] COLUMNS = {
            "Variable", "Type", "Observed", "Missing", "Missing %", "Min Pairwise n"
    };

    /**
     * The dataset being audited.
     */
    private final DataSet dataSet;

    /**
     * The audit for the dataset.
     */
    private final MissingDataAudit audit;

    /**
     * Constructs the model for the given dataset and its audit.
     *
     * @param dataSet the dataset.
     * @param audit   the audit of that dataset.
     */
    public DataAuditModel(DataSet dataSet, MissingDataAudit audit) {
        this.dataSet = dataSet;
        this.audit = audit;
    }

    /**
     * {@inheritDoc}
     */
    public String getColumnName(int col) {
        return COLUMNS[col];
    }

    /**
     * {@inheritDoc}
     */
    public int getRowCount() {
        return this.dataSet.getNumColumns();
    }

    /**
     * {@inheritDoc}
     */
    public int getColumnCount() {
        return COLUMNS.length;
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int row, int col) {
        List<Node> variables = this.dataSet.getVariables();
        Node variable = variables.get(row);
        int numRows = this.dataSet.getNumRows();
        int missing = this.audit.getMissingCount(row);

        switch (col) {
            case 0:
                return variable.getName();
            case 1:
                if (variable instanceof ContinuousVariable) return "Continuous";
                if (variable instanceof DiscreteVariable) return "Discrete";
                return variable.getClass().getSimpleName();
            case 2:
                return Integer.toString(numRows - missing);
            case 3:
                return Integer.toString(missing);
            case 4:
                NumberFormat pct = NumberFormat.getPercentInstance();
                pct.setMaximumFractionDigits(1);
                return pct.format(this.audit.getMissingRate(row));
            case 5:
                return Integer.toString(minPairwiseFor(row));
            default:
                throw new IllegalArgumentException("Unexpected column: " + col);
        }
    }

    /**
     * The minimum, over other variables, of the pairwise complete count involving the given variable; or the
     * variable's observed count if it is the only variable.
     */
    private int minPairwiseFor(int column) {
        int[][] counts = this.audit.getPairwiseCompleteCounts();
        int numColumns = this.dataSet.getNumColumns();

        if (numColumns < 2) {
            return this.dataSet.getNumRows() - this.audit.getMissingCount(column);
        }

        int min = Integer.MAX_VALUE;

        for (int k = 0; k < numColumns; k++) {
            if (k == column) continue;
            if (counts[column][k] < min) min = counts[column][k];
        }

        return min;
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return dataSet;
    }
}
