///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.graph.Edge.Property;

import java.util.List;


/**
 * This factory class produces edges of the types commonly used for Tetrad-style graphs.  For each method in the class,
 * one supplies a pair of nodes, and an edge is returned, connecting those nodes, of the specified type.  Methods are
 * also included to help determine whether a specified edge falls into one of the types produced by this factory.  It's
 * entirely possible to produce edges of these types other than by using this factory.  For randomUtil, an edge counts
 * as a directed edge just in case it has one null endpoint and one arrow endpoint.  Any edge which has one null
 * endpoint and one arrow endpoint will do, whether or not this factory produced it.  These helper methods provide a
 * uniform way of testing whether an edge is in fact, e.g., a directed edge (or any of the other types).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Edges {

    /**
     * Private constructor to prevent instantiation.
     */
    private Edges() {

    }

    /**
     * Constructs a new bidirected edge from nodeA to nodeB (&lt;-&gt;).
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public static Edge bidirectedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.ARROW, Endpoint.ARROW);
    }

    /**
     * Constructs a new directed edge from nodeA to nodeB (--&gt;).
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public static Edge directedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.TAIL, Endpoint.ARROW);
    }

    /**
     * Constructs a new partially oriented edge from nodeA to nodeB (o-&gt;).
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public static Edge partiallyOrientedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.CIRCLE, Endpoint.ARROW);
    }

    /**
     * Constructs a new nondirected edge from nodeA to nodeB (o-o).
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public static Edge nondirectedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.CIRCLE, Endpoint.CIRCLE);
    }

    /**
     * Constructs a new undirected edge from nodeA to nodeB (--).
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.graph.Edge} object
     */
    public static Edge undirectedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.TAIL, Endpoint.TAIL);
    }

    /**
     * <p>isBidirectedEdge.</p>
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true iff an edge is a bidirected edge (&lt;-&gt;).
     */
    public static boolean isBidirectedEdge(Edge edge) {
        return (edge.getEndpoint1() == Endpoint.ARROW) &&
               (edge.getEndpoint2() == Endpoint.ARROW);
    }

    /**
     * <p>isDirectedEdge.</p>
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true iff the given edge is a directed edge (--&gt;).
     */
    public static boolean isDirectedEdge(Edge edge) {
        if (edge.getEndpoint1() == Endpoint.TAIL) {
            return edge.getEndpoint2() == Endpoint.ARROW;
        } else if (edge.getEndpoint2() == Endpoint.TAIL) {
            return edge.getEndpoint1() == Endpoint.ARROW;
        }

        return false;
    }

    /**
     * <p>isPartiallyOrientedEdge.</p>
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true iff the given edge is a partially oriented edge (o-&gt;)
     */
    public static boolean isPartiallyOrientedEdge(Edge edge) {
        if (edge.getEndpoint1() == Endpoint.CIRCLE) {
            return edge.getEndpoint2() == Endpoint.ARROW;
        } else if (edge.getEndpoint2() == Endpoint.CIRCLE) {
            return edge.getEndpoint1() == Endpoint.ARROW;
        }

        return false;
    }

    /**
     * <p>isNondirectedEdge.</p>
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true iff some edge is an nondirected edge (o-o).
     */
    public static boolean isNondirectedEdge(Edge edge) {
        return ((edge.getEndpoint1() == Endpoint.CIRCLE) &&
                (edge.getEndpoint2() == Endpoint.CIRCLE));
    }

    /**
     * <p>isUndirectedEdge.</p>
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return true iff some edge is an undirected edge (-).
     */
    public static boolean isUndirectedEdge(Edge edge) {
        return ((edge.getEndpoint1() == Endpoint.TAIL) &&
                (edge.getEndpoint2() == Endpoint.TAIL));
    }

    /**
     * If node is one endpoint of edge, returns the other endpoint.
     *
     * @param node The one endpoint.
     * @param edge The edge
     * @return The other endpoint.
     */
    public static Node traverse(Node node, Edge edge) {
        if (node == null) {
            return null;
        }
        // changed == to equals.
        if (node.equals(edge.getNode1())) {
            return edge.getNode2();
        } else if (node.equals(edge.getNode2())) {
            return edge.getNode1();
        }

        return null;
    }

    /**
     * For A -&gt; B, given A, returns B; otherwise returns null.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public static Node traverseDirected(Node node, Edge edge) {
        if (node == edge.getNode1()) {
            if ((edge.getEndpoint1() == Endpoint.TAIL) &&
                (edge.getEndpoint2() == Endpoint.ARROW)) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if ((edge.getEndpoint2() == Endpoint.TAIL) &&
                (edge.getEndpoint1() == Endpoint.ARROW)) {
                return edge.getNode1();
            }
        }

        return null;
    }

    /**
     * For A -&gt; B, given B, returns A; otherwise returns null.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public static Node traverseReverseDirected(Node node, Edge edge) {
        if (edge == null) {
            return null;
        }

        if (node == edge.getNode1()) {
            if ((edge.getEndpoint1() == Endpoint.ARROW) &&
                (edge.getEndpoint2() == Endpoint.TAIL)) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if ((edge.getEndpoint2() == Endpoint.ARROW) &&
                (edge.getEndpoint1() == Endpoint.TAIL)) {
                return edge.getNode1();
            }
        }

        return null;
    }

    /**
     * For A --* B or A o-* B, given A, returns B. For A &lt;-* B, returns null. Added by ekorber, 2004/06/12.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     */
    public static Node traverseSemiDirected(Node node, Edge edge) {
        if (node == edge.getNode1()) {
            if ((edge.getEndpoint1() == Endpoint.TAIL || edge.getEndpoint1() == Endpoint.CIRCLE)) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if ((edge.getEndpoint2() == Endpoint.TAIL || edge.getEndpoint2() == Endpoint.CIRCLE)) {
                return edge.getNode1();
            }
        }
        return null;
    }

    /**
     * For a directed edge, returns the node adjacent to the arrow endpoint.
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     * @throws java.lang.IllegalArgumentException if the given edge is not a directed edge.
     */
    public static Node getDirectedEdgeHead(Edge edge) {
        if ((edge.getEndpoint1() == Endpoint.ARROW) &&
            (edge.getEndpoint2() == Endpoint.TAIL)) {
            return edge.getNode1();
        } else if ((edge.getEndpoint2() == Endpoint.ARROW) &&
                   (edge.getEndpoint1() == Endpoint.TAIL)) {
            return edge.getNode2();
        } else {
            throw new IllegalArgumentException("Not a directed edge: " + edge);
        }
    }

    /**
     * For a directed edge, returns the node adjacent to the null endpoint.
     *
     * @param edge a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a {@link edu.cmu.tetrad.graph.Node} object
     * @throws java.lang.IllegalArgumentException if the given edge is not a directed edge.
     */
    public static Node getDirectedEdgeTail(Edge edge) {
        if ((edge.getEndpoint2() == Endpoint.ARROW) &&
            (edge.getEndpoint1() == Endpoint.TAIL)) {
            return edge.getNode1();
        } else if ((edge.getEndpoint1() == Endpoint.ARROW) &&
                   (edge.getEndpoint2() == Endpoint.TAIL)) {
            return edge.getNode2();
        } else {
            throw new IllegalArgumentException("Not a directed edge: " + edge);
        }
    }

    /**
     * <p>sortEdges.</p>
     *
     * @param edges a {@link java.util.List} object
     */
    public static void sortEdges(List<Edge> edges) {
        edges.sort((edge1, edge2) -> {
            if (edge1 == null || edge2 == null) {
                return 0;
            }

            Node left1 = edge1.getNode1();
            Node right1 = edge1.getNode2();

            Node left2 = edge2.getNode1();
            Node right2 = edge2.getNode2();

            List<Property> propertiesLeft = edge1.getProperties();
            List<EdgeTypeProbability> edgeTypePropertiesLeft = edge1.getEdgeTypeProbabilities();

            List<Property> propertiesRight = edge2.getProperties();
            List<EdgeTypeProbability> edgeTypePropertiesRight = edge2.getEdgeTypeProbabilities();

            // Compare edgeTypeProperty first, if exists
            int compareEdgeTypeProperty = 0;
            if (!edgeTypePropertiesLeft.isEmpty() && !edgeTypePropertiesRight.isEmpty()) {
                // Max probability on the left - excluding [no edge]
                double probLeft = 0;
                for (EdgeTypeProbability etp : edgeTypePropertiesLeft) {
                    if (etp.getEdgeType() != EdgeTypeProbability.EdgeType.nil && etp.getProbability() > probLeft) {
                        probLeft = etp.getProbability();
                    }
                }

                // Max probability on the right - excluding [no edge]
                double probRight = 0;
                for (EdgeTypeProbability etp : edgeTypePropertiesRight) {
                    if (etp.getEdgeType() != EdgeTypeProbability.EdgeType.nil && etp.getProbability() > probRight) {
                        probRight = etp.getProbability();
                    }
                }

                if (probLeft - probRight > 0) {
                    compareEdgeTypeProperty = -1;
                } else if (probLeft - probRight < 0) {
                    compareEdgeTypeProperty = 1;
                }
            }
            if (compareEdgeTypeProperty != 0) {
                return compareEdgeTypeProperty;
            }

            // Compare edge's properties
            int compareProperty = 0;
            int scorePropertyLeft = 0;
            for (Property property : propertiesLeft) {
                if (property == Property.dd || property == Property.nl) {
                    scorePropertyLeft += 2;
                }
                if (property == Property.pd || property == Property.pl) {
                    scorePropertyLeft += 1;
                }
            }
            int scorePropertyRight = 0;
            for (Property property : propertiesRight) {
                if (property == Property.dd || property == Property.nl) {
                    scorePropertyRight += 2;
                }
                if (property == Property.pd || property == Property.pl) {
                    scorePropertyRight += 1;
                }
            }

            if (scorePropertyLeft - scorePropertyRight > 0) {
                compareProperty = -1;
            } else if (scorePropertyLeft - scorePropertyRight < 0) {
                compareProperty = 1;
            }
            if (compareProperty != 0) {
                return compareProperty;
            }

            int compareLeft = left1.compareTo(left2);
            int compareRight = right1.compareTo(right2);

            if (compareLeft != 0) {
                return compareLeft;
            } else {
                return compareRight;
            }
        });
    }
}





