///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.MatrixUtils;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a correlation matrix together with variable names and sample size; intended as a representation of a data
 * set.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class CorrelationMatrix extends CovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new correlation matrix using the covariances in the given covariance matrix.
     *
     * @param matrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public CorrelationMatrix(ICovarianceMatrix matrix) {
        this(matrix.getVariables(), MatrixUtils.convertCovToCorr(matrix.getMatrix()), matrix.getSampleSize());
    }

    /**
     * Constructs a new correlation matrix from the the given DataSet.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public CorrelationMatrix(DataSet dataSet) {
        super(Collections.unmodifiableList(dataSet.getVariables()),
                dataSet.getCorrelationMatrix(), dataSet.getNumRows());

        // These checks break testwise deletion

    }

    /**
     * Constructs a correlation matrix data set using the given information. The matrix matrix is internally converted
     * to a correlation matrix.
     *
     * @param variables  a {@link java.util.List} object
     * @param matrix     a {@link edu.cmu.tetrad.util.Matrix} object
     * @param sampleSize a int
     */
    public CorrelationMatrix(List<Node> variables, Matrix matrix,
                             int sampleSize) {
        super(variables, MatrixUtils.convertCovToCorr(matrix).copy(), sampleSize);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.CorrelationMatrix} object
     */
    public static CorrelationMatrix serializableInstance() {
        return new CorrelationMatrix(new LinkedList<>(),
                new Matrix(0, 0), 1);
    }

    /**
     * {@inheritDoc}
     */
    public void setMatrix(Matrix matrix) {
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square.");
        }

        for (int i = 0; i < matrix.getNumRows(); i++) {
            if (FastMath.abs(matrix.get(i, i) - 1.0) > 1.e-5) {
                throw new IllegalArgumentException(
                        "For a correlation matrix, " +
                        "variances (diagonal elements) must be 1.0");
            }
        }

        super.setMatrix(matrix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Matrix getSelection(int[] rows, int[] cols) {
        return getMatrix().view(rows, cols).mat();
    }

    /**
     * <p>getSubCorrMatrix.</p>
     *
     * @param submatrixVarNames an array of {@link java.lang.String} objects
     * @return a submatrix, returning as a correlation matrix, with variables in the given order.
     */
    public CorrelationMatrix getSubCorrMatrix(String[] submatrixVarNames) {
        ICovarianceMatrix covarianceMatrix = getSubmatrix(submatrixVarNames);
        return new CorrelationMatrix(covarianceMatrix);
    }

}






