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

import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/**
 * Some static utility methods for dealing with data sets.
 *
 * @author Various folks.
 * @version $Id: $Id
 */
public final class DataUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private DataUtils() {
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
     * <p>defaultCategory.</p>
     *
     * @param index Ond plus the given index.
     * @return the default category for index i. (The default category should ALWAYS be obtained by calling this
     * method.)
     */
    public static String defaultCategory(int index) {
        return Integer.toString(index);
    }

    /**
     * A discrete data set used to construct some other serializable instances.
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
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
     * <p>containsMissingValue.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return true iff the data sets contains a missing value.
     */
    public static boolean containsMissingValue(Matrix data) {
        for (int i = 0; i < data.getNumRows(); i++) {
            for (int j = 0; j < data.getNumColumns(); j++) {
                if (Double.isNaN(data.get(i, j))) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * <p>containsMissingValue.</p>
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a boolean
     */
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
     * <p>createContinuousVariables.</p>
     *
     * @param varNames an array of {@link java.lang.String} objects
     * @return a {@link java.util.List} object
     */
    public static List<Node> createContinuousVariables(String[] varNames) {
        List<Node> variables = new LinkedList<>();

        for (String varName : varNames) {
            variables.add(new ContinuousVariable(varName));
        }

        return variables;
    }

    /**
     * <p>subMatrix.</p>
     *
     * @param m a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.List} object
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
     * <p>subMatrix.</p>
     *
     * @param m         a {@link edu.cmu.tetrad.util.Matrix} object
     * @param variables a {@link java.util.List} object
     * @param x         a {@link edu.cmu.tetrad.graph.Node} object
     * @param y         a {@link edu.cmu.tetrad.graph.Node} object
     * @param z         a {@link java.util.List} object
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
     * <p>subMatrix.</p>
     *
     * @param m        a {@link edu.cmu.tetrad.util.Matrix} object
     * @param indexMap a {@link java.util.Map} object
     * @param x        a {@link edu.cmu.tetrad.graph.Node} object
     * @param y        a {@link edu.cmu.tetrad.graph.Node} object
     * @param z        a {@link java.util.List} object
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
     * <p>subMatrix.</p>
     *
     * @param m        a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @param indexMap a {@link java.util.Map} object
     * @param x        a {@link edu.cmu.tetrad.graph.Node} object
     * @param y        a {@link edu.cmu.tetrad.graph.Node} object
     * @param z        a {@link java.util.List} object
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

    /**
     * <p>means.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public static Vector means(Matrix data) {
        Vector means = new Vector(data.getNumColumns());

        for (int j = 0; j < means.size(); j++) {
            double sum = 0.0;
            int count = 0;

            for (int i = 0; i < data.getNumRows(); i++) {
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
     *
     * @param data an array of {@link double} objects
     * @return a {@link edu.cmu.tetrad.util.Vector} object
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

    /**
     * <p>cov.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix cov(Matrix data) {
        for (int j = 0; j < data.getNumColumns(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data.getNumRows(); i++) {
                sum += data.get(i, j);
            }

            double mean = sum / data.getNumRows();

            for (int i = 0; i < data.getNumRows(); i++) {
                data.set(i, j, data.get(i, j) - mean);
            }
        }

        RealMatrix q = new BlockRealMatrix(data.toArray());

        RealMatrix q1 = MatrixUtils.transposeWithoutCopy(q);
        RealMatrix q2 = DataUtils.times(q1, q);
        Matrix prod = new Matrix(q2.getData());

        double factor = 1.0 / (data.getNumRows() - 1);

        for (int i = 0; i < prod.getNumRows(); i++) {
            for (int j = 0; j < prod.getNumColumns(); j++) {
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

        ForkJoinPool pool = ForkJoin.getInstance().newPool(Runtime.getRuntime().availableProcessors());

        for (int t = 0; t < NTHREADS; t++) {
            int _t = t;

            Runnable worker = () -> {
                int chunk = rowDimension / NTHREADS + 1;
                for (int row = _t * chunk; row < FastMath.min((_t + 1) * chunk, rowDimension); row++) {
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

    /**
     * <p>mean.</p>
     *
     * @param data a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Vector} object
     */
    public static Vector mean(Matrix data) {
        Vector mean = new Vector(data.getNumColumns());

        for (int i = 0; i < data.getNumColumns(); i++) {
            mean.set(i, StatUtils.mean(data.getColumn(i).toArray()));
        }

        return mean;

    }

    /**
     * <p>choleskySimulation.</p>
     *
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
            double[] exoData = new double[cholesky.getNumRows()];

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
     * <p>ranks.</p>
     *
     * @param x an array of {@link double} objects
     * @return an array of {@link double} objects
     */
    public static double[] ranks(double[] x) {
        int numRows = x.length;
        double[] ranks = new double[numRows];

        for (int i = 0; i < numRows; i++) {
            double d = x[i];
            int count = 0;

            for (double v : x) {
                if (v <= d) {
                    count++;
                }
            }

            ranks[i] = count;
        }

        return ranks;
    }

    /**
     * <p>getExampleNonsingular.</p>
     *
     * @param covarianceMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @param depth            a int
     * @return a {@link java.util.List} object
     */
    public static List<Node> getExampleNonsingular(ICovarianceMatrix covarianceMatrix, int depth) {
        List<Node> variables = covarianceMatrix.getVariables();

        SublistGenerator generator = new SublistGenerator(variables.size(), depth);
        int[] choice;

        while ((choice = generator.next()) != null) {
            if (choice.length < 2) continue;
            List<Node> _choice = GraphUtils.asList(choice, variables);

            List<String> names = new ArrayList<>();

            for (Node node : _choice) {
                names.add(node.getName());
            }

            ICovarianceMatrix _dataSet = covarianceMatrix.getSubmatrix(names);

            if (new CovarianceMatrix(_dataSet).isSingular()) {
                return _choice;
            }
        }

        return null;
    }

    /**
     * Returns the equivalent sample size, assuming all units are equally correlated and all unit variances are equal.
     *
     * @param covariances a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @return a double
     */
    public static double getEss(ICovarianceMatrix covariances) {
        Matrix C = new CorrelationMatrix(covariances).getMatrix();

        double m = covariances.getSize();
        double n = covariances.getSampleSize();

        double sum = 0;

        for (int i = 0; i < C.getNumRows(); i++) {
            for (int j = 0; j < C.getNumColumns(); j++) {
                sum += C.get(i, j);
            }
        }

        double rho = (n * sum - n * m) / (m * (n * n - n));
        return n / (1. + (n - 1.) * rho);
    }
}





