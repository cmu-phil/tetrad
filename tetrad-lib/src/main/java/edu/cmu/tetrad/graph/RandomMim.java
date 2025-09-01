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
 * measured &lt;-&gt; measured bidirected "error correlations"
 */
public final class RandomMim {

    private RandomMim() {
    }

    /**
     * Constructs a random meta-graph as a Multiple Indicator Model (MIM) with specified structural constraints. This
     * method generates latent groups based on the provided specifications, builds a meta-DAG among the groups, and
     * materializes the graph with impurities such as cross-loadings, directed impurities, and bidirectional
     * associations over measured nodes.
     *
     * @param specs                                 the list of specifications describing latent groups, including group
     *                                              ranks and children per group; must not be null or empty.
     * @param metaEdgeCount                         the number of edges in the meta-DAG over latent groups; if null, a
     *                                              random proportion of possible forward edges is chosen instead.
     * @param numLatentMeasuredImpureParents        the number of additional latent-to-measured cross-loadings to
     *                                              introduce.
     * @param numMeasuredMeasuredImpureParents      the number of directed edges to introduce among measured nodes as
     *                                              impurities.
     * @param numMeasuredMeasuredImpureAssociations the number of bidirectional associations to introduce among measured
     *                                              nodes.
     * @param latentLinkMode                        the mode of linking latents between groups in the meta-DAG, either
     *                                              via Cartesian product or corresponding positions.
     * @param rng                                   the random generator used to make stochastic decisions during graph
     *                                              construction; can be null to use a default Random instance with a
     *                                              seed of 0.
     * @return the constructed graph representing the random MIM with all specified constraints and impurities.
     * @throws IllegalArgumentException if the specifications are null, empty, or malformed, if the number of meta-edges
     *                                  is out of valid range, or if the latentLinkMode is unrecognized.
     */
    public static Graph constructRandomMim(
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
            // no edges possible; falls through
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

        // Accumulators for impurities
        List<Node> allLatents = new ArrayList<>();
        List<Node> allMeasured = new ArrayList<>();

        // Create groups’ latents + measureds
        for (int g = 0; g < G; g++) {
            Group grp = groups.get(g);

            // ---- inside the group loop ----
            List<Node> latents = new ArrayList<>(grp.rank);
            for (int r = 0; r < grp.rank; r++) {
                String name = latentName(g, r, grp.rank);
                GraphNode L = new GraphNode(name);
                L.setNodeType(NodeType.LATENT);
                graph.addNode(L);
                latents.add(L);
                allLatents.add(L);
            }
            grp.latents = latents;

            // Create the group's measured children ONCE per group
            List<Node> measureds = new ArrayList<>(grp.childrenPerGroup);
            for (int k = 0; k < grp.childrenPerGroup; k++) {
                String xName = "X" + (g + 1) + "." + (k + 1); // keeps group-local flavor, still globally unique
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

            if (latentLinkMode == RandomMim.LatentLinkMode.CARTESIAN_PRODUCT) {
                for (Node Lfrom : from.latents) {
                    for (Node Lto : to.latents) {
                        if (graph.isAdjacentTo(Lfrom, Lto)) continue;
                        graph.addDirectedEdge(Lfrom, Lto);
                    }
                }
            } else if (latentLinkMode == RandomMim.LatentLinkMode.CORRESPONDING) {
                if (from.latents.size() != to.latents.size()) {
                    throw new IllegalArgumentException("Latent groups must have the same number of latents to " +
                                                       "link corresponding latents.");
                }

                for (int i = 0; i < from.latents.size(); i++) {
                    if (graph.isAdjacentTo(from.latents.get(i), to.latents.get(i))) continue;
                    graph.addDirectedEdge(from.latents.get(i), to.latents.get(i));
                }
            } else if (latentLinkMode == RandomMim.LatentLinkMode.PATCHY_CONNECTIONS) {
                // Build candidates from the full Cartesian product (respecting existing adjacencies)
                final List<Node[]> candidates = new ArrayList<>(from.latents.size() * to.latents.size());
                for (Node Lfrom : from.latents) {
                    for (Node Lto : to.latents) {
                        if (!graph.isAdjacentTo(Lfrom, Lto)) {
                            candidates.add(new Node[] { Lfrom, Lto });
                        }
                    }
                }

                if (!candidates.isEmpty()) {
                    Collections.shuffle(candidates, rng);
                    // “Patchy” = pick about half of the possible connections, but at least one.
                    final int k = Math.max(1, candidates.size() / 2);
                    for (int i = 0; i < k; i++) {
                        Node Lfrom = candidates.get(i)[0];
                        Node Lto   = candidates.get(i)[1];
                        // Acyclicity is guaranteed by the meta-DAG (i<j), so just add the edge.
                        graph.addDirectedEdge(Lfrom, Lto);
                    }
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
        int tries = 0, added = 0, maxTries = count * 50;
        while (added < count && tries++ < maxTries) {
            int i = rng.nextInt(measured.size());
            int j = rng.nextInt(measured.size());
            if (i == j) continue;

            Node A = measured.get(Math.min(i, j)); // bias lower -> higher
            Node B = measured.get(Math.max(i, j));

            if (g.isAdjacentTo(A, B)) continue;           // any edge already between them? skip
            if (hasDirectedPath(g, B, A)) continue;       // adding A->B would create a cycle
            g.addDirectedEdge(A, B);
            added++;
        }
    }

    // Simple DFS over directed edges only
    private static boolean hasDirectedPath(Graph g, Node from, Node to) {
        if (from == to) return true;
        Deque<Node> stack = new ArrayDeque<>();
        Set<Node> seen = new HashSet<>();
        stack.push(from);
        seen.add(from);

        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            if (cur == to) return true;
            for (Node child : g.getChildren(cur)) { // directed out-neighbors
                if (seen.add(child)) stack.push(child);
            }
        }
        return false;
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

    // ---- helper method ----
//    private static String latentName(int groupIndexZeroBased, int r, int rank) {
//        int g1 = groupIndexZeroBased + 1;
//        if (rank == 1 || r == 0) {
//            // single latent group: plain L1, L2, ...
//            return "L" + g1;
//        } else {
//            // multi-latent group: start with A, B, C...
//            char suffix = (char) ('A' + r);
//            return "L" + g1 + suffix;
//        }
//    }

    private static String latentName(int groupIndexZeroBased, int r, int rank) {
        int g1 = groupIndexZeroBased + 1;
        if (rank == 1 || r == 0) return "L" + g1;
        return "L" + g1 + alphaCode(r); // r = 1 -> B, but alphaCode(1) = "B", etc.
    }

    private static String alphaCode(int idx) { // 0->A, 25->Z, 26->AA
        idx = Math.max(0, idx);
        StringBuilder sb = new StringBuilder();
        do {
            int rem = idx % 26;
            sb.insert(0, (char) ('A' + rem));
            idx = idx / 26 - 1;
        } while (idx >= 0);
        return sb.toString();
    }

    /**
     * Enum representing the mode of linking between latent nodes in the construction of a random model-in-mapping (MIM)
     * graph. Determines how connections are formed between latent groups during the graph generation process.
     * <p>
     * The available modes are: - CARTESIAN_PRODUCT: All possible links between elements of two groups are formed. -
     * CORRESPONDING: Links are formed only between corresponding indices of two groups.
     */
    public enum LatentLinkMode {

        /**
         * Represents the Cartesian product mode for linking latent nodes in the construction of a random MIM
         * (model-in-mapping) graph. In this mode, all possible connections between elements of two latent groups are
         * established, forming a complete set of links between the groups.
         */
        CARTESIAN_PRODUCT,

        /**
         * Represents the corresponding mode for linking latent nodes in the construction of a random MIM
         * (model-in-mapping) graph. In this mode, connections are established only between elements at the same index
         * in two latent groups.
         */
        CORRESPONDING,

        /**
         * Represents the patchy connections mode for linking latent nodes in the construction of a random MIM
         * (model-in-mapping) graph. In this mode, connections are established between elements of two latent groups in
         * a patchy manner, where at least one connection is drawn from one group to another, but if more than one is
         * possible in the Cartesian product, # possible connections / 2 connections are drawn.
         */
        PATCHY_CONNECTIONS
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
}