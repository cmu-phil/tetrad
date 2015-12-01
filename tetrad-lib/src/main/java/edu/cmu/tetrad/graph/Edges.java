///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * This factory class produces edges of the types commonly used for Tetrad-style
 * graphs.  For each method in the class, one supplies a pair of nodes, and an
 * edge is returned, connecting those nodes, of the specified type.  Methods are
 * also included to help determine whether a specified edge falls into one of
 * the types produced by this factory.  It's entirely possible to produce edges
 * of these types other than by using this factory.  For randomUtil, an edge
 * counts as a directed edge just in case it has one null endpoint and one arrow
 * endpoint.  Any edge which has one null endpoint and one arrow endpoint will
 * do, whether or not this factory produced it.  These helper methods provide a
 * uniform way of testing whether an edge is in fact, e.g., a directed edge (or
 * any of the other types).
 *
 * @author Joseph Ramsey
 */
public final class Edges {

    /**
     * Constructs a new bidirected edge from nodeA to nodeB (<->).
     */
    public static Edge bidirectedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.ARROW, Endpoint.ARROW);
    }

    /**
     * Constructs a new directed edge from nodeA to nodeB (-->).
     */
    public static Edge directedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.TAIL, Endpoint.ARROW);
    }

    /**
     * Constructs a new partially oriented edge from nodeA to nodeB (o->).
     */
    public static Edge partiallyOrientedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.CIRCLE, Endpoint.ARROW);
    }

    /**
     * Constructs a new nondirected edge from nodeA to nodeB (o-o).
     */
    public static Edge nondirectedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.CIRCLE, Endpoint.CIRCLE);
    }

    /**
     * Constructs a new undirected edge from nodeA to nodeB (--).
     */
    public static Edge undirectedEdge(Node nodeA, Node nodeB) {
        return new Edge(nodeA, nodeB, Endpoint.TAIL, Endpoint.TAIL);
    }

    /**
     * @return true iff an edge is a bidirected edge (<->).
     */
    public static boolean isBidirectedEdge(Edge edge) {
        return (edge.getEndpoint1() == Endpoint.ARROW) &&
                (edge.getEndpoint2() == Endpoint.ARROW);
    }

    /**
     * @return true iff the given edge is a directed edge (-->).
     */
    public static boolean isDirectedEdge(Edge edge) {
        if (edge.getEndpoint1() == Endpoint.TAIL) {
            if (edge.getEndpoint2() == Endpoint.ARROW) {
                return true;
            }
        } else if (edge.getEndpoint2() == Endpoint.TAIL) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true iff the given edge is a partially oriented edge (o->)
     */
    public static boolean isPartiallyOrientedEdge(Edge edge) {
        if (edge.getEndpoint1() == Endpoint.CIRCLE) {
            if (edge.getEndpoint2() == Endpoint.ARROW) {
                return true;
            }
        } else if (edge.getEndpoint2() == Endpoint.CIRCLE) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true iff some edge is an nondirected edge (o-o).
     */
    public static boolean isNondirectedEdge(Edge edge) {
        return ((edge.getEndpoint1() == Endpoint.CIRCLE) &&
                (edge.getEndpoint2() == Endpoint.CIRCLE));
    }

    /**
     * @return true iff some edge is an undirected edge (-).
     */
    public static boolean isUndirectedEdge(Edge edge) {
        return ((edge.getEndpoint1() == Endpoint.TAIL) &&
                (edge.getEndpoint2() == Endpoint.TAIL));
    }

    public static boolean isAdjacentEdge(Edge edge) {
        if (edge.getEndpoint1() == Endpoint.CIRCLE || edge.getEndpoint1() == Endpoint.ARROW || edge.getEndpoint1() == Endpoint.TAIL) {
            if (edge.getEndpoint2() == Endpoint.ARROW || edge.getEndpoint1() == Endpoint.CIRCLE || edge.getEndpoint1() == Endpoint.TAIL) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the node opposite the given node along the given edge.
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
     * For A -> B, given A, returns B; otherwise returns null.
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
     * For A -> B, given B, returns A; otherwise returns null.
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
     * For A --* B or A o-* B, given A, returns B. For A <-* B, returns null.
     * Added by ekorber, 2004/06/12.
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

    public static Node traverseUndirected(Node node, Edge edge) {
        if (node == edge.getNode1()) {
            return edge.getNode2();
        } else if (node == edge.getNode2()) {
            return edge.getNode1();
        } else {
            return null;
        }
    }


    /**
     * For a directed edge, returns the node adjacent to the arrow endpoint.
     *
     * @throws IllegalArgumentException if the given edge is not a directed
     *                                  edge.
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
     * @throws IllegalArgumentException if the given edge is not a directed
     *                                  edge.
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

    public static void sortEdges(List<Edge> edges) {
        Collections.sort(edges, new Comparator<Edge>() {
            public int compare(Edge edge1, Edge edge2) {
                if (edge1 == null || edge2 == null) {
                    return 0;
                }

                Node left1 = edge1.getNode1();
                Node right1 = edge1.getNode2();

                Node left2 = edge2.getNode1();
                Node right2 = edge2.getNode2();

                int compareLeft = left1.toString().compareTo(left2.toString());
                int compareRight = right1.toString().compareTo(right2.toString());

                if (compareLeft != 0) {
                    return compareLeft;
                } else {
                    return compareRight;
                }
            }
        });
    }
}





