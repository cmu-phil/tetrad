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

package edu.cmu.tetrad.util;

/**
 * Represents a multidimensional second-degree Taylor series approximation for a differentiable function. This class
 * provides functionality to construct the Taylor expansion based on input parameters such as the expansion point,
 * function value, gradient, and Hessian, and to evaluate it at a given point.
 * <p>
 * The Taylor series is represented as: f(x) â fA + Î£(âf/âx_i * (x_i - a_i)) + 1/2 Î£Î£(âÂ²f/âx_iâx_j * (x_i - a_i) * (x_j
 * - a_j))
 */
public class MultiDimensionalTaylorDegree2 {

    private final double[] a; // Expansion point
    private final double fA; // Function value at point a
    private final double[] gradient; // Gradient at point a
    private final double[][] hessian; // Hessian at point a

    /**
     * Constructs a representation of a second-degree Taylor series expansion for a multi-dimensional function.
     *
     * @param a        the expansion point. It is an array representing the coordinates of the point at which the Taylor
     *                 expansion is centered.
     * @param fA       the value of the function at the expansion point.
     * @param gradient the gradient vector of the function at the expansion point. Each element represents the partial
     *                 derivative with respect to a variable.
     * @param hessian  the Hessian matrix of the function at the expansion point. This is a 2D array where each element
     *                 at position (i, j) represents the second partial derivative with respect to the ith and jth
     *                 variables.
     */
    public MultiDimensionalTaylorDegree2(double[] a, double fA, double[] gradient, double[][] hessian) {
        this.a = a;
        this.fA = fA;
        this.gradient = gradient;
        this.hessian = hessian;
    }

    /**
     * The main method demonstrates the use of the MultiDimensionalTaylorDegree2 class to evaluate a second-degree
     * Taylor series expansion at a specific point.
     *
     * @param args command-line arguments passed to the program
     */
    public static void main(String[] args) {
        // Example: f(x, y) = 1 + 2x + 3y + x^2 + xy + y^2 (around a = (1, 1))
        double[] a = {1.0, 1.0}; // Expansion point
        double fA = 6.0; // f(1, 1)
        double[] gradient = {2.0, 3.0}; // [df/dx, df/dy] at (1, 1)
        double[][] hessian = { // [d^2f/dx^2, d^2f/dxdy; d^2f/dydx, d^2f/dy^2] at (1, 1)
                {2.0, 1.0},
                {1.0, 2.0}
        };

        MultiDimensionalTaylorDegree2 taylor = new MultiDimensionalTaylorDegree2(a, fA, gradient, hessian);

        // Evaluate the Taylor series at (2, 2)
        double[] x = {2.0, 2.0};
        double result = taylor.evaluate(x);

        System.out.println("The result of the Taylor series at point (2, 2) is: " + result);
    }

    /**
     * Evaluates a second-degree Taylor series expansion at a given point.
     *
     * @param x the point at which the Taylor series is evaluated. It is an array where each element represents a
     *          variable's value.
     * @return the value of the Taylor series expansion at the specified point.
     */
    public double evaluate(double[] x) {
        double result = fA;

        // First-order term: gradient contribution
        for (int i = 0; i < gradient.length; i++) {
            result += gradient[i] * (x[i] - a[i]);
        }

        // Second-order term: Hessian contribution
        for (int i = 0; i < hessian.length; i++) {
            for (int j = 0; j < hessian[i].length; j++) {
                result += 0.5 * hessian[i][j] * (x[i] - a[i]) * (x[j] - a[j]);
            }
        }

        return result;
    }
}


