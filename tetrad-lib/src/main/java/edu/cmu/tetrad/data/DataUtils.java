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

package edu.cmu.tetrad.data;

import cern.colt.list.DoubleArrayList;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.rmi.MarshalledObject;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Some static utility methods for dealing with data sets.
 *
 * @author Various folks.
 */
public final class DataUtils {


    public static void copyColumn(final Node node, final DataSet source, final DataSet dest) {
        final int sourceColumn = source.getColumn(node);
        final int destColumn = dest.getColumn(node);
        if (sourceColumn < 0) {
            throw new NullPointerException("The given node was not in the source dataset");
        }
        if (destColumn < 0) {
            throw new NullPointerException("The given node was not in the destination dataset");
        }
        final int sourceRows = source.getNumRows();
        final int destRows = dest.getNumRows();
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
    public static boolean isBinary(final DataSet data, final int column) {
        final Node node = data.getVariable(column);
        final int size = data.getNumRows();
        if (node instanceof DiscreteVariable) {
            for (int i = 0; i < size; i++) {
                final int value = data.getInt(i, column);
                if (value != 1 && value != 0) {
                    return false;
                }
            }
        } else if (node instanceof ContinuousVariable) {
            for (int i = 0; i < size; i++) {
                final double value = data.getDouble(i, column);
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
    public static String defaultCategory(final int index) {
        return Integer.toString(index);
    }

    /**
     * Adds missing data values to cases in accordance with probabilities
     * specified in a double array which has as many elements as there are
     * columns in the input dataset.  Hence if the first element of the array of
     * probabilities is alpha, then the first column will contain a -99 (or
     * other missing value code) in a given case with probability alpha. </p>
     * This method will be useful in generating datasets which can be used to
     * test algorithm that handle missing data and/or latent variables. </p>
     * Author:  Frank Wimberly
     *
     * @param inData The data to which random missing data is to be added.
     * @param probs  The probability of adding missing data to each column.
     * @return The new data sets with missing data added.
     */
    public static DataSet addMissingData(
            final DataSet inData, final double[] probs) {
        final DataSet outData;

        outData = inData.copy();

        if (probs.length != outData.getNumColumns()) {
            throw new IllegalArgumentException(
                    "Wrong number of elements in prob array");
        }

        for (final double prob : probs) {
            if (prob < 0.0 || prob > 1.0) {
                throw new IllegalArgumentException("Probability out of range");
            }
        }

        for (int j = 0; j < outData.getNumColumns(); j++) {
            final Node node = outData.getVariable(j);

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

    public static DataSet replaceMissingWithRandom(final DataSet inData) {
        final DataSet outData;

        try {
            outData = new MarshalledObject<>(inData).get();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        for (int j = 0; j < outData.getNumColumns(); j++) {
            final Node variable = outData.getVariable(j);

            if (variable instanceof DiscreteVariable) {
                final List<Integer> values = new ArrayList<>();

                for (int i = 0; i < outData.getNumRows(); i++) {
                    final int value = outData.getInt(i, j);
                    if (value == -99) continue;
                    values.add(value);
                }

                Collections.sort(values);

                for (int i = 0; i < outData.getNumRows(); i++) {
                    if (outData.getInt(i, j) == -99) {
                        final int value = RandomUtil.getInstance().nextInt(values.size());
                        outData.setInt(i, j, values.get(value));
                    }
                }
            } else {
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < outData.getNumRows(); i++) {
                    final double value = outData.getDouble(i, j);
                    if (value < min) min = value;
                    if (value > max) max = value;
                }

                for (int i = 0; i < outData.getNumRows(); i++) {
                    final double random = RandomUtil.getInstance().nextDouble();
                    outData.setDouble(i, j, min + random * (max - min));
                }
            }
        }

        return outData;
    }

    /**
     * A continuous data set used to construct some other serializable
     * instances.
     */
    public static DataSet continuousSerializableInstance() {
        final List<Node> variables = new LinkedList<>();
        variables.add(new ContinuousVariable("X"));
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(10, variables.size()), variables);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, RandomUtil.getInstance().nextDouble());
            }
        }

        return dataSet;
    }

    /**
     * A discrete data set used to construct some other serializable instances.
     */
    public static DataSet discreteSerializableInstance() {
        final List<Node> variables = new LinkedList<>();
        variables.add(new DiscreteVariable("X", 2));
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(2, variables.size()), variables);
        dataSet.setInt(0, 0, 0);
        dataSet.setInt(1, 0, 1);
        return dataSet;
    }

    /**
     * @return true iff the data sets contains a missing value.
     */
    public static boolean containsMissingValue(final Matrix data) {
        for (int i = 0; i < data.rows(); i++) {
            for (int j = 0; j < data.columns(); j++) {
                if (Double.isNaN(data.get(i, j))) {
                    return true;
                }
            }
        }

        return false;
    }


    public static boolean containsMissingValue(final DataSet data) {
        for (int j = 0; j < data.getNumColumns(); j++) {
            final Node node = data.getVariable(j);

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
     *
     * @param dataSet
     * @param a
     * @param isUnlog
     * @return
     */
    public static DataSet logData(final DataSet dataSet, final double a, final boolean isUnlog, final int base) {
        final Matrix data = dataSet.getDoubleData();
        final Matrix X = data.like();
        final double n = dataSet.getNumRows();

        for (int j = 0; j < data.columns(); j++) {
            final double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());
            final double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());

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
                    if (base == 0) {
                        x1[i] = Math.log(a + x1Orig[i]);
                    } else {
                        x1[i] = Math.log(a + x1Orig[i]) / Math.log(base);
                    }
                }
            }

            X.assignColumn(j, new Vector(x1));
        }

        return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
    }


    public static Matrix standardizeData(final Matrix data) {
        final Matrix data2 = data.copy();

        for (int j = 0; j < data2.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data2.rows(); i++) {
                sum += data2.get(i, j);
            }

            final double mean = sum / data.rows();

            for (int i = 0; i < data.rows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }

            double norm = 0.0;

            for (int i = 0; i < data.rows(); i++) {
                final double v = data2.get(i, j);
                norm += v * v;
            }

            norm = Math.sqrt(norm / (data.rows() - 1));

            for (int i = 0; i < data.rows(); i++) {
                data2.set(i, j, data2.get(i, j) / norm);
            }
        }

        return data2;
    }

    public static double[] standardizeData(final double[] data) {
        final double[] data2 = new double[data.length];

        double sum = 0.0;

        for (final double d : data) {
            sum += d;
        }

        final double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data2[i] = data[i] - mean;
        }

        double norm = 0.0;

        for (final double v : data2) {
            norm += v * v;
        }

        norm = Math.sqrt(norm / (data2.length - 1));

        for (int i = 0; i < data2.length; i++) {
            data2[i] = data2[i] / norm;
        }

        return data2;
    }

    public static DoubleArrayList standardizeData(final DoubleArrayList data) {
        final DoubleArrayList data2 = new DoubleArrayList(data.size());

        double sum = 0.0;

        for (int i = 0; i < data.size(); i++) {
            sum += data.get(i);
        }

        final double mean = sum / data.size();

        for (int i = 0; i < data.size(); i++) {
            data2.add(data.get(i) - mean);
        }

        double norm = 0.0;

        for (int i = 0; i < data2.size(); i++) {
            final double v = data2.get(i);
            norm += v * v;
        }

        norm = Math.sqrt(norm / (data2.size() - 1));

        for (int i = 0; i < data2.size(); i++) {
            data2.set(i, data2.get(i) / norm);
        }

        return data2;
    }

    public static List<DataSet> standardizeData(final List<DataSet> dataSets) {
        final List<DataSet> outList = new ArrayList<>();

        for (final DataSet dataSet : dataSets) {
            if (!(dataSet.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + dataSet.getName());
            }

            final Matrix data2 = DataUtils.standardizeData(dataSet.getDoubleData());

            final DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), dataSet.getVariables());
            outList.add(dataSet2);
        }

        return outList;
    }

    public static DataSet standardizeData(final DataSet dataSet) {
        final List<DataSet> dataSets = Collections.singletonList(dataSet);
        final List<DataSet> outList = DataUtils.standardizeData(dataSets);
        return outList.get(0);
    }

    /**
     * Centers the array in place.
     */
    public static void centerData(final double[] data) {
        final double[] data2 = new double[data.length];

        double sum = 0.0;

        for (int i = 0; i < data2.length; i++) {
            sum += data[i];
        }

        final double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data2[i] -= mean;
        }
    }

    public static double[] center(final double[] d) {
        double sum = 0.0;

        for (int i = 0; i < d.length; i++) {
            sum += d[i];
        }

        final double mean = sum / d.length;
        final double[] d2 = new double[d.length];

        for (int i = 0; i < d.length; i++) {
            d2[i] = d[i] - mean;
        }

        return d2;
    }

    public static Matrix centerData(final Matrix data) {
        final Matrix data2 = data.copy();

        for (int j = 0; j < data2.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data2.rows(); i++) {
                sum += data2.get(i, j);
            }

            final double mean = sum / data.rows();

            for (int i = 0; i < data.rows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }
        }

        return data2;
    }

//    public static DataSet center(DataSet data) {
//        List<DataSet> dataSets = Collections.singletonList(data);
//        return center(dataSets).get(0);
//    }

    public static List<DataSet> center(final List<DataSet> dataList) {
        final List<DataSet> dataSets = new ArrayList<>();

        for (final DataSet dataSet : dataList) {
            dataSets.add(dataSet);
        }

        final List<DataSet> outList = new ArrayList<>();

        for (final DataModel model : dataSets) {
            if (!(model instanceof DataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            final DataSet dataSet = (DataSet) model;

            if (!(dataSet.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + dataSet.getName());
            }

            final Matrix data2 = DataUtils.centerData(dataSet.getDoubleData());
            final List<Node> list = dataSet.getVariables();
            final List<Node> list2 = new ArrayList<>();

            for (final Node node : list) {
                list2.add(node);
            }

            final DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), list2);
            outList.add(dataSet2);
        }

        return outList;
    }


    public static DataSet discretize(final DataSet dataSet, final int numCategories, final boolean variablesCopied) {
        final Discretizer discretizer = new Discretizer(dataSet);
        discretizer.setVariablesCopied(variablesCopied);

        for (final Node node : dataSet.getVariables()) {
//            if (dataSet.getVariable(node.getNode()) instanceof ContinuousVariable) {
            discretizer.equalIntervals(node, numCategories);
//            }
        }

        return discretizer.discretize();
    }

    public static List<Node> createContinuousVariables(final String[] varNames) {
        final List<Node> variables = new LinkedList<>();

        for (final String varName : varNames) {
            variables.add(new ContinuousVariable(varName));
        }

        return variables;
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(final ICovarianceMatrix m, final Node x, final Node y, final List<Node> z) {
        if (x == null) {
            throw new NullPointerException();
        }

        if (y == null) {
            throw new NullPointerException();
        }

        if (z == null) {
            throw new NullPointerException();
        }

        for (final Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        final List<Node> variables = m.getVariables();
//        TetradMatrix _covMatrix = m.getMatrix();

        // Create index array for the given variables.
        final int[] indices = new int[2 + z.size()];

        indices[0] = variables.indexOf(x);
        indices[1] = variables.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = variables.indexOf(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.
        final Matrix submatrix = m.getSelection(indices, indices);

        if (DataUtils.containsMissingValue(submatrix)) {
            throw new IllegalArgumentException(
                    "Please remove or impute missing values first.");
        }

        return submatrix;
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(final Matrix m, final List<Node> variables, final Node x, final Node y, final List<Node> z) {
        if (x == null) {
            throw new NullPointerException();
        }

        if (y == null) {
            throw new NullPointerException();
        }

        if (z == null) {
            throw new NullPointerException();
        }

        for (final Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        // Create index array for the given variables.
        final int[] indices = new int[2 + z.size()];

        indices[0] = variables.indexOf(x);
        indices[1] = variables.indexOf(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = variables.indexOf(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.

//        if (containsMissingValue(submatrix)) {
//            throw new IllegalArgumentException(
//                    "Please remove or impute missing values first.");
//        }

        return m.getSelection(indices, indices);
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static Matrix subMatrix(final Matrix m, final Map<Node, Integer> indexMap, final Node x, final Node y, final List<Node> z) {
        if (x == null) {
            throw new NullPointerException();
        }

        if (y == null) {
            throw new NullPointerException();
        }

        if (z == null) {
            throw new NullPointerException();
        }

        for (final Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        // Create index array for the given variables.
        final int[] indices = new int[2 + z.size()];

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
    public static Matrix subMatrix(final ICovarianceMatrix m, final Map<Node, Integer> indexMap, final Node x, final Node y, final List<Node> z) {
//        if (x == null) {
//            throw new NullPointerException();
//        }
//
//        if (y == null) {
//            throw new NullPointerException();
//        }
//
//        if (z == null) {
//            throw new NullPointerException();
//        }
//
//        for (Node node : z) {
//            if (node == null) {
//                throw new NullPointerException();
//            }
//        }

        // Create index array for the given variables.
        final int[] indices = new int[2 + z.size()];

        indices[0] = indexMap.get(x);
        indices[1] = indexMap.get(y);

        for (int i = 0; i < z.size(); i++) {
            indices[i + 2] = indexMap.get(z.get(i));
        }

        // Extract submatrix of correlation matrix using this index array.

        return m.getSelection(indices, indices);
    }

    /**
     * @param dataSet The data set to shuffle.
     * @return Ibid.
     */
    public static DataSet shuffleColumnsCov(final DataSet dataSet) {
        final int numVariables = dataSet.getNumColumns();

        final List<Integer> indicesList = new ArrayList<>();
        for (int i = 0; i < numVariables; i++) indicesList.add(i);
        Collections.shuffle(indicesList);

        final int[] indices = new int[numVariables];

        for (int i = 0; i < numVariables; i++) {
            indices[i] = indicesList.get(i);
        }

        return dataSet.subsetColumns(indices);
    }

    public static DataSet convertNumericalDiscreteToContinuous(
            final DataSet dataSet) throws NumberFormatException {
        final List<Node> variables = new ArrayList<>();

        for (final Node variable : dataSet.getVariables()) {
            if (variable instanceof ContinuousVariable) {
                variables.add(variable);
            } else {
                variables.add(new ContinuousVariable(variable.getName()));
            }
        }

        final DataSet continuousData = new BoxDataSet(new VerticalDoubleDataBox(dataSet.getNumRows(), variables.size()), variables);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final Node variable = dataSet.getVariable(j);

            if (variable instanceof ContinuousVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    continuousData.setDouble(i, j, dataSet.getDouble(i, j));
                }
            } else {
                final DiscreteVariable discreteVariable = (DiscreteVariable) variable;

                boolean allNumerical = true;

                for (final String cat : discreteVariable.getCategories()) {
                    try {
                        Double.parseDouble(cat);
                    } catch (final NumberFormatException e) {
                        allNumerical = false;
                        break;
                    }
                }


                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    final int index = dataSet.getInt(i, j);
                    final String catName = discreteVariable.getCategory(index);
                    final double value;

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

    public static DataSet concatenate(final DataSet dataSet1, final DataSet dataSet2) {
        final List<Node> vars1 = dataSet1.getVariables();
        final List<Node> vars2 = dataSet2.getVariables();
        final Map<String, Integer> varMap2 = new HashMap<>();
        for (int i = 0; i < vars2.size(); i++) {
            varMap2.put(vars2.get(i).getName(), i);
        }
        final int rows1 = dataSet1.getNumRows();
        final int rows2 = dataSet2.getNumRows();
        final int cols1 = dataSet1.getNumColumns();

        final Matrix concatMatrix = new Matrix(rows1 + rows2, cols1);
        final Matrix matrix1 = dataSet1.getDoubleData();
        final Matrix matrix2 = dataSet2.getDoubleData();

        for (int i = 0; i < vars1.size(); i++) {
            final int var2 = varMap2.get(vars1.get(i).getName());
            for (int j = 0; j < rows1; j++) {
                concatMatrix.set(j, i, matrix1.get(j, i));
            }
            for (int j = 0; j < rows2; j++) {
                concatMatrix.set(j + rows1, i, matrix2.get(j, var2));
            }
        }

        return new BoxDataSet(new VerticalDoubleDataBox(concatMatrix.transpose().toArray()), vars1);
    }

//    public static TetradMatrix concatenate(TetradMatrix dataSet1, TetradMatrix dataSet2) {
//        int rows1 = dataSet1.rows();
//        int rows2 = dataSet2.rows();
//        int cols1 = dataSet1.columns();
//
//        TetradMatrix concatMatrix = new TetradMatrix(rows1 + rows2, cols1);
//
//        for (int i = 0; i < cols1; i++) {
//            for (int j = 0; j < rows1; j++) {
//                concatMatrix.set(j, i, dataSet1.get(j, i));
//            }
//            for (int j = 0; j < rows2; j++) {
//                concatMatrix.set(j + rows1, i, dataSet2.get(j, i));
//            }
//        }
//
//        return concatMatrix;
//    }


    public static DataSet concatenate(final DataSet... dataSets) {
        final List<DataSet> _dataSets = new ArrayList<>();

        Collections.addAll(_dataSets, dataSets);

        return DataUtils.concatenate(_dataSets);
    }

    public static Matrix concatenate(final Matrix... dataSets) {
        int totalSampleSize = 0;

        for (final Matrix dataSet : dataSets) {
            totalSampleSize += dataSet.rows();
        }

        final int numColumns = dataSets[0].columns();
        final Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (final Matrix dataSet : dataSets) {
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
    public static DataSet concatenate(final List<DataSet> dataSets) {
        int totalSampleSize = 0;

        for (final DataSet dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        final int numColumns = dataSets.get(0).getNumColumns();
        final Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (final DataSet dataSet : dataSets) {
            final Matrix _data = dataSet.getDoubleData();
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

    public static Matrix concatenateTetradMatrices(final List<Matrix> dataSets) {
        int totalSampleSize = 0;

        for (final Matrix dataSet : dataSets) {
            totalSampleSize += dataSet.rows();
        }

        final int numColumns = dataSets.get(0).columns();
        final Matrix allData = new Matrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (final Matrix _data : dataSets) {
            r = _data.rows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, _data.get(i, j));
                }
            }

            q += r;
        }

        return allData;
    }

    public static DataSet collectVariables(final List<DataSet> dataSets) {
        int totalNumColumns = 0;

        for (final DataSet dataSet : dataSets) {
            totalNumColumns += dataSet.getNumColumns();
        }

        final int numRows = dataSets.get(0).getNumRows();
        final Matrix allData = new Matrix(numRows, totalNumColumns);
        int q = 0;
        int cc;

        for (final DataSet dataSet : dataSets) {
            final Matrix _data = dataSet.getDoubleData();
            cc = _data.columns();

            for (int jj = 0; jj < cc; jj++) {
                for (int ii = 0; ii < numRows; ii++) {
                    allData.set(ii, q + jj, _data.get(ii, jj));
                }
            }

            q += cc;
        }

        final List<Node> variables = new ArrayList<>();

        for (final DataSet dataSet : dataSets) {
            variables.addAll(dataSet.getVariables());
        }

        return new BoxDataSet(new VerticalDoubleDataBox(allData.transpose().toArray()), variables);
    }

    public static DataSet concatenateDataNoChecks(final List<DataSet> datasets) {
        final List<Node> vars1 = datasets.get(0).getVariables();
        final int cols = vars1.size();
        int rows = 0;
        for (final DataSet dataset : datasets) {
            rows += dataset.getNumRows();
        }

        final Matrix concatMatrix = new Matrix(rows, vars1.size());

        int index = 0;

        for (final DataSet dataset : datasets) {
            for (int i = 0; i < dataset.getNumRows(); i++) {
                for (int j = 0; j < cols; j++) {
                    concatMatrix.set(index, j, dataset.getDouble(i, j));
                }
                index++;
            }
        }

        return new BoxDataSet(new VerticalDoubleDataBox(concatMatrix.transpose().toArray()), vars1);
    }


    public static DataSet concatenateDiscreteData(final DataSet dataSet1, final DataSet dataSet2) {
        final List<Node> vars = dataSet1.getVariables();
        final int rows1 = dataSet1.getNumRows();
        final int rows2 = dataSet2.getNumRows();
        final DataSet concatData = new BoxDataSet(new VerticalDoubleDataBox(rows1 + rows2, vars.size()), vars);

        for (final Node var : vars) {
            final int var1 = dataSet1.getColumn(dataSet1.getVariable(var.toString()));
            final int varc = concatData.getColumn(concatData.getVariable(var.toString()));
            for (int i = 0; i < rows1; i++) {
                concatData.setInt(i, varc, dataSet1.getInt(i, var1));
            }
            final int var2 = dataSet2.getColumn(dataSet2.getVariable(var.toString()));
            for (int i = 0; i < rows2; i++) {
                concatData.setInt(i + rows1, varc, dataSet2.getInt(i, var2));
            }
        }

        return concatData;
    }

    public static DataSet noisyZeroes(DataSet dataSet) {
        dataSet = dataSet.copy();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            boolean allZeroes = true;

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                if (dataSet.getDouble(i, j) != 0) {
                    allZeroes = false;
                    break;
                }
            }

            if (allZeroes) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    dataSet.setDouble(i, j, RandomUtil.getInstance().nextNormal(0, 1));
                }
            }
        }

        return dataSet;
    }

    public static void printAndersonDarlingPs(final DataSet dataSet) {
        System.out.println("Anderson Darling P value for Variables\n");

        final NumberFormat nf = new DecimalFormat("0.0000");
        final Matrix m = dataSet.getDoubleData();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            final double[] x = m.getColumn(j).toArray();
            final double p = new AndersonDarlingTest(x).getP();
            System.out.println("For " + dataSet.getVariable(j) +
                    ", Anderson-Darling p = " + nf.format(p)
                    + (p > 0.05 ? " = Gaussian" : " = Nongaussian"));
        }

    }

    public static DataSet restrictToMeasured(final DataSet fullDataSet) {
        final List<Node> measuredVars = new ArrayList<>();
        final List<Node> latentVars = new ArrayList<>();

        for (final Node node : fullDataSet.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measuredVars.add(node);
            } else {
                latentVars.add(node);
            }
        }

        return latentVars.isEmpty() ? fullDataSet : fullDataSet.subsetColumns(measuredVars);
    }

    public static Matrix cov2(final Matrix data) {
        final RealMatrix covarianceMatrix = new Covariance(new BlockRealMatrix(data.toArray())).getCovarianceMatrix();
        return new Matrix(covarianceMatrix.getData());
    }

    public static Vector means(final Matrix data) {
        final Vector means = new Vector(data.columns());

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

            final double mean = sum / count;

            means.set(j, mean);
        }

        return means;
    }

    /**
     * Column major data.
     */
    public static Vector means(final double[][] data) {
        final Vector means = new Vector(data.length);
        final int rows = data[0].length;

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

            final double mean = sum / count;

            means.set(j, mean);
        }

        return means;
    }

    public static void demean(final Matrix data, final Vector means) {
        for (int j = 0; j < data.columns(); j++) {
            for (int i = 0; i < data.rows(); i++) {
                data.set(i, j, data.get(i, j) - means.get(j));
            }
        }
    }

    /**
     * Column major data.
     */
    public static void demean(final double[][] data, final Vector means) {
        final int rows = data[0].length;

        for (int j = 0; j < data.length; j++) {
            for (int i = 0; i < rows; i++) {
                data[j][i] = data[j][i] - means.get(j);
            }
        }
    }

    public static void remean(final Matrix data, final Vector means) {
        for (int j = 0; j < data.columns(); j++) {
            for (int i = 0; i < data.rows(); i++) {
                data.set(i, j, data.get(i, j) + means.get(j));
            }
        }
    }

    public static Matrix covDemeaned(final Matrix data) {
        final Matrix transpose = data.transpose();
        final Matrix prod = transpose.times(data);

        final double factor = 1.0 / (data.rows() - 1);

        for (int i = 0; i < prod.rows(); i++) {
            for (int j = 0; j < prod.columns(); j++) {
                prod.set(i, j, prod.get(i, j) * factor);
            }
        }

        return prod;

//        return prod.scalarMult(1.0 / (data.rows() - 1));
    }

    public static Matrix cov(final Matrix data) {


        for (int j = 0; j < data.columns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data.rows(); i++) {
                sum += data.get(i, j);
            }

            final double mean = sum / data.rows();

            for (int i = 0; i < data.rows(); i++) {
                data.set(i, j, data.get(i, j) - mean);
            }
        }

        final RealMatrix q = new BlockRealMatrix(data.toArray());

        final RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
        final RealMatrix q2 = DataUtils.times(q1, q);
        final Matrix prod = new Matrix(q2.getData());

        final double factor = 1.0 / (data.rows() - 1);

        for (int i = 0; i < prod.rows(); i++) {
            for (int j = 0; j < prod.columns(); j++) {
                prod.set(i, j, prod.get(i, j) * factor);
            }
        }

        return prod;
    }

    public static void simpleTest() {
        final double[][] d = new double[][]{
                {1, 2},
                {3, 4},
                {5, 6},
        };

        final RealMatrix m = new BlockRealMatrix(d);

        System.out.println(m);

        System.out.println(DataUtils.times(m.transpose(), m));

        System.out.println(MatrixUtils.transposeWithoutCopy(m).multiply(m));

        final Matrix n = new Matrix(m.getData());

        System.out.println(n);

        final RealMatrix q = new BlockRealMatrix(n.toArray());

        final RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
        final RealMatrix q2 = DataUtils.times(q1, q);
        System.out.println(new Matrix(q2.getData()));
    }

    private static RealMatrix times(final RealMatrix m, final RealMatrix n) {
        if (m.getColumnDimension() != n.getRowDimension()) throw new IllegalArgumentException("Incompatible matrices.");

        final int rowDimension = m.getRowDimension();
        final int columnDimension = n.getColumnDimension();

        final RealMatrix out = new BlockRealMatrix(rowDimension, columnDimension);

        final int NTHREADS = Runtime.getRuntime().availableProcessors();

        final ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        for (int t = 0; t < NTHREADS; t++) {
            final int _t = t;

            final Runnable worker = new Runnable() {
                @Override
                public void run() {
                    final int chunk = rowDimension / NTHREADS + 1;
                    for (int row = _t * chunk; row < Math.min((_t + 1) * chunk, rowDimension); row++) {
                        if ((row + 1) % 100 == 0) System.out.println(row + 1);

                        for (int col = 0; col < columnDimension; ++col) {
                            double sum = 0.0D;

                            final int commonDimension = m.getColumnDimension();

                            for (int i = 0; i < commonDimension; ++i) {
                                sum += m.getEntry(row, i) * n.getEntry(i, col);
                            }

//                            double sum = m.getRowVector(row).dotProduct(n.getColumnVector(col));
                            out.setEntry(row, col, sum);
                        }
                    }
                }
            };

            pool.submit(worker);
        }

        while (!pool.isQuiescent()) {
        }

        return out;
    }

    // for online learning.
    public static Matrix onlineCov(final Matrix data) {
        final int N = data.rows();
        final int M = data.columns();

        final Matrix cov = new Matrix(M, M);

        final double[] m = new double[M];
        final double[] d = new double[M];

        for (int j = 0; j < M; j++) {
            m[j] = data.get(0, j);
        }

        for (int j = 0; j < M; j++) {
            for (int k = 0; k < M; k++) {
                cov.set(j, k, 0.0);
            }
        }

        final double a = 1.0;
        double b = a;

        for (int i = 1; i < N; i++) {
            final double b0 = b;
            b += a;

            for (int j1 = 0; j1 < M; j1++) {
                final double mj0 = m[j1];
                final double xj = data.get(i, j1);
                d[j1] = (a / b) * (xj - mj0);
                m[j1] += d[j1];
            }

            for (int j = 0; j < M; j++) {
                for (int k = j; k < M; k++) {
                    final double cjk0 = cov.get(j, k);

                    final double xj = data.get(i, j);
                    final double xk = data.get(i, k);

                    final double f = (1. / b) * (b0 * cjk0 + b * d[j] * d[k] + a * (xj - m[j]) * (xk - m[k]));

                    cov.set(j, k, f);
                    cov.set(k, j, f);
                }
            }
        }

        return cov;
    }

//    public static TetradMatrix cov2(TetradMatrix data) {
//        TetradMatrix cov = new TetradMatrix(data.columns(), data.columns());
//
//        for (int i = 0; i < data.columns(); i++) {
//            for (int j = 0; j < data.columns(); j++) {
//                cov.set(i, j, StatUtils.covariance(data.getColumn(i).toArray(), data.getColumn(j).toArray()));
//            }
//        }
//
//        return cov;
//
//    }

//    public static TetradMatrix corr(TetradMatrix data) {
//        TetradMatrix corr = new TetradMatrix(data.columns(), data.columns());
//
//        for (int i = 0; i < data.columns(); i++) {
//            for (int j = 0; j < data.columns(); j++) {
//                corr.set(i, j, StatUtils.correlation(data.getColumn(i).toArray(), data.getColumn(j).toArray()));
//            }
//        }
//
//        return corr;
//
//    }

    public static Vector mean(final Matrix data) {
        final Vector mean = new Vector(data.columns());

        for (int i = 0; i < data.columns(); i++) {
            mean.set(i, StatUtils.mean(data.getColumn(i).toArray()));
        }

        return mean;

    }

    /**
     * @param cov The variables and covariance matrix over the variables.
     * @return The simulated data.
     */
    public static DataSet choleskySimulation(final CovarianceMatrix cov) {
        System.out.println(cov);
        final int sampleSize = cov.getSampleSize();

        final List<Node> variables = cov.getVariables();
        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);
        final Matrix _cov = cov.getMatrix().copy();

        final Matrix cholesky = MatrixUtils.cholesky(_cov);

        System.out.println("Cholesky decomposition" + cholesky);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            final double[] exoData = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            final double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            for (int col = 0; col < variables.size(); col++) {
                final int index = variables.indexOf(variables.get(col));
                final double value = point[index];

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
    public static Matrix getBootstrapSample(final Matrix data, final int sampleSize) {
        final int actualSampleSize = data.rows();

        final int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        final int[] cols = new int[data.columns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return data.getSelection(rows, cols);
    }

    /**
     * @return a sample without replacement with the given sample size from the
     * given dataset.
     */
    public static DataSet getResamplingDataset(final DataSet data, final int sampleSize) {
        final int actualSampleSize = data.getNumRows();
        int _size = sampleSize;
        if (actualSampleSize < _size) {
            _size = actualSampleSize;
        }

        final List<Integer> availRows = new ArrayList<>();
        for (int i = 0; i < actualSampleSize; i++) {
            availRows.add(i);
        }

        Collections.shuffle(availRows);

        final List<Integer> addedRows = new ArrayList<>();
        final int[] rows = new int[_size];
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

        final int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()), data.getVariables());
    }

    /**
     * @return a sample with replacement with the given sample size from the
     * given dataset.
     */
    public static DataSet getBootstrapSample(final DataSet data, final int sampleSize) {
        final int actualSampleSize = data.getNumRows();

        final int[] rows = new int[sampleSize];

        for (int i = 0; i < rows.length; i++) {
            rows[i] = RandomUtil.getInstance().nextInt(actualSampleSize);
        }

        final int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()),
                data.getVariables());
    }

    /**
     * @return a sample without replacement with the given sample size from the
     * given dataset. May return a sample of less than the given size; makes
     * sampleAttempts attempts to sample.
     */
    public static DataSet getBootstrapSample2(final DataSet data, final int sampleAttempts) {
        final int actualSampleSize = data.getNumRows();
        final List<Integer> samples = new ArrayList<>();

        for (int i = 0; i < sampleAttempts; i++) {
            final int sample = RandomUtil.getInstance().nextInt(actualSampleSize);
            if (!samples.contains(sample)) samples.add(sample);
        }

        final int[] rows = new int[samples.size()];

        for (int i = 0; i < samples.size(); i++) {
            rows[i] = samples.get(i);
        }

        final int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return new BoxDataSet(new VerticalDoubleDataBox(data.getDoubleData().getSelection(rows, cols).transpose().toArray()),
                data.getVariables());
    }

    public static List<DataSet> split(final DataSet data, final double percentTest) {
        if (percentTest <= 0 || percentTest >= 1) throw new IllegalArgumentException();

        final List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) rows.add(i);

        Collections.shuffle(rows);

        final int split = (int) (rows.size() * percentTest);

        final List<Integer> rows1 = new ArrayList<>();
        final List<Integer> rows2 = new ArrayList<>();

        for (int i = 0; i < split; i++) {
            rows1.add(rows.get(i));
        }

        for (int i = split; i < rows.size(); i++) {
            rows2.add(rows.get(i));
        }

        final int[] _rows1 = new int[rows1.size()];
        final int[] _rows2 = new int[rows2.size()];

        for (int i = 0; i < rows1.size(); i++) _rows1[i] = rows1.get(i);
        for (int i = 0; i < rows2.size(); i++) _rows2[i] = rows2.get(i);

        final int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        final BoxDataSet boxDataSet1 = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(_rows1, cols).transpose().toArray()),
                data.getVariables());

        final BoxDataSet boxDataSet2 = new BoxDataSet(new VerticalDoubleDataBox(
                data.getDoubleData().getSelection(_rows2, cols).transpose().toArray()),
                data.getVariables());

        final List<DataSet> ret = new ArrayList<>();

        ret.add(boxDataSet1);
        ret.add(boxDataSet2);

        return ret;
    }

    /**
     * Subtracts the mean of each column from each datum that column.
     */
    public static DataSet center(final DataSet data) {
        final DataSet _data = data.copy();

        for (int j = 0; j < _data.getNumColumns(); j++) {
            double sum = 0.0;
            int n = 0;

            for (int i = 0; i < _data.getNumRows(); i++) {
                final double v = _data.getDouble(i, j);

                if (!Double.isNaN(v)) {
                    sum += v;
                    n++;
                }
            }

            final double avg = sum / n;

            for (int i = 0; i < _data.getNumRows(); i++) {
                _data.setDouble(i, j, _data.getDouble(i, j) - avg);
            }
        }

        return _data;
    }

    /**
     * @param dataSet The data; missing values are permitted.
     * @param vars    The indices of the targeted variables in the data. The returned covariance matrix will have
     *                variables in the same order.
     * @param n       The returned sample size, n[0]. Provide a length 1 array.
     * @return The reduced covariance matrix.
     */
    public static Matrix covMatrixForDefinedRows(final DataSet dataSet, final int[] vars, final int[] n) {
        DataSet _dataSet = dataSet.copy();
        _dataSet = DataUtils.center(_dataSet);

        final Matrix reduced = new Matrix(vars.length, vars.length);

        final List<Integer> rows = new ArrayList<>();

        I:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (final int var : vars) {
                if (Double.isNaN(_dataSet.getDouble(i, var))) {
                    continue I;
                }
            }

            rows.add(i);
        }

        for (int i = 0; i < reduced.rows(); i++) {
            for (int j = 0; j < reduced.columns(); j++) {
                double sum = 0.0;

                for (final int k : rows) {
                    final double v = _dataSet.getDouble(k, vars[i]) * _dataSet.getDouble(k, vars[j]);
                    sum += v;
                }

                reduced.set(i, j, sum / rows.size());
            }
        }

        n[0] = rows.size();

        return reduced;
    }

    public static IKnowledge createRequiredKnowledge(final Graph resultGraph) {
        final IKnowledge knowledge = new Knowledge2();

        final List<Node> nodes = resultGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
//                if (resultGraph.getEdges().size() >= 2) continue;
                if (nodes.get(i).getName().startsWith("E_")) continue;
                if (nodes.get(j).getName().startsWith("E_")) continue;

                final Edge edge = resultGraph.getEdge(nodes.get(i), nodes.get(j));

                if (edge == null) {
                } else if (edge.isDirected()) {
                    final Node node1 = edge.getNode1();
                    final Node node2 = edge.getNode2();
//                    knowledge.setEdgeForbidden(node2.getNode(), node1.getNode(), true);
                    knowledge.setRequired(node1.getName(), node2.getName());
                } else if (Edges.isUndirectedEdge(edge)) {
                    final Node node1 = edge.getNode1();
                    final Node node2 = edge.getNode2();
                    knowledge.setRequired(node1.getName(), node2.getName());
                    knowledge.setRequired(node2.getName(), node1.getName());
                }
            }
        }

        return knowledge;
    }

    public static DataSet shuffleColumns(final DataSet dataModel) {
        final String name = dataModel.getName();
        final int numVariables = dataModel.getNumColumns();

        final List<Integer> indicesList = new ArrayList<>();
        for (int i = 0; i < numVariables; i++) indicesList.add(i);
        Collections.shuffle(indicesList);

        final int[] indices = new int[numVariables];

        for (int i = 0; i < numVariables; i++) {
            indices[i] = indicesList.get(i);
        }

        final DataSet dataSet = dataModel.subsetColumns(indices);
        dataSet.setName(name);
        return dataSet;
    }

    public static List<DataSet> shuffleColumns2(final List<DataSet> dataSets) {
        final List<Node> vars = new ArrayList<>();

        final List<Node> variables = dataSets.get(0).getVariables();
        Collections.shuffle(variables);

        for (final Node node : variables) {
            final Node _node = dataSets.get(0).getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        final List<DataSet> ret = new ArrayList<>();

        for (final DataSet m : dataSets) {
            final List<Node> myVars = new ArrayList<>();
            for (final Node n1 : variables) {
                myVars.add(m.getVariable(n1.getName()));
            }
            final DataSet data = m.subsetColumns(myVars);
            data.setName(m.getName() + ".reordered");
            ret.add(data);
        }

        return ret;
    }

    public static ICovarianceMatrix shuffleColumnsCov(final ICovarianceMatrix cov) {
        final List<String> vars = new ArrayList<>();

        final List<Node> variables = new ArrayList<>(cov.getVariables());
        Collections.shuffle(variables);

        for (final Node node : variables) {
            final Node _node = cov.getVariable(node.getName());

            if (_node != null) {
                vars.add(_node.getName());
            }
        }

        return cov.getSubmatrix(vars);
    }


    public static ICovarianceMatrix covarianceNonparanormalDrton(final DataSet dataSet) {
        final CovarianceMatrix covMatrix = new CovarianceMatrix(dataSet);
        final Matrix data = dataSet.getDoubleData();
        final int NTHREDS = Runtime.getRuntime().availableProcessors() * 10;
        final int EPOCH_COUNT = 100000;

        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        int runnableCount = 0;

        for (int _i = 0; _i < dataSet.getNumColumns(); _i++) {
            for (int _j = _i; _j < dataSet.getNumColumns(); _j++) {
                final int i = _i;
                final int j = _j;

//                double tau = StatUtils.rankCorrelation(data.viewColumn(i).toArray(), data.viewColumn(j).toArray());
                final Runnable worker = new Runnable() {
                    @Override
                    public void run() {
                        final double tau = StatUtils.kendallsTau(data.getColumn(i).toArray(), data.getColumn(j).toArray());
                        covMatrix.setValue(i, j, tau);
                        covMatrix.setValue(j, i, tau);
                    }
                };

                executor.execute(worker);

                if (runnableCount < EPOCH_COUNT) {
                    runnableCount++;
//                    System.out.println(runnableCount);
                } else {
                    executor.shutdown();
                    try {
                        // Wait until all threads are finish
                        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                        System.out.println("Finished all threads");
                    } catch (final InterruptedException e) {
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
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            System.out.println("Finished all threads");
        } catch (final InterruptedException e) {
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

    public static DataSet getNonparanormalTransformed(final DataSet dataSet) {
        try {
            final Matrix data = dataSet.getDoubleData();
            final Matrix X = data.like();
            final double n = dataSet.getNumRows();
            final double delta = 1e-8;
//            delta = 1.0 / (4.0 * Math.pow(n, 0.25) * Math.sqrt(Math.PI * Math.log(n)));

            final NormalDistribution normalDistribution = new NormalDistribution();

            double std = Double.NaN;

            for (int j = 0; j < data.columns(); j++) {
                final double[] x1Orig = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());
                final double[] x1 = Arrays.copyOf(data.getColumn(j).toArray(), data.rows());

                final double a2Orig = new AndersonDarlingTest(x1).getASquaredStar();

                if (dataSet.getVariable(j) instanceof DiscreteVariable) {
                    X.assignColumn(j, new Vector(x1));
                    continue;
                }

                final double std1 = StatUtils.sd(x1);
                final double mu1 = StatUtils.mean(x1);
                final double[] xTransformed = DataUtils.ranks(data, x1);

                for (int i = 0; i < xTransformed.length; i++) {
                    xTransformed[i] /= n;

//                    if (xTransformed[i] < delta) xTransformed[i] = delta;
//                    if (xTransformed[i] > (1. - delta)) xTransformed[i] = 1. - delta;

//                    if (xTransformed[i] <= 0) xTransformed[i] = 0;
//                    if (xTransformed[i] >= 1) xTransformed[i] = 1;
                    xTransformed[i] = normalDistribution.inverseCumulativeProbability(xTransformed[i]);
                }

                if (Double.isNaN(std)) {
                    std = StatUtils.sd(x1Orig);
                }

                for (int i = 0; i < xTransformed.length; i++) {
//                    xTransformed[i] /= std;
                    xTransformed[i] *= std1;
                    xTransformed[i] += mu1;
                }

                final double a2Transformed = new AndersonDarlingTest(xTransformed).getASquaredStar();

                System.out.println(dataSet.getVariable(j) + ": A^2* = " + a2Orig + " transformed A^2* = " + a2Transformed);

                if (a2Transformed < a2Orig) {
                    X.assignColumn(j, new Vector(xTransformed));
                } else {
                    X.assignColumn(j, new Vector(x1Orig));
                }
            }

            return new BoxDataSet(new VerticalDoubleDataBox(X.transpose().toArray()), dataSet.getVariables());
        } catch (final OutOfRangeException e) {
            e.printStackTrace();
            return dataSet;
        }
    }

    private static double[] ranks(final Matrix data, final double[] x) {
        final double[] ranks = new double[x.length];

        for (int i = 0; i < data.rows(); i++) {
            final double d = x[i];
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

    public static DataSet removeConstantColumns(final DataSet dataSet) {
        final int columns = dataSet.getNumColumns();
        final int rows = dataSet.getNumRows();
        if (rows == 0) {
            return dataSet;
        }

        final List<Integer> keepCols = new ArrayList<>();

        for (int j = 0; j < columns; j++) {
            final Object previous = dataSet.getObject(0, j);
            boolean constant = true;
            for (int row = 1; row < rows; row++) {
                final Object current = dataSet.getObject(row, j);
                if (!previous.equals(current)) {
                    constant = false;
                    break;
                }

                if (previous instanceof Double && current instanceof Double) {
                    final double _previouw = (Double) previous;
                    final double _current = (Double) current;

                    if (Double.isNaN(_previouw) && Double.isNaN(_current)) {
                        constant = false;
                        break;
                    }
                }
            }

            if (!constant) keepCols.add(j);
        }

        final int[] newCols = new int[keepCols.size()];
        for (int j = 0; j < keepCols.size(); j++) newCols[j] = keepCols.get(j);

        return dataSet.subsetColumns(newCols);
    }

    public static ICovarianceMatrix getCovMatrix(final DataModel dataModel) {
        if (dataModel == null) {
            throw new IllegalArgumentException("Expecting either a tabular dataset or a covariance matrix.");
        }

        if (dataModel instanceof ICovarianceMatrix) {
            return (ICovarianceMatrix) dataModel;
        } else if (dataModel instanceof DataSet) {
            return new CovarianceMatrix((DataSet) dataModel);
        } else {
            throw new IllegalArgumentException("Sorry, I was expecting either a tabular dataset or a covariance matrix.");
        }
    }

    public static DataSet getDiscreteDataSet(final DataModel dataSet) {
        if (dataSet == null || !(dataSet instanceof DataSet) || !dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Sorry, I was expecting a discrete data set.");
        }

        return (DataSet) dataSet;
    }

    public static DataSet getContinuousDataSet(final DataModel dataSet) {
        if (dataSet == null || !(dataSet instanceof DataSet) || !dataSet.isContinuous()) {
            throw new IllegalArgumentException("Sorry, I was expecting a (tabular) continuous data set.");
        }

        return (DataSet) dataSet;
    }

    public static DataSet getMixedDataSet(final DataModel dataSet) {
        if (dataSet == null || !(dataSet instanceof DataSet)) {
            throw new IllegalArgumentException("Sorry, I was expecting a (tabular) mixed data set.");
        }

        return (DataSet) dataSet;
    }

    /**
     * Returns the equivalent sample size, assuming all units are equally correlated and all unit variances are equal.
     */
    public static double getEss(final ICovarianceMatrix covariances) {
        final Matrix C = new CorrelationMatrix(covariances).getMatrix();

        final double m = covariances.getSize();
        final double n = covariances.getSampleSize();

        double sum = 0;

        for (int i = 0; i < C.rows(); i++) {
            for (int j = 0; j < C.columns(); j++) {
                sum += C.get(i, j);
            }
        }

        final double rho = (n * sum - n * m) / (m * (n * n - n));
        return n / (1. + (n - 1.) * rho);
    }

    /**
     * Loads knowledge from a file. Assumes knowledge is the only thing in the
     * file. No jokes please. :)
     */
    public static IKnowledge loadKnowledge(final File file, final DelimiterType delimiterType, final String commentMarker) throws IOException {
        final FileReader reader = new FileReader(file);
        final Lineizer lineizer = new Lineizer(reader, commentMarker);
        final IKnowledge knowledge = DataUtils.loadKnowledge(lineizer, delimiterType.getPattern());
        TetradLogger.getInstance().reset();
        return knowledge;
    }

    /**
     * Reads a knowledge file in tetrad2 format (almost--only does temporal
     * tiers currently). Format is:
     * <pre>
     * /knowledge
     * addtemporal
     * 0 x1 x2
     * 1 x3 x4
     * 4 x5
     * </pre>
     */
    public static IKnowledge loadKnowledge(final Lineizer lineizer, final Pattern delimiter) {
        final IKnowledge knowledge = new Knowledge2();

        String line = lineizer.nextLine();
        String firstLine = line;

        if (line == null) {
            return new Knowledge2();
        }

        if (line.startsWith("/knowledge")) {
            line = lineizer.nextLine();
            firstLine = line;
        }

        TetradLogger.getInstance().log("info", "\nLoading knowledge.");

        SECTIONS:
        while (lineizer.hasMoreLines()) {
            if (firstLine == null) {
                line = lineizer.nextLine();
            } else {
                line = firstLine;
            }

            // "addtemp" is the original in Tetrad 2.
            if ("addtemporal".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    int tier = -1;

                    final RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
                    if (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        boolean forbiddenWithin = false;
                        if (token.endsWith("*")) {
                            forbiddenWithin = true;
                            token = token.substring(0, token.length() - 1);
                        }

                        tier = Integer.parseInt(token);
                        if (tier < 1) {
                            throw new IllegalArgumentException(
                                    lineizer.getLineNumber() + ": Tiers must be 1, 2...");
                        }
                        if (forbiddenWithin) {
                            knowledge.setTierForbiddenWithin(tier - 1, true);
                        }
                    }

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();

                        if (token.isEmpty()) {
                            continue;
                        }

                        final String name = DataUtils.substitutePeriodsForSpaces(token);

                        DataUtils.addVariable(knowledge, name);

                        knowledge.addToTier(tier - 1, name);

                        TetradLogger.getInstance().log("info", "Adding to tier " + (tier - 1) + " " + name);
                    }
                }
            } else if ("forbiddengroup".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    final Set<String> from = new HashSet<>();
                    final Set<String> to = new HashSet<>();

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        final String name = DataUtils.substitutePeriodsForSpaces(token);

                        DataUtils.addVariable(knowledge, name);

                        from.add(name);
                    }

                    line = lineizer.nextLine();

                    st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        final String name = DataUtils.substitutePeriodsForSpaces(token);

                        DataUtils.addVariable(knowledge, name);

                        to.add(name);
                    }

                    final KnowledgeGroup group = new KnowledgeGroup(KnowledgeGroup.FORBIDDEN, from, to);

                    knowledge.addKnowledgeGroup(group);
                }
            } else if ("requiredgroup".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    final Set<String> from = new HashSet<>();
                    final Set<String> to = new HashSet<>();

                    RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        final String name = DataUtils.substitutePeriodsForSpaces(token);

                        DataUtils.addVariable(knowledge, name);

                        from.add(name);
                    }

                    line = lineizer.nextLine();

                    st = new RegexTokenizer(line, delimiter, '"');

                    while (st.hasMoreTokens()) {
                        String token = st.nextToken();
                        token = token.trim();
                        final String name = DataUtils.substitutePeriodsForSpaces(token);

                        DataUtils.addVariable(knowledge, name);

                        to.add(name);
                    }

                    final KnowledgeGroup group = new KnowledgeGroup(KnowledgeGroup.REQUIRED, from, to);

                    knowledge.addKnowledgeGroup(group);
                }
            } else if ("forbiddirect".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    final RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
                    String from = null, to = null;

                    if (st.hasMoreTokens()) {
                        from = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        to = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Lines contains more than two elements.");
                    }

                    if (from == null || to == null) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Line contains fewer than two elements.");
                    }

                    DataUtils.addVariable(knowledge, from);

                    DataUtils.addVariable(knowledge, to);

                    knowledge.setForbidden(from, to);
                }
            } else if ("requiredirect".equalsIgnoreCase(line.trim())) {
                while (lineizer.hasMoreLines()) {
                    line = lineizer.nextLine();

                    if (line.startsWith("forbiddirect")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("addtemporal")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("forbiddengroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    if (line.startsWith("requiredgroup")) {
                        firstLine = line;
                        continue SECTIONS;
                    }

                    final RegexTokenizer st = new RegexTokenizer(line, delimiter, '"');
                    String from = null, to = null;

                    if (st.hasMoreTokens()) {
                        from = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        to = st.nextToken();
                    }

                    if (st.hasMoreTokens()) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Lines contains more than two elements.");
                    }

                    if (from == null || to == null) {
                        throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                                + ": Line contains fewer than two elements.");
                    }

                    DataUtils.addVariable(knowledge, from);
                    DataUtils.addVariable(knowledge, to);

                    knowledge.removeForbidden(from, to);
                    knowledge.setRequired(from, to);
                }
            } else {
                throw new IllegalArgumentException("Line " + lineizer.getLineNumber()
                        + ": Expecting 'addtemporal', 'forbiddirect' or 'requiredirect'.");
            }
        }

        return knowledge;
    }

    private static void addVariable(final IKnowledge knowledge, final String from) {
        if (!knowledge.getVariables().contains(from)) {
            knowledge.addVariable(from);
        }
    }

    private static String substitutePeriodsForSpaces(final String s) {
        return s.replaceAll(" ", ".");
    }

    public static double[] removeMissingValues(final double[] data) {
        int c = 0;

        for (final double datum : data) {
            if (Double.isNaN(datum)) c++;
        }

        final double[] data2 = new double[data.length - c];

        int i = 0;

        for (final double datum : data) {
            if (!Double.isNaN(data[i])) {
                data2[i++] = datum;
            }
        }

        return data2;
    }

    @NotNull
    public static DataSet loadContinuousData(final File file, final String commentMarker, final char quoteCharacter,
                                             final String missingValueMarker, final boolean hasHeader, final Delimiter delimiter)
            throws IOException {
        final ContinuousTabularDatasetFileReader dataReader
                = new ContinuousTabularDatasetFileReader(file.toPath(), delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setHasHeader(hasHeader);
        final ContinuousData data = (ContinuousData) dataReader.readInData();
        return (DataSet) DataConvertUtils.toContinuousDataModel(data);
    }

    @NotNull
    public static DataSet loadDiscreteData(final File file, final String commentMarker, final char quoteCharacter,
                                           final String missingValueMarker, final boolean hasHeader, final Delimiter delimiter)
            throws IOException {
        final TabularColumnReader columnReader = new TabularColumnFileReader(file.toPath(), delimiter);
        final DataColumn[] dataColumns = columnReader.readInDataColumns(new int[]{1}, true);

        columnReader.setCommentMarker(commentMarker);

        final TabularDataReader dataReader = new TabularDataFileReader(file.toPath(), delimiter);

        // Need to specify commentMarker, .... again to the TabularDataFileReader
        dataReader.setCommentMarker(commentMarker);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setQuoteCharacter(quoteCharacter);

        final Data data = dataReader.read(dataColumns, hasHeader);
        final DataModel dataModel = DataConvertUtils.toDataModel(data);

        return (DataSet) dataModel;
    }


    /**
     * Reads in a covariance matrix. The format is as follows. </p>
     * <pre>
     * /covariance
     * 100
     * X1   X2   X3   X4
     * 1.4
     * 3.2  2.3
     * 2.5  3.2  5.3
     * 3.2  2.5  3.2  4.2
     * </pre>
     * <pre>
     * CovarianceMatrix dataSet = DataLoader.loadCovMatrix(
     *                           new FileReader(file), " \t", "//");
     * </pre> The initial "/covariance" is optional.
     */
    public static ICovarianceMatrix parseCovariance(final char[] chars, final String commentMarker,
                                                    final DelimiterType delimiterType,
                                                    final char quoteChar,
                                                    final String missingValueMarker) {

        // Do first pass to get a description of the file.
        final CharArrayReader reader = new CharArrayReader(chars);

        // Close the reader and re-open for a second pass to load the data.
        reader.close();
        final CharArrayReader reader2 = new CharArrayReader(chars);
        final ICovarianceMatrix covarianceMatrix = DataUtils.doCovariancePass(reader2, commentMarker,
                delimiterType, quoteChar, missingValueMarker);

        TetradLogger.getInstance().log("info", "\nData set loaded!");
        return covarianceMatrix;
    }

    /**
     * Parses the given files for a tabular data set, returning a
     * RectangularDataSet if successful.
     *
     * @throws IOException if the file cannot be read.
     */
    public static ICovarianceMatrix parseCovariance(final File file, final String commentMarker,
                                                    final DelimiterType delimiterType,
                                                    final char quoteChar,
                                                    final String missingValueMarker) throws IOException {
        FileReader reader = null;

        try {
            reader = new FileReader(file);
            final ICovarianceMatrix covarianceMatrix = DataUtils.doCovariancePass(reader, commentMarker,
                    delimiterType, quoteChar, missingValueMarker);

            TetradLogger.getInstance().log("info", "\nCovariance matrix loaded!");
            return covarianceMatrix;
        } catch (final FileNotFoundException e) {
            throw e;
        } catch (final Exception e) {
            if (reader != null) {
                reader.close();
            }

            throw new RuntimeException("Parsing failed.", e);
        }
    }


    static ICovarianceMatrix doCovariancePass(final Reader reader, final String commentMarker, final DelimiterType delimiterType,
                                              final char quoteChar, final String missingValueMarker) {
        TetradLogger.getInstance().log("info", "\nDATA LOADING PARAMETERS:");
        TetradLogger.getInstance().log("info", "File type = COVARIANCE");
        TetradLogger.getInstance().log("info", "Comment marker = " + commentMarker);
        TetradLogger.getInstance().log("info", "Delimiter type = " + delimiterType);
        TetradLogger.getInstance().log("info", "Quote char = " + quoteChar);
        TetradLogger.getInstance().log("info", "Missing value marker = " + missingValueMarker);
        TetradLogger.getInstance().log("info", "--------------------");

        final Lineizer lineizer = new Lineizer(reader, commentMarker);

        // Skip "/Covariance" if it is there.
        String line = lineizer.nextLine();

        if ("/Covariance".equalsIgnoreCase(line.trim())) {
            line = lineizer.nextLine();
        }

        // Read br sample size.
        RegexTokenizer st = new RegexTokenizer(line, delimiterType.getPattern(), quoteChar);
        final String token = st.nextToken();

        final int n;

        try {
            n = Integer.parseInt(token);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Expected a sample size here, got \"" + token + "\".");
        }

        if (st.hasMoreTokens() && !"".equals(st.nextToken())) {
            throw new IllegalArgumentException(
                    "Line from file has more tokens than expected: \"" + st.nextToken() + "\"");
        }

        // Read br variable names and set up DataSet.
        line = lineizer.nextLine();

        // Variable lists can't have missing values, so we can excuse an extra tab at the end of the line.
        if (line.subSequence(line.length() - 1, line.length()).equals("\t")) {
            line = line.substring(0, line.length() - 1);
        }

        st = new RegexTokenizer(line, delimiterType.getPattern(), quoteChar);

        final List<String> vars = new ArrayList<>();

        while (st.hasMoreTokens()) {
            final String _token = st.nextToken();

            if ("".equals(_token)) {
                TetradLogger.getInstance().log("emptyToken", "Parsed an empty token for a variable name--ignoring.");
                continue;
            }

            vars.add(_token);
        }

        final String[] varNames = vars.toArray(new String[vars.size()]);

        TetradLogger.getInstance().log("info", "Variables:");

        for (final String varName : varNames) {
            TetradLogger.getInstance().log("info", varName + " --> Continuous");
        }

        // Read br covariances.
        final Matrix c = new Matrix(vars.size(), vars.size());

        for (int i = 0; i < vars.size(); i++) {
            st = new RegexTokenizer(lineizer.nextLine(), delimiterType.getPattern(), quoteChar);

            for (int j = 0; j <= i; j++) {
                if (!st.hasMoreTokens()) {
                    throw new IllegalArgumentException("Expecting " + (i + 1)
                            + " numbers on line " + (i + 1)
                            + " of the covariance " + "matrix input.");
                }

                final String literal = st.nextToken();

                if ("".equals(literal)) {
                    TetradLogger.getInstance().log("emptyToken", "Parsed an empty token for a "
                            + "covariance value--ignoring.");
                    continue;
                }

                if ("*".equals(literal)) {
                    c.set(i, j, Double.NaN);
                    c.set(j, i, Double.NaN);
                    continue;
                }

                final double r = Double.parseDouble(literal);

                c.set(i, j, r);
                c.set(j, i, r);
            }
        }

        final IKnowledge knowledge = DataUtils.loadKnowledge(lineizer, delimiterType.getPattern());

        final ICovarianceMatrix covarianceMatrix
                = new CovarianceMatrix(DataUtils.createContinuousVariables(varNames), c, n);

        covarianceMatrix.setKnowledge(knowledge);

        TetradLogger.getInstance().log("info", "\nData set loaded!");
        return covarianceMatrix;
    }
}





