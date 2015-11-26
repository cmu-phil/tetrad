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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.Vector;

/**
 * Useful references:
 * "Factor Analysis of Data Matrices" - Paul Horst (1965)
 *         This work has good specifications and explanations
 *          of factor analysis algorithms and methods of
 *          communality estimation.
 *
 * "Applied Factor Analysis" - R.J. Rummel (1970)
 *          This book is a good companion to the book listed
 *          above.  While it doesn't specify any actual
 *          algorithms, it has a great introduction to the
 *          subject that gives the reader a good appreciation
 *          of the philosophy and the mathematics behind
 *          factor analysis.
 *
 * @author Mike Freenor
 */
public class FactorAnalysisJoe {
    public ICovarianceMatrix covarianceMatrix;
    public CorrelationMatrix correlationMatrix;

// method-specific fields that get used
    public Vector<Double> dValues;
    public Vector<TetradMatrix> factorLoadingVectors;
    public Vector<TetradMatrix> residualMatrices;


    public FactorAnalysisJoe(ICovarianceMatrix covarianceMatrix)
    {
        this.covarianceMatrix = covarianceMatrix;
        this.correlationMatrix = new CorrelationMatrix(covarianceMatrix);
    }

    public FactorAnalysisJoe(DataSet dataSet)
    {
        this.covarianceMatrix = new CovarianceMatrix(dataSet);
        this.correlationMatrix = new CorrelationMatrix(dataSet);
    }

    //================= COMMUNALITY ESTIMATES =================//

    /*
     * Unity method.
     *
     * Takes the residual correlation matrix and sets the diagonals to unity.
     * This, in effect, estimates the communality as 1.
     *
     * This method is better when there are more variables, but should still be
     * avoided in the cases where the off-diagonal correlations are quite small.
     *
     * One nice property of this method is that the total amount of variance
     * accounted for is guaranteed to be less than or equal to the original
     * variance.  This prevents resultant factor loading matrices from accounting
     * for more variance than the original, which is strange.
     */
    public void unity(CorrelationMatrix r)
    {
        TetradMatrix residual = r.getMatrix();
        //System.out.println("About to compute unity estimate:");
        //System.out.println(residual.toString());
        for(int i = 0; i < residual.columns(); i++)
        {
            residual.set(i, i, 1);
        }
        //System.out.println(residual.toString());
    }

    /*
     * Largest nondiagonal magnitude method.
     *
     * Estimates the communality (diagonal of the residual correlation
     * matrix) as the largest nondiagonal absolute value present in each column.
     *
     * Tends to produce smaller numbers of factors than the unity method.
     */
    public void largestNonDiagonalMagnitude(CorrelationMatrix r)
    {
        TetradMatrix residual = r.getMatrix();
        //System.out.println("About to compute magnitude estimate:");
        //System.out.println(residual.toString());
        for(int i = 0; i < residual.columns(); i++)
        {
            double max = 0;
            for(int j = 0; j < residual.columns(); j++)
            {
                if(i == j) continue;
                double temp = Math.abs(residual.get(j, i));
                if(temp > max) max = temp;
            }
            residual.set(i, i, max);
        }
        //System.out.println(residual.toString());
    }

    //================= FACTORING METHODS =================//

    public void loadTestMatrix()
    {
        TetradMatrix testMatrix = new TetradMatrix(9, 9);
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

        System.out.println(correlationMatrix);

//        System.out.println("Positive definite: " + MatrixUtils.isPositiveDefinite(correlationMatrix.getMatrix()));
    }

    /*
     * Successive method with residual matrix
     */

    public TetradMatrix successiveResidual()
    {
        loadTestMatrix();

        this.factorLoadingVectors = new Vector<TetradMatrix>();
        this.residualMatrices = new Vector<TetradMatrix>();
        this.dValues = new Vector<Double>();

        this.residualMatrices.add(correlationMatrix.getMatrix());

        TetradMatrix unitVector = new TetradMatrix(9, 1);
        for(int i = 0; i < 9; i++)
        {
            unitVector.set(i, 0, 1);
        }

        //find the first fact                   or loading vector
        successiveResidualHelper(residualMatrices.lastElement(),
                                    unitVector);

        int failSafe = 0;

        while(vectorSum(dValues) / trace(correlationMatrix.getMatrix()) < .99)
        {
            System.out.println("****************  " + this.dValues.lastElement() / trace(correlationMatrix.getMatrix()));
            //calculate new residual matrix
            TetradMatrix prod = matrixMult(factorLoadingVectors.lastElement(),
                    transpose(factorLoadingVectors.lastElement()));
//            System.out.println("Prod = " + prod);

            TetradMatrix residual = matrixSubtract(residualMatrices.lastElement(), prod);
            System.out.println(residual);
            residualMatrices.add(residual);
            successiveResidualHelper(residualMatrices.lastElement(), unitVector);

            failSafe++;
            if(failSafe > 500) break;
        }

        return null;
    }

    /*
     * Helper method for the basic structure successive factor method above.
     * Takes a residual matrix and a approximation vector, and finds both
     * the factor loading vector and the "d value" which is used to determine
     * the amount of total variance accounted for so far.
     */
    public void successiveResidualHelper(TetradMatrix residual, TetradMatrix approximationVector)
    {
        TetradMatrix uVector = matrixMult(residual, approximationVector);
        TetradMatrix lVector = matrixMult(transpose(approximationVector), uVector);
        double dScalar = Math.sqrt(lVector.get(0, 0));
        TetradMatrix aVector = matrixDiv(dScalar, uVector);

        Vector aVectors = new Vector();
        Vector uVectors = new Vector();
        Vector dScalars = new Vector();

        aVectors.add(aVector);
        uVectors.add(uVector);
        dScalars.add(dScalar);



        for(int i = 0; i < 100; i++)
        {
            approximationVector = (TetradMatrix) aVectors.lastElement();
            uVector = matrixMult(residual, approximationVector);
            lVector = matrixMult(transpose(approximationVector), uVector);
            dScalar = Math.sqrt(lVector.get(0, 0));
            aVector = matrixDiv(dScalar, uVector);

            aVectors.add(aVector);
            uVectors.add(uVector);
            dScalars.add(dScalar);

//
//            TetradMatrix oldFactorLoading = (TetradMatrix)factorLoadings.lastElement();
////            TetradMatrix newUVector = matrixMult((TetradMatrix)correlationMatrix.getMatrix(),
////                                                        (TetradMatrix)factorLoadings.lastElement());
//            TetradMatrix newUVector = matrixMult((TetradMatrix)uVectors.lastElement(),
//                                                        (TetradMatrix)factorLoadings.lastElement());
//            uVectors.add(newUVector);
//            TetradMatrix newLScalar = matrixMult(transpose(oldFactorLoading), newUVector);

//            if (lVeco.get(0, 0) < 0) throw new IllegalArgumentException();

//            double newDScalar = Math.sqrt(lVector.get(0, 0));
//            dScalars.add(newDScalar);
//            TetradMatrix _aVector = matrixDiv(newDScalar, uVector);
//            aVectors.add(_aVector);
            System.out.println("New D Scalar: " + dScalar);

            if(Math.sqrt((dScalar / (Double)dScalars.get(dScalars.size() - 2)) - 1) < .00001)
            {
                System.out.println("Stopped on the " + i + "th iteration.");
                break;
            }
        }

        System.out.println("Resultant factor loading matrix: ");
        System.out.println(aVectors.lastElement());

        this.dValues.add((Double)dScalars.lastElement());
        this.factorLoadingVectors.add((TetradMatrix) aVectors.lastElement());
    }

    /*
     * Centroid method with unity.
     */
    /*
    public TetradMatrix centroidUnity()
    {
        loadTestMatrix();

        //calculate residual matrix with 0's in the diagonal
        TetradMatrix residualMatrix = TetradMatrix.instance(correlationMatrix.getMatrix().toArray());
        residualMatrix = matrixSubtract(residualMatrix, diag(residualMatrix));
        System.out.println("0th residual matrix with 0's in the diagonal:");
        System.out.println(residualMatrix);

        TetradMatrix unitVector = TetradMatrix.instance(9, 1);
        for(int i = 0; i < unitVector.rows(); i++)
        {
            unitVector.set(i, 0, 1);
        }

        System.out.println("Here's our unit vector.");
        System.out.println(unitVector);

        System.out.println("Multiplying our residual matrix by the unit vector:");
        TetradMatrix resTimesSignVector = FactorAnalysis.matrixMult(residualMatrix, unitVector);

        System.out.println("Time to find alpha_1:");
        double maxValue = Math.abs(resTimesSignVector.get(0, 0));
        int index = 0;
        for(int i = 1; i < resTimesSignVector.rows(); i++)
        {
            if(Math.abs(resTimesSignVector.get(i, 0)) > maxValue)
            {
                maxValue = Math.abs(resTimesSignVector.get(i, 0));
                index = i;
            }
        }

        System.out.println("Alpha index is " + index);
        unitVector.set(index, 0, unitVector.get(index, 0) * -1);

        System.out.println("Now the sign vector looks like this:");
        System.out.println(unitVector);

        System.out.println("The " + index + "th column of residual matrix:");
        TetradMatrix column = TetradMatrix.instance(9, 1);
        for(int i = 0; i < 9; i++)
        {
            column.set(i, 0, residualMatrix.get(i, index));
        }
        System.out.println(column);
        TetradMatrix pV = FactorAnalysis.matrixMult(unitVector.get(index, 0), column);

        System.out.println("2 * that:");
        TetradMatrix pV2 = FactorAnalysis.matrixMult(2, pV);
        TetradMatrix newResidualTimesSign = FactorAnalysis.matrixAdd(resTimesSignVector, pV2);


        return null;
    }
    */

    //================= MATRIX FUNCTIONS =================//

    /*
     * @return a vector that runs along the diagonal of the supplied 2D matrix.
     * If the matrix is not a square matrix, then it compiles what WOULD be the
     * diagonal if it were, starting from the upper-left corner.
     */
    public static TetradMatrix diag(TetradMatrix matrix)
    {
        //System.out.println(matrix);
        TetradMatrix diagonal = new TetradMatrix(matrix.columns(), matrix.columns());
        for(int i = 0; i < matrix.columns(); i++)
        {
            for(int j = 0; j < matrix.columns(); j++)
            {
                if(i == j) diagonal.set(j, i, matrix.get(i, i));
                else diagonal.set(j, i, 0);
            }
        }
        //System.out.println(diagonal);
        return diagonal;
    }

    public static TetradMatrix diag(DataSet dataSet)
    {
        return diag(dataSet.getDoubleData());
    }

    public static TetradMatrix diag(ICovarianceMatrix covMatrix)
    {
        return diag(covMatrix.getMatrix());
    }

    public static TetradMatrix diag(CorrelationMatrix corMatrix)
    {
        return diag(corMatrix.getMatrix());
    }

    /*
     * Subtracts b from a.
     */
    public static TetradMatrix matrixSubtract(TetradMatrix a, TetradMatrix b)
    {
        if(!(a.columns() == b.columns() && a.rows() == b.rows())) {
            throw new IllegalArgumentException();
        };

        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());
        for(int i = 0; i < result.rows(); i++)
        {
            for(int j = 0; j < result.columns(); j++)
            {
                result.set(i, j, a.get(i, j) - b.get(i, j));
            }
        }

        return result;
    }

    /*
     * Calculates (a * b)
     */
    public static TetradMatrix matrixMult(TetradMatrix a, TetradMatrix b)
    {
        if(a.columns() != b.rows()) {
            throw new IllegalArgumentException();
        };

        TetradMatrix result = new TetradMatrix(a.rows(), b.columns());

        for(int i = 0; i < a.rows(); i++)
        {
            for(int j = 0; j < b.columns(); j++)
            {
                double value = 0;
                for(int k = 0; k < b.rows(); k++)
                {
                    value += a.get(i, k) * b.get(k, j);
                }
                result.set(i, j, value);
            }
        }

        //System.out.println(result);
        return result;
    }

    public static TetradMatrix matrixMult(double scalar, TetradMatrix a)
    {
        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());

        for(int i = 0; i < a.rows(); i++)
        {
            for(int j = 0; j < a.columns(); j++)
            {
                result.set(i, j, scalar * a.get(i, j));
            }
        }

        //System.out.println(result);
        return result;
    }

    public static TetradMatrix matrixAdd(TetradMatrix a, TetradMatrix b)
    {
        if(!(a.rows() == b.rows() && a.columns() == b.columns())) {
            throw new IllegalArgumentException();
        };

        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());

        for(int i = 0; i < a.rows(); i++)
        {
            for(int j = 0; j < a.columns(); j++)
            {
                result.set(i, j, a.get(i, j) + b.get(i, j));
            }
        }

        //System.out.println(result);
        return result;
    }

    public static TetradMatrix matrixDiv(double scalar, TetradMatrix a)
    {
        TetradMatrix result = new TetradMatrix(a.rows(), a.columns());
        //System.out.println("About to divide " + a + " by " + scalar);

        for(int i = 0; i < a.rows(); i++)
        {
            for(int j = 0; j < a.columns(); j++)
            {
                result.set(i, j, a.get(i, j) / scalar);
            }
        }

        //System.out.println("Result of division: " + result);

        return result;
    }

    public static TetradMatrix transpose(TetradMatrix a)
    {
        //System.out.println("About to transpose:");
        //System.out.println(a);

        TetradMatrix result = new TetradMatrix(a.columns(), a.rows());

        for(int i = 0; i < a.columns(); i++)
        {
            for(int j = 0; j < a.rows(); j++)
            {
                result.set(i, j, a.get(j, i));
            }
        }

        //System.out.println("Result is:");
        //System.out.println(result);
        return result;
    }

    public static double trace(TetradMatrix a)
    {
        double result = 0;
        for(int i = 0; i < a.columns(); i++)
        {
            result += a.get(i, i);
        }
        return result;
    }

    public static double vectorSum(Vector<Double> vector)
    {
        double sum = 0;
        for(int i = 0; i < vector.size(); i++) sum += vector.get(i);
        return sum;
    }
}



