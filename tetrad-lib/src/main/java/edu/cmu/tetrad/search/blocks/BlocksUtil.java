package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.utils.ClusterSignificance;

import java.util.*;

/**
 * Utility class for handling operations related to blocks, such as creating block variables,
 * canonicalizing blocks, ensuring valid indices, and applying various cluster policies.
 * This class includes methods to manipulate and process blocks and their corresponding data
 * representations within a dataset.
 */
public final class BlocksUtil {
    private BlocksUtil() {}

    /**
     * Creates a list of block variables based on the provided list of blocks and the dataset.
     * If a block contains a single index, the corresponding variable from the dataset is added
     * to the result. For larger blocks, a new latent variable is created and added to the result.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices
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
                ContinuousVariable latent = new ContinuousVariable("L" +  latentIndex++);
                latent.setNodeType(NodeType.LATENT);
                meta.add(latent);
            }
        }

        return meta;
    }

    /**
     * Canonicalizes a list of blocks by removing null or empty blocks, sorting the contents of
     * each block, and ensuring the resulting blocks are unique. The returned list maintains
     * the order of the first occurrence of each unique block.
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
     * Validates the provided list of blocks to ensure that all indices within each block
     * are non-negative, within the range of columns in the given dataset, and not null.
     * Throws an IllegalArgumentException if any of these conditions are violated.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices to validate
     * @param data the dataset providing the number of columns for range validation
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
     * Converts a list of block indices and a dataset into a BlockSpec object, ensuring the blocks
     * are canonicalized and generating the appropriate block variables.
     *
     * @param blocks a list of lists, where each inner list represents a block of indices
     * @param dataSet the dataset associated with the blocks
     * @return a BlockSpec object containing the dataset, canonicalized blocks, and block variables
     */
    public static BlockSpec toSpec(List<List<Integer>> blocks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(dataSet, canon, makeBlockVariables(canon, dataSet));
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
     * Creates a list of disjoint blocks from the provided list of blocks, prioritizing larger blocks first.
     * Each block is processed to ensure no overlapping indices, and elements within processed blocks are sorted.
     * The resulting list is unmodifiable and contains unique, disjoint, and sorted blocks.
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
     * Constructs a BlockSpec object using the provided DataSet and block definitions, ensuring that
     * the blocks are made disjoint by prioritizing larger blocks first. The resulting BlockSpec includes
     * the dataset, the disjoint blocks, and associated block variables.
     *
     * @param ds the dataset associated with the blocks
     * @param blocks a list of lists, where each inner list represents a block of indices
     * @return a BlockSpec object containing the dataset, disjoint blocks, and block variables
     */
    public static BlockSpec makeDisjointSpec(DataSet ds, List<List<Integer>> blocks) {
        List<List<Integer>> disjoint = makeDisjointBySize(blocks);
        List<Node> blockVars = makeBlockVariables(disjoint, ds); // your existing helper
        return new BlockSpec(ds, disjoint, blockVars); // ranks default to 1s
    }

    /**
     * Applies the specified single cluster policy to the given set of blocks.
     * Depending on the policy, this method may modify or extend the blocks to include
     * singleton clusters, exclude them, or group them as noise variables.
     *
     * @param policy the single cluster policy to apply, determining how singleton clusters are handled
     * @param blocks a list of lists where each inner list represents a cluster of indices
     * @param dataSet the dataset associated with the blocks, providing information about column indices
     * @return an unmodifiable list of lists representing the updated or unchanged clusters based on the specified policy
     * @throws IllegalArgumentException if an unknown policy is provided
     */
    public static List<List<Integer>> applySingleClusterPolicy(SingleClusterPolicy policy, List<List<Integer>> blocks, DataSet dataSet) {
        switch (policy) {
            case INCLUDE -> {
                Set<Integer> used =  new HashSet<>();
                for (List<Integer> block : blocks) {
                    used.addAll(block);
                }

                List<List<Integer>> out = new ArrayList<>(blocks);

                List<Integer> all = new ArrayList<>();
                for (int i = 0; i < dataSet.getNumColumns(); i++) {
                    all.add(i);
                }

                Set<Integer> unused = new HashSet<>(all);
                unused.removeAll(used);

                for (int i : unused) {
                    out.add(Collections.singletonList(i));
                }

                return Collections.unmodifiableList(out);
            }
            case EXCLUDE -> {
                return Collections.unmodifiableList(blocks);
            }
            case NOISE_VAR -> {
                Set<Integer> used =  new HashSet<>();
                for (List<Integer> block : blocks) {
                    used.addAll(block);
                }

                List<List<Integer>> out = new ArrayList<>(blocks);

                List<Integer> all = new ArrayList<>();
                for (int i = 0; i < dataSet.getNumColumns(); i++) {
                    all.add(i);
                }

                Set<Integer> unused = new HashSet<>(all);
                unused.removeAll(used);

                out.add(new ArrayList<>(unused));
                return Collections.unmodifiableList(out);
            }
            default -> throw new IllegalArgumentException("Unknown policy: " + policy);
        }
    }

    /**
     * Renames the last variable in the block variables of the given BlockSpec to "Noise".
     * This method modifies the name of the last variable while preserving other aspects of the BlockSpec.
     *
     * @param spec the BlockSpec object containing the block variables to modify
     * @return a new BlockSpec object with the last variable renamed to "Noise"
     */
    public static BlockSpec renameLastVarAsNoise(BlockSpec spec) {
        List<Node> blockVars = spec.blockVariables();
        Node noise = blockVars.getLast();
        noise.setName("Noise");
        return new BlockSpec(spec.dataSet(), spec.blocks(), blockVars, List.copyOf(spec.ranks()));
    }

    public static BlockSpec giveGoodLatentNames(BlockSpec spec, Map<String, List<String>> trueClusters) {
        List<List<Integer>> blocks = spec.blocks();
        DataSet dataSet = spec.dataSet();
        
        List<Node> dataVars = dataSet.getVariables();
        
        List<String> newNames = new ArrayList<>();

        for (List<Integer> block : blocks) {
            List<Node> blockNodes = ClusterSignificance.variablesForIndices(block, dataVars);
            List<String> blockNames = new ArrayList<>();
            for (Node blockNode : blockNodes) {
                blockNames.add(blockNode.getName());
            }

            StringBuilder newName = new StringBuilder();

            for (String trueName : trueClusters.keySet()) {
                List<String> cluster = trueClusters.get(trueName);

                if (new HashSet<>(cluster).containsAll(blockNames)) {
                    if (!newName.isEmpty()) {
                        newName.append("-");
                    }

                    newName.append(trueName);
                }
            }

            if (newNames.contains(newName.toString())) {
                long count = newNames.stream().filter(name -> name.startsWith(newName.toString())).count();
                newName.append("-").append(count + 1);
            }

            newNames.add(newName.toString());
        }

        List<Node> newLatents = new ArrayList<>();

        for (String name : newNames) {
            Node newLatent = new ContinuousVariable(name);
            newLatent.setNodeType(NodeType.LATENT);
            newLatents.add(newLatent);
        }
        
        return new BlockSpec(dataSet, blocks, newLatents);
    }
}