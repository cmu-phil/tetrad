/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Utility class for computing the ordered local Markov property of a maximal ancestral graph (MAG). The ordered local
 * Markov property defines conditional independencies for each node in the graph conditional on its Markov blanket.
 * <p>
 * The main functionality of this class is represented by the static method {@code getModel(Graph mag)} which computes a
 * set of {@code IndependenceFact}s representing the ordered local Markov property for a given MAG.
 * <p>
 * This class is not meant to be instantiated.
 */
public class OrderedLocalMarkovProperty {

    private OrderedLocalMarkovProperty() {
    }

    /**
     * Computes the ordered local Markov property for a maximal ancestral graph (MAG). The method generates a set of
     * independence facts representing the conditional independencies implied by the MAG.
     * <p>
     * The graph passed in should be a legal MAG; this is not checked (for speed).
     *
     * @param mag The input maximal ancestral graph (MAG) represented as a graph object. Must be a valid legal MAG;
     *            otherwise, an {@code IllegalArgumentException} is thrown.
     * @return A set of {@code IndependenceFact} objects that represent the conditional independencies for the given
     * input MAG based on the ordered local Markov property.
     * @throws IllegalArgumentException if the graph is not a legal MAG.
     */
    public static Set<IndependenceFact> getModel(Graph mag) {
        Paths paths = new Paths(mag);

//        if (!paths.isLegalMag()) {
//            List<Node> selection = mag.getNodes().stream()
//                    .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();
//            PagLegalityCheck.LegalMagRet ret = PagLegalityCheck.isLegalMag(mag, new HashSet<>(selection));
//            throw new IllegalArgumentException("MAG not valid, reason = " + ret.getReason());
//        }

        Set<IndependenceFact> model = new HashSet<>();
        Map<Node, Set<Node>> de = paths.getDescendantsMap();
        EdgeListGraph mag_ = new EdgeListGraph((mag));

        List<Node> unprocessed = new ArrayList<>(mag.getNodes());
        while (!unprocessed.isEmpty()) {
            Node sink = unprocessed.getFirst();
            while (!mag_.getChildren(sink).isEmpty()) {
                sink = mag_.getChildren(sink).getFirst();
            }

            Set<Node> dis = GraphUtils.district(sink, mag_);
            processSink(model, de, sink, dis, mag_);
            mag_.removeNode(sink);

            unprocessed.remove(sink);
        }

        return model;
    }

    /**
     * Returns the subset of ordered-local-Markov independence facts whose "sink"
     * is the given node {@code x}. In other words, it returns all facts of the form
     * {@code (x ⟂ y | MB_x)} (and the recursive/district variants produced by
     * {@link #processSink}) that are generated when {@code x} is eliminated as a sink
     * in the sink-elimination ordering induced by the input MAG.
     *
     * <p>Notes:
     * <ul>
     *   <li>This follows the same sink-selection policy as {@link #getModel(Graph)}:
     *       repeatedly pick an unprocessed node, walk down children to a sink, process it,
     *       then remove it.</li>
     *   <li>Only the iteration where {@code sink == x} contributes facts; once {@code x}
     *       is processed and removed, the method returns immediately.</li>
     *   <li>If {@code x} is not in {@code mag}, returns an empty set.</li>
     * </ul>
     *
     * @param mag a legal MAG (not checked for speed).
     * @param x   the node for which to return implied ordered-local-Markov independencies.
     * @return the set of implied independence facts with sink {@code x}.
     */
//    public static Set<IndependenceFact> getModelForNode(Graph mag, Node x) {
//        if (mag == null) throw new NullPointerException("mag");
//        if (x == null) throw new NullPointerException("x");
//        if (!mag.getNodes().contains(x)) return Collections.emptySet();
//
//        Paths paths = new Paths(mag);
//        Map<Node, Set<Node>> de = paths.getDescendantsMap();
//
//        Set<IndependenceFact> out = new HashSet<>();
//        EdgeListGraph mag_ = new EdgeListGraph(mag);
//
//        List<Node> unprocessed = new ArrayList<>(mag.getNodes());
//        while (!unprocessed.isEmpty()) {
//            Node sink = findSinkByChildWalk(mag_, unprocessed.getFirst());
//
//            Set<Node> dis = GraphUtils.district(sink, mag_);
//
//            if (sink == x) {
//                processSink(out, de, sink, dis, mag_);
//                // After x is processed, we can stop: we only want x's implied facts.
//                return out;
//            }
//
//            // Otherwise eliminate and continue.
//            mag_.removeNode(sink);
//            unprocessed.remove(sink);
//        }
//
//        // Should not happen if x was in mag, but keep it safe.
//        return out;
//    }

    public static Set<IndependenceFact> getModelForNode(Graph mag, Node x) {
        // Compute full OLMP model on this MAG
        Set<IndependenceFact> all = getModel(mag);

        // Map names -> the actual Node objects from *mag* (the graph VertexCheck uses)
        Map<String, Node> byName = new HashMap<>();
        for (Node n : mag.getNodes()) byName.put(n.getName(), n);

        String xName = x.getName();
        Set<IndependenceFact> out = new HashSet<>();

        for (IndependenceFact f : all) {
            if (!f.getX().getName().equals(xName) && !f.getY().getName().equals(xName)) continue;  // name-based match

//            Node X = byName.get(f.getX().getName());
//            Node Y = byName.get(f.getY().getName());

            boolean xIsLeft = f.getX().getName().equals(xName);

            Node X = byName.get(f.getX().getName());
            Node Y = byName.get(f.getY().getName());

            // Remap Z nodes by name too
            Set<Node> Z = new HashSet<>();
            for (Node z : f.getZ()) {
                Node zz = byName.get(z.getName());
                if (zz != null) Z.add(zz);
            }

//            if (X != null && Y != null) {
//                out.add(new IndependenceFact(X, Y, Z));
//            }

            if (X != null && Y != null) {
                if (xIsLeft) out.add(new IndependenceFact(X, Y, Z));
                else         out.add(new IndependenceFact(Y, X, Z)); // now x is always the X endpoint
            }
        }

        return out;
    }

    /** Mirrors the sink-selection policy in getModel(): walk down children until none remain. */
    private static Node findSinkByChildWalk(EdgeListGraph g, Node start) {
        Node sink = start;
        while (!g.getChildren(sink).isEmpty()) {
            sink = g.getChildren(sink).getFirst();
        }
        return sink;
    }

    private static void processSink(Set<IndependenceFact> model, Map<Node, Set<Node>> de, Node sink, Set<Node> dis, EdgeListGraph mag) {
        Set<Node> mb = GraphUtils.markovBlanket(sink, mag);
        for (Node node : mag.getNodes()) {
            if (node == sink) continue;
            if (mb.contains(node)) continue;
            IndependenceFact ind_fact = new IndependenceFact(sink, node, mb);
            model.add(ind_fact);
        }

        Set<Node> dis_ = new HashSet<>(dis);
        for (Node node : dis) {
            if (de.get(node).contains(sink)) continue;

            dis_ = new HashSet<>(dis_);
            dis_.remove((node));

            EdgeListGraph mag_ = new EdgeListGraph(mag);
            mag_.removeNodes(new ArrayList<>(de.get(node)));

            processSink(model, de, sink, dis_, mag_);
        }
    }
}
