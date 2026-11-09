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

import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.StatUtils;

import javax.swing.table.AbstractTableModel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final DataSet dataSet;

    /**
     * Constructs a new DisplayTableModel to wrap the given dataSet.
     *
     * @param dataSet the dataSet.
     */
    public DescriptiveStatsModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.vars = new ArrayList<>(dataSet.getVariables());
        vars.sort(NaturalSort.naturalComparator());
        this.stats = new ArrayList<>();

        for (Node n : vars) {
            this.stats.add(generateDescriptiveStats(dataSet, n));
        }
    }

    /**
     * <p>generateDescriptiveStats.</p>
     *
     * @param dataSet  a {@link edu.cmu.tetrad.data.DataSet} object
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetradapp.editor.DescriptiveStatsModel.Ret} object
     */
    public static Ret generateDescriptiveStats(DataSet dataSet, Node variable) {
        List<String> names = new ArrayList<>();
        List<Object> stats = new ArrayList<>();

        int col = dataSet.getColumnIndex(variable);
        int numRows = dataSet.getNumRows();

        boolean continuous = variable instanceof ContinuousVariable;

        // Extract the observed (non-missing) data, counting missing values as
        // we go. For continuous variables missing values are NaN; for
        // discrete variables the marker is DiscreteVariable.MISSING_VALUE
        // (-99), which must not be treated as a category index. Discrete
        // variables whose categories are not integers (e.g., string
        // categories) get counts only; their moments are not defined.
        int numMissing = 0;
        boolean numeric = true;
        List<Double> observed = new ArrayList<>();

        if (continuous) {
            for (int i = 0; i < numRows; i++) {
                double value = dataSet.getDouble(i, col);

                if (Double.isNaN(value)) {
                    numMissing++;
                } else {
                    observed.add(value);
                }
            }
        } else {
            DiscreteVariable var = (DiscreteVariable) variable;

            for (int i = 0; i < numRows; i++) {
                int index = dataSet.getInt(i, col);

                if (index == DiscreteVariable.MISSING_VALUE) {
                    numMissing++;
                    continue;
                }

                if (numeric) {
                    try {
                        observed.add((double) Integer.parseInt(var.getCategory(index)));
                    } catch (NumberFormatException e) {
                        numeric = false;
                        observed.clear();
                    }
                }
            }
        }

        double[] data = new double[observed.size()];
        for (int i = 0; i < data.length; i++) data[i] = observed.get(i);

        // All statistics below are calculated over the observed values only.
        boolean haveMoments = numeric && data.length > 1;
        double[] normalValues = haveMoments ? DescriptiveStats.normalParams(data) : null;

        names.add("N");
        stats.add(numRows);

        names.add("Missing");
        stats.add(numMissing);

        names.add("Mean");
        stats.add(haveMoments ? (Object) normalValues[0] : "-");

        names.add("StdDev");
        stats.add(haveMoments ? (Object) normalValues[1] : "-");

        names.add("Variance");
        stats.add(haveMoments ? (Object) normalValues[2] : "-");

        names.add("Skewness");
        stats.add(haveMoments ? (Object) StatUtils.skewness(data) : "-");

        names.add("Kurtosis");
        stats.add(haveMoments ? (Object) StatUtils.kurtosis(data) : "-");

        // The remaining columns apply only to continuous variables, but they
        // are added (as "-") for every variable so that all rows have the
        // same columns. (Previously, mixed datasets produced ragged rows,
        // causing an IndexOutOfBoundsException when the table was rendered.)
        if (continuous && haveMoments) {
            double[] median = DescriptiveStats.median(data);

            names.add("SE_Mean");
            stats.add(DescriptiveStats.standardErrorMean(normalValues[1], data.length));

            names.add("Median");
            stats.add(median[0]);

            names.add("Minimum");
            stats.add(median[1]);

            names.add("Maximum");
            stats.add(median[2]);

            AndersonDarlingTest andersonDarlingTest = new AndersonDarlingTest(data);

            names.add("A2");
            stats.add(andersonDarlingTest.getASquared());

            names.add("A2*");
            stats.add(andersonDarlingTest.getASquaredStar());

            names.add("AD-p");
            stats.add(andersonDarlingTest.getP());

            double[] ksResults = NormalityTests.kolmogorovSmirnov(dataSet, (ContinuousVariable) variable);

            String[] pass = new String[5];

            if (Double.isNaN(ksResults[0])) {
                Arrays.fill(pass, "-");
            } else {
                Arrays.fill(pass, "FAIL");
                if (ksResults[0] < ksResults[1]) pass[0] = "ACCEPT";
                if (ksResults[0] < ksResults[2]) pass[1] = "ACCEPT";
                if (ksResults[0] < ksResults[3]) pass[2] = "ACCEPT";
                if (ksResults[0] < ksResults[4]) pass[3] = "ACCEPT";
                if (ksResults[0] < ksResults[5]) pass[4] = "ACCEPT";
            }

            names.add("KS.2");
            stats.add(pass[0]);

            names.add("KS.15");
            stats.add(pass[1]);

            names.add("KS.1");
            stats.add(pass[2]);

            names.add("KS.05");
            stats.add(pass[3]);

            names.add("KS.01");
            stats.add(pass[4]);
        } else {
            for (String name : new String[]{"SE_Mean", "Median", "Minimum", "Maximum",
                    "A2", "A2*", "AD-p", "KS.2", "KS.15", "KS.1", "KS.05", "KS.01"}) {
                names.add(name);
                stats.add("-");
            }
        }

        Ret ret = new Ret();
        ret.names = names;
        ret.stats = stats;

        return ret;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that returning null here has two effects. First, it
     */
    public String getColumnName(int col) {
        if (col == 0) return "Variable";
        if (stats.isEmpty()) return "";
        return stats.get(0).names.get(col - 1);
    }

    /**
     * <p>getRowCount.</p>
     *
     * @return the number of rows in the wrapper table model. Guarantees that this number will be at least 100.
     */
    public int getRowCount() {
        return vars.size();
    }

    /**
     * <p>getColumnCount.</p>
     *
     * @return the number of columns in the wrapper table model. Guarantees that this number will be at least 30.
     */
    public int getColumnCount() {
        if (stats.isEmpty()) return 1;
        return stats.get(0).stats.size() + 1;
    }

    /**
     * {@inheritDoc}
     */
    public Object getValueAt(int row, int col) {
        if (col == 0) return vars.get(row).getName();
        else {
            final Object o = stats.get(row).stats.get(col - 1);

            if (o instanceof Double number) {
                if (Double.isNaN(number)) return "-";
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                return nf.format(number);
            } else if (o instanceof Integer number) {
                return Integer.toString(number);
            } else if (o instanceof String) {
                return o.toString();
            } else {
                throw new IllegalArgumentException("Unexpected value type.");
            }
        }
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return dataSet;
    }

    private static class Ret {
        List<String> names;
        List<Object> stats;
    }

}

