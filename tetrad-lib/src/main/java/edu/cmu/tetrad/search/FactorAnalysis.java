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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.util.LinkedList;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * Implements the classical Factor Analysis algorithm. Some references include: Horst, P. (1965). Factor analysis of
 * data matrices. Holt, Rinehart and Winston. This work has good specifications and explanations of factor analysis
 * algorithm and methods of communality estimation.
 * <p>
 * Rummel, R. J. (1988). Applied factor analysis. Northwestern University Press. This book is a good companion to the
 * book listed above.  While it doesn't specify any actual algorithm, it has a great introduction to the subject that
 * gives the reader a good appreciation of the philosophy and the mathematics behind factor analysis.
 * <p>
 * This class is not configured to respect knowledge of forbidden and required edges.
 *
 * @author Mike Freenor
 * @version $Id: $Id
 */
public class FactorAnalysis {
    /**
     * the covariance matrix
     */
    private final CovarianceMatrix covariance;
    /**
     * method-specific fields that get used
     */
    private LinkedList<Matrix> factorLoadingVectors;
    /**
     * the threshold for the algorithm
     */
    private double threshold = 0.001;
    /**
     * the number of factors to find
     */
    private int numFactors = 2;
    /**
     * the residual matrix
     */
    private Matrix residual;

    /**
     * Constructor.
     *
     * @param covarianceMatrix The covariance matrix being analyzed.
     */
    public FactorAnalysis(ICovarianceMatrix covarianceMatrix) {
        this.covariance = new CovarianceMatrix(covarianceMatrix);
    }

    /**
     * Constructor.
     *
     * @param dataSet The continuous dataset being analyzed.
     */
    public FactorAnalysis(DataSet dataSet) {
        this.covariance = new CovarianceMatrix(dataSet);
    }

    /**
     * designed for normalizing a vector. as usual, vectors are treated as matrices to simplify operations elsewhere
     */
    private static Matrix normalizeRows(Matrix matrix) {
        LinkedList<Matrix> normalizedRows = new LinkedList<>();
        for (int i = 0; i < matrix.getNumRows(); i++) {
            Vector vector = matrix.getRow(i);
            Matrix colVector = new Matrix(matrix.getNumColumns(), 1);
            for (int j = 0; j < matrix.getNumColumns(); j++)
                colVector.set(j, 0, vector.get(j));

            normalizedRows.add(FactorAnalysis.normalizeVector(colVector));
        }

        Matrix result = new Matrix(matrix.getNumRows(), matrix.getNumColumns());
        for (int i = 0; i < matrix.getNumRows(); i++) {
            Matrix normalizedRow = normalizedRows.get(i);
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                result.set(i, j, normalizedRow.get(j, 0));
            }
        }

        return result;
    }

    /**
     * Normalizes a vector.
     *
     * @param vector The vector to be normalized.
     * @return The normalized vector.
     */
    private static Matrix normalizeVector(Matrix vector) {
        double scalar = FastMath.sqrt(vector.transpose().times(vector).get(0, 0));
        return vector.scalarMult(1.0 / scalar);
    }

    /**
     * Calculates the matrix exponentiation of a given matrix with the specified exponent.
     *
     * @param matrix The matrix to be exponentiated.
     * @param exponent The exponent to raise the matrix to.
     * @return The result of the matrix exponentiation.
     */
    private static Matrix matrixExp(Matrix matrix, double exponent) {
        Matrix result = new Matrix(matrix.getNumRows(), matrix.getNumColumns());
        for (int i = 0; i < matrix.getNumRows(); i++) {
            for (int j = 0; j < matrix.getNumColumns(); j++) {
                result.set(i, j, FastMath.pow(matrix.get(i, j), exponent));
            }
        }
        return result;
    }

    /**
     * Successive method with residual matrix.
     * <p>
     * This algorithm makes use of a helper algorithm. Together, they solve for an unrotated factor loading matrix.
     * <p>
     * This method calls upon its helper to find column vectors, with which it constructs its factor loading matrix.
     * Upon receiving each successive column vector from its helper method, it makes sure that we want to keep this
     * vector instead of discarding it. After keeping a vector, a residual matrix is calculated, upon which solving for
     * the next column vector is directly dependent.
     * <p>
     * We stop looking for new vectors either when we've accounted for close to all the variance in the original
     * correlation matrix, or when the "d scalar" for a new vector is less than 1 (the d-scalar is the corresponding
     * diagonal for the factor loading matrix -- thus, when it's less than 1, the vector we've solved for barely
     * accounts for any more variance). This means we've already "pulled out" all the variance we can from the residual
     * matrix, and we should stop as further factors don't explain much more (and serve to complicate the model).
     * <p>
     * PSEUDO-CODE:
     * <p>
     * 0th Residual Matrix = Original Correlation Matrix Ask helper for the 1st factor (first column vector in our
     * factor loading vector) Add 1st factor's d-scalar (for i'th factor, call its d-scalar the i'th d-scalar) to a list
     * of d-scalars.
     * <p>
     * While the ratio of the sum of d-scalars to the trace of the original correlation matrix is less than .99 (in
     * other words, while we haven't accounted for practically all the variance):
     * <p>
     * i'th residual matrix = (i - 1)'th residual matrix SUBTRACT the major product moment of (i - 1)'th factor loading
     * vector Ask helper for i'th factor If i'th factor's d-value is less than 1, throw it out and end loop. Otherwise,
     * add it to the factor loading matrix and continue loop.
     * <p>
     * END PSEUDO-CODE
     * <p>
     * At the end of the method, the list of column vectors is actually assembled into a TetradMatrix.
     *
     * @return The matrix of residuals.
     */
    public Matrix successiveResidual() {
        this.factorLoadingVectors = new LinkedList<>();

        Matrix residual = this.covariance.getMatrix().copy();
        Matrix unitVector = new Matrix(residual.getNumRows(), 1);

        for (int i = 0; i < unitVector.getNumRows(); i++) {
            unitVector.set(i, 0, 1);
        }

        for (int i = 0; i < this.numFactors; i++) {
            boolean found = successiveResidualHelper(residual, unitVector);

            if (!found) break;

            Matrix f = this.factorLoadingVectors.getLast();
            residual = residual.minus(f.times(f.transpose()));
        }

        this.factorLoadingVectors.removeFirst();

        Matrix result = new Matrix(residual.getNumRows(), this.factorLoadingVectors.size());

        for (int i = 0; i < result.getNumRows(); i++) {
            for (int j = 0; j < result.getNumColumns(); j++) {
                result.set(i, j, this.factorLoadingVectors.get(j).get(i, 0));
            }
        }

        this.residual = residual;

        return result;
    }

    /**
     * Returns the matrix result for the varimax algorithm.
     *
     * @param factorLoadingMatrix The matrix of factor loadings.
     * @return The result matrix.
     */
    public Matrix successiveFactorVarimax(Matrix factorLoadingMatrix) {
        if (factorLoadingMatrix.getNumColumns() == 1)
            return factorLoadingMatrix;

        LinkedList<Matrix> residuals = new LinkedList<>();
        LinkedList<Matrix> rotatedFactorVectors = new LinkedList<>();

        Matrix normalizedFactorLoadings = FactorAnalysis.normalizeRows(factorLoadingMatrix);
        residuals.add(normalizedFactorLoadings);

        Matrix unitColumn = new Matrix(factorLoadingMatrix.getNumRows(), 1);

        for (int i = 0; i < factorLoadingMatrix.getNumRows(); i++) {
            unitColumn.set(i, 0, 1);
        }

        Matrix r = residuals.getLast();

        Matrix sumCols = r.transpose().times(unitColumn);
        Matrix wVector = sumCols.scalarMult(1.0 / FastMath.sqrt(unitColumn.transpose().times(r).times(sumCols).get(0, 0)));
        Matrix vVector = r.times(wVector);

        for (int k = 0; k < normalizedFactorLoadings.getNumColumns(); k++) {

            //time to find the minimum value in the v vector
            int lIndex = 0;
            double minValue = Double.POSITIVE_INFINITY;

            for (int i = 0; i < vVector.getNumRows(); i++) {
                if (vVector.get(i, 0) < minValue) {
                    minValue = vVector.get(i, 0);
                    lIndex = i;
                }
            }

            LinkedList<Matrix> hVectors = new LinkedList<>();
            LinkedList<Matrix> bVectors = new LinkedList<>();
            double alpha1 = Double.NaN;

            r = residuals.getLast();

            hVectors.add(new Matrix(r.getNumColumns(), 1));
            Vector rowFromFactorLoading = r.getRow(lIndex);

            for (int j = 0; j < hVectors.getLast().getNumRows(); j++) {
                hVectors.getLast().set(j, 0, rowFromFactorLoading.get(j));
            }

            for (int i = 0; i < 200; i++) {
                Matrix bVector = r.times(hVectors.get(i));
                double averageSumSquaresBVector = unitColumn.transpose().times(FactorAnalysis.matrixExp(bVector, 2))
                        .scalarMult(1.0 / (double) bVector.getNumRows()).get(0, 0);

                Matrix betaVector = FactorAnalysis.matrixExp(bVector, 3).minus(bVector.scalarMult(averageSumSquaresBVector));
                Matrix uVector = r.transpose().times(betaVector);

                double alpha2 = (FastMath.sqrt(uVector.transpose().times(uVector).get(0, 0)));
                bVectors.add(bVector);

                hVectors.add(uVector.scalarMult(1.0 / alpha2));

                if (!Double.isNaN(alpha1)) {
                    if (abs((alpha2 - alpha1)) < this.threshold) {
                        break;
                    }
                }

                alpha1 = alpha2;
            }

            Matrix b = bVectors.getLast();

            rotatedFactorVectors.add(b);
            residuals.add(r.minus(b.times(hVectors.getLast().transpose())));
        }

        Matrix result = factorLoadingMatrix.like();

        if (!rotatedFactorVectors.isEmpty()) {
            for (int i = 0; i < rotatedFactorVectors.get(0).getNumRows(); i++) {
                for (int j = 0; j < rotatedFactorVectors.size(); j++) {
                    result.set(i, j, rotatedFactorVectors.get(j).get(i, 0));
                }
            }
        }

        return result;
    }

    /**
     * Sets the threshold.
     *
     * @param threshold This threshold.
     */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /**
     * Sets the number of factors to find.
     *
     * @param numFactors This number.
     */
    public void setNumFactors(int numFactors) {
        this.numFactors = numFactors;
    }

    /**
     * Returns the matrix of residuals.
     *
     * @return This matrix.
     */
    public Matrix getResidual() {
        return this.residual;
    }

    /**
     * Helper method for the basic structure successive factor method above. Takes a residual matrix and an
     * approximation vector, and finds both the factor loading vector and the "d scalar" which is used to determine the
     * amount of total variance accounted for so far.
     * <p>
     * The helper takes, to begin with, the unit vector as its approximation to the factor column vector. With each
     * iteration, it approximates a bit closer -- the d-scalar for each successive step eventually converges to a value
     * (provably).
     * <p>
     * Thus, the ratio between the last iteration's d-scalar and this iteration's d-scalar should approach 1. When this
     * ratio gets sufficiently close to 1, the algorithm halts and returns its getModel approximation.
     * <p>
     * Important to note: the residual matrix stays fixed for this entire algorithm.
     * <p>
     * PSEUDO-CODE:
     * <p>
     * Calculate the 0'th d-scalar, which is done with the following few calculations: 0'th U Vector = residual matrix *
     * approximation vector (this is just the unit vector for the 0'th) 0'th L Scalar = transpose(approximation vector)
     * * U Vector 0'th d-scalar = square root(L Scalar) 0'th approximation to factor loading (A Vector) = 0'th U Vector
     * / 0'th d-scalar
     * <p>
     * <p>
     * While the ratio of the new d-scalar to the old is not sufficiently close to 1 (or if we haven't approximated 100
     * times yet, a failsafe):
     * <p>
     * i'th U Vector = residual matrix * (i - 1)'th factor loading i'th L Scalar = transpose((i - 1)'th factor loading)
     * * i'th U Vector i'th D Scalar = square root(i'th L Scalar) i'th factor loading = i'th U Vector / i'th D Scalar
     * <p>
     * Return the final i'th factor loading as our best approximation.
     */
    private boolean successiveResidualHelper(Matrix residual, Matrix approximationVector) {
        Matrix l0 = approximationVector.transpose().times(residual).times(approximationVector);

        if (l0.get(0, 0) < 0) {
            return false;
        }

        double d = FastMath.sqrt(l0.get(0, 0));
        Matrix f = residual.times(approximationVector).scalarMult(1.0 / d);

        for (int i = 0; i < 100; i++) {
            Matrix ui = residual.times(f);
            Matrix li = f.transpose().times(ui);
            double di = FastMath.sqrt(li.get(0, 0));

            if (abs((d - di)) <= this.threshold) {
                break;
            }

            d = di;
            f = ui.scalarMult(1.0 / d);
        }

        this.factorLoadingVectors.add(f);
        return true;
    }
}



