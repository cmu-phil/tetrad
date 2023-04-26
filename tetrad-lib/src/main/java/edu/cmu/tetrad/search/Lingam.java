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
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.PermutationGenerator;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.StrictMath.abs;

/**
 * <p>Implements the LiNGAM algorithm in Shimizu, Hoyer, Hyvarinen, and Kerminen, A linear
 * nongaussian acyclic model for causal discovery, JMLR 7 (2006).</p>
 *
 * @author josephramsey
 */
public class Lingam {
    private double pruneFactor = 0.3;
    private Matrix permutedBHat = null;
    private List<Node> permutedVars = null;

    //================================CONSTRUCTORS==========================//

    /**
     * Constructs a new LiNGAM algorithm with the given alpha level (used for pruning).
     */
    public Lingam() {
    }

    /**
     * Estimates the W matrix using FastICA.
     *
     * @param data             The dataset to estimate W for.
     * @param fastIcaMaxIter   Maximum number of iterations of ICA.
     * @param fastIcaTolerance Tolerance for ICA.
     * @param fastIcaA         Alpha for ICA.
     * @return The estimated W matrix.
     */
    public static Matrix estimateW(DataSet data, int fastIcaMaxIter, double fastIcaTolerance, double fastIcaA) {
        Matrix X = data.getDoubleData();
        X = DataUtils.centerData(X).transpose();
        FastIca fastIca = new FastIca(X, X.rows());
        fastIca.setVerbose(false);
        fastIca.setMaxIterations(fastIcaMaxIter);
        fastIca.setAlgorithmType(FastIca.DEFLATION);
        fastIca.setTolerance(fastIcaTolerance);
        fastIca.setFunction(FastIca.EXP);
        fastIca.setRowNorm(false);
        fastIca.setAlpha(fastIcaA);
        FastIca.IcaResult result11 = fastIca.findComponents();
        return result11.getW();
    }

    /**
     * Searches given the W matrix from ICA.
     *
     * @param W the W matrix from ICA.
     * @param variables The variables from the original dataset used to generate the W matrix,
     *                  in the order they occur in that dataset.
     * @return The graph returned.
     */
    public Graph search(Matrix W, List<Node> variables) {
        PermutationGenerator gen1 = new PermutationGenerator(W.rows());

        // The first task is to find a row permutation of the W matrix that maximizes
        // the absolute values on its diagonal. We do this by minimizing SUM(1 / |Wii|).
        int[] rowPerm = new int[0];
        double sum1 = Double.POSITIVE_INFINITY;
        int[] choice1;

        while ((choice1 = gen1.next()) != null) {
            double sum = 0.0;

            for (int j = 0; j < W.rows(); j++) {
                sum += 1.0 / abs(W.get(choice1[j], j));
            }

            if (sum < sum1) {
                sum1 = sum;
                rowPerm = Arrays.copyOf(choice1, choice1.length);
            }
        }

        // We grab the resulting diagonal-optimized matrix and scale it by divind each entry Wij
        // by Wjj. How the diagonal should consist only of 1's.
        Matrix WTilde = new PermutationMatrixPair(rowPerm, null, W).getPermutedMatrix();
        WTilde = scale(WTilde);

        // We calculate BHat as I - WTilde.
        Matrix BHat = Matrix.identity(W.columns()).minus(WTilde);

        // The second task is to rearrange the BHat matrix by permuting rows and columns
        // simultaneously so that the lower triangle is maximal--i.e., so that SUM(WTilde(i, j)^2)
        // is maximal for j > i. The goal of this is to find a causal order for the variables.
        // If all the big coefficients are in the lower triangle, we can interpret it as a
        // DAG model. We will ignore any big coefficients left over in the upper triangle.
        // We will assume the diagonal of the BHat matrix is zero--i.e., no self-loops.
        PermutationGenerator gen2 = new PermutationGenerator(BHat.rows());
        int[] perm = new int[0];
        double sum2 = Double.NEGATIVE_INFINITY;
        int[] choice2;

        while ((choice2 = gen2.next()) != null) {
            double sum = 0.0;

            for (int i = 0; i < W.rows(); i++) {
                for (int j = 0; j < i; j++) {
                    double b = BHat.get(choice2[i], choice2[j]);
                    sum += b * b;
                }
            }

            if (sum > sum2) {
                sum2 = sum;
                perm = Arrays.copyOf(choice2, choice2.length);
            }
        }

        // Grab that lower-triangle maximized version of the BHat matrix.
        Matrix BHatTilde = new PermutationMatrixPair(perm, perm, BHat).getPermutedMatrix();

        // Set the upper triangle now to zero, since we are ignoring it for this DAG algorithm.
        for (int i = 0; i < BHatTilde.rows(); i++) {
            for (int j = i + 1; j < BHatTilde.columns(); j++) {
                BHatTilde.set(i, j, 0.0);
            }
        }


        // Permute the variables too for that order.
        List<Node> varPerm = new ArrayList<>();
        for (int k : perm) varPerm.add(variables.get(k));

        // Grab the permuted BHat and variables.
        this.permutedBHat = BHatTilde;
        this.permutedVars = varPerm;

        // Make the graph and return it.
        Graph g = new EdgeListGraph(varPerm);

        for (int j = 0; j < getPermutedBHat().columns(); j++) {
            for (int i = j + 1; i < getPermutedBHat().rows(); i++) {
                if (abs(getPermutedBHat().get(i, j)) > pruneFactor) {
                    g.addDirectedEdge(getPermutedVars().get(j), getPermutedVars().get(i));
                }
            }
        }

        return g;
    }

    /**
     * Scares the given matrix M by diving each entry (i, j) by M(j, j)
     *
     * @param M The matrix to scale.
     * @return The scaled matrix.
     */
    public static Matrix scale(Matrix M) {
        Matrix _M = M.like();

        for (int i = 0; i < _M.rows(); i++) {
            for (int j = 0; j < _M.columns(); j++) {
                _M.set(i, j, M.get(i, j) / M.get(j, j));
            }
        }

        return _M;
    }

    /**
     * Thresholds the givem matrix, sending any small entries to zero.
     *
     * @param M         The matrix to threshold.
     * @param threshold The value such that M(i, j) is set to zero if |M(i, j)| < threshold.
     * @return The thresholded matrix.
     */
    public static Matrix threshold(Matrix M, double threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + threshold);

        Matrix _M = M.copy();

        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.columns(); j++) {
                if (FastMath.abs(M.get(i, j)) < threshold) _M.set(i, j, 0.0);
            }
        }

        return _M;
    }

    /**
     * The threshold to use for estimated B Hat matrices for the LiNGAM algorithm.
     * @param pruneFactor Some value >= 0.
     */
    public void setPruneFactor(double pruneFactor) {
        if (pruneFactor < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + pruneFactor);
        this.pruneFactor = pruneFactor;
    }

    /**
     * After search the permuted BHat matrix can be retrieved using this method.
     * @return The permutated (lower triangle) BHat matrix. Here, BHat(i, j) != 0 means that
     * there is an edge vars(j)-->vars(i) in the graph, where 'vars' means the permuted variables.
     */
    public Matrix getPermutedBHat() {
        return permutedBHat;
    }

    /**
     * The permuted variables of the graph. This is the estimated causal order of the models.
     * @return This list of variables.
     */
    public List<Node> getPermutedVars() {
        return permutedVars;
    }
}

