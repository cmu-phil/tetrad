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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * BlocksBicScore: BIC-style local score over block representations using cached RCCA singular values from RankTests.
 * <p>
 * fit(r) = -nEff * sum_{i=0}^{r-1} log(1 - rho_i^2) pen(r) = c * [ r * (p + q - r) ] * log(n) + 2 * gamma * [ r * (p +
 * q - r) ] * log(P_pool)
 * <p>
 * where p = |X_block|, q = |Y_block|, r in {0..min(p,q,n-1)}, and nEff = n - 1 - (p + q + 1)/2 (floored at 1). P_pool
 * excludes Y's own block.
 * <p>
 * CONTRACT: - 'blocks' maps each block index b (0..B-1) to the list of embedded column indices in THIS dataset for that
 * block. - 'blockVariables' is exactly the list of Nodes to score over, one per block, same order (size B). -
 * getVariables() returns exactly 'blockVariables'.
 * <p>
 * This is Wilks' Lambda test statistic for canonical correlations: 2â = -(n_eff) * Î£ log(1 - rho_i^2) (see Mardia, Kent
 * &amp; Bibby 1979, section 12.6; Anderson 2003, section 12.3.2) This is already in 2 log-likelihood units, so the BIC
 * penalty can be applied directly as 2â - c k log n.
 */
@Deprecated
public class BlocksBicScore implements Score, BlockScore, EffectiveSampleSizeSettable {
    // --- Caches ---
    private static final int SCORE_CACHE_MAX = 100_000;
    private static final int XBLOCK_CACHE_MAX = 50_000;

    // --- Data / bookkeeping ---
    private final List<Node> variables;          // block-level variables, size == blocks.size()
    private final Map<Node, Integer> nodeIndex;  // block node -> block index
    private final SimpleMatrix Sphi;             // covariance (or correlation) of embedded data
    private final int n;                         // sample size

    // Precomputed embedded column arrays (avoid per-call boxing/copies)
    private final int[][] blockAllCols;          // per block -> all embedded cols
    private final int totalEmbeddedCols;         // sum of all embedded widths across blocks

    /**
     * LRU cache for full local scores per (y, parents, knobs). Not thread-safe; wrap externally if running
     * multi-threaded.
     */
    private final Map<FamilyKey, Double> scoreCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FamilyKey, Double> e) {
                    return size() > SCORE_CACHE_MAX;
                }
            };

    /**
     * LRU cache for concatenated X-block embedded columns per parent set.
     */
    private final Map<ParentsKey, int[]> xblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ParentsKey, int[]> e) {
                    return size() > XBLOCK_CACHE_MAX;
                }
            };
    private final BlockSpec blockSpec;

    // --- Knobs ---
    private double penaltyDiscount = 1.0;   // c
    private double ridge = 1e-8;            // regLambda passed to RankTests
    private double ebicGamma = 0.0;         // gamma for EBIC-style extra penalty (0 disables)
    private int nEff;

    /**
     * Constructs a BlocksBicScore object using the provided BlockSpec configuration. This class prepares the necessary
     * data structures and performs precomputations related to block-level variables, dataset dimensions, and embedded
     * columns for scoring calculations.
     *
     * @param blockSpec The specification of the block-based structure, containing information such as dataset, blocks,
     *                  and block variables. Must not be null. Throws an IllegalArgumentException if blockSpec is
     *                  invalid or contains inconsistencies (e.g., null or duplicate variables, invalid column
     *                  indices).
     */
    public BlocksBicScore(BlockSpec blockSpec) {
        this.blockSpec = Objects.requireNonNull(blockSpec, "blockspec == null");

        int B = blockSpec.blocks().size();

        // block-level variables & index
        this.variables = new ArrayList<>(blockSpec.blockVariables());
        this.nodeIndex = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            Node v = variables.get(j);
            if (v == null) throw new IllegalArgumentException("blockVariables[" + j + "] is null");
            if (nodeIndex.put(v, j) != null) {
                throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
            }
        }

        this.n = blockSpec.dataSet().getNumRows();
        setEffectiveSampleSize(-1);

        this.Sphi = new CorrelationMatrix(blockSpec.dataSet()).getMatrix().getSimpleMatrix();
        // this.Sphi = DataUtils.cov(dataSet.getDoubleData().getSimpleMatrix()); // alternative

        // Precompute embedded column arrays for each block
        int D = blockSpec.dataSet().getNumColumns();
        this.blockAllCols = new int[B][];
        int totalCols = 0;
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blockSpec.blocks().get(b);
            if (cols == null || cols.isEmpty()) {
                this.blockAllCols[b] = new int[0];
            } else {
                int[] all = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) {
                    int col = cols.get(k);
                    if (col < 0 || col >= D) {
                        throw new IllegalArgumentException(
                                "Block " + b + " references column " + col + " outside dataset width " + D);
                    }
                    all[k] = col;
                }
                this.blockAllCols[b] = all;
            }
            totalCols += this.blockAllCols[b].length;
        }
        this.totalEmbeddedCols = totalCols;
    }

    /**
     * Computes the local score for a given node specified by its index and a set of parent nodes.
     *
     * @param i       the index of the target node whose local score is to be computed
     * @param parents the indices of the parent nodes influencing the target node
     * @return the computed local score for the target node given its parent nodes
     */
    public double localScore(int i, int... parents) {
        Node y = variables.get(i);
        List<Node> _parents = new ArrayList<>();
        for (int parent : parents) _parents.add(variables.get(parent));
        return localScore(y, _parents);
    }

    /**
     * Computes the local score of a target node given its parent nodes in a graph. The computation involves statistical
     * evaluation and penalization based on the given data structure and parameters. If the target node or its parents
     * are invalid or incompatible for the computation, a negative infinity score is returned.
     *
     * @param y       the target node for which the local score is calculated
     * @param parents a list of parent nodes of the target node
     * @return the computed local score for the node-parent configuration; returns a negative infinity score in cases of
     * invalid configurations
     */
    public double localScore(Node y, List<Node> parents) {
        int yi = idx(y);

        // Null baseline (no parents)
        if (parents == null || parents.isEmpty()) return 0.0;

        // Build sorted parent indices for stable keys
        int[] parentIdx = new int[parents.size()];
        for (int t = 0; t < parents.size(); t++) parentIdx[t] = idx(parents.get(t));
        Arrays.sort(parentIdx);

        FamilyKey fkey = new FamilyKey(yi, parentIdx, ridge, penaltyDiscount, ebicGamma);
        Double cached = scoreCache.get(fkey);
        if (cached != null) return cached;

        // Y block
        int[] Yblock = blockFor(yi);
        if (Yblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // X block (concat of parents)
        ParentsKey pkey = new ParentsKey(parentIdx);
        int[] Xblock = xblockCache.get(pkey);
        if (Xblock == null) {
            Xblock = concatBlocksFromSortedParentIdx(parentIdx);
            xblockCache.put(pkey, Xblock);
        }
        if (Xblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // RCCA entry (cached inside RankTests)
        RankTests.RccaEntry ent = RankTests.getRccaEntry(Sphi, Xblock, Yblock, /*regLambda*/ ridge);
        if (ent == null) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        int p = Xblock.length, q = Yblock.length;

        // Bartlett-style effective n for CCA LLR: _nEff = n - 1 - (p + q + 1)/2, floored at 1
        double _nEff = this.nEff - 1.0 - 0.5 * (p + q + 1.0);
        if (_nEff < 1.0) _nEff = 1.0;

        // Pull suffix logs and clamp m by both n and suffix support
        double[] suffix = ent.suffixLogs;
        if (suffix == null) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }
        int m = Math.min(Math.min(p, q), (int) _nEff - 1);
        m = Math.min(m, suffix.length - 1); // need suffix[m]
        if (m <= 0) {
            scoreCache.put(fkey, -1e-12);
            return -1e-12;
        }

        // Fit/penalty sweep over r
        double best = Double.NEGATIVE_INFINITY;
        double suffix0 = suffix[0];

        // EBIC pool size: all potential predictors' embedded columns excluding Y's own block
        int Ppool = Math.max(totalEmbeddedCols - Yblock.length, 2);

        // Evaluate r = m
        {
            double sumLogsTopR = suffix0 - suffix[m];
            double fit = -_nEff * sumLogsTopR;
            int k = m * (p + q - m);

            double logN = Math.log(Math.max(_nEff, 2.0));
            double pen = penaltyDiscount * k * logN;
            if (ebicGamma > 0.0) {
                pen += 2.0 * ebicGamma * k * Math.log(Ppool);
            }

            double sc = fit - pen;
            if (!Double.isNaN(sc) && !Double.isInfinite(sc)) {
                best = sc;
            }
        }

        // Scan r = 1..m-1  (skip r = 0 to avoid null ties)
        for (int r = 1; r < m; r++) {
            double sumLogsTopR = suffix0 - suffix[r];
            double fit = -_nEff * sumLogsTopR;
            int k = r * (p + q - r);

            double logN = Math.log(Math.max(_nEff, 2.0));
            double pen = penaltyDiscount * k * logN;
            if (ebicGamma > 0.0) {
                pen += 2.0 * ebicGamma * k * Math.log(Ppool);
            }

            double sc = fit - pen;
            if (Double.isNaN(sc) || Double.isInfinite(sc)) continue;
            if (sc > best) best = sc;
        }

        // Optional nudge to avoid null-equivalent ties being selected upstream
        if (best <= 0.0) best = -1e-12;

        scoreCache.put(fkey, best);
        return best;
    }

    /**
     * Computes the difference in local score for a given node when a parenthood relationship is either added or
     * removed. This method evaluates the impact of modifying the parent set on the local score of the specified node.
     *
     * @param y             the target node for which the local score difference is computed
     * @param oldParents    the current list of parent nodes associated with the target node
     * @param changedParent the parent node that is being added or removed from the parent set
     * @param adding        a flag indicating whether the parent node is being added (true) or removed (false)
     * @return the difference between the new local score (after adding or removing the parent) and the old local score
     */
    public double localScoreDelta(Node y, List<Node> oldParents, Node changedParent, boolean adding) {
        List<Node> newParents = new ArrayList<>(oldParents);
        if (adding) newParents.add(changedParent);
        else newParents.remove(changedParent);
        return localScore(y, newParents) - localScore(y, oldParents);
    }

    /**
     * Sets the penalty discount used in scoring calculations and clears the cached scores, since changing the penalty
     * affects the score computations.
     *
     * @param c the new penalty discount value to be set
     */
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        scoreCache.clear(); // penalty affects scores
    }

    /**
     * Updates the ridge parameter and clears the score cache. The ridge parameter impacts computations and the cached
     * results are invalidated upon its change.
     *
     * @param ridge the new ridge value to be set
     */
    public void setRidge(double ridge) {
        this.ridge = ridge;
        scoreCache.clear(); // ridge affects rhos
        // RankTests has its own LRU keyed by regLambda.
    }

    /**
     * Sets the EBIC gamma parameter and clears the score cache. The EBIC gamma parameter impacts computations and the
     * cached results are invalidated upon its change.
     *
     * @param gamma the new EBIC gamma value to be set
     */
    public void setEbicGamma(double gamma) {
        this.ebicGamma = gamma;
        scoreCache.clear(); // gamma affects scores
    }

    // --- Internals ---
    private int idx(Node v) {
        Integer i = nodeIndex.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node " + v);
        return i;
    }

    /**
     * Embedded columns for a block (precomputed).
     */
    private int[] blockFor(int blockIndex) {
        return blockAllCols[blockIndex];
    }

    /**
     * Concatenate embedded columns for all parents (parents given as sorted block indices).
     */
    private int[] concatBlocksFromSortedParentIdx(int[] sortedParents) {
        int total = 0;
        for (int p : sortedParents) total += blockAllCols[p].length;
        int[] out = new int[total];
        int k = 0;
        for (int p : sortedParents) {
            int[] cols = blockAllCols[p];
            System.arraycopy(cols, 0, out, k, cols.length);
            k += cols.length;
        }
        return out;
    }

    /**
     * Concatenate embedded columns for all parents (block Node list) â unused helper.
     */
    @SuppressWarnings("unused")
    private int[] concatBlocks(List<Node> parents) {
        int[] parentIdx = new int[parents.size()];
        for (int t = 0; t < parents.size(); t++) parentIdx[t] = idx(parents.get(t));
        Arrays.sort(parentIdx);
        return concatBlocksFromSortedParentIdx(parentIdx);
    }

    /**
     * Computes the local score difference for a given node and its parents. This method evaluates the effect of adding
     * a single parent to a node's parent set by calculating the difference in local scores.
     *
     * @param x the node being considered as an additional parent
     * @param y the node for which the local score difference is being computed
     * @param z the array of parent indices currently associated with node y
     * @return the difference between the local score of y with (z + x) as parents and the local score of y with z as
     * parents
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(variables.get(y), appendNodes(z, x)) - localScore(variables.get(y), z);
    }

    /**
     * Retrieves the list of variables associated with the current instance.
     *
     * @return a list of Node objects representing the variables
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size of the dataset associated with the block specification.
     *
     * @return the number of rows in the dataset.
     */
    @Override
    public int getSampleSize() {
        return blockSpec.dataSet().getNumRows();
    }

    // --- Helpers ---
    private List<Node> appendNodes(int[] parents, int x) {
        List<Node> list = new ArrayList<>(parents.length + 1);
        for (int p : parents) list.add(variables.get(p));
        list.add(variables.get(x));
        return list;
    }

    private double localScore(Node y, int[] parents) {
        List<Node> ps = new ArrayList<>(parents.length);
        for (int p : parents) ps.add(variables.get(p));
        return localScore(y, ps);
    }

    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.n : nEff;
        scoreCache.clear();
        xblockCache.clear();
    }

    // ----- Key types for caches -----

    /**
     * Key for the per-family score cache. Includes knobs that affect the score.
     */
    private static final class FamilyKey {
        final int y;
        final int[] parents; // sorted block indices
        final long ridgeBits;
        final long penBits;
        final long ebicBits;
        private final int hash;

        FamilyKey(int y, int[] parents, double ridge, double pen, double ebic) {
            this.y = y;
            this.parents = parents.clone();
            this.ridgeBits = quantize(ridge);
            this.penBits = quantize(pen);
            this.ebicBits = quantize(ebic);
            this.hash = computeHash();
        }

        private static long quantize(double x) {
            // quantize to ~1e-12 relative resolution to keep keys stable
            return Double.doubleToLongBits(Math.rint(x * 1e12) / 1e12);
        }

        private int computeHash() {
            int h = 1;
            h = 31 * h + y;
            h = 31 * h + Arrays.hashCode(parents);
            h = 31 * h + Long.hashCode(ridgeBits);
            h = 31 * h + Long.hashCode(penBits);
            h = 31 * h + Long.hashCode(ebicBits);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FamilyKey fk)) return false;
            return y == fk.y
                   && ridgeBits == fk.ridgeBits
                   && penBits == fk.penBits
                   && ebicBits == fk.ebicBits
                   && Arrays.equals(parents, fk.parents);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Key for the per-parent-set X-block cache (depends only on parent block indices).
     */
    private static final class ParentsKey {
        final int[] parents; // sorted block indices
        private final int hash;

        ParentsKey(int[] parents) {
            this.parents = parents.clone();
            this.hash = Arrays.hashCode(this.parents);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ParentsKey pk)) return false;
            return Arrays.equals(parents, pk.parents);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
