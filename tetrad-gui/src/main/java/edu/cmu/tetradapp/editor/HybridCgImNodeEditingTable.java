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

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal hybrid CG editor table: Each row corresponds to a (discrete-parent configuration) for one target node.
 * Columns: [Node, DiscreteConfig, Mean, Variance, Betas]
 */
public class HybridCgImNodeEditingTable extends AbstractTableModel {

    public static final int COL_NODE = 0;
    public static final int COL_CFG = 1;
    public static final int COL_MEAN = 2;
    public static final int COL_VAR = 3;
    public static final int COL_BETA = 4;

    private final String[] columns = {"Node", "Config", "Mean", "Variance", "Betas (comma-separated)"};
    private final List<Row> rows = new ArrayList<>();

    public HybridCgImNodeEditingTable() {
        // TODO seed with something if you want; otherwise, provide setters to populate.
        // Example placeholder:
        rows.add(new Row("Y", "A=0,B=0", 0.0, 1.0, new double[]{0.0, 0.0}));
    }

    // --- small helpers ---
    private static String betasToString(double[] b) {
        if (b == null || b.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(b[i]);
        }
        return sb.toString();
    }

    private static double[] parseBetas(String s) {
        if (s.isEmpty()) return new double[0];
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int c) {
        return columns[c];
    }

    @Override
    public boolean isCellEditable(int r, int c) {
        return c != COL_NODE;
    }

    @Override
    public Object getValueAt(int r, int c) {
        Row row = rows.get(r);
        return switch (c) {
            case COL_NODE -> row.nodeName;
            case COL_CFG -> row.discreteConfig;
            case COL_MEAN -> row.mean;
            case COL_VAR -> row.variance;
            case COL_BETA -> betasToString(row.betas);
            default -> "";
        };
    }

    @Override
    public void setValueAt(Object aValue, int r, int c) {
        Row row = rows.get(r);
        if (aValue == null) return;
        try {
            switch (c) {
                case COL_CFG -> row.discreteConfig = aValue.toString().trim();
                case COL_MEAN -> row.mean = Double.parseDouble(aValue.toString());
                case COL_VAR -> {
                    double v = Double.parseDouble(aValue.toString());
                    if (v <= 0) throw new IllegalArgumentException("Variance must be > 0.");
                    row.variance = v;
                }
                case COL_BETA -> row.betas = parseBetas(aValue.toString().trim());
            }
            fireTableRowsUpdated(r, r);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Edit Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void addDiscreteConfig() {
        // TODO: compute next config string based on current targetâs discrete parents.
        rows.add(new Row("Y", "A=0,B=1", 0.0, 1.0, new double[]{0.0, 0.0}));
        int r = rows.size() - 1;
        fireTableRowsInserted(r, r);
    }

    public void deleteSelectedConfig(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        rows.remove(rowIndex);
        fireTableDataChanged();
    }

    public void validateAndNormalize() {
        // For HybridCg, no row-wise normalization like CPTs; you can check variance>0, NaNs, etc.
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (!(row.variance > 0)) {
                JOptionPane.showMessageDialog(null, "Row " + r + " has non-positive variance.", "Validation", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    // --- API to integrate with estimator/model layer later ---
    public List<Object> toParameterBlocks() {
        // TODO: convert rows to your engineâs parameter objects (e.g., HybridCgIm blocks).
        return List.of();
    }

    public void fromParameterBlocks(List<Object> blocks) {
        // TODO: populate rows from a fitted HybridCgIm model (SEM or MLE layer).
    }

    /**
     * A tiny row holder. In practice youâll point these at your HybridCgIm param objects.
     */
    static class Row {
        String nodeName;
        String discreteConfig; // e.g. "A=0,B=1"
        double mean;
        double variance;
        double[] betas; // regression coefficients for continuous parents, in a fixed order

        Row(String nodeName, String cfg, double mean, double variance, double[] betas) {
            this.nodeName = nodeName;
            this.discreteConfig = cfg;
            this.mean = mean;
            this.variance = variance;
            this.betas = (betas == null ? new double[0] : betas.clone());
        }
    }
}
