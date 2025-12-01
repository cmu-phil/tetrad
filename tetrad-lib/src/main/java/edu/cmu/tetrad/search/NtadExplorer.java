package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
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
     * Identifies and returns a list of rank-deficient NTAD (near-total aggregate decomposition) results,
     * based on the given correlation matrix and other input parameters.
     * This method divides variables into two blocks (A and B) of the specified size and evaluates
     * rank deficiencies based on canonical correlation analysis.
     *
     * @param data the correlation matrix containing the data to analyze.
     * @param vars the list of variables to consider for forming blocks A and B.
     * @param blockSize the size of each block (A and B) to analyze.
     * @param maxResults the maximum number of rank-deficient NTAD results to return.
     * @param alpha the significance level for Wilks test used in rank estimation.
     * @param ccaTest the object used to perform canonical correlation analysis (CCA).
     * @return a list of {@code NtadResult} objects, where each result represents a pair of rank-deficient blocks (A and B).
     */
    public static List<NtadResult> listRankDeficientNtads(
            CorrelationMatrix data,
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

            for (int j = 0; j < data.getVariableNames().size(); j++) {
                if (data.getVariableNames().get(j).equals(v.getName())) {
                    colIndex[i] = j;
                    break;
                }
            }
//            colIndex[i] = data.getColumn(v);
        }

        SimpleMatrix Scond = new CorrelationMatrix(data).getMatrix().getSimpleMatrix();
        int nEff = data.getSampleSize();

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
     * Generates the next lexicographical combination of k elements chosen from a total of n elements.
     * If the combination reaches the final possible state, the method resets and returns false.
     *
     * @param comb the current combination, represented as an array of integers. This array should
     *             initially contain the indices of the current combination in ascending order.
     *             It will be updated in-place to represent the next combination.
     * @param n the total number of elements to choose from.
     * @param k the number of elements to include in each combination.
     * @return {@code true} if the next combination was successfully generated, or {@code false}
     *         if there are no more combinations and the sequence in the array has reached its terminal state.
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
     * Represents the result of a near-total aggregate decomposition (NTAD) analysis.
     * Instances of this class are immutable and store information about two block
     * structures (Block A and Block B) along with their properties, including block size,
     * rank, and p-value based on a canonical correlation analysis (CCA).
     */
    public static final class NtadResult implements TetradSerializable {

        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * A list representing the nodes in Block A associated with an NTAD analysis result.
         * Block A is one of the two groups of nodes analyzed during the Nonparametric Test
         * for Associations in Data (NTAD) process, and this field contains the nodes that
         * belong to the first group.
         *
         * This field is initialized during the creation of an instance of the NtadResult
         * class and remains immutable throughout the lifecycle of the object.
         */
        private final List<Node> blockA;
        /**
         * Represents the list of nodes in Block B associated with the NTAD (Network-Theoretic Analysis of Dependence) analysis.
         * Block B corresponds to one of the two substructures examined in the analysis, where each node contributes
         * to the statistical and structural properties being evaluated.
         */
        private final List<Node> blockB;
        /**
         * Represents the size of the block structures involved in the NTAD analysis.
         */
        private final int blockSize;
        /**
         * Represents the rank of the canonical correlation analysis (CCA) result.
         */
        private final int rank;
        /**
         * Represents the p-value from the statistical test associated with the analysis.
         */
        private final double pValue;

        /**
         * Constructs an instance of NtadResult with specified properties.
         *
         * @param blockA the list of nodes in Block A associated with the NTAD analysis
         * @param blockB the list of nodes in Block B associated with the NTAD analysis
         * @param blockSize the size of the block structures involved in the analysis
         * @param rank the rank of the canonical correlation analysis (CCA) result
         * @param pValue the p-value from the statistical test associated with the analysis
         */
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

        /**
         * Returns the list of nodes in Block A associated with the NTAD analysis result.
         *
         * @return an unmodifiable list of nodes in Block A
         */
        public List<Node> getBlockA() {
            return blockA;
        }

        /**
         * Returns the list of nodes in Block B associated with the NTAD analysis result.
         *
         * @return an unmodifiable list of nodes in Block B
         */
        public List<Node> getBlockB() {
            return blockB;
        }

        /**
         * Retrieves the block size associated with the NTAD analysis result.
         *
         * @return the size of the block structures involved in the analysis
         */
        public int getBlockSize() {
            return blockSize;
        }

        /**
         * Retrieves the rank of the canonical correlation analysis (CCA) result.
         *
         * @return the rank associated with the analysis
         */
        public int getRank() {
            return rank;
        }

        /**
         * Retrieves the p-value from the statistical test associated with the NTAD analysis result.
         *
         * @return the p-value as a double
         */
        public double getPValue() {
            return pValue;
        }

        /**
         * Returns a string representation of the NtadResult object,
         * including the values of its fields: blockA, blockB, blockSize, rank, and pValue.
         *
         * @return a string representation of the current NtadResult instance
         */
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