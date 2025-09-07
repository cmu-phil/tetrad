package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Utility class for handling operations related to blocks, such as creating block variables, canonicalizing blocks,
 * ensuring valid indices, and applying various cluster policies. This class includes methods to manipulate and process
 * blocks and their corresponding data representations within a dataset.
 */
public final class BlocksUtil {
    private BlocksUtil() {
    }

    /**
     * Creates a list of block variables based on the provided list of blocks and the dataset. If a block contains a
     * single index, the corresponding variable from the dataset is added to the result. For larger blocks, a new latent
     * variable is created and added to the result.
     *
     * @param blocks  a list of lists, where each inner list represents a block of indices
     * @param dataSet the dataset associated with the specified blocks, providing the variables
     * @return a list of Node objects representing the block variables, either existing or newly created
     */
    public static List<Node> makeBlockVariables(List<List<Integer>> blocks, DataSet dataSet) {
        int latentIndex = 1;
        List<Node> meta = new ArrayList<>();
        for (List<Integer> block : blocks) {
            if (block.size() == 1) {
                meta.add(dataSet.getVariable(block.getFirst()));
            } else {
                ContinuousVariable latent = new ContinuousVariable("L" + latentIndex++);
                latent.setNodeType(NodeType.LATENT);
                meta.add(latent);
            }
        }

        return meta;
    }

    /**
     * Canonicalizes a list of blocks by removing null or empty blocks, sorting the contents of each block, and ensuring
     * the resulting blocks are unique. The returned list maintains the order of the first occurrence of each unique
     * block.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to canonicalize
     * @return a list of canonicalized blocks that are non-empty, sorted internally, and unique in order
     */
    public static List<List<Integer>> canonicalizeBlocks(List<List<Integer>> blocks) {
        LinkedHashSet<List<Integer>> uniq = new LinkedHashSet<>();
        for (List<Integer> b : blocks) {
            if (b == null || b.isEmpty()) continue;
            List<Integer> s = new ArrayList<>(b);
            Collections.sort(s);
            uniq.add(Collections.unmodifiableList(s));
        }
        return new ArrayList<>(uniq);
    }

    /**
     * Validates the provided list of blocks to ensure that all indices within each block are non-negative, within the
     * range of columns in the given dataset, and not null. Throws an IllegalArgumentException if any of these
     * conditions are violated.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to validate
     * @param data   the dataset providing the number of columns for range validation
     */
    public static void validateBlocks(List<List<Integer>> blocks, DataSet data) {
        int p = data.getNumColumns();
        for (List<Integer> b : blocks) {
            for (Integer v : b) {
                if (v == null || v < 0 || v >= p) {
                    throw new IllegalArgumentException("Block contains out-of-range index: " + v);
                }
            }
        }
    }

    /**
     * Converts a list of block indices and a dataset into a BlockSpec object, ensuring the blocks are canonicalized and
     * generating the appropriate block variables.
     *
     * @param blocks  a list of lists, where each inner list represents a block of indices
     * @param dataSet the dataset associated with the blocks
     * @return a BlockSpec object containing the dataset, canonicalized blocks, and block variables
     */
    public static BlockSpec toSpec(List<List<Integer>> blocks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(dataSet, canon, makeBlockVariables(canon, dataSet));
    }

    /**
     * Converts a list of blocks, ranks, and a dataset into a BlockSpec object. The blocks are canonicalized to ensure
     * uniformity, and block variables are generated based on the canonicalized blocks and dataset.
     *
     * @param blocks  a list of lists, where each inner list represents a block of indices
     * @param ranks   a list of integers representing the ranks associated with the blocks
     * @param dataSet the dataset associated with the blocks, providing the variables for block creation
     * @return a BlockSpec object containing the dataset, canonicalized blocks, block variables, and ranks
     */
    public static BlockSpec toSpec(List<List<Integer>> blocks, List<Integer> ranks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(dataSet, canon, makeBlockVariables(canon, dataSet), ranks);
    }

    /**
     * Expand ranks -> per-latent variables named Lk-1..Lk-r.
     *
     * @param spec the BlockSpec object containing the block variables to expand
     * @return the expanded list of Node objects
     */
    public static List<Node> expandLatents(BlockSpec spec) {
        List<Node> expanded = new ArrayList<>();
        for (int i = 0; i < spec.blocks().size(); i++) {
            int r = spec.ranks().get(i);
            String baseName = spec.blockVariables().get(i).getName();
            if (spec.blocks().get(i).size() == 1 && r == 1) {
                // singleton: just pass through observed Node
                expanded.add(spec.blockVariables().get(i));
            } else {
                for (int k = 1; k <= r; k++) {
                    var L = new ContinuousVariable(baseName + "-" + k);
                    L.setNodeType(NodeType.LATENT);
                    expanded.add(L);
                }
            }
        }
        return expanded;
    }

    /**
     * Creates a list of disjoint blocks from the provided list of blocks, prioritizing larger blocks first. Each block
     * is processed to ensure no overlapping indices, and elements within processed blocks are sorted. The resulting
     * list is unmodifiable and contains unique, disjoint, and sorted blocks.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to be made disjoint
     * @return a list of disjoint blocks, where each block is a sorted and unmodifiable list of indices
     */
    public static List<List<Integer>> makeDisjointBySize(List<List<Integer>> blocks) {
        // Sort by descending size; work on copies so we don’t mutate inputs
        List<ArrayList<Integer>> sorted = blocks.stream()
                .sorted((a, b) -> Integer.compare(b.size(), a.size()))
                .map(ArrayList::new)
                .toList();

        BitSet used = new BitSet();
        List<List<Integer>> out = new ArrayList<>();

        for (List<Integer> block : sorted) {
            // Drop indices already used by earlier (bigger) blocks
            List<Integer> pruned = new ArrayList<>(block.size());
            for (Integer v : block) {
                if (v != null && !used.get(v)) {
                    pruned.add(v);
                }
            }
            if (!pruned.isEmpty()) {
                // Mark these indices as used and keep this pruned block
                for (int v : pruned) used.set(v);
                // Optional: sort within-block for determinism
                Collections.sort(pruned);
                out.add(Collections.unmodifiableList(pruned));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Constructs a BlockSpec object using the provided DataSet and block definitions, ensuring that the blocks are made
     * disjoint by prioritizing larger blocks first. The resulting BlockSpec includes the dataset, the disjoint blocks,
     * and associated block variables.
     *
     * @param ds     the dataset associated with the blocks
     * @param blocks a list of lists, where each inner list represents a block of indices
     * @return a BlockSpec object containing the dataset, disjoint blocks, and block variables
     */
    public static BlockSpec makeDisjointSpec(DataSet ds, List<List<Integer>> blocks) {
        List<List<Integer>> disjoint = makeDisjointBySize(blocks);
        List<Node> blockVars = makeBlockVariables(disjoint, ds); // your existing helper
        return new BlockSpec(ds, disjoint, blockVars); // ranks default to 1s
    }

    /**
     * Applies a single-cluster policy to the provided BlockSpec. Depending on the specified policy, the method modifies
     * the blocks, ranks, and variables in the BlockSpec and returns a new BlockSpec object.
     *
     * @param blockSpec the BlockSpec object containing the current block configuration, ranks, and dataset
     * @param policy    the SingleClusterPolicy to apply, which determines how unused columns or variables are handled
     *                  (e.g., INCLUDE, EXCLUDE, NOISE_VAR)
     * @param alpha     a double value representing a parameter used in the computation of ranks
     * @return a new BlockSpec object that reflects the changes made according to the specified policy
     */
    public static BlockSpec applySingleClusterPolicy(
            BlockSpec blockSpec, SingleClusterPolicy policy, double alpha
    ) {
        final DataSet dataSet = blockSpec.dataSet();
        final List<List<Integer>> blocks = blockSpec.blocks();

        // --- normalize ranks (may be null) ---
        final List<Integer> ranksIn = blockSpec.ranks();
        final List<Integer> ranksNorm = new ArrayList<>(blocks.size());
        if (ranksIn != null && !ranksIn.isEmpty()) {
            for (int i = 0; i < blocks.size(); i++) {
                Integer r = i < ranksIn.size() ? ranksIn.get(i) : 1;
                ranksNorm.add((r == null || r < 0) ? 1 : 0);
            }
        } else {
            for (int i = 0; i < blocks.size(); i++) ranksNorm.add(1);
        }

        // --- start output with current blocks/latents/ranks ---
        final List<List<Integer>> outBlocks = new ArrayList<>(blocks.size() + 8);
        for (List<Integer> b : blocks) outBlocks.add(new ArrayList<>(b));

        final List<Node> inLatents = blockSpec.blockVariables();
        final List<Node> outLatents = new ArrayList<>(blocks.size() + 8);
        final Set<String> takenNames = new HashSet<>();
        final Set<String> observedNames = new HashSet<>();
        for (Node v : dataSet.getVariables()) observedNames.add(v.getName());

        if (inLatents != null && !inLatents.isEmpty()) {
            for (Node n : inLatents) {
                outLatents.add(n);
                takenNames.add(n.getName());
            }
        } else {
            // synthesize L1, L2, ... for existing blocks if none present
            for (int i = 0; i < blocks.size(); i++) {
                String nm = ensureUnique("L" + (i + 1), takenNames, observedNames);
                Node latent = new edu.cmu.tetrad.data.ContinuousVariable(nm);
                latent.setNodeType(edu.cmu.tetrad.graph.NodeType.LATENT);
                outLatents.add(latent);
                takenNames.add(nm);
            }
        }

        final List<Integer> outRanks = new ArrayList<>(ranksNorm);

        // --- precompute correlation once ---
        final edu.cmu.tetrad.data.CorrelationMatrix corr = new edu.cmu.tetrad.data.CorrelationMatrix(dataSet);
        final org.ejml.simple.SimpleMatrix S = corr.getMatrix().getSimpleMatrix();
        final int n = dataSet.getNumRows();
        final int p = dataSet.getNumColumns();

        // compute used / unused
        final Set<Integer> used = new HashSet<>();
        for (List<Integer> b : blocks) used.addAll(b);
        final List<Integer> all = new ArrayList<>(p);
        for (int i = 0; i < p; i++) all.add(i);
        final LinkedHashSet<Integer> unused = new LinkedHashSet<>(all);
        unused.removeAll(used);

        switch (policy) {
            case INCLUDE -> {
                if (unused.isEmpty()) return blockSpec;

                // others = all minus the singletons as we add them (OK to use full others each time)
                final int[] others = toIndexArray(allMinus(unused, all)); // others = all \ unused
                for (int idx : unused) {
                    List<Integer> newBlock = Collections.singletonList(idx);
                    outBlocks.add(newBlock);

                    // latent name "S_<Var>" but ensure uniqueness vs existing
//                    String base = "S_" + sanitize(dataSet.getVariable(idx).getName());
//                    String name = ensureUnique(base, takenNames, observedNames);

//                    Node latent = new edu.cmu.tetrad.data.ContinuousVariable(name);
//                    latent.setNodeType(edu.cmu.tetrad.graph.NodeType.LATENT);
                    Node variable = dataSet.getVariable(idx);
                    outLatents.add(variable);
                    takenNames.add(variable.getName());

                    int rk = estimateRankSafe(S, n, newBlock, others, alpha);
                    outRanks.add(Math.max(0, rk));
                }

                return new BlockSpec(dataSet, outBlocks, outLatents, outRanks);
            }

            case EXCLUDE -> {
                return blockSpec;
            }

            case NOISE_VAR -> {
                if (unused.isEmpty()) return blockSpec;

                List<Integer> noise = new ArrayList<>(unused);
                outBlocks.add(noise);

                // name "Noise" but unique
                String noiseName = ensureUnique("Noise", takenNames, observedNames);
                Node latent = new edu.cmu.tetrad.data.ContinuousVariable(noiseName);
                latent.setNodeType(edu.cmu.tetrad.graph.NodeType.LATENT);
                outLatents.add(latent);
                takenNames.add(noiseName);

                int[] others = toIndexArray(allMinus(unused, all));
                int rk = estimateRankSafe(S, n, noise, others, alpha);
                outRanks.add(Math.max(0, rk));

                return new BlockSpec(dataSet, outBlocks, outLatents, outRanks);
            }

            default -> throw new IllegalArgumentException("Unknown policy: " + policy);
        }
    }

    // ---------- helpers ----------

    // Safe rank: if others empty → unconditioned fallback; singleton → 1.
    private static int estimateRankSafe(
            org.ejml.simple.SimpleMatrix S, int nRows,
            List<Integer> block, int[] others, double alpha) {
        if (block == null || block.isEmpty()) return 0; // empty → 0
        int[] blk = toIndexArray(block);

        if (others != null && others.length > 0) {
            return Math.max(0, edu.cmu.tetrad.util.RankTests
                    .estimateWilksRank(S, blk, others, nRows, alpha));
        } else {
            return Math.max(0, edu.cmu.tetrad.util.RankTests
                    .estimateWilksRank(S, blk, new int[0], nRows, alpha));
        }
    }

    private static int[] toIndexArray(Collection<Integer> list) {
        int[] a = new int[list.size()];
        int k = 0;
        for (int v : list) a[k++] = v;
        return a;
    }

    private static List<Integer> allMinus(Collection<Integer> minus, List<Integer> all) {
        List<Integer> out = new ArrayList<>(all.size());
        for (int x : all) if (!minus.contains(x)) out.add(x);
        return out;
    }

    private static String sanitize(String s) {
        return s == null ? "X" : s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static String ensureUnique(String base, Set<String> taken, Set<String> observed) {
        String b = (base == null || base.isEmpty()) ? "L" : base;
        if (!taken.contains(b) && !observed.contains(b)) return b;
        int k = 2;
        while (taken.contains(b + "-" + k) || observed.contains(b + "-" + k)) k++;
        return b + "-" + k;
    }

    /**
     * Assigns meaningful names to latent variables in the provided BlockSpec object based on the given true clusters
     * and the specified naming mode. This helps in creating more interpretable and user-friendly block specifications.
     *
     * @param spec         the BlockSpec object containing the initial latent variable definitions
     * @param trueClusters a map where keys represent cluster names and values are lists of variable names associated
     *                     with each cluster
     * @param mode         the NamingMode specifying how the latent variables should be named
     * @return a BlockSpec object with updated latent variable names based on the true clusters and naming mode
     */
    public static BlockSpec giveGoodLatentNames(BlockSpec spec,
                                                Map<String, List<String>> trueClusters,
                                                NamingMode mode) {
        return LatentNameAssigner.giveGoodLatentNames(spec, trueClusters, mode);
    }

    public enum NamingMode {LEARNED_SINGLE, SIMULATION_EXPANDED}
}