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

package edu.pitt.csb.mgm;

import cern.colt.matrix.DoubleMatrix1D;

/**
 * This interface should be used for non-differentiable convex functions that are decomposable such that
 * f(x) = g(x) + h(x) where g(x) is a differentiable convex function (i.e. smooth) and h(x) is a convex but not
 * necessarily differentiable (i.e. non-smooth) and has a proximal operator prox_t(x) = argmin_z 1/(2t) norm2(x-z)^2 +
 * h(z) has a solution for any t > 0. Typically g(x) will be a likelihood, and h(x) is a penalty term (as in l_1 in the
 * lasso)
 *
 *
 * Created by ajsedgewick on 8/4/15.
 */
public abstract class ConvexProximal {

    /**
     * Calculate value of smooth function g(X)
     *
     * @param X input vector
     * @return value of g(X)
     */
    abstract double smoothValue(DoubleMatrix1D X);

    /**
     * Gradient of smooth function g(X)
     *
     * @param X input vector
     * @return vector containing gradient of g(X)
     */
    abstract DoubleMatrix1D smoothGradient(DoubleMatrix1D X);


    /**
     * Calculate value of g(X) and gradient of g(X) at the same time for efficiency reasons.
     *
     * @param X input Vector
     * @param Xout gradient of g(X)
     * @return value of g(X)
     */
    public double smooth(DoubleMatrix1D X, DoubleMatrix1D Xout){
        Xout.assign(smoothGradient(X));
        return smoothValue(X);
    }

    /**
     * Calculate value of h(X)
     *
     * @param X input vector
     * @return value of h(X)
     */
    abstract double nonSmoothValue(DoubleMatrix1D X);

    /**
     * A proximal operator is the solution to this optimization problem:
     *     prox_t(x) = argmin_z \frac{1}{2t} \|x-z\|^2_2 + h(x)
     *
     * @param t positive parameter for prox operator
     * @param X input vector
     * @return vector solution to prox_t(X)
     */
    abstract DoubleMatrix1D proximalOperator(double t, DoubleMatrix1D X);

    /**
     * Calculate value of h(X) and proxOperator of h(X) at the same time for efficiency reasons.
     *
     * @param t positive parameter for prox operator
     * @param X input vector
     * @param Xout vector solution to prox_t(X)
     * @return value of h(X)
     */
    public double nonSmooth(double t, DoubleMatrix1D X, DoubleMatrix1D Xout){
        Xout.assign(proximalOperator(t,X));
        return nonSmoothValue(X);
    }

}

