package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public final class BlocksUtil {
    private BlocksUtil() {}

    /** Default latent/block names: L1, L2, ... (or pass a custom namer). */
    public static List<Node> makeBlockVariables(List<List<Integer>> blocks, DataSet dataSet) {
        int latentIndex = 1;
        List<Node> nodes = dataSet.getVariables();
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

    /** Defensive copy + canonicalize (sort members, drop duplicates, drop empties). */
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

    /** Ensure indices are in-range for the given dataset. */
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

    public static BlockSpec toSpec(List<List<Integer>> blocks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(dataSet, canon, makeBlockVariables(canon, dataSet));
    }

    // Helper to set ranks parsed from text:
    public static BlockSpec withRanks(BlockSpec base, List<Integer> ranks) {
        if (ranks.size() != base.blocks().size()) throw new IllegalArgumentException("rank size mismatch");
        return new BlockSpec(base.dataSet(), base.blocks(), base.blockVariables(), List.copyOf(ranks));
    }

    // Expand ranks -> per-latent variables named Lk-1..Lk-r
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

    /** Keep blocks disjoint by preference order (bigger first), removing overlaps later in the list. */
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

    public static BlockSpec makeDisjointSpec(DataSet ds, List<List<Integer>> blocks) {
        List<List<Integer>> disjoint = makeDisjointBySize(blocks);
        List<Node> blockVars = makeBlockVariables(disjoint, ds); // your existing helper
        return new BlockSpec(ds, disjoint, blockVars); // ranks default to 1s
    }
}