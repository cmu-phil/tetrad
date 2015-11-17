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

package edu.cmu.tetrad.search.kernel;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.List;

/**
 * Various kernel utilities
 *
 * @author Robert Tillman
 */
public class KernelUtils {

    /**
     * Constructs Gram matrix for a given vector valued sample. The set of kernels corresponds to the variables in the
     * set. The output matrix is the tensor product of Gram matrices for each variable.
     *
     * @param kernels the kernels for each variable
     * @param dataset the dataset containing each variable
     * @param nodes   the variables to construct the Gram matrix for
     * @return
     */
    public static TetradMatrix constructGramMatrix(List<Kernel> kernels, DataSet dataset, List<Node> nodes) {
        int m = dataset.getNumRows();
        TetradMatrix gram = new TetradMatrix(m, m);
        for (int k = 0; k < nodes.size(); k++) {
            Node node = nodes.get(k);
            int col = dataset.getColumn(node);
            Kernel kernel = kernels.get(k);
            for (int i = 0; i < m; i++) {
                for (int j = i; j < m; j++) {
                    double keval = kernel.eval(dataset.getDouble(i, col), dataset.getDouble(j, col));
                    if (k == 0) {
                        gram.set(i, j, keval);
                    } else {
                        keval *= gram.get(i, j);
                        gram.set(i, j, keval);
                    }
                }
            }
        }
        return gram;
    }

    /**
     * Constructs the centralized Gram matrix for a given vector valued sample. The set of kernels corresponds to the
     * variables in the set. The output matrix is the tensor product of Gram matrices for each variable.
     *
     * @param kernels the kernels for each variable
     * @param dataset the dataset containing each variable
     * @param nodes   the variables to construct the Gram matrix for
     * @return
     */
    public static TetradMatrix constructCentralizedGramMatrix(List<Kernel> kernels, DataSet dataset, List<Node> nodes) {
        int m = dataset.getNumRows();
        TetradMatrix gram = constructGramMatrix(kernels, dataset, nodes);
        TetradMatrix H = constructH(m);
        TetradMatrix KH = gram.times(H);
        TetradMatrix HKH = H.times(KH);
        return HKH;
    }

    /**
     * Constructs the projection matrix on 1/m
     *
     * @param m the sample size
     * @return
     */
    public static TetradMatrix constructH(int m) {
        TetradMatrix H = new TetradMatrix(m, m);
        double od = -1.0 / (double) m;
        double d = od + 1;
        for (int i = 0; i < m; i++) {
            for (int j = i; j < m; j++) {
                if (i == j) {
                    H.set(i, j, d);
                } else {
                    H.set(i, j, od);
                }
            }
        }
        return H;
    }

    /**
     * Approximates Gram matrix using incomplete Cholesky factorization
     *
     * @param kernels the kernels for each variable
     * @param dataset the dataset containing each variable
     * @param nodes   the variables to construct the Gram matrix for
     * @return
     */
    public static TetradMatrix incompleteCholeskyGramMatrix(List<Kernel> kernels, DataSet dataset, List<Node> nodes, double precision) {
        if (precision <= 0) {
            throw new IllegalArgumentException("Precision must be > 0");
        }

        int m = dataset.getNumRows();
        TetradMatrix G = new TetradMatrix(m, m);

        // get diagonal of Gram matrix and initialize permutation vector
        double[] Dadv = new double[m];
        int[] p = new int[m];
        for (int i = 0; i < m; i++) {
            Dadv[i] = evaluate(kernels, dataset, nodes, i, i);
            p[i] = i;
        }

        // loop through top diagonal elements until precision is met
        int cols = m;
        for (int k = 0; k < m; k++) {

            // find best element
            double best = Dadv[k];
            int bestInd = k;
            for (int j = (k + 1); j < m; j++) {
                if (Dadv[j] > best / .99) {
                    best = Dadv[j];
                    bestInd = j;
                }
            }

            // exit if best element does not exceed precision
            if (best < precision) {
                cols = k - 1;
                break;
            }

            // permute best vector and k
            int pk = p[k];
            p[k] = p[bestInd];
            p[bestInd] = pk;
            double dk = Dadv[k];
            Dadv[k] = Dadv[bestInd];
            Dadv[bestInd] = dk;
            for (int j = 0; j < k; j++) {
                double gk = G.get(k, j);
                G.set(k, j, G.get(bestInd, j));
                G.set(bestInd, j, gk);
            }

            // compute next column
            double diag = Math.sqrt(Dadv[k]);
            G.set(k, k, diag);
            for (int j = (k + 1); j < m; j++) {
                double s = 0.0;
                for (int i = 0; i < k; i++) {
                    s += G.get(j, i) * G.get(k, i);
                }
                G.set(j, k, (evaluate(kernels, dataset, nodes, p[j], p[k]) - s) / diag);
            }

            // update diagonal
            for (int j = (k + 1); j < m; j++) {
                Dadv[j] -= Math.pow(G.get(j, k), 2);
            }
            Dadv[k] = 0;
        }

        // trim columns
        TetradMatrix Gm = new TetradMatrix(m, cols);
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < cols; j++) {
                Gm.set(i, j, G.get(i, j));
            }
        }
        return Gm;
    }

    // evaluates tensor product for kernels

    private static double evaluate(List<Kernel> kernels, DataSet dataset, List<Node> vars, int i, int j) {
        int col = dataset.getColumn(vars.get(0));
        double keval = kernels.get(0).eval(dataset.getDouble(i, col), dataset.getDouble(j, col));
        for (int k = 1; k < vars.size(); k++) {
            col = dataset.getColumn(vars.get(k));
            keval *= kernels.get(k).eval(dataset.getDouble(i, col), dataset.getDouble(j, col));
        }
        return keval;
    }

    // computes the trace

    private static double trace(TetradMatrix A, int m) {
        double trace = 0.0;
        for (int i = 0; i < m; i++) {
            trace += A.get(i, i);
        }
        return trace;
    }


}



