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
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.rmi.MarshalledObject;
import java.text.DecimalFormat;
import java.text.NumberFormat;
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
     * columns in the input dataset.  Hence if the first element of the array of
     * probabilities is alpha, then the first column will contain a -99 (or
     * other missing value code) in a given case with probability alpha. </p>
     * This method will be useful in generating datasets which can be used to
     * test algorithms that handle missing data and/or latent variables. </p>
     * Author:  Frank Wimberly
     *
     * @param inData The data to which random missing data is to be added.
     * @param probs  The probability of adding missing data to each column.
     * @return The new data sets with missing data added.
     */
    public static DataSet addMissingData(
            DataSet inData, double[] probs) {
        DataSet outData;

        try {
            outData = new MarshalledObject<>(inData).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
            Node variable = outData.getVariable(j);

            for (int i = 0; i < outData.getNumRows(); i++) {
                double test = RandomUtil.getInstance().nextDouble();

                if (test < probs[j]) {
                    outData.setObject(i, j,
                            ((Variable) variable).getMissingValueMarker());
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
     * A continuous data set used to construct some other serializable
     * instances.
     */
    public static DataSet continuousSerializableInstance() {
        List<Node> variables = new LinkedList<>();
        variables.add(new ContinuousVariable("X"));
        DataSet dataSet = new ColtDataSet(10, variables);

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
        List<Node> variables = new LinkedList<>();
        variables.add(new DiscreteVariable("X", 2));
        DataSet dataSet = new ColtDataSet(2, variables);
        dataSet.setInt(0, 0, 0);
        dataSet.setInt(1, 0, 1);
        return dataSet;
    }

    /**
     * @return true iff the data sets contains a missing value.
     */
    public static boolean containsMissingValue(TetradMatrix data) {
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

    public static TetradMatrix standardizeData(TetradMatrix data) {
        TetradMatrix data2 = data.copy();

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

            TetradMatrix data2 = DataUtils.standardizeData(dataSet.getDoubleData());

            DataSet dataSet2 = ColtDataSet.makeContinuousData(dataSet.getVariables(), data2);
            outList.add(dataSet2);
        }

        return outList;
    }

    public static DataSet standardizeData(DataSet dataSet) {
        List<DataSet> dataSets = Collections.singletonList(dataSet);
        List<DataSet> outList = standardizeData(dataSets);
        return outList.get(0);
    }

//    public static double[] centerData(double[] data) {
//        double[] data2 = new double[data.length];
//
//        double sum = 0.0;
//
//        for (int i = 0; i < data2.length; i++) {
//            sum += data[i];
//        }
//
//        double mean = sum / data.length;
//
//        for (int i = 0; i < data.length; i++) {
//            data2[i] = data[i] - mean;
//        }
//
//        return data2;
//    }

    public static TetradMatrix centerData(TetradMatrix data) {
        TetradMatrix data2 = data.copy();

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

//    public static DataSet center(DataSet data) {
//        List<DataSet> dataSets = Collections.singletonList(data);
//        return center(dataSets).get(0);
//    }

    public static List<DataSet> center(List<DataSet> dataList) {
        List<DataSet> dataSets = new ArrayList<>();

        for (DataSet dataSet : dataList) {
            dataSets.add(dataSet);
        }

        List<DataSet> outList = new ArrayList<>();

        for (DataModel model : dataSets) {
            if (!(model instanceof DataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            DataSet dataSet = (DataSet) model;

            if (!(dataSet.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + dataSet.getName());
            }

            TetradMatrix data2 = DataUtils.centerData(dataSet.getDoubleData());
            List<Node> list = dataSet.getVariables();
            List<Node> list2 = new ArrayList<>();

            for (Node node : list) {
                list2.add(node);
            }

            DataSet dataSet2 = ColtDataSet.makeContinuousData(list2, data2);
            outList.add(dataSet2);
        }

        return outList;
    }


    public static DataSet discretize(DataSet dataSet, int numCategories, boolean variablesCopied) {
        Discretizer discretizer = new Discretizer(dataSet);
        discretizer.setVariablesCopied(variablesCopied);

        for (Node node : dataSet.getVariables()) {
//            if (dataSet.getVariable(node.getName()) instanceof ContinuousVariable) {
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
    public static TetradMatrix subMatrix(ICovarianceMatrix m, Node x, Node y, List<Node> z) {
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
        TetradMatrix submatrix = m.getSelection(indices, indices);

        if (containsMissingValue(submatrix)) {
            throw new IllegalArgumentException(
                    "Please remove or impute missing values first.");
        }

        return submatrix;
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static TetradMatrix subMatrix(TetradMatrix m, List<Node> variables, Node x, Node y, List<Node> z) {
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

//        if (containsMissingValue(submatrix)) {
//            throw new IllegalArgumentException(
//                    "Please remove or impute missing values first.");
//        }

        return m.getSelection(indices, indices);
    }

    /**
     * @return the submatrix of m with variables in the order of the x variables.
     */
    public static TetradMatrix subMatrix(TetradMatrix m, Map<Node, Integer> indexMap, Node x, Node y, List<Node> z) {
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
    public static TetradMatrix subMatrix(ICovarianceMatrix m, Map<Node, Integer> indexMap, Node x, Node y, List<Node> z) {
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
     * @param dataSet The data set to shuffle.
     * @return Ibid.
     */
    public static DataSet shuffleColumns(DataSet dataSet) {
        int numVariables;

        numVariables = dataSet.getNumColumns();

        List<Integer> indicesList = new ArrayList<>();
        for (int i = 0; i < numVariables; i++) indicesList.add(i);
        Collections.shuffle(indicesList);

        int[] indices = new int[numVariables];

        for (int i = 0; i < numVariables; i++) {
            indices[i] = indicesList.get(i);
        }

        return dataSet.subsetColumns(indices);
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

        DataSet continuousData = new ColtDataSet(dataSet.getNumRows(),
                variables);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            Node variable = dataSet.getVariable(j);

            if (variable instanceof ContinuousVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    continuousData.setDouble(i, j, dataSet.getDouble(i, j));
                }
            } else {
                DiscreteVariable discreteVariable = (DiscreteVariable) variable;

                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    int index = dataSet.getInt(i, j);
                    String catName = discreteVariable.getCategory(index);
                    double value;

                    if (catName.equals("*")) {
                        value = Double.NaN;
                    } else {
                        value = Double.parseDouble(catName);
                    }

                    continuousData.setDouble(i, j, value);
                }
            }
        }

        return continuousData;
    }

    public static DataSet concatenateData(DataSet dataSet1, DataSet dataSet2) {
        List<Node> vars1 = dataSet1.getVariables();
        List<Node> vars2 = dataSet2.getVariables();
        Map<String, Integer> varMap2 = new HashMap<>();
        for (int i = 0; i < vars2.size(); i++) {
            varMap2.put(vars2.get(i).getName(), i);
        }
        int rows1 = dataSet1.getNumRows();
        int rows2 = dataSet2.getNumRows();
        int cols1 = dataSet1.getNumColumns();

        TetradMatrix concatMatrix = new TetradMatrix(rows1 + rows2, cols1);
        TetradMatrix matrix1 = dataSet1.getDoubleData();
        TetradMatrix matrix2 = dataSet2.getDoubleData();

        for (int i = 0; i < vars1.size(); i++) {
            int var2 = varMap2.get(vars1.get(i).getName());
            for (int j = 0; j < rows1; j++) {
                concatMatrix.set(j, i, matrix1.get(j, i));
            }
            for (int j = 0; j < rows2; j++) {
                concatMatrix.set(j + rows1, i, matrix2.get(j, var2));
            }
        }

        return ColtDataSet.makeData(vars1, concatMatrix);
    }

//    public static TetradMatrix concatenateData(TetradMatrix dataSet1, TetradMatrix dataSet2) {
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


    public static DataSet concatenateData(DataSet... dataSets) {
        List<DataSet> _dataSets = new ArrayList<>();

        Collections.addAll(_dataSets, dataSets);

        return concatenateData(_dataSets);
    }

    public static TetradMatrix concatenate(TetradMatrix... dataSets) {
        int totalSampleSize = 0;

        for (TetradMatrix dataSet : dataSets) {
            totalSampleSize += dataSet.rows();
        }

        int numColumns = dataSets[0].columns();
        TetradMatrix allData = new TetradMatrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (TetradMatrix dataSet : dataSets) {
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
    public static DataSet concatenateData(List<DataSet> dataSets) {
        int totalSampleSize = 0;

        for (DataSet dataSet : dataSets) {
            totalSampleSize += dataSet.getNumRows();
        }

        int numColumns = dataSets.get(0).getNumColumns();
        TetradMatrix allData = new TetradMatrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (DataSet dataSet : dataSets) {
            TetradMatrix _data = dataSet.getDoubleData();
            r = _data.rows();

            for (int i = 0; i < r; i++) {
                for (int j = 0; j < numColumns; j++) {
                    allData.set(q + i, j, _data.get(i, j));
                }
            }

            q += r;
        }

        return ColtDataSet.makeContinuousData(dataSets.get(0).getVariables(), allData);
    }

    public static TetradMatrix concatenateTetradMatrices(List<TetradMatrix> dataSets) {
        int totalSampleSize = 0;

        for (TetradMatrix dataSet : dataSets) {
            totalSampleSize += dataSet.rows();
        }

        int numColumns = dataSets.get(0).columns();
        TetradMatrix allData = new TetradMatrix(totalSampleSize, numColumns);
        int q = 0;
        int r;

        for (TetradMatrix _data : dataSets) {
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

    public static DataSet collectVariables(List<DataSet> dataSets) {
        int totalNumColumns = 0;

        for (DataSet dataSet : dataSets) {
            totalNumColumns += dataSet.getNumColumns();
        }

        int numRows = dataSets.get(0).getNumRows();
        TetradMatrix allData = new TetradMatrix(numRows, totalNumColumns);
        int q = 0;
        int cc;

        for (DataSet dataSet : dataSets) {
            TetradMatrix _data = dataSet.getDoubleData();
            cc = _data.columns();

            for (int jj = 0; jj < cc; jj++) {
                for (int ii = 0; ii < numRows; ii++) {
                    allData.set(ii, q + jj, _data.get(ii, jj));
                }
            }

            q += cc;
        }

        List<Node> variables = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            variables.addAll(dataSet.getVariables());
        }

        return ColtDataSet.makeContinuousData(variables, allData);
    }

    public static DataSet concatenateDataNoChecks(List<DataSet> datasets) {
        List<Node> vars1 = datasets.get(0).getVariables();
        int cols = vars1.size();
        int rows = 0;
        for (DataSet dataset : datasets) {
            rows += dataset.getNumRows();
        }

        TetradMatrix concatMatrix = new TetradMatrix(rows, vars1.size());

        int index = 0;

        for (DataSet dataset : datasets) {
            for (int i = 0; i < dataset.getNumRows(); i++) {
                for (int j = 0; j < cols; j++) {
                    concatMatrix.set(index, j, dataset.getDouble(i, j));
                }
                index++;
            }
        }

        return ColtDataSet.makeData(vars1, concatMatrix);
    }


    public static DataSet concatenateDiscreteData(DataSet dataSet1, DataSet dataSet2) {
        List<Node> vars = dataSet1.getVariables();
        int rows1 = dataSet1.getNumRows();
        int rows2 = dataSet2.getNumRows();
        DataSet concatData = new ColtDataSet(rows1 + rows2, vars);

        for (Node var : vars) {
            int var1 = dataSet1.getColumn(dataSet1.getVariable(var.toString()));
            int varc = concatData.getColumn(concatData.getVariable(var.toString()));
            for (int i = 0; i < rows1; i++) {
                concatData.setInt(i, varc, dataSet1.getInt(i, var1));
            }
            int var2 = dataSet2.getColumn(dataSet2.getVariable(var.toString()));
            for (int i = 0; i < rows2; i++) {
                concatData.setInt(i + rows1, varc, dataSet2.getInt(i, var2));
            }
        }

        return concatData;
    }

    public static DataSet noisyZeroes(DataSet dataSet) {
        dataSet = new ColtDataSet((ColtDataSet) dataSet);

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

    public static void printAndersonDarlingPs(DataSet dataSet) {
        System.out.println("Anderson Darling P value for Variables\n");

        NumberFormat nf = new DecimalFormat("0.0000");
        TetradMatrix m = dataSet.getDoubleData();

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            double[] x = m.getColumn(j).toArray();
            double p = new AndersonDarlingTest(x).getP();
            System.out.println("For " + dataSet.getVariable(j) +
                    ", Anderson-Darling p = " + nf.format(p)
                    + (p > 0.05 ? " = Gaussian" : " = Nongaussian"));
        }

    }

    public static DataSet restrictToMeasured(DataSet fullDataSet) {
        List<Node> measuredVars = new ArrayList<>();

        for (Node node : fullDataSet.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                measuredVars.add(node);
            }
        }

        return fullDataSet.subsetColumns(measuredVars);
    }

    public static TetradMatrix cov2(TetradMatrix data) {
        RealMatrix covarianceMatrix = new Covariance(data.getRealMatrix()).getCovarianceMatrix();
        return new TetradMatrix(covarianceMatrix, covarianceMatrix.getRowDimension(), covarianceMatrix.getColumnDimension());
    }

    public static TetradVector means(TetradMatrix data) {
        TetradVector means = new TetradVector(data.columns());

        for (int j = 0; j < means.size(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data.rows(); i++) {
                sum += data.get(i, j);
            }

            double mean = sum / data.rows();

            means.set(j, mean);
        }

        return means;
    }

    /**
     * Column major data.
     */
    public static TetradVector means(double[][] data) {
        TetradVector means = new TetradVector(data.length);
        int rows = data[0].length;

        for (int j = 0; j < means.size(); j++) {
            double sum = 0.0;

            for (int i = 0; i < rows; i++) {
                sum += data[j][i];
            }

            double mean = sum / rows;

            means.set(j, mean);
        }

        return means;
    }

    public static void demean(TetradMatrix data, TetradVector means) {
        for (int j = 0; j < data.columns(); j++) {
            for (int i = 0; i < data.rows(); i++) {
                data.set(i, j, data.get(i, j) - means.get(j));
            }
        }
    }

    /**
     * Column major data.
     */
    public static void demean(double[][] data, TetradVector means) {
        int rows = data[0].length;

        for (int j = 0; j < data.length; j++) {
            for (int i = 0; i < rows; i++) {
                data[j][i] = data[j][i] - means.get(j);
            }
        }
    }

    public static void remean(TetradMatrix data, TetradVector means) {
        for (int j = 0; j < data.columns(); j++) {
            for (int i = 0; i < data.rows(); i++) {
                data.set(i, j, data.get(i, j) + means.get(j));
            }
        }
    }

    public static TetradMatrix covDemeaned(TetradMatrix data) {
        TetradMatrix transpose = data.transpose();
        TetradMatrix prod = transpose.times(data);

        double factor = 1.0 / (data.rows() - 1);

        for (int i = 0; i < prod.rows(); i++) {
            for (int j = 0; j < prod.columns(); j++) {
                prod.set(i, j, prod.get(i, j) * factor);
            }
        }

        return prod;

//        return prod.scalarMult(1.0 / (data.rows() - 1));
    }

    public static TetradMatrix cov(TetradMatrix data) {
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

        RealMatrix q = data.getRealMatrix();

        RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
        RealMatrix q2 = times(q1, q);
        TetradMatrix prod = new TetradMatrix(q2, q.getColumnDimension(), q.getColumnDimension());

        double factor = 1.0 / (data.rows() - 1);

        for (int i = 0; i < prod.rows(); i++) {
            for (int j = 0; j < prod.columns(); j++) {
                prod.set(i, j, prod.get(i, j) * factor);
            }
        }

        return prod;
    }

    public static void simpleTest() {
        double[][] d = new double[][]{
                {1, 2},
                {3, 4},
                {5, 6},
        };

        RealMatrix m = new BlockRealMatrix(d);

        System.out.println(m);

        System.out.println(times(m.transpose(), m));

        System.out.println(MatrixUtils.transposeWithoutCopy(m).multiply(m));

        TetradMatrix n = new TetradMatrix(m, m.getRowDimension(), m.getColumnDimension());

        System.out.println(n);

        RealMatrix q = n.getRealMatrix();

        RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
        RealMatrix q2 = times(q1, q);
        System.out.println(new TetradMatrix(q2, q.getColumnDimension(), q.getColumnDimension()));
    }

    private static RealMatrix times(final RealMatrix m, final RealMatrix n) {
        if (m.getColumnDimension() != n.getRowDimension()) throw new IllegalArgumentException("Incompatible matrices.");

        final int rowDimension = m.getRowDimension();
        final int columnDimension = n.getColumnDimension();

        final RealMatrix out = new BlockRealMatrix(rowDimension, columnDimension);

        final int NTHREADS = Runtime.getRuntime().availableProcessors();

        ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        for (int t = 0; t < NTHREADS; t++) {
            final int _t = t;

            Runnable worker = new Runnable() {
                @Override
                public void run() {
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
                }
            };

            pool.submit(worker);
        }

        while (!pool.isQuiescent()) {
        }

        return out;
    }

    // for online learning.
    public static TetradMatrix onlineCov(TetradMatrix data) {
        int N = data.rows();
        int M = data.columns();

        TetradMatrix cov = new TetradMatrix(M, M);

        double[] m = new double[M];
        double[] d = new double[M];

        for (int j = 0; j < M; j++) {
            m[j] = data.get(0, j);
        }

        for (int j = 0; j < M; j++) {
            for (int k = 0; k < M; k++) {
                cov.set(j, k, 0.0);
            }
        }

        double a = 1.0;
        double b = a;

        for (int i = 1; i < N; i++) {
            double b0 = b;
            b += a;

            for (int j1 = 0; j1 < M; j1++) {
                double mj0 = m[j1];
                double xj = data.get(i, j1);
                d[j1] = (a / b) * (xj - mj0);
                m[j1] += d[j1];
            }

            for (int j = 0; j < M; j++) {
                for (int k = j; k < M; k++) {
                    double cjk0 = cov.get(j, k);

                    double xj = data.get(i, j);
                    double xk = data.get(i, k);

                    double f = (1. / b) * (b0 * cjk0 + b * d[j] * d[k] + a * (xj - m[j]) * (xk - m[k]));

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

    public static TetradVector mean(TetradMatrix data) {
        TetradVector mean = new TetradVector(data.columns());

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
        DataSet dataSet = new ColtDataSet(sampleSize, variables);
        TetradMatrix _cov = cov.getMatrix().copy();

        TetradMatrix cholesky = MatrixUtils.choleskyC(_cov);

        System.out.println("Cholesky decomposition" + cholesky);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            double exoData[] = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            double point[] = new double[exoData.length];

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
    public static TetradMatrix getBootstrapSample(TetradMatrix data, int sampleSize) {
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

        return ColtDataSet.makeData(data.getVariables(), data.getDoubleData().getSelection(rows, cols));
    }

    /**
     * @return a sample without replacement with the given sample size from the
     * given dataset. May return a sample of less than the given size; makes
     * sampleAttempts attempts to sample.
     */
    public static DataSet getBootstrapSample2(DataSet data, int sampleAttempts) {
        int actualSampleSize = data.getNumRows();
        List<Integer> samples = new ArrayList<>();

        for (int i = 0; i < sampleAttempts; i++) {
            int sample = RandomUtil.getInstance().nextInt(actualSampleSize);
            if (!samples.contains(sample)) samples.add(sample);
        }

        int[] rows = new int[samples.size()];

        for (int i = 0; i < samples.size(); i++) {
            rows[i] = samples.get(i);
        }

        int[] cols = new int[data.getNumColumns()];
        for (int i = 0; i < cols.length; i++) cols[i] = i;

        return ColtDataSet.makeData(data.getVariables(), data.getDoubleData().getSelection(rows, cols));
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

    /**
     * @param dataSet The data; missing values are permitted.
     * @param vars    The indices of the targeted variables in the data. The returned covariance matrix will have
     *                variables in the same order.
     * @param n       The returned sample size, n[0]. Provide a length 1 array.
     * @return The reduced covariance matrix.
     */
    public static TetradMatrix covMatrixForDefinedRows(DataSet dataSet, int[] vars, int[] n) {
        DataSet _dataSet = new ColtDataSet((ColtDataSet) dataSet);
        _dataSet = center(_dataSet);

        TetradMatrix reduced = new TetradMatrix(vars.length, vars.length);

        List<Integer> rows = new ArrayList<>();

        I:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int var : vars) {
                if (Double.isNaN(_dataSet.getDouble(i, var))) {
                    continue I;
                }
            }

            rows.add(i);
        }

        for (int i = 0; i < reduced.rows(); i++) {
            for (int j = 0; j < reduced.columns(); j++) {
                double sum = 0.0;

                for (int k : rows) {
                    double v = _dataSet.getDouble(k, vars[i]) * _dataSet.getDouble(k, vars[j]);
                    sum += v;
                }

                reduced.set(i, j, sum / rows.size());
            }
        }

        n[0] = rows.size();

        return reduced;
    }

    public static IKnowledge createRequiredKnowledge(Graph resultGraph) {
        IKnowledge knowledge = new Knowledge2();

        List<Node> nodes = resultGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
//                if (resultGraph.getEdges().size() >= 2) continue;
                if (nodes.get(i).getName().startsWith("E_")) continue;
                if (nodes.get(j).getName().startsWith("E_")) continue;

                Edge edge = resultGraph.getEdge(nodes.get(i), nodes.get(j));

                if (edge == null) {
                } else if (edge.isDirected()) {
                    Node node1 = edge.getNode1();
                    Node node2 = edge.getNode2();
//                    knowledge.setEdgeForbidden(node2.getName(), node1.getName(), true);
                    knowledge.setRequired(node1.getName(), node2.getName());
                } else if (Edges.isUndirectedEdge(edge)) {
                    Node node1 = edge.getNode1();
                    Node node2 = edge.getNode2();
                    knowledge.setRequired(node1.getName(), node2.getName());
                    knowledge.setRequired(node2.getName(), node1.getName());
                }
            }
        }

        return knowledge;
    }

    public static DataSet reorderColumns(DataSet dataModel) {
        List<Node> vars = new ArrayList<>();

        List<Node> variables = dataModel.getVariables();
        Collections.shuffle(variables);

        for (Node node : variables) {
            Node _node = dataModel.getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        return dataModel.subsetColumns(vars);
    }

    public static List<DataSet> reorderColumns(List<DataSet> dataSets) {
        List<Node> vars = new ArrayList<>();

        List<Node> variables = dataSets.get(0).getVariables();
        Collections.shuffle(variables);

        for (Node node : variables) {
            Node _node = dataSets.get(0).getVariable(node.getName());

            if (_node != null) {
                vars.add(_node);
            }
        }

        List<DataSet> ret = new ArrayList<>();

        for (DataSet m : dataSets) {
            ret.add(m.subsetColumns(vars));
        }

        return ret;
    }

    public static ICovarianceMatrix reorderColumns(ICovarianceMatrix cov) {
        List<String> vars = new ArrayList<>();

        List<Node> variables = new ArrayList<>(cov.getVariables());
        Collections.shuffle(variables);

        for (Node node : variables) {
            Node _node = cov.getVariable(node.getName());

            if (_node != null) {
                vars.add(_node.getName());
            }
        }

        return cov.getSubmatrix(vars);
    }


    public static ICovarianceMatrix covarianceNonparanormalDrton(DataSet dataSet) {
        final CovarianceMatrix covMatrix = new CovarianceMatrix(dataSet);
        final TetradMatrix data = dataSet.getDoubleData();
        final int NTHREDS = Runtime.getRuntime().availableProcessors() * 10;
        final int EPOCH_COUNT = 100000;

        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);
        int runnableCount = 0;

        for (int _i = 0; _i < dataSet.getNumColumns(); _i++) {
            for (int _j = _i; _j < dataSet.getNumColumns(); _j++) {
                final int i = _i;
                final int j = _j;

//                double tau = StatUtils.rankCorrelation(data.viewColumn(i).toArray(), data.viewColumn(j).toArray());
                Runnable worker = new Runnable() {
                    @Override
                    public void run() {
                        double tau = StatUtils.kendallsTau(data.getColumn(i).toArray(), data.getColumn(j).toArray());
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
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            System.out.println("Finished all threads");
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
        final TetradMatrix data = dataSet.getDoubleData();
        final TetradMatrix X = data.like();
        final double n = dataSet.getNumRows();
        final double delta = 1.0 / (4.0 * Math.pow(n, 0.25) * Math.sqrt(Math.PI * Math.log(n)));

        final NormalDistribution normalDistribution = new NormalDistribution();

        double std = Double.NaN;

        for (int j = 0; j < data.columns(); j++) {
            final double[] x1 = data.getColumn(j).toArray();
            double std1 = StatUtils.sd(x1);
            double mu1 = StatUtils.mean(x1);
            double[] x = ranks(data, x1);

            for (int i = 0; i < x.length; i++) {
                x[i] /= n;
                if (x[i] < delta) x[i] = delta;
                if (x[i] > (1. - delta)) x[i] = 1. - delta;
                x[i] = normalDistribution.inverseCumulativeProbability(x[i]);
            }

            if (Double.isNaN(std)) {
                std = StatUtils.sd(x);
            }

            for (int i = 0; i < x.length; i++) {
                x[i] /= std;
                x[i] *= std1;
                x[i] += mu1;
            }

            X.assignColumn(j, new TetradVector(x));
        }
        return ColtDataSet.makeContinuousData(dataSet.getVariables(), X);
    }

    private static double[] ranks(TetradMatrix data, double[] x) {
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
            }

            if (!constant) keepCols.add(j);
        }

        int[] newCols = new int[keepCols.size()];
        for (int j = 0; j < keepCols.size(); j++) newCols[j] = keepCols.get(j);

        return dataSet.subsetColumns(newCols);
    }
}





