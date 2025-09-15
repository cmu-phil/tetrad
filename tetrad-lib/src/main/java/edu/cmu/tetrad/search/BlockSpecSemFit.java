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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.*;

/**
 * Class BlockSpecSemFit.
 * <p>
 * BlockSpec-based measurement fit allowing rank r_i >= 1 per block.
 * <p>
 * Purpose: given (i) a clustering of indicators (BlockSpec.blocks()), (ii) per-block ranks (BlockSpec.ranks(), aligned
 * with blocks()), and (iii) a DataSet, this class fits a linear multi-factor-per-block measurement model and returns a
 * chi-square style p-value assessing the goodness-of-fit of that *entire BlockSpec* to the observed covariance.
 * <p>
 * Interpretation: the p-value answers âAre these clusters with these ranks plausible for this data (up to sampling
 * error)?â  This is complementary to structure learning over blocks (e.g., CPC + IndTestBlocksTs), which does not
 * require this fit.
 * <p>
 * Per block i: - choose r_i "anchor" indicators (first r_i by the block order), - enforce a lower-triangular r_i x r_i
 * top submatrix of loadings with positive diagonal, - remaining rows free, - unique variances positive, - latent
 * covariance Î£_L (dimension R = sum_i r_i) parameterized via Cholesky to stay PD.
 * <p>
 * Objective (scale-free): 0.5 * || I - (Î Î£_L Î' + Î¨) S^{-1} ||_F^2
 * <p>
 * NOTE: Use this for BlockSpec fit diagnostics. For learning a latent graph over blocks from rank constraints, prefer
 * CPC + IndTestBlocksTs directly (no optimization needed).
 */
public final class BlockSpecSemFit {

    // ---- Inputs
    private final BlockSpec spec;
    private final DataSet data;

    // ---- BlockSpec snapshots
    private final List<List<Integer>> blocks; // columns per block (disjoint)
    private final List<Node> blockLatents;    // one node per block
    private final List<Integer> ranksList;    // r_i per block, aligned with blocks()

    // ---- Data covariance (restricted to used columns)
    private final ICovarianceMatrix measuresCov;
    private final Matrix S;
    private final Matrix S_inv;

    // ---- Shapes
    private final int B;            // #blocks
    private final int[] m;          // m_i per block
    private final int[] r;          // r_i per block
    private final int M;            // total indicators = sum m_i
    private final int R;            // total latent dim = sum r_i
    private final int[][] anchors;  // chosen anchor row indices within each block (size r_i)
    private final int[][] nonAnch;  // remaining row indices within each block (size m_i - r_i)

    // ---- Fitted params
    private Matrix Lambda;          // (M x R), block-structured
    private double[] PsiDiag;       // (M), uniques
    private Matrix SigmaL;          // (R x R), latent covariance
    private double minObj;          // objective minimum
    private double pValue;          // heuristic chi^2 p-value

    public BlockSpecSemFit(BlockSpec spec) {
        if (spec == null) throw new IllegalArgumentException("BlockSpec is null");
        this.spec = spec;
        this.data = spec.dataSet();

        this.blocks = new ArrayList<>(spec.blocks());
        this.blockLatents = new ArrayList<>(spec.blockVariables());
        this.B = blocks.size();
        if (B == 0) throw new IllegalArgumentException("No blocks in BlockSpec.");

        // ranks() is a List<Integer> aligned with blocks()
        List<Integer> rk = spec.ranks();
        if (rk == null) {
            this.ranksList = new ArrayList<>(Collections.nCopies(B, 1));
        } else {
            if (rk.size() != B) {
                throw new IllegalArgumentException("ranks().size() != blocks().size(): " + rk.size() + " vs " + B);
            }
            this.ranksList = new ArrayList<>(rk);
            for (int i = 0; i < B; i++) {
                if (this.ranksList.get(i) == null || this.ranksList.get(i) < 1) {
                    this.ranksList.set(i, 1);
                }
            }
        }

        // Validate blocks and compute sizes
        this.m = new int[B];
        this.r = new int[B];
        int sumM = 0, sumR = 0;
        int nCols = data.getNumColumns();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < B; i++) {
            List<Integer> bi = blocks.get(i);
            if (bi == null || bi.isEmpty()) throw new IllegalArgumentException("Empty block " + i);
            for (int c : bi) {
                if (c < 0 || c >= nCols) throw new IllegalArgumentException("Block " + i + " bad col " + c);
                if (!seen.add(c)) throw new IllegalArgumentException("Blocks must be disjoint; repeated col " + c);
            }
            m[i] = bi.size();
            r[i] = ranksList.get(i);
            if (r[i] > m[i]) throw new IllegalArgumentException("rank r_i > m_i for block " + i);
            if (m[i] < 2 * r[i] + 1) {
                // Sufficient (not necessary) rule-of-thumb; warn only.
                System.err.println("[WARN] Block " + i + " has m_i=" + m[i] + " < 2r_i+1=" + (2 * r[i] + 1) + "; identifiability may be weak.");
            }
            sumM += m[i];
            sumR += r[i];
        }
        this.M = sumM;
        this.R = sumR;

        // Choose anchors: first r_i rows by block order
        this.anchors = new int[B][];
        this.nonAnch = new int[B][];
        for (int i = 0; i < B; i++) {
            int ri = r[i], mi = m[i];
            int[] A = new int[ri];
            int[] N = new int[mi - ri];
            for (int t = 0; t < ri; t++) A[t] = t;
            for (int t = 0; t < mi - ri; t++) N[t] = ri + t;
            anchors[i] = A;
            nonAnch[i] = N;
        }

        // Build measures covariance restricted to used cols (preserve block order)
        List<String> names = new ArrayList<>(M);
        for (List<Integer> bi : blocks) {
            for (int c : bi) names.add(data.getVariable(c).getName());
        }
        ICovarianceMatrix full = new CovarianceMatrix(data);
        this.measuresCov = full.getSubmatrix(names);
        this.S = measuresCov.getMatrix();
        this.S_inv = S.inverse();
    }

    /**
     * Fit (Î, Î¨, Î£_L) under triangular constraints and return the BlockSpec goodness-of-fit p-value. Also exposes
     * objective via getObjective() and fitted params via getters below.
     */
    public double fit() {
        // Initialize params
        this.Lambda = new Matrix(M, R);
        this.PsiDiag = new double[M];
        this.SigmaL = Matrix.identity(R);

        // Simple init: triangular blocks ~ identity; small off-diagonals; uniques 10% diag
        int row = 0, colBase = 0;
        for (int i = 0; i < B; i++) {
            int mi = m[i], ri = r[i];
            for (int a = 0; a < ri; a++) {
                for (int b = 0; b <= a; b++) {
                    Lambda.set(row + a, colBase + b, (a == b) ? 1.0 : 0.1);
                }
            }
            for (int nr = 0; nr < mi - ri; nr++) {
                for (int b = 0; b < ri; b++) Lambda.set(row + ri + nr, colBase + b, 0.1);
            }
            for (int u = 0; u < mi; u++) {
                PsiDiag[row + u] = Math.max(1e-6, 0.1 * S.get(row + u, row + u));
            }
            row += mi;
            colBase += ri;
        }

        // Optimize
        double[] theta0 = packAll(Lambda, PsiDiag, SigmaL);
        PowellOptimizer opt = new PowellOptimizer(1e-7, 1e-7);
        PointValuePair sol = opt.optimize(
                new InitialGuess(theta0),
                new ObjectiveFunction(new Obj()),
                GoalType.MINIMIZE,
                new MaxEval(200000)
        );
        this.minObj = sol.getValue();
        unpackAll(sol.getPoint(), Lambda, PsiDiag, SigmaL);

        // Heuristic chi^2 p-value
        int p = M;
        int n = measuresCov.getSampleSize();
        int df = p * (p + 1) / 2 - theta0.length;
        if (df < 1) df = 1;
        double x = (n - 1) * minObj;
        ChiSquaredDistribution chi = new ChiSquaredDistribution(df);
        this.pValue = (Double.isFinite(x) ? 1.0 - chi.cumulativeProbability(x) : 0.0);
        return pValue;
    }

    // ---- Implied covariance
    private Matrix impliedCov(Matrix Lambda, double[] psi, Matrix SigmaL) {
        Matrix implied = Lambda.times(SigmaL).times(Lambda.transpose());
        for (int i = 0; i < M; i++) implied.set(i, i, implied.get(i, i) + Math.max(1e-12, psi[i]));
        return implied;
    }

    private int countParams() {
        int count = 0;
        for (int i = 0; i < B; i++) {
            int ri = r[i], mi = m[i];
            count += ri + (ri * (ri - 1)) / 2;  // triangular block
            count += (mi - ri) * ri;            // free rows
        }
        count += M;                             // uniques
        count += (R * (R + 1)) / 2;             // Cholesky upper
        return count;
    }

    // ---- Parameterization with constraints ---------------------------------
    // Ordering of params:
    //  For each block i:
    //    - triangular T_i (r_i x r_i) with positive diag: store diag logs + strict-lower entries
    //    - free part B_i ((m_i - r_i) x r_i)
    //  uniques: log-params so psi_j = exp(u_j) > 0
    //  Sigma_L: upper-Cholesky U (diag via logs), then Î£_L = U^T U

    private double[] packAll(Matrix Lambda, double[] psi, Matrix SigmaL) {
        double[] v = new double[countParams()];
        int t = 0, rowBase = 0, colBase = 0;

        for (int i = 0; i < B; i++) {
            int ri = r[i], mi = m[i];

            // top ri x ri (lower-tri), diag in logs
            for (int a = 0; a < ri; a++) {
                double diag = Lambda.get(rowBase + a, colBase + a);
                v[t++] = Math.log(Math.max(1e-12, diag));
                for (int b = 0; b < a; b++) v[t++] = Lambda.get(rowBase + a, colBase + b);
            }
            // free rows
            for (int nr = 0; nr < mi - ri; nr++) {
                for (int b = 0; b < ri; b++) v[t++] = Lambda.get(rowBase + ri + nr, colBase + b);
            }

            rowBase += mi;
            colBase += ri;
        }

        for (int i = 0; i < M; i++) v[t++] = Math.log(Math.max(1e-12, psi[i]));

        Matrix U = cholUpper(SigmaL);
        for (int i = 0; i < R; i++) {
            for (int j = i; j < R; j++) {
                double x = U.get(i, j);
                v[t++] = (i == j) ? Math.log(Math.max(1e-12, x)) : x;
            }
        }
        return v;
    }

    private void unpackAll(double[] v, Matrix Lambda, double[] psi, Matrix SigmaL) {
        int t = 0, rowBase = 0, colBase = 0;

        for (int i = 0; i < B; i++) {
            int ri = r[i], mi = m[i];
            // zero block slice
            for (int rr = 0; rr < mi; rr++) for (int cc = 0; cc < ri; cc++) Lambda.set(rowBase + rr, colBase + cc, 0.0);

            // triangular with positive diag
            for (int a = 0; a < ri; a++) {
                double diag = Math.exp(v[t++]);
                Lambda.set(rowBase + a, colBase + a, diag);
                for (int b = 0; b < a; b++) Lambda.set(rowBase + a, colBase + b, v[t++]);
            }
            // free rows
            for (int nr = 0; nr < mi - ri; nr++) {
                for (int b = 0; b < ri; b++) Lambda.set(rowBase + ri + nr, colBase + b, v[t++]);
            }

            rowBase += mi;
            colBase += ri;
        }

        for (int i = 0; i < M; i++) psi[i] = Math.exp(v[t++]);

        Matrix U = new Matrix(R, R);
        for (int i = 0; i < R; i++) {
            for (int j = i; j < R; j++) {
                double x = (i == j) ? Math.exp(v[t++]) : v[t++];
                U.set(i, j, x);
            }
        }
        Matrix Ut = U.transpose();
        Matrix Sig = Ut.times(U);
        for (int i = 0; i < R; i++) for (int j = 0; j < R; j++) SigmaL.set(i, j, Sig.get(i, j));
    }

    private Matrix cholUpper(Matrix SPD) {
        // Minimal upper-Cholesky (replace with a robust routine if you like)
        int n = SPD.getNumRows();
        Matrix L = new Matrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double sum = SPD.get(i, j);
                for (int k = 0; k < i; k++) sum -= L.get(k, i) * L.get(k, j);
                if (i == j) {
                    double val = Math.sqrt(Math.max(sum, 1e-10));
                    L.set(i, i, val);
                } else {
                    L.set(i, j, sum / L.get(i, i));
                }
            }
        }
        return L;
    }

    /**
     * Observed covariance used in the fit (restricted to BlockSpec variables, in block order).
     */
    public ICovarianceMatrix getMeasuresCov() {
        return measuresCov;
    }

    // ---- Public accessors

    /**
     * Loadings (M x R) in block-concatenated order.
     */
    public Matrix getLambda() {
        return Lambda;
    }

    /**
     * Unique variances (length M).
     */
    public double[] getPsiDiag() {
        return PsiDiag;
    }

    /**
     * Latent covariance (R x R).
     */
    public Matrix getSigmaL() {
        return SigmaL;
    }

    /**
     * Objective value at optimum.
     */
    public double getObjective() {
        return minObj;
    }

    /**
     * Chi-square style goodness-of-fit p-value for the provided BlockSpec.
     */
    public double getPValue() {
        return pValue;
    }

    /**
     * Mapping utilities if you need per-block slices of Î / Î£_L.
     */
    public int blockStartRow(int b) {
        int s = 0;
        for (int i = 0; i < b; i++) s += m[i];
        return s;
    }

    public int blockStartCol(int b) {
        int s = 0;
        for (int i = 0; i < b; i++) s += r[i];
        return s;
    }

    /**
     * Objective: 0.5 * || I - (Î Î£_L Î' + Î¨) S^{-1} ||_F^2 (constraints enforced by parameterization).
     */
    private class Obj implements MultivariateFunction {
        @Override
        public double value(double[] theta) {
            Matrix Lmb = new Matrix(M, R);
            double[] psi = new double[M];
            Matrix Sig = new Matrix(R, R);
            unpackAll(theta, Lmb, psi, Sig);
            Matrix implied = impliedCov(Lmb, psi, Sig);
            Matrix I = Matrix.identity(M);
            Matrix diff = I.minus(implied.times(S_inv));
            double obj = 0.5 * (diff.times(diff)).trace();
            return (Double.isFinite(obj) ? obj : Double.POSITIVE_INFINITY);
        }
    }
}
