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
 * @author Trevor Burns
 * <p>
 * GraphChange is a data structure created mainly for use in the ION search algorithm. However, it models a general
 * concept: storage for a variety of seperate changes one could possibly apply to a PAG.
 * <p>
 * By convention, the NodePairs in the orients ArrayList are ordered such that Node1 is the "from" node and Node2 is the
 * "to" node (ie 1 o-> 2)
 */
public class GraphChange {
    private final List<Edge> removes;
    private final List<Triple> colliders;
    private final List<Triple> nonColliders;
    private final List<NodePair> orients;

    /**
     * Default constructor, holds no changes.
     */
    public GraphChange() {
        this.removes = new ArrayList<>();
        this.colliders = new ArrayList<>();
        this.nonColliders = new ArrayList<>();
        this.orients = new ArrayList<>();
    }

    /**
     * Copy constructor.
     */
    public GraphChange(final GraphChange source) {
        this.removes = new ArrayList<>(source.removes);
        this.colliders = new ArrayList<>(source.colliders);
        this.nonColliders = new ArrayList<>(source.nonColliders);
        this.orients = new ArrayList<>(source.orients);
    }


    /**
     * Absorbs all changes from the GraphChange other into the calling GraphChange.
     */
    public void union(final GraphChange other) {
        this.removes.addAll(other.removes);
        this.colliders.addAll(other.colliders);
        this.nonColliders.addAll(other.nonColliders);
        this.orients.addAll(other.orients);
    }


    /**
     * Consistency check, nonexhaustive, but catches the most blatant inconsistencies.
     */
    public boolean isConsistent(final GraphChange other) {

        /* checks same triples being marked as colliders and non colliders */
        for (final Triple colide : this.colliders)
            if (other.nonColliders.contains(colide))
                return false;
        for (final Triple nonColide : this.nonColliders)
            if (other.colliders.contains(nonColide))
                return false;

        final Collection colidePairsOther = makePairs(other.colliders);
        final Collection nonColidePairsOther = makePairs(other.nonColliders);

        /* checks for overlap between removes and noncolliders */
        for (final Edge e : this.removes) {
            final NodePair rem = new NodePair(e.getNode1(), e.getNode2());
            if (colidePairsOther.contains(rem) || nonColidePairsOther.contains(rem)
                    || other.orients.contains(rem))
                return false;
        }

        final Collection colidePairsThis = makePairs(this.colliders);
        final Collection nonColidePairsThis = makePairs(this.nonColliders);

        /* checks for overlap between removes and colliders/orients*/
        for (final Edge e : other.removes) {
            final NodePair rem = new NodePair(e.getNode1(), e.getNode2());
            if (colidePairsThis.contains(rem) || nonColidePairsThis.contains(rem)
                    || this.orients.contains(rem))
                return false;
        }

        return true;
    }


    /**
     * Outputs a new PAG, a copy of the input excepting the applied changes of this object. Will return null if some
     * change fails (ie an obscure inconsistensy).
     */
    public Graph applyTo(final Graph graph) {
        Graph output = new EdgeListGraph(graph);
        output = makeNewEdges(output);

        for (final Triple t : this.nonColliders)
            output.addUnderlineTriple(t.getX(), t.getY(), t.getZ());

        for (final Edge e : this.removes)
            if (!output.removeEdge(e))
                return null;

        final Collection<OrderedNodePair> allOrients = makePairs(this.colliders);
        allOrients.addAll(pairToOrdered(this.orients));

        for (final OrderedNodePair or : allOrients) {
            final Node to = or.getSecond();
            final Node from = or.getFirst();

            if (!output.setEndpoint(from, to, Endpoint.ARROW))
                return null;
        }

        return output;
    }


    /**
     * Add another remove operation to the GraphChange.
     */
    public void addRemove(final Edge removalEdge) {
        this.removes.add(removalEdge);
    }


    /**
     * Add another collider operation to the GraphChange.
     */
    public void addCollider(final Triple colliderTrip) {
        this.colliders.add(colliderTrip);
    }


    /**
     * Add another non-collider operation to the GraphChange.
     */
    public void addNonCollider(final Triple nonColliderTrip) {
        this.nonColliders.add(nonColliderTrip);
    }


    /**
     * Add another orient operation to the GraphChange.
     */
    public void addOrient(final Node from, final Node to) {
        this.orients.add(new NodePair(from, to));
    }


    /**
     * Contains is defined such that if the internal strucs of this GraphChange all individually contain the elements in
     * the corresponding strucs of GraphChange gc, then this "contains" gc.
     */
    public boolean contains(final GraphChange gc) {
        if (!this.removes.containsAll(gc.removes)) return false;
        if (!this.colliders.containsAll(gc.colliders)) return false;
        if (!this.nonColliders.containsAll(gc.nonColliders)) return false;
        return this.orients.containsAll(gc.orients);
    }


    /**
     * Anly outputs ops which have elements, not empty structures.
     */
    public String toString() {
        String ret = "[ ";
//        ret = "\n" + super.toString();
        if (!this.removes.isEmpty())
            ret = ret + "\n removes: " + this.removes;
        if (!this.colliders.isEmpty())
            ret = ret + "\n colliders: " + this.colliders;
        if (!this.nonColliders.isEmpty())
            ret = ret + "\n nonColliders: " + this.nonColliders;
        if (!this.orients.isEmpty())
            ret = ret + "\n orient: " + this.orients;
        ret = ret + " ]";
        return ret;
    }

    /**
     * Return colliders
     */
    public List<Triple> getColliders() {
        return this.colliders;
    }

    /**
     * Return noncolliders
     */
    public List<Triple> getNoncolliders() {
        return this.nonColliders;
    }

    /**
     * Return removes
     */
    public List<Edge> getRemoves() {
        return this.removes;
    }

    /**
     * Return orients
     */
    public List<NodePair> getOrients() {
        return this.orients;
    }


    /**
     * Equals is defined such that if the internal strucs of this GraphChange all individually equal the corresponding
     * strucs of GraphChange gc, then this "equals" gc
     */
    public boolean equals(final Object other) {
        if (!(other instanceof GraphChange))
            return false;
        final GraphChange otherGC = (GraphChange) other;

        return otherGC.removes.equals(this.removes) &&
                otherGC.colliders.equals(this.colliders) &&
                otherGC.nonColliders.equals(this.nonColliders) &&
                otherGC.orients.equals(this.orients);
    }


    public int hashCode() {
        int hash = 1;
        hash *= 17 * this.removes.hashCode();
        hash *= 19 * this.colliders.hashCode();
        hash *= 7 * this.nonColliders.hashCode();
        hash *= 23 * this.orients.hashCode();
        return hash;
    }


    /**
     * ******************************************************************************** /* Private
     * /***********************************************************************************
     * <p>
     * <p>
     * /** creates OrderedNodePairs out of given List. For use in consistent and applyTo
     */
    private Collection<OrderedNodePair> makePairs(final List<Triple> input) {
        final HashSet<OrderedNodePair> outputPairs = new HashSet<>();
        for (final Triple trip : input) {
            final Node y = trip.getY();
            outputPairs.add(new OrderedNodePair(trip.getX(), y));
            outputPairs.add(new OrderedNodePair(trip.getZ(), y));
        }
        return outputPairs;
    }

    /**
     * Creates a List of OrderedNodePairs from a datastructure of NodePairs.
     */
    private Collection<OrderedNodePair> pairToOrdered(final List<NodePair> orig) {
        final List<OrderedNodePair> ordered = new ArrayList<>(orig.size());

        for (final NodePair p : orig) {
            ordered.add(new OrderedNodePair(p.getFirst(), p.getSecond()));
        }

        return ordered;
    }

    /**
     * Takes a graph and recreates all edges. Used in order to copy a graph, because the copy constructor does not go
     * all the way down through the datastructures to make entirely new objects for everything
     */
    private Graph makeNewEdges(final Graph graph) {
        final Set<Edge> origEdges = graph.getEdges();

        for (final Edge e : origEdges) {
            final Edge newEdge = new Edge(e.getNode1(), e.getNode2(),
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

        public OrderedNodePair(final Node first, final Node second) {
            super(first, second);
        }

        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof NodePair)) {
                return false;
            }
            final NodePair thatPair = (NodePair) o;
            return this.getFirst().equals(thatPair.getFirst())
                    && this.getSecond().equals(thatPair.getSecond());

        }
    }
}



