///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flexible MIM (Multiple Indicator Model) generator.
 * <p>
 * A "group" is a structural node in the meta-DAG. Each group has {@code rank} many latent factors,
 * and {@code childrenPerGroup} many measured indicators. For a meta-edge G_i -> G_j, directed edges
 * are added from each latent in G_i to each latent in G_j according to the chosen
 * {@link LatentLinkMode}.
 * <p>
 * Impurities:
 * <ul>
 *   <li>numLatentMeasuredImpureParents: extra latent -> measured cross-loadings</li>
 *   <li>numMeasuredMeasuredImpureParents: measured -> measured directed edges</li>
 *   <li>numMeasuredMeasuredImpureAssociations: measured &lt;-&gt; measured bidirected error correlations</li>
 * </ul>
 */
public final class RandomMim {

    private RandomMim() {
    }

    /**
     * Constructs a random meta-graph as a Multiple Indicator Model (MIM) with specified structural
     * constraints. This method generates latent groups based on the provided specifications, builds a
     * meta-DAG among the groups, and materializes the graph with impurities such as cross-loadings,
     * directed impurities, and bidirectional associations over measured nodes.
     *
     * @param specs                                 the list of specifications describing latent groups,
     *                                              including group ranks and children per group; must
     *                                              not be null or empty.
     * @param metaEdgeCount                         the number of edges in the meta-DAG over latent
     *                                              groups; if null, a random proportion (~20%) of
     *                                              possible forward edges is chosen instead.
     * @param numLatentMeasuredImpureParents        the number of additional latent-to-measured
     *                                              cross-loadings to introduce.
     * @param numMeasuredMeasuredImpureParents      the number of directed edges to introduce among
     *                                              measured nodes as impurities.
     * @param numMeasuredMeasuredImpureAssociations the number of bidirectional associations to
     *                                              introduce among measured nodes.
     * @param latentLinkMode                        the mode of linking latents between groups in the
     *                                              meta-DAG.
     * @return the constructed graph representing the random MIM with all specified constraints and
     * impurities.
     * @throws IllegalArgumentException if the specifications are null, empty, or malformed, if the
     *                                  number of meta-edges is out of valid range, or if the
     *                                  latentLinkMode is unrecognized.
     */
    public static Graph constructRandomMim(
            List<LatentGroupSpec> specs,
            Integer metaEdgeCount,
            int numLatentMeasuredImpureParents,
            int numMeasuredMeasuredImpureParents,
            int numMeasuredMeasuredImpureAssociations,
            LatentLinkMode latentLinkMode
    ) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("Specs cannot be empty.");
        }

        // ----- 1) Expand specs into concrete groups
        final List<Group> groups = new ArrayList<>();
        for (LatentGroupSpec s : specs) {
            for (int i = 0; i < s.countGroups; i++) {
                groups.add(new Group(s.rank, s.childrenPerGroup));
            }
        }
        final int G = groups.size();
        if (G < 1) throw new IllegalArgumentException("No groups formed.");

        // ----- 2) Build a random acyclic meta-DAG (forward-only edges i -> j for i < j)
        final List<int[]> possibleForward = new ArrayList<>();
        for (int i = 0; i < G; i++) {
            for (int j = i + 1; j < G; j++) possibleForward.add(new int[]{i, j});
        }

        final List<int[]> metaEdges = new ArrayList<>();
        if (possibleForward.isEmpty()) {
            // no edges possible; falls through
        } else if (metaEdgeCount == null) {
            for (int[] e : possibleForward) {
                if (RandomUtil.getInstance().nextDouble() < 0.20) metaEdges.add(e);
            }
        } else {
            if (metaEdgeCount < 0 || metaEdgeCount > possibleForward.size()) {
                throw new IllegalArgumentException(
                        "metaEdgeCount out of range [0, " + possibleForward.size() + "]");
            }
            Collections.shuffle(possibleForward);
            metaEdges.addAll(possibleForward.subList(0, metaEdgeCount));
        }

        // ----- 3) Materialize the graph
        Graph graph = new EdgeListGraph();
        List<Node> allLatents = new ArrayList<>();
        List<Node> allMeasured = new ArrayList<>();

        for (int g = 0; g < G; g++) {
            Group grp = groups.get(g);

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

            List<Node> measureds = new ArrayList<>(grp.childrenPerGroup);
            for (int k = 0; k < grp.childrenPerGroup; k++) {
                String xName = "X" + (g + 1) + "." + (k + 1);
                ContinuousVariable X = new ContinuousVariable(xName);
                X.setNodeType(NodeType.MEASURED);
                graph.addNode(X);
                measureds.add(X);
                allMeasured.add(X);
            }

            for (Node L : latents) {
                for (Node X : measureds) {
                    if (graph.isAdjacentTo(X, L)) continue;
                    graph.addDirectedEdge(L, X);
                }
            }

            grp.measured = measureds;
        }

        // ----- 4) Wire latent -> latent edges based on meta-DAG
        for (int[] e : metaEdges) {
            Group from = groups.get(e[0]);
            Group to = groups.get(e[1]);

            if (latentLinkMode == LatentLinkMode.CARTESIAN_PRODUCT) {
                for (Node Lfrom : from.latents) {
                    for (Node Lto : to.latents) {
                        if (graph.isAdjacentTo(Lfrom, Lto)) continue;
                        graph.addDirectedEdge(Lfrom, Lto);
                    }
                }
            } else if (latentLinkMode == LatentLinkMode.CORRESPONDING) {
                if (from.latents.size() != to.latents.size()) {
                    throw new IllegalArgumentException(
                            "Latent groups must have the same number of latents to link corresponding latents.");
                }
                for (int i = 0; i < from.latents.size(); i++) {
                    if (graph.isAdjacentTo(from.latents.get(i), to.latents.get(i))) continue;
                    graph.addDirectedEdge(from.latents.get(i), to.latents.get(i));
                }
            } else if (latentLinkMode == LatentLinkMode.PATCHY_CONNECTIONS) {
                final List<Node[]> candidates = new ArrayList<>(from.latents.size() * to.latents.size());
                for (Node Lfrom : from.latents) {
                    for (Node Lto : to.latents) {
                        if (!graph.isAdjacentTo(Lfrom, Lto)) {
                            candidates.add(new Node[]{Lfrom, Lto});
                        }
                    }
                }
                if (!candidates.isEmpty()) {
                    RandomUtil.shuffle(candidates);
                    final int k = TMath.max(1, candidates.size() / 2);
                    for (int i = 0; i < k; i++) {
                        graph.addDirectedEdge(candidates.get(i)[0], candidates.get(i)[1]);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unrecognized latent link mode: " + latentLinkMode + ".");
            }
        }

        // ----- 5) Add impurities
        addLatentMeasuredImpurities(graph, allLatents, allMeasured, numLatentMeasuredImpureParents);
        addMeasuredMeasuredParents(graph, allLatents, allMeasured, numMeasuredMeasuredImpureParents);
        addMeasuredMeasuredAssociations(graph, allLatents, allMeasured, numMeasuredMeasuredImpureAssociations);

        try {
            LayoutUtil.fruchtermanReingoldLayout(graph);
        } catch (Throwable ignore) {
        }

        return graph;
    }

    /**
     * Parses a specification string defining latent group configurations into a list of
     * {@code LatentGroupSpec} objects. Each configuration must follow the pattern:
     * {@code countGroups:childrenPerGroup(rank)}. Multiple configurations should be separated by
     * commas (e.g., {@code 5:3(2), 4:6(1)}).
     *
     * @param value the specification string; must not be null or empty.
     * @return a list of parsed {@code LatentGroupSpec} objects.
     * @throws IllegalArgumentException if the input is null, empty, or any token is malformed.
     */
    public static List<LatentGroupSpec> parseLatentGroupSpecs(String value) {
        if (value == null) throw new IllegalArgumentException("Spec string cannot be null.");
        String s = value.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Spec string cannot be empty.");

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
            int countGroups      = Integer.parseInt(m.group(1));
            int childrenPerGroup = Integer.parseInt(m.group(2));
            int rank             = Integer.parseInt(m.group(3));

            if (countGroups < 1 || childrenPerGroup < 1 || rank < 1) {
                throw new IllegalArgumentException(
                        "All values must be >= 1 at item " + (i + 1) + " : '" + part.trim() + "'");
            }

            out.add(new LatentGroupSpec(countGroups, rank, childrenPerGroup));
        }

        return out;
    }

    // ---- Shared impurity helpers (package-private so RandomMimic can call them) ----

    static void addLatentMeasuredImpurities(Graph g, List<Node> latents, List<Node> measured, int count) {
        if (count <= 0 || latents.isEmpty() || measured.isEmpty()) return;

        // Only add cross-loadings to nodes that are already children of some latent
        List<Node> indicators = measured.stream()
                .filter(x -> latents.stream().anyMatch(l -> g.isParentOf(l, x)))
                .toList();
        if (indicators.isEmpty()) return;

        int tries = 0, added = 0, maxTries = count * 20;
        while (added < count && tries++ < maxTries) {
            Node L = latents.get(RandomUtil.getInstance().nextInt(latents.size()));
            Node X = indicators.get(RandomUtil.getInstance().nextInt(indicators.size()));
            if (g.isParentOf(L, X)) continue;
            if (g.isAdjacentTo(X, L)) continue;
            g.addDirectedEdge(L, X);
            added++;
        }
    }

    static void addMeasuredMeasuredParents(Graph g, List<Node> latents, List<Node> measured, int count) {
        if (count <= 0 || measured.size() < 2) return;

        List<Node> indicators = measured.stream()
                .filter(x -> latents.stream().anyMatch(l -> g.isParentOf(l, x)))
                .toList();
        if (indicators.size() < 2) return;

        int tries = 0, added = 0, maxTries = count * 50;
        while (added < count && tries++ < maxTries) {
            int i = RandomUtil.getInstance().nextInt(indicators.size());
            int j = RandomUtil.getInstance().nextInt(indicators.size());
            if (i == j) continue;
            Node A = indicators.get(TMath.min(i, j));
            Node B = indicators.get(TMath.max(i, j));
            if (g.isAdjacentTo(A, B)) continue;
            if (hasDirectedPath(g, B, A)) continue;
            g.addDirectedEdge(A, B);
            added++;
        }
    }

    static void addMeasuredMeasuredAssociations(Graph g, List<Node> latents, List<Node> measured, int count) {
        if (count <= 0 || measured.size() < 2) return;

        List<Node> indicators = measured.stream()
                .filter(x -> latents.stream().anyMatch(l -> g.isParentOf(l, x)))
                .toList();
        if (indicators.size() < 2) return;

        int tries = 0, added = 0, maxTries = count * 30;
        while (added < count && tries++ < maxTries) {
            Node A = indicators.get(RandomUtil.getInstance().nextInt(indicators.size()));
            Node B = indicators.get(RandomUtil.getInstance().nextInt(indicators.size()));
            if (A == B) continue;
            if (g.isAdjacentTo(A, B)) continue;
            g.addBidirectedEdge(A, B);
            added++;
        }
    }
    static boolean hasDirectedPath(Graph g, Node from, Node to) {
        if (from == to) return true;
        Deque<Node> stack = new ArrayDeque<>();
        Set<Node> seen = new HashSet<>();
        stack.push(from);
        seen.add(from);
        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            if (cur == to) return true;
            for (Node child : g.getChildren(cur)) {
                if (seen.add(child)) stack.push(child);
            }
        }
        return false;
    }

    // ---- Private helpers ----

    private static String latentName(int groupIndexZeroBased, int r, int rank) {
        int g1 = groupIndexZeroBased + 1;
        if (rank == 1 || r == 0) return "L" + g1;
        return "L" + g1 + alphaCode(r);
    }

    private static String alphaCode(int idx) {
        idx = TMath.max(0, idx);
        StringBuilder sb = new StringBuilder();
        do {
            int rem = idx % 26;
            sb.insert(0, (char) ('A' + rem));
            idx = idx / 26 - 1;
        } while (idx >= 0);
        return sb.toString();
    }

    // ---- Types ----

    /**
     * Enum representing the mode of linking between latent nodes across groups in the meta-DAG.
     */
    public enum LatentLinkMode {
        /** All possible links between the two groups' latents are formed. */
        CARTESIAN_PRODUCT,
        /** Links are formed only between latents at the same index in each group. */
        CORRESPONDING,
        /** Roughly half of the possible Cartesian-product links are chosen, but at least one. */
        PATCHY_CONNECTIONS
    }

    /**
     * Specification for a block of groups sharing the same rank and number of children per latent.
     *
     * @param countGroups      how many groups with this configuration; must be >= 1
     * @param rank             number of latent factors per group; must be >= 1
     * @param childrenPerGroup number of measured children per latent group; must be >= 1
     */
    public record LatentGroupSpec(int countGroups, int rank, int childrenPerGroup) {

        /**
         * Constructs a specification for a block of groups sharing the same rank and number of children per latent.
         * Validates that all provided parameters meet the required constraints.
         *
         * @param countGroups      the number of groups with this configuration; must be >= 1
         * @param rank             the number of latent factors per group; must be >= 1
         * @param childrenPerGroup the number of measured children per latent group; must be >= 1
         * @throws IllegalArgumentException if any parameter value is less than 1
         */
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