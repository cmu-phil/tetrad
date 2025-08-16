package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

/** Centralizes rank calls & an RLCD-style atomic-cover equality check. */
public final class RankEqualities {
    private RankEqualities() {}

    public static int rank(SimpleMatrix S, int[] A, int[] B, int n, double alpha) {
        return RankTests.estimateWilksRank(S, A, B, n, alpha);
    }
    public static int rankCond(SimpleMatrix S, int[] A, int[] B, int[] Z, int n, double alpha) {
        return RankTests.estimateWilksRankConditioned(S, A, B, Z, n, alpha);
    }

    /**
     * RLCD-like atomic-cover equality: rank([C∪X], D) == k  with size guards implied by caller.
     * This is a light-weight guard; it mirrors the equality used to certify a cover.
     */
    public static boolean atomicCoverEquality(SimpleMatrix S,
                                              int[] C, int[] X, int[] D,
                                              int k, int n, double alpha) {
        if (C == null || X == null || D == null) return false;
        if (C.length + X.length <= 0 || D.length <= 0) return false;
        int[] left = concat(C, X);
        int r = RankTests.estimateWilksRank(S, left, D, n, alpha);
        return r == k;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}