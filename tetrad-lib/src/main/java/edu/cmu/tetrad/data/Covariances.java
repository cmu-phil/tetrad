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

/**
 * Some comemon methods for the various covariance implementations.
 *
 * @author Joseph D. Ramsey
 * @version $Id: $Id
 */
public interface Covariances {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Returns the covariance at (i, j).
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    double covariance(int i, int j);

    /**
     * Returns the dimensiom of the matrix.
     *
     * @return a int
     */
    int size();

    /**
     * Sets the covariance at (i, j) to a particular value. Not effective for implemetations that calculate covariances
     * from data on the fly.
     *
     * @param i a int
     * @param j a int
     * @param v a double
     */
    void setCovariance(int i, int j, double v);

    /**
     * Returns the underlying covariance matrix.
     *
     * @return an array of  objects
     */
    double[][] getMatrix();

    /**
     * Returns a submatrix of the covariance matrix for the given rows and columns.
     *
     * @param rows an array of  objects
     * @param cols an array of  objects
     * @return an array of  objects
     */
    double[][] getSubMatrix(int[] rows, int[] cols);
}

