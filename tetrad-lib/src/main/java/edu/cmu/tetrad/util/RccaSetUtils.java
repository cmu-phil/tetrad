package edu.cmu.tetrad.util;

// Put these next to RankConditionalIndependenceTest, or in a small Utils class.

import org.ejml.simple.SimpleMatrix;

public final class RccaSetUtils {
    private static final double RIDGE = 1e-10;

    // ---- exact copies of your small helpers (or import them) ----
    private static int[] range(int a, int b) {
        int[] r = new int[b - a];
        for (int i = 0; i < r.length; i++) r[i] = a + i;
        return r;
    }
    private static SimpleMatrix block(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int ri = rows[i];
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, S.get(ri, cols[j]));
            }
        }
        return out;
    }
    private static SimpleMatrix safeSolve(SimpleMatrix A, SimpleMatrix B) {
        try {
            return A.solve(B);
        } catch (RuntimeException e) {
            SimpleMatrix Areg = A.copy();
            int n = Math.min(Areg.getNumRows(), Areg.getNumCols());
            for (int i = 0; i < n; i++) Areg.set(i, i, Areg.get(i, i) + RIDGE);
            return Areg.solve(B);
        }
    }

    /** Build the *conditioned* covariance over [X|Y] exactly like your CI test does. */
    public static SimpleMatrix scondXY(SimpleMatrix S, int[] X, int[] Y, int[] Z) {
        int[] V = new int[X.length + Y.length];
        System.arraycopy(X, 0, V, 0, X.length);
        System.arraycopy(Y, 0, V, X.length, Y.length);

        if (Z == null || Z.length == 0) {
            return block(S, V, V);
        }
        SimpleMatrix S_VV = block(S, V, Z.length == 0 ? V : V);
        SimpleMatrix S_VZ = block(S, V, Z);
        SimpleMatrix S_ZZ = block(S, Z, Z);
        SimpleMatrix S_ZV = S_VZ.transpose();
        // Schur: S_VV - S_VZ * (S_ZZ \ S_ZV)
        SimpleMatrix solved = safeSolve(S_ZZ, S_ZV);
        return S_VV.minus(S_VZ.mult(solved));
    }

    /** Set–vs–set rank using the SAME logic as RankConditionalIndependenceTest. */
    public static int rankSetVsSet(SimpleMatrix S, int[] X, int[] Y, int[] Z,
                                   int n, double alpha) {
        // Localize X and Y inside Scond and call your rank test
        SimpleMatrix Scond = scondXY(S, X, Y, Z);
        int p = X.length, q = Y.length;
        int[] xLoc = range(0, p);
        int[] yLoc = range(p, p + q);
        return RankTests.estimateRccaRank(Scond, xLoc, yLoc, n, alpha);
    }
}