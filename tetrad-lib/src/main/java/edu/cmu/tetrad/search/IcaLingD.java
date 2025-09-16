///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
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
 * ICA-LiNG-D (Lacerda, Spirtes, Ramsey, Hoyer, 2012).
 *
 * Stability hardening:
 *  - Uses fixed FastICA (sym. update derivative, whitening ridge, orthonormal wInit).
 *  - Robust diagonal scaling with epsilon guard.
 *  - Prefer Hungarian "best-diagonal" permutation; then enumerate NRooks permutations.
 *  - Spectral radius stability check with small tolerance.
 *
 * API is unchanged.
 *
 * @author peterspirtes
 * @author gustavolacerda
 * @author patrickhoyer
 * @author josephramsey
 * @version $Id: $Id
 * @see IcaLingam
 */
public class IcaLingD {

    /** Entries |W_ij| < wThreshold -> 0 before permutation search. */
    private double wThreshold = 0.1;

    /** Entries |B_ij| < bThreshold -> 0 after forming B̂. */
    private double bThreshold = 0.1;

    /** Small guard used when scaling by diagonal. */
    private static final double DIAG_EPS = 1e-8;

    /** Spectral-radius tolerance for stability (rho < 1 - tol). */
    private static final double STAB_TOL = 1e-8;

    public IcaLingD() {}

    // ----------------------------------------------------------------------
    // Estimation of W via FastICA
    // ----------------------------------------------------------------------

    public static Matrix estimateW(DataSet data, int fastIcaMaxIter, double fastIcaTolerance, double fastIcaA) {
        return estimateW(data, fastIcaMaxIter, fastIcaTolerance, fastIcaA, false);
    }

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

        // ICA expects (p x N); Tetrad stores (N x p). We'll transpose internally in FastIca call context.
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

    // ----------------------------------------------------------------------
    // Graph helper
    // ----------------------------------------------------------------------

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

    // ----------------------------------------------------------------------
    // Diagonal permutation helpers
    // ----------------------------------------------------------------------

    public static PermutationMatrixPair maximizeDiagonal(Matrix W) {
        return maximizeDiagonalSum(W);
    }

    /** Spectral-radius stability with small tolerance. */
    public static boolean isStable(Matrix bHat) {
        SimpleEVD<SimpleMatrix> eig = bHat.getSimpleMatrix().eig();
        for (int i = 0; i < eig.getNumberOfEigenvalues(); i++) {
            double real = eig.getEigenvalue(i).getReal();
            double imag = eig.getEigenvalue(i).getImaginary();
            double rho = sqrt(real * real + imag * imag);
            if (rho >= 1.0 - STAB_TOL) return false;
        }
        return true;
    }

    /** Scale columns by their diagonal (guarding tiny/zero). */
    public static Matrix scale(Matrix M) {
        Matrix _M = M.like();
        for (int i = 0; i < _M.getNumRows(); i++) {
            for (int j = 0; j < _M.getNumColumns(); j++) {
                double d = M.get(j, j);
                if (abs(d) < DIAG_EPS) d = (d >= 0 ? DIAG_EPS : -DIAG_EPS);
                _M.set(i, j, M.get(i, j) / d);
            }
        }
        return _M;
    }

    /** Hard threshold (copy). */
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

    /** Build B̂ from a permutation result; robust to tiny diagonals. */
    public static Matrix getScaledBHat(PermutationMatrixPair pair) {
        Matrix WTilde = pair.getPermutedMatrix().transpose();
        WTilde = IcaLingD.scale(WTilde); // normalize diagonal to ~1
        int p = WTilde.getNumColumns();
        Matrix BHat = Matrix.identity(p).minus(WTilde);

        // Return B̂ in the original variable order by undoing the row permutation we applied to W
        int[] perm = pair.getRowPerm();
        int[] inverse = IcaLingD.inversePermutation(perm);
        PermutationMatrixPair inversePair = new PermutationMatrixPair(BHat, inverse, inverse);
        return inversePair.getPermutedMatrix();
    }

    @NotNull
    private static PermutationMatrixPair maximizeDiagonalSum(Matrix W) {
        double[][] costMatrix = new double[W.getNumRows()][W.getNumColumns()];
        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                double a = abs(W.get(i, j));
                costMatrix[i][j] = (a > 0.0) ? 1.0 / a : 1e6; // prefer larger |W_ij|
            }
        }
        HungarianAlgorithm alg = new HungarianAlgorithm(costMatrix);
        int[][] assignment = alg.findOptimalAssignment();
        int[] perm = new int[assignment.length];
        for (int i = 0; i < perm.length; i++) perm[i] = assignment[i][1];
        return new PermutationMatrixPair(W, perm, null);
    }

    @NotNull
    private static List<PermutationMatrixPair> pairsNRook(Matrix W) {
        boolean[][] allowable = new boolean[W.getNumRows()][W.getNumColumns()];
        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                allowable[i][j] = abs(W.get(i, j)) > 0;
            }
        }
        List<PermutationMatrixPair> pairs = new ArrayList<>();
        List<int[]> colPerms = NRooks.nRooks(allowable);
        for (int[] colPerm : colPerms) pairs.add(new PermutationMatrixPair(W, null, colPerm));
        return pairs;
    }

    private static int[] inversePermutation(int[] perm) {
        int[] inverse = new int[perm.length];
        for (int i = 0; i < perm.length; i++) inverse[perm[i]] = i;
        return inverse;
    }

    // ----------------------------------------------------------------------
    // Public pipeline
    // ----------------------------------------------------------------------

    /**
     * Convenience: estimate W via FastICA, then enumerate B̂ candidates.
     */
    public List<Matrix> fit(DataSet D) {
        Matrix W = IcaLingD.estimateW(D, 10000, 1e-6, 1.1, true);
        return getScaledBHats(W);
    }

    /**
     * Local LiNG-D from a given W:
     *  1) Threshold W (small entries -> 0).
     *  2) Try best-diagonal permutation (Hungarian) first; then all NRooks permutations.
     *  3) For each permutation, scale to WTilde with diag≈1, form B̂ = I - WTilde, threshold B̂.
     *  4) Return the list (caller can filter with isStable).
     */
    public List<Matrix> getScaledBHats(Matrix W) {
        // 1) Threshold W
        W = new Matrix(W);
        double wt = Math.max(0.0, this.wThreshold);
        for (int i = 0; i < W.getNumRows(); i++) {
            for (int j = 0; j < W.getNumColumns(); j++) {
                if (abs(W.get(i, j)) < wt) W.set(i, j, 0.0);
            }
        }

        // 2) Build permutation candidates: best-diagonal + NRooks set (dedup simple way)
        List<PermutationMatrixPair> pairs = new ArrayList<>();
        try {
            pairs.add(maximizeDiagonalSum(W));
        } catch (Exception ignore) {
            // fall through to NRooks only
        }
        for (PermutationMatrixPair p : pairsNRook(W)) {
            pairs.add(p);
        }
        if (pairs.isEmpty()) throw new IllegalArgumentException("Could not find an N Rooks solution with that threshold.");

        // 3) Build B̂ per permutation with robust scaling & threshold
        List<Matrix> results = new ArrayList<>();
        double bt = Math.max(0.0, this.bThreshold);

        for (PermutationMatrixPair pair : pairs) {
            Matrix bHat = IcaLingD.getScaledBHat(pair);

            // threshold B̂
            for (int i = 0; i < bHat.getNumRows(); i++) {
                for (int j = 0; j < bHat.getNumColumns(); j++) {
                    if (abs(bHat.get(i, j)) < bt) bHat.set(i, j, 0.0);
                }
            }

            results.add(bHat);
        }

        return results;
    }

    // ----------------------------------------------------------------------
    // Params
    // ----------------------------------------------------------------------

    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    public void setWThreshold(double wThreshold) {
        if (wThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + wThreshold);
        this.wThreshold = wThreshold;
    }
}