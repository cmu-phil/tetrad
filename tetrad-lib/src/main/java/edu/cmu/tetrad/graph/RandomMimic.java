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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flexible MIMIC (Multiple Indicators Multiple Causes) graph generator.
 * <p>
 * Each group has a single latent factor (rank = 1), {@code parentsPerGroup} private measured cause
 * nodes pointing into the latent, and {@code childrenPerGroup} measured indicator nodes pointed to
 * by the latent. A random meta-DAG is built over the latents, optionally shared measured parents are
 * added across pairs of latents, and impurities of three kinds can be introduced.
 * <p>
 * Impurities:
 * <ul>
 *   <li>numLatentMeasuredImpureParents: extra latent -> measured cross-loadings</li>
 *   <li>numMeasuredMeasuredImpureParents: measured -> measured directed edges</li>
 *   <li>numMeasuredMeasuredImpureAssociations: measured &lt;-&gt; measured bidirected error correlations</li>
 * </ul>
 */
public final class RandomMimic {

    private RandomMimic() {
    }

    /**
     * Constructs a random MIMIC graph with specified structural constraints. Each group has a single
     * latent variable, private measured cause nodes (parents of the latent), and measured indicator
     * nodes (children of the latent). A random meta-DAG is built over the latents, shared parents
     * and impurities are added as specified.
     *
     * @param specs                                 list of group specs (countGroups, childrenPerGroup,
     *                                              parentsPerGroup); must not be null or empty.
     * @param metaEdgeCount                         number of edges in the meta-DAG over latents; if
     *                                              null, ~20% of forward edges are chosen randomly.
     * @param numSharedParents                      number of new measured nodes each wired as a parent
     *                                              to a randomly chosen pair of latents.
     * @param numLatentMeasuredImpureParents        extra latent->measured cross-loadings.
     * @param numMeasuredMeasuredImpureParents      directed edges among measured nodes as impurities.
     * @param numMeasuredMeasuredImpureAssociations bidirected associations among measured nodes.
     * @return the constructed MIMIC graph.
     * @throws IllegalArgumentException if specs are null/empty, metaEdgeCount is out of range, or
     *                                  numSharedParents > 0 with fewer than 2 latents.
     */
    public static Graph constructRandomMimic(
            List<MimicGroupSpec> specs,
            Integer metaEdgeCount,
            int numSharedParents,
            int numLatentMeasuredImpureParents,
            int numMeasuredMeasuredImpureParents,
            int numMeasuredMeasuredImpureAssociations
    ) {
        if (specs == null || specs.isEmpty()) {
            throw new IllegalArgumentException("Specs cannot be empty.");
        }

        // ----- 1) Expand specs into concrete groups
        class MimicGroup {
            final int childrenPerGroup;
            final int parentsPerGroup;
            Node latent;
            final List<Node> causes     = new ArrayList<>();
            final List<Node> indicators = new ArrayList<>();

            MimicGroup(int childrenPerGroup, int parentsPerGroup) {
                this.childrenPerGroup = childrenPerGroup;
                this.parentsPerGroup  = parentsPerGroup;
            }
        }

        final List<MimicGroup> groups = new ArrayList<>();
        for (MimicGroupSpec s : specs) {
            for (int i = 0; i < s.countGroups(); i++) {
                groups.add(new MimicGroup(s.childrenPerGroup(), s.parentsPerGroup()));
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
        if (!possibleForward.isEmpty()) {
            if (metaEdgeCount == null) {
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
        }

        // ----- 3) Materialize the graph
        Graph graph = new EdgeListGraph();
        List<Node> allLatents  = new ArrayList<>();
        List<Node> allMeasured = new ArrayList<>();

        for (int g = 0; g < G; g++) {
            MimicGroup grp = groups.get(g);

            // Single latent per group
            GraphNode L = new GraphNode("L" + (g + 1));
            L.setNodeType(NodeType.LATENT);
            graph.addNode(L);
            grp.latent = L;
            allLatents.add(L);

            // Private measured cause nodes -> latent
            for (int p = 0; p < grp.parentsPerGroup; p++) {
                ContinuousVariable P = new ContinuousVariable("P" + (g + 1) + "." + (p + 1));
                P.setNodeType(NodeType.MEASURED);
                graph.addNode(P);
                grp.causes.add(P);
                allMeasured.add(P);
                graph.addDirectedEdge(P, L);
            }

            // Latent -> measured indicator nodes
            for (int k = 0; k < grp.childrenPerGroup; k++) {
                ContinuousVariable X = new ContinuousVariable("X" + (g + 1) + "." + (k + 1));
                X.setNodeType(NodeType.MEASURED);
                graph.addNode(X);
                grp.indicators.add(X);
                allMeasured.add(X);
                graph.addDirectedEdge(L, X);
            }
        }

        // ----- 4) Wire latent -> latent edges from meta-DAG (rank=1, corresponding is trivial)
        for (int[] e : metaEdges) {
            Node Lfrom = groups.get(e[0]).latent;
            Node Lto   = groups.get(e[1]).latent;
            if (!graph.isAdjacentTo(Lfrom, Lto)) {
                graph.addDirectedEdge(Lfrom, Lto);
            }
        }

        // ----- 5) Add shared parents (each wired to a random pair of distinct latents)
        if (numSharedParents > 0) {
            if (allLatents.size() < 2) {
                throw new IllegalArgumentException(
                        "Need at least 2 latents to add shared parents.");
            }
            for (int sp = 0; sp < numSharedParents; sp++) {
                ContinuousVariable S = new ContinuousVariable("S" + (sp + 1));
                S.setNodeType(NodeType.MEASURED);
                graph.addNode(S);
                allMeasured.add(S);

                List<Node> shuffled = new ArrayList<>(allLatents);
                Collections.shuffle(shuffled);
                graph.addDirectedEdge(S, shuffled.get(0));
                graph.addDirectedEdge(S, shuffled.get(1));
            }
        }

        // ----- 6) Add impurities (delegates to RandomMim's package-private helpers)
        RandomMim.addLatentMeasuredImpurities(graph, allLatents, allMeasured, numLatentMeasuredImpureParents);
        RandomMim.addMeasuredMeasuredParents(graph, allLatents, allMeasured, numMeasuredMeasuredImpureParents);
        RandomMim.addMeasuredMeasuredAssociations(graph, allLatents, allMeasured, numMeasuredMeasuredImpureAssociations);

        try {
            LayoutUtil.fruchtermanReingoldLayout(graph);
        } catch (Throwable ignore) {
        }

        return graph;
    }

    /**
     * Parses a specification string defining MIMIC group configurations into a list of
     * {@code MimicGroupSpec} objects. Each configuration must follow the pattern:
     * {@code countGroups:childrenPerGroup:parentsPerGroup}. Multiple configurations should be
     * separated by commas (e.g., {@code 5:6:3, 2:8:4}).
     *
     * @param value the specification string; must not be null or empty.
     * @return a list of parsed {@code MimicGroupSpec} objects.
     * @throws IllegalArgumentException if the input is null, empty, or any token is malformed.
     */
    public static List<MimicGroupSpec> parseMimicGroupSpecs(String value) {
        if (value == null) throw new IllegalArgumentException("Spec string cannot be null.");
        String s = value.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Spec string cannot be empty.");

        final var pat = Pattern.compile("\\s*(\\d+)\\s*:\\s*(\\d+)\\s*:\\s*(\\d+)\\s*");
        String[] parts = s.split(",");
        List<MimicGroupSpec> out = new ArrayList<>(parts.length);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            Matcher m = pat.matcher(part);
            if (!m.matches()) {
                throw new IllegalArgumentException(
                        "Invalid spec at item " + (i + 1) + " : '" + part.trim() +
                                "'. Expected form: count:children:parents, e.g., 5:6:3"
                );
            }
            int countGroups      = Integer.parseInt(m.group(1));
            int childrenPerGroup = Integer.parseInt(m.group(2));
            int parentsPerGroup  = Integer.parseInt(m.group(3));
            out.add(new MimicGroupSpec(countGroups, childrenPerGroup, parentsPerGroup));
        }

        return out;
    }

    // ---- Types ----

    /**
     * Specification for a block of MIMIC groups sharing the same structure. Each group has a single
     * latent factor with private measured cause nodes and measured indicator nodes.
     *
     * @param countGroups      how many groups with this configuration; must be >= 1
     * @param childrenPerGroup number of measured indicators per group; must be >= 1
     * @param parentsPerGroup  number of measured cause nodes per group; must be >= 1
     */
    public record MimicGroupSpec(int countGroups, int childrenPerGroup, int parentsPerGroup) {
        public MimicGroupSpec {
            if (countGroups < 1 || childrenPerGroup < 1 || parentsPerGroup < 1)
                throw new IllegalArgumentException("All values must be >= 1");
        }
    }
}