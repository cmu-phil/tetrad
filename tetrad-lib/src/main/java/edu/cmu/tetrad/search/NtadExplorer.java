package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradSerializable;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * NtadExplorer contains utility methods for identifying and analyzing rank-deficient m×m variable subsets (n-tads)
 * within a dataset. These subsets are determined by their statistical properties evaluated using canonical correlation
 * analysis (CCA).
 */
public final class NtadExplorer {

    private NtadExplorer() {
        // utility class
    }

    /**
     * Scan the given variable subset for rank-deficient m×m blocks (A,B), where |A| = |B| = blockSize, A and B are
     * disjoint subsets of vars, and rank(Σ_AB) < blockSize according to Wilks' Lambda at level alpha.
     * <p>
     * The sublist 'vars' is first sorted alphabetically by name to give a canonical order. Pairs (A,B) are enumerated
     * in lexicographic order over that sorted list. Up to maxResults rank-deficient blocks are returned.
     * <p>
     * The p-value stored in each result is the p-value for H0: rank(Σ_AB) ≤ blockSize - 1, as returned by
     * ccaTest.ntad(ntad).
     */
    public static List<NtadResult> listRankDeficientNtads(
            DataSet data,
            List<Node> vars,
            int blockSize,
            int maxResults,
            double alpha,
            Cca ccaTest
    ) {
        List<NtadResult> results = new ArrayList<>();

        if (blockSize <= 0 || maxResults <= 0) {
            return results;
        }

        // 1. Sort variables alphabetically by name for determinism.
        List<Node> sortedVars = new ArrayList<>(vars);
        sortedVars.sort(Comparator.comparing(Node::getName));

        int nVars = sortedVars.size();
        if (2 * blockSize > nVars) {
            return results; // not enough variables to form A and B
        }

        // 2. Map sortedVars to dataset column indices (assumed consistent with ccaTest).
        int[] colIndex = new int[nVars];
        for (int i = 0; i < nVars; i++) {
            Node v = sortedVars.get(i);
            // Adjust if your DataSet API uses a different method name.
            colIndex[i] = data.getColumn(v);
        }

        SimpleMatrix Scond = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();
        int nEff = data.getNumRows();

        // 3. Enumerate A combinations: indices into sortedVars
        int[] aPos = new int[blockSize];
        for (int i = 0; i < blockSize; i++) {
            aPos[i] = i;
        }

        boolean moreA = true;

        while (moreA && results.size() < maxResults) {

            // mark which positions are used by A
            boolean[] used = new boolean[nVars];
            for (int i = 0; i < blockSize; i++) {
                used[aPos[i]] = true;
            }

            // build list of remaining positions for B
            int remSize = nVars - blockSize;
            int[] remPositions = new int[remSize];
            int rIdx = 0;
            for (int i = 0; i < nVars; i++) {
                if (!used[i]) {
                    remPositions[rIdx++] = i;
                }
            }

            if (remSize >= blockSize) {
                // initial combination for B: first m indices into remPositions
                int[] bIdx = new int[blockSize];
                for (int i = 0; i < blockSize; i++) {
                    bIdx[i] = i;
                }

                boolean moreB = true;

                while (moreB && results.size() < maxResults) {
                    // map indices into remPositions to actual positions in sortedVars
                    int[] bPos = new int[blockSize];
                    for (int i = 0; i < blockSize; i++) {
                        bPos[i] = remPositions[bIdx[i]];
                    }

                    // Map positions in sortedVars to column indices in the correlation matrix
                    int[] aCols = new int[blockSize];
                    int[] bCols = new int[blockSize];
                    for (int i = 0; i < blockSize; i++) {
                        aCols[i] = colIndex[aPos[i]];
                        bCols[i] = colIndex[bPos[i]];
                    }

                    int[][] ntad = new int[][]{aCols, bCols};

                    // ---- Rank estimation via RankTests.estimateWilksRank ----
                    int rank = RankTests.estimateWilksRank(Scond, aCols, bCols, nEff, alpha);

                    // p-value for H0: rank(Σ_AB) ≤ m - 1
                    double pValue = ccaTest.ntad(ntad);

                    // Keep only rank-deficient blocks (rank < m).
                    if (rank < blockSize) {
                        List<Node> blockA = new ArrayList<>(blockSize);
                        List<Node> blockB = new ArrayList<>(blockSize);

                        for (int i = 0; i < blockSize; i++) {
                            blockA.add(sortedVars.get(aPos[i]));
                            blockB.add(sortedVars.get(bPos[i]));
                        }

                        results.add(new NtadResult(blockA, blockB, blockSize, rank, pValue));
                    }

                    // next B combination
                    moreB = nextCombination(bIdx, remSize, blockSize);
                }
            }

            // next A combination
            moreA = nextCombination(aPos, nVars, blockSize);
        }

        return results;
    }

    /**
     * Lexicographic next-combination generator over {0, ..., n-1}. 'comb' is a k-length array of strictly increasing
     * indices. Returns true if updated to the next combination; false if there is none.
     */
    private static boolean nextCombination(int[] comb, int n, int k) {
        int i = k - 1;
        while (i >= 0 && comb[i] == n - k + i) {
            i--;
        }
        if (i < 0) {
            return false;
        }
        comb[i]++;
        for (int j = i + 1; j < k; j++) {
            comb[j] = comb[j - 1] + 1;
        }
        return true;
    }

    /**
     * Record for one rank-deficient n-tad (A,B).
     */
    public static final class NtadResult implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        private final List<Node> blockA;
        private final List<Node> blockB;
        private final int blockSize;
        private final int rank;
        private final double pValue;

        public NtadResult(List<Node> blockA,
                          List<Node> blockB,
                          int blockSize,
                          int rank,
                          double pValue) {
            this.blockA = Collections.unmodifiableList(new ArrayList<>(blockA));
            this.blockB = Collections.unmodifiableList(new ArrayList<>(blockB));
            this.blockSize = blockSize;
            this.rank = rank;
            this.pValue = pValue;
        }

        public List<Node> getBlockA() {
            return blockA;
        }

        public List<Node> getBlockB() {
            return blockB;
        }

        public int getBlockSize() {
            return blockSize;
        }

        public int getRank() {
            return rank;
        }

        /**
         * p-value for H0: rank(Σ_AB) ≤ blockSize - 1 (Wilks test used in CCA).
         */
        public double getPValue() {
            return pValue;
        }

        @Override
        public String toString() {
            return "NtadResult{" +
                   "blockA=" + blockA +
                   ", blockB=" + blockB +
                   ", blockSize=" + blockSize +
                   ", rank=" + rank +
                   ", pValue=" + pValue +
                   '}';
        }
    }
}