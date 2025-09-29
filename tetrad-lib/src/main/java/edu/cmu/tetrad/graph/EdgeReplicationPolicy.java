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
     * @param g the current graph (read-only usage expected)
     * @param e the seed edge whose mirrors are requested
     * @return a set of edges to sync (may include e)
     */
    Set<Edge> mirrorsFor(EdgeListGraph g, Edge e);
}