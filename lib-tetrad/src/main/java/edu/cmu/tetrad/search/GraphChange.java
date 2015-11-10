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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;


/**
 * **************************************************************************************
 *
 * @author: Trevor Burns
 * <p/>
 * GraphChange is a data structure created mainly for use in the ION search algorithm. However, it models a general
 * concept: storage for a variety of seperate changes one could possibly apply to a PAG.
 * <p/>
 * By convention, the NodePairs in the orients ArrayList are ordered such that Node1 is the "from" node and Node2 is the
 * "to" node (ie 1 o-> 2)
 */
public class GraphChange {
    private List<Edge> removes;
    private List<Triple> colliders;
    private List<Triple> nonColliders;
    private List<NodePair> orients;

    /**
     * Default constructor, holds no changes.
     */
    public GraphChange() {
        removes = new ArrayList<Edge>();
        colliders = new ArrayList<Triple>();
        nonColliders = new ArrayList<Triple>();
        orients = new ArrayList<NodePair>();
    }

    /**
     * Copy constructor.
     */
    public GraphChange(GraphChange source) {
        removes = new ArrayList<Edge>(source.removes);
        colliders = new ArrayList<Triple>(source.colliders);
        nonColliders = new ArrayList<Triple>(source.nonColliders);
        orients = new ArrayList<NodePair>(source.orients);
    }


    /**
     * Absorbs all changes from the GraphChange other into the calling GraphChange.
     */
    public void union(GraphChange other) {
        removes.addAll(other.removes);
        colliders.addAll(other.colliders);
        nonColliders.addAll(other.nonColliders);
        orients.addAll(other.orients);
    }


    /**
     * Consistency check, nonexhaustive, but catches the most blatant inconsistencies.
     */
    public boolean isConsistent(GraphChange other) {

        /* checks same triples being marked as colliders and non colliders */
        for (Triple colide : this.colliders)
            if (other.nonColliders.contains(colide))
                return false;
        for (Triple nonColide : this.nonColliders)
            if (other.colliders.contains(nonColide))
                return false;

        Collection colidePairsOther = makePairs(other.colliders);
        Collection nonColidePairsOther = makePairs(other.nonColliders);

        /* checks for overlap between removes and noncolliders */
        for (Edge e : this.removes) {
            NodePair rem = new NodePair(e.getNode1(), e.getNode2());
            if (colidePairsOther.contains(rem) || nonColidePairsOther.contains(rem)
                    || other.orients.contains(rem))
                return false;
        }

        Collection colidePairsThis = makePairs(this.colliders);
        Collection nonColidePairsThis = makePairs(this.nonColliders);

        /* checks for overlap between removes and colliders/orients*/
        for (Edge e : other.removes) {
            NodePair rem = new NodePair(e.getNode1(), e.getNode2());
            if (colidePairsThis.contains(rem) || nonColidePairsThis.contains(rem)
                    || this.orients.contains(rem))
                return false;
        }

        return true;
    }


    /**
     * Outputs a new Pag, a copy of the input excepting the applied changes of this object. Will return null if some
     * change fails (ie an obscure inconsistensy).
     */
    public Graph applyTo(Graph graph) {
        Graph output = new EdgeListGraph(graph);
        output = makeNewEdges(output);

        for (Triple t : nonColliders)
            output.addUnderlineTriple(t.getX(), t.getY(), t.getZ());

        for (Edge e : removes)
            if (!output.removeEdge(e))
                return null;

        Collection<OrderedNodePair> allOrients = makePairs(colliders);
        allOrients.addAll(pairToOrdered(orients));

        for (OrderedNodePair or : allOrients) {
            Node to = or.getSecond();
            Node from = or.getFirst();

            if (!output.setEndpoint(from, to, Endpoint.ARROW))
                return null;
        }

        return output;
    }


    /**
     * Add another remove operation to the GraphChange.
     */
    public void addRemove(Edge removalEdge) {
        removes.add(removalEdge);
    }


    /**
     * Add another collider operation to the GraphChange.
     */
    public void addCollider(Triple colliderTrip) {
        colliders.add(colliderTrip);
    }


    /**
     * Add another non-collider operation to the GraphChange.
     */
    public void addNonCollider(Triple nonColliderTrip) {
        nonColliders.add(nonColliderTrip);
    }


    /**
     * Add another orient operation to the GraphChange.
     */
    public void addOrient(Node from, Node to) {
        orients.add(new NodePair(from, to));
    }


    /**
     * Contains is defined such that if the internal strucs of this GraphChange all individually contain the elements in
     * the corresponding strucs of GraphChange gc, then this "contains" gc.
     */
    public boolean contains(GraphChange gc) {
        if (!removes.containsAll(gc.removes)) return false;
        if (!colliders.containsAll(gc.colliders)) return false;
        if (!nonColliders.containsAll(gc.nonColliders)) return false;
        if (!orients.containsAll(gc.orients)) return false;

        return true;
    }


    /**
     * Anly outputs ops which have elements, not empty structures.
     */
    public String toString() {
        String ret = "[ ";
//        ret = "\n" + super.toString();
        if (!removes.isEmpty())
            ret = ret + "\n removes: " + removes.toString();
        if (!colliders.isEmpty())
            ret = ret + "\n colliders: " + colliders.toString();
        if (!nonColliders.isEmpty())
            ret = ret + "\n nonColliders: " + nonColliders.toString();
        if (!orients.isEmpty())
            ret = ret + "\n orient: " + orients.toString();
        ret = ret + " ]";
        return ret;
    }

    /**
     * Return colliders
     */
    public List<Triple> getColliders() {
        return colliders;
    }

    /**
     * Return noncolliders
     */
    public List<Triple> getNoncolliders() {
        return nonColliders;
    }

    /**
     * Return removes
     */
    public List<Edge> getRemoves() {
        return removes;
    }

    /**
     * Return orients
     */
    public List<NodePair> getOrients() {
        return orients;
    }


    /**
     * Equals is defined such that if the internal strucs of this GraphChange all individually equal the corresponding
     * strucs of GraphChange gc, then this "equals" gc
     */
    public boolean equals(Object other) {
        if (!(other instanceof GraphChange))
            return false;
        GraphChange otherGC = (GraphChange) other;

        if (!otherGC.removes.equals(this.removes) ||
                !otherGC.colliders.equals(this.colliders) ||
                !otherGC.nonColliders.equals(this.nonColliders) ||
                !otherGC.orients.equals(this.orients))
            return false;

        return true;
    }


    public int hashCode() {
        int hash = 1;
        hash *= 17 * removes.hashCode();
        hash *= 19 * colliders.hashCode();
        hash *= 7 * nonColliders.hashCode();
        hash *= 23 * orients.hashCode();
        return hash;
    }


    /**
     * ******************************************************************************** /* Private
     * /***********************************************************************************
     * <p/>
     * <p/>
     * /** creates OrderedNodePairs out of given List. For use in consistent and applyTo
     */
    private Collection<OrderedNodePair> makePairs(List<Triple> input) {
        HashSet<OrderedNodePair> outputPairs = new HashSet<OrderedNodePair>();
        for (Triple trip : input) {
            Node y = trip.getY();
            outputPairs.add(new OrderedNodePair(trip.getX(), y));
            outputPairs.add(new OrderedNodePair(trip.getZ(), y));
        }
        return outputPairs;
    }

    /**
     * Creates a List of OrderedNodePairs from a datastructure of NodePairs.
     */
    private Collection<OrderedNodePair> pairToOrdered(List<NodePair> orig) {
        List<OrderedNodePair> ordered = new ArrayList<OrderedNodePair>(orig.size());

        for (NodePair p : orig) {
            ordered.add(new OrderedNodePair(p.getFirst(), p.getSecond()));
        }

        return ordered;
    }

    /**
     * Takes a graph and recreates all edges. Used in order to copy a graph, because the copy constructor does not go
     * all the way down through the datastructures to make entirely new objects for everything
     */
    private Graph makeNewEdges(Graph graph) {
        Set<Edge> origEdges = graph.getEdges();

        for (Edge e : origEdges) {
            Edge newEdge = new Edge(e.getNode1(), e.getNode2(),
                    e.getEndpoint1(), e.getEndpoint2());
            graph.removeEdge(e);
            graph.addEdge(newEdge);
        }

        return graph;
    }


    /**
     * Almost a direct copy of edu.cmu.tetrad.graph.NodePair. While  NodePairs can be partially tricked into preserving
     * order of nodes, its .equals does not acknowledge said convention, thus the need for OrderedNodePairs on occasion
     */
    private class OrderedNodePair extends NodePair {

        public OrderedNodePair(Node first, Node second) {
            super(first, second);
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof NodePair)) {
                return false;
            }
            NodePair thatPair = (NodePair) o;
            return super.getFirst().equals(thatPair.getFirst())
                    && super.getSecond().equals(thatPair.getSecond());

        }
    }
}



