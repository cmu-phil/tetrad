package edu.cmu.tetrad.graph;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy interface: given a seed edge E on graph G, return the full set of
 * mirrored edges that should be kept in sync with E. The set should include E
 * itself if appropriate. Implementations MUST NOT create nodes.
 */
public interface EdgeReplicationPolicy {
    /**
     * Returns a set of mirrored edges in the given graph that should be kept in sync
     * with the specified edge. The returned set may include the specified edge if appropriate.
     *
     * @param g the graph containing the edge
     * @param e the seed edge for which mirrored edges are to be determined
     * @return a set of edges that should be synchronized with the specified edge
     */
    Set<Edge> mirrorsFor(EdgeListGraph g, Edge e);
}