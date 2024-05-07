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

package edu.pitt.csb.mgm;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import org.apache.commons.math3.util.FastMath;

/**
 * Implementation of Nesterov's 83 method as described in Beck and Teboulle, 2009 aka Fast Iterative Shrinkage
 * Thresholding Algorithm
 * <p>
 * with step size scaling from Becker et all 2011
 * <p>
 * Created by ajsedgewick on 7/29/15.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ProximalGradient {

    // Factors to alter Lipshitz constant estimate L, used for stepsize t = 1/L
    private final double beta; //factor to increase L when Lipshitz violated
    private final double alpha; //factor to decrease L otherwise
    private final Algebra alg = new Algebra();
    private final DoubleFactory1D factory1D = DoubleFactory1D.dense;

    private final boolean edgeConverge; //if this is true we look to stop optimization when the edge predictions stop changing
    private int noEdgeChangeTol = 3; //number of iterations in a row with no edge changes before we break


    /**
     * Constructor, set parameters for a proximal gradient run
     *
     * @param beta         (0,1) factor to increase L when Lipshitz violated, L = L_old/beta
     * @param alpha        (0,1) factor to decrease L otherwise, L = L_old*alpha
     * @param edgeConverge a boolean
     */
    public ProximalGradient(double beta, double alpha, boolean edgeConverge) {
        if (beta <= 0 || beta >= 1)
            throw new IllegalArgumentException("beta must be (0,1): " + beta);

        if (alpha <= 0 || alpha >= 1)
            throw new IllegalArgumentException("alpha must be (0,1): " + alpha);

        this.beta = beta;
        this.alpha = alpha;
        this.edgeConverge = edgeConverge;
    }

    /**
     * Constructor using defaults from Becker et al 2011. beta = .5, alpha = .9
     */
    public ProximalGradient() {
        this.beta = .5;
        this.alpha = .9;
        this.edgeConverge = false;
    }

    /**
     * <p>norm2.</p>
     *
     * @param vec a {@link cern.colt.matrix.DoubleMatrix1D} object
     * @return a double
     */
    public static double norm2(DoubleMatrix1D vec) {
        //return FastMath.sqrt(vec.copy().assign(Functions.pow(2)).zSum());
        return FastMath.sqrt(new Algebra().norm2(vec));
    }

    /**
     * Positive edge change tolerance is the number of iterations with 0 edge changes needed to converge. Negative edge
     * change tolerance means convergence happens when number of difference edges &lt;= |edge change tol|. Default is
     * 3.
     *
     * @param t a int
     */
    public void setEdgeChangeTol(int t) {
        this.noEdgeChangeTol = t;
    }

    //run FISTA with step size backtracking attempt to speed up

    /**
     * <p>learnBackTrack.</p>
     *
     * @param cp        a {@link edu.pitt.csb.mgm.ConvexProximal} object
     * @param Xin       a {@link cern.colt.matrix.DoubleMatrix1D} object
     * @param epsilon   a double
     * @param iterLimit a int
     * @return a {@link cern.colt.matrix.DoubleMatrix1D} object
     */
    public DoubleMatrix1D learnBackTrack(ConvexProximal cp, DoubleMatrix1D Xin, double epsilon, int iterLimit) {
        DoubleMatrix1D X = cp.proximalOperator(1.0, Xin.copy());
        DoubleMatrix1D Y = X.copy();
        DoubleMatrix1D Z = X.copy();
        DoubleMatrix1D GrY = cp.smoothGradient(Y);
        DoubleMatrix1D GrX = cp.smoothGradient(X);

        int iterCount = 0;
        int noEdgeChangeCount = 0;

        double theta = Double.POSITIVE_INFINITY;
        double thetaOld = theta;
        double L = 1.0;
        double Lold = L;


        boolean backtrackSwitch = true;
        double dx;
        double Fx = Double.POSITIVE_INFINITY;
        double Gx = Double.POSITIVE_INFINITY;
        double Fy;
        double obj;

        while (true) {
            Lold = L;
            L = L * this.alpha;
            thetaOld = theta;
            DoubleMatrix1D Xold = X.copy();
            obj = Fx + Gx;

            while (true) {
                theta = 2.0 / (1.0 + FastMath.sqrt(1.0 + (4.0 * L) / (Lold * FastMath.pow(thetaOld, 2))));
                if (theta < 1) {
                    Y.assign(Xold.copy().assign(Functions.mult(1 - theta)));
                    Y.assign(Z.copy().assign(Functions.mult(theta)), Functions.plus);
                }


                Fy = cp.smooth(Y, GrY);
                DoubleMatrix1D temp = Y.copy().assign(GrY.copy().assign(Functions.mult(1.0 / L)), Functions.minus);
                Gx = cp.nonSmooth(1.0 / L, temp, X);

                if (backtrackSwitch) {
                    Fx = cp.smoothValue(X);
                } else {
                    //tempPar = new MGMParams();
                    Fx = cp.smooth(X, GrX);
                    //GrX.assign(factory1D.make(tempPar.toVector()[0]));
                }

                DoubleMatrix1D XmY = X.copy().assign(Y, Functions.minus);
                double normXY = this.alg.norm2(XmY);
                if (normXY == 0)
                    break;

                double Qx;
                double LocalL;

                if (backtrackSwitch) {
                    //System.out.println("Back Norm");
                    Qx = Fy + this.alg.mult(XmY, GrY) + (L / 2.0) * normXY;
                    LocalL = L + 2 * FastMath.max(Fx - Qx, 0) / normXY;
                    double backtrackTol = 1e-10;
                    backtrackSwitch = FastMath.abs(Fy - Fx) >= backtrackTol * FastMath.max(FastMath.abs(Fx), FastMath.abs(Fy));
                } else {
                    //System.out.println("Close Rule");

                    //it shouldn't be possible for GrX to be null here...
                    LocalL = 2 * this.alg.mult(XmY, GrX.assign(GrY, Functions.minus)) / normXY;

                }
                //System.out.println("Iter: " + iterCount + " Fx: " + Fx + " Qx: " + Qx + " L : " + L );
                //if(-1e-8 <= Qx - Fx){
                //if(Fx <= Qx){
                //System.out.println("LocalL: " + LocalL + " L: " + L);
                if (LocalL <= L) {
                    break;
                } else if (LocalL != Double.POSITIVE_INFINITY) {
                    L = LocalL;
                } else {
                    LocalL = L;
                }

                L = FastMath.max(LocalL, L / this.beta);

            }

            int diffEdges = 0;
            for (int i = 0; i < X.size(); i++) {
                double a = X.get(i);
                double b = Xold.get(i);
                if (a != 0 & b == 0) {
                    diffEdges++;
                } else if (a == 0 & b != 0) {
                    diffEdges++;
                }
            }

            dx = ProximalGradient.norm2(X.copy().assign(Xold, Functions.minus)) / FastMath.max(1, ProximalGradient.norm2(X));

            //sometimes there are more edge changes after initial 0, so may want to do two zeros in a row...
            if (diffEdges == 0 && this.edgeConverge) {
                noEdgeChangeCount++;
                if (noEdgeChangeCount >= this.noEdgeChangeTol) {
                    System.out.println("Edges converged at iter: " + iterCount + " with |dx|/|x|: " + dx);
                    System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + ProximalGradient.norm2(X) + " nll: " +
                                       Fx + " reg: " + Gx + " DiffEdges: " + 0 + " L: " + L);
                    break;
                }
                // negative noEdgeChangeTol stops when diffEdges <= |noEdgeChangeTol|
            } else if (this.noEdgeChangeTol < 0 && diffEdges <= FastMath.abs(this.noEdgeChangeTol)) {
                System.out.println("Edges converged at iter: " + iterCount + " with |dx|/|x|: " + dx);
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + ProximalGradient.norm2(X) + " nll: " +
                                   Fx + " reg: " + Gx + " DiffEdges: " + diffEdges + " L: " + L);
                break;
            } else {
                noEdgeChangeCount = 0;
            }

            //edge converge should happen before params converge, unless epsilon is big
            if (dx < epsilon && !this.edgeConverge) {
                System.out.println("Converged at iter: " + iterCount + " with |dx|/|x|: " + dx + " < epsilon: " + epsilon);
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + ProximalGradient.norm2(X) + " nll: " +
                                   Fx + " reg: " + Gx + " DiffEdges: " + diffEdges + " L: " + L);
                break;
            }

            //restart acceleration if objective got worse
            if (Fx + Gx > obj) {
                theta = Double.POSITIVE_INFINITY;
                Y.assign(X.copy());
                //Ypar = new MGMParams(Xpar);
                Z.assign(X.copy());
            } else if (theta == 1) {
                Z.assign(X.copy());
            } else {
                Z.assign(X.copy().assign(Functions.mult(1 / theta)));
                Z.assign(Xold.copy().assign(Functions.mult(1 - (1.0 / theta))), Functions.plus);
            }


            int printIter = 100;
            if (iterCount % printIter == 0) {
                System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " normX: " + ProximalGradient.norm2(X) + " nll: " +
                                   Fx + " reg: " + Gx + " DiffEdges: " + diffEdges + " L: " + L);
                //System.out.println("Iter: " + iterCount + " |dx|/|x|: " + dx + " nll: " + negLogLikelihood(params) + " reg: " + regTerm(params));
            }

            iterCount++;
            if (iterCount >= iterLimit) {
                System.out.println("Iter limit reached");
                break;
            }
        }
        return X;
    }
}

