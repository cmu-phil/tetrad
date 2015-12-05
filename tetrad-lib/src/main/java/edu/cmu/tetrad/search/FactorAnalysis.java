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
 * explanations of factor analysis algorithms and methods of communality estimation.
 * <p>
 * "Applied Factor Analysis" - R.J. Rummel (1970) This book is a good companion to the book listed above.  While it
 * doesn't specify any actual algorithms, it has a great introduction to the subject that gives the reader a good
 * appreciation of the philosophy and the mathematics behind factor analysis.
 *
 * @author Mike Freenor
 */
public class FactorAnalysis {
    public ICovarianceMatrix covarianceMatrix;
    public CorrelationMatrix correlationMatrix;

    // method-specific fields that get used
    public Vector<Double> dValues;
    public Vector<TetradMatrix> factorLoadingVectors;
    public Vector<TetradMatrix> residualMatrices;


    public FactorAnalysis(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
        this.correlationMatrix = new CorrelationMatrix(covarianceMatrix);
    }

    public FactorAnalysis(DataSet dataSet) {
        this.covarianceMatrix = new CovarianceMatrix(dataSet);
        this.correlationMatrix = new CorrelationMatrix(dataSet);
    }

    //================= COMMUNALITY ESTIMATES =================//

    /*
     *  A GENERAL NOTE ABOUT COMMUNALITY ESTIMATES
     *
     * As of 8/3/09, none of these are used in any implemented algorithms.
     * This is because the successive residual method implemented doesn't require
     * communality estimates.
     */


//    /**
//     * Unity method.
//     *
//     * Takes the residual correlation matrix and sets the diagonals to unity.
//     * This, in effect, estimates the communality as 1.
//     *
//     * This method is better when there are more variables, but should still be
//     * avoided in the cases where the off-diagonal correlations are quite small.
//     *
//     * One nice property of this method is that the total amount of variance
//     * accounted for is guaranteed to be less than or equal to the original
//     * variance.  This prevents resultant factor loading matrices from accounting
//     * for more variance than the original, which is strange.
//     */
//    public void unity(CorrelationMatrix r) {
//        TetradMatrix residual = r.getMatrix();
//
//        for (int i = 0; i < residual.columns(); i++) {
//            residual.set(i, i, 1);
//        }
//    }

    /**
     * Largest nondiagonal magnitude method.
     *
     * Estimates the communality (diagonal of the residual correlation
     * matrix) as the largest nondiagonal absolute value present in each column.
     *
     * Tends to produce smaller numbers of factors than the unity method.
     */

    public void largestNonDiagonalMagnitude(CorrelationMatrix r) {
        TetradMatrix residual = r.getMatrix();

        for (int i = 0; i < residual.columns(); i++) {
            double max = 0;
            for (int j = 0; j < residual.columns(); j++) {
                if (i == j) continue;
                double temp = Math.abs(residual.get(j, i));
                if (temp > max) max = temp;
            }
            residual.set(i, i, max);
        }
    }

    //================= FACTORING METHODS =================//

    /*
     * The "loadTestMatrix" method serves to load in the numerical example from
     * Horst's 1965 book (referenced at the top of this java file).
     *
     * In order to use this test data, you must create a dummy project where you
     * simulate data from 9 variables. (This script replaces the correlationMatrix
     * with the correlationMatrix from the numerical example.)
     */

    /*
    public void loadTestMatrix()
    {
        TetradMatrix testMatrix = TetradMatrix.instance(9, 9);
        //set diagonals to test
        for(int i = 0; i < 9; i++)
            testMatrix.set(i, i, 1);

        testMatrix.set(0, 1, .829);
        testMatrix.set(0, 2, .768);
        testMatrix.set(0, 3, .108);
        testMatrix.set(0, 4, .033);
        testMatrix.set(0, 5, .108);
        testMatrix.set(0, 6, .298);
        testMatrix.set(0, 7, .309);
        testMatrix.set(0, 8, .351);

        testMatrix.set(1, 0, .829);
        testMatrix.set(2, 0, .768);
        testMatrix.set(3, 0, .108);
        testMatrix.set(4, 0, .033);
        testMatrix.set(5, 0, .108);
        testMatrix.set(6, 0, .298);
        testMatrix.set(7, 0, .309);
        testMatrix.set(8, 0, .351);

        testMatrix.set(1, 2, .775);
        testMatrix.set(1, 3, .115);
        testMatrix.set(1, 4, .061);
        testMatrix.set(1, 5, .125);
        testMatrix.set(1, 6, .323);
        testMatrix.set(1, 7, .347);
        testMatrix.set(1, 8, .369);

        testMatrix.set(2, 1, .775);
        testMatrix.set(3, 1, .115);
        testMatrix.set(4, 1, .061);
        testMatrix.set(5, 1, .125);
        testMatrix.set(6, 1, .323);
        testMatrix.set(7, 1, .347);
        testMatrix.set(8, 1, .369);

        testMatrix.set(2, 3, .272);
        testMatrix.set(2, 4, .205);
        testMatrix.set(2, 5, .238);
        testMatrix.set(2, 6, .296);
        testMatrix.set(2, 7, .271);
        testMatrix.set(2, 8, .385);

        testMatrix.set(3, 2, .272);
        testMatrix.set(4, 2, .205);
        testMatrix.set(5, 2, .238);
        testMatrix.set(6, 2, .296);
        testMatrix.set(7, 2, .271);
        testMatrix.set(8, 2, .385);

        testMatrix.set(3, 4, .636);
        testMatrix.set(3, 5, .626);
        testMatrix.set(3, 6, .249);
        testMatrix.set(3, 7, .183);
        testMatrix.set(3, 8, .369);

        testMatrix.set(4, 3, .636);
        testMatrix.set(5, 3, .626);
        testMatrix.set(6, 3, .249);
        testMatrix.set(7, 3, .183);
        testMatrix.set(8, 3, .369);

        testMatrix.set(4, 5, .709);
        testMatrix.set(4, 6, .138);
        testMatrix.set(4, 7, .091);
        testMatrix.set(4, 8, .254);

        testMatrix.set(5, 4, .709);
        testMatrix.set(6, 4, .138);
        testMatrix.set(7, 4, .091);
        testMatrix.set(8, 4, .254);

        testMatrix.set(5, 6, .190);
        testMatrix.set(5, 7, .103);
        testMatrix.set(5, 8, .291);

        testMatrix.set(6, 5, .190);
        testMatrix.set(7, 5, .103);
        testMatrix.set(8, 5, .291);

        testMatrix.set(6, 7, .654);
        testMatrix.set(6, 8, .527);

        testMatrix.set(7, 6, .654);
        testMatrix.set(8, 6, .527);

        testMatrix.set(7, 8, .541);
        testMatrix.set(8, 7, .541);

        correlationMatrix.setMatrix(testMatrix);
    }
    */

    /**
     * Successive method with residual matrix.
     *
     * This algorithm makes use of a helper algorithm.  Together they solve for an unrotated
     * factor loading matrix.
     *
     * This method calls upon its helper to find column vectors, with which it constructs its
     * factor loading matrix.  Upon receiving each successive column vector from its helper
     * method, it makes sure that we want to keep this vector instead of discarding it.  After
     * keeping a vector, a residual matrix is calculated, upon which solving for the next column
     * vector is directly dependent.
     *
     * We stop looking for new vectors either when we've accounted for close to all of the variance in
     * the original correlation matrix, or when the "d scalar" for a new vector is less than 1 (the
     * d-scalar is the corresponding diagonal for the factor loading matrix -- thus, when it's less
     * than 1, the vector we've solved for barely accounts for any more variance).  This means we've
     * already "pulled out" all of the variance we can from the residual matrix, and we should stop
     * as further factors don't explain much more (and serve to complicate the model).
     *
     * PSEUDO-CODE:
     *
     * 0th Residual Matrix = Original Correlation Matrix
     * Ask helper for the 1st factor (first column vector in our factor loading vector)
     * Add 1st factor's d-scalar (for i'th factor, call its d-scalar the i'th d-scalar) to a list of d-scalars.
     *
     * While the ratio of the sum of d-scalars to the trace of the original correlation matrix is less than .99
     * (in other words, while we haven't accounted for practically all of the variance):
     *
     *     i'th residual matrix = (i - 1)'th residual matrix SUBTRACT the major product moment of (i - 1)'th factor loading vector      
     *     Ask helper for i'th factor
     *     If i'th factor's d-value is less than 1, throw it out and end loop.
     *     Otherwise, add it to the factor loading matrix and continue loop.
     *
     * END PSEUDO-CODE
     *
     * At the end of the method, the list of column vectors is actually assembled into a TetradMatrix.
     *
     */

    public TetradMatrix successiveResidual() {
        this.factorLoadingVectors = new Vector<TetradMatrix>();
        this.residualMatrices = new Vector<TetradMatrix>();
        this.dValues = new Vector<Double>();

        this.residualMatrices.add(correlationMatrix.getMatrix());

        TetradMatrix unitVector = new TetradMatrix(correlationMatrix.getMatrix().rows(), 1);
        for (int i = 0; i < unitVector.rows(); i++) {
            unitVector.set(i, 0, 1);
        }

        //find the first factor loading vector
        successiveResidualHelper(residualMatrices.lastElement(), unitVector);

        int failSafe = 0;

        while (vectorSum(dValues) / trace(correlationMatrix.getMatrix()) < .99) {
            //calculate new residual matrix
            TetradMatrix residual = matrixSubtract(residualMatrices.lastElement(),
                    matrixMult(factorLoadingVectors.lastElement(),
                            transpose(factorLoadingVectors.lastElement())));
            residualMatrices.add(residual);
            if (!successiveResidualHelper(residualMatrices.lastElement(), unitVector)) break;

            failSafe++;
            if (failSafe > 500) break;
        }

        TetradMatrix result = new TetradMatrix(correlationMatrix.getMatrix().rows(), factorLoadingVectors.size());

        for (int i = 0; i < result.rows(); i++) {
            for (int j = 0; j < result.columns(); j++) {
                result.set(i, j, factorLoadingVectors.get(j).get(i, 0));
            }
        }

        return result;
    }

    /*
     * Helper method for the basic structure successive factor method above.
     * Takes a residual matrix and a approximation vector, and finds both
     * the factor loading vector and the "d scalar" which is used to determine
     * the amount of total variance accounted for so far.
     *
     * The helper takes, to begin with, the unit vector as its approximation to the
     * factor column vector.  With each iteration, it approximates a bit closer --
     * the d-scalar for each successive step eventually converges to a value (provably).
     *
     * Thus, the ratio between the last iteration's d-scalar and this iteration's d-scalar
     * should approach 1.  When this ratio gets sufficiently close to 1, the algorithm halts
     * and returns its getModel approximation.
     *
     * Important to note: the residual matrix stays fixed for this entire algorithm.
     *
     * PSEUDO-CODE:
     *
     * Calculate the 0'th d-scalar, which is done with the following few calculations:
     * 0'th U Vector = residual matrix * approximation vector (this is just the unit vector for the 0'th)
     * 0'th L Scalar = transpose(approximation vector) * U Vector
     * 0'th d-scalar = square root(L Scalar)
     * 0'th approximation to factor loading (A Vector) = 0'th U Vector / 0'th d-scalar
     *
     *
     * While the ratio of the new d-scalar to the old is not sufficiently close to 1
     * (or if we haven't approximated 100 times yet, a failsafe):
     *
     *      i'th U Vector = residual matrix * (i - 1)'th factor loading
     *      i'th L Scalar = transpose((i - 1)'th factor loading) * i'th U Vector
     *      i'th D Scalar = square root(i'th L Scalar)
     *      i'th factor loading = i'th U Vector / i'th D Scalar
     *
     * Return the final i'th factor loading as our best approximation.
     *
     */

    public boolean successiveResidualHelper(TetradMatrix residual, TetradMatrix approximationVector) {
        TetradMatrix uVector = matrixMult(residual, approximationVector);
        TetradMatrix lVector = matrixMult(transpose(approximationVector), uVector);
        double dScalar = Math.sqrt(lVector.get(0, 0));
        TetradMatrix aVector = matrixDiv(dScalar, uVector);

        Vector factorLoadings = new Vector();
        Vector uVectors = new Vector();
        Vector dScalars = new Vector();

        factorLoadings.add(aVector);
        uVectors.add(uVector);
        dScalars.add(dScalar);

        for (int i = 0; i < 100; i++) {
            TetradMatrix oldFactorLoading = (TetradMatrix) factorLoadings.lastElement();
            TetradMatrix newUVector = matrixMult(residual, (TetradMatrix) factorLoadings.lastElement());
            uVectors.add(newUVector);
            TetradMatrix newLScalar = matrixMult(transpose(oldFactorLoading), newUVector);
            double newDScalar = Math.sqrt(newLScalar.get(0, 0));

            dScalars.add(newDScalar);
            TetradMatrix newFactorLoading = matrixDiv(newDScalar, newUVector);
            factorLoadings.add(newFactorLoading);

            if (Math.abs((newDScalar / (Double) dScalars.get(dScalars.size() - 2)) - 1) < .00001) {
                break;
            }
        }

        if ((Double) dScalars.lastElement() < 1) return false;

        this.dValues.add((Double) dScalars.lastElement());
        this.factorLoadingVectors.add((TetradMatrix) factorLoadings.lastElement());
        return true;
    }

    //================= MATRIX FUNCTIONS =================//

    /**
     * @return a vector that runs along the diagonal of the supplied 2D matrix.
     * If the matrix is not a square matrix, then it compiles what WOULD be the
     * diagonal if it were, starting from the upper-left corner.
     */

    public static TetradMatrix diag(TetradMatrix matrix) {
        //System.out.println(matrix);
        TetradMatrix diagonal = new TetradMatrix(matrix.columns(), matrix.columns());
        for (int i = 0; i < matrix.columns(); i++) {
            for (int j = 0; j < matrix.columns(); j++) {
                if (i == j) diagonal.set(j, i, matrix.get(i, i));
                else diagonal.set(j, i, 0);
            }
        }
        //System.out.println(diagonal);
        return diagonal;
    }

    public static TetradMatrix diag(DataSet dataSet) {
        return diag(dataSet.getDoubleData());
    }

    public static TetradMatrix diag(ICovarianceMatrix covMatrix) {
        return diag(covMatrix.getMatrix());
    }

    public static TetradMatrix diag(CorrelationMatrix corMatrix) {
        return diag(corMatrix.getMatrix());
    }

    /**
     * Subtracts b from a.
     */

    public static TetradMatrix matrixSubtract(TetradMatrix a, TetradMatrix b) {
        if (!(a.columns() == b.columns() && a.rows() == b.rows())) return null;

        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());
        for (int i = 0; i < result.rows(); i++) {
            for (int j = 0; j < result.columns(); j++) {
                result.set(i, j, a.get(i, j) - b.get(i, j));
            }
        }
        return result;
    }

    /**
     * Calculates (a * b)
     */

    public static TetradMatrix matrixMult(TetradMatrix a, TetradMatrix b) {
        if (a.columns() != b.rows()) return null;

        TetradMatrix result = new TetradMatrix(a.rows(), b.columns());

        for (int i = 0; i < a.rows(); i++) {
            for (int j = 0; j < b.columns(); j++) {
                double value = 0;
                for (int k = 0; k < b.rows(); k++) {
                    value += a.get(i, k) * b.get(k, j);
                }
                result.set(i, j, value);
            }
        }

        //System.out.println(result);
        return result;
    }

    public static TetradMatrix matrixMult(double scalar, TetradMatrix a) {
        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());

        for (int i = 0; i < a.rows(); i++) {
            for (int j = 0; j < a.columns(); j++) {
                result.set(i, j, scalar * a.get(i, j));
            }
        }

        //System.out.println(result);
        return result;
    }

    public static TetradMatrix matrixAdd(TetradMatrix a, TetradMatrix b) {
        if (!(a.rows() == b.rows() && a.columns() == b.columns())) return null;

        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());

        for (int i = 0; i < a.rows(); i++) {
            for (int j = 0; j < a.columns(); j++) {
                result.set(j, i, a.get(j, i) + b.get(j, i));
            }
        }

        //System.out.println(result);
        return result;
    }

    public static TetradMatrix matrixDiv(double scalar, TetradMatrix a) {
        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());
        //System.out.println("About to divide " + a + " by " + scalar);

        for (int i = 0; i < a.rows(); i++) {
            for (int j = 0; j < a.columns(); j++) {
                result.set(i, j, a.get(i, j) / scalar);
            }
        }

        //System.out.println("Result of division: " + result);

        return result;
    }

    public static TetradMatrix transpose(TetradMatrix a) {
        //System.out.println("About to transpose:");
        //System.out.println(a);

        TetradMatrix result = new TetradMatrix(a.columns(), a.rows());

        for (int i = 0; i < a.columns(); i++) {
            for (int j = 0; j < a.rows(); j++) {
                result.set(i, j, a.get(j, i));
            }
        }

        //System.out.println("Result is:");
        //System.out.println(result);
        return result;
    }

    public static double trace(TetradMatrix a) {
        double result = 0;
        for (int i = 0; i < a.columns(); i++) {
            result += a.get(i, i);
        }
        return result;
    }

    public static double vectorSum(Vector<Double> vector) {
        double sum = 0;
        for (int i = 0; i < vector.size(); i++) sum += vector.get(i);
        return sum;
    }

    //designed for normalizing a vector.
    //as usual, vectors are treated as matrices to simplify operations elsewhere

    public static TetradMatrix normalizeRows(TetradMatrix matrix) {
        Vector<TetradMatrix> normalizedRows = new Vector<TetradMatrix>();
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

    //works on column vectors

    public static TetradMatrix normalizeVector(TetradMatrix vector) {
        double scalar = Math.sqrt(matrixMult(transpose(vector), vector).get(0, 0));
        return matrixDiv(scalar, vector);
    }

    public static TetradMatrix matrixExp(TetradMatrix matrix, double exponent) {
        TetradMatrix result = new TetradMatrix(matrix.rows(), matrix.columns());
        for (int i = 0; i < matrix.rows(); i++) {
            for (int j = 0; j < matrix.columns(); j++) {
                result.set(i, j, Math.pow(matrix.get(i, j), exponent));
            }
        }
        return result;
    }

    public static TetradMatrix successiveFactorVarimax(TetradMatrix factorLoadingMatrix) {
        if (factorLoadingMatrix.columns() == 1)
            return factorLoadingMatrix;

        Vector<TetradMatrix> residuals = new Vector<TetradMatrix>();
        Vector<TetradMatrix> rotatedFactorVectors = new Vector<TetradMatrix>();
        Vector<TetradMatrix> vVectors = new Vector<TetradMatrix>();

        TetradMatrix normalizedFactorLoadings = normalizeRows(factorLoadingMatrix);
        residuals.add(normalizedFactorLoadings);

        TetradMatrix unitColumn = new TetradMatrix(factorLoadingMatrix.rows(), 1);
        for (int i = 0; i < factorLoadingMatrix.rows(); i++) {
            unitColumn.set(i, 0, 1);
        }

        TetradMatrix sumCols = matrixMult(transpose(residuals.lastElement()), unitColumn);
        TetradMatrix wVector = matrixDiv(Math.sqrt(matrixMult(matrixMult(transpose(unitColumn), residuals.lastElement()), sumCols).get(0, 0)),
                sumCols);

        /*
        System.out.println(transpose(unitColumn));
        System.out.println(residuals.lastElement());
        System.out.println("--");
        System.out.println(matrixMult(transpose(unitColumn), residuals.lastElement()));
        System.out.println(sumCols);
        System.out.println("--");
        System.out.println(matrixMult(matrixMult(transpose(unitColumn), residuals.lastElement()), sumCols).get(0, 0));
        System.out.println(Math.sqrt(matrixMult(matrixMult(transpose(unitColumn), residuals.lastElement()), sumCols).get(0, 0)));
        System.out.println(sumCols);

        System.out.println("W Vector:");
        System.out.println(wVector);
        */

        TetradMatrix vVector = matrixMult(residuals.lastElement(), wVector);
        vVectors.add(vVector);

        for (int k = 0; k < normalizedFactorLoadings.columns(); k++) {
            //time to find the minimum value in the v vector
            int lIndex = 0;
            double minValue = 1000000000;

            for (int i = 0; i < vVector.rows(); i++) {
                if (vVector.get(i, 0) < minValue) {
                    minValue = vVector.get(i, 0);
                    lIndex = i;
                }
            }

            Vector<TetradMatrix> hVectors = new Vector<TetradMatrix>();
            Vector<TetradMatrix> bVectors = new Vector<TetradMatrix>();
            Vector<Double> alphas = new Vector<Double>();

            hVectors.add(new TetradMatrix(residuals.lastElement().columns(), 1));
            TetradVector rowFromFactorLoading = residuals.lastElement().getRow(lIndex);

            for (int j = 0; j < hVectors.lastElement().rows(); j++) {
                hVectors.lastElement().set(j, 0, rowFromFactorLoading.get(j));
            }

            boolean firstRun = true;

            for (int i = 0; i < 50; i++) {
                TetradMatrix bVector = matrixMult(residuals.lastElement(), hVectors.get(i));
                double averageSumSquaresBVector = matrixDiv(bVector.rows(), matrixMult(transpose(unitColumn), matrixExp(bVector, 2))).get(0, 0);
                //System.out.println(averageSumSquaresBVector);

                TetradMatrix betaVector = matrixSubtract(matrixExp(bVector, 3), matrixMult(averageSumSquaresBVector, bVector));
                TetradMatrix uVector = matrixMult(transpose(residuals.lastElement()), betaVector);

                /*
                System.out.println("Residual");
                System.out.println(transpose(residuals.lastElement()));

                System.out.println("Beta vector");
                System.out.println(betaVector);
                */

                alphas.add(Math.sqrt(matrixMult(transpose(uVector), uVector).get(0, 0)));
                bVectors.add(bVector);

                hVectors.add(matrixDiv(alphas.lastElement(), uVector));

                //System.out.println(uVector);

                if (!firstRun && Math.abs((alphas.lastElement() / alphas.get(alphas.size() - 2)) - 1) < .0001) {
                    break;
                }
                firstRun = false;
            }

            //System.out.println(bVectors.lastElement());

            rotatedFactorVectors.add(bVectors.lastElement());
            residuals.add(matrixSubtract((residuals.lastElement()), matrixMult(bVectors.lastElement(), transpose(hVectors.lastElement()))));
        }

        TetradMatrix result = new TetradMatrix(factorLoadingMatrix.rows(), factorLoadingMatrix.columns());

        for (int i = 0; i < rotatedFactorVectors.get(0).rows(); i++) {
            for (int j = 0; j < rotatedFactorVectors.size(); j++) {
                result.set(i, j, rotatedFactorVectors.get(j).get(i, 0));
            }
        }

        return result;
    }
}



