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
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.List;

import static org.apache.commons.math3.linear.MatrixUtils.*;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements the DAGMA algorithm. The reference is here:
 * <p>
 * Bello, K., Aragam, B., &amp; Ravikumar, P. (2022). Dagma: Learning dags via m-matrices and a log-determinant acyclicity
 * characterization. Advances in Neural Information Processing Systems, 35, 8226-8239.
 *
 * @author bryanandrews
 * @version $Id: $Id
 */
public class Dagma {

    private final double[] T;
    private final double muInit;
    private final double muFactor;
    private final int warmIter;
    private final int maxIter;
    private final double lr;
    private final int checkpoint;
    private final double b1;
    private final double b2;
    private final double tol;
    private final RealMatrix cov;
    private final List<Node> variables;
    private final RealMatrix I;
    private final int d;
    private double lambda1;
    private double wThreshold;
    private boolean cpdag;


    /**
     * Constructor.
     *
     * @param dataset a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public Dagma(DataSet dataset) {
        this.variables = dataset.getVariables();
        this.cov = dataset.getCorrelationMatrix().getApacheData();
        this.d = cov.getRowDimension();
        this.I = createRealIdentityMatrix(this.d);

        // tunable parameters
        this.lambda1 = 0.05;
        this.wThreshold = 0.1;
        this.cpdag = true;

        // M-matrix s values
        this.T = new double[]{1.0, .9, .8, .7};

        // central path coefficient and decay factor
        this.muInit = 1.0;
        this.muFactor = 0.1;

        // ADAM optimizer parameters
        this.warmIter = 20000;
        this.maxIter = 70000;
        this.lr = 3e-4;
        this.checkpoint = 1000;
        this.b1 = 0.99;
        this.b2 = 0.999;
        this.tol = 1e-6;
    }


    /**
     * NEEDS DOCUMENTATION
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph search() {
        RealMatrix W = createRealMatrix(this.d, this.d);

        double mu = this.muInit;
        double lrAdam;

        int outerIters = this.T.length;
        int innerIters = this.warmIter;

        for (double s : this.T) {
            lrAdam = this.lr;
            if (outerIters-- == 1) innerIters = this.maxIter;
            while (minimize(W, mu, innerIters, s, lrAdam)) {
                lrAdam *= 0.5;
                s += 0.1;
            }
            mu *= this.muFactor;
        }

        return toGraph(W);
    }


    /**
     * <p>Getter for the field <code>lambda1</code>.</p>
     *
     * @return a double
     */
    public double getLambda1() {
        return this.lambda1;
    }


    /**
     * <p>Setter for the field <code>lambda1</code>.</p>
     *
     * @param lambda1 a double
     */
    public void setLambda1(double lambda1) {
        this.lambda1 = lambda1;
    }


    /**
     * <p>Getter for the field <code>wThreshold</code>.</p>
     *
     * @return a double
     */
    public double getWThreshold() {
        return this.wThreshold;
    }


    /**
     * <p>Setter for the field <code>wThreshold</code>.</p>
     *
     * @param wThreshold a double
     */
    public void setWThreshold(double wThreshold) {
        this.wThreshold = wThreshold;
    }


    /**
     * <p>Getter for the field <code>cpdag</code>.</p>
     *
     * @return a boolean
     */
    public boolean getCpdag() {
        return this.cpdag;
    }


    /**
     * <p>Setter for the field <code>cpdag</code>.</p>
     *
     * @param cpdag a boolean
     */
    public void setCpdag(boolean cpdag) {
        this.cpdag = cpdag;
    }


    // Evaluate value and gradient of the score function.
    private double _score(RealMatrix W) {
        RealMatrix dif = this.I.subtract(W);
        RealMatrix rhs = this.cov.multiply(dif);
        return 0.5 * dif.transpose().multiply(rhs).getTrace();
    }


    // Evaluate value and gradient of the logdet acyclicity constraint.
    private double _h(RealMatrix W, double s) {
        RealMatrix M = getMMatrix(W, s);
        return this.d * log(s) - logDet(M);
    }


    // Evaluate value of the penalized objective function.
    private double _func(RealMatrix W, double mu, double s) {
        double score = _score(W);
        double h = _h(W, s);
        return mu * (score + this.lambda1 * absSum(W)) + h;
    }


    private void adamUpdate(RealMatrix grad, int iter, RealMatrix optM, RealMatrix optV) {

        double b1_ = 1 - this.b1;
        double b2_ = 1 - this.b2;

        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                double g = grad.getEntry(i, j);
                double m = optM.getEntry(i, j);
                double v = optV.getEntry(i, j);

                double a = this.b1 * m + b1_ * g;
                double b = this.b2 * v + b2_ * pow(g, 2);

                optM.setEntry(i, j, a);
                optV.setEntry(i, j, b);
                a /= 1 - pow(this.b1, iter);
                b /= 1 - pow(this.b2, iter);

                grad.setEntry(i, j, a / (sqrt(b) + 1e-8));
            }
        }
    }


    private boolean minimize(RealMatrix W, double mu, int innerIter, double s, double lrAdam) {
        RealMatrix optM = createRealMatrix(this.d, this.d);
        RealMatrix optV = createRealMatrix(this.d, this.d);

        double objPrev = 1e16;
        double objNew;

        RealMatrix W_old = W.copy();
        RealMatrix grad = null;

        for (int iter = 1; iter <= innerIter; iter++) {
            RealMatrix M = inverse(getMMatrix(W, s));
            addToEntries(M, 1e-16);

            while (notMMatrix(M)) {
                if ((iter == 1) || (s <= 0.9)) {
                    setEntries(W, W_old);
                    return true;
                } else if (lrAdam <= 2e-16) {
                    addToEntries(W, grad, lrAdam);
                    return false;
                } else {
                    lrAdam *= 0.5;
                    addToEntries(W, grad, lrAdam);
                    M = inverse(getMMatrix(W, s));
                    addToEntries(M, 1e-16);
                }
            }

            grad = this.cov.multiply(W);
            for (int i = 0; i < this.d; i++) {
                for (int j = 0; j < this.d; j++) {
                    double g = grad.getEntry(i, j);
                    double c = this.cov.getEntry(i, j);
                    double w = W.getEntry(i, j);
                    double mt = M.getEntry(j, i);

                    double sign = 0;
                    if (w > 0) sign = 1;
                    if (w < 0) sign = -1;

                    grad.setEntry(i, j, mu * (g - c + this.lambda1 * sign) + 2 * w * mt);
                }
            }

            // Adam step
            adamUpdate(grad, iter, optM, optV);
            addToEntries(W, grad, -lrAdam);

            // Check obj convergence
            if (iter % this.checkpoint == 0) {
                objNew = _func(W, mu, s);
                if (abs((objPrev - objNew) / objPrev) <= this.tol) break;
                objPrev = objNew;
            }
        }

        return false;
    }


    private RealMatrix getMMatrix(RealMatrix W, double s) {
        RealMatrix M = this.I.scalarMultiply(s);

        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                M.addToEntry(i, j, -W.getEntry(i, j) * W.getEntry(i, j));
            }
        }

        return M;
    }


    private void setEntries(RealMatrix A, RealMatrix B) {
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                A.setEntry(i, j, B.getEntry(i, j));
            }
        }
    }


    private void addToEntries(RealMatrix A, double c) {
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                A.addToEntry(i, j, c);
            }
        }
    }


    private void addToEntries(RealMatrix A, RealMatrix B, double c) {
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                A.addToEntry(i, j, c * B.getEntry(i, j));
            }
        }
    }


    private double logDet(RealMatrix M) {
        assert M.isSquare();
        int d = M.getRowDimension();

        LUDecomposition lud = new LUDecomposition(M);
        RealMatrix P = lud.getP();
        RealMatrix L = lud.getL();
        RealMatrix U = lud.getU();

        double logDet = log(abs(d - P.getTrace() - 1));
        for (int i = 0; i < d; i++) {
            logDet += log(abs(L.getEntry(i, i)));
            logDet += log(abs(U.getEntry(i, i)));
        }

        return logDet;
    }


    private boolean notMMatrix(RealMatrix M) {
        assert M.isSquare();
        int d = M.getRowDimension();

        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                if (M.getEntry(i, j) < 0) {
                    return true;
                }
            }
        }

        return false;
    }


    private double absSum(RealMatrix M) {
        assert M.isSquare();
        int d = M.getRowDimension();

        double s = 0;
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < d; j++) {
                s += abs(M.getEntry(i, j));
            }
        }

        return s;
    }


    private Graph toGraph(RealMatrix W) {
        RealMatrix W_ = createRealMatrix(this.d, this.d);
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                W_.setEntry(i, j, abs(W.getEntry(i, j)));
            }
        }

        double wThreshold = this.wThreshold;
        double wMin;

        do {
            wMin = Double.MAX_VALUE;
            for (int i = 0; i < this.d; i++) {
                for (int j = 0; j < this.d; j++) {
                    double w_ = W_.getEntry(i, j);
                    if (w_ < wThreshold) {
                        W_.setEntry(i, j, 0);
                    } else if (w_ < wMin) {
                        wMin = w_;
                    }
                }
            }
            wThreshold = wMin + 1e-6;
        } while (W_.power(this.d).getTrace() > 0);

        Graph graph = new EdgeListGraph(this.variables);
        for (int i = 0; i < this.d; i++) {
            for (int j = 0; j < this.d; j++) {
                if (W_.getEntry(i, j) == 0) continue;
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
