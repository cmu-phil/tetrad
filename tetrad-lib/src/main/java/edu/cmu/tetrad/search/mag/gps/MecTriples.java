// =========================
// File: src/main/java/edu/cmu/tetrad/mag/gps/MecTriples.java
// =========================
package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Triple;

import java.util.*;

/**
 * @param skeleton     undirected skeleton (edges added with Edges.undirectedEdge)
 * @param colliders    ordered triples <x,z,y>
 * @param noncolliders ordered triples <x,z,y>
 * @param order        exact order k (0 = unshielded / order-0)
 */
record MecTriples(Graph skeleton, List<Triple> colliders, List<Triple> noncolliders, Map<Triple, Integer> order) {
    MecTriples(Graph skeleton, List<Triple> colliders, List<Triple> noncolliders, Map<Triple, Integer> order) {
        this.skeleton = skeleton;
        this.colliders = Collections.unmodifiableList(new ArrayList<>(colliders));
        this.noncolliders = Collections.unmodifiableList(new ArrayList<>(noncolliders));
        this.order = Collections.unmodifiableMap(new LinkedHashMap<>(order));
    }
}