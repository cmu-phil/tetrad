package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Trek-separation block-level CI test (IndTestBlocksTs):
 * <p>
 * Given blocks X, Y, and conditioning blocks Z1..Zk that correspond to latent factors [X], [Y], [Z1]..[Zk], split each
 * Zi into two nearly-equal parts ZiA, ZiB. Form L = X ∪ Z1A ∪ ... ∪ ZkA,   R = Y ∪ Z1B ∪ ... ∪ ZkB and estimate
 * rank(Σ_{L,R}). Under linear measurement models with n conditioning latents, independence suggests rank(Σ_{L,R}) ≤
 * 2k.
 * <p>
 * Drop-in replacement matching the public surface of IndTestBlocksLemma10 (no p-values exposed).
 */
public class IndTestBlocksTs implements IndependenceTest, BlockTest {

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

    /**
     * Construct from a BlockSpec (same pattern as Lemma 10 test).
     */
    public IndTestBlocksTs(BlockSpec blockSpec) {
        if (blockSpec == null) throw new IllegalArgumentException("blockspec == null");
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
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

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

    private static List<Node> indicesToNodes(int[] idxs, List<Node> all) {
        List<Node> out = new ArrayList<>(idxs.length);
        for (int i : idxs) out.add(all.get(i));
        return out;
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return blockSpec.dataSet();
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0,1).");
        this.alpha = alpha;
    }

    /**
     * Enable randomized Zi splits (min over {@code numTrials} trials).
     */
    public void setRandomizeSplits(boolean randomize, long seed) {
        this.randomizeSplits = randomize;
        this.splitSeed = seed;
    }

    // === Core test ===

    /**
     * Number of split trials; min-rank across trials is used.
     */
    public void setNumTrials(int t) {
        if (t < 1) throw new IllegalArgumentException("numTrials >= 1");
        this.numTrials = t;
    }

    // === Rank with trials ===

    /**
     * If true and |Zi| is odd, left gets floor(|Zi|/2); otherwise left gets ceil(|Zi|/2).
     */
    public void setLeftGetsSmallerHalfWhenOdd(boolean flag) {
        this.leftGetsSmallerHalfWhenOdd = flag;
    }

    // === Rank via RankTests with LRU cache ===

    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    // === Build L/R from blocks and Z split ===

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {

        // Build sides L, R using split of each Zi
        long baseSeed = this.splitSeed;
        int bestRank = Integer.MAX_VALUE;
        Build bestBuild = null;

        for (int trial = 0; trial < Math.max(1, numTrials); trial++) {
            if (randomizeSplits) this.splitSeed = baseSeed + trial;
            Build b = buildSides(x, y, z);
            int r = getRank(b.Lcols, b.Rcols);
            if (r < bestRank) {
                bestRank = r;
                bestBuild = b; // keep the winner
            }
            if (!randomizeSplits) break;
        }
        this.splitSeed = baseSeed;

        // use bestBuild for verbose and result context
        Build b = (bestBuild != null) ? bestBuild : buildSides(x, y, z);

        if (verbose) {
            List<Node> leftVars = indicesToNodes(b.Lcols, dataVars);
            List<Node> rightVars = indicesToNodes(b.Rcols, dataVars);
            System.out.println("TS split: left=" + leftVars + " right=" + rightVars);
        }

        // Estimate rank for Σ_{L,R}
        int estRank = getRankMinOverTrials(b.Lcols, b.Rcols);

        int k = b.n;

        int target = 0;
        for (Node _z : z) {
            Integer i = nodeHash.get(_z);
            if (i == null) throw new IllegalArgumentException("Conditioning node not found: " + _z);
            Integer rk = blockSpec.ranks().get(i);
            if (rk == null) throw new IllegalStateException("Missing rank for block index " + i + " (node=" + _z + ")");
            target += rk;
        }

        boolean indep = estRank == k;

        if (verbose) {
            System.out.printf("TS: %s _||_ %s | %s ? estRank(min over trials)=%d, target(sum ranks)=%d -> %s%n",
                    b.xName, b.yName, b.zNames, bestRank, target, indep ? "INDEP" : "DEP");
        }

        return new IndependenceResult(
                new IndependenceFact(b.xNode, b.yNode, z),
                indep,
                Double.NaN, // p-value intentionally not exposed
                Double.NaN  // score not used
        );
    }

    private int getRankMinOverTrials(int[] L, int[] R) {
        int best = Integer.MAX_VALUE;
        for (int t = 0; t < numTrials; t++) {
            int[] Lt = L, Rt = R;
            // When randomizing, re-split the Z blocks inside buildSides(); here we only cache by (L,R,alpha,...)
            int r = getRank(Lt, Rt);
            if (r < best) best = r;
            if (!randomizeSplits) break; // deterministic: only one pass
        }
        return best;
    }

    private int getRank(int[] L, int[] R) {
        RKey key = new RKey(L, R, n, alpha, splitSeed, randomizeSplits, numTrials);
        Integer cached = rankCache.get(key);
        if (cached != null) return cached;
        int rank = RankTests.estimateWilksRank(S, L, R, n, alpha);
        if (rank < 0) rank = 0;
        rankCache.put(key, rank);
        return rank;
    }

    private Build buildSides(Node x, Node y, Set<Node> z) {
        Integer xiVar = nodeHash.get(x);
        Integer yiVar = nodeHash.get(y);
        if (xiVar == null || yiVar == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        // sorted Z block indices
        int[] zVars = new int[z.size()];
        int t = 0;
        for (Node zn : z) {
            Integer idx = nodeHash.get(zn);
            if (idx == null) throw new IllegalArgumentException("Unknown conditioning node: " + zn);
            zVars[t++] = idx;
        }
        Arrays.sort(zVars);

        // Start with X on L and Y on R
        IntBuilder Lb = new IntBuilder();
        IntBuilder Rb = new IntBuilder();
        Lb.addAll(allCols[xiVar]);
        Rb.addAll(allCols[yiVar]);

        // Split each Zi into (Ai,Bi) and append
        Random rng = randomizeSplits ? new Random(splitSeed) : null;
        for (int zv : zVars) {
            int[] Zcols = allCols[zv];
            int[][] AB = splitCols(Zcols, rng, leftGetsSmallerHalfWhenOdd);
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
            this.maxSize = Math.max(16, maxSize);
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

    private record Build(Node xNode, Node yNode,
                         String xName, String yName, String zNames,
                         Set<Node> zSet, int[] Lcols, int[] Rcols, int n) {
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
            this.alphaBits = Double.doubleToLongBits(Math.rint(alpha * 1e12) / 1e12);
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