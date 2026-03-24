///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Extracts true latent clusters from a graph produced by {@link edu.cmu.tetrad.graph.RandomMim}.
 *
 * <p>A <em>true cluster</em> is the set of measured (indicator) children of a single
 * latent-group leader.  In a pure NOLAC MIM every measured variable has exactly one
 * latent parent, so the clusters form a partition of the measured variables.  In an
 * impure MIM (cross-loadings present) a measured variable may appear in more than one
 * cluster; this class still returns one cluster per latent, leaving duplicate membership
 * for the caller to handle.
 *
 * <p>The {@code RandomMim} naming convention is exploited to group latents by their
 * base name (the prefix before any alphabetic suffix).  For example, latents
 * {@code L3}, {@code L3A}, {@code L3B} all belong to group 3 and their combined
 * measured children form a single true cluster.  This correctly handles both rank-1
 * (single latent per group) and rank-2+ (multiple latents per group) models.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 *   Graph mim = RandomMim.constructRandomMim(...);
 *   List<Set<Node>> trueClusters = TrueClusterExtractor.extractClusters(mim);
 * }</pre>
 *
 * @author josephramsey
 */
public final class TrueClusterExtractor {

    private TrueClusterExtractor() {
    }

    /**
     * Extracts true latent clusters from {@code graph}.
     *
     * <p>Each returned set contains the measured children that belong to one latent
     * group.  Clusters are returned in a deterministic order (sorted by the group's
     * base latent name) and each set is unmodifiable.
     *
     * @param graph a graph produced by {@code RandomMim}; must not be {@code null}.
     * @return an unmodifiable list of unmodifiable sets, one per latent group,
     *         each containing the measured indicator nodes for that group.
     *         Groups with no measured children are omitted.
     * @throws IllegalArgumentException if {@code graph} is {@code null}.
     */
    public static List<Set<Node>> extractClusters(Graph graph) {
        if (graph == null) throw new IllegalArgumentException("Graph must not be null.");

        // ---- 1. Collect all latent nodes, grouped by their base name ----
        //
        // RandomMim names latents as:
        //   rank-1 group g  ->  "Lg"          (single latent, no suffix)
        //   rank-r group g  ->  "Lg", "LgA", "LgB", ...
        //
        // We strip any trailing alphabetic suffix to recover the base name,
        // then group all latents sharing a base name together.
        //
        // e.g.  L3, L3A, L3B  all have base "L3"  -> one cluster entry.

        Map<String, Set<Node>> baseToMeasured = new LinkedHashMap<>();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) continue;

            String base = latentBaseName(node.getName());

            // Collect measured children of this latent
            for (Node child : graph.getChildren(node)) {
                if (child.getNodeType() == NodeType.MEASURED) {
                    baseToMeasured
                            .computeIfAbsent(base, k -> new LinkedHashSet<>())
                            .add(child);
                }
            }
        }

        // ---- 2. Sort groups by base name and return ----
        List<String> sortedBases = new ArrayList<>(baseToMeasured.keySet());
        sortedBases.sort(Comparator.comparingInt(TrueClusterExtractor::latentGroupIndex)
                .thenComparing(Comparator.naturalOrder()));

        List<Set<Node>> result = new ArrayList<>(sortedBases.size());
        for (String base : sortedBases) {
            Set<Node> cluster = baseToMeasured.get(base);
            if (!cluster.isEmpty()) {
                result.add(Collections.unmodifiableSet(cluster));
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Extracts true clusters and returns them as sets of variable <em>names</em>
     * rather than {@link Node} objects.  Useful when comparing against algorithm
     * output that works with names.
     *
     * @param graph a graph produced by {@code RandomMim}; must not be {@code null}.
     * @return an unmodifiable list of unmodifiable sets of node names.
     */
    public static List<Set<String>> extractClusterNames(Graph graph) {
        List<Set<Node>> nodeClusters = extractClusters(graph);
        List<Set<String>> result = new ArrayList<>(nodeClusters.size());
        for (Set<Node> cluster : nodeClusters) {
            Set<String> names = new LinkedHashSet<>(cluster.size() * 2);
            for (Node n : cluster) names.add(n.getName());
            result.add(Collections.unmodifiableSet(names));
        }
        return Collections.unmodifiableList(result);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Strips any trailing uppercase alphabetic suffix from a latent name to
     * recover the group base name.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "L3"}   -> {@code "L3"}</li>
     *   <li>{@code "L3A"}  -> {@code "L3"}</li>
     *   <li>{@code "L3AB"} -> {@code "L3"}</li>
     *   <li>{@code "L12B"} -> {@code "L12"}</li>
     * </ul>
     */
    static String latentBaseName(String name) {
        if (name == null || name.isEmpty()) return name;
        int end = name.length();
        while (end > 0 && Character.isUpperCase(name.charAt(end - 1))) {
            end--;
        }
        // Guard: if stripping leaves nothing (e.g. name was all uppercase letters),
        // return the original name unchanged.
        return (end == 0) ? name : name.substring(0, end);
    }

    /**
     * Extracts the integer group index embedded in a base latent name such as
     * {@code "L3"} -> {@code 3}.  Returns {@code Integer.MAX_VALUE} if no
     * integer is found, so that non-standard names sort last.
     */
    private static int latentGroupIndex(String baseName) {
        if (baseName == null) return Integer.MAX_VALUE;
        // Find the first digit run in the name
        int start = -1;
        for (int i = 0; i < baseName.length(); i++) {
            if (Character.isDigit(baseName.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) return Integer.MAX_VALUE;
        int end = start;
        while (end < baseName.length() && Character.isDigit(baseName.charAt(end))) end++;
        try {
            return Integer.parseInt(baseName.substring(start, end));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
