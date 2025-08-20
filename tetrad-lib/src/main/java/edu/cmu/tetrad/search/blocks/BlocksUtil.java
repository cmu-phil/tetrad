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
                nodes.add(dataSet.getVariable(block.getFirst()));
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

    /** Convenience to build BlockSpec with default names L1.. for each block. */
    public static BlockSpec toSpec(List<List<Integer>> blocks, DataSet dataSet) {
        List<List<Integer>> canon = canonicalizeBlocks(blocks);
        return new BlockSpec(canon, makeBlockVariables(blocks, dataSet));
    }

    /** Keep blocks disjoint by preference order (bigger first), removing overlaps later in the list. */
    public static List<List<Integer>> makeDisjointBySize(List<List<Integer>> blocks) {
        List<List<Integer>> sorted = blocks.stream()
                .sorted((a,b) -> Integer.compare(b.size(), a.size()))
                .map(ArrayList::new)
                .collect(Collectors.toList());
        BitSet used = new BitSet();
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> b : sorted) {
            List<Integer> pruned = b.stream().filter(i -> !used.get(i)).collect(Collectors.toList());
            if (!pruned.isEmpty()) {
                for (int i : pruned) used.set(i);
                out.add(pruned);
            }
        }
        return out;
    }
}