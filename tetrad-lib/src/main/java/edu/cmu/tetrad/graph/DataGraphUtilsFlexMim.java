package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.ContinuousVariable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flexible MIM generator.
 * <p>
 * A "group" is a structural node in the meta-DAG. Each group has 'rank' many latent factors, and each group has
 * 'childrenPerGroup' many measured indicators.
 * <p>
 * For a meta-edge G_i -> G_j, we add directed edges from each latent in G_i to each latent in G_j. (Complete bipartite
 * between factor sets, as requested.)
 * <p>
 * Impurities: - numLatentMeasuredImpureParents: extra latent -> measured cross-loadings -
 * numMeasuredMeasuredImpureParents: measured -> measured directed edges - numMeasuredMeasuredImpureAssociations:
 * measured <-> measured bidirected "error correlations"
 */
public final class DataGraphUtilsFlexMim {

    private DataGraphUtilsFlexMim() {
    }

    /**
     * Convenience overload: pick a random number of meta-edges.
     */
    public static Graph randomMimGeneral(
            List<LatentGroupSpec> specs,
            int numLatentMeasuredImpureParents,
            int numMeasuredMeasuredImpureParents,
            int numMeasuredMeasuredImpureAssociations
    ) {
        // default: target ~20% of all possible forward edges (acyclic)
        return randomMimGeneral(
                specs,
                null,
                numLatentMeasuredImpureParents,
                numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations,
                LatentLinkMode.CARTESIAN_PRODUCT,
                new Random()
        );
    }

    /**
     * Main entry point. If metaEdgeCount is null, an ER-like random forward-DAG density is used (~20%). If
     * metaEdgeCount is provided, we sample exactly that many forward edges across the group order.
     */
    public static Graph randomMimGeneral(
            List<LatentGroupSpec> specs,
            Integer metaEdgeCount, // number of edges in the meta-DAG over groups; if null, choose randomly
            int numLatentMeasuredImpureParents,
            int numMeasuredMeasuredImpureParents,
            int numMeasuredMeasuredImpureAssociations,
            LatentLinkMode latentLinkMode,
            Random rng
    ) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("Specs cannot be empty.");
        }
        if (rng == null) rng = new Random(0);

        // ----- 1) Expand specs into concrete groups (structural nodes in the meta-DAG)
        final List<Group> groups = new ArrayList<>();
        for (LatentGroupSpec s : specs) {
            for (int i = 0; i < s.countGroups; i++) {
                groups.add(new Group(s.rank, s.childrenPerGroup));
            }
        }
        final int G = groups.size();
        if (G < 1) throw new IllegalArgumentException("No groups formed.");

        // ----- 2) Build a random acyclic meta-DAG over groups (0..G-1 topological order)
        // forward-only edges i -> j for i < j
        final List<int[]> possibleForward = new ArrayList<>();
        for (int i = 0; i < G; i++) {
            for (int j = i + 1; j < G; j++) possibleForward.add(new int[]{i, j});
        }

        final List<int[]> metaEdges = new ArrayList<>();
        if (possibleForward.isEmpty()) {
            // no edges possible; fine
        } else if (metaEdgeCount == null) {
            // choose ~20% of forward pairs
            double p = 0.20;
            for (int[] e : possibleForward) {
                if (rng.nextDouble() < p) metaEdges.add(e);
            }
        } else {
            // choose exactly metaEdgeCount forward edges without replacement
            if (metaEdgeCount < 0 || metaEdgeCount > possibleForward.size()) {
                throw new IllegalArgumentException("metaEdgeCount out of range [0, " + possibleForward.size() + "]");
            }
            Collections.shuffle(possibleForward, rng);
            metaEdges.addAll(possibleForward.subList(0, metaEdgeCount));
        }

        // ----- 3) Materialize the actual Tetrad graph
        Graph graph = new EdgeListGraph();

        // We'll create all latents and measureds for each group, track them, then wire everything up.
        NameFactory nameFactory = new NameFactory();

        // Accumulators for impurities
        List<Node> allLatents = new ArrayList<>();
        List<Node> allMeasured = new ArrayList<>();

        // Create groups’ latents + measureds
        for (int g = 0; g < G; g++) {
            Group grp = groups.get(g);

            // Latent names per group: L1, L1B, L1C, ... (prefix by group index + 1)
            List<Node> latents = new ArrayList<>(grp.rank);
            for (int r = 0; r < grp.rank; r++) {
                String base = (r == 0) ? ("L" + (g + 1)) : ("L" + (g + 1) + letterSuffix(r));
                String unique = nameFactory.unique(base);
                GraphNode L = new GraphNode(unique);
                L.setNodeType(NodeType.LATENT);
                graph.addNode(L);
                latents.add(L);
                allLatents.add(L);
            }

            // Create the group's measured children ONCE per group
            List<Node> measureds = new ArrayList<>(grp.childrenPerGroup);
            for (int k = 0; k < grp.childrenPerGroup; k++) {
                String xName = nameFactory.unique("X" + (g + 1)); // keeps group-local flavor, still globally unique
                ContinuousVariable X = new ContinuousVariable(xName);
                X.setNodeType(NodeType.MEASURED);
                graph.addNode(X);
                measureds.add(X);
                allMeasured.add(X);
            }

            // Every latent in the group points to EVERY measured child in the group
            for (Node L : latents) {
                for (Node X : measureds) {
                    if (graph.isAdjacentTo(X, L)) continue;
                    graph.addDirectedEdge(L, X);
                }
            }

            grp.latents = latents;
            grp.measured = measureds;
        }

        // ----- 4) Wire latent -> latent edges based on meta-DAG using complete bipartite between factor sets
        for (int[] e : metaEdges) {
            Group from = groups.get(e[0]);
            Group to = groups.get(e[1]);

            if (latentLinkMode == DataGraphUtilsFlexMim.LatentLinkMode.CARTESIAN_PRODUCT) {
                for (Node Lfrom : from.latents) {
                    for (Node Lto : to.latents) {
                        if (graph.isAdjacentTo(Lfrom, Lto)) continue;
                        graph.addDirectedEdge(Lfrom, Lto);
                    }
                }
            } else if (latentLinkMode == DataGraphUtilsFlexMim.LatentLinkMode.CORRESPONDING) {
                if (from.latents.size() != to.latents.size()) {
                    throw new IllegalArgumentException("Latent groups must have the same number of latents to " +
                                                       "link corresponding latents.");
                }

                for (int i = 0; i < from.latents.size(); i++) {
                    if (graph.isAdjacentTo(from.latents.get(i), to.latents.get(i))) continue;
                    graph.addDirectedEdge(from.latents.get(i), to.latents.get(i));
                }
            } else {
                throw new IllegalArgumentException("Unrecognized latent link mode: " + latentLinkMode + ".");
            }
        }

        // ----- 5) Add impurities

        // 5a) extra latent -> measured cross-loadings (avoid duplicating existing parent edges)
        addLatentMeasuredImpurities(graph, allLatents, allMeasured, numLatentMeasuredImpureParents, rng);

        // 5b) measured -> measured directed impurities (avoid cycles as much as possible by preferring “forward” indices)
        addMeasuredMeasuredParents(graph, allMeasured, numMeasuredMeasuredImpureParents, rng);

        // 5c) measured <-> measured bidirected “error correlations”
        addMeasuredMeasuredAssociations(graph, allMeasured, numMeasuredMeasuredImpureAssociations, rng);

        // layout (nice to have)
        try {
            LayoutUtil.fruchtermanReingoldLayout(graph);
        } catch (Throwable ignore) {
        }

        return graph;
    }

    private static void addLatentMeasuredImpurities(Graph g, List<Node> latents, List<Node> measured,
                                                    int count, Random rng) {
        if (count <= 0 || latents.isEmpty() || measured.isEmpty()) return;
        int tries = 0, added = 0, maxTries = count * 20;
        while (added < count && tries++ < maxTries) {
            Node L = latents.get(rng.nextInt(latents.size()));
            Node X = measured.get(rng.nextInt(measured.size()));
            if (g.isParentOf(L, X)) continue;              // already a parent
            if (L == X) continue;
            if (g.isAdjacentTo(X, L)) continue;
            g.addDirectedEdge(L, X);
            added++;
        }
    }

    private static void addMeasuredMeasuredParents(Graph g, List<Node> measured, int count, Random rng) {
        if (count <= 0 || measured.size() < 2) return;
        // To reduce obvious cycles we’ll bias edges to go from a lower index to a higher index.
        int tries = 0, added = 0, maxTries = count * 30;
        while (added < count && tries++ < maxTries) {
            int i = rng.nextInt(measured.size());
            int j = rng.nextInt(measured.size());
            if (i == j) continue;
            Node A = measured.get(Math.min(i, j));
            Node B = measured.get(Math.max(i, j));
            if (g.isAdjacentTo(A, B)) continue;
            g.addDirectedEdge(A, B);
            added++;
        }
    }

    // ========= helpers =========

    private static void addMeasuredMeasuredAssociations(Graph g, List<Node> measured, int count, Random rng) {
        if (count <= 0 || measured.size() < 2) return;
        int tries = 0, added = 0, maxTries = count * 30;
        while (added < count && tries++ < maxTries) {
            Node A = measured.get(rng.nextInt(measured.size()));
            Node B = measured.get(rng.nextInt(measured.size()));
            if (A == B) continue;
            if (g.isAdjacentTo(A, B)) continue;
            g.addBidirectedEdge(A, B); // error correlation style impurity
            added++;
        }
    }

    private static String letterSuffix(int indexFrom1) {
        // indexFrom1: 1 -> B, 2 -> C, 3 -> D, ...
        // Supports up to 26 extras easily; extend if you like.
        int i = Math.max(1, indexFrom1);
        char c = (char) ('A' + i); // 1->B, 2->C, ...
        return String.valueOf(c);
    }

    /**
     * Parses a specification string defining latent group configurations into a list of {@code LatentGroupSpec}
     * objects. Each configuration in the input string must follow the pattern:
     * {@code countGroups:childrenPerGroup(rank)}. Multiple configurations should be separated by commas (e.g.,
     * {@code 5:3(2), 4:6(1)}).
     *
     * @param value the specification string containing one or more latent group configurations; must not be null or
     *              empty, and each configuration must adhere to the specified pattern.
     * @return a list of parsed {@code LatentGroupSpec} objects representing the configurations.
     * @throws IllegalArgumentException if the input string is null, empty, or contains invalid configurations, or if
     *                                  any parsed values are less than 1.
     */
    public static List<LatentGroupSpec> parseLatentGroupSpecs(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Spec string cannot be null.");
        }
        String s = value.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Spec string cannot be empty.");
        }

        // pattern: countGroups : childrenPerGroup ( rank )
        final var pat = Pattern.compile("\\s*(\\d+)\\s*:\\s*(\\d+)\\s*\\(\\s*(\\d+)\\s*\\)\\s*");
        String[] parts = s.split(",");
        List<LatentGroupSpec> out = new ArrayList<>(parts.length);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            Matcher m = pat.matcher(part);
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "Invalid spec at item " + (i + 1) + " : '" + part.trim() +
                        "'. Expected form: count:children(rank), e.g., 5:5(1)"
                );
            }
            int countGroups = Integer.parseInt(m.group(1));
            int childrenPerGroup = Integer.parseInt(m.group(2));
            int rank = Integer.parseInt(m.group(3));

            if (countGroups < 1 || childrenPerGroup < 1 || rank < 1) {
                throw new IllegalArgumentException(
                        "All values must be >= 1 at item " + (i + 1) +
                        " : '" + part.trim() + "'"
                );
            }

            out.add(new LatentGroupSpec(countGroups, rank, childrenPerGroup));
        }

        return out;
    }

    public enum LatentLinkMode {
        CARTESIAN_PRODUCT,
        CORRESPONDING
    }

    /**
     * Specification for a block of groups that share the same rank and #children per latent.
     *
     * @param countGroups      how many groups with this configuration
     * @param rank             # of latent factors in each such group
     * @param childrenPerGroup # measured children per latent group
     */
    public record LatentGroupSpec(int countGroups, int rank, int childrenPerGroup) {
        public LatentGroupSpec {
            if (countGroups < 1 || rank < 1 || childrenPerGroup < 1)
                throw new IllegalArgumentException("All values must be >= 1");
        }
    }

    private static final class Group {
        final int rank;
        final int childrenPerGroup;
        List<Node> latents;
        List<Node> measured;

        Group(int rank, int childrenPerGroup) {
            this.rank = rank;
            this.childrenPerGroup = childrenPerGroup;
        }
    }

    /**
     * Ensures global uniqueness while trying to keep the requested base pattern.
     */
    private static final class NameFactory {
        private final Map<String, Integer> used = new HashMap<>();

        public String unique(String base) {
            if (!used.containsKey(base)) {
                used.put(base, 1);
                return base;
            }
            int k = used.get(base);
            used.put(base, k + 1);
            // minimal, readable disambiguator: base, base2, base3, ...
            return base + (k); // base2 on first clash
        }
    }
}