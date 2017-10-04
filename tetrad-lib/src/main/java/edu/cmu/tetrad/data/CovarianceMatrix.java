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

//import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Stores a covariance matrix together with variable names and sample size,
 * intended as a representation of a data set. When constructed from a
 * continuous data set, the matrix is not checked for positive definiteness;
 * however, when a covariance matrix is supplied, its positive definiteness is
 * always checked. If the sample size is less than the number of variables, the
 * positive definiteness is "spot-checked"--that is, checked for various
 * submatrices.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see edu.cmu.tetrad.data.CorrelationMatrix
 */
public class CovarianceMatrix implements ICovarianceMatrix {

    static final long serialVersionUID = 23L;

    /**
     * The name of the covariance matrix.
     *
     * @serial May be null.
     */
    private String name;

    /**
     * The variables (in order) for this covariance matrix.
     *
     * @serial Cannot be null.
     */
    private List<Node> variables;

    /**
     * The size of the sample from which this covariance matrix was calculated.
     *
     * @serial Range > 0.
     */
    private int sampleSize;

    /**
     * Stored matrix data. Should be square. This may be set by derived classes,
     * but it must always be set to a legitimate covariance matrix.
     *
     * @serial Cannot be null. Must be symmetric and positive definite.
     */
    private TetradMatrix matrix;

    /**
     * The list of selected variables.
     *
     * @serial Cannot be null.
     */
    private Set<Node> selectedVariables = new HashSet<>();

    /**
     * The knowledge for this data.
     *
     * @serial Cannot be null.
     */
    private IKnowledge knowledge = new Knowledge2();

    private double[][] vectors = null;

    //=============================CONSTRUCTORS=========================//
    /**
     * Constructs a new covariance matrix from the given data set.
     *
     * @throws IllegalArgumentException if this is not a continuous data set.
     */
    public CovarianceMatrix(DataSet dataSet) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Not a continuous data set.");
        }

        this.matrix = new TetradMatrix(dataSet.getNumColumns(), dataSet.getNumColumns());

        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();

        if (dataSet instanceof BoxDataSet) {

            DataBox box = ((BoxDataSet) dataSet).getDataBox().copy();

            if (box instanceof VerticalDoubleDataBox) {
                if (!dataSet.getVariables().equals(variables)) {
                    throw new IllegalArgumentException();
                }

                vectors = ((VerticalDoubleDataBox) box).getVariableVectors();

//                final TetradMatrix doubleData = dataSet.getDoubleData();
//                DataUtils.remean(doubleData, means);
            }

        }

        if (vectors == null) {
            final TetradMatrix doubleData = dataSet.getDoubleData().copy();
            TetradVector means = DataUtils.means(doubleData);
            DataUtils.demean(doubleData, means);

            final RealMatrix realMatrix = doubleData.getRealMatrix();

            vectors = new double[variables.size()][];

            for (int i = 0; i < variables.size(); i++) {
                vectors[i] = realMatrix.getColumnVector(i).toArray();
            }
        }

        TetradVector means = DataUtils.means(vectors);

        int NTHREADS = Runtime.getRuntime().availableProcessors() * 10;
        int _chunk = variables.size() / NTHREADS + 1;
        int minChunk = 100;
        final int chunk = _chunk < minChunk ? minChunk : _chunk;

        class VarianceTask extends RecursiveTask<Boolean> {

            private int chunk;
            private int from;
            private int to;

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
                        v /= (count - 1);

                        matrix.set(i, i, v);

                        if (v == 0) {
                            System.out.println("    Zero variance! " + variables.get(i));
                        }
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    VarianceTask left = new VarianceTask(chunk, from, mid);
                    VarianceTask right = new VarianceTask(chunk, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        class RestOfThemTask extends RecursiveTask<Boolean> {

            private int chunk;
            private int from;
            private int to;

            public RestOfThemTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        for (int j = 0; j < i; j++) {

                            double d = 0.0D;

                            double[] v1 = vectors[i];
                            double[] v2 = vectors[j];
                            int count = 0;

                            for (int k = 0; k < sampleSize; k++) {
                                if (Double.isNaN(v1[k])) {
                                    continue;
                                }
                                if (Double.isNaN(v2[k])) {
                                    continue;
                                }

                                d += v1[k] * v2[k];
                                count++;
                            }

                            double v = d;
                            v /= (count - 1);

                            matrix.set(i, j, v);
                            matrix.set(j, i, v);
                        }
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    RestOfThemTask left = new RestOfThemTask(chunk, from, mid);
                    RestOfThemTask right = new RestOfThemTask(chunk, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        VarianceTask task = new VarianceTask(chunk, 0, variables.size());
        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        RestOfThemTask task2 = new RestOfThemTask(chunk, 0, variables.size());
        ForkJoinPoolInstance.getInstance().getPool().invoke(task2);

        DataUtils.demean(vectors, means);

        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
    }

    /**
     * Protected constructor to construct a new covariance matrix using the
     * supplied continuous variables and the the given symmetric, positive
     * definite matrix and sample size. The number of variables must equal the
     * dimension of the array.
     *
     * @param variables the list of variables (in order) for the covariance
     * matrix.
     * @param matrix an square array of containing covariances.
     * @param sampleSize the sample size of the data for these covariances.
     * @throws IllegalArgumentException if the given matrix is not symmetric (to
     * a tolerance of 1.e-5) and positive definite, if the number of variables
     * does not equal the dimension of m, or if the sample size is not positive.
     */
    public CovarianceMatrix(List<Node> variables, TetradMatrix matrix,
            int sampleSize) {
        if (variables.size() != matrix.rows() && variables.size() != matrix.columns()) {
            throw new IllegalArgumentException("# variables not equal to matrix dimension.");
        }

        this.variables = Collections.unmodifiableList(variables);
        this.sampleSize = sampleSize;
        this.matrix = matrix;
        checkMatrix();
    }

    /**
     * Copy constructor.
     */
    public CovarianceMatrix(CovarianceMatrix covMatrix) {
        this(covMatrix.variables, covMatrix.matrix,
                covMatrix.sampleSize);
    }

    public CovarianceMatrix(ICovarianceMatrix covMatrix) {
        this(covMatrix.getVariables(), covMatrix.getMatrix(),
                covMatrix.getSampleSize());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ICovarianceMatrix serializableInstance() {
        List<Node> variables = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        variables.add(x);
        TetradMatrix matrix = TetradAlgebra.identity(1);
        return new CovarianceMatrix(variables, matrix, 100);
    }

    //============================PUBLIC METHODS=========================//
    /**
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return this.variables;
    }

    /**
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
     * @return the variable name at the given index.
     */
    public final String getVariableName(int index) {
        if (index >= getVariables().size()) {
            throw new IllegalArgumentException("Index out of range: " + index);
        }

        Node variable = getVariables().get(index);
        return variable.getName();
    }

    /**
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return variables.size();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (> 0).
     */
    public final int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Gets the name of the covariance matrix.
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Sets the name of the covariance matrix.
     */
    public final void setName(String name) {
        this.name = name;
    }

    /**
     * @return the knowledge associated with this data.
     */
    public final IKnowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * Associates knowledge with this data.
     */
    public final void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * @return a submatrix of the covariance matrix with variables in the given
     * order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(variables.get(indice));
        }

        TetradMatrix cov = matrix.getSelection(indices, indices);
        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        String[] varNames = new String[submatrixVarNames.size()];

        for (int i = 0; i < submatrixVarNames.size(); i++) {
            varNames[i] = submatrixVarNames.get(i);
        }

        return getSubmatrix(varNames);
    }

    /**
     * @return a submatrix of this matrix, with variables in the given order.
     */
    public final CovarianceMatrix getSubmatrix(String[] submatrixVarNames) {
        List<Node> submatrixVars = new LinkedList<>();

        for (String submatrixVarName : submatrixVarNames) {
            submatrixVars.add(getVariable(submatrixVarName));
        }

        if (!getVariables().containsAll(submatrixVars)) {
            throw new IllegalArgumentException(
                    "The variables in the submatrix "
                    + "must be in the original matrix: original=="
                    + getVariables() + ", sub==" + submatrixVars);
        }

        for (int i = 0; i < submatrixVars.size(); i++) {
            if (submatrixVars.get(i) == null) {
                throw new NullPointerException(
                        "The variable name at index " + i + " is null.");
            }
        }

        int[] indices = new int[submatrixVars.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = getVariables().indexOf(submatrixVars.get(i));
        }

        TetradMatrix cov = matrix.getSelection(indices, indices);
        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    /**
     * @return the value of element (i,j) in the matrix
     */
    public final double getValue(int i, int j) {
        return matrix.get(i, j);
    }

    public void setMatrix(TetradMatrix matrix) {
        this.matrix = matrix;
        checkMatrix();
    }

    public final void setSampleSize(int sampleSize) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        this.sampleSize = sampleSize;
    }

    /**
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return matrix.rows();
    }

    /**
     * @return a the covariance matrix (not a copy).
     */
    public final TetradMatrix getMatrix() {
        return matrix;
    }

    public final void select(Node variable) {
        if (variables.contains(variable)) {
            getSelectedVariables().add(variable);
        }
    }

    public final void clearSelection() {
        getSelectedVariables().clear();
    }

    public final boolean isSelected(Node variable) {
        if (variable == null) {
            throw new NullPointerException("Null variable. Try again.");
        }

        return getSelectedVariables().contains(variable);
    }

    public final List<String> getSelectedVariableNames() {
        List<String> selectedVariableNames = new LinkedList<>();

        for (Node variable : selectedVariables) {
            selectedVariableNames.add(variable.getName());
        }

        return selectedVariableNames;
    }

    /**
     * Prints out the matrix
     */
    public final String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();

        // Build the variable names
        buf.append(getVariableNames().stream().collect(Collectors.joining("\t")));

        int numVars = getVariableNames().size();
        buf.append("\n");

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                buf.append(nf.format(getValue(i, j)));

                // Don't add ending tab to each data line, data reader will validate this extra tab as missing value
                // This is the fix to issue #525: https://github.com/cmu-phil/tetrad/issues/525
                if (i < j) {
                    buf.append("\t");
                }
            }
            buf.append("\n");
        }

//        buf.append("\nCovariance matrix:");
//        buf.append("\n\tVariables = ").append(getVariable());
//        buf.append("\n\tSample size = ").append(getSampleSize());
//        buf.append("\n");
//        buf.append(MatrixUtils.toString(matrixC.toArray()));
//
//        if (getKnowledge() != null && !getKnowledge().isEmpty()) {
//            buf.append(getKnowledge());
//        }
        return buf.toString();
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    @Override
    public boolean isMixed() {
        return false;
    }

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) {
            throw new IllegalArgumentException("Wrong # of variables.");
        }
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name "
                        + "as the variable being substituted for it.");
            }
            this.variables = variables;
        }
    }

    @Override
    public TetradMatrix getSelection(int[] rows, int[] cols) {
        return matrix.getSelection(rows, cols);
    }

    //========================PRIVATE METHODS============================//
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    @Override
    public DataModel copy() {
        return null;
    }

    @Override
    public void setValue(int i, int j, double v) {
        if (i == j) {
            matrix.set(i, j, v);
        } else {
            matrix.set(i, j, v);
            matrix.set(j, i, v);
        }
    }

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
        for (Node variable : variables) {
            if (variable == null) {
                throw new NullPointerException();
            }
        }

        if (sampleSize < 1) {
            throw new IllegalArgumentException(
                    "Sample size must be at least 1.");
        }

//        for (int i = 0; i < matrix.rows(); i++) {
//            for (int j = 0; j < matrix.columns(); j++) {
//                if (Double.isNaN(matrix.get(i, j))) {
//                    throw new IllegalArgumentException("Please remove or impute missing values.");
//                }
//            }
//        }
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getVariables() == null) {
            throw new NullPointerException();
        }

//        if (matrixC != null) {
//            matrix = new TetradMatrix(matrixC.toArray());
//            matrixC = null;
//        }
        if (knowledge == null) {
            throw new NullPointerException();
        }

        if (sampleSize < -1) {
            throw new IllegalStateException();
        }

        if (selectedVariables == null) {
            selectedVariables = new HashSet<>();
        }
    }
}
