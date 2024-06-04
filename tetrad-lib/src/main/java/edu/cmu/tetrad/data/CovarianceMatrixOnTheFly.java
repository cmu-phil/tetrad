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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

/**
 * Stores a covariance matrix together with variable names and sample size, intended as a representation of a data set.
 * When constructed from a continuous data set, the matrix is not checked for positive definiteness; however, when a
 * covariance matrix is supplied, its positive definiteness is always checked. If the sample size is less than the
 * number of variables, the positive definiteness is "spot-checked"--that is, checked for various submatrices.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see CorrelationMatrix
 */
public class CovarianceMatrixOnTheFly implements ICovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The variances of the variables.
     */
    private final double[] variances;

    /**
     * Whether to print out verbose information.
     */
    private boolean verbose = false;
    /**
     * The name of the covariance matrix.
     */
    private String name;
    /**
     * The variables (in order) for this covariance matrix.
     */
    private List<Node> variables;
    /**
     * The size of the sample from which this covariance matrix was calculated.
     */
    private int sampleSize;
    /**
     * Stored matrix data. Should be square. This may be set by derived classes, but it must always be set to a
     * legitimate covariance matrix.
     */
    private Matrix matrix;
    /**
     * The list of selected variables.
     */
    private Set<Node> selectedVariables = new HashSet<>();
    /**
     * The knowledge for this data.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The vectors for the variables.
     */
    private double[][] vectors = null;

    /**
     * Constructs a new covariance matrix from the given data set. If dataSet is a BoxDataSet with a
     * VerticalDoubleDataBox, the data will be mean-centered by the constructor; is non-mean-centered version of the
     * data is needed, the data should be copied before being send into the constructor.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @throws java.lang.IllegalArgumentException if this is not a continuous data set.
     */
    public CovarianceMatrixOnTheFly(DataSet dataSet) {
        this(dataSet, false);
    }

    /**
     * <p>Constructor for CovarianceMatrixOnTheFly.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param verbose a boolean
     */
    public CovarianceMatrixOnTheFly(DataSet dataSet, boolean verbose) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Not a continuous data set.");
        }

        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();

        if (verbose) {
            System.out.println("Calculating variable vectors");
        }

        if (dataSet instanceof BoxDataSet) {

            DataBox box = ((BoxDataSet) dataSet).getDataBox();

            if (box instanceof VerticalDoubleDataBox) {
                if (verbose) {
                    System.out.println("Getting vectors from VerticalDoubleDataBox");
                }
//                box = box.copy();

                if (!dataSet.getVariables().equals(variables)) throw new IllegalArgumentException();

                vectors = ((VerticalDoubleDataBox) box).getVariableVectors();

                if (verbose) {
                    System.out.println("Calculating means");
                }

                Vector means = DataUtils.means(vectors);
                demean(vectors, means);
            } else if (box instanceof DoubleDataBox) {
                if (verbose) {
                    System.out.println("Getting vectors from DoubleDataBox");
                }
                if (!dataSet.getVariables().equals(variables)) throw new IllegalArgumentException();
                double[][] horizData = ((DoubleDataBox) box).getData();

                if (verbose) {
                    System.out.println("Transposing data");
                }

                vectors = new double[horizData[0].length][horizData.length];

                for (int i = 0; i < horizData.length; i++) {
                    for (int j = 0; j < horizData[0].length; j++) {
                        vectors[j][i] = horizData[i][j];
                    }
                }

                if (verbose) {
                    System.out.println("Calculating means");
                }

                Vector means = DataUtils.means(vectors);
                demean(vectors, means);
            }


        }

        if (vectors == null) {
            if (verbose) {
                System.out.println("Copying data");
            }

            Matrix doubleData = dataSet.getDoubleData().copy();

            if (verbose) {
                System.out.println("Calculating means");
            }

            Vector means = DataUtils.means(doubleData);

            if (verbose) {
                System.out.println("Demeaning");
            }

            demean(vectors, means);

            if (verbose) {
                System.out.println("Getting vectors from data");
            }

            vectors = new double[variables.size()][];

            for (int i = 0; i < variables.size(); i++) {
                vectors[i] = matrix.getColumn(i).toArray();
            }
        }

        if (verbose) {
            System.out.println("Calculating variances");
        }

        this.variances = new double[variables.size()];

        class VarianceTask extends RecursiveTask<Boolean> {
            private final int chunk;
            private final int from;
            private final int to;

            public VarianceTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        double d = 0.0D;

                        int count = 0;

                        double[] v1 = vectors[i];

                        for (int k = 0; k < sampleSize; ++k) {
                            if (Double.isNaN(v1[k])) {
                                continue;
                            }

                            d += v1[k] * v1[k];
                            count++;
                        }

                        double v = d;
//                        v /= (sampleSize - 1);
                        v /= (count - 1);

                        variances[i] = v;

                        if (v == 0) {
                            System.out.println("Zero variance! " + variables.get(i));
                        }
                    }

                    return true;
                } else {
                    final int numIntervals = 4;

                    int step = (to - from) / numIntervals + 1;

                    List<VarianceTask> tasks = new ArrayList<>();

                    for (int i = 0; i < numIntervals; i++) {
                        VarianceTask task = new VarianceTask(chunk, from + i * step, FastMath.min(from + (i + 1) * step, to));
                        tasks.add(task);
                    }

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        int NTHREADS = Runtime.getRuntime().availableProcessors() * 10;
        int _chunk = variables.size() / NTHREADS + 1;
        int minChunk = 100;
        int chunk = FastMath.max(_chunk, minChunk);

        VarianceTask task = new VarianceTask(chunk, 0, variables.size());
        int parallelism = Runtime.getRuntime().availableProcessors();
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        try {
            pool.invoke(task);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        if (!pool.awaitQuiescence(1, TimeUnit.DAYS)) {
            throw new IllegalStateException("Pool timed out");
        }

        if (verbose) {
            System.out.println("Done with variances.");
        }


    }

    /**
     * <p>demean.</p>
     *
     * @param data  an array of {@link double} objects
     * @param means a {@link edu.cmu.tetrad.util.Vector} object
     */
    public static void demean(double[][] data, Vector means) {
        for (int j = 0; j < data.length; j++) {
            for (int i = 0; i < data[j].length; i++) {
                data[j][i] -= means.get(j);
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public static ICovarianceMatrix serializableInstance() {
        List<Node> variables = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        variables.add(x);
        Matrix matrix = Matrix.identity(1);
        return new CovarianceMatrix(variables, matrix, 100); //
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return this.variables;
    }

    /**
     * {@inheritDoc}
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = variables;
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return the variable names, in order.
     */
    public final List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            names.add(variable.getName());
        }

        return names;
    }

    /**
     * {@inheritDoc}
     */
    public final String getVariableName(int index) {
        if (index >= getVariables().size()) {
            throw new IllegalArgumentException("Index out of range: " + index);
        }

        Node variable = getVariables().get(index);
        return variable.getName();
    }

    /**
     * <p>getDimension.</p>
     *
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return variables.size();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (&gt; 0).
     */
    public final int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * {@inheritDoc}
     */
    public final void setSampleSize(int sampleSize) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        this.sampleSize = sampleSize;
    }

    /**
     * Gets the name of the covariance matrix.
     *
     * @return a {@link java.lang.String} object
     */
    public final String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the covariance matrix.
     */
    public final void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return the knowledge associated with this data.
     */
    public final Knowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Associates knowledge with this data.
     */
    public final void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param indices an array of {@link int} objects
     * @return a submatrix of the covariance matrix with variables in the given order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(variables.get(indice));
        }

        Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                double d = getValue(indices[i], indices[j]);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param indices  an array of {@link int} objects
     * @param dataRows an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices, int[] dataRows) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(variables.get(indice));
        }

        Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                double d = getValue(indices[i], indices[j], dataRows);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames an array of {@link java.lang.String} objects
     * @return a submatrix of this matrix, with variables in the given order.
     */
    public final CovarianceMatrixOnTheFly getSubmatrix(String[] submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public final double getValue(int i, int j) {
        if (i == j) {
            return variances[i];
        }

        double d = 0.0D;

        double[] v1 = vectors[i];
        double[] v2 = vectors[j];
        int count = 0;

        for (int k = 0; k < sampleSize; k++) {
            if (Double.isNaN(v1[k])) continue;
            if (Double.isNaN(v2[k])) continue;

            d += v1[k] * v2[k];
            count++;
        }

        double v = d;
//        v /= (sampleSize - 1);
        v /= (count - 1);
        return v;
    }

    /**
     * <p>getValue.</p>
     *
     * @param i    a int
     * @param j    a int
     * @param rows an array of {@link int} objects
     * @return a double
     */
    public final double getValue(int i, int j, int[] rows) {
        double d = 0.0D;

        double[] v1 = vectors[i];
        double[] v2 = vectors[j];
        int count = 0;

        for (int k : rows) {
            if (Double.isNaN(v1[k])) continue;
            if (Double.isNaN(v2[k])) continue;

            d += v1[k] * v2[k];
            count++;
        }

        double v = d;
//        v /= (sampleSize - 1);
        v /= (count - 1);
//        v /= count;
        return v;
    }

    /**
     * <p>getSize.</p>
     *
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return getVariables().size();
    }

    /**
     * <p>Getter for the field <code>matrix</code>.</p>
     *
     * @return a copy of the covariance matrix.
     */
    public final Matrix getMatrix() {
        Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j));
            }
        }

        return matrix;
    }

    /**
     * {@inheritDoc}
     */
    public void setMatrix(Matrix matrix) {
        this.matrix = matrix;
        checkMatrix();
    }

    /**
     * <p>Getter for the field <code>matrix</code>.</p>
     *
     * @param rows an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public final Matrix getMatrix(int[] rows) {
        Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j, rows));
            }
        }

        return matrix;
    }

    /**
     * {@inheritDoc}
     */
    public final void select(Node variable) {
        if (variables.contains(variable)) {
            getSelectedVariables().add(variable);
        }
    }

    /**
     * <p>clearSelection.</p>
     */
    public final void clearSelection() {
        getSelectedVariables().clear();
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isSelected(Node variable) {
        if (variable == null) {
            throw new NullPointerException("Null variable. Try again.");
        }

        return getSelectedVariables().contains(variable);
    }

    /**
     * <p>getSelectedVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public final List<String> getSelectedVariableNames() {
        List<String> selectedVariableNames = new LinkedList<>();

        for (Node variable : selectedVariables) {
            selectedVariableNames.add(variable.getName());
        }

        return selectedVariableNames;
    }

    /**
     * Prints out the matrix
     *
     * @return a {@link java.lang.String} object
     */
    public final String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();

        int numVars = getVariableNames().size();
        buf.append(getSampleSize()).append("\n");

        for (int i = 0; i < numVars; i++) {
            String name = getVariableNames().get(i);
            buf.append(name).append("\t");
        }

        buf.append("\n");

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                buf.append(nf.format(getValue(i, j))).append("\t");
            }
            buf.append("\n");
        }

        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContinuous() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDiscrete() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMixed() {
        return false;
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Matrix getSelection(int[] rows, int[] cols) {
        Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    /**
     * <p>getSelection.</p>
     *
     * @param rows     an array of {@link int} objects
     * @param cols     an array of {@link int} objects
     * @param dataRows an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getSelection(int[] rows, int[] cols, int[] dataRows) {
        Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j], dataRows);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j], dataRows);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    /**
     * {@inheritDoc}
     */
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel copy() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValue(int i, int j, double v) {
        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeVariables(List<String> remaining) {
        ICovarianceMatrix cov = getSubmatrix(remaining);
        this.matrix = cov.getMatrix();
        this.variables = cov.getVariables();
        clearSelection();
    }

    private Set<Node> getSelectedVariables() {
        return selectedVariables;
    }

    /**
     * Checks the sample size, variable, and matrix information.
     */
    private void checkMatrix() {
        int numVars = variables.size();

        for (Node variable : variables) {
            if (variable == null) {
                throw new NullPointerException();
            }
        }

        if (sampleSize < 1) {
            throw new IllegalArgumentException(
                    "Sample size must be at least 1.");
        }

        if (numVars != matrix.getNumRows() || numVars != matrix.getNumColumns()) {
            throw new IllegalArgumentException("Number of variables does not " +
                                               "equal the dimension of the matrix.");
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}





