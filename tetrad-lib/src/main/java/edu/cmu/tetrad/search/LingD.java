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

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.pow;

/**
 * Lacerda, G., Spirtes, P. L., Ramsey, J., & Hoyer, P. O. (2012). Discovering
 * cyclic causal models by independent components analysis. arXiv preprint
 * arXiv:1206.3273.
 *
 * @author josephramsey
 */
public class LingD {

    /**
     * Constructor. The W matrix needs to be estimated separately (e.g., using
     * the Lingam.estimateW(.) method using the ICA method in Tetrad, or some
     * method in Python or R) and passed into the search(W) method.
     */
    public LingD() {
    }

    /**
     * Searches given the W matrix.
     *
     * @param W the W matrix.
     * @param wThreshold The threshold below which entries in the W matrix are send to
     *                   zero.
     * @return the LiNGAM graph.
     */
    public List<PermutationMatrixPair> search(Matrix W, double wThreshold) {
        return nRooks(threshold(W, wThreshold));
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

    public static List<PermutationMatrixPair> nRooks(Matrix W) {
        List<PermutationMatrixPair> pairs = new ArrayList<>();

        System.out.println("\nThresholded W = \n" + W);

        //returns all zeroless-diagonal column-pairs
        boolean[][] allowablePositions = new boolean[W.rows()][W.columns()];

        for (int i = 0; i < W.rows(); i++) {
            for (int j = 0; j < W.columns(); j++) {
                allowablePositions[i][j] = W.get(i, j) != 0;
            }
        }

        printAllowablePositions(W, allowablePositions);

        List<int[]> colPermutations = NRooks.nRooks(allowablePositions);

        //for each assignment, add the corresponding permutation to 'pairs'
        for (int[] colPermutation : colPermutations) {
            pairs.add(new PermutationMatrixPair(W, null, colPermutation));
        }

        return pairs;
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
                if (abs(M.get(i, j)) < threshold) _M.set(i, j, 0.0);
            }
        }

        return _M;
    }

    /**
     * Returns the BHat matrix, permuted to causal order (lower triangle) and
     * scaled so that the diagonal consists only of 1's.
     * @param pair The (column permutation, thresholded, column permuted W matrix)
     *             pair.
     * @return The estimated B Hat matrix for this pair.
     */
    public static Matrix getPermutedScaledBHat(PermutationMatrixPair pair) {
        Matrix _w = pair.getPermutedMatrix();
        Matrix bHat = Matrix.identity(_w.rows()).minus(_w);
        return scale(bHat);
    }

    /**
     * Returns the thresholded W matrix, permuted to causal order (lower triangle),
     * unscaled.
     * @param pair The (column permutation, thresholded, column permuted W matrix)
     *             pair.
     * @return The thresholded W matrix for this pair.
     */
    public static Matrix getPermutedThresholdedW(PermutationMatrixPair pair) {
        return pair.getPermutedMatrix();
    }

    /**
     * Returns the BHat matrix, permuted to causal order (lower triangle) and
     * unscaled.
     * @param pair The (column permutation, thresholded, column permuted W matrix)
     *             pair.
     * @return The estimated B Hat matrix for this pair.
     */
    public static Matrix getPermutedUnscaledBHat(PermutationMatrixPair pair) {
        Matrix _w = pair.getPermutedMatrix();
        return Matrix.identity(_w.rows()).minus(_w);
    }

    public static List<Node> getPermutedVariables(PermutationMatrixPair pair,
            List<Node> variables) {
        int[] perm = pair.getColPerm();

        List<Node> permVars = new ArrayList<>();

        for (int i = 0; i < variables.size(); i++) {
            permVars.add(variables.get(perm[i]));
        }

        return permVars;
    }

    /**
     * Returns the estimated graph for the given column permutation of the
     * thresholded W matrix, with self-loops. (We are assuming for purposes of
     * the LiNG-D algorithm that all variables have self-loops.)
     * @param pair The (column permutation, thresholded, column permuted W matrix)
     *        pair.
     * @param variables The variables in the order in which they occur in the
     *                  original dataset being analyzed.
     * @return The estimated graph for this pair.
     */
    public static Graph getGraph(PermutationMatrixPair pair, List<Node> variables) {
        List<Node> permVars = getPermutedVariables(pair, variables);

        Matrix bHat = getPermutedScaledBHat(pair);
        Graph graph = new EdgeListGraph(permVars);

        for (int i = 0; i < permVars.size(); i++) {
            for (int j = 0; j < permVars.size(); j++) {
                if (bHat.get(j, i) != 0) {
                    graph.addDirectedEdge(permVars.get(i), permVars.get(j));
                }
            }
        }

        return graph;
    }


    private static void printAllowablePositions(Matrix W, boolean[][] allowablePositions) {
        System.out.println("\nAllowable rook positions");

        // Print allowable board.
        for (int i = 0; i < W.rows(); i++) {
            System.out.println();
            for (int j = 0; j < W.columns(); j++) {
                System.out.print((allowablePositions[i][j] ? 1 : 0) + " ");
            }
        }

        System.out.println();
        System.out.println();
    }
}


