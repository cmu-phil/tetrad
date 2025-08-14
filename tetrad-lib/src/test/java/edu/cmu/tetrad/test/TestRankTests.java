package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.RankTests;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class TestRankTests {

    public static void main(String[] args) {
        double alpha = 0.05;
        double regParam = 0.01;
        double essMult = 1;
        int size = 3;
        int rank = 2;

        test(size, rank, true, alpha, regParam, essMult);
        test(size, rank, false, alpha, regParam, essMult);
    }

    private static void test(int size, int rank, boolean coherent, double alpha, double regParam, double essMult) {
        try {
            DataSet data = SimpleDataLoader.loadContinuousData(
                    new File("/Users/josephramsey/IdeaProjects/BryanStuff/lv_work/check_ranks.csv"),
                    "//", '"', "*", true, Delimiter.COMMA, false);
            SimpleMatrix S = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();
            int dim = S.getNumRows();
            int origSize = dim / 2;

            ChoiceGenerator gen = new ChoiceGenerator(dim, size);
            int[] indexx;

            while ((indexx = gen.next()) != null) {
                boolean hasSmaller = false;
                boolean hasLarger = false;

                for (int idx : indexx) {
                    if (idx < origSize) {
                        hasSmaller = true;
                    } else {
                        hasLarger = true;
                    }
                }

                if (coherent && !((hasSmaller && !hasLarger) || (!hasSmaller && hasLarger))) {
                    continue;
                }

                if (!coherent && !(hasSmaller && hasLarger)) {
                    continue;
                }

                ArrayList<Integer> indices = new ArrayList<>();

//                System.out.println(Arrays.toString(indexx));

                Q:
                for (int q = 0; q < dim; q++) {
                    for (int i : indexx) {
                        if (i == q) {
                            continue Q;
                        }
                    }

                    indices.add(q);
                }

                int[] indexy = indices.stream().mapToInt(Integer::intValue).toArray();

                int ess = (int) (data.getNumRows() * essMult);
                double p = RankTests.rankLeByWilks(S, indexx, indexy, ess, rank);
                int estRank = RankTests.estimateWilksRank(S, indexx, indexy, ess, alpha);

                // Print out the discrepancies
                if ((coherent && p < alpha) || (!coherent && p > alpha)) {
                    System.out.println((coherent ? "coher" : "incoh") + " size = " + size + " rank = " + rank + ": " + Arrays.toString(indexx)
                                       + " p = " + p + (p > alpha ? " p > alpha" : " p < alpha") + " est_rank = " + estRank);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Pascal triangle up to n choose k (inclusive on n)
    static long[][] precomputeBinom(int n, int k) {
        long[][] C = new long[n + 1][k + 1];
        for (int i = 0; i <= n; i++) {
            C[i][0] = 1;
            int maxj = Math.min(i, k);
            for (int j = 1; j <= maxj; j++) {
                long v = C[i - 1][j - 1] + C[i - 1][j];
                if (v < 0 || v < C[i - 1][j - 1]) v = Long.MAX_VALUE; // clamp on overflow
                C[i][j] = v;
            }
        }
        return C;
    }

    // choose(x, j) with the convention C(x, j) = 0 when x < j
    static long choose(long[][] C, int x, int j) {
        if (x < j || j < 0) return 0L;
        return C[x][j];
    }

    /**
     * Unrank colex: return the m-th k-combination of {0..n-1} in colex order. Correct logic: r = m for i = k..1: pick
     * largest x in [0, bound-1] with C(x, i) <= r set a[i-1] = x r -= C(x, i) bound = x
     */
    static int[] combinadicDecodeColex(long m, int n, int k, long[][] C) {
        int[] comb = new int[k];
        long r = m;
        int bound = n; // elements must be < bound; shrinks each step

        for (int i = k; i >= 1; i--) {
            int lo = 0, hi = bound - 1, v = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long c = choose(C, mid, i);
                if (c <= r) {
                    v = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            comb[i - 1] = v;           // element value (0-based)
            r -= choose(C, v, i);      // <-- correct decrement
            bound = v;                  // next elements must be smaller
        }
        return comb; // ascending: comb[0] < comb[1] < ... < comb[k-1]
    }

    @Test
    public void test1() {
        int n = 3;
        int k = 2;

        long[][] C = precomputeBinom(n, k); // size: (n+1) x (k+1)
        long total = C[n][k];

        System.out.println("n = " + n + ", k = " + k + ", total combinations = " + total);
        for (long m = 0; m < total; m++) {
            int[] comb = combinadicDecodeColex(m, n, k, C);
            System.out.println(m + ": " + Arrays.toString(comb));
        }
    }
}
