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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MeekRules;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;

import java.util.*;

import static org.apache.commons.math3.linear.MatrixUtils.*;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * <p>Implements the DAGMA algorithm. The reference is here:</p>
 *
 * <p>NEEDS DOCUMENTATION</p>
 *
 * @author bryanandrews
 */
public class Dagma {

    private RealMatrix cov;
    private List<Node> variables;
    private int d;
    private double lambda1;
    private double w_threshold;
    private double[] T;
    private double mu_init;
    private double mu_factor;


    private int warm_iter;
    private int max_iter;
    private double lr;
    private int checkpoint;
    private double b1;
    private double b2;
    private RealMatrix opt_m;
    private RealMatrix opt_v;
    private RealMatrix I;
    private double tol;
    private boolean cpdag;

    /**
     * Constructor.
     */
    public Dagma(DataSet dataset) {
        this.variables = dataset.getVariables();
        this.cov = dataset.getCorrelationMatrix().getApacheData();
        this.d = cov.getRowDimension();

        this.I = createRealIdentityMatrix(this.d);

        this.lambda1 = 0.01;
        this.w_threshold = 0.01;

        this.T = new double[] {1.0, .9, .8, .7};

        this.mu_init = 1.0;
        this.mu_factor = 0.1;
        this.warm_iter = 20000;
        this.max_iter = 70000;
        this.lr = 3e-4;
        this.checkpoint = 1000;
        this.b1=0.99;
        this.b2=0.999;
        this.tol = 1e-6;

        this.cpdag = true;

    }

    /**
     * NEEDS DOCUMENTATION
     */

    // Assumes square positive semi-definite matrix
    private double log_det(RealMatrix M) {
        int d = M.getRowDimension();
        LUDecomposition lud = new LUDecomposition(M);
        RealMatrix P = lud.getP();
        RealMatrix L = lud.getL();
        RealMatrix U = lud.getU();

        double ldet = log(abs(d - P.getTrace() - 1));
        for (int i = 0; i < d; i++) {
            ldet += log(abs(L.getEntry(i, i)));
            ldet += log(abs(U.getEntry(i, i)));
        }

        return ldet;
    }

    // Assumes square matrix
    private boolean any_neg(RealMatrix M) {
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                if (M.getEntry(i, j) < 0) {
                    return true;
                }
            }
        }

        return false;
    }


    // Assumes square matrix
    private double abs_sum(RealMatrix M) {
        double s = 0;

        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                s += abs(M.getEntry(i, j));
            }
        }

        return s;
    }


    // Assumes two square matrices of equal dimension
    private RealMatrix prod(RealMatrix A, RealMatrix B) {
        int d = A.getRowDimension();
        RealMatrix C = createRealMatrix(d, d);

        for (int i = 0; i < d; i++) {
            for (int j = 0; i < d; i++) {
                C.setEntry(i, j, A.getEntry(i, j) * B.getEntry(i, j));
            }
        }

        return C;
    }

    // Evaluate value and gradient of the score function.
    private double _score(RealMatrix W) {
        RealMatrix dif = this.I.subtract(W);
        RealMatrix rhs = this.cov.multiply(dif);
        return 0.5 * dif.transpose().multiply(rhs).getTrace();
    }

    // Evaluate value and gradient of the logdet acyclicity constraint.
    private double _h(RealMatrix W, double s) {
        RealMatrix M = this.I.scalarMultiply(s).subtract(prod(W, W));
        return this.d * log(s) - log_det(M);
    }

    // Evaluate value of the penalized objective function.
    private double _func(RealMatrix W, double mu, double s) {
        double score = _score(W);
        double h = _h(W, s);
        return mu * (score + this.lambda1 * abs_sum(W)) + h;
    }

    private void _adam_update(RealMatrix grad, int iter) {
        int d = grad.getRowDimension();

        double b1_ = 1 - this.b1;
        double b2_ = 1 - this.b2;

        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                double g = grad.getEntry(i, j);
                double m = this.opt_m.getEntry(i, j);
                double v = this.opt_v.getEntry(i, j);
                double a = this.b1 * m + b1_ * g;
                double b = this.b2 * v + b2_ * pow(g, 2);
                this.opt_m.setEntry(i, j, a);
                this.opt_v.setEntry(i, j, b);
                a /= 1 - pow(this.b1, iter);
                b /= 1 - pow(this.b2, iter);
                grad.setEntry(i, j, a / (sqrt(b) + 1e-8));
            }
        }
    }

    private boolean minimize(RealMatrix W, double mu, int inner_iter, double s, double lr) {
        double obj_prev = 1e16;
        double obj_new;
        this.opt_m = createRealMatrix(this.d, this.d);
        this.opt_v = createRealMatrix(this.d, this.d);

        RealMatrix W_old = W.copy();
        RealMatrix grad = null;

        for (int iter = 1; iter <= inner_iter; iter++) {
            // Compute the (sub) gradient of the objective
            RealMatrix M = inverse(this.I.scalarMultiply(s).subtract(prod(W, W))).scalarAdd(1e-16);
            // sI - W o W is not an M -matrix


            while (any_neg(M)) {

                if ((iter == 1) || (s <= 0.9)) {

                    for (int i = 0; i < this.d; i++) {
                        for (int j = 0; j < this.d; j++) {
                            W.setEntry(i, j, W_old.getEntry(i, j));
                        }
                    }
                    return true;
                }
                else {

                    for (int i = 0; i < this.d; i++) {
                        for (int j = 0; j < this.d; j++) {
                            W.addToEntry(i, j, lr * grad.getEntry(i, j));
                        }
                    }

                    lr *= 0.5;
                    if (lr <= 1e-16) return false;

                    for (int i = 0; i < this.d; i++) {
                        for (int j = 0; j < this.d; j++) {
                            W.addToEntry(i, j, -lr * grad.getEntry(i, j));
                        }
                    }

                    M = inverse(this.I.scalarMultiply(s).subtract(prod(W, W))).scalarAdd(1e-16);
                }
            }

            grad = this.cov.multiply(W);
            for (int i = 0; i < this.d; i++) {
                for (int j = 0; j < this.d; j++) {
                    double g = grad.getEntry(i, j);
                    double c = this.cov.getEntry(i, j);
                    double w = W.getEntry(i, j);
                    double sign = 0;
                    if (w > 0) sign = 1;
                    if (w < 0) sign = -1;
                    double mt = M.getEntry(j, i);
                    grad.setEntry(i, j, mu * (g - c + this.lambda1 * sign) + 2 * w * mt);
                }
            }

            // Adam step
            _adam_update(grad, iter);
            for (int i = 0; i < this.d; i++) {
                for (int j = 0; j < this.d; j++) {
                    W.addToEntry(i, j, -lr * grad.getEntry(i, j));
                }
            }

            // Check obj convergence
            if ((iter % this.checkpoint == 0) || (iter == this.max_iter)) {
                obj_new = _func(W, mu, s);
                if (abs((obj_prev - obj_new) / obj_prev) <= this.tol) break;
                obj_prev = obj_new;
            }
        }
        return false;
    }





    public Graph search() {


        RealMatrix W_est = createRealMatrix(this.d, this.d);

        double mu = this.mu_init;
        double lr_adam;

        int outer_iters = this.T.length;
        int inner_iters = this.warm_iter;

        for (double s : this.T) {
            lr_adam = this.lr;
            if (--outer_iters == 0) inner_iters = this.max_iter;
            while (minimize(W_est, mu, inner_iters, s, lr_adam)) {
                lr_adam *= 0.5;
                s += 0.1;
            }
            mu *= this.mu_factor;
        }

        // Convert W to graph
        double w_min;
        double w_threshold = this.w_threshold;
        do {
            w_min = Double.MAX_VALUE;
            for (int i = 0; i < this.d; i++) {
                W_est.setEntry(i, i, 0.0);
                for (int j = 0; j < this.d; j++) {

                    double w = abs(W_est.getEntry(i, j));
                    if (w < w_threshold) {
                        W_est.setEntry(i, j, 0);
                    } else if (w < w_min) {
                        w_min = w;
                    }
                }
            }
            w_threshold = w_min + 1e-6;
        } while (W_est.power(this.d).getTrace() != 0);

        Graph graph = new EdgeListGraph(this.variables);
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                if (W_est.getEntry(i, j) == 0) continue;
                graph.addDirectedEdge(this.variables.get(i), this.variables.get(j));
            }
        }

        if (this.cpdag) {
            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
        }

        return graph;
    }
}
