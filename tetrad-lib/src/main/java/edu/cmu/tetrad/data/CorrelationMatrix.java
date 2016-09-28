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
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a correlation matrix together with variable names and sample size;
 * intended as a representation of a data set.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class CorrelationMatrix extends CovarianceMatrix
        implements TetradSerializable {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a new correlation matrix using the covariances in the given
     * covariance matrix.
     */
    public CorrelationMatrix(ICovarianceMatrix matrix) {
        this(matrix.getVariables(), matrix.getMatrix().copy(), matrix.getSampleSize());
    }

    /**
     * Constructs a new correlation matrix using the covariances in the given
     * covariance matrix.
     */
    public CorrelationMatrix(ICovarianceMatrix matrix, boolean inPlace) {
        this(matrix.getVariables(), (inPlace ? matrix.getMatrix() : new CovarianceMatrix(matrix).getMatrix()),
                matrix.getSampleSize(), inPlace);
    }

    /**
     * Constructs a new correlation matrix from the the given DataSet.
     */
    public CorrelationMatrix(DataSet dataSet) {
        super(Collections.unmodifiableList(dataSet.getVariables()),
                dataSet.getCorrelationMatrix(), dataSet.getNumRows());
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Data set not continuous.");
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }
    }

    /**
     * Constructs a correlation matrix data set using the given information. The
     * matrix matrix is internally converted to a correlation matrix.
     */
    private CorrelationMatrix(List<Node> variables, TetradMatrix matrix,
                              int sampleSize) {
        super(variables, MatrixUtils.convertCovToCorr(matrix).copy(), sampleSize);
    }

    /**
     * Constructs a correlation matrix data set using the given information. The
     * matrix matrix is internally converted to a correlation matrix.
     */
    private CorrelationMatrix(List<Node> variables, TetradMatrix matrix,
                              int sampleSize, boolean inPlace) {
        super(variables, inPlace ? MatrixUtils.convertCovToCorr(matrix) :
                MatrixUtils.convertCovToCorr(matrix.copy()), sampleSize);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static CorrelationMatrix serializableInstance() {
        return new CorrelationMatrix(new LinkedList<Node>(),
                new TetradMatrix(0, 0), 1);
    }

    //=================================PUBLIC METHODS======================//

    public final void setMatrix(TetradMatrix matrix) {
        if (!matrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square.");
        }

        for (int i = 0; i < matrix.rows(); i++) {
            if (Math.abs(matrix.get(i, i) - 1.0) > 1.e-5) {
                throw new IllegalArgumentException(
                        "For a correlation matrix, " +
                                "variances (diagonal elements) must be 1.0");
            }
        }

        super.setMatrix(matrix);
    }

    @Override
    public TetradMatrix getSelection(int[] rows, int[] cols) {
        return getMatrix().getSelection(rows, cols);
    }

    /**
     * @return a submatrix, returning as a correlation matrix, with variables
     * in the given order.
     */
    public CorrelationMatrix getSubCorrMatrix(String[] submatrixVarNames) {
        ICovarianceMatrix covarianceMatrix = getSubmatrix(submatrixVarNames);
        return new CorrelationMatrix(covarianceMatrix);
    }

    /**
     * @return a submatrix, returning as a correlation matrix, with variables
     * in the given order.
     */
    public CorrelationMatrix getSubCorrMatrix(int[] indices) {
        ICovarianceMatrix covarianceMatrix = getSubmatrix(indices);
        return new CorrelationMatrix(covarianceMatrix);
    }
}





