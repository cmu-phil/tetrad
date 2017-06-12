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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.util.Vector;

/**
 * Useful references: "Factor Analysis of Data Matrices" - Paul Horst (1965) This work has good specifications and
 * explanations of factor analysis algorithm and methods of communality estimation.
 * <p>
 * "Applied Factor Analysis" - R.J. Rummel (1970) This book is a good companion to the book listed above.  While it
 * doesn't specify any actual algorithm, it has a great introduction to the subject that gives the reader a good
 * appreciation of the philosophy and the mathematics behind factor analysis.
 *
 * @author Mike Freenor
 */
public class FactorAnalysis {
    private CorrelationMatrix correlationMatrix;

    // method-specific fields that get used
    private Vector<Double> dValues;
    private Vector<TetradMatrix> factorLoadingVectors;
    private double convergenceThreshold;

    public FactorAnalysis(ICovarianceMatrix covarianceMatrix) {
        this.correlationMatrix = new CorrelationMatrix(covarianceMatrix);
    }

    public FactorAnalysis(DataSet dataSet) {
        this.correlationMatrix = new CorrelationMatrix(dataSet);
    }

    //================= COMMUNALITY ESTIMATES =================//

    /**
     * Successive method with residual matrix.
     * <p>
     * This algorithm makes use of a helper algorithm.  Together they solve for an unrotated
     * factor loading matrix.
     * <p>
     * This method calls upon its helper to find column vectors, with which it constructs its
     * factor loading matrix.  Upon receiving each successive column vector from its helper
     * method, it makes sure that we want to keep this vector instead of discarding it.  After
     * keeping a vector, a residual matrix is calculated, upon which solving for the next column
     * vector is directly dependent.
     * <p>
     * We stop looking for new vectors either when we've accounted for close to all of the variance in
     * the original correlation matrix, or when the "d scalar" for a new vector is less than 1 (the
     * d-scalar is the corresponding diagonal for the factor loading matrix -- thus, when it's less
     * than 1, the vector we've solved for barely accounts for any more variance).  This means we've
     * already "pulled out" all of the variance we can from the residual matrix, and we should stop
     * as further factors don't explain much more (and serve to complicate the model).
     * <p>
     * PSEUDO-CODE:
     * <p>
     * 0th Residual Matrix = Original Correlation Matrix
     * Ask helper for the 1st factor (first column vector in our factor loading vector)
     * Add 1st factor's d-scalar (for i'th factor, call its d-scalar the i'th d-scalar) to a list of d-scalars.
     * <p>
     * While the ratio of the sum of d-scalars to the trace of the original correlation matrix is less than .99
     * (in other words, while we haven't accounted for practically all of the variance):
     * <p>
     * i'th residual matrix = (i - 1)'th residual matrix SUBTRACT the major product moment of (i - 1)'th factor loading vector
     * Ask helper for i'th factor
     * If i'th factor's d-value is less than 1, throw it out and end loop.
     * Otherwise, add it to the factor loading matrix and continue loop.
     * <p>
     * END PSEUDO-CODE
     * <p>
     * At the end of the method, the list of column vectors is actually assembled into a TetradMatrix.
     */
    public TetradMatrix successiveResidual() {
        this.factorLoadingVectors = new Vector<>();
        Vector<TetradMatrix> residualMatrices = new Vector<>();
        this.dValues = new Vector<>();

        residualMatrices.add(correlationMatrix.getMatrix());

        TetradMatrix unitVector = new TetradMatrix(correlationMatrix.getMatrix().rows(), 1);
        for (int i = 0; i < unitVector.rows(); i++) {
            unitVector.set(i, 0, 1);
        }

        //find the first factor loading vector
        if (successiveResidualHelper(residualMatrices.lastElement(), unitVector)) {
            int failSafe = 0;

            while (vectorSum(dValues) / correlationMatrix.getMatrix().trace() < .99) {

                //calculate new residual matrix
                TetradMatrix residual = residualMatrices.lastElement().minus(factorLoadingVectors.lastElement().times(factorLoadingVectors.lastElement().transpose()));
                residualMatrices.add(residual);
                if (!successiveResidualHelper(residualMatrices.lastElement(), unitVector)) break;

                failSafe++;
                if (failSafe > 500) break;
            }
        }

        TetradMatrix result = new TetradMatrix(correlationMatrix.getMatrix().rows(), factorLoadingVectors.size());

        for (int i = 0; i < result.rows(); i++) {
            for (int j = 0; j < result.columns(); j++) {
                result.set(i, j, factorLoadingVectors.get(j).get(i, 0));
            }
        }

        return result;
    }

    public TetradMatrix successiveFactorVarimax(TetradMatrix factorLoadingMatrix) {
        if (factorLoadingMatrix.columns() == 1)
            return factorLoadingMatrix;

        Vector<TetradMatrix> residuals = new Vector<>();
        Vector<TetradMatrix> rotatedFactorVectors = new Vector<>();

        TetradMatrix normalizedFactorLoadings = normalizeRows(factorLoadingMatrix);
        residuals.add(normalizedFactorLoadings);

        TetradMatrix unitColumn = new TetradMatrix(factorLoadingMatrix.rows(), 1);

        for (int i = 0; i < factorLoadingMatrix.rows(); i++) {
            unitColumn.set(i, 0, 1);
        }

        TetradMatrix sumCols = residuals.lastElement().transpose().times(unitColumn);
        TetradMatrix wVector = sumCols.scalarMult(1.0 / Math.sqrt(unitColumn.transpose().times(residuals.lastElement()).times(sumCols).get(0, 0)));
        TetradMatrix vVector = residuals.lastElement().times(wVector);

        for (int k = 0; k < normalizedFactorLoadings.columns(); k++) {

            //time to find the minimum value in the v vector
            int lIndex = 0;
            double minValue = Double.POSITIVE_INFINITY;

            for (int i = 0; i < vVector.rows(); i++) {
                if (vVector.get(i, 0) < minValue) {
                    minValue = vVector.get(i, 0);
                    lIndex = i;
                }
            }

            Vector<TetradMatrix> hVectors = new Vector<>();
            Vector<TetradMatrix> bVectors = new Vector<>();
            Vector<Double> alphas = new Vector<>();

            hVectors.add(new TetradMatrix(residuals.lastElement().columns(), 1));
            TetradVector rowFromFactorLoading = residuals.lastElement().getRow(lIndex);

            for (int j = 0; j < hVectors.lastElement().rows(); j++) {
                hVectors.lastElement().set(j, 0, rowFromFactorLoading.get(j));
            }

            boolean firstRun = true;

            for (int i = 0; i < 200; i++) {
                TetradMatrix bVector = residuals.lastElement().times(hVectors.get(i));
                double averageSumSquaresBVector = unitColumn.transpose().times(matrixExp(bVector, 2))
                        .scalarMult(1.0 / (double) bVector.rows()).get(0, 0);

                TetradMatrix betaVector = matrixExp(bVector, 3).minus(bVector.scalarMult(averageSumSquaresBVector));
                TetradMatrix uVector = residuals.lastElement().transpose().times(betaVector);

                alphas.add(Math.sqrt(uVector.transpose().times(uVector).get(0, 0)));
                bVectors.add(bVector);

                hVectors.add(uVector.scalarMult(1.0 / alphas.lastElement()));

//                if (!firstRun && Math.abs((alphas.lastElement() / alphas.get(alphas.size() - 2)) - 1) < .0001) {
//                    break;
//                }

                if (!firstRun && Math.abs((alphas.lastElement() - alphas.get(alphas.size() - 2))) < convergenceThreshold) {
                    break;
                }

                firstRun = false;
            }

            rotatedFactorVectors.add(bVectors.lastElement());
            residuals.add((residuals.lastElement()).minus(bVectors.lastElement().times(hVectors.lastElement().transpose())));
            alphas.add(alphas.lastElement());
        }

        TetradMatrix result = factorLoadingMatrix.like();

        if (!rotatedFactorVectors.isEmpty()) {
            for (int i = 0; i < rotatedFactorVectors.get(0).rows(); i++) {
                for (int j = 0; j < rotatedFactorVectors.size(); j++) {
                    result.set(i, j, rotatedFactorVectors.get(j).get(i, 0));
                }
            }
        }

        return result;
    }

    // ------------------Private methods-------------------//

    /**
     * Helper method for the basic structure successive factor method above.
     * Takes a residual matrix and a approximation vector, and finds both
     * the factor loading vector and the "d scalar" which is used to determine
     * the amount of total variance accounted for so far.
     * <p>
     * The helper takes, to begin with, the unit vector as its approximation to the
     * factor column vector.  With each iteration, it approximates a bit closer --
     * the d-scalar for each successive step eventually converges to a value (provably).
     * <p>
     * Thus, the ratio between the last iteration's d-scalar and this iteration's d-scalar
     * should approach 1.  When this ratio gets sufficiently close to 1, the algorithm halts
     * and returns its getModel approximation.
     * <p>
     * Important to note: the residual matrix stays fixed for this entire algorithm.
     * <p>
     * PSEUDO-CODE:
     * <p>
     * Calculate the 0'th d-scalar, which is done with the following few calculations:
     * 0'th U Vector = residual matrix * approximation vector (this is just the unit vector for the 0'th)
     * 0'th L Scalar = transpose(approximation vector) * U Vector
     * 0'th d-scalar = square root(L Scalar)
     * 0'th approximation to factor loading (A Vector) = 0'th U Vector / 0'th d-scalar
     * <p>
     * <p>
     * While the ratio of the new d-scalar to the old is not sufficiently close to 1
     * (or if we haven't approximated 100 times yet, a failsafe):
     * <p>
     * i'th U Vector = residual matrix * (i - 1)'th factor loading
     * i'th L Scalar = transpose((i - 1)'th factor loading) * i'th U Vector
     * i'th D Scalar = square root(i'th L Scalar)
     * i'th factor loading = i'th U Vector / i'th D Scalar
     * <p>
     * Return the final i'th factor loading as our best approximation.
     */
    private boolean successiveResidualHelper(TetradMatrix residual, TetradMatrix approximationVector) {
        TetradMatrix uVector = residual.times(approximationVector);
        TetradMatrix lVector = approximationVector.transpose().times(uVector);
        double dScalar = Math.sqrt(lVector.get(0, 0));
        TetradMatrix aVector = uVector.scalarMult(1.0 / dScalar);

        Vector<TetradMatrix> factorLoadings = new Vector<>();
//        Vector<TetradMatrix> uVectors = new Vector<>();
        Vector<Double> dScalars = new Vector<>();

        factorLoadings.add(aVector);
//        uVectors.add(uVector);
        dScalars.add(dScalar);

        for (int i = 0; i < 100; i++) {
            TetradMatrix oldFactorLoading = factorLoadings.lastElement();
            TetradMatrix newUVector = residual.times(factorLoadings.lastElement());
//            uVectors.add(newUVector);
            TetradMatrix newLScalar = oldFactorLoading.transpose().times(newUVector);
            double newDScalar = Math.sqrt(newLScalar.get(0, 0));

            dScalars.add(newDScalar);
            TetradMatrix newFactorLoading = newUVector.scalarMult(1.0 / newDScalar);
            factorLoadings.add(newFactorLoading);

            System.out.println("new d scalar = " + newDScalar);

//            if (Math.abs((newDScalar / dScalars.get(dScalars.size() - 2)) - 1) < 1e-7) {
//                break;
//            }

            if (Math.abs((newDScalar - dScalars.get(dScalars.size() - 2))) < convergenceThreshold) {
                break;
            }
        }

        Double d = dScalars.lastElement();

        if (d < .999) return false;

        this.dValues.add(d);
        this.factorLoadingVectors.add(factorLoadings.lastElement());
        return true;
    }


    private static double vectorSum(Vector<Double> vector) {
        double sum = 0;
        for (double a : vector) sum += a;
        return sum;
    }

    //designed for normalizing a vector.
    //as usual, vectors are treated as matrices to simplify operations elsewhere
    private static TetradMatrix normalizeRows(TetradMatrix matrix) {
        Vector<TetradMatrix> normalizedRows = new Vector<>();
        for (int i = 0; i < matrix.rows(); i++) {
            TetradVector vector = matrix.getRow(i);
            TetradMatrix colVector = new TetradMatrix(matrix.columns(), 1);
            for (int j = 0; j < matrix.columns(); j++)
                colVector.set(j, 0, vector.get(j));

            normalizedRows.add(normalizeVector(colVector));
        }

        TetradMatrix result = new TetradMatrix(matrix.rows(), matrix.columns());
        for (int i = 0; i < matrix.rows(); i++) {
            TetradMatrix normalizedRow = normalizedRows.get(i);
            for (int j = 0; j < matrix.columns(); j++) {
                result.set(i, j, normalizedRow.get(j, 0));
            }
        }

        return result;
    }

    private static TetradMatrix normalizeVector(TetradMatrix vector) {
        double scalar = Math.sqrt(vector.transpose().times(vector).get(0, 0));
        return vector.scalarMult(1.0 / scalar);
    }

    private static TetradMatrix matrixExp(TetradMatrix matrix, double exponent) {
        TetradMatrix result = new TetradMatrix(matrix.rows(), matrix.columns());
        for (int i = 0; i < matrix.rows(); i++) {
            for (int j = 0; j < matrix.columns(); j++) {
                result.set(i, j, Math.pow(matrix.get(i, j), exponent));
            }
        }
        return result;
    }

    public void setConvergenceThreshold(double convergenceThreshold) {
        this.convergenceThreshold = convergenceThreshold;
    }
}



