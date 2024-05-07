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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.SepsetMap;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * This is the same as the usual SepsetMap described below, but also keeps up with the individual sets of conditioning
 * nodes for d-separation relations for use with the Distributed Causal Inference (DCI) algorithm.  <p>Stores a map from
 * pairs of nodes to separating sets--that is, for each unordered pair of nodes {node1, node2} in a graph, stores a set
 * of nodes conditional on which node1 and node2 are independent (where the nodes are considered as variables) or stores
 * null if the pair was not judged to be independent. (Note that if a sepset is non-null and empty, that should means
 * that the compared nodes were found to be independent conditional on the empty set, whereas if a sepset is null, that
 * should mean that no set was found yet conditional on which the compared nodes are independent. So at the end of the
 * search, a null sepset carries different information from an empty sepset.)&gt; 0 <p>We cast the variable-like objects
 * to Node to allow them either to be variables explicitly or else to be graph nodes that in some model could be
 * considered as variables. This allows us to use d-separation as a graphical indicator of what independendence in
 * models ideally should be.&gt; 0
 *
 * @author Robert Tillman
 * @version $Id: $Id
 */
public final class SepsetMapDci {
    private static final long serialVersionUID = 23L;
    private final Map<Node, LinkedHashSet<Node>> parents = new HashMap<>();
    /**
     * @serial
     */
    private Map<Set<Node>, Set<Node>> sepsets =
            new HashMap<>();
    private Map<Set<Node>, Set<Set<Node>>> sepsetSets =
            new HashMap<>();

    //=============================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for SepsetMapDci.</p>
     */
    public SepsetMapDci() {
    }

    /**
     * <p>Constructor for SepsetMapDci.</p>
     *
     * @param map a {@link edu.cmu.tetrad.search.work_in_progress.SepsetMapDci} object
     */
    public SepsetMapDci(SepsetMapDci map) {
        this.sepsets = new HashMap<>(map.sepsets);
        this.sepsetSets = new HashMap<>(map.sepsetSets);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.search.utils.SepsetMap} object
     */
    public static SepsetMap serializableInstance() {
        return new SepsetMap();
    }

    //=============================PUBLIC METHODS========================//

    /**
     * Sets the sepset for {x, y} to be z. Note that {x, y} is unordered.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.Set} object
     */
    public void set(Node x, Node y, Set<Node> z) {
        Set<Node> pair = new HashSet<>(2);
        pair.add(x);
        pair.add(y);
        if (this.sepsets.get(pair) == null) {
            this.sepsets.put(pair, z);
        } else {
            Set<Node> newSet = new HashSet<>(this.sepsets.get(pair));
            newSet.addAll(z);
            this.sepsets.put(pair, newSet);
        }
        if (this.sepsetSets.containsKey(pair)) {
            this.sepsetSets.get(pair).add(new HashSet<>(z));
        } else {
            Set<Set<Node>> condSets = new HashSet<>();
            condSets.add(new HashSet<>(z));
            this.sepsetSets.put(pair, condSets);
        }
    }

    /**
     * Retrieves the sepset previously set for {x, y}, or null if no such set was previously set.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     */
    public Set<Node> get(Node x, Node y) {
        Set<Node> pair = new HashSet<>(2);
        pair.add(x);
        pair.add(y);
        return this.sepsets.get(pair);
    }

    /**
     * Retrieves the set of all condioning sets for {x, y} or null if no such set was ever set
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     */
    public Set<Set<Node>> getSet(Node x, Node y) {
        Set<Node> pair = new HashSet<>(2);
        pair.add(x);
        pair.add(y);
        return this.sepsetSets.get(pair);
    }

    /**
     * <p>set.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.LinkedHashSet} object
     */
    public void set(Node x, LinkedHashSet<Node> z) {
        if (this.parents.get(x) != null) {
            this.parents.get(x).addAll(z);
        } else {
            this.parents.put(x, z);
        }
    }

    /**
     * <p>get.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.LinkedHashSet} object
     */
    public LinkedHashSet<Node> get(Node x) {
        return this.parents.get(x) == null ? new LinkedHashSet<>() : this.parents.get(x);
    }

    /**
     * <p>getSeparatedPairs.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Set<Node>> getSeparatedPairs() {
        return this.sepsets.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (!(o instanceof SepsetMapDci _sepset)) {
            return false;
        }

        return this.sepsets.equals(_sepset.sepsets) && this.sepsetSets.equals(_sepset.sepsetSets);
    }


    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.sepsets == null) {
            throw new NullPointerException();
        }
    }

    /**
     * <p>size.</p>
     *
     * @return a int
     */
    public int size() {
        return this.sepsets.keySet().size();
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.sepsets.toString() + "\n" + this.sepsetSets.toString();
    }
}





