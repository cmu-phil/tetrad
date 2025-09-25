// src/main/java/edu/cmu/tetrad/search/mag/gps/GpsUtils.java
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.*;
import java.util.*;

/**
 * Utilities for GPS: UG detection and parameter counting.
 */
public final class GpsUtils {

    private GpsUtils() { }

    /**
     * Return the indices of undirected-component (UG) nodes: those with
     * no incoming arrowheads, consistent with MAG definition.
     */
    public static int[] ugIndices(Graph g, List<String> varNames) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < varNames.size(); i++) {
            String name = varNames.get(i);
            Node v = g.getNode(name);
            if (v == null) continue;
            // UG nodes: no incoming arrowheads
            if (g.getNodesInTo(v, Endpoint.ARROW).isEmpty()) {
                idx.add(i);
            }
        }
        return idx.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Count the number of free parameters in the MAG, following
     * the standard parametrization:
     * - One parameter per directed edge (regression coefficient).
     * - One parameter per bidirected edge (error covariance).
     * - One parameter per undirected edge (precision entry).
     * - One variance parameter per node (diagonal of Omega or Lambda).
     */
    public static int numFreeParams(Graph g) {
        int k = 0;
        for (Edge e : g.getEdges()) {
            if (e.isDirected()) {
                k += 1;
            } else if (Edges.isBidirectedEdge(e)) {
                k += 1;
            } else if (Edges.isUndirectedEdge(e)) {
                k += 1;
            }
        }
        // add diagonal variance/precision terms: one per node
        k += g.getNumNodes();
        return k;
    }
}