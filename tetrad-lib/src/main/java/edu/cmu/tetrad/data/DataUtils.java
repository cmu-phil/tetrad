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

package edu.cmu.tetrad.data;

import cern.colt.list.DoubleArrayList;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.random.RandomGenerator;

import java.rmi.MarshalledObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Some static utility methods for dealing with data sets.
 *
 * @author Various folks.
 */
public final class DataUtils {


    public static void copyColumn(Node node, DataSet source, DataSet dest) {
        int sourceColumn = source.getColumn(node);
        int destColumn = dest.getColumn(node);
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
     * States whether the given column of the given data set is binary.
     *
     * @param data   Ibid.
     * @param column Ibid.
     * @return true iff the column is binary.
     */
    public static boolean isBinary(DataSet data, int column) {
        Node node = data.getVariable(column);
        int size = data.getNumRows();
        if (node instanceof DiscreteVariable) {
            for (int i = 0; i < size; i++) {
                int value = data.getInt(i, column);
                if (value != 1 && value != 0) {
                    return false;
                }
            }
        } else if (node instanceof ContinuousVariable) {
            for (int i = 0; i < size; i++) {
                double value = data.getDouble(i, column);
                if (value != 1.0 && value != 0.0) {
                    return false;
                }
            }
        } else {
            throw new IllegalArgumentException("The given column is not discrete or continuous");
        }
        return true;
    }

    /**
     * @param index Ond plus the given index.
     * @return the default category for index i. (The default category should
     * ALWAYS be obtained by calling this method.)
     */
    public static String defaultCategory(int index) {
        return Integer.toString(index);
    }

    /**
     * Adds missing data values to cases in accordance with probabilities
     * specified in a double array which has as many elements as there are
     * columns in the input dataset.  Hence, if the first element of the array of
     * probabilities is alpha, then the first column will contain a -99 (or
     * other missing value code) in a given case with probability alpha.
     * This method will be useful in generating datasets which can be used to
     * test algorithm that handle missing data and/or latent variables.
     * Author:  Frank Wimberly
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
     * A discrete data set used to construct some other serializable instances.
     */
    public static DataSet discreteSerializableInstance() {
        List<Node> variables = new LinkedList<>();
        variables.add(new DiscreteVariable("X", 2));
        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(2, variables.size()), variables);
        dataSet.setInt(0, 0, 0);
        dataSet.setInt(1, 0, 1);
        return dataSet;
    }

    /**
     * @return true iff the data sets contains a missing value.
     */
    public static boolean containsMissingValue(Matrix data) {
        for (int i = 0; i < data.rows(); i++) {
            for (int j = 0; j < data.columns(); j++) {
                if (Double.isNaN(data.get(i, j))) {
                    return true;
                }
            }
        }

        return false;
    }


    public static boolean containsMissingValue(DataSet data) {
        for (int j = 0; j < data.getNumColumns(); j++) {
            Node node = data.getVariable(j);

            if (node instanceof ContinuousVariable) {
                for (int i = 0; i < data.getNumRows(); i++) {
                    if (Double.isNaN(data.getDouble(i, j))) {
                        return true;
                    }
                }
            }

            if (node instanceof DiscreteVariable) {
                for (int i = 0; i < data.getNumRows(); i++) {
                    if (data.getInt(i, j) == DiscreteVariable.MISSING_VALUE) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Log or unlog data
     */
    public static DataSet logData(DataSet dataSet, double a, boolean isUnlog, int base) {
        Matrix data = dataSet.getDoubleData();
        Matrix X = data.like();

        for (int j = 0; j < data.columns(); j++) {
            double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());
            double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());

            if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                X.assignColumn(j, new Vector(x1));
                continue;
            }

            for (int i = 0; i < x1.length; i++) {
                if (isUnlog) {
                    if (base == 0) {
                        x1[i] = Math.exp(x1Orig[i]) - a;
                    } else {
                        x1[i] = Math.pow(base, (x1Orig[i])) - a;
                    }
                } else {
                    double log = Math.log(a + x1Orig[i]);
                    if (base == 0) {
                        x1[i] = log;
                    } else {
                        x1[i] = log / Math.log(base);
                    }
                }
            }

            X.assignColumn(j, new Vector(x1));
        }

        return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
    }


    public static Matrix standardizeData(Matrix data) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data2.rows(); i++) {
                sum += data2.get(i, j);
            }

            double mean = sum / data.rows();

            for (int i = 0; i < data.rows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }

            double norm = 0.0;

            for (int i = 0; i < data.rows(); i++) {
                double v = data2.get(i, j);
                norm += v * v;
            }

            norm = Math.sqrt(norm / (data.rows() - 1));

            for (int i = 0; i < data.rows(); i++) {
                data2.set(i, j, data2.get(i, j) / norm);
            }
        }

        return data2;
    }

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

        norm = Math.sqrt(norm / (data2.length - 1));

        for (int i = 0; i < data2.length; i++) {
            data2[i] = data2[i] / norm;
        }

        return data2;
    }

    public static DoubleArrayList standardizeData(DoubleArrayList data) {
        DoubleArrayList data2 = new DoubleArrayList(data.size());

        double sum = 0.0;

        for (int i = 0; i < data.size(); i++) {
            sum += data.get(i);
        }

        double mean = sum / data.size();

        for (int i = 0; i < data.size(); i++) {
            data2.add(data.get(i) - mean);
        }

        double norm = 0.0;

        for (int i = 0; i < data2.size(); i++) {
            double v = data2.get(i);
            norm += v * v;
        }

        norm = Math.sqrt(norm / (data2.size() - 1));

        for (int i = 0; i < data2.size(); i++) {
            data2.set(i, data2.get(i) / norm);
        }

        return data2;
    }

    public static List<DataSet> standardizeData(List<DataSet> dataSets) {
        List<DataSet> outList = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            if (!(dataSet.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + dataSet.getName());
            }

            Matrix data2 = DataUtils.standardizeData(dataSet.getDoubleData());

            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), dataSet.getVariables());
            outList.add(dataSet2);
        }

        return outList;
    }

    public static DataSet standardizeData(DataSet dataSet) {
        List<DataSet> dataSets = Collections.singletonList(dataSet);
        List<DataSet> outList = DataUtils.standardizeData(dataSets);
        return outList.get(0);
    }

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

    public static Matrix centerData(Matrix data) {
        Matrix data2 = data.copy();

        for (int j = 0; j < data2.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data2.rows(); i++) {
                sum += data2.get(i, j);
            }

            double mean = sum / data.rows();

            for (int i = 0; i < data.rows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }
        }

        return data2;
    }

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

            Matrix data2 = DataUtils.centerData(model.getDoubleData());
            List<Node> list = model.getVariables();
            List<Node> list2 = new ArrayList<>(list);

            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), list2);
            outList.add(dataSet2);
        }

        return outList;
    }


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

    public static List<Node> createContinuousVariables(String[] varNames) {
        List<Node> variables = new LinkedList<>();

        for (String varName : varNames) {
            variables.add(new ContinuousVariable(varName));
        }

        return variables;
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(ICovarianceMatrix m, Node x, Node y, List<Node> z) {
        if (x == null) {
            throw new NullPointerException();
        }

        if (y == null) {
            throw new NullPointerException();
        }

        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> variables = m.getVariables();
//        TetradMatrix _covMatrix = m.getMatrix();

        // Create index array for the given variables.
        int[] indices = new int[2 + z.size()];

        indices[0] = variables.indexOf(x);
        indices[1] = variables.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = variables.indexOf(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.
        Matrix submatrix = m.getSelection(indices, indices);

        if (DataUtils.containsMissingValue(submatrix)) {
            throw new IllegalArgumentException(
                    "Please remove or impute missing values first.");
        }

        return submatrix;
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(Matrix m, List<Node> variables, Node x, Node y, List<Node> z) {
        if (x == null) {
            throw new NullPointerException();
        }

        if (y == null) {
            throw new NullPointerException();
        }

        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        // Create index array for the given variables.
        int[] indices = new int[2 + z.size()];

        indices[0] = variables.indexOf(x);
        indices[1] = variables.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = variables.indexOf(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.

        return m.getSelection(indices, indices);
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(Matrix m, Map<Node, Integer> indexMap, Node x, Node y, List<Node> z) {
        if (x == null) {
            throw new NullPointerException();
        }

        if (y == null) {
            throw new NullPointerException();
        }

        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        // Create index array for the given variables.
        int[] indices = new int[2 + z.size()];

        indices[0] = indexMap.get(x);
        indices[1] = indexMap.get(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = indexMap.get(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.
        return m.getSelection(indices, indices);
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(ICovarianceMatrix m, Map<Node, Integer> indexMap, Node x, Node y, List<Node> z) {
        int[] indices = new int[2 + z.size()];

        indices[0] = indexMap.get(x);
        indices[1] = indexMap.get(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = indexMap.get(z.get(i));
        }

        return m.getSelection(indices, indices);
    }

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


    public static DataSet concatenate(DataSet... dataSets) {
        List<DataSet> _dataSets = new ArrayList<>();

        Collections.addAll(_dataSets, dataSets);

        return DataUtils.concatenate(_dataSets);
    }

    public static Matrix concatenate(Matrix... dataSets) {
        int totalSampleSize = 0;

        for (Matrix dataSet : dataSets) {
            totalSampleSize += dataSet.rows();
        }

        int numColumns = dataSets[0].columns();
        Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (Matrix dataSet : dataSets) {
            r = dataSet.rows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, dataSet.get(i, j));
                }
            }

            q += r;
        }

        return allData;
    }

    // Trying to optimize some.
    public static DataSet concatenate(List<DataSet> dataSets) {
        int totalSampleSize = 0;

        for (DataSet dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        int numColumns = dataSets.get(0).getNumColumns();
        Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (DataSet dataSet : dataSets) {
            Matrix _data = dataSet.getDoubleData();
            r = _data.rows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, _data.get(i, j));
                }
            }

            q += r;
        }

        return new BoxDataSet(new VerticalDoubleDataBox(allData.transpose().toArray()), dataSets.get(0).getVariables());
    }

    public static DataSet restrictToMeasured(DataSet fullDataSet) {
        List<Node> measuredVars = new ArrayList<>();
        List<Node> latentVars = new ArrayList<>();

        for (Node node : fullDataSet.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measuredVars.add(node);
            } else {
                latentVars.add(node);
            }
        }

        return latentVars.isEmpty() ? fullDataSet : fullDataSet.subsetColumns(measuredVars);
    }

    public static Vector means(Matrix data) {
        Vector means = new Vector(data.columns());

        for (int j = 0; j < means.size(); j++) {
            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < data.rows(); i++) {
                if (Double.isNaN(data.get(i, j))) {
                    continue;
                }

                sum += data.get(i, j);
                count++;
            }

            double mean = sum / count;

            means.set(j, mean);
        }

        return means;
    }

    /**
     * Column major data.
     */
    public static Vector means(double[][] data) {
        Vector means = new Vector(data.length);
        int rows = data[0].length;

        for (int j = 0; j < means.size(); j++) {
            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < rows; i++) {
                if (Double.isNaN(data[j][i])) {
                    continue;
                }

                sum += data[j][i];
                count++;
            }

            double mean = sum / count;

            means.set(j, mean);
        }

        return means;
    }

    public static Matrix cov(Matrix data) {
        for (int j = 0; j < data.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data.rows(); i++) {
                sum += data.get(i, j);
            }

            double mean = sum / data.rows();

            for (int i = 0; i < data.rows(); i++) {
                data.set(i, j, data.get(i, j) - mean);
            }
        }

        RealMatrix q = new BlockRealMatrix(data.toArray());

        RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
        RealMatrix q2 = DataUtils.times(q1, q);
        Matrix prod = new Matrix(q2.getData());

        double factor = 1.0 / (data.rows() - 1);

        for (int i = 0; i < prod.rows(); i++) {
            for (int j = 0; j < prod.columns(); j++) {
                prod.set(i, j, prod.get(i, j) * factor);
            }
        }

        return prod;
    }

    private static RealMatrix times(RealMatrix m, RealMatrix n) {
        if (m.getColumnDimension() != n.getRowDimension()) throw new IllegalArgumentException("Incompatible matrices.");

        int rowDimension = m.getRowDimension();
        int columnDimension = n.getColumnDimension();

        RealMatrix out = new BlockRealMatrix(rowDimension, columnDimension);

        int NTHREADS = Runtime.getRuntime().availableProcessors();

        ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        for (int t = 0; t < NTHREADS; t++) {
            int _t = t;

            Runnable worker = () -> {
                int chunk = rowDimension / NTHREADS + 1;
                for (int row = _t * chunk; row < Math.min((_t + 1) * chunk, rowDimension); row++) {
                    if ((row + 1) % 100 == 0) System.out.println(row + 1);

                    for (int col = 0; col < columnDimension; ++col) {
                        double sum = 0.0D;

                        int commonDimension = m.getColumnDimension();

                        for (int i = 0; i < commonDimension; ++i) {
                            sum += m.getEntry(row, i) * n.getEntry(i, col);
                        }

//                            double sum = m.getRowVector(row).dotProduct(n.getColumnVector(col));
                        out.setEntry(row, col, sum);
                    }
                }
            };

            pool.submit(worker);
        }

        while (true) {
            if (pool.isQuiescent()) break;
        }

        return out;
    }

    public static Vector mean(Matrix data) {
        Vector mean = new Vector(data.columns());

        for (int i = 0; i < data.columns(); i++) {
            mean.set(i, StatUtils.mean(data.getColumn(i).toArray()));
        }

        return mean;

    }

    /**
     * @param cov The variables and covariance matrix over the variables.
     * @return The simulated data.
     */
    public static DataSet choleskySimulation(CovarianceMatrix cov) {
        System.out.println(cov);
        int sampleSize = cov.getSampleSize();

        List<Node> variables = cov.getVariables();
        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);
        Matrix _cov = cov.getMatrix().copy();

        Matrix cholesky = MatrixUtils.cholesky(_cov);

        System.out.println("Cholesky decomposition" + cholesky);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            double[] exoData = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            for (int col = 0; col < variables.size(); col++) {
                int index = variables.indexOf(variables.get(col));
                double value = point[index];

                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    System.out.println("Value out of range: " + value);
                }

                dataSet.setDouble(row, col, value);
            }
        }

        return dataSet;
    }

    /**
     * @return a sample with replacement with the given sample size from the
     * given dataset.
     */
    public static Matrix getBootstrapSample(Matrix data, int sampleSize) {
        int actualSampleSize = data.rows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[data.columns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return data.getSelection(rows, cols);
    }

    /**
     * @return a sample without replacement with the given sample size from the
     * given dataset.
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

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()), data.getVariables());
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

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()), data.getVariables());
    }

    /**
     * @return a sample with replacement with the given sample size from the
     * given dataset.
     */
    public static DataSet getBootstrapSample(DataSet data, int sampleSize) {
        int actualSampleSize = data.getNumRows();

        int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()),
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

        BoxDataSet boxDataSet = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(rows, cols).transpose().toArray()),
                data.getVariables());
        boxDataSet.setKnowledge(data.getKnowledge());

        return boxDataSet;
    }

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

        BoxDataSet boxDataSet1 = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(_rows1, cols).transpose().toArray()),
                data.getVariables());

        BoxDataSet boxDataSet2 = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(_rows2, cols).transpose().toArray()),
                data.getVariables());

        List<DataSet> ret = new ArrayList<>();

        ret.add(boxDataSet1);
        ret.add(boxDataSet2);

        return ret;
    }

    /**
     * Subtracts the mean of each column from each datum that column.
     */
    public static DataSet center(DataSet data) {
        DataSet _data = data.copy();

        for (int j = 0; j < _data.getNumColumns(); j++) {
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

    public static List<DataSet> shuffleColumns2(List<DataSet> dataSets) {
        List<Node> vars = new ArrayList<>();

        List<Node> variables = dataSets.get(0).getVariables();
        RandomUtil.shuffle(variables);

        for (Node node : variables) {
            Node _node = dataSets.get(0).getVariable(node.getName());

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

//    function (x, npn.func = "shrinkage", npn.thresh = NULL, verbose = TRUE)
//    {
//        gcinfo(FALSE)
//        n = nrow(x)
//        d = ncol(x)
//        x.col = colnames(x)
//        x.row = rownames(x)
//        if (npn.func == "shrinkage") {
//            if (verbose)
//                cat("Conducting the nonparanormal (npn) transformation via shrunkun ECDF....")
//            x = qnorm(apply(x, 2, rank)/(n + 1))
//            x = x/sd(x[, 1])
//            if (verbose)
//                cat("done.\n")
//            rm(n, d, verbose)
//            gc()
//            colnames(x) = x.col
//            rownames(x) = x.row
//        }
//        if (npn.func == "truncation") {
//            if (verbose)
//                cat("Conducting nonparanormal (npn) transformation via truncated ECDF....")
//            if (is.null(npn.thresh))
//            npn.thresh = 1/(4 * (n^0.25) * sqrt(pi * log(n)))
//            x = qnorm(pmin(pmax(apply(x, 2, rank)/n, npn.thresh),
//                    1 - npn.thresh))
//            x = x/sd(x[, 1])
//            if (verbose)
//                cat("done.\n")
//            rm(n, d, npn.thresh, verbose)
//            gc()
//            colnames(x) = x.col
//            rownames(x) = x.row
//        }
//        if (npn.func == "skeptic") {
//            if (verbose)
//                cat("Conducting nonparanormal (npn) transformation via skeptic....")
//            x = 2 * sin(pi/6 * cor(x, method = "spearman"))
//            if (verbose)
//                cat("done.\n")
//            rm(n, d, verbose)
//            gc()
//            colnames(x) = x.col
//            rownames(x) = x.col
//        }
//        return(x)
//    }

    public static DataSet getNonparanormalTransformed(DataSet dataSet) {
        try {
            Matrix data = dataSet.getDoubleData();
            Matrix X = data.like();
            double n = dataSet.getNumRows();
//            delta = 1.0 / (4.0 * Math.pow(n, 0.25) * Math.sqrt(Math.PI * Math.log(n)));

            NormalDistribution normalDistribution = new NormalDistribution();

            double std = Double.NaN;

            for (int j = 0; j < data.columns(); j++) {
                double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());
                double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());

                double a2Orig = new AndersonDarlingTest(x1).getASquaredStar();

                if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                    X.assignColumn(j, new Vector(x1));
                    continue;
                }

                double std1 = StatUtils.sd(x1);
                double mu1 = StatUtils.mean(x1);
                double[] xTransformed = DataUtils.ranks(data, x1);

                for (int i = 0; i < xTransformed.length; i++) {
                    xTransformed[i] /= n;
                    xTransformed[i] = normalDistribution.inverseCumulativeProbability(xTransformed[i]);
                }

                if (Double.isNaN(std)) {
                    std = StatUtils.sd(x1Orig);
                }

                for (int i = 0; i < xTransformed.length; i++) {
                    xTransformed[i] *= std1;
                    xTransformed[i] += mu1;
                }

                double a2Transformed = new AndersonDarlingTest(xTransformed).getASquaredStar();

                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (double v : xTransformed) {
                    if (v > max && !Double.isInfinite(v)) {
                        max = v;
                    }

                    if (v < min && !Double.isInfinite(v)) {
                        min = v;
                    }
                }

                for (int i = 0; i < xTransformed.length; i++) {
                    if (xTransformed[i] == Double.POSITIVE_INFINITY) {
                        xTransformed[i] = max;
                    }

                    if (xTransformed[i] < Double.NEGATIVE_INFINITY) {
                        xTransformed[i] = min;
                    }
                }

                System.out.println(dataSet.getVariable(j) + ": A^2* = " + a2Orig + " transformed A^2* = " + a2Transformed);

//                if (a2Transformed < a2Orig) {
                X.assignColumn(j, new Vector(xTransformed));
//                } else {
//                    X.assignColumn(j, new Vector(x1Orig));
//                }
            }

            return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
        } catch (OutOfRangeException e) {
            e.printStackTrace();
            return dataSet;
        }
    }

    private static double[] ranks(Matrix data, double[] x) {
        double[] ranks = new double[x.length];

        for (int i = 0; i < data.rows(); i++) {
            double d = x[i];
            int count = 0;

            for (int k = 0; k < data.rows(); k++) {
                if (x[k] <= d) {
                    count++;
                }
            }

            ranks[i] = count;
        }

        return ranks;
    }

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
     * Returns the equivalent sample size, assuming all units are equally correlated and all unit variances are equal.
     */
    public static double getEss(ICovarianceMatrix covariances) {
        Matrix C = new CorrelationMatrix(covariances).getMatrix();

        double m = covariances.getSize();
        double n = covariances.getSampleSize();

        double sum = 0;

        for (int i = 0; i < C.rows(); i++) {
            for (int j = 0; j < C.columns(); j++) {
                sum += C.get(i, j);
            }
        }

        double rho = (n * sum - n * m) / (m * (n * n - n));
        return n / (1. + (n - 1.) * rho);
    }
}





