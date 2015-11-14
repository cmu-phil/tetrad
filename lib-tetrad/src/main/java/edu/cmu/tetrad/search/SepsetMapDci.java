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

import edu.cmu.tetrad.graph.Node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * This is the same as the usual SepsetMap described below, but also keeps up with the individual sets of conditioning
 * nodes for d-separation relations for use with the Distributed Causal Inference (DCI) algorithm. <p/> <p>Stores a map
 * from pairs of nodes to separating sets--that is, for each unordered pair of nodes {node1, node2} in a graph, stores a
 * set of nodes conditional on which node1 and node2 are independent (where the nodes are considered as variables) or
 * stores null if the pair was not judged to be independent. (Note that if a sepset is non-null and empty, that should
 * means that the compared nodes were found to be independent conditional on the empty set, whereas if a sepset is null,
 * that should mean that no set was found yet conditional on which the compared nodes are independent. So at the end of
 * the search, a null sepset carries different information from an empty sepset.)</p> <p>We cast the variable-like
 * objects to Node to allow them either to be variables explicitly or else to be graph nodes that in some model could be
 * considered as variables. This allows us to use d-separation as a graphical indicator of what independendence in
 * models ideally should be.</p>
 *
 * @author Robert Tillman
 */
public final class SepsetMapDci {
    static final long serialVersionUID = 23L;

    /**
     * @serial
     */
    private Map<Set<Node>, List<Node>> sepsets =
            new HashMap<Set<Node>, List<Node>>();

    private Map<Set<Node>, List<List<Node>>> sepsetSets =
            new HashMap<Set<Node>, List<List<Node>>>();

    private Map<Node, LinkedHashSet<Node>> parents = new HashMap<Node, LinkedHashSet<Node>>();

    //=============================CONSTRUCTORS===========================//

    public SepsetMapDci() {
    }

    public SepsetMapDci(SepsetMapDci map) {
        this.sepsets = new HashMap<Set<Node>, List<Node>>(map.sepsets);
        this.sepsetSets = new HashMap<Set<Node>, List<List<Node>>>(map.sepsetSets);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SepsetMap serializableInstance() {
        return new SepsetMap();
    }

    //=============================PUBLIC METHODS========================//

    /**
     * Sets the sepset for {x, y} to be z. Note that {x, y} is unordered.
     */
    public void set(Node x, Node y, List<Node> z) {
        Set<Node> pair = new HashSet<Node>(2);
        pair.add(x);
        pair.add(y);
        if (sepsets.get(pair) == null) {
            sepsets.put(pair, z);
        } else {
            List<Node> newSet = new ArrayList<Node>(sepsets.get(pair));
            newSet.addAll(z);
            sepsets.put(pair, newSet);
        }
        if (sepsetSets.containsKey(pair)) {
            sepsetSets.get(pair).add(new ArrayList<Node>(z));
        } else {
            List<List<Node>> condSets = new ArrayList<List<Node>>();
            condSets.add(new ArrayList<Node>(z));
            sepsetSets.put(pair, condSets);
        }
    }

    /**
     * Retrieves the sepset previously set for {x, y}, or null if no such set
     * was previously set.
     */
    public List<Node> get(Node x, Node y) {
        Set<Node> pair = new HashSet<Node>(2);
        pair.add(x);
        pair.add(y);
        return sepsets.get(pair);
    }

    /**
     * Retrieves the set of all condioning sets for {x, y} or null if no such
     * set was ever set
     */
    public List<List<Node>> getSet(Node x, Node y) {
        Set<Node> pair = new HashSet<Node>(2);
        pair.add(x);
        pair.add(y);
        return sepsetSets.get(pair);
    }

    public void set(Node x, LinkedHashSet<Node> z) {
        if (parents.get(x) != null) {
            parents.get(x).addAll(z);
        } else {
            parents.put(x, z);
        }
    }

    public LinkedHashSet<Node> get(Node x) {
        return parents.get(x) == null ? new LinkedHashSet<Node>() : parents.get(x);
    }

    public Set<Set<Node>> getSeparatedPairs() {
        return sepsets.keySet();
    }

    public boolean equals(Object o) {
        if (!(o instanceof SepsetMapDci)) {
            return false;
        }

        SepsetMapDci _sepset = (SepsetMapDci) o;
        return sepsets.equals(_sepset.sepsets) && sepsetSets.equals(_sepset.sepsetSets);
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

        if (sepsets == null) {
            throw new NullPointerException();
        }
    }

    public int size() {
        return sepsets.keySet().size();
    }

    public String toString() {
        return sepsets.toString() + "\n" + sepsetSets.toString();
    }
}





