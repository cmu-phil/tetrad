/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility class containing graph function from graph theory. These
 * implementations derived from Weka's implementation.
 *
 * Oct 7, 2019 2:56:07 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @see
 * <a href="https://raw.githubusercontent.com/Waikato/weka-3.8/master/weka/src/main/java/weka/classifiers/bayes/net/MarginCalculator.java">MarginCalculator.java</a>
 */
public final class GraphTools {

    private GraphTools() {
    }

    /**
     *
     * @param ordering maximum cardinality ordering
     * @param cliques set of cliques
     * @param separators set of separator sets
     * @return parent cliques
     */
    public static Map<Node, Node> getCliqueTree(Node[] ordering, Map<Node, Set<Node>> cliques, Map<Node, Set<Node>> separators) {
        Map<Node, Node> parentCliques = new HashMap<>();

        Arrays.stream(ordering).forEach(v -> {
            if (cliques.containsKey(v) && separators.containsKey(v) && !separators.get(v).isEmpty()) {
                for (Node w : ordering) {
                    if (v != w && cliques.containsKey(w) && cliques.get(w).containsAll(separators.get(v))) {
                        parentCliques.put(v, w);
                        break;
                    }
                }
            }
        });

        return parentCliques;
    }

    /**
     * Calculate separator sets in clique tree. A separator (set) is the
     * intersection of two adjacent nodes.
     *
     * @param ordering maximum cardinality ordering of the graph
     * @param cliques set of cliques
     * @return set of separator sets
     */
    public static Map<Node, Set<Node>> getSeparators(Node[] ordering, Map<Node, Set<Node>> cliques) {
        Map<Node, Set<Node>> separators = new HashMap<>();

        Set<Node> processedNodes = new HashSet<>();
        Arrays.stream(ordering).forEach(node -> {
            if (cliques.containsKey(node)) {
                Set<Node> clique = cliques.get(node);
                if (!clique.isEmpty()) {
                    Set<Node> separator = new HashSet<>();
                    separator.addAll(clique);
                    separator.retainAll(processedNodes);
                    separators.put(node, separator);
                    processedNodes.addAll(clique);
                }
            }
        });

        return separators;
    }

    /**
     * Get cliques in a decomposable graph. A clique is a fully-connected
     * subgraph.
     *
     * @param graph decomposable graph
     * @param ordering maximum cardinality ordering
     * @return set of cliques
     */
    public static Map<Node, Set<Node>> getCliques(Node[] ordering, Graph graph) {
        Map<Node, Set<Node>> cliques = new HashMap<>();
        for (int i = ordering.length - 1; i >= 0; i--) {
            Node v = ordering[i];

            Set<Node> clique = new HashSet<>();
            clique.add(v);

            for (int j = 0; j < i; j++) {
                Node w = ordering[j];
                if (graph.isAdjacentTo(v, w)) {
                    clique.add(w);
                }
            }

            cliques.put(v, clique);
        }

        // remove subcliques
        cliques.forEach((k1, v1) -> {
            cliques.forEach((k2, v2) -> {
                if ((k1 != k2) && !(v1.isEmpty() || v2.isEmpty()) && v1.containsAll(v2)) {
                    v2.clear();
                }
            });
        });

        // remove empty sets from map
        while (cliques.values().remove(Collections.EMPTY_SET)) {
        };

        return cliques;
    }

    /**
     * Apply Tarjan and Yannakakis (1984) fill in algorithm for graph
     * triangulation. An undirected graph is triangulated if every cycle of
     * length greater than 4 has a chord.
     *
     * @param graph moral graph
     * @param ordering maximum cardinality ordering
     */
    public static void fillIn(Graph graph, Node[] ordering) {
        int numOfNodes = ordering.length;

        // in reverse order, insert edges between any non-adjacent neighbors that are lower numbered in the ordering.
        for (int i = numOfNodes - 1; i >= 0; i--) {
            Node v = ordering[i];

            // find pairs of neighbors with lower order
            for (int j = 0; j < i; j++) {
                Node w = ordering[j];
                if (graph.isAdjacentTo(v, w)) {
                    for (int k = j + 1; k < i; k++) {
                        Node x = ordering[k];
                        if (graph.isAdjacentTo(x, v)) {
                            graph.addUndirectedEdge(x, w); // fill in edge
                        }
                    }
                }
            }
        }
    }

    /**
     * Perform Tarjan and Yannakakis (1984) maximum cardinality search (MCS) to
     * get the maximum cardinality ordering.
     *
     * @param graph moral graph
     * @return maximum cardinality ordering of the graph
     */
    public static Node[] getMaximumCardinalityOrdering(Graph graph) {
        int numOfNodes = graph.getNumNodes();
        if (numOfNodes == 0) {
            return new Node[0];
        }

        Node[] ordering = new Node[numOfNodes];
        Set<Node> numbered = new HashSet<>(numOfNodes);
        for (int i = 0; i < numOfNodes; i++) {
            // find an unnumbered node that is adjacent to the most number of numbered nodes
            Node maxCardinalityNode = null;
            int maxCardinality = -1;
            for (Node v : graph.getNodes()) {
                if (!numbered.contains(v)) {
                    // count the number of times node v is adjacent to numbered node w
                    int cardinality = (int) graph.getAdjacentNodes(v).stream()
                            .filter(u -> numbered.contains(u))
                            .count();

                    // find the maximum cardinality
                    if (cardinality > maxCardinality) {
                        maxCardinality = cardinality;
                        maxCardinalityNode = v;
                    }
                }
            }

            // add the node with maximum cardinality to the ordering and number it
            ordering[i] = maxCardinalityNode;
            numbered.add(maxCardinalityNode);
        }

        return ordering;
    }

    /**
     * Create a moral graph. A graph is moralized if an edge is added between
     * two parents with common a child and the edge orientation is removed,
     * making an undirected graph.
     *
     * @param graph to moralized
     * @return a moral graph
     */
    public static Graph moralize(Graph graph) {
        Graph moralGraph = new EdgeListGraph(graph.getNodes());

        // make skeleton
        graph.getEdges()
                .forEach(e -> moralGraph.addUndirectedEdge(e.getNode1(), e.getNode2()));

        // add edges to connect parents with common child
        graph.getNodes()
                .forEach(node -> {
                    List<Node> parents = graph.getParents(node);
                    if (!(parents == null || parents.isEmpty()) && parents.size() > 1) {
                        Node[] p = parents.toArray(new Node[parents.size()]);
                        for (int i = 0; i < p.length; i++) {
                            for (int j = i + 1; j < p.length; j++) {
                                Node node1 = p[i];
                                Node node2 = p[j];
                                if (!moralGraph.isAdjacentTo(node1, node2)) {
                                    moralGraph.addUndirectedEdge(node1, node2);
                                }
                            }
                        }
                    }
                });

        return moralGraph;
    }

}
