package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Naming convention tests for giveGoodLatentNames(...).
 * Assumes the method is available in scope, e.g., BlockSpecUtil.giveGoodLatentNames(...)
 * Adjust the static imports / class names to match your codebase.
 */
public class GiveGoodLatentNamesTest {

    // --- Replace these with the actual class + static method where you put giveGoodLatentNames(...)
    // e.g., import static edu.cmu.tetrad.search.blocks.BlockSpecUtil.giveGoodLatentNames;
    private static BlockSpec giveGoodLatentNames(BlockSpec spec,
                                                 Map<String, List<String>> trueClusters,
                                                 BlocksUtil.NamingMode mode) {
        return BlocksUtil.giveGoodLatentNames(spec, trueClusters, mode);
    }

    @Test
    public void learnedSingle_prefersContentNames_andDropsB_whenRanked() {
        // Build dataset with variables used in the user's example
        List<String> vnames = Arrays.asList(
                // L4/L1(2) example
                "X4","X8","X7","X2","X5","X3","X6",
                // L2(2)
                "X11","X14","X12","X13","X10","X15",
                // L3(2)
                "X21","X20","X23","X19","X24","X18",
                // L1(2)
                "X25","X28","X32","X27","X29","X31"
        );
        DataSet ds = makeTinyDataSet(vnames);

        // Blocks by indices (one block per cluster)
        List<List<Integer>> blocks = Arrays.asList(
                idxs(ds, "X25","X28","X32","X27","X29","X31"), // true L1
                idxs(ds, "X11","X14","X12","X13","X10","X15"), // true L2
                idxs(ds, "X21","X20","X23","X19","X24","X18"), // true L3
                idxs(ds, "X4","X8","X7","X2","X5","X3","X6")   // true L1 (alternate ordering)
        );

        // All learned ranks=2 (as in your example)
        List<Integer> ranks = Arrays.asList(2,2,2,2);

        // Original latent names in a potentially misleading order
        List<Node> origLatents = namedLatents("L4","L2","L3","L1B"); // Intentionally tricky
        BlockSpec spec = new BlockSpec(ds, blocks, origLatents, ranks);

        // True clusters map (by content)
        Map<String, List<String>> trueClusters = new LinkedHashMap<>();
        trueClusters.put("L4", Arrays.asList("X25","X28","X32","X27","X29","X31"));
        trueClusters.put("L2", Arrays.asList("X11","X14","X12","X13","X10","X15"));
        trueClusters.put("L3", Arrays.asList("X21","X20","X23","X19","X24","X18"));
        trueClusters.put("L1", Arrays.asList("X4","X8","X7","X2","X5","X3","X6"));

        BlockSpec learned = giveGoodLatentNames(spec, trueClusters, BlocksUtil.NamingMode.LEARNED_SINGLE);

        // Assert: still 4 blocks, ranks preserved
        assertEquals(4, learned.blocks().size());
        assertEquals(ranks, learned.ranks());

        // Names should collapse any trailing single-letter suffix (L1B -> L1) and
        // align to content, not original indices.
        List<String> names = namesOf(learned.blockVariables());
        // All names should be the root (no trailing single capital letter)
        for (String n : names) {
            assertFalse("Should not end with single capital letter: " + n, n.matches(".*\\d[A-Z]$"));
        }

        // Each true block should resolve to its root:
        // The two L1-content blocks should both be named L1 (numeric disambiguation allowed for the second one)
        // We accept either "L1" and "L1-2" or "L1" and something similar due to uniqueness.
        assertTrue(containsRoot(names, "L1"));
        assertTrue(containsRoot(names, "L2"));
        assertTrue(containsRoot(names, "L3"));

        // Ensure no stray B-suffixed display names
        for (String n : names) {
            assertFalse("Unexpected 'B' suffix in learned names: " + n, n.endsWith("B"));
        }
    }

    @Test
    public void simulationExpanded_expandsRankIntoMultipleLatents() {
        // Simpler example: one block with rank=2 -> expands to two latents: L1 and L1B
        List<String> vnames = Arrays.asList("X1","X2","X3","X4","X5","X6");
        DataSet ds = makeTinyDataSet(vnames);

        List<List<Integer>> blocks = Collections.singletonList(idxs(ds, "X1","X2","X3","X4","X5","X6"));
        List<Integer> ranks  = Collections.singletonList(2);
        List<Node> origLatents = namedLatents("L1B"); // Original name has a B; expansion should still produce L1, L1B
        BlockSpec spec = new BlockSpec(ds, blocks, origLatents, ranks);

        Map<String, List<String>> trueClusters = new LinkedHashMap<>();
        trueClusters.put("L1", Arrays.asList("X1","X2","X3","X4","X5","X6"));

        BlockSpec expanded = giveGoodLatentNames(spec, trueClusters, BlocksUtil.NamingMode.SIMULATION_EXPANDED);

        // Expect 2 blocks, both with the same observed indices, ranks all 1
        assertEquals(2, expanded.blocks().size());
        for (List<Integer> b : expanded.blocks()) {
            assertEquals(blocks.get(0), b);
        }
        assertEquals(Arrays.asList(1,1), expanded.ranks());

        // Names should be L1 (root) and L1B (B suffix retained in expanded case)
        List<String> names = namesOf(expanded.blockVariables());
        assertEquals(2, names.size());
        assertTrue(names.contains("L1") || names.contains("L1-2")); // uniqueness fallback is ok
        // One of them should be exactly the lettered variant; allow uniqueness fallback if L1-2 was used
        boolean hasLettered = names.stream().anyMatch(n -> n.equals("L1B") || n.matches("L1-\\d+"));
        assertTrue("Expected a second expanded name with letter or numeric suffix", hasLettered);
    }

    // --- helpers ---

    private static DataSet makeTinyDataSet(List<String> varNames) {
        List<Node> vars = new ArrayList<>();
        for (String v : varNames) {
            ContinuousVariable cv = new ContinuousVariable(v);
            cv.setNodeType(NodeType.MEASURED);
            vars.add(cv);
        }
        // 1-row dummy data is enough to carry variable metadata
        DoubleDataBox box = new DoubleDataBox(1, vars.size());
        return new BoxDataSet(box, vars);
    }

    private static List<Node> namedLatents(String... names) {
        List<Node> L = new ArrayList<>();
        for (String n : names) {
            ContinuousVariable v = new ContinuousVariable(n);
            v.setNodeType(NodeType.LATENT);
            L.add(v);
        }
        return L;
    }

    private static List<Integer> idxs(DataSet ds, String... varNames) {
        List<Integer> out = new ArrayList<>();
        for (String v : varNames) {
            out.add(ds.getColumn(ds.getVariable(v)));
        }
        return out;
    }

    private static List<String> namesOf(List<Node> nodes) {
        List<String> out = new ArrayList<>();
        for (Node n : nodes) out.add(n.getName());
        return out;
    }

    private static boolean containsRoot(List<String> names, String root) {
        // Accept exact root or a uniqueness-suffixed variant like root-2
        for (String n : names) {
            if (n.equals(root) || n.matches(root + "-\\d+")) return true;
        }
        return false;
    }
}