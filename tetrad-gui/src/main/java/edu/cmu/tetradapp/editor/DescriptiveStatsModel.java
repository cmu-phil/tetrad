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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.StatUtils;

import javax.swing.table.AbstractTableModel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A table for descriptive statistics for each variable in a dataset.
 *
 * @author josephramsey
 */
class DescriptiveStatsModel extends AbstractTableModel {
    private static final long serialVersionUID = 23L;
    private final List<Node> vars;
    private final List<Ret> stats;
    private DataSet dataSet;

    /**
     * Constructs a new DisplayTableModel to wrap the given dataSet.
     *
     * @param dataSet the dataSet.
     */
    public DescriptiveStatsModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.vars = new ArrayList<>(dataSet.getVariables());
        Collections.sort(vars);
        this.stats = new ArrayList<>();

        for (Node n : vars) {
            this.stats.add(generateDescriptiveStats(dataSet, n));
        }
    }

    /**
     * Note that returning null here has two effects. First, it
     */
    public String getColumnName(int col) {
        if (col == 0) return  "Variable";
        return stats.get(0).names.get(col - 1);
    }

    /**
     * @return the number of rows in the wrapper table model. Guarantees that
     * this number will be at least 100.
     */
    public int getRowCount() {
        return vars.size();
    }

    /**
     * @return the number of columns in the wrapper table model. Guarantees that
     * this number will be at least 30.
     */
    public int getColumnCount() {
        return stats.get(0).stats.size() + 1;
    }

    public Object getValueAt(int row, int col) {
        if (col == 0) return vars.get(row).getName();
        else {
            final Number number = stats.get(row).stats.get(col - 1);
            NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

            if (number instanceof Double) {
                return nf.format(number);
            } else {
                return number;
            }
        }
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    private static class Ret {
        List<String> names;
        List<Number> stats;
    }

    public static Ret generateDescriptiveStats(DataSet dataSet, Node variable) {
        List<String> names = new ArrayList<>();
        List<Number> stats = new ArrayList<>();

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        int col = dataSet.getColumn(variable);

        // Extract the data.
        double[] data = new double[dataSet.getNumRows()];
        boolean continuous = false;

        if (variable instanceof ContinuousVariable) {
            continuous = true;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                data[i] = dataSet.getDouble(i, col);
            }
        } else {
            DiscreteVariable var = (DiscreteVariable) variable;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                String category = var.getCategory(dataSet.getInt(i, col));
                int value = Integer.parseInt(category);
                data[i] = value;
            }
        }

        double[] normalValues = DescriptiveStats.normalParams(data);

        names.add("N");
        stats.add(dataSet.getNumRows());

        names.add("Mean");
        stats.add(normalValues[0]);

        names.add("StdDev");
        stats.add(normalValues[1]);

        names.add("Variance");
        stats.add(normalValues[2]);

        names.add("Skewness");
        stats.add(StatUtils.skewness(data));

        names.add("Kurtosis");
        stats.add(StatUtils.kurtosis(data));

        names.add("Skewness");
        stats.add(StatUtils.skewness(data));



        if (continuous) {
            double[] median = DescriptiveStats.median(data);

            names.add("SE Mean");
            stats.add(StatUtils.skewness(data));

            names.add("Median");
            stats.add(median[0]);

            names.add("Minimum");
            stats.add(median[1]);

            names.add("Maximum");
            stats.add(median[2]);
        }

//        table.setToken(rowindex, 0, "Constant Columns:");
//        java.util.List<Node> constantColumns = DataUtils.getConstantColumns(dataSet);
//        table.setToken(rowindex++, 1, constantColumns.isEmpty() ? "None" : constantColumns.toString());
//
//        table.setToken(rowindex, 0, "Example Nonsingular (2 - 3 vars):");
//
//        CovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
//        List<Node> exampleNonsingular = DataUtils.getExampleNonsingular(covarianceMatrix, 3);
//        table.setToken(rowindex, 1, exampleNonsingular == null ? "None" : exampleNonsingular.toString());

//        b.append(table);

        Ret ret = new Ret();
        ret.names = names;
        ret.stats = stats;

        return ret;
    }

}