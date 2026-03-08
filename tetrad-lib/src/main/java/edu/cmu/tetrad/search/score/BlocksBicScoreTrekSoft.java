package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TMath;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static edu.cmu.tetrad.util.TMath.abs;

/**
 * <b>BlocksBicScoreTrekSoft</b>
 *
 * <p>
 * A block-structured BIC score based on regularized canonical correlation analysis (RCCA)
 * with an additional soft regularizer encouraging agreement with the trek-implied rank.
 * </p>
 *
 * <p>
 * For a candidate family <code>Y | Pa(Y)</code>, let <code>Xblock</code> be the concatenation
 * of the indicator columns for the parent blocks and <code>Yblock</code> the indicator columns
 * for the target block. The score evaluates reduced-rank models of canonical rank
 * <code>r</code> between these two blocks.
 * </p>
 *
 * <p>The score for a candidate rank <code>r</code> is</p>
 *
 * <pre>
 * score(r) = fit(r)
 *            - c * k(r) * log(nAdj)
 *            - (mult * c) * log(nAdj) * (r - r*)²
 * </pre>
 *
 * <p>where</p>
 *
 * <ul>
 *   <li><code>fit(r) = -nAdj * Σ log(1 - ρᵢ²)</code> for the top <code>r</code> canonical correlations</li>
 *   <li><code>k(r) = r (p + q - r)</code> is the parameter count for rank <code>r</code></li>
 *   <li><code>r* = Σ rank(Z)</code> for <code>Z ∈ Pa(Y)</code>, the trek-implied rank</li>
 *   <li><code>p</code>, <code>q</code> are the sizes of the parent and target blocks</li>
 *   <li><code>nAdj</code> is the Bartlett-style effective sample size used in the CCA likelihood ratio</li>
 *   <li><code>c</code> is <code>coupledTrekPenalty</code> (the BIC penalty discount)</li>
 *   <li><code>mult</code> is <code>trekPenaltyMultiplier</code></li>
 * </ul>
 *
 * <p>
 * The first penalty term is the usual BIC complexity penalty for a rank-<code>r</code>
 * reduced-rank model. The second term softly encourages the canonical rank to match
 * the trek-implied rank <code>r*</code>, while still allowing the data to select a
 * different rank if strongly supported.
 * </p>
 *
 * <p>
 * Blocks whose rank is specified as <code>0</code> are treated as structurally isolated:
 * they cannot have parents and cannot act as parents of other variables.
 * </p>
 */
public class BlocksBicScoreTrekSoft implements Score, BlockScore, EffectiveSampleSizeSettable {

    private static final int SCORE_CACHE_MAX = 100_000;
    private static final int XBLOCK_CACHE_MAX = 50_000;

    private final List<Node> variables;
    private final Map<Node, Integer> nodeIndex;
    private final SimpleMatrix Sphi;
    private final int n;

    private final int[][] blockAllCols;

    private double trekPenaltyMultiplier = 4.0; // default: 1

    private final Map<FamilyKey, Double> scoreCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FamilyKey, Double> e) {
                    return size() > SCORE_CACHE_MAX;
                }
            };

    private final Map<ParentsKey, int[]> xblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ParentsKey, int[]> e) {
                    return size() > XBLOCK_CACHE_MAX;
                }
            };

    private final BlockSpec blockSpec;

    // --- Knobs (same as BlocksBicScore) ---
    private double penaltyDiscount = 1.0;
    private double ridge = 1e-8;
    private int nEff;

    /**
     * Constructs a BlocksBicScoreTrekSoft object with the specified block specification.
     * Initializes the variables, node indices, correlation matrix, and block column mappings
     * based on the given block specification.
     *
     * @param blockSpec the block specification containing dataset, block structure, and variables
     *                  used for initializing the object. Must not be null and should contain valid
     *                  blocks and variables to configure the score computations.
     * @throws NullPointerException if the provided block specification is null.
     * @throws IllegalArgumentException if any variable in the block variables is null,
     *                                  or if there are duplicate nodes in the block variables,
     *                                  or if any block references columns outside the dataset's
     *                                  bounds.
     */
    public BlocksBicScoreTrekSoft(BlockSpec blockSpec) {
        this.blockSpec = Objects.requireNonNull(blockSpec, "blockspec == null");

        int B = blockSpec.blocks().size();

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

        int D = blockSpec.dataSet().getNumColumns();
        this.blockAllCols = new int[B][];
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
        }
    }

    /**
     * Computes the local score for the given node index and its parent nodes.
     *
     * @param i the index of the target node for which the local score is computed.
     * @param parents the indices of the parent nodes influencing the target node.
     * @return the computed local score for the specified node and its parents.
     */
    @Override
    public double localScore(int i, int... parents) {
        Node y = variables.get(i);
        List<Node> _parents = new ArrayList<>();
        for (int parent : parents) _parents.add(variables.get(parent));
        return localScore(y, _parents);
    }

    /**
     * Computes the local score for a given node and its potential parent nodes.
     * The method calculates a scoring measure based on the relationships
     * specified, enforcing certain constraints such as rank-based exclusions
     * and penalties for overly complex parent-child relationships.
     *
     * @param y the target node for which the local score is computed.
     * @param parents the list of potential parent nodes influencing the target node.
     *                The parents list may be filtered to exclude invalid or
     *                disallowed nodes based on specific criteria (e.g., rank-0 blocks).
     * @return the computed local score. Returns negative infinity for invalid
     *         configurations or blocked relationships. A small negative value may
     *         also indicate penalties in some scenarios. Positive or zero scores represent
     *         valid and permissible relationships weighted by their associated scores.
     */
    public double localScore(Node y, List<Node> parents) {
        final int yi = idx(y);

        // --- Enforce "rank-0 blocks are isolated" semantics ---
        Integer yRkObj = blockSpec.ranks().get(yi);
        if (yRkObj == null) {
            throw new IllegalStateException("Missing rank for block index " + yi + " (node=" + y + ")");
        }
        final int yRank = yRkObj;
        if (yRank == 0) {
            // Rank-0 blocks: no parents allowed (and they shouldn't help others either; handled below for parents).
            return (parents == null || parents.isEmpty()) ? 0.0 : Double.NEGATIVE_INFINITY;
        }

        // Null baseline (no parents)
        if (parents == null || parents.isEmpty()) return 0.0;

        // --- Filter out rank-0 parents (they must not create edges) ---
        int[] parentIdxTmp = new int[parents.size()];
        int mParents = 0;
        for (Node pNode : parents) {
            int pi = idx(pNode);
            Integer prk = blockSpec.ranks().get(pi);
            if (prk == null) {
                throw new IllegalStateException("Missing rank for block index " + pi + " (parent node=" + pNode + ")");
            }
            if (prk == 0) continue; // ignore rank-0 parent blocks entirely
            parentIdxTmp[mParents++] = pi;
        }

        // If all proposed parents were rank-0, treat as empty parent set.
        if (mParents == 0) return 0.0;

        int[] parentIdx = Arrays.copyOf(parentIdxTmp, mParents);
        Arrays.sort(parentIdx);

        // Cache key uses filtered parent set (critical!)
        FamilyKey fkey = new FamilyKey(yi, parentIdx, ridge, penaltyDiscount, trekPenaltyMultiplier);
        Double cached = scoreCache.get(fkey);
        if (cached != null) return cached;

        // Y block
        int[] Yblock = blockFor(yi);
        if (Yblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // X block (concat of filtered parents)
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
        RankTests.RccaEntry ent = RankTests.getRccaEntry(Sphi, Xblock, Yblock, ridge);
        if (ent == null || ent.suffixLogs == null) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        int p = Xblock.length, q = Yblock.length;

        // Bartlett-style effective n for CCA LLR
        double nAdj = this.nEff - 1.0 - 0.5 * (p + q + 1.0);
        if (nAdj < 1.0) nAdj = 1.0;

        double[] suffix = ent.suffixLogs;
        int m = TMath.min(TMath.min(p, q), (int) nAdj - 1);
        m = TMath.min(m, suffix.length - 1);
        if (m <= 0) {
            scoreCache.put(fkey, -1e-12);
            return -1e-12;
        }

        // Trek-implied rank target r*
        int rStar = 0;
        for (int pi : parentIdx) {
            Integer rk = blockSpec.ranks().get(pi);
            if (rk == null) throw new IllegalStateException("Missing rank for block index " + pi);
            rStar += rk;
        }
        if (rStar < 0) rStar = 0;
        rStar = TMath.min(rStar, m);

        double best = Double.NEGATIVE_INFINITY;
        double suffix0 = suffix[0];

        double logN = TMath.log(TMath.max(nAdj, 2.0));

        // Sweep r = 1..m
        for (int r = 1; r <= m; r++) {
            double sumLogsTopR = suffix0 - suffix[r];
            double fit = -nAdj * sumLogsTopR;

            int k = r * (p + q - r);

            double cEff = penaltyDiscount * (yRank * yRank);

            double pen = cEff * k * logN;

            double trekPen = 0.0;
            if (penaltyDiscount > 0.0) {
                double d = (double) r - (double) rStar;
                trekPen = trekPenaltyMultiplier * cEff * logN * d * d;
            }

            System.out.println("r=" + r + " fit=" + fit + " pen=" + pen + " trekPen=" + trekPen);

            double sc = fit - pen - trekPen;
            if (Double.isNaN(sc) || Double.isInfinite(sc)) continue;
            if (sc > best) best = sc;
        }

        if (best <= 0.0) best = -1e-12;
        scoreCache.put(fkey, best);
        return best;
    }

    /**
     * Computes the difference in local scores for a specified target node and
     * its parent configurations. The method calculates the score for the target
     * node with an additional parent node `x` compared to the original parent
     * configuration `z`.
     *
     * @param x the index of the potential new parent node to be added.
     * @param y the index of the target node for which the score difference is computed.
     * @param z the array of indices representing the original parent nodes of the target node.
     * @return the difference in local scores between the configuration with
     *         the additional parent node `x` and the original configuration without `x`.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(variables.get(y), appendNodes(z, x)) - localScore(variables.get(y), z);
    }

    private double localScore(Node y, int[] parents) {
        List<Node> ps = new ArrayList<>(parents.length);
        for (int p : parents) ps.add(variables.get(p));
        return localScore(y, ps);
    }

    // --- knobs ---

    /**
     * Sets the penalty discount value used in score calculation.
     * The penalty discount adjusts the penalties applied during the
     * scoring process, allowing control over how penalties influence the
     * computed scores.
     *
     * @param c the penalty discount value to set. Must be a finite, non-negative number.
     *          Higher values reduce the impact of penalties, while lower values
     *          increase their effect during calculations.
     */
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        scoreCache.clear();
    }

    /**
     * Sets the penalty multiplier for trek mismatch penalties.
     * The multiplier scales the penalty applied during the computation
     * of scores when there are mismatches or inconsistencies in trek relationships.
     *
     * @param mult the penalty multiplier to set. Must be a finite, non-negative value.
     *             Values greater than zero increase the penalty weight,
     *             while zero disables the penalty effect.
     * @throws IllegalArgumentException if the provided value is not finite or is less than zero.
     */
    public void setTrekPenaltyMultiplier(double mult) {
        if (!Double.isFinite(mult) || mult < 0.0) {
            throw new IllegalArgumentException("trekPenaltyMultiplier must be finite and >= 0.");
        }
        this.trekPenaltyMultiplier = mult;
        scoreCache.clear();
    }

    /**
     * Sets the ridge parameter used in the scoring process.
     * The ridge parameter is typically used to enhance numerical stability
     * by adding a small regularization term in calculations involving
     * matrix inversions or linear regressions.
     *
     * This method also clears the cached scores as the ridge value change
     * might affect the computed scores.
     *
     * @param ridge the new ridge parameter value. Must be non-negative.
     */
    public void setRidge(double ridge) {
        this.ridge = ridge;
        scoreCache.clear();
    }

    /**
     * Retrieves the list of variables associated with this object.
     * The returned list represents the nodes or variables being managed
     * or utilized within the current context.
     *
     * @return a list of Node objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size of the dataset associated with the current block specification.
     *
     * @return the number of rows in the dataset.
     */
    @Override
    public int getSampleSize() {
        return blockSpec.dataSet().getNumRows();
    }

    /**
     * Retrieves the block specification associated with this score object.
     *
     * @return the BlockSpec object representing the block specification.
     */
    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    /**
     * Retrieves the effective sample size used in the score calculation.
     * This is a measure of the effective number of independent observations
     * used in the calculation, accounting for potential dependencies.
     *
     * @return the effective sample size.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size used in the score calculation.
     * This is a measure of the effective number of independent observations
     * used in the calculation, accounting for potential dependencies.
     *
     * @param nEff the effective sample size.
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.n : nEff;
        scoreCache.clear();
        xblockCache.clear();
    }

    // --- internals ---
    private int idx(Node v) {
        Integer i = nodeIndex.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node " + v);
        return i;
    }

    private int[] blockFor(int blockIndex) {
        return blockAllCols[blockIndex];
    }

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

    private List<Node> appendNodes(int[] parents, int x) {
        List<Node> list = new ArrayList<>(parents.length + 1);
        for (int p : parents) list.add(variables.get(p));
        list.add(variables.get(x));
        return list;
    }

    // --- cache keys ---
    private static final class FamilyKey {
        final int y;
        final int[] parents;
        final long ridgeBits, penBits, multBits;
        private final int hash;

        FamilyKey(int y, int[] parents, double ridge, double pen, double mult) {
            this.y = y;
            this.parents = parents.clone();
            this.ridgeBits = quantize(ridge);
            this.penBits = quantize(pen);
            this.multBits = quantize(mult);

            int h = 1;
            h = 31 * h + y;
            h = 31 * h + Arrays.hashCode(this.parents);
            h = 31 * h + Long.hashCode(ridgeBits);
            h = 31 * h + Long.hashCode(penBits);
            h = 31 * h + Long.hashCode(multBits);
            this.hash = h;
        }

        private static long quantize(double x) {
            return Double.doubleToLongBits(TMath.rint(x * 1e12) / 1e12);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FamilyKey fk)) return false;
            return y == fk.y
                    && ridgeBits == fk.ridgeBits
                    && penBits == fk.penBits
                    && multBits == fk.multBits
                    && Arrays.equals(parents, fk.parents);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class ParentsKey {
        final int[] parents;
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