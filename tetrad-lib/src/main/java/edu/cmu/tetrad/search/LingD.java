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

import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.HungarianAlgorithm;
import edu.cmu.tetrad.search.utils.NRooks;
import edu.cmu.tetrad.search.utils.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * <p>Implements the ICA LiNG-D algorithm as well as a number of ancillary methods for
 * LiNG-D and LiNGAM. The reference is here:</p>
 *
 * <p>Lacerda, G., Spirtes, P. L., Ramsey, J., &amp; Hoyer, P. O. (2012). Discovering
 * cyclic causal models by independent components analysis. arXiv preprint
 * arXiv:1206.3273.</p>
 *
 * <p>We use the N Rooks algorithm to find alternative strongest diagonals for
 * permutations of the W matrix. The parameter that N Rooks requires is a
 * spine threshold, which is the lowest number in absolute value that a W matrix
 * entry can take to be part of a strongest diagonal; the implied
 * permutation is the permutation that permutes rows so that this combination
 * lies along the diagonal of W, which is then scaled, and the separate satisfactory
 * B Hat matrices reported.</p>
 *
 * <p>The B Hat matrices are further thresholded using a coefficient threshold;
 * values in B hat less than this minimum in absolute value are sent to zero
 * and will not correspond to edges in the output model.</p>
 *
 * <p>There are also a number of methods that are used in the paper, but are not
 * included in this class. These are included in the class Lingam.</p>
 *
 * <p>LiNG-D is a method for estimating a directed acyclic graph (DAG) from a
 * dataset. The graph is estimated by finding a permutation of the columns of the
 * dataset so that the resulting matrix has a strong diagonal. This permutation
 * is then used to estimate a DAG. The method is an extension of LiNGAM, which
 * estimates a DAG from a dataset using independent components analysis (ICA).
 * LiNG-D is particularly useful when the underlying data may have multiple
 * consistent cyclic models.</p>
 *
 * <p>This class is not configured to respect knowledge of forbidden and required
 * edges.</p>
 *
 * @author peterspirtes
 * @author gustavolacerda
 * @author patrickhoyer
 * @author josephramsey
 * @see Lingam
 */
public class LingD {
    private double spineThreshold = 0.5;
    private double bThreshold = 0.1;

    /**
     * Constructor.
     */
    public LingD() {
    }

    public static boolean isAcyclic(Matrix scaledBHat, List<Node> variables) {
        Graph g = makeGraph(scaledBHat, variables);
        return !g.paths().existsDirectedCycle();
    }

    /**
     * Fits a LiNG-D model to the given dataset using a default method for estimating
     * W.
     *
     * @param D A continuous dataset.
     * @return The BHat matrix, where B[i][j] gives the coefficient of j->i if nonzero.
     */
    public List<Matrix> fit(DataSet D) {
        Matrix W = LingD.estimateW(D, 5000, 1e-6, 1.2);
        return fitW(W);
    }

    /**
     * Performs the LiNG-D algorithm given a W matrix, which needs to be discovered
     * elsewhere. The 'local algorithm' is assumed--in fact, the W matrix is simply
     * thresholded without bootstrapping.
     *
     * @param W The W matrix to be used.
     * @return A list of estimated B Hat matrices generated by LiNG-D.
     */
    public List<Matrix> fitW(Matrix W) {
        List<PermutationMatrixPair> pairs = pairsNRook(W.transpose(), spineThreshold);

        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("Could not find an N Rooks solution with that threshold.");
        }

        List<Matrix> results = new ArrayList<>();

        for (PermutationMatrixPair pair : pairs) {
            Matrix bHat = LingD.getScaledBHat(pair, bThreshold);
            results.add(bHat);
        }

        return results;
    }

    /**
     * The threshold to use for set small elements to zero in the B Hat matrices.
     *
     * @param bThreshold The threshold, a non-negative number.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    /**
     * Sets the threshold used to prune the matrix for purpose of searching for alterantive
     * strong diagonals.
     *
     * @param spineThreshold The threshold, a non-negative number.
     */
    public void setSpineThreshold(double spineThreshold) {
        if (spineThreshold < 0)
            throw new IllegalArgumentException("Expecting a non-negative number: " + spineThreshold);
        this.spineThreshold = spineThreshold;
    }

    /**
     * Estimates the W matrix using FastICA. Assumes the "parallel" option, using the
     * "exp" function.
     *
     * @param data             The dataset to estimate W for.
     * @param fastIcaMaxIter   Maximum number of iterations of ICA.
     * @param fastIcaTolerance Tolerance for ICA.
     * @param fastIcaA         Alpha for ICA.
     * @return The estimated W matrix.
     */
    public static Matrix estimateW(DataSet data, int fastIcaMaxIter, double fastIcaTolerance,
                                   double fastIcaA) {
        double[][] _data = data.getDoubleData().transpose().toArray();
        TetradLogger.getInstance().forceLogMessage("Anderson Darling P-values Per Variables (p < alpha means Non-Guassian)");
        TetradLogger.getInstance().forceLogMessage("");

        for (int i = 0; i < _data.length; i++) {
            Node node = data.getVariable(i);
            AndersonDarlingTest test = new AndersonDarlingTest(_data[i]);
            double p = test.getP();
            NumberFormat nf = new DecimalFormat("0.000");
            TetradLogger.getInstance().forceLogMessage(node.getName() + ": p = " + nf.format(p));
        }

        TetradLogger.getInstance().forceLogMessage("");

        Matrix X = data.getDoubleData();
        X = DataUtils.centerData(X).transpose();
        FastIca fastIca = new FastIca(X, X.getNumRows());
        fastIca.setVerbose(false);
        fastIca.setMaxIterations(fastIcaMaxIter);
        fastIca.setAlgorithmType(FastIca.PARALLEL);
        fastIca.setTolerance(fastIcaTolerance);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setRowNorm(false);
        fastIca.setAlpha(fastIcaA);
        FastIca.IcaResult result11 = fastIca.findComponents();
        return result11.getW();
    }

    /**
     * Returns a graph given a coefficient matrix and a list of variables. It is
     * assumed that any non-zero entry in B corresponds to a directed edges, so
     * that Bij != 0 implies that j->i in the graph.
     *
     * @param B         The coefficient matrix.
     * @param variables The list of variables.
     * @return The built graph.
     */
    @NotNull
    public static Graph makeGraph(Matrix B, List<Node> variables) {
        Graph g = new EdgeListGraph(variables);

        for (int j = 0; j < B.getNumColumns(); j++) {
            for (int i = 0; i < B.getNumRows(); i++) {
                if (B.get(i, j) != 0) {
                    g.addDirectedEdge(variables.get(j), variables.get(i));
                }
            }
        }

        return g;
    }

    /**
     * Finds a column permutation of the W matrix that maximizes the sum
     * of 1 / |Wii| for diagonal elements Wii in W.
     *
     * @param W The W matrix, WX = e.
     * @return The model with the strongest diagonal, as a permutation matrix pair.
     * @see PermutationMatrixPair
     */
    public static PermutationMatrixPair hungarianDiagonal(Matrix W) {
        return hungarian(W.transpose());
    }

    /**
     * Whether the BHat matrix represents a stable model. The eigenvalues are
     * checked ot make sure they are all less than 1 in modulus.
     *
     * @param bHat The bHat matrix.
     * @return True iff the model is stable.
     */
    public static boolean isStable(Matrix bHat) {
        EigenDecomposition eigen = new EigenDecomposition(new BlockRealMatrix(bHat.toArray()));
        double[] realEigenvalues = eigen.getRealEigenvalues();
        double[] imagEigenvalues = eigen.getImagEigenvalues();

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            double modulus = sqrt(pow(realEigenvalue, 2) + pow(imagEigenvalue, 2));

            System.out.println("Modulus for eigenvalue " + (i + 1) + " = " + modulus);

            if (modulus >= 1.0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Scales the given matrix M by diving each entry (i, j) by M(j, j)
     *
     * @param M The matrix to scale.
     * @return The scaled matrix.
     */
    public static Matrix scale(Matrix M) {
        Matrix _M = M.like();

        for (int i = 0; i < _M.getNumRows(); i++) {
            for (int j = 0; j < _M.getNumColumns(); j++) {
                _M.set(i, j, M.get(i, j) / M.get(j, j));
            }
        }

        return _M;
    }

    /**
     * Thresholds the givem matrix, sending any small entries in absolute value to zero.
     *
     * @param M         The matrix to threshold.
     * @param threshold The value such that M(i, j) is set to zero if |M(i, j)| < threshold.
     *                  Should be non-negative.
     * @return The thresholded matrix.
     */
    public static Matrix threshold(Matrix M, double threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + threshold);

        Matrix _M = M.copy();

        for (int i = 0; i < M.getNumRows(); i++) {
            for (int j = 0; j < M.getNumColumns(); j++) {
                if (abs(M.get(i, j)) < threshold) _M.set(i, j, 0.0);
            }
        }

        return _M;
    }

    /**
     * Returns the BHat matrix, permuted to the variable order of the original
     * data and scaled so that the diagonal consists only of 1's.
     *
     * @param pair       The (column permutation, thresholded, column permuted W matrix)
     *                   pair.
     * @param bThreshold Valued in the BHat matrix less than this in absolute
     *                   value are set to 0.
     * @return The estimated B Hat matrix for this pair.
     * @see PermutationMatrixPair
     */
    public static Matrix getScaledBHat(PermutationMatrixPair pair, double bThreshold) {
        Matrix WTilde = pair.getPermutedMatrix().transpose();
        WTilde = LingD.scale(WTilde);
        Matrix BHat = Matrix.identity(WTilde.getNumColumns()).minus(WTilde);
        BHat = threshold(BHat, bThreshold);
        int[] perm = pair.getRowPerm();
        int[] inverse = LingD.inversePermutation(perm);
        PermutationMatrixPair inversePair = new PermutationMatrixPair(BHat, inverse, inverse);
        return inversePair.getPermutedMatrix();
    }

    @NotNull
    private static PermutationMatrixPair hungarian(Matrix W) {
        double[][] costMatrix = new double[W.getNumRows()][W.getNumColumns()];

        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                if (W.get(i, j) != 0) {
                    costMatrix[i][j] = 1.0 / abs(W.get(i, j));
                } else {
                    costMatrix[i][j] = 1000.0;
                }
            }
        }

        HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
        int[][] assignment = alg.findOptimalAssignment();

        int[] perm = new int[assignment.length];
        for (int i = 0; i < perm.length; i++) perm[i] = assignment[i][1];

        return new PermutationMatrixPair(W, perm, null);
    }

    @NotNull
    private static List<PermutationMatrixPair> pairsNRook(Matrix W, double spineThreshold) {
        boolean[][] allowablePositions = new boolean[W.getNumRows()][W.getNumColumns()];

        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                allowablePositions[i][j] = abs(W.get(i, j)) > spineThreshold;
            }
        }

        List<PermutationMatrixPair> pairs = new ArrayList<>();
        List<int[]> colPermutations = NRooks.nRooks(allowablePositions);

        for (int[] colPermutation : colPermutations) {
            pairs.add(new PermutationMatrixPair(W, null, colPermutation));
        }

        return pairs;
    }

    static int[] inversePermutation(int[] perm) {
        int[] inverse = new int[perm.length];

        for (int i = 0; i < perm.length; i++) {
            inverse[perm[i]] = i;
        }

        return inverse;
    }
}


