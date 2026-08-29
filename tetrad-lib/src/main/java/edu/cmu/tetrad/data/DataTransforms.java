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

package edu.cmu.tetrad.data;


import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.random.RandomGenerator;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.rmi.MarshalledObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>DataTransforms class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DataTransforms {

    /**
     * Prevent instantiation.
     */
    private DataTransforms() {
    }

    /**
     * Log or unlog data
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param a       a double
     * @param isUnlog a boolean
     * @param base    a int
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet logData(DataSet dataSet, double a, boolean isUnlog, int base) {
        Map<String, LogTransformSpec> specs = new HashMap<>();
        LogTransformSpec spec = new LogTransformSpec(a, base, isUnlog);

        // Discrete columns are passed through unchanged, as before; omitting them from the spec map has
        // exactly that effect.
        for (Node variable : dataSet.getVariables()) {
            if (!(variable instanceof DiscreteVariable)) {
                specs.put(variable.getName(), spec);
            }
        }

        return logData(dataSet, specs);
    }

    /**
     * Applies a logarithmic transform to selected variables of the given dataset, each with its own offset, base,
     * and direction.
     * <p>
     * Variables absent from the map are copied through unchanged, as are discrete variables (which are never
     * transformed even if named in the map). Passing a map that assigns the same spec to every continuous variable
     * reproduces {@link #logData(DataSet, double, boolean, int)} exactly.
     * <p>
     * No check is made that the offsets keep values in the domain of the logarithm; a variable whose values are not
     * strictly positive under its offset will yield NaN or negative infinity, exactly as the dataset-wide transform
     * does. {@link LogTransformSpec#safeOffsetFor(double[])} is available to callers that want to propose a safe
     * offset to the user first.
     *
     * @param dataSet The dataset to transform.
     * @param specs   A map from variable name to the transform to apply to that variable.
     * @return The transformed dataset.
     */
    public static DataSet logData(DataSet dataSet, Map<String, LogTransformSpec> specs) {
        Matrix data = dataSet.getDoubleData();
        Matrix X = data.like();

        for (int j = 0; j < data.getNumColumns(); j++) {
            double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());
            double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());

            Node variable = dataSet.getVariable(j);
            LogTransformSpec spec = specs == null ? null : specs.get(variable.getName());

            if (variable instanceof DiscreteVariable || spec == null) {
                X.assignColumn(j, new Vector(x1));
                continue;
            }

            for (int i = 0; i < x1.length; i++) {
                x1[i] = spec.apply(x1Orig[i]);
            }

            X.assignColumn(j, new Vector(x1));
        }

        return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param dataSets a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> standardizeData(List<DataSet> dataSets) {
        List<DataSet> outList = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            Matrix data2 = standardizeData(dataSet.getDoubleData(), dataSet.getVariables());
            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), dataSet.getVariables());
            outList.add(dataSet2);
        }

        return outList;
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet standardizeData(DataSet dataSet) {
        List<DataSet> dataSets = Collections.singletonList(dataSet);
        List<DataSet> outList = standardizeData(dataSets);
        return outList.getFirst();
    }

    /**
     * <p>center.</p>
     *
     * @param dataList a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> center(List<DataSet> dataList) {
        List<DataSet> dataSets = new ArrayList<>(dataList);
        List<DataSet> outList = new ArrayList<>();

        for (DataSet model : dataSets) {
            if (model == null) {
                throw new NullPointerException("Missing dataset.");
            }

            if (!(model.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + model.getName());
            }

            Matrix data2 = centerData(model.getDoubleData());
            List<Node> list = model.getVariables();
            List<Node> list2 = new ArrayList<>(list);

            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), list2);
            outList.add(dataSet2);
        }

        return outList;
    }

    /**
     * <p>discretize.</p>
     *
     * @param dataSet         a {@link edu.cmu.tetrad.data.DataSet} object
     * @param numCategories   a int
     * @param variablesCopied a boolean
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet discretize(DataSet dataSet, int numCategories, boolean variablesCopied) {
        Discretizer discretizer = new Discretizer(dataSet);
        discretizer.setVariablesCopied(variablesCopied);

        for (Node node : dataSet.getVariables()) {
//            if (dataSet.getVariable(node.getNode()) instanceof ContinuousVariable) {
            discretizer.equalIntervals(node, numCategories);
//            }
        }

        return discretizer.discretize();
    }

    /**
     * <p>convertNumericalDiscreteToContinuous.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     * @throws java.lang.NumberFormatException if any.
     */
    public static DataSet convertNumericalDiscreteToContinuous(
            DataSet dataSet) throws NumberFormatException {
        List<Node> variables = new ArrayList<>();

        for (Node variable : dataSet.getVariables()) {
            if (variable instanceof ContinuousVariable) {
                variables.add(variable);
            } else {
                variables.add(new ContinuousVariable(variable.getName()));
            }
        }

        DataSet continuousData = new BoxDataSet(new VerticalDoubleDataBox(dataSet.getNumRows(), variables.size()), variables);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node variable = dataSet.getVariable(j);

            if (variable instanceof ContinuousVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    continuousData.setDouble(i, j, dataSet.getDouble(i, j));
                }
            } else {
                DiscreteVariable discreteVariable = (DiscreteVariable) variable;

                boolean allNumerical = true;

                for (String cat : discreteVariable.getCategories()) {
                    try {
                        Double.parseDouble(cat);
                    } catch (NumberFormatException e) {
                        allNumerical = false;
                        break;
                    }
                }


                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    int index = dataSet.getInt(i, j);
                    String catName = discreteVariable.getCategory(index);
                    double value;

                    if (catName.equals("*")) {
                        value = Double.NaN;
                    } else {
                        if (allNumerical) {
                            value = Double.parseDouble(catName);
                        } else {
                            value = index;
                        }
                    }

                    continuousData.setDouble(i, j, value);
                }
            }
        }

        return continuousData;
    }

    /**
     * <p>concatenate.</p>
     *
     * @param dataSet1 a {@link edu.cmu.tetrad.data.DataSet} object
     * @param dataSet2 a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet concatenate(DataSet dataSet1, DataSet dataSet2) {
        List<Node> vars1 = dataSet1.getVariables();
        List<Node> vars2 = dataSet2.getVariables();
        Map<String, Integer> varMap2 = new HashMap<>();
        for (int i = 0; i < vars2.size(); i++) {
            varMap2.put(vars2.get(i).getName(), i);
        }
        int rows1 = dataSet1.getNumRows();
        int rows2 = dataSet2.getNumRows();
        int cols1 = dataSet1.getNumColumns();

        Matrix concatMatrix = new Matrix(rows1 + rows2, cols1);
        Matrix matrix1 = dataSet1.getDoubleData();
        Matrix matrix2 = dataSet2.getDoubleData();

        for (int i = 0; i < vars1.size(); i++) {
            int var2 = varMap2.get(vars1.get(i).getName());
            for (int j = 0; j < rows1; j++) {
                concatMatrix.set(j, i, matrix1.get(j, i));
            }
            for (int j = 0; j < rows2; j++) {
                concatMatrix.set(j + rows1, i, matrix2.get(j, var2));
            }
        }

        return new BoxDataSet(new VerticalDoubleDataBox(concatMatrix.transpose().toArray()), vars1);
    }

    /**
     * <p>concatenate.</p>
     *
     * @param dataSets a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet concatenate(DataSet... dataSets) {
        List<DataSet> _dataSets = new ArrayList<>();

        Collections.addAll(_dataSets, dataSets);

        return concatenate(_dataSets);
    }

    // Trying to optimize some.

    /**
     * <p>concatenate.</p>
     *
     * @param dataSets a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet concatenate(List<DataSet> dataSets) {
        int totalSampleSize = 0;

        for (DataSet dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        int numColumns = dataSets.getFirst().getNumColumns();
        Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (DataSet dataSet : dataSets) {
            Matrix _data = dataSet.getDoubleData();
            r = _data.getNumRows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, _data.get(i, j));
                }
            }

            q += r;
        }

        return new BoxDataSet(new VerticalDoubleDataBox(allData.transpose().toArray()), dataSets.getFirst().getVariables());
    }

    /**
     * Appends a discrete block-ID column to the data set, in place, by run-length encoding over the given key
     * columns: a new block starts at each row where any key column's value changes from the previous row. This
     * recovers repeated-measures block structure from files sorted by configuration (as in the Airfoil self-noise
     * data, where the geometry columns are constant within each swept configuration), giving the analogue of the
     * TOWN column in the corrected Boston Housing data. The appended column is bookkeeping, not a variable of the
     * system: it should be excluded from causal searches, and its intended use is to name the block structure in
     * grouped analyses - in particular as the data audit's serial grouping variable, which recomputes the serial
     * dependence check with per-block centering, separating block structure from genuine time-series structure.
     * <p>
     * Encoding is by run in file order, not by unique key tuple: if the same configuration recurs later in the
     * file, it starts a new block, which is the correct semantics for file-order diagnostics. Category labels are
     * "c0", "c1", ... in order of appearance; string labels also keep the column discrete under the tabular reader
     * regardless of the maximum-categories setting, if the data set is saved and reloaded. Missing continuous
     * values (NaN) compare equal to each other for the purpose of detecting changes.
     *
     * @param dataSet    the data set to modify in place.
     * @param keyColumns the names of the columns whose joint value defines a configuration.
     * @param columnName the name for the appended column (must not already exist).
     * @return the number of blocks.
     * @throws IllegalArgumentException if no key columns are given, a key column is missing, the column name is
     *                                  blank or already exists, or the data set has no rows.
     */
    /**
     * Appends within-block centered ("fixed effects" / within-transformed) copies of the data set's continuous
     * columns, in place: for each continuous column that varies within blocks, a new column named
     * name + suffix is appended holding the values minus their within-block observed means. Centering on the block
     * removes ALL block-level variation, including unobserved block-level confounding - it conditions on the block
     * without introducing a many-level discrete variable - so searches run on the centered columns estimate
     * within-block structure only; between-block relationships are removed by construction. This is the standard
     * treatment for multilevel data where the blocks are nuisance (subjects, towns, batches). It is NOT a remedy
     * for designed sweep data such as Airfoil, where the scientifically meaningful variables are block-constant:
     * centering maps those columns to zero, which is why block-constant columns (within-block variance negligible
     * relative to total variance) are skipped rather than appended.
     * <p>
     * Discrete columns and the block column itself are skipped. Missing values (NaN) are left missing and are
     * excluded from the block means. Rows in blocks with fewer than 2 observed values for a column get NaN in the
     * centered column, since a within-block deviation from a single observation is identically zero by
     * construction and carries no information.
     *
     * @param dataSet     the data set to modify in place.
     * @param blockColumn the name of the discrete column identifying blocks.
     * @param suffix      the suffix for appended column names (must be nonempty).
     * @return the names of the appended columns, in order.
     * @throws IllegalArgumentException if the block column is missing or not discrete, the suffix is blank, an
     *                                  appended name would collide with an existing column, or no continuous column
     *                                  varies within blocks.
     */
    public static List<String> appendWithinBlockCenteredColumns(DataSet dataSet, String blockColumn,
                                                                String suffix) {
        if (suffix == null || suffix.isBlank()) {
            throw new IllegalArgumentException("A nonempty suffix is required.");
        }

        int blockCol = dataSet.getVariableNames().indexOf(blockColumn);
        if (blockCol < 0) {
            throw new IllegalArgumentException("No column named '" + blockColumn + "'.");
        }
        if (!(dataSet.getVariable(blockCol) instanceof DiscreteVariable blockVar)) {
            throw new IllegalArgumentException("Block column '" + blockColumn + "' must be discrete.");
        }

        int n = dataSet.getNumRows();
        int numGroups = blockVar.getNumCategories();

        List<Integer> sourceCols = new ArrayList<>();
        List<String> newNames = new ArrayList<>();

        int numColumns = dataSet.getNumColumns();
        for (int j = 0; j < numColumns; j++) {
            if (j == blockCol || !(dataSet.getVariable(j) instanceof ContinuousVariable)) continue;

            // Within-block vs. total variance, over observed values.
            double[] sum = new double[numGroups];
            double[] sumSq = new double[numGroups];
            int[] count = new int[numGroups];
            double totalSum = 0, totalSumSq = 0;
            int totalCount = 0;

            for (int r = 0; r < n; r++) {
                double v = dataSet.getDouble(r, j);
                if (Double.isNaN(v)) continue;
                int g = dataSet.getInt(r, blockCol);
                sum[g] += v;
                sumSq[g] += v * v;
                count[g]++;
                totalSum += v;
                totalSumSq += v * v;
                totalCount++;
            }

            if (totalCount < 2) continue;

            double withinSS = 0;
            for (int g = 0; g < numGroups; g++) {
                if (count[g] > 0) withinSS += sumSq[g] - sum[g] * sum[g] / count[g];
            }
            double totalSS = totalSumSq - totalSum * totalSum / totalCount;

            if (totalSS <= 0 || withinSS <= 1e-12 * totalSS) continue; // block-constant: skip

            String newName = dataSet.getVariable(j).getName() + suffix;
            if (dataSet.getVariableNames().contains(newName) || newNames.contains(newName)) {
                throw new IllegalArgumentException("A column named '" + newName + "' already exists.");
            }

            sourceCols.add(j);
            newNames.add(newName);
        }

        if (sourceCols.isEmpty()) {
            throw new IllegalArgumentException("No continuous column varies within blocks of '"
                    + blockColumn + "'.");
        }

        for (int k = 0; k < sourceCols.size(); k++) {
            int j = sourceCols.get(k);

            double[] sum = new double[numGroups];
            int[] count = new int[numGroups];
            for (int r = 0; r < n; r++) {
                double v = dataSet.getDouble(r, j);
                if (Double.isNaN(v)) continue;
                int g = dataSet.getInt(r, blockCol);
                sum[g] += v;
                count[g]++;
            }

            dataSet.addVariable(new ContinuousVariable(newNames.get(k)));
            int col = dataSet.getNumColumns() - 1;

            for (int r = 0; r < n; r++) {
                double v = dataSet.getDouble(r, j);
                int g = dataSet.getInt(r, blockCol);
                double out = (Double.isNaN(v) || count[g] < 2) ? Double.NaN : v - sum[g] / count[g];
                dataSet.setDouble(r, col, out);
            }
        }

        return newNames;
    }

    /**
     * Appends a new block ID column to the given dataset. The block ID column partitions the dataset's rows
     * into blocks based on changes in the values of the specified key columns. Each block is represented by
     * a unique categorical value.
     *
     * @param dataSet     The dataset to which the block ID column will be appended.
     * @param keyColumns  A list of column names that determine the block boundaries in the dataset.
     * @param columnName  The name for the new block ID column to be added. Must not already exist in the dataset.
     * @return The number of unique blocks created in the dataset.
     * @throws IllegalArgumentException If no key columns are provided, the column name is empty, the dataset
     *                                  has no rows, or any of the specified key columns do not exist in the dataset.
     */
    public static int appendBlockIdColumn(DataSet dataSet, List<String> keyColumns, String columnName) {
        if (keyColumns == null || keyColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one key column is required.");
        }
        if (columnName == null || columnName.isBlank()) {
            throw new IllegalArgumentException("A nonempty column name is required.");
        }
        if (dataSet.getVariableNames().contains(columnName)) {
            throw new IllegalArgumentException("A column named '" + columnName + "' already exists.");
        }
        int n = dataSet.getNumRows();
        if (n == 0) {
            throw new IllegalArgumentException("The data set has no rows.");
        }

        int[] cols = new int[keyColumns.size()];
        boolean[] disc = new boolean[keyColumns.size()];
        for (int k = 0; k < cols.length; k++) {
            int idx = dataSet.getVariableNames().indexOf(keyColumns.get(k));
            if (idx < 0) {
                throw new IllegalArgumentException("No column named '" + keyColumns.get(k) + "'.");
            }
            cols[k] = idx;
            disc[k] = dataSet.getVariable(idx) instanceof DiscreteVariable;
        }

        int[] block = new int[n];
        int b = 0;
        for (int r = 1; r < n; r++) {
            boolean change = false;
            for (int k = 0; k < cols.length; k++) {
                if (disc[k]) {
                    if (dataSet.getInt(r, cols[k]) != dataSet.getInt(r - 1, cols[k])) {
                        change = true;
                        break;
                    }
                } else {
                    double a = dataSet.getDouble(r, cols[k]);
                    double c = dataSet.getDouble(r - 1, cols[k]);
                    if (!((Double.isNaN(a) && Double.isNaN(c)) || a == c)) {
                        change = true;
                        break;
                    }
                }
            }
            if (change) b++;
            block[r] = b;
        }

        int numBlocks = b + 1;
        List<String> categories = new ArrayList<>();
        for (int i = 0; i < numBlocks; i++) categories.add("c" + i);
        DiscreteVariable var = new DiscreteVariable(columnName, categories);
        dataSet.addVariable(var);
        int col = dataSet.getNumColumns() - 1;
        for (int r = 0; r < n; r++) dataSet.setInt(r, col, block[r]);
        return numBlocks;
    }

    /**
     * <p>restrictToMeasured.</p>
     *
     * @param fullDataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet restrictToMeasured(DataSet fullDataSet) {
        List<Node> measuredVars = new ArrayList<>();
        List<Node> latentVars = new ArrayList<>();

        for (Node node : fullDataSet.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED || node.getNodeType() == NodeType.SELECTION) {
                measuredVars.add(node);
            } else {
                latentVars.add(node);
            }
        }

        return latentVars.isEmpty() ? fullDataSet : fullDataSet.subsetColumns(measuredVars);
    }

    /**
     * <p>getResamplingDataset.</p>
     *
     * @param data       a {@link edu.cmu.tetrad.data.DataSet} object
     * @param sampleSize a int
     * @return a sample without replacement with the given sample size from the given dataset.
     */
    public static DataSet getResamplingDataset(DataSet data, int sampleSize) {
        int actualSampleSize = data.getNumRows();
        int _size = sampleSize;
        if (actualSampleSize < _size) {
            _size = actualSampleSize;
        }

        List<Integer> availRows = new ArrayList<>();
        for (int i = 0; i < actualSampleSize; i++) {
            availRows.add(i);
        }

        RandomUtil.shuffle(availRows);

        List<Integer> addedRows = new ArrayList<>();
        int[] rows = new int[_size];
        for (int i = 0; i < _size; i++) {
            int row = -1;
            int index = -1;
            while (row == -1 || addedRows.contains(row)) {
                index = RandomUtil.getInstance().nextInt(availRows.size());
                row = availRows.get(index);
            }
            rows[i] = row;
            addedRows.add(row);
            availRows.remove(index);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        Matrix matrix = data.getDoubleData();
        return new BoxDataSet(new VerticalDoubleDataBox(matrix.view(rows, cols).mat().transpose().toArray()), data.getVariables());
    }

    /**
     * Get dataset sampled without replacement.
     *
     * @param data            original dataset
     * @param sampleSize      number of data (row)
     * @param randomGenerator random number generator
     * @return dataset
     */
    public static DataSet getResamplingDataset(DataSet data, int sampleSize, RandomGenerator randomGenerator) {
        int actualSampleSize = data.getNumRows();
        int _size = sampleSize;
        if (actualSampleSize < _size) {
            _size = actualSampleSize;
        }

        List<Integer> availRows = new ArrayList<>();
        for (int i = 0; i < actualSampleSize; i++) {
            availRows.add(i);
        }

        RandomUtil.shuffle(availRows);

        List<Integer> addedRows = new ArrayList<>();
        int[] rows = new int[_size];
        for (int i = 0; i < _size; i++) {
            int row = -1;
            int index = -1;
            while (row == -1 || addedRows.contains(row)) {
                index = randomGenerator.nextInt(availRows.size());
                row = availRows.get(index);
            }
            rows[i] = row;
            addedRows.add(row);
            availRows.remove(index);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        Matrix matrix = data.getDoubleData();
        return new BoxDataSet(new VerticalDoubleDataBox(matrix.view(rows, cols).mat().transpose().toArray()), data.getVariables());
    }

    /**
     * <p>getBootstrapSample.</p>
     *
     * @param data       a {@link edu.cmu.tetrad.data.DataSet} object
     * @param sampleSize a int
     * @return a sample with replacement with the given sample size from the given dataset.
     */
    public static DataSet getBootstrapSample(DataSet data, int sampleSize) {
        int actualSampleSize = data.getNumRows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        Matrix matrix = data.getDoubleData();
        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(matrix.view(rows, cols).mat().transpose().toArray()),
                data.getVariables());
        boxDataSet.setKnowledge(data.getKnowledge());
        return boxDataSet;
    }

    /**
     * Get dataset sampled with replacement.
     *
     * @param data            original dataset
     * @param sampleSize      number of data (row)
     * @param randomGenerator random number generator
     * @return dataset
     */
    public static DataSet getBootstrapSample(DataSet data, int sampleSize, RandomGenerator randomGenerator) {
        int actualSampleSize = data.getNumRows();
        int[] rows = new int[sampleSize];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = randomGenerator.nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) {
            cols[i] = i;
        }

        Matrix matrix = data.getDoubleData();
        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(
                matrix.view(rows, cols).mat().transpose().toArray()),
                data.getVariables());
        boxDataSet.setKnowledge(data.getKnowledge());

        return boxDataSet;
    }

    /**
     * <p>split.</p>
     *
     * @param data        a {@link edu.cmu.tetrad.data.DataSet} object
     * @param percentTest a double
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> split(DataSet data, double percentTest) {
        if (percentTest <= 0 || percentTest >= 1) throw new IllegalArgumentException();

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) rows.add(i);

        RandomUtil.shuffle(rows);

        int split = (int) (rows.size() * percentTest);

        List<Integer> rows1 = new ArrayList<>();
        List<Integer> rows2 = new ArrayList<>();

        for (int i = 0; i < split; i++) {
            rows1.add(rows.get(i));
        }

        for (int i = split; i < rows.size(); i++) {
            rows2.add(rows.get(i));
        }

        int[] _rows1 = new int[rows1.size()];
        int[] _rows2 = new int[rows2.size()];

        for (int i = 0; i < rows1.size(); i++) _rows1[i] = rows1.get(i);
        for (int i = 0; i < rows2.size(); i++) _rows2[i] = rows2.get(i);

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        Matrix matrix1 = data.getDoubleData();
        BoxDataSet boxDataSet1 = new BoxDataSet(new VerticalDoubleDataBox(
                matrix1.view(_rows1, cols).mat().transpose().toArray()),
                data.getVariables());

        Matrix matrix = data.getDoubleData();
        BoxDataSet boxDataSet2 = new BoxDataSet(new VerticalDoubleDataBox(
                matrix.view(_rows2, cols).mat().transpose().toArray()),
                data.getVariables());

        List<DataSet> ret = new ArrayList<>();

        ret.add(boxDataSet1);
        ret.add(boxDataSet2);

        return ret;
    }

    /**
     * Subtracts the mean of each column from each datum that column.
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet center(DataSet data) {
        DataSet _data = data.copy();

        for (int j = 0; j < _data.getNumColumns(); j++) {
            if (_data.getVariable(j) instanceof DiscreteVariable) {
                continue;
            }

            double sum = 0.0;
            int n = 0;

            for (int i = 0; i < _data.getNumRows(); i++) {
                double v = _data.getDouble(i, j);

                if (!Double.isNaN(v)) {
                    sum += v;
                    n++;
                }
            }

            double avg = sum / n;

            for (int i = 0; i < _data.getNumRows(); i++) {
                _data.setDouble(i, j, _data.getDouble(i, j) - avg);
            }
        }

        return _data;
    }

    /**
     * <p>shuffleColumns.</p>
     *
     * @param dataModel a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet shuffleColumns(DataSet dataModel) {
        String name = dataModel.getName();
        int numVariables = dataModel.getNumColumns();

        List<Integer> indicesList = new ArrayList<>();
        for (int i = 0; i < numVariables; i++) indicesList.add(i);
        RandomUtil.shuffle(indicesList);

        int[] indices = new int[numVariables];

        for (int i = 0; i < numVariables; i++) {
            indices[i] = indicesList.get(i);
        }

        DataSet dataSet = dataModel.subsetColumns(indices);
        dataSet.setName(name);
        return dataSet;
    }

    /**
     * <p>shuffleColumns2.</p>
     *
     * @param dataSets a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<DataSet> shuffleColumns2(List<DataSet> dataSets) {
        List<Node> vars = new ArrayList<>();

        List<Node> variables = dataSets.getFirst().getVariables();
        RandomUtil.shuffle(variables);

        for (Node node : variables) {
            Node _node = dataSets.getFirst().getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        List<DataSet> ret = new ArrayList<>();

        for (DataSet m : dataSets) {
            DataSet data = m.subsetColumns(vars);
            data.setName(m.getName() + ".reordered");
            ret.add(data);
        }

        return ret;
    }

    /**
     * Returns the Gaussian copula ("nonparanormal") correlation matrix of a continuous data set: for each pair of
     * variables the sample Kendall's tau-b is computed and mapped to the latent Gaussian correlation by
     * <pre>    rho = sin(pi * tau / 2),</pre>
     * the standard inversion for a Gaussian copula (Liu, Han, Yuan, Lafferty and Wasserman 2012). Diagonal entries
     * are set to exactly 1, so the result is a correlation matrix carrying the source data set's sample size.
     * <p>
     * WHAT THIS BUYS. Under the nonparanormal model each variable is an unknown strictly monotone transformation
     * of a latent Gaussian. Pearson correlation is not invariant to those transformations and is biased toward
     * zero by them; rank correlation is invariant, so this estimator recovers the latent correlation structure
     * that a linear-Gaussian score or likelihood is written against. As a worked case: for a latent pair with
     * rho = 0.7, exponentiating one variable drops the Pearson correlation to about 0.51 while this estimator
     * returns about 0.70.
     * <p>
     * WHAT IT DOES NOT BUY. The invariance is to monotone MARGINAL transformation only. Genuine nonlinearity in a
     * conditional mean -- an interaction, a non-monotone dependence, X = f(Y, Z) for non-additive f -- is outside
     * the model and this transform does nothing about it. It is a fix for distorted margins, not a nonlinear
     * method.
     * <p>
     * CAVEATS, all reported as warnings to the log rather than by throwing:
     * <ul>
     *   <li>The elementwise map sin(pi * tau / 2) does not guarantee positive semidefiniteness. The smallest
     *       eigenvalue is checked and a warning logged if it is negative. Run a Covariance Audit on the result
     *       for the full diagnosis; likelihoods and partial correlations can behave incoherently on a
     *       non-PSD matrix.</li>
     *   <li>Discrete columns are included in the computation but the copula inversion is not correct for them
     *       (a polychoric estimator is the right tool there); a warning is logged if any are present.</li>
     *   <li>Missing values are handled by pairwise-complete deletion, which is itself a route to a non-PSD
     *       matrix and makes the single stated sample size approximate; a warning is logged if any are
     *       present.</li>
     * </ul>
     * Kendall's tau-b is computed in O(n log n) per pair by merge-sort inversion counting, not the O(n^2)
     * pair enumeration.
     *
     * @param dataSet a continuous {@link edu.cmu.tetrad.data.DataSet} object
     * @return the Gaussian copula correlation matrix, as an {@link edu.cmu.tetrad.data.ICovarianceMatrix}
     * @throws IllegalArgumentException if the data set has fewer than two rows or no variables
     */
    public static ICovarianceMatrix covarianceGaussianCopula(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data set is null.");
        }

        int p = dataSet.getNumColumns();
        int n = dataSet.getNumRows();

        if (p == 0) {
            throw new IllegalArgumentException("Data set has no variables.");
        }

        if (n < 2) {
            throw new IllegalArgumentException("Kendall's tau needs at least two rows; this data set has " + n + ".");
        }

        int numDiscrete = 0;

        for (Node node : dataSet.getVariables()) {
            if (!(node instanceof ContinuousVariable)) {
                numDiscrete++;
            }
        }

        if (numDiscrete > 0) {
            TetradLogger.getInstance().log("Gaussian copula correlation: " + numDiscrete + " of " + p
                                           + " variable(s) are not continuous. Kendall's tau-b is computed for "
                                           + "them, but the inversion sin(pi * tau / 2) assumes a continuous "
                                           + "latent Gaussian, so those entries are not the polychoric "
                                           + "correlations an ordinal model would call for.");
        }

        double[][] columns = new double[p][];
        boolean anyMissing = false;

        for (int j = 0; j < p; j++) {
            columns[j] = new double[n];

            for (int i = 0; i < n; i++) {
                double v = dataSet.getDouble(i, j);
                columns[j][i] = v;

                if (Double.isNaN(v)) {
                    anyMissing = true;
                }
            }
        }

        if (anyMissing) {
            TetradLogger.getInstance().log("Gaussian copula correlation: the data set has missing values. Each "
                                           + "pair is computed on the rows complete for that pair, so different "
                                           + "entries rest on different row sets, the stated sample size of " + n
                                           + " is an upper bound, and the assembled matrix need not be positive "
                                           + "semidefinite.");
        }

        Matrix sigma = new Matrix(p, p);

        for (int i = 0; i < p; i++) {
            sigma.set(i, i, 1.0);
        }

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double tau = kendallsTauB(columns[i], columns[j]);
                double rho = Double.isNaN(tau) ? Double.NaN : FastMath.sin(FastMath.PI * tau / 2.0);

                sigma.set(i, j, rho);
                sigma.set(j, i, rho);
            }
        }

        int numNaN = 0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                if (Double.isNaN(sigma.get(i, j))) {
                    numNaN++;
                    sigma.set(i, j, 0.0);
                    sigma.set(j, i, 0.0);
                }
            }
        }

        if (numNaN > 0) {
            TetradLogger.getInstance().log("Gaussian copula correlation: " + numNaN + " pair(s) had no defined "
                                           + "tau-b (a constant column, or too few rows complete for the pair) "
                                           + "and were set to zero. A zero here is an absence of information, "
                                           + "not evidence of independence.");
        }

        warnIfNotPsd(sigma);

        List<Node> variables = new ArrayList<>(dataSet.getVariables());

        return new CovarianceMatrix(variables, sigma, n);
    }

    /**
     * Logs a warning if the given symmetric matrix has a negative eigenvalue. Warn-only by design: the matrix is
     * returned as computed rather than projected onto the nearest correlation matrix, so the numbers a user sees
     * are the numbers the estimator produced.
     */
    private static void warnIfNotPsd(Matrix sigma) {
        try {
            org.ejml.simple.SimpleEVD<org.ejml.simple.SimpleMatrix> evd
                    = new org.ejml.simple.SimpleMatrix(sigma.toArray()).eig();

            double minEig = Double.POSITIVE_INFINITY;
            int numNegative = 0;

            for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
                double ev = evd.getEigenvalue(i).getReal();

                if (ev < minEig) {
                    minEig = ev;
                }

                if (ev < 0) {
                    numNegative++;
                }
            }

            if (numNegative > 0) {
                TetradLogger.getInstance().log("Gaussian copula correlation: the result has " + numNegative
                                               + " negative eigenvalue(s) (minimum " + minEig + "), so it is not "
                                               + "the correlation matrix of any data set. The elementwise map "
                                               + "sin(pi * tau / 2) does not preserve positive semidefiniteness. "
                                               + "Likelihoods, partial correlations and regression coefficients "
                                               + "computed from it can fail or behave incoherently; run a "
                                               + "Covariance Audit on the result for the full diagnosis.");
            }
        } catch (Exception e) {
            TetradLogger.getInstance().log("Gaussian copula correlation: the eigenvalues could not be computed, "
                                           + "so positive semidefiniteness was not checked.");
        }
    }

    /**
     * Kendall's tau-b for two columns, computed in O(n log n) by merge-sort inversion counting, with
     * pairwise-complete deletion of rows where either value is NaN.
     * <p>
     * With <code>tot</code> the number of usable row pairs, <code>xTies</code> and <code>yTies</code> the pairs
     * tied in x and in y respectively, <code>bothTies</code> the pairs tied in both, and <code>dis</code> the
     * number of discordant pairs obtained as inversions in y after sorting by (x, y):
     * <pre>    tau_b = (tot - xTies - yTies + bothTies - 2 * dis) / sqrt((tot - xTies) * (tot - yTies)).</pre>
     *
     * @param x the first column.
     * @param y the second column, the same length as the first.
     * @return tau-b, or NaN if it is undefined (fewer than two complete rows, or a column constant on them).
     */
    public static double kendallsTauB(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Arrays not the same length.");
        }

        int m = 0;

        for (int i = 0; i < x.length; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                m++;
            }
        }

        if (m < 2) {
            return Double.NaN;
        }

        double[] xs = new double[m];
        double[] ys = new double[m];
        int k = 0;

        for (int i = 0; i < x.length; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                xs[k] = x[i];
                ys[k] = y[i];
                k++;
            }
        }

        Integer[] order = new Integer[m];

        for (int i = 0; i < m; i++) {
            order[i] = i;
        }

        final double[] fx = xs;
        final double[] fy = ys;

        Arrays.sort(order, (aIdx, bIdx) -> {
            int c = Double.compare(fx[aIdx], fx[bIdx]);
            return c != 0 ? c : Double.compare(fy[aIdx], fy[bIdx]);
        });

        double[] sx = new double[m];
        double[] sy = new double[m];

        for (int i = 0; i < m; i++) {
            sx[i] = xs[order[i]];
            sy[i] = ys[order[i]];
        }

        long tot = (long) m * (m - 1) / 2;

        long xTies = 0;
        long bothTies = 0;

        int runStart = 0;

        for (int i = 1; i <= m; i++) {
            if (i == m || sx[i] != sx[runStart]) {
                long t = i - runStart;
                xTies += t * (t - 1) / 2;

                int innerStart = runStart;

                for (int j = runStart + 1; j <= i; j++) {
                    if (j == i || sy[j] != sy[innerStart]) {
                        long u = j - innerStart;
                        bothTies += u * (u - 1) / 2;
                        innerStart = j;
                    }
                }

                runStart = i;
            }
        }

        double[] sortedY = sy.clone();
        Arrays.sort(sortedY);

        long yTies = 0;
        runStart = 0;

        for (int i = 1; i <= m; i++) {
            if (i == m || sortedY[i] != sortedY[runStart]) {
                long t = i - runStart;
                yTies += t * (t - 1) / 2;
                runStart = i;
            }
        }

        if (tot - xTies <= 0 || tot - yTies <= 0) {
            return Double.NaN;
        }

        long dis = countInversions(sy.clone(), new double[m], 0, m - 1);

        double numerator = tot - xTies - yTies + bothTies - 2.0 * dis;
        double denominator = FastMath.sqrt((double) (tot - xTies)) * FastMath.sqrt((double) (tot - yTies));

        return numerator / denominator;
    }

    /**
     * Counts inversions in {@code a[lo..hi]} by merge sort, sorting the range in place. A pair counts as an
     * inversion only on a strict decrease, so tied values are not counted as discordant.
     */
    private static long countInversions(double[] a, double[] scratch, int lo, int hi) {
        if (lo >= hi) {
            return 0;
        }

        int mid = (lo + hi) >>> 1;

        long count = countInversions(a, scratch, lo, mid) + countInversions(a, scratch, mid + 1, hi);

        int i = lo;
        int j = mid + 1;
        int t = lo;

        while (i <= mid && j <= hi) {
            if (a[i] <= a[j]) {
                scratch[t++] = a[i++];
            } else {
                count += (mid - i + 1);
                scratch[t++] = a[j++];
            }
        }

        while (i <= mid) {
            scratch[t++] = a[i++];
        }

        while (j <= hi) {
            scratch[t++] = a[j++];
        }

        System.arraycopy(scratch, lo, a, lo, hi - lo + 1);

        return count;
    }

    /**
     * Fills a matrix with raw Kendall's tau-a values.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @deprecated This does NOT compute the nonparanormal (Gaussian copula) correlation estimator, despite the
     * name. It stores raw Kendall's tau, omitting the inversion sin(pi * tau / 2) that maps a rank correlation
     * back to the latent Gaussian correlation. Raw tau is severely attenuated relative to the correlation it is
     * standing in for -- a latent rho of 0.9 returns about 0.71, and 0.5 returns about 0.34 -- so anything scored
     * from this matrix systematically under-detects edges. It also uses tau-a rather than tau-b, so ties attenuate
     * it further, and it is O(n^2) per variable pair. Use {@link #covarianceGaussianCopula(DataSet)} instead,
     * which corrects all three. Retained only because removing a public method is a breaking change; nothing in
     * Tetrad calls it.
     */
    @Deprecated
    public static ICovarianceMatrix covarianceNonparanormalDrton(DataSet dataSet) {
        CovarianceMatrix covMatrix = new CovarianceMatrix(dataSet);
        Matrix data = dataSet.getDoubleData();
        int NTHREDS = Runtime.getRuntime().availableProcessors() * 10;
        final int EPOCH_COUNT = 100000;

        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        int runnableCount = 0;

        for (int _i = 0; _i < dataSet.getNumColumns(); _i++) {
            for (int _j = _i; _j < dataSet.getNumColumns(); _j++) {
                int i = _i;
                int j = _j;

                Runnable worker = () -> {
                    double tau = StatUtils.kendallsTau(data.getColumn(i).toArray(), data.getColumn(j).toArray());
                    covMatrix.setValue(i, j, tau);
                    covMatrix.setValue(j, i, tau);
                };

                executor.execute(worker);

                if (runnableCount < EPOCH_COUNT) {
                    runnableCount++;
//                    System.out.println(runnableCount);
                } else {
                    executor.shutdown();
                    try {
                        // Wait until all threads are finish
                        boolean b = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

                        if (b) {
                            System.out.println("Finished all threads");
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    executor = Executors.newFixedThreadPool(NTHREDS);
                    runnableCount = 0;
                }
            }
        }

        executor.shutdown();

        try {
            // Wait until all threads are finish
            boolean b = executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            if (b) {
                System.out.println("Finished all threads");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return covMatrix;
    }

//    /**
//     * <p>getNonparanormalTransformed.</p>
//     *
//     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
//     * @return a {@link edu.cmu.tetrad.data.DataSet} object
//     */
//    public static DataSet getNonparanormalTransformed(DataSet dataSet) {
//        try {
//            Matrix data = dataSet.getDoubleData();
//            Matrix X = data.like();
//            double n = dataSet.getNumRows();
////            delta = 1.0 / (4.0 * TMath.pow(n, 0.25) * TMath.sqrt(TMath.PI * TMath.log(n)));
//
//            NormalDistribution normalDistribution = new NormalDistribution();
//
//            double std = Double.NaN;
//
//            for (int j = 0; j < data.getNumColumns(); j++) {
//                double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());
//                double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.getNumRows());
//
//                double a2Orig = new AndersonDarlingTest(x1).getASquaredStar();
//
//                if (dataSet.getVariable(j) instanceof DiscreteVariable) {
//                    X.assignColumn(j, new Vector(x1));
//                    continue;
//                }
//
//                double std1 = StatUtils.sd(x1);
//                double mu1 = StatUtils.mean(x1);
//                double[] xTransformed = DataUtils.ranks(x1);
//
//                for (int i = 0; i < xTransformed.length; i++) {
//                    xTransformed[i] /= n;
//                    xTransformed[i] = normalDistribution.inverseCumulativeProbability(xTransformed[i]);
//                }
//
//                if (Double.isNaN(std)) {
//                    std = StatUtils.sd(x1Orig);
//                }
//
//                for (int i = 0; i < xTransformed.length; i++) {
//                    xTransformed[i] *= std1;
//                    xTransformed[i] += mu1;
//                }
//
//                double a2Transformed = new AndersonDarlingTest(xTransformed).getASquaredStar();
//
//                double min = Double.POSITIVE_INFINITY;
//                double max = Double.NEGATIVE_INFINITY;
//
//                for (double v : xTransformed) {
//                    if (v > max && !Double.isInfinite(v)) {
//                        max = v;
//                    }
//
//                    if (v < min && !Double.isInfinite(v)) {
//                        min = v;
//                    }
//                }
//
//                for (int i = 0; i < xTransformed.length; i++) {
//                    if (xTransformed[i] == Double.POSITIVE_INFINITY) {
//                        xTransformed[i] = max;
//                    }
//
//                    if (xTransformed[i] < Double.NEGATIVE_INFINITY) {
//                        xTransformed[i] = min;
//                    }
//                }
//
//                System.out.println(dataSet.getVariable(j) + ": A^2* = " + a2Orig + " transformed A^2* = " + a2Transformed);
//
////                if (a2Transformed < a2Orig) {
//                X.assignColumn(j, new Vector(xTransformed));
////                } else {
////                    X.assignColumn(j, new Vector(x1Orig));
////                }
//            }
//
//            return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
//        } catch (OutOfRangeException e) {
//            e.printStackTrace();
//            return dataSet;
//        }
//    }


    /**
     * Returns a nonparanormal-transformed version of the dataset. Each continuous
     * column is rank-transformed and mapped through the normal quantile function,
     * then rescaled to the original column mean and standard deviation. Discrete
     * columns are left unchanged.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet getNonparanormalTransformed(DataSet dataSet) {
        try {
            Matrix data = dataSet.getDoubleData();
            Matrix X = data.like();
            double n = dataSet.getNumRows();
            NormalDistribution normal = new NormalDistribution();

            for (int j = 0; j < data.getNumColumns(); j++) {
                double[] col = data.getColumn(j).toArray();

                if (isTreatedAsDiscrete(dataSet, j, col)) {
                    X.assignColumn(j, new Vector(col));
                    continue;
                }

                double mu  = StatUtils.mean(col);
                double std = StatUtils.sd(col);

                double[] ranks = DataUtils.ranks(col);
                double[] transformed = new double[ranks.length];

                for (int i = 0; i < ranks.length; i++) {
                    transformed[i] = normal.inverseCumulativeProbability(ranks[i] / (n + 1));
                    transformed[i] = transformed[i] * std + mu;
                }

                X.assignColumn(j, new Vector(transformed));
            }

            return new BoxDataSet(
                    new VerticalDoubleDataBox(X.transpose().toArray()),
                    dataSet.getVariables());

        } catch (OutOfRangeException e) {
            e.printStackTrace();
            return dataSet;
        }
    }

    private static boolean isTreatedAsDiscrete(DataSet dataSet, int j, double[] col) {
        if (dataSet.getVariable(j) instanceof DiscreteVariable) return true;
        long uniqueCount = Arrays.stream(col).distinct().count();
        return uniqueCount <= TMath.max(10, TMath.sqrt(col.length));
    }

    /**
     * <p>removeConstantColumns.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet removeConstantColumns(DataSet dataSet) {
        int columns = dataSet.getNumColumns();
        int rows = dataSet.getNumRows();
        if (rows == 0) {
            return dataSet;
        }

        List<Integer> keepCols = new ArrayList<>();

        for (int j = 0; j < columns; j++) {
            Object previous = dataSet.getObject(0, j);
            boolean constant = true;
            for (int row = 1; row < rows; row++) {
                Object current = dataSet.getObject(row, j);
                if (!previous.equals(current)) {
                    constant = false;
                    break;
                }

                if (previous instanceof Double && current instanceof Double) {
                    double _previouw = (Double) previous;
                    double _current = (Double) current;

                    if (Double.isNaN(_previouw) && Double.isNaN(_current)) {
                        constant = false;
                        break;
                    }
                }
            }

            if (!constant) keepCols.add(j);
        }

        int[] newCols = new int[keepCols.size()];
        for (int j = 0; j < keepCols.size(); j++) newCols[j] = keepCols.get(j);

        return dataSet.subsetColumns(newCols);
    }

    /**
     * <p>getConstantColumns.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link java.util.List} object
     */
    public static List<Node> getConstantColumns(DataSet dataSet) {
        List<Node> constantColumns = new ArrayList<>();
        int rows = dataSet.getNumRows();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Object first = dataSet.getObject(0, j);
            boolean constant = true;

            for (int row = 1; row < rows; row++) {
                Object current = dataSet.getObject(row, j);
                if (!first.equals(current)) {
                    constant = false;
                    break;
                }
            }

            if (constant) {
                constantColumns.add(dataSet.getVariable(j));
            }
        }

        return constantColumns;
    }

    /**
     * <p>removeRandomColumns.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param aDouble a double
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet removeRandomColumns(DataSet dataSet, double aDouble) {
        int columns = dataSet.getNumColumns();
        int rows = dataSet.getNumRows();
        if (rows == 0) {
            return dataSet;
        }

        List<Integer> keepCols = new ArrayList<>();

        for (int j = 0; j < columns; j++) {
            if (RandomUtil.getInstance().nextDouble() > aDouble) {
                keepCols.add(j);
            }
        }

        int[] newCols = new int[keepCols.size()];
        for (int j = 0; j < keepCols.size(); j++) newCols[j] = keepCols.get(j);

        return dataSet.subsetColumns(newCols);
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix standardizeData(Matrix data) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.getNumColumns(); j++) {
            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    sum += data2.get(i, j);
                    count++;
                }
            }

            double mean = sum / count;

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data.get(i, j) - mean);
                }
            }

            double norm = 0.0;

            for (int i = 0; i < data.getNumRows(); i++) {
                double v = data2.get(i, j);

                if (!Double.isNaN(v)) {
                    norm += v * v;
                }
            }

            norm = TMath.sqrt(norm / (data.getNumRows() - 1));

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data2.get(i, j) / norm);
                }
            }
        }

        return data2;
    }

    /**
     * Standardizes the columns of the given data matrix by centering and scaling. For each column representing a
     * continuous variable, the method calculates the mean and standard deviation, subtracts the mean from each value,
     * and divides by the standard deviation. Discrete variables are ignored.
     *
     * @param data      The input data matrix to be standardized. Each column corresponds to a variable, and each row
     *                  represents an observation.
     * @param variables A list of nodes representing the variables in the data. The type of each variable (e.g.,
     *                  continuous or discrete) determines whether the variable will be standardized.
     * @return A new standardized data matrix where each continuous variable has been mean-centered and normalized by
     * its standard deviation.
     */
    public static Matrix standardizeData(Matrix data, List<Node> variables) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.getNumColumns(); j++) {
            if (variables.get(j) instanceof DiscreteVariable) {
                continue;
            }

            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    sum += data2.get(i, j);
                    count++;
                }
            }

            double mean = sum / count;

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data.get(i, j) - mean);
                }
            }

            double norm = 0.0;

            for (int i = 0; i < data.getNumRows(); i++) {
                double v = data2.get(i, j);

                if (!Double.isNaN(v)) {
                    norm += v * v;
                }
            }

            norm = TMath.sqrt(norm / (data.getNumRows() - 1));

            for (int i = 0; i < data.getNumRows(); i++) {
                if (!Double.isNaN(data2.get(i, j))) {
                    data2.set(i, j, data2.get(i, j) / norm);
                }
            }
        }

        return data2;
    }

    /**
     * <p>standardizeData.</p>
     *
     * @param data an array of  objects
     * @return an array of  objects
     */
    public static double[] standardizeData(double[] data) {
        double[] data2 = new double[data.length];

        double sum = 0.0;

        for (double d : data) {
            sum += d;
        }

        double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data2[i] = data[i] - mean;
        }

        double norm = 0.0;

        for (double v : data2) {
            norm += v * v;
        }

        norm = TMath.sqrt(norm / (data2.length - 1));

        for (int i = 0; i < data2.length; i++) {
            data2[i] = data2[i] / norm;
        }

        return data2;
    }

    /**
     * Centers the values in the given array by subtracting the mean of the array from each element.
     *
     * @param d the array of double values to be centered
     * @return a new array where each element is the original value minus the mean of the input array
     */
    public static double[] center(double[] d) {
        double sum = 0.0;

        for (double v : d) {
            sum += v;
        }

        double mean = sum / d.length;
        double[] d2 = new double[d.length];

        for (int i = 0; i < d.length; i++) {
            d2[i] = d[i] - mean;
        }

        return d2;
    }

    /**
     * <p>centerData.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix centerData(Matrix data) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.getNumColumns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                sum += data2.get(i, j);
            }

            double mean = sum / data.getNumRows();

            for (int i = 0; i < data.getNumRows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }
        }

        return data2;
    }

    /**
     * <p>concatenate.</p>
     *
     * @param dataSets a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix concatenate(Matrix... dataSets) {
        int totalSampleSize = 0;

        for (Matrix dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        int numColumns = dataSets[0].getNumColumns();
        Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (Matrix dataSet : dataSets) {
            r = dataSet.getNumRows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, dataSet.get(i, j));
                }
            }

            q += r;
        }

        return allData;
    }

    /**
     * <p>getBootstrapSample.</p>
     *
     * @param data       a {@link edu.cmu.tetrad.util.Matrix} object
     * @param sampleSize a int
     * @return a sample with replacement with the given sample size from the given dataset.
     */
    public static Matrix getBootstrapSample(Matrix data, int sampleSize) {
        int actualSampleSize = data.getNumRows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return data.view(rows, cols).mat();
    }

    /**
     * <p>copyColumn.</p>
     *
     * @param node   a {@link edu.cmu.tetrad.graph.Node} object
     * @param source a {@link edu.cmu.tetrad.data.DataSet} object
     * @param dest   a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static void copyColumn(Node node, DataSet source, DataSet dest) {
        int sourceColumn = source.getColumnIndex(node);
        int destColumn = dest.getColumnIndex(node);
        if (sourceColumn < 0) {
            throw new NullPointerException("The given node was not in the source dataset");
        }
        if (destColumn < 0) {
            throw new NullPointerException("The given node was not in the destination dataset");
        }
        int sourceRows = source.getNumRows();
        int destRows = dest.getNumRows();
        if (node instanceof ContinuousVariable) {
            for (int i = 0; i < destRows && i < sourceRows; i++) {
                dest.setDouble(i, destColumn, source.getDouble(i, sourceColumn));
            }
        } else if (node instanceof DiscreteVariable) {
            for (int i = 0; i < destRows && i < sourceRows; i++) {
                dest.setInt(i, destColumn, source.getInt(i, sourceColumn));
            }
        } else {
            throw new IllegalArgumentException("The given variable most be discrete or continuous");
        }
    }

    /**
     * Adds missing data values to cases in accordance with probabilities specified in a double array which has as many
     * elements as there are columns in the input dataset.  Hence, if the first element of the array of probabilities is
     * alpha, then the first column will contain a -99 (or other missing value code) in a given case with probability
     * alpha. This method will be useful in generating datasets which can be used to test algorithm that handle missing
     * data and/or latent variables. Author:  Frank Wimberly
     *
     * @param inData The data to which random missing data is to be added.
     * @param probs  The probability of adding missing data to each column.
     * @return The new data sets with missing data added.
     */
    public static DataSet addMissingData(
            DataSet inData, double[] probs) {
        DataSet outData;

        outData = inData.copy();

        if (probs.length != outData.getNumColumns()) {
            throw new IllegalArgumentException(
                    "Wrong number of elements in prob array");
        }

        for (double prob : probs) {
            if (prob < 0.0 || prob > 1.0) {
                throw new IllegalArgumentException("Probability out of range");
            }
        }

        for (int j = 0; j < outData.getNumColumns(); j++) {
            Node node = outData.getVariable(j);

            if (node instanceof ContinuousVariable) {
                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (RandomUtil.getInstance().nextDouble() < probs[j]) {
                        outData.setDouble(i, j, Double.NaN);
                    }
                }
            } else if (node instanceof DiscreteVariable) {
                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (RandomUtil.getInstance().nextDouble() < probs[j]) {
                        outData.setInt(i, j, -99);
                    }
                }
            }
        }

        return outData;
    }

    /**
     * <p>replaceMissingWithRandom.</p>
     *
     * @param inData a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public static DataSet replaceMissingWithRandom(DataSet inData) {
        DataSet outData;

        try {
            outData = new MarshalledObject<>(inData).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (int j = 0; j < outData.getNumColumns(); j++) {
            Node variable = outData.getVariable(j);

            if (variable instanceof DiscreteVariable) {
                List<Integer> values = new ArrayList<>();

                for (int i = 0; i < outData.getNumRows(); i++) {
                    int value = outData.getInt(i, j);
                    if (value == -99) continue;
                    values.add(value);
                }

                Collections.sort(values);

                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (outData.getInt(i, j) == -99) {
                        int value = RandomUtil.getInstance().nextInt(values.size());
                        outData.setInt(i, j, values.get(value));
                    }
                }
            } else {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < outData.getNumRows(); i++) {
                    double value = outData.getDouble(i, j);
                    if (value < min) min = value;
                    if (value > max) max = value;
                }

                for (int i = 0; i < outData.getNumRows(); i++) {
                    double random = RandomUtil.getInstance().nextDouble();
                    outData.setDouble(i, j, min + random * (max - min));
                }
            }
        }

        return outData;
    }

    /**
     * Scales the continuous variables in the given DataSet to have values in the range [-1, 1].
     * <p>
     * For each continuous column, the method computes the maximum of the absolute values of the minimum and maximum of
     * the column, and divides all values in that column by this maximum value. Discrete columns are not affected.
     *
     * @param dataSet  The DataSet containing variables to be scaled.
     * @param scaleMin The minimum value to scale to.
     * @param scaleMax The maximum value to scale to.
     * @return A new DataSet with scaled continuous variables, while discrete variables remain unchanged.
     */
    public static DataSet scale(DataSet dataSet, double scaleMin, double scaleMax) {
        dataSet = dataSet.copy();

        // For each continuous column, find the min and max of the column, then max(abs(min, max)), then divide the column by that value.
        // Ignore the discrete columns.

        for (Node node : dataSet.getVariables()) {
            scale(dataSet, scaleMin, scaleMax, node);
        }

        return dataSet;
    }

    /**
     * Scales the values of a specified node in the given dataset to a specified range [scaleMin, scaleMax]. This method
     * only processes nodes that are instances of ContinuousVariable.
     *
     * @param dataSet  the dataset containing the values to be scaled
     * @param scaleMin the minimum value of the target range
     * @param scaleMax the maximum value of the target range
     * @param node     the node corresponding to the column in the dataset to be scaled
     */
    public static void scale(DataSet dataSet, double scaleMin, double scaleMax, Node node) {
        if (node instanceof ContinuousVariable) {
            int j = dataSet.getColumnIndex(node);

            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double value = dataSet.getDouble(i, j);
                if (value < min) {
                    min = value;
                }
                if (value > max) {
                    max = value;
                }
            }

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double value = dataSet.getDouble(i, j);
                dataSet.setDouble(i, j, scale(value, min, max, scaleMin, scaleMax));
            }
        }
    }

//    private static double scale(double value, double a, double b, double scaleMin, double scaleMax) {
//        if (a == b) {
//            throw new IllegalArgumentException("Lower and upper bounds must not be the same.");
//        }
//        return 2 * scaleMax * (value - a) / (b - a) - scaleMin;
//    }

    /**
     * Scales a value from one range to another.
     *
     * @param value    The value to scale
     * @param dataMin  The minimum value of the data range
     * @param dataMax  The maximum value of the data range
     * @param scaleMin The minimum value of the scale range
     * @param scaleMax The maximum value of the scale range
     * @return The scaled value
     * @throws IllegalArgumentException if dataMin is equal to dataMax
     */
    public static double scale(double value, double dataMin, double dataMax, double scaleMin, double scaleMax) {
        if (dataMax == dataMin) {
            throw new IllegalArgumentException("dataMin and dataMax cannot be the same (division by zero).");
        }
        return scaleMin + (value - dataMin) * (scaleMax - scaleMin) / (dataMax - dataMin);
    }

    /**
     * Scales the columns of the provided dataset based on the given scale factors. Only continuous variables in the
     * dataset are scaled. Discrete variables are ignored. The method returns a new dataset with scaled values, leaving
     * the original dataset unmodified.
     *
     * @param dataSet the input dataset to be scaled
     * @param scales  an array of scale factors, where each scale corresponds to a column in the dataset
     * @return a new dataset with the continuous columns scaled by the given factors
     */
    public static DataSet scale(DataSet dataSet, double[] scales) {
        dataSet = dataSet.copy();

        // For each continuous column, find the min and max of the column, then max(abs(min, max)), then divide the column by that value.
        // Ignore the discrete columns.

        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                int j = dataSet.getColumnIndex(node);

                double scale = scales[j];

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    double value = dataSet.getDouble(i, j);
                    dataSet.setDouble(i, j, value / scale);
                }
            }
        }

        return dataSet;
    }
}


