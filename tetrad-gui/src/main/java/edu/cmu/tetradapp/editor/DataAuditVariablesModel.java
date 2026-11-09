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
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.table.AbstractTableModel;
import java.text.NumberFormat;
import java.util.List;

/**
 * A table of per-variable facts for the Data Audit dialog, one row per variable in dataset column order (so that
 * rows correspond to audit column indices). Missingness facts come from the {@link MissingDataAudit}; distinct
 * observed counts, Anderson-Darling p-values, and lag-1 autocorrelations (pooled and, when a serial-dependence
 * grouping variable is selected in the dialog, within-group) come from the {@link DataAudit}. Dataset-level facts,
 * findings, and advice are not in this table; the dialog presents those separately.
 *
 * @author josephramsey
 */
class DataAuditVariablesModel extends AbstractTableModel {
    private static final long serialVersionUID = 23L;

    /**
     * The column headers, in order.
     */
    private static final String[] COLUMNS = {
            "Variable", "Type", "Observed", "Missing", "Missing %", "Distinct", "Levels (counts)", "AD p",
            "Min Pairwise n", "Lag-1 r (pooled)", "Lag-1 r (grouped)"
    };

    /**
     * The maximum number of levels rendered in the "Levels (counts)" column before eliding.
     */
    private static final int MAX_LEVELS_SHOWN = 8;

    /**
     * The dataset being audited.
     */
    private final DataSet dataSet;

    /**
     * The audit of the dataset.
     */
    private final DataAudit audit;

    /**
     * An audit of the dataset with a serial-dependence grouping variable configured, or null when no grouping is
     * selected. Only its lag-1 autocorrelations are consulted; the pooled audit remains the source for everything
     * else, so the pooled and within-group values can be compared side by side (the comparison that distinguishes
     * genuine sequential dependence from block structure).
     */
    private final DataAudit groupedAudit;

    /**
     * The missingness audit of the dataset. (The DataAudit's delegated missingness audit is null when the dataset is
     * complete, so the dialog supplies one unconditionally.)
     */
    private final MissingDataAudit missingAudit;

    /**
     * Constructs the model with no serial-dependence grouping.
     *
     * @param dataSet      the dataset.
     * @param audit        the audit of that dataset.
     * @param missingAudit the missingness audit of that dataset.
     */
    public DataAuditVariablesModel(DataSet dataSet, DataAudit audit, MissingDataAudit missingAudit) {
        this(dataSet, audit, null, missingAudit);
    }

    /**
     * Constructs the model.
     *
     * @param dataSet      the dataset.
     * @param audit        the (pooled) audit of that dataset.
     * @param groupedAudit an audit with a serial-dependence grouping variable configured, or null for none.
     * @param missingAudit the missingness audit of that dataset.
     */
    public DataAuditVariablesModel(DataSet dataSet, DataAudit audit, DataAudit groupedAudit,
                                   MissingDataAudit missingAudit) {
        this.dataSet = dataSet;
        this.audit = audit;
        this.groupedAudit = groupedAudit;
        this.missingAudit = missingAudit;
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
        String name = variable.getName();
        int numRows = this.dataSet.getNumRows();
        int missing = this.missingAudit.getMissingCount(row);

        switch (col) {
            case 0:
                return name;
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
                return pct.format(this.missingAudit.getMissingRate(row));
            case 5:
                Integer distinct = this.audit.getObservedDistinctCounts().get(name);
                return distinct == null ? "-" : Integer.toString(distinct);
            case 6:
                return variable instanceof DiscreteVariable var ? levelCounts(var, row) : "-";
            case 7:
                Double adP = this.audit.getAdPValues().get(name);
                if (adP == null || Double.isNaN(adP)) return "-";
                return NumberFormatUtil.getInstance().getNumberFormat().format(adP);
            case 8:
                return Integer.toString(minPairwiseFor(row));
            case 9:
                return formatR1(this.audit, name);
            case 10:
                return this.groupedAudit == null ? "-" : formatR1(this.groupedAudit, name);
            default:
                throw new IllegalArgumentException("Unexpected column: " + col);
        }
    }

    /**
     * The given audit's lag-1 autocorrelation for the named variable, formatted; or "-" when unavailable (discrete
     * variables, too few observed values, or negligible within-group variance).
     */
    private static String formatR1(DataAudit audit, String name) {
        Double r1 = audit.getLag1Autocorrelations().get(name);
        if (r1 == null || Double.isNaN(r1)) return "-";
        return NumberFormatUtil.getInstance().getNumberFormat().format(r1);
    }

    /**
     * Observed per-category counts for a discrete variable, as "cat: n" pairs in category order, eliding after
     * {@link #MAX_LEVELS_SHOWN} levels. Missing values (the -99 marker) are not counted in any category.
     */
    private String levelCounts(DiscreteVariable var, int column) {
        int numCategories = var.getNumCategories();
        int[] counts = new int[numCategories];

        for (int i = 0; i < this.dataSet.getNumRows(); i++) {
            int value = this.dataSet.getInt(i, column);
            if (value >= 0 && value < numCategories) counts[value]++;
        }

        StringBuilder b = new StringBuilder();
        int shown = Math.min(numCategories, MAX_LEVELS_SHOWN);

        for (int k = 0; k < shown; k++) {
            if (k > 0) b.append(", ");
            b.append(var.getCategory(k)).append(": ").append(counts[k]);
        }

        if (numCategories > MAX_LEVELS_SHOWN) {
            b.append(", ... (").append(numCategories).append(" levels)");
        }

        return b.toString();
    }

    /**
     * The minimum, over other variables, of the pairwise complete count involving the given variable; or the
     * variable's observed count if it is the only variable.
     */
    private int minPairwiseFor(int column) {
        int[][] counts = this.missingAudit.getPairwiseCompleteCounts();
        int numColumns = this.dataSet.getNumColumns();

        if (numColumns < 2) {
            return this.dataSet.getNumRows() - this.missingAudit.getMissingCount(column);
        }

        int min = Integer.MAX_VALUE;

        for (int k = 0; k < numColumns; k++) {
            if (k == column) continue;
            if (counts[column][k] < min) min = counts[column][k];
        }

        return min;
    }
}
