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

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

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
 * @see CorrelationMatrix
 */
public class CovarianceMatrixOnTheFly implements ICovarianceMatrix {
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
     * @serial Do not remove this field; it is needed for serialization.
     */
    private DoubleMatrix2D matrixC;

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

    private double[] variances;


    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a new covariance matrix from the given data set.
     *
     * @throws IllegalArgumentException if this is not a continuous data set.
     */
    public CovarianceMatrixOnTheFly(DataSet dataSet) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Not a continuous data set.");
        }

        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();

        if (dataSet instanceof BoxDataSet) {

            DataBox box = ((BoxDataSet) dataSet).getDataBox().copy();

            if (box instanceof VerticalDoubleDataBox) {
                if (!dataSet.getVariables().equals(variables)) throw new IllegalArgumentException();

                vectors = ((VerticalDoubleDataBox) box).getVariableVectors();

//                final TetradMatrix doubleData = dataSet.getDoubleData();
                TetradVector means = DataUtils.means(vectors);
                DataUtils.demean(vectors, means);

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

//            DataUtils.remean(doubleData, means);
        }

        this.variances = new double[variables.size()];

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

                        double[] v1 = vectors[i];

                        for (int k = 0; k < sampleSize; ++k) {
                            d += v1[k] * v1[k];
                        }

                        double v = d;
                        v /= (sampleSize - 1);

                        variances[i] = v;

                        if (v == 0) System.out.println("Zero variance! " + variables.get(i));
                    }

                    return true;
                } else {
                    final int numIntervals = 4;

                    int step = (to - from) / numIntervals + 1;

                    List<VarianceTask> tasks = new ArrayList<>();

                    for (int i = 0; i < numIntervals; i++) {
                        tasks.add(new VarianceTask(chunk, from + i * step, Math.min(from + (i + 1) * step, to)));
                    }

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        int NTHREADS = Runtime.getRuntime().availableProcessors() * 10;
        int _chunk = variables.size() / NTHREADS + 1;
        int minChunk = 100;
        final int chunk = _chunk < minChunk ? minChunk : _chunk;

        ForkJoinPoolInstance.getInstance().getPool().invoke(new VarianceTask(chunk, 0, variables.size()));

//        System.out.println("Done with variances.");


    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ICovarianceMatrix serializableInstance() {
        List<Node> variables = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        variables.add(x);
        TetradMatrix matrix = TetradAlgebra.identity(1);
        return new CovarianceMatrix(variables, matrix, 100); //
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
     * @return a submatrix of the covariance matrix with variables in the
     * given order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        throw new UnsupportedOperationException();
    }

    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a submatrix of this matrix, with variables in the given
     * order.
     */
    public final CovarianceMatrixOnTheFly getSubmatrix(String[] submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the value of element (i,j) in the matrix
     */
    public final double getValue(int i, int j) {
        if (i == j) {
            return variances[i];
        }

        double d = 0.0D;

        double[] v1 = vectors[i];
        double[] v2 = vectors[j];

        for (int k = 0; k < sampleSize; ++k) {
            d += v1[k] * v2[k];
        }

        double v = d;
        v /= (sampleSize - 1);
        return v;
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
     * @return a copy of the covariance matrix.
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


//        buf.append("\nCovariance matrix:");
//        buf.append("\n\tVariables = ").append(getVariables());
//        buf.append("\n\tSample size = ").append(getSampleSize());
//        buf.append("\n");
//        buf.append(MatrixUtils.toString(matrixC.toArray()));
//
//        if (getKnowledge() != null && !getKnowledge().isEmpty()) {
//            buf.append(getKnowledge());
//        }

        return buf.toString();
    }

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = variables;
    }

    @Override
    public TetradMatrix getSelection(int[] rows, int[] cols) {
        TetradMatrix m = new TetradMatrix(rows.length, cols.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                m.set(i, j, getValue(rows[i], cols[j]));
            }
        }

        return m;
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
    public void setValue(int i, int j, double v) {
        throw new IllegalArgumentException();
//        if (i == j) {
//            matrix.set(i, j, v);
//        } else {
//            matrix.set(i, j, v);
//            matrix.set(j, i, v);
//        }
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
        int numVars = variables.size();

        for (Node variable : variables) {
            if (variable == null) {
                throw new NullPointerException();
            }

//            if (!(variables.get(i) instanceof ContinuousVariable)) {
//                throw new IllegalArgumentException();
//            }
        }

        if (sampleSize < 1) {
            throw new IllegalArgumentException(
                    "Sample size must be at least 1.");
        }

        if (numVars != matrix.rows() || numVars != matrix.columns()) {
            throw new IllegalArgumentException("Number of variables does not " +
                    "equal the dimension of the matrix.");
        }
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

        if (matrixC != null) {
            matrix = new TetradMatrix(matrixC.toArray());
            matrixC = null;
        }

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





