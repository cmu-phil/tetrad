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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.LinkedList;
import java.util.List;

/**
 * <p>Includes methods for estimating the standard errors of the freeParameters of
 * an estimated SEM.  The standard errors are the square roots of the diagonal
 * elements of the inverse of the "information matrix" (see Bollen page
 * 135).</p> </p> <p>This class resembles the SemOptimizer implementations.</p>
 *
 * @author Frank Wimberly
 */
public class SemStdErrorEstimator {

    //Some constants used by the method SecondPartialDerivativeRidr
    private static final double CON = 1.4;
    private static final double CON2 = CON * CON;
    private static final double BIG = 1.e30;
    private static final int NTAB = 10;
    private static final double SAFE = 2.0;

    /**
     * The array in which the standard errors of the freeParameters are stored.
     */
    private double[] stdErrs = null;
//    private SemIm semIm;

    /**
     * Blank constructor.
     */
    public SemStdErrorEstimator() {
    }

    /**
     * <p>This method computes the information matrix or Hessian matrix of
     * second order partial derivatives of the fitting function (4B_2 on page
     * 135 of Bollen) with respect to the free freeParameters of the estimated SEM.
     * It then computes the inverse of the the information matrix and calculates
     * the standard errors of the freeParameters as the square roots of the diagonal
     * elements of that matrix.</p>
     *
     * @param estSem the estimated SEM.
     */
    public void computeStdErrors(ISemIm estSem) {
//        if (!unmeasuredLatents(estSem.getSemPm()).isEmpty()) {
//            int n = estSem.getFreeParameters().size();
//            stdErrs = new double[n];
//
//            for (int i = 0; i < n; i++) {
//                stdErrs[i] = Double.NaN;
//            }
//
//            return;
//        }


//        this.semIm = estSem;
        estSem.setParameterBoundsEnforced(false);
        double[] paramsOriginal = estSem.getFreeParamValues();
        double delta;
        FittingFunction fcn = new SemFittingFunction(estSem);
        boolean ridder = false;  // Ridder is more accurate but a lot slower.

        int n = fcn.getNumParameters();

        //Store the free freeParameters of the SemIm so that they can be reset to these
        //values.  The differentiation methods change them.
        double[] params = new double[n];
        System.arraycopy(paramsOriginal, 0, params, 0, n);

        //If the Ridder method (secondPartialDerivativeRidr) is used to search for
        //the best delta it is initially set to 0.1.  Otherwise the delta is set to
        //0.005.  That value has worked well for those fitting functions tested to
        //date.
        if (ridder) {
            delta = 0.1;
        } else {
            delta = 0.005;
        }

        //The Hessian matrix of second order partial derivatives is called the
        //information matrix.
        TetradMatrix hess = new TetradMatrix(n, n);

        List<Parameter> freeParameters = estSem.getFreeParameters();
        boolean containsCovararianceParameter = false;

        for (Parameter p : freeParameters) {
            if (p.getType() == ParamType.COVAR) {
                containsCovararianceParameter = true;
                break;
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                Parameter pi = freeParameters.get(i);
                Parameter pj = freeParameters.get(j);

                if (!containsCovararianceParameter) {

                    // Restrict off-diagonal to just collider edge freeParameters.
                    if (i != j && (pi.getType() != ParamType.COEF || pj.getType() != ParamType.COEF)) {
                        continue;
                    }

                    if (pi.getNodeB() != pj.getNodeB()) {
                        continue;
                    }
                }

                double v;

                if (ridder) {
                    v = secondPartialDerivativeRidr(fcn, i, j, params, delta);
                } else {
                    v = secondPartialDerivative(fcn, i, j, params, delta);
                }

                if (Math.abs(v) < 1e-7) {
                    v = 0;
                }

//                if (Double.isNaN(v)) {
//                    v = 0;
//                }

                hess.set(i, j, v);
                hess.set(j, i, v);
            }
        }

        ROWS:
        for (int i = 0; i < hess.rows(); i++) {
            for (int j = 0; j < hess.columns(); j++) {
                if (hess.get(i, j) != 0) {
                    continue ROWS;
                }
            }

//            System.out.println("Zero row for " + freeParameters.get(i));
        }

        //The diagonal elements of the inverse of the information matrix are the
        //squares of the standard errors of the freeParameters.  Their order is the
        //same as in the array of free parameter values stored in paramsOriginal.
        try {

            TetradMatrix hessInv = hess.inverse();
//            TetradMatrix hessInv = hess.ginverse();

//            System.out.println("Inverse: " + hessInv);

//            for (int i = 0; i < freeParameters.size(); i++) {
//                System.out.println(i + " = " + freeParameters.get(i));
//            }

            stdErrs = new double[n];

            //Hence the standard errors of the freeParameters are the square roots of the
            //diagonal elements of the inverse of the information matrix.
            for (int i = 0; i < n; i++) {
                double v = Math.sqrt((2.0 / (estSem.getSampleSize() - 1)) * hessInv.get(i, i));

                if (v == 0) {
                    System.out.println("v = " + v + " hessInv(i, i) = " + hessInv.get(i, i));
                }

                if (v == 0) {
                    stdErrs[i] = Double.NaN;
                } else {
                    stdErrs[i] = v;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            stdErrs = new double[n];

            for (int i = 0; i < n; i++) {
                stdErrs[i] = Double.NaN;
            }
        }

        //Restore the freeParameters of the estimated SEM to their original values.
        estSem.setFreeParamValues(paramsOriginal);
        estSem.setParameterBoundsEnforced(true);
    }

    private List<Node> unmeasuredLatents(SemPm semPm) {
        SemGraph graph = semPm.getGraph();

        List<Node> unmeasuredLatents = new LinkedList<Node>();

        NODES:
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                for (Node child : graph.getChildren(node)) {
                    if (child.getNodeType() == NodeType.MEASURED) {
                        continue NODES;
                    }
                }

                unmeasuredLatents.add(node);
            }
        }

        return unmeasuredLatents;
    }

    /**
     * @return the array of standard errors for the free paramaeters of the
     * SEM.
     */
    public double[] getStdErrors() {
        return stdErrs;
    }

    /**
     * This method straightforwardly applies the standard definition of the
     * numerical estimates of the second order partial derivatives.  See for
     * example Section 5.7 of Numerical Recipes in C.
     */
    public double secondPartialDerivative(FittingFunction f, int i, int j,
                                          double[] p, double delt) {
        double[] arg = new double[p.length];
        System.arraycopy(p, 0, arg, 0, p.length);

        double center = f.evaluate(arg);

        arg[i] += delt;
        arg[j] += delt;
        double ff1 = f.evaluate(arg);

        arg[j] -= 2 * delt;
        double ff2 = f.evaluate(arg);

        arg[i] -= 2 * delt;
        arg[j] += 2 * delt;
        double ff3 = f.evaluate(arg);

        arg[j] -= 2 * delt;
        double ff4 = f.evaluate(arg);

        if (Double.isNaN(ff1)) {
            ff1 = center;
        }

        if (Double.isNaN(ff2)) {
            ff2 = center;
        }

        if (Double.isNaN(ff3)) {
            ff3 = center;
        }

        if (Double.isNaN(ff4)) {
            ff4 = center;
        }

        double fsSum = ff1 - ff2 - ff3 + ff4;

        return fsSum / (4.0 * delt * delt);
    }

    /**
     * This method implements Ridder's algorithm for computing the second order
     * partial derivatives.  It is a translation of the C program in section 5.7
     * of Numerical Recipes in C.  It is more robust than the above method in
     * that it searches for a perferred value of delt.  But based on our
     * experience to date with SEM fitting functions, the above method seems to
     * be adequately accurate and faster that this one.
     */
    public double secondPartialDerivativeRidr(FittingFunction f, int i, int j,
                                              double[] args, double delt) {

        double[] arg = new double[args.length];
        double[][] a = new double[NTAB][NTAB];
        double hh = delt;
        double errt;
        double ans = 0.0;
        double fac;

        System.arraycopy(args, 0, arg, 0, args.length);

        double center = f.evaluate(arg);

        arg[i] += delt;
        arg[j] += delt;
        double ff1 = f.evaluate(arg);

        arg[j] -= 2 * delt;
        double ff2 = f.evaluate(arg);

        arg[i] -= 2 * delt;
        arg[j] += 2 * delt;
        double ff3 = f.evaluate(arg);

        arg[j] -= 2 * delt;
        double ff4 = f.evaluate(arg);

        if (Double.isNaN(ff1)) {
            ff1 = center;
        }

        if (Double.isNaN(ff2)) {
            ff2 = center;
        }

        if (Double.isNaN(ff3)) {
            ff3 = center;
        }

        if (Double.isNaN(ff4)) {
            ff4 = center;
        }

        a[0][0] = (ff1 - ff2 - ff3 + ff4) / (4.0 * delt * delt);
        double err = BIG;

        for (int ii = 1; ii < NTAB; ii++) {
            hh /= CON;

            System.arraycopy(args, 0, arg, 0, args.length);

            arg[i] += hh;
            arg[j] += hh;
            ff1 = f.evaluate(arg);

            arg[j] -= 2 * hh;
            ff2 = f.evaluate(arg);

            arg[i] -= 2 * hh;
            arg[j] += 2 * hh;
            ff3 = f.evaluate(arg);

            arg[j] -= 2 * hh;
            ff4 = f.evaluate(arg);

            if (Double.isNaN(ff1)) {
                ff1 = center;
            }

            if (Double.isNaN(ff2)) {
                ff2 = center;
            }

            if (Double.isNaN(ff3)) {
                ff3 = center;
            }

            if (Double.isNaN(ff4)) {
                ff4 = center;
            }

            a[0][ii] = (ff1 - ff2 - ff3 + ff4) / (4.0 * hh * hh);

            fac = CON2;

            for (int jj = 1; jj < ii; jj++) {
                a[jj][ii] =
                        (a[jj - 1][ii] * fac - a[jj - 1][ii - 1]) / (fac - 1.0);
                fac = CON2 * fac;
                errt = Math.max(Math.abs(a[jj][ii] - a[jj - 1][ii]),
                        Math.abs(a[jj][ii] - a[jj - 1][ii - 1]));
                if (errt < err) {
                    err = errt;
                    ans = a[jj][ii];
                }
            }

            if (Math.abs(a[ii][ii] - a[ii - 1][ii - 1]) >= SAFE * err) {
                break;
            }
        }

        return ans;
    }

    /**
     * Evaluates a fitting function for an array of freeParameters.
     *
     * @author Joseph Ramsey
     */
    static interface FittingFunction {

        /**
         * @return the value of the function for the given array of parameter
         * values.
         */
        double evaluate(double[] argument);

        /**
         * @return the number of freeParameters.
         */
        int getNumParameters();
    }

    /**
     * Wraps a Sem for purposes of calculating its fitting function for given
     * parameter values.
     *
     * @author Joseph Ramsey
     */
    static class SemFittingFunction implements FittingFunction {

        /**
         * The wrapped Sem.
         */
        private final ISemIm sem;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public SemFittingFunction(ISemIm sem) {
            this.sem = sem;
        }

        /**
         * Computes the maximum likelihood function value for the given
         * freeParameters values as given by the optimizer. These values are mapped
         * to parameter values.
         */
        public double evaluate(double[] parameters) {
            List<Parameter> _parameters = sem.getSemPm().getFreeParameters();

            for (int i = 0; i < _parameters.size(); i++) {
                Parameter parameter = _parameters.get(i);
                if (parameter.getType() == ParamType.VAR && parameters[i] < 0) {
                    parameters[i] = 0;
                }
            }

            sem.setFreeParamValues(parameters);

            // This needs to be FML-- see Bollen p. 109.
//            try {
            return sem.getScore();
//            } catch (Exception e) {
//                return Double.NEGATIVE_INFINITY;
//            }
        }

        /**
         * @return the number of arguments. Required by the MultivariateFunction
         * interface.
         */
        public int getNumParameters() {
            return this.sem.getNumFreeParams();
        }
    }
}





