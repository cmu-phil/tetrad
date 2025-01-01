package edu.cmu.tetrad.util;

public class MultiDimensionalTaylorDegree2 {

    private final double[] a; // Expansion point
    private final double fA; // Function value at point a
    private final double[] gradient; // Gradient at point a
    private final double[][] hessian; // Hessian at point a

    // Constructor
    public MultiDimensionalTaylorDegree2(double[] a, double fA, double[] gradient, double[][] hessian) {
        this.a = a;
        this.fA = fA;
        this.gradient = gradient;
        this.hessian = hessian;
    }

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

    // Evaluate the Taylor series at a given point x
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

