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

package edu.cmu.tetrad.util;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

/**
 * Some algebra methods.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TetradAlgebra {

    /**
     * Initializes a new instance of the TetradAlgebra class.
     */
    public TetradAlgebra() {
    }

    /**
     * <p>multOuter.</p>
     *
     * @param v1 a {@link edu.cmu.tetrad.util.Vector} object
     * @param v2 a {@link edu.cmu.tetrad.util.Vector} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix multOuter(Vector v1, Vector v2) {
        DoubleMatrix2D m = new Algebra().multOuter(new DenseDoubleMatrix1D(v1.toArray()),
                new DenseDoubleMatrix1D(v2.toArray()), null);
        return new Matrix(m.toArray());
    }

    /**
     * <p>solve.</p>
     *
     * @param a a {@link edu.cmu.tetrad.util.Matrix} object
     * @param b a {@link edu.cmu.tetrad.util.Matrix} object
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix solve(Matrix a, Matrix b) {
        DoubleMatrix2D _a = new DenseDoubleMatrix2D(a.toArray());
        DoubleMatrix2D _b = new DenseDoubleMatrix2D(b.toArray());
        return new Matrix(new Algebra().solve(_a, _b).toArray());
    }

    /**
     * <p>identity.</p>
     *
     * @param rows a int
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public static Matrix identity(int rows) {
        return new Matrix(DoubleFactory2D.dense.identity(rows).toArray());
    }
}



