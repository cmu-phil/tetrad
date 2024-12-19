/// ////////////////////////////////////////////////////////////////////////////
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
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.HungarianAlgorithm;
import edu.cmu.tetrad.search.utils.NRooks;
import edu.cmu.tetrad.search.utils.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements the ICA-LiNG-D algorithm as well as a some auxiliary methods for ICA-LiNG-D and ICA-LiNGAM. The reference
 * is here:
 * <p>
 * Lacerda, G., Spirtes, P. L., Ramsey, J., &amp; Hoyer, P. O. (2012). Discovering cyclic causal models by independent
 * components analysis. arXiv preprint arXiv:1206.3273.
 * <p>
 * ICA-LING-D is a method for estimating a possible cyclic linear models graph from a dataset. It is based on the
 * assumption that the data are generated by a linear model with non-Gaussian noise. The method is based on the
 * following assumptions: (1) The data are generated by a linear model with non-Gaussian noise. (2) The noise is
 * independent across variables. (3) The noises for all but possibly one variable are non-Gaussian. (4) There is no
 * unobserved confounding.
 * <p>
 * Under these assumptions, the method estimates matrices W such that WX = e, where X is the data matrix, e is a matrix
 * of noise, and W is a matrix of coefficients. The matrix W is then used to estimate a matrix B Hat, where B Hat is the
 * matrix of coefficients in the linear model that generated the data. The graph is then estimated by finding edges in B
 * Hat.
 * <p>
 * We use the N Rooks algorithm to find alternative diagonals for permutations of the W matrix. The parameter that N
 * Rooks requires is a threshold for entries in W to be sent to zero; the implied permutations is the permutations that
 * permutes rows so that these combinations lie along their respective diagonals in W, which are then scaled, and the
 * separate satisfactory B Hat matrices reported. These B Hat matrices are then thresholded as well, using a separate
 * threshold for B matrices. Unlike ICA-LiNGAM, an acyclic model is not assumed.
 * <p>
 * If the verbose flag is set to true ('Yes'), all stable and unstable models are printed to the console with both their
 * B Hat matrices and graphs. If a stable model is found, it is returned; otherwise, an empty graph is returned.
 * <p>
 * This class does not use knowledge of forbidden and required edges.
 *
 * @author peterspirtes
 * @author gustavolacerda
 * @author patrickhoyer
 * @author josephramsey
 * @version $Id: $Id
 * @see IcaLingam
 */
public class IcaLingD {

    /**
     * The wThreshold variable represents the threshold value for the W matrix. Small W values in absolute value are
     * sent to zero.
     */
    private double wThreshold = 0.1;

    /**
     * Represents the threshold value for the B matrix. The B matrix is thresholded by setting any small entries in
     * absolute value to zero.
     *
     * @see IcaLingD
     */
    private double bThreshold = 0.1;

    /**
     * Constructor.
     */
    public IcaLingD() {
    }

    /**
     * Estimates the W matrix using FastICA. Assumes the "parallel" option, using the "exp" function.
     *
     * @param data             The dataset to estimate W for.
     * @param fastIcaMaxIter   Maximum number of iterations of ICA.
     * @param fastIcaTolerance Tolerance for ICA.
     * @param fastIcaA         Alpha for ICA.
     * @return The estimated W matrix.
     */
    public static Matrix estimateW(DataSet data, int fastIcaMaxIter, double fastIcaTolerance, double fastIcaA) {
        return estimateW(data, fastIcaMaxIter, fastIcaTolerance, fastIcaA, false);
    }

    /**
     * Estimates the W matrix using FastICA. Assumes the "parallel" option, using the "exp" function.
     *
     * @param data             The dataset to estimate W for.
     * @param fastIcaMaxIter   Maximum number of iterations of ICA.
     * @param fastIcaTolerance Tolerance for ICA.
     * @param fastIcaA         Alpha for ICA.
     * @param verbose          Whether to print the Anderson-Darling test results.
     * @return The estimated W matrix.
     */
    public static Matrix estimateW(DataSet data, int fastIcaMaxIter, double fastIcaTolerance, double fastIcaA, boolean verbose) {
        double[][] _data = data.getDoubleData().transpose().toArray();

        if (verbose) {
            TetradLogger.getInstance().log("Anderson Darling P-values Per Variables (p < alpha means Non-Gaussian)");
            TetradLogger.getInstance().log("");

            for (int i = 0; i < _data.length; i++) {
                Node node = data.getVariable(i);
                AndersonDarlingTest test = new AndersonDarlingTest(_data[i]);
                double p = test.getP();
                NumberFormat nf = new DecimalFormat("0.000");
                TetradLogger.getInstance().log(node.getName() + ": p = " + nf.format(p));
            }
        }

        // Please note, the ICA algorithm uses data formatted as p x N where p is the number of variables and N is
        // the sample size. Since Tetrad otherwise uses N x p, we need to transpose the data and then transpose
        // the W matrix back at the end to adjust.
        Matrix X = data.getDoubleData();
        X = DataTransforms.centerData(X).transpose();
        FastIca fastIca = new FastIca(X, X.getNumRows());
        fastIca.setVerbose(false);
        fastIca.setMaxIterations(fastIcaMaxIter);
        fastIca.setAlgorithmType(FastIca.PARALLEL);
        fastIca.setTolerance(fastIcaTolerance);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setRowNorm(false);
        fastIca.setAlpha(fastIcaA);
        FastIca.IcaResult result = fastIca.findComponents();

        return result.getW().transpose();
    }

    /**
     * Returns a graph given a coefficient matrix and a list of variables. It is assumed that any non-zero entry in B
     * corresponds to a directed edges, so that Bij != 0 implies that j->i in the graph.
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
     * Finds a column permutation of the W matrix that maximizes the sum of 1 / |Wii| for diagonal elements Wii in W.
     *
     * @param W The W matrix, WX = e.
     * @return The model with the strongest diagonal, as a permutation matrix pair.
     * @see PermutationMatrixPair
     */
    public static PermutationMatrixPair maximizeDiagonal(Matrix W) {
        return maximizeDiagonalSum(W);
    }

    /**
     * Whether the BHat matrix represents a stable model. The eigenvalues are checked to make sure they are all less
     * than 1 in modulus.
     *
     * @param bHat The bHat matrix.
     * @return True iff the model is stable.
     */
    public static boolean isStable(Matrix bHat) {
        SimpleEVD<SimpleMatrix> eig = bHat.getDataCopy().eig();

        for (int i = 0; i < eig.getNumberOfEigenvalues(); i++) {
            double realEigenvalue = eig.getEigenvalue(i).getReal();
            double imagEigenvalue = eig.getEigenvalue(i).getImaginary();
            double modulus = sqrt(pow(realEigenvalue, 2) + pow(imagEigenvalue, 2));

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
     * Thresholds the given matrix, sending any small entries in absolute value to zero.
     *
     * @param M         The matrix to threshold.
     * @param threshold The value such that M(i, j) is set to zero if |M(i, j)| &lt; threshold. Should be non-negative.
     * @return The thresholded matrix.
     */
    public static Matrix threshold(Matrix M, double threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + threshold);

        Matrix _M = M.copy();

        for (int i = 0; i < M.getNumRows(); i++) {
            for (int j = 0; j < M.getNumColumns(); j++) {
                if (abs(M.get(i, j)) < abs(threshold)) _M.set(i, j, 0.0);
            }
        }

        return _M;
    }

    /**
     * Returns the BHat matrix, permuted to the variable order of the original data and scaled so that the diagonal
     * consists only of 1's.
     *
     * @param pair The (column permutation, thresholded, column permuted W matrix) pair.
     * @return The estimated B Hat matrix for this pair.
     * @see PermutationMatrixPair
     */
    public static Matrix getScaledBHat(PermutationMatrixPair pair) {
        Matrix WTilde = pair.getPermutedMatrix().transpose();
        WTilde = IcaLingD.scale(WTilde);
        int p = WTilde.getNumColumns();
        Matrix BHat = Matrix.identity(p).minus(WTilde);

        int[] perm = pair.getRowPerm();
        int[] inverse = IcaLingD.inversePermutation(perm);

        PermutationMatrixPair inversePair = new PermutationMatrixPair(BHat, inverse, inverse);
        return inversePair.getPermutedMatrix();
    }

    /**
     * Finds a column permutation of the W matrix that maximizes the sum of 1 / |Wii| for diagonal elements Wii in W.
     *
     * @param W The W matrix.
     * @return The model with the strongest diagonal, as a permutation matrix pair.
     */
    @NotNull
    private static PermutationMatrixPair maximizeDiagonalSum(Matrix W) {
        double[][] costMatrix = new double[W.getNumRows()][W.getNumColumns()];

        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                if (W.get(i, j) != 0) {
                    costMatrix[i][j] = 1.0 / abs(W.get(i, j));
                } else {
                    costMatrix[i][j] = 1000000.0;
                }
            }
        }

        HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
        int[][] assignment = alg.findOptimalAssignment();

        int[] perm = new int[assignment.length];
        for (int i = 0; i < perm.length; i++) perm[i] = assignment[i][1];

        return new PermutationMatrixPair(W, perm, null);
    }

    /**
     * Generates a list of PermutationMatrixPairs by finding all possible column permutations of the input matrix W.
     * Each PermutationMatrixPair has a column permutation and the original matrix W.
     *
     * @param W The input matrix.
     * @return A list of PermutationMatrixPairs.
     */
    @NotNull
    private static List<PermutationMatrixPair> pairsNRook(Matrix W) {
        boolean[][] allowablePositions = new boolean[W.getNumRows()][W.getNumColumns()];

        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                allowablePositions[i][j] = abs(W.get(i, j)) > 0;
            }
        }

        List<PermutationMatrixPair> pairs = new ArrayList<>();
        List<int[]> colPerms = NRooks.nRooks(allowablePositions);

        for (int[] colPerm : colPerms) {
            pairs.add(new PermutationMatrixPair(W, null, colPerm));
        }

        return pairs;
    }

    /**
     * Calculates the inverse permutation of the given permutation.
     *
     * @param perm The permutation array.
     * @return The inverse permutation array.
     */
    private static int[] inversePermutation(int[] perm) {
        int[] inverse = new int[perm.length];

        for (int i = 0; i < perm.length; i++) {
            inverse[perm[i]] = i;
        }

        return inverse;
    }

    /**
     * Fits a LiNG-D model to the given dataset using a default method for estimating W.
     *
     * @param D A continuous dataset.
     * @return The BHat matrix, where B[i][j] gives the coefficient of j->i if nonzero.
     */
    public List<Matrix> fit(DataSet D) {
        Matrix W = IcaLingD.estimateW(D, 10000, 1e-6, 1.1, true);
        return getScaledBHats(W);
    }

    /**
     * Performs the LiNG-D algorithm given a W matrix, which needs to be discovered elsewhere. The 'local algorithm' is
     * assumed--in fact, the W matrix is simply thresholded without bootstrapping.
     *
     * @param W The W matrix to be used.
     * @return A list of estimated B Hat matrices generated by LiNG-D.
     */
    public List<Matrix> getScaledBHats(Matrix W) {
        W = new Matrix(W);

        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                if (abs(W.get(i, j)) < wThreshold) {
                    W.set(i, j, 0);
                }
            }
        }

        List<PermutationMatrixPair> pairs = pairsNRook(W);

        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("Could not find an N Rooks solution with that threshold.");
        }

        List<Matrix> results = new ArrayList<>();

        for (PermutationMatrixPair pair : pairs) {
            Matrix bHat = IcaLingD.getScaledBHat(pair);

            for (int i = 0; i < bHat.getNumRows(); i++) {
                for (int j = 0; j < bHat.getNumColumns(); j++) {
                    if (abs(bHat.get(i, j)) < bThreshold) bHat.set(i, j, 0.0);
                }
            }

            results.add(bHat);
        }

        return results;
    }

    /**
     * Sets the threshold value for the B matrix.
     *
     * @param bThreshold The threshold value for the bThreshold field. Must be non-negative.
     * @throws IllegalArgumentException If bThreshold is a negative number.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    /**
     * Sets the threshold value for the W matrix.
     *
     * @param wThreshold The threshold value for the wThreshold field. Must be non-negative.
     * @throws IllegalArgumentException If wThreshold is a negative number.
     */
    public void setWThreshold(double wThreshold) {
        if (wThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + wThreshold);
        this.wThreshold = wThreshold;
    }
}


