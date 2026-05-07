/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.*;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A block-level conditional independence test based on trek separation, operating under
 * linear latent factor measurement models. This class implements the Trek Separation
 * Blocks Independence (TSBI) test, elaborating on the {@link IndTestTrekSep} class of
 * Adam Brodie and Peter Spirtes.
 *
 * <h2>Setting</h2>
 * Variables are organized into <em>blocks</em>, where each block corresponds to a set
 * of observed indicator variables that measure a common latent factor. The independence
 * test operates at the block level: X, Y, and the conditioning set Z = {Z1, ..., Zk}
 * are each a block (latent factor), and the test assesses whether the latent factor
 * behind X is independent of the latent factor behind Y given the latent factors behind
 * Z1, ..., Zk.
 *
 * <h2>Test logic</h2>
 * The test is based on the rank of the cross-covariance matrix between two constructed
 * variable sets L and R:
 * <ol>
 *   <li>Each conditioning block Zi is split into two nearly equal halves ZiA and ZiB.</li>
 *   <li>The left side is formed as L = cols(X) ∪ cols(Z1A) ∪ ... ∪ cols(ZkA).</li>
 *   <li>The right side is formed as R = cols(Y) ∪ cols(Z1B) ∪ ... ∪ cols(ZkB).</li>
 *   <li>The rank of the cross-covariance submatrix Σ_{L,R} is estimated via
 *       {@link RankTests#estimateWilksRank}.</li>
 * </ol>
 * Under a linear measurement model with k conditioning latent factors, trek separation
 * implies that X ⊥ Y | Z1,...,Zk if and only if:
 * <pre>
 *   rank(Σ_{L,R}) = sum of ranks(Zi)
 * </pre>
 * where the rank of each Zi block is specified externally via {@link BlockSpec#ranks()}.
 * The test declares independence when the estimated rank equals this target sum.
 *
 * <h2>Splitting strategy</h2>
 * Each conditioning block Zi is split either deterministically or randomly:
 * <ul>
 *   <li>When {@code randomizeSplits=false}, the same fixed split (determined by
 *       {@code splitSeed}) is used for every query.</li>
 *   <li>When {@code randomizeSplits=true} (default), each trial uses a different
 *       seed derived from {@code splitSeed + trialIndex}, allowing the minimum rank
 *       over multiple trials to be taken. This reduces sensitivity to unlucky splits
 *       for odd-sized blocks.</li>
 * </ul>
 * When a block has an odd number of indicators, the {@code leftGetsSmallerHalfWhenOdd}
 * flag controls whether the left or right side receives the extra column.
 *
 * <h2>Multiple trials</h2>
 * When {@code numTrials > 1} and {@code randomizeSplits=true}, the test runs the full
 * rank estimation procedure {@code numTrials} times with different random splits and
 * takes the minimum estimated rank. This provides a more conservative (less likely to
 * falsely declare dependence) result when block sizes are small.
 *
 * <h2>Caching</h2>
 * Rank estimates are cached in a thread-safe LRU cache keyed by the left and right
 * column sets together with all hyperparameters that affect the rank estimate (sample
 * size, alpha, seed, randomize flag, and number of trials). The cache holds up to
 * {@value #RANK_CACHE_MAX} entries.
 *
 * <h2>P-values</h2>
 * This test does not produce p-values. All {@link IndependenceResult} objects returned
 * carry {@code NaN} for both the p-value and the alpha-minus-p fields. The independence
 * decision is made purely by comparing the estimated rank to the target rank.
 *
 * @see IndTestTrekSep
 * @see BlockSpec
 * @see RankTests
 * @see IndependenceTest
 * @see BlockTest
 */
public class TrekSeparationBlocksIndependence implements IndependenceTest, EffectiveSampleSizeSettable, BlockTest {

    // ---- Cache sizes (tune) ----
    private static final int RANK_CACHE_MAX = 400_000; // (L,R,n,alpha,splitSeed,randomize,numTrials)->rank

    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix S; // correlation (or covariance)
    private final int n;

    // Block -> observed column indices
    private final int[][] allCols;

    // LRUs
    private final LruMap<RKey, Integer> rankCache = new LruMap<>(RANK_CACHE_MAX);

    private final BlockSpec blockSpec;
    private final List<Node> dataVars;

    // knobs
    private double alpha = 0.01;
    private boolean verbose = false;

    // split knobs
    private boolean randomizeSplits = true;
    private long splitSeed = 17L;
    private int numTrials = 1; // take min rank over trials
    private boolean leftGetsSmallerHalfWhenOdd = true; // if true and |Zi| is odd, left gets floor(|Zi|/2)
    private int nEff;

    /**
     * Constructs an instance of IndTestBlocksTs using the provided block specification. Validates the input and
     * initializes various internal properties required for the block-based independence test, including correlation
     * matrix computation, variable mapping, and block configuration. Throws an exception if invalid configurations are
     * detected.
     *
     * @param blockSpec the block specification used for setting up the test. Contains information about the data set,
     *                  variables, and blocks. Must not be null.
     * @throws IllegalArgumentException if blockSpec is null or contains invalid configurations such as duplicate nodes,
     *                                  null variables, or invalid block column references.
     */
    public TrekSeparationBlocksIndependence(BlockSpec blockSpec) {
        if (blockSpec == null) throw new IllegalArgumentException("blockspec == null");

        for (Node v : blockSpec.dataSet().getVariables()) {
            if (v instanceof DiscreteVariable) {
                throw new IllegalArgumentException("TrekSep does not support discrete variables.");
            }
        }

        this.blockSpec = blockSpec;
        this.dataVars = blockSpec.dataSet().getVariables();

        this.variables = new ArrayList<>(blockSpec.blockVariables());
        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) {
            Node v = this.variables.get(j);
            if (v == null) throw new IllegalArgumentException("blockVariables[" + j + "] is null");
            if (nodesHash.put(v, j) != null) {
                throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
            }
        }
        this.nodeHash = nodesHash;

        this.n = blockSpec.dataSet().getNumRows();
        this.nEff = n;
        this.S = new CorrelationMatrix(blockSpec.dataSet()).getMatrix().getSimpleMatrix();

        final int B = blockSpec.blocks().size();
        final int D = blockSpec.dataSet().getNumColumns();
        this.allCols = new int[B][];
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blockSpec.blocks().get(b);
            if (cols == null || cols.isEmpty()) {
                allCols[b] = new int[0];
            } else {
                int[] a = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) {
                    int col = cols.get(k);
                    if (col < 0 || col >= D) {
                        throw new IllegalArgumentException("Block " + b + " references column " + col + " outside dataset width " + D);
                    }
                    a[k] = col;
                }
                allCols[b] = a;
            }
        }
    }

    // === Public API knobs (matching Lemma10 style) ===

    private static String zNames(Set<Node> z) {
        if (z == null || z.isEmpty()) return "{}";
        List<String> names = new ArrayList<>(z.size());
        for (Node n : z) names.add(n.getName());
        names.sort(NaturalSort.naturalComparator());;
        return "{" + String.join(",", names) + "}";
    }

    /**
     * Deterministic alternating split; if rng!=null, shuffle then half/half.
     */
    private static int[][] splitCols(int[] cols, boolean leftGetsSmaller, Random rng) {
        if (cols == null || cols.length == 0) {
            return new int[][]{new int[0], new int[0]};
        }

        int[] idx = Arrays.copyOf(cols, cols.length);

        // Fisher-Yates with the caller-supplied RNG.
        for (int i = idx.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }

        // leftGetsSmaller = true  → left gets floor(n/2), right gets ceil(n/2)
        // leftGetsSmaller = false → left gets ceil(n/2),  right gets floor(n/2)
        int leftSize = leftGetsSmaller
                ? idx.length / 2
                : (idx.length + 1) / 2;

        int[] A = Arrays.copyOfRange(idx, 0, leftSize);
        int[] B = Arrays.copyOfRange(idx, leftSize, idx.length);
        return new int[][]{A, B};
    }

    private static List<Node> indicesToNodes(int[] idxs, List<Node> all) {
        List<Node> out = new ArrayList<>(idxs.length);
        for (int i : idxs) out.add(all.get(i));
        return out;
    }

    /**
     * Retrieves the list of variable nodes associated with this instance.
     *
     * @return a new list containing the variable nodes.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the data model associated with the current block specification.
     *
     * @return the DataModel instance representing the data set associated with this block specification
     */
    @Override
    public DataModel getData() {
        return blockSpec.dataSet();
    }

    /**
     * Indicates whether verbose mode is enabled.
     *
     * @return true if verbose mode is enabled; false otherwise
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose mode for this instance.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the significance level (alpha) for the independence test.
     *
     * @return the significance level (alpha) for the independence test
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) for the independence test.
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0,1).");
        this.alpha = alpha;
    }

    /**
     * Retrieves the effective sample size for the independence test.
     *
     * @return the effective sample size for the independence test
     */
    @Override
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    /**
     * Sets the effective sample size for the independence test.
     *
     * @param effectiveSampleSize the effective sample size
     */
    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.nEff = effectiveSampleSize < 0 ? this.n : effectiveSampleSize;
    }

    /**
     * Sets whether to randomize the splits and specifies a seed for randomization.
     *
     * @param randomize a boolean indicating whether the splits should be randomized.
     * @param seed      a long value specifying the seed for randomization.
     */
    public void setRandomizeSplits(boolean randomize, long seed) {
        this.randomizeSplits = randomize;
        this.splitSeed = seed;
    }

    // === Core test ===

    /**
     * Sets the number of trials for the independence test.
     *
     * @param t the number of trials
     */
    public void setNumTrials(int t) {
        if (t < 1) throw new IllegalArgumentException("numTrials >= 1");
        this.numTrials = t;
    }

    // === Rank with trials ===

    /**
     * If true and |Zi| is odd, left gets floor(|Zi|/2); otherwise left gets ceil(|Zi|/2).
     *
     * @param flag a boolean indicating whether to use the smaller half when |Zi| is odd
     */
    public void setLeftGetsSmallerHalfWhenOdd(boolean flag) {
        this.leftGetsSmallerHalfWhenOdd = flag;
    }

    // === Rank via RankTests with LRU cache ===

    /**
     * Retrieves the block specification associated with this instance.
     *
     * @return the BlockSpec instance representing the current block specification.
     */
    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    // === Build L/R from blocks and Z split ===

    /**
     * Evaluates whether two nodes (variables) are independent given a set of conditioning nodes using a block-based
     * conditional independence test. The method uses ranks to determine independence, with the process involving random
     * splits and trials to enhance reliability.
     *
     * @param x the first node being tested for independence
     * @param y the second node being tested for independence
     * @param z the set of conditioning nodes
     * @return an {@code IndependenceResult} object containing the outcome of the independence test, including whether
     * the two nodes are independent given the conditioning set
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {

        // Read once; never written. Thread-safe.
        long baseSeed = this.splitSeed;

        int bestRank = Integer.MAX_VALUE;
        Build bestBuild = null;

        for (int trial = 0; trial < TMath.max(1, numTrials); trial++) {
            long effectiveSeed = randomizeSplits ? baseSeed + trial : baseSeed;
            Build b = buildSides(x, y, z, effectiveSeed);
            int r = getRank(b.Lcols, b.Rcols);

            if (r < bestRank) {
                bestRank = r;
                bestBuild = b;
            }

            if (!randomizeSplits) break;
        }

        if (blockSpec.ranks().get(nodeHash.get(x)) == 0 || blockSpec.ranks().get(nodeHash.get(y)) == 0) {
            boolean indep = true;

            return new IndependenceResult(
                    new IndependenceFact(x, y, z), indep, Double.NaN, Double.NaN);
        }

        // Defensive guard: reachable only if numTrials <= 0.
        if (bestBuild == null) {
            bestBuild = buildSides(x, y, z, baseSeed);
            bestRank = getRank(bestBuild.Lcols, bestBuild.Rcols);
        }

        // bestRank is already the minimum over all trials; no second pass needed.
        int estRank = bestRank;

        int target = 0;
        for (Node _z : z) {
            Integer i = nodeHash.get(_z);
            if (i == null) throw new IllegalArgumentException("Conditioning node not found: " + _z);
            Integer rk = blockSpec.ranks().get(i);
            if (rk == null) throw new IllegalStateException(
                    "Missing rank for block index " + i + " (node=" + _z + ")");
            target += rk;
        }

        boolean indep = estRank == target;

        if (verbose) {
            List<Node> leftVars  = indicesToNodes(bestBuild.Lcols, dataVars);
            List<Node> rightVars = indicesToNodes(bestBuild.Rcols, dataVars);
            TetradLogger.getInstance().log(
                    "TS split: left=" + leftVars + " right=" + rightVars);
            TetradLogger.getInstance().log(
                    "TS: " + bestBuild.xName + " _||_ " + bestBuild.yName
                            + " | " + bestBuild.zNames
                            + " ? estRank(min over trials)=" + estRank
                            + ", target(sum ranks)=" + target
                            + " -> " + (indep ? "INDEP" : "DEP"));
        }

        if (verbose) {
            if (indep) {
                System.out.println(LogUtilsSearch.independenceFactMsg(x, y, z, Double.NaN));
            } else {
                System.out.println(LogUtilsSearch.dependenceFactMsg(x, y, z, Double.NaN));
            }
        }

        return new IndependenceResult(
                new IndependenceFact(x, y, z), indep, Double.NaN, Double.NaN);
    }

    private int getRank(int[] L, int[] R) {
        RKey key = new RKey(L, R, nEff, alpha, splitSeed, randomizeSplits, numTrials);
        Integer cached = rankCache.get(key);
        if (cached != null) return cached;
        int rank = RankTests.estimateWilksRank(S, L, R, nEff, alpha);
        if (rank < 0) rank = 0;
        rankCache.put(key, rank);
        return rank;
    }

    private Build buildSides(Node x, Node y, Set<Node> z, long effectiveSeed) {
        Integer xiVar = nodeHash.get(x);
        Integer yiVar = nodeHash.get(y);
        if (xiVar == null || yiVar == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        int[] zVars = new int[z.size()];
        int t = 0;
        for (Node zn : z) {
            Integer idx = nodeHash.get(zn);
            if (idx == null)
                throw new IllegalArgumentException("Unknown conditioning node: " + zn);
            zVars[t++] = idx;
        }
        Arrays.sort(zVars);

        IntBuilder Lb = new IntBuilder();
        IntBuilder Rb = new IntBuilder();
        Lb.addAll(allCols[xiVar]);
        Rb.addAll(allCols[yiVar]);

        // One seeded RNG per call. Each Zi receives successive draws from the same
        // sequence, so the split is fully determined by effectiveSeed and the sorted
        // order of zVars. No global or instance state is touched.
        Random rng = new Random(effectiveSeed);

        for (int zv : zVars) {
            int[] Zcols = allCols[zv];
            int[][] AB = splitCols(Zcols, leftGetsSmallerHalfWhenOdd, rng);
            Lb.addAll(AB[0]);
            Rb.addAll(AB[1]);
        }

        int[] L = Lb.toArraySortedDistinct();
        int[] R = Rb.toArraySortedDistinct();

        return new Build(x, y, x.getName(), y.getName(), zNames(z), z, L, R, zVars.length);
    }

    // === Small utilities ===

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

    // === Small, thread-safe LRU (access-order) ===
    private static final class LruMap<K, V> {
        private final ReentrantLock lock = new ReentrantLock();
        private final int maxSize;
        private final LinkedHashMap<K, V> map;

        LruMap(int maxSize) {
            this.maxSize = TMath.max(16, maxSize);
            this.map = new LinkedHashMap<>(1024, 0.75f, true);
        }

        V get(K k) {
            lock.lock();
            try {
                return map.get(k);
            } finally {
                lock.unlock();
            }
        }

        void put(K k, V v) {
            lock.lock();
            try {
                map.put(k, v);
                while (map.size() > maxSize) {
                    Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    } else break;
                }
            } finally {
                lock.unlock();
            }
        }

        void clear() {
            lock.lock();
            try {
                map.clear();
            } finally {
                lock.unlock();
            }
        }
    }

    private record Build(Node xNode, Node yNode, String xName, String yName, String zNames, Set<Node> zSet, int[] Lcols,
                         int[] Rcols, int n) {
    }

    private static final class RKey {
        final int[] L;
        final int[] R;
        final int n;
        final long alphaBits;
        final long seed;
        final boolean rand;
        final int trials;
        private final int hash;

        RKey(int[] L, int[] R, int n, double alpha, long seed, boolean rand, int trials) {
            this.L = L.clone();
            this.R = R.clone();
            this.n = n;
            this.alphaBits = Double.doubleToLongBits(TMath.rint(alpha * 1e12) / 1e12);
            this.seed = seed;
            this.rand = rand;
            this.trials = trials;
            int h = 1;
            h = 31 * h + Arrays.hashCode(this.L);
            h = 31 * h + Arrays.hashCode(this.R);
            h = 31 * h + n;
            h = 31 * h + Long.hashCode(alphaBits);
            h = 31 * h + Long.hashCode(seed);
            h = 31 * h + Boolean.hashCode(rand);
            h = 31 * h + trials;
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RKey k)) return false;
            return n == k.n && alphaBits == k.alphaBits && seed == k.seed && rand == k.rand && trials == k.trials && Arrays.equals(L, k.L) && Arrays.equals(R, k.R);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
