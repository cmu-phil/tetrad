package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TMath;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * BlocksTrekBicScore:
 * A trek-aware blocks-based BIC-style local score that mirrors the split construction used in block trek-separation tests.
 * <p>
 * For a child block Y and parent blocks Z1..Zk:
 * - Split each Zi's observed indicators into ZiA/ZiB (random or deterministic).
 * - Form L = Y ∪ ZiA and R = ZiB.
 * - Compute RCCA singular values/canonical correlations for (L,R).
 * - Score ONLY the fixed target rank r* = sum_i rank(Zi) (from BlockSpec.ranks()).
 * <p>
 * Score(r*) = fit(r*) - penaltyDiscount * k(r*) * log(nEffAdj) - EBIC extra penalty (optional),
 * where k(r) = r * (p + q - r), p = |L|, q = |R|, and
 * fit(r) = -nEffAdj * sum_{j=1..r} log(1 - rho_j^2).
 * <p>
 * Multiple split trials can be used; we take the best score across trials.
 * <p>
 * NOTE: This is a heuristic score (like BlocksBicScore) but it explicitly incorporates the trek split structure.
 */
public class BlocksTrekBicScore implements Score, BlockScore, EffectiveSampleSizeSettable {

    // --- Caches ---
    private static final int SCORE_CACHE_MAX = 100_000;

    // --- Data / bookkeeping ---
    private final List<Node> variables;          // block-level variables
    private final Map<Node, Integer> nodeIndex;  // block node -> block index
    private final SimpleMatrix Sphi;             // correlation/cov of embedded data
    private final int n;                         // sample size

    // Precomputed embedded column arrays
    private final int[][] blockAllCols;          // per block -> all embedded cols
    private final int totalEmbeddedCols;

    private final BlockSpec blockSpec;
    /**
     * LRU cache for per-family scores (y, parents, knobs, split knobs).
     * Not thread-safe; wrap externally if using multi-threaded.
     */
    private final Map<FamilyKey, Double> scoreCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FamilyKey, Double> e) {
                    return size() > SCORE_CACHE_MAX;
                }
            };
    // --- Knobs (similar to BlocksBicScore) ---
    private double penaltyDiscount = 1.0;   // c
    private double ridge = 1e-8;            // regLambda for RankTests RCCA
    private double ebicGamma = 0.0;         // gamma for EBIC extra penalty (0 disables)
    private int nEff;
    // --- Split knobs (similar to IndTestBlocksTs) ---
    private boolean randomizeSplits = true;
    private long splitSeed = 17L;
    private int numTrials = 1; // best score over trials
    private boolean leftGetsSmallerHalfWhenOdd = true;

    /**
     * Constructs an instance of the {@code BlocksTrekBicScore} class using the provided block specification.
     * This constructor initializes internal structures required for computing scores, including
     * blocks, variables, correlation matrices, and column arrangements. It validates the block
     * specification and organizes the data for efficient computation.
     *
     * @param blockSpec The block specification defining the dataset, blocks, and variables to be used.
     *                  Must not be {@code null}. Blocks and variables must conform to the dataset
     *                  structure, and duplicate or invalid entries will trigger an exception.
     * @throws NullPointerException     If {@code blockSpec} is {@code null}.
     * @throws IllegalArgumentException If any variable in the block's variables list is {@code null},
     *                                  if variables contain duplicates, or if blocks reference columns
     *                                  outside the dataset dimensions.
     */
    public BlocksTrekBicScore(BlockSpec blockSpec) {
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

    // ------------------ Score API ------------------

    /**
     * Deterministic alternating split; if rng!=null, shuffle then half/half.
     */
    private static int[][] splitCols(int[] cols, Random rng, boolean leftGetsSmaller) {
        if (cols == null || cols.length == 0) return new int[][]{new int[0], new int[0]};
        int[] idx = Arrays.copyOf(cols, cols.length);

        if (rng == null) {
            // Alternate indices. If leftGetsSmaller and odd length, give left the smaller half (floor).
            boolean leftGetsOddPositions = leftGetsSmaller; // odd positions count = floor(n/2)
            int aCount = leftGetsOddPositions ? (idx.length / 2) : ((idx.length + 1) / 2);
            int bCount = idx.length - aCount;
            int[] A = new int[aCount];
            int[] B = new int[bCount];
            int ai = 0, bi = 0;
            for (int i = 0; i < idx.length; i++) {
                boolean toA = ((i & 1) == 1) == leftGetsOddPositions;
                if (toA) A[ai++] = idx[i];
                else B[bi++] = idx[i];
            }
            return new int[][]{A, B};
        } else {
            for (int i = idx.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = idx[i];
                idx[i] = idx[j];
                idx[j] = tmp;
            }
            int half = idx.length / 2; // smaller half size when odd
            int[] A = Arrays.copyOfRange(idx, 0, half);
            int[] B = Arrays.copyOfRange(idx, half, idx.length);
            return new int[][]{A, B};
        }
    }

    /**
     * Computes the local score for a given variable and its parent variables.
     * This method resolves the variable and parent indices into corresponding nodes
     * and calculates the score using another internal method.
     *
     * @param i       The index of the target variable whose local score is to be computed.
     * @param parents An array of indices representing the parent variables.
     * @return The local score for the specified variable and its parent variables.
     */
    @Override
    public double localScore(int i, int... parents) {
        Node y = variables.get(i);
        List<Node> ps = new ArrayList<>(parents.length);
        for (int p : parents) ps.add(variables.get(p));
        return localScore(y, ps);
    }

    /**
     * Computes the local score for a given node and its parent nodes in a directed structure.
     * The score evaluates the fit and statistical properties of the relationship between
     * the node and its parent set based on a variety of factors, including rank tests, penalties,
     * and structural constraints.
     *
     * @param y       the target node whose local score is being computed.
     * @param parents a list of parent nodes that form the conditional relationships with the target node.
     *                If this list is null or empty, a baseline score of 0.0 is returned.
     * @return the computed local score, which reflects the likelihood and fit of the given family configuration.
     * Scores may include penalties for complexity or constraints and reflect poor fit with negative values.
     */
    public double localScore(Node y, List<Node> parents) {
        int yi = idx(y);

        // Null baseline
        if (parents == null || parents.isEmpty()) return 0.0;

        // Build sorted parent indices
        int[] parentIdx = new int[parents.size()];
        for (int t = 0; t < parents.size(); t++) parentIdx[t] = idx(parents.get(t));
        Arrays.sort(parentIdx);

        // Include split knobs in cache key (since score depends on them)
        FamilyKey fkey = new FamilyKey(
                yi, parentIdx,
                ridge, penaltyDiscount, ebicGamma,
                randomizeSplits, splitSeed, numTrials, leftGetsSmallerHalfWhenOdd
        );

        Double cached = scoreCache.get(fkey);
        if (cached != null) return cached;

        int[] Yblock = blockFor(yi);
        if (Yblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // Target rank r* = sum ranks of parent blocks, per spec
        int target = 0;
        for (int pi : parentIdx) {
            Integer rk = blockSpec.ranks().get(pi);
            if (rk == null) throw new IllegalStateException("Missing rank for block index " + pi);
            target += rk;
        }
        if (target <= 0) {
            // With parents present but zero implied rank, treat as near-null to avoid ties
            double sc = -1e-12;
            scoreCache.put(fkey, sc);
            return sc;
        }

        // Try multiple split trials; take best score.
        double best = Double.NEGATIVE_INFINITY;
        long baseSeed = this.splitSeed;

        int trials = TMath.max(1, numTrials);
        for (int t = 0; t < trials; t++) {
            long seed = randomizeSplits ? (baseSeed + t) : baseSeed;

            // Build L/R using split: L = Y ∪ ZiA; R = ZiB
            BuildLR lr = buildLRForYAndParents(yi, parentIdx, seed);

            int[] L = lr.L;
            int[] R = lr.R;

            if (L.length == 0 || R.length == 0) continue;

            // RCCA entry
            RankTests.RccaEntry ent = RankTests.getRccaEntry(Sphi, L, R, ridge);
            if (ent == null || ent.suffixLogs == null) continue;

            int p = L.length, q = R.length;

            // Effective n adjustment (Bartlett-style for CCA LLR)
            double nAdj = this.nEff - 1.0 - 0.5 * (p + q + 1.0);
            if (nAdj < 1.0) nAdj = 1.0;

            // Max admissible rank
            int m = TMath.min(TMath.min(p, q), (int) nAdj - 1);
            m = TMath.min(m, ent.suffixLogs.length - 1); // need suffix[r]
            if (m <= 0) continue;

            // Clamp target rank to feasible range; if target > m, this parent set cannot realize the implied rank
            // under current split/sample, so score it harshly.
            int r = target;
            if (r > m) {
                // You can choose different behavior here. This is a firm penalty:
                // treat as poor fit because the implied rank can't even be tested.
                // Alternatively, set r=m and rely on penalty; but that changes the meaning.
                continue;
            }

            // fit(r) = -nAdj * (suffix[0] - suffix[r])
            double suffix0 = ent.suffixLogs[0];
            double sumLogsTopR = suffix0 - ent.suffixLogs[r];
            double fit = -nAdj * sumLogsTopR;

            int k = r * (p + q - r);

            double logN = TMath.log(TMath.max(nAdj, 2.0));
            double pen = penaltyDiscount * k * logN;

            if (ebicGamma > 0.0) {
                // Pool size excludes Y's own block
                int Ppool = TMath.max(totalEmbeddedCols - Yblock.length, 2);
                pen += 2.0 * ebicGamma * k * TMath.log(Ppool);
            }

            double sc = fit - pen;
            if (Double.isNaN(sc) || Double.isInfinite(sc)) continue;

            if (sc > best) best = sc;

            if (!randomizeSplits) break;
        }

        // If nothing feasible, return very poor score
        if (best == Double.NEGATIVE_INFINITY) best = Double.NEGATIVE_INFINITY;

        // Optional tiny nudge to avoid null ties
        if (best != Double.NEGATIVE_INFINITY && best <= 0.0) best = -1e-12;

        scoreCache.put(fkey, best);
        return best;
    }

    // ------------------ Knobs ------------------

    /**
     * Computes the difference in local scores when a new parent variable is added to the parent set of
     * a given target variable. This assesses the impact of adding the variable to the parent set on
     * the local score of the target variable.
     *
     * @param x The index of the candidate parent variable to be added.
     * @param y The index of the target variable whose local score is being evaluated.
     * @param z An array of indices representing the current parent set of the target variable.
     * @return The difference in local scores, calculated as the local score of the target variable
     * with the new parent set minus the local score with the original parent set.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(variables.get(y), appendNodes(z, x)) - localScore(variables.get(y), z);
    }

    /**
     * Sets the penalty discount factor for score computations in this instance.
     * The penalty discount is used to adjust the complexity penalty applied
     * during the scoring process. Updates to this value will clear the score
     * cache to ensure all subsequent computations reflect the new penalty factor.
     *
     * @param c The penalty discount value to set. Must be a non-negative double.
     *          A higher value results in lower penalty influence on the score.
     */
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        scoreCache.clear();
    }

    /**
     * Sets the ridge regularization parameter and clears the associated caches.
     * <p>
     * The ridge parameter controls the amount of regularization applied during
     * the computation, helping to prevent overfitting.
     *
     * @param ridge the regularization parameter to be set
     */
    public void setRidge(double ridge) {
        this.ridge = ridge;
        scoreCache.clear();
        // RankTests has its own cache keyed by regLambda too.
    }

    /**
     * Sets the penalty coefficient (gamma) used in the Extended Bayesian Information Criterion (EBIC) calculation.
     * This coefficient influences the trade-off between model complexity and goodness-of-fit.
     *
     * @param gamma the EBIC gamma value to set. Must be a non-negative double.
     */
    public void setEbicGamma(double gamma) {
        this.ebicGamma = gamma;
        scoreCache.clear();
    }

    /**
     * Sets whether to randomize the splits of columns when computing scores and specifies the seed
     * to be used by the randomization process.
     *
     * @param randomize A boolean indicating whether the splits should be randomized.
     * @param seed      A long value representing the seed for random number generation, used
     *                  when randomizing splits if {@code randomize} is set to {@code true}.
     */
    public void setRandomizeSplits(boolean randomize, long seed) {
        this.randomizeSplits = randomize;
        this.splitSeed = seed;
        scoreCache.clear();
    }

    /**
     * Sets the number of trials to be used for computations. The value must be a positive integer.
     * If the provided value is less than 1, an {@code IllegalArgumentException} is thrown.
     * This method also clears the score cache as the number of trials changes.
     *
     * @param t The number of trials to set. Must be greater than or equal to 1.
     * @throws IllegalArgumentException If the specified number of trials is less than 1.
     */
    public void setNumTrials(int t) {
        if (t < 1) throw new IllegalArgumentException("numTrials >= 1");
        this.numTrials = t;
        scoreCache.clear();
    }

    // ------------------ BlockScore / EffectiveSampleSizeSettable ------------------

    /**
     * Sets whether the left portion in column splits gets the smaller half when
     * the number of columns being split is odd. This behavior applies during
     * processes that involve dividing a set of columns into two groups.
     * Changing this flag also clears the score cache to ensure the changes
     * are reflected in future computations.
     *
     * @param flag A boolean value where {@code true} indicates that the left
     *             portion receives the smaller half during splits with an
     *             odd number of columns, and {@code false} indicates otherwise.
     */
    public void setLeftGetsSmallerHalfWhenOdd(boolean flag) {
        this.leftGetsSmallerHalfWhenOdd = flag;
        scoreCache.clear();
    }

    /**
     * Retrieves the block specification associated with this object.
     * The block specification defines how the columns or variables are
     * grouped and processed as part of score computations or other
     * related operations.
     *
     * @return The current block specification of type {@code BlockSpec}.
     */
    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    /**
     * Retrieves the sample size by returning the number of rows in the data set
     * associated with the block specification.
     *
     * @return the number of rows in the data set, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return blockSpec.dataSet().getNumRows();
    }

    /**
     * Retrieves the list of variables associated with this instance.
     * Each element in the list represents a specific variable used in
     * computations or operations within the current context.
     *
     * @return A list of {@code Node} objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the effective sample size used in computations.
     * This value typically represents an adjusted sample size
     * accounting for factors such as data structure or model complexity.
     *
     * @return the effective sample size as an integer.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    // ------------------ Internals ------------------

    /**
     * Sets the effective sample size to be used in computations. This value can influence
     * various calculations within the instance. If the provided value is less than zero,
     * the effective sample size is defaulted to the regular sample size.
     *
     * @param nEff The effective sample size to set. If less than zero, the effective
     *             sample size is reset to the total sample size.
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.n : nEff;
        scoreCache.clear();
    }

    private int idx(Node v) {
        Integer i = nodeIndex.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node " + v);
        return i;
    }

    private int[] blockFor(int blockIndex) {
        return blockAllCols[blockIndex];
    }

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

    private BuildLR buildLRForYAndParents(int yi, int[] sortedParents, long seed) {
        IntBuilder Lb = new IntBuilder();
        IntBuilder Rb = new IntBuilder();

        // L starts with Y block
        Lb.addAll(blockAllCols[yi]);

        Random rng = randomizeSplits ? new Random(seed) : null;

        for (int pi : sortedParents) {
            int[] Zcols = blockAllCols[pi];
            int[][] AB = splitCols(Zcols, rng, leftGetsSmallerHalfWhenOdd);
            Lb.addAll(AB[0]); // ZiA
            Rb.addAll(AB[1]); // ZiB
        }

        return new BuildLR(Lb.toArraySortedDistinct(), Rb.toArraySortedDistinct());
    }

    private static final class IntBuilder {
        private final BitSet bs = new BitSet();

        void addAll(int[] a) {
            if (a != null) for (int v : a) bs.set(v);
        }

        int[] toArraySortedDistinct() {
            int[] out = new int[bs.cardinality()];
            int k = 0;
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) out[k++] = i;
            return out;
        }
    }

    private record BuildLR(int[] L, int[] R) {
    }

    // ------------------ Cache key ------------------

    private static final class FamilyKey {
        final int y;
        final int[] parents; // sorted block indices

        final long ridgeBits;
        final long penBits;
        final long ebicBits;

        final boolean rand;
        final long seed;
        final int trials;
        final boolean leftSmallerOdd;

        private final int hash;

        FamilyKey(int y, int[] parents,
                  double ridge, double pen, double ebic,
                  boolean rand, long seed, int trials, boolean leftSmallerOdd) {
            this.y = y;
            this.parents = parents.clone();

            this.ridgeBits = quantize(ridge);
            this.penBits = quantize(pen);
            this.ebicBits = quantize(ebic);

            this.rand = rand;
            this.seed = seed;
            this.trials = trials;
            this.leftSmallerOdd = leftSmallerOdd;

            this.hash = computeHash();
        }

        private static long quantize(double x) {
            return Double.doubleToLongBits(TMath.rint(x * 1e12) / 1e12);
        }

        private int computeHash() {
            int h = 1;
            h = 31 * h + y;
            h = 31 * h + Arrays.hashCode(parents);
            h = 31 * h + Long.hashCode(ridgeBits);
            h = 31 * h + Long.hashCode(penBits);
            h = 31 * h + Long.hashCode(ebicBits);
            h = 31 * h + Boolean.hashCode(rand);
            h = 31 * h + Long.hashCode(seed);
            h = 31 * h + trials;
            h = 31 * h + Boolean.hashCode(leftSmallerOdd);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FamilyKey fk)) return false;
            return y == fk.y
                    && ridgeBits == fk.ridgeBits
                    && penBits == fk.penBits
                    && ebicBits == fk.ebicBits
                    && rand == fk.rand
                    && seed == fk.seed
                    && trials == fk.trials
                    && leftSmallerOdd == fk.leftSmallerOdd
                    && Arrays.equals(parents, fk.parents);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}