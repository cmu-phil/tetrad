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

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Represents an edge node1 *-# node2 where * and # are endpoints of type
 * Endpoint--that is, Endpoint.TAIL, Endpoint.ARROW, or Endpoint.CIRCLE.
 * <p>
 * Note that because speed is of the essence, and Edge cannot be compared
 * to an object of any other type; this will throw an exception.
 *
 * @author Joseph Ramsey
 */
public class Edge implements TetradSerializable, Comparable {
    static final long serialVersionUID = 23L;

    /**
     * @serial
     */
    private Node node1;

    /**
     * @serial
     */
    private Node node2;

    /**
     * @serial
     */
    private Endpoint endpoint1;

    /**
     * @serial
     */
    private Endpoint endpoint2;

    //=========================CONSTRUCTORS============================//

    /**
     * Constructs a new edge by specifying the nodes it connects and the
     * endpoint types.
     *
     * @param node1     the first node
     * @param node2     the second node            _
     * @param endpoint1 the endpoint at the first node
     * @param endpoint2 the endpoint at the second node
     */
    public Edge(Node node1, Node node2, Endpoint endpoint1,
                Endpoint endpoint2) {
        if (node1 == null || node2 == null) {
            throw new NullPointerException("Nodes must not be null.");
        }

        if (endpoint1 == null || endpoint2 == null) {
            throw new NullPointerException("Endpoints must not be null.");
        }

        // Flip edges pointing left the other way.
        if (pointingLeft(endpoint1, endpoint2)) {
            this.node1 = node2;
            this.node2 = node1;
            this.endpoint1 = endpoint2;
            this.endpoint2 = endpoint1;
        } else {
            this.node1 = node1;
            this.node2 = node2;
            this.endpoint1 = endpoint1;
            this.endpoint2 = endpoint2;
        }
    }

    public Edge(Edge edge) {
        this(edge.node1, edge.node2, edge.endpoint1, edge.endpoint2);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Edge serializableInstance() {
        return new Edge(GraphNode.serializableInstance(),
                GraphNode.serializableInstance(), Endpoint.ARROW,
                Endpoint.ARROW);
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * @return the A node.
     */
    public final Node getNode1() {
        return this.node1;
    }

    /**
     * @return the B node.
     */
    public final Node getNode2() {
        return this.node2;
    }

    /**
     * @return the endpoint of the edge at the A node.
     */
    public final Endpoint getEndpoint1() {
        return this.endpoint1;
    }

    /**
     * @return the endpoint of the edge at the B node.
     */
    public final Endpoint getEndpoint2() {
        return this.endpoint2;
    }

    public final void setEndpoint1(Endpoint e) {
        this.endpoint1 = e;
    }

    public final void setEndpoint2(Endpoint e) {
        this.endpoint2 = e;
    }


    /**
     * @return the endpoint nearest to the given node.
     * @throws IllegalArgumentException if the given node is not along the
     *                                  edge.
     */
    public final Endpoint getProximalEndpoint(Node node) {
        if (this.node1 == node) {
            return getEndpoint1();
        } else if (this.node2 == node) {
            return getEndpoint2();
        }

        return null;
    }

    /**
     * @return the endpoint furthest from the given node.
     * @throws IllegalArgumentException if the given node is not along the
     *                                  edge.
     */
    public final Endpoint getDistalEndpoint(Node node) {
        if (this.node1 == node) {
            return getEndpoint2();
        } else if (this.node2 == node) {
            return getEndpoint1();
        }

        return null;
    }

    /**
     * Traverses the edge in an undirected fashion--given one node along the
     * edge, returns the node at the opposite end of the edge.
     */
    public final Node getDistalNode(Node node) {
        if (this.node1 == node) {
            return this.node2;
        }

        if (this.node2 == node) {
            return this.node1;
        }

        return null;
    }


    /**
     * @return true just in case this edge is directed.
     */
    public boolean isDirected() {
        return Edges.isDirectedEdge(this);
    }

    /**
     * @return true just in case the edge is pointing toward the given node--
     * that is, x --> node or x o--> node.
     */
    public boolean pointsTowards(Node node) {
        Endpoint proximal = getProximalEndpoint(node);
        Endpoint distal = getDistalEndpoint(node);
        return (proximal == Endpoint.ARROW &&
                (distal == Endpoint.TAIL || distal == Endpoint.CIRCLE));
    }

    /**
     * @return the edge with endpoints reversed.
     */
    public Edge reverse() {
        return new Edge(getNode2(), getNode1(), getEndpoint1(), getEndpoint2());
    }

    /**
     * Produces a string representation of the edge.
     */
    public final String toString() {
        StringBuilder buf = new StringBuilder();

        Endpoint endptTypeA = getEndpoint1();
        Endpoint endptTypeB = getEndpoint2();

        buf.append(getNode1());
        buf.append(" ");

        if (endptTypeA == Endpoint.TAIL) {
            buf.append("-");
        } else if (endptTypeA == Endpoint.ARROW) {
            buf.append("<");
        } else if (endptTypeA == Endpoint.CIRCLE) {
            buf.append("o");
        }

        buf.append("-");

        if (endptTypeB == Endpoint.TAIL) {
            buf.append("-");
        } else if (endptTypeB == Endpoint.ARROW) {
            buf.append(">");
        } else if (endptTypeB == Endpoint.CIRCLE) {
            buf.append("o");
        }

        buf.append(" ");
        buf.append(getNode2());

        return buf.toString();
    }

    public final int hashCode() {
        return node1.hashCode() + node2.hashCode();
    }

    /**
     * Two edges are equal just in case they connect the same nodes and have the
     * same endpoints proximal to each node.
     */
    public final boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Edge)) return false;

        Edge edge = (Edge) o;

        if (NodeEqualityMode.getEqualityType() == NodeEqualityMode.Type.OBJECT) {
            Node node1 = getNode1();
            Node node2 = getNode2();
            Node node1b = edge.getNode1();
            Node node2b = edge.getNode2();

            Endpoint end1 = getEndpoint1();
            Endpoint end2 = getEndpoint2();
            Endpoint end1b = edge.getEndpoint1();
            Endpoint end2b = edge.getEndpoint2();

            boolean equal;

//            if ((node1 == node1b && node2 == node2b) || (node1 == node2b && node2 == node1b)) {
//                System.out.println();
//            }

            if (node1 == node1b && node2 == node2b) {
                equal = end1 == end1b && end2 == end2b;
            } else
                equal = node1 == node2b && node2 == node1b && end1 == end2b && end2 == end1b;

            return equal;
        } else if (NodeEqualityMode.getEqualityType() == NodeEqualityMode.Type.NAME) {
            String name1 = getNode1().getName();
            String name2 = getNode2().getName();
            String name1b = edge.getNode1().getName();
            String name2b = edge.getNode2().getName();

            Endpoint end1 = getEndpoint1();
            Endpoint end2 = getEndpoint2();
            Endpoint end1b = edge.getEndpoint1();
            Endpoint end2b = edge.getEndpoint2();

            if (name1.equals(name1b) && name2.equals(name2b)) {
                return end1 == end1b && end2 == end2b;
            } else
                return name1.equals(name2b) && name2.equals(name1b) && end1 == end2b && end2 == end1b;
        } else {
            throw new IllegalStateException();
        }
    }

    public int compareTo(Object o) {
        Edge _edge = (Edge) o;

        int comp1 = getNode1().compareTo(_edge.getNode1());

        if (comp1 != 0) {
            return comp1;
        }

        return getNode2().compareTo(_edge.getNode2());
    }

    //===========================PRIVATE METHODS===========================//

    private boolean pointingLeft(Endpoint endpoint1, Endpoint endpoint2) {
        return (endpoint1 == Endpoint.ARROW &&
                (endpoint2 == Endpoint.TAIL || endpoint2 == Endpoint.CIRCLE));
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (node1 == null) {
            throw new NullPointerException();
        }

        if (node2 == null) {
            throw new NullPointerException();
        }

        if (endpoint1 == null) {
            throw new NullPointerException();
        }

        if (endpoint2 == null) {
            throw new NullPointerException();
        }
    }

    public boolean isNull() {
        return endpoint1 == Endpoint.NULL && endpoint2 == Endpoint.NULL;
    }
}





