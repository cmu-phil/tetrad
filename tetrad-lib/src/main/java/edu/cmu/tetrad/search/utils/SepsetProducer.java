/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;
import java.util.Set;

/**
 * Provides a covenience interface for classes that can generate and keep track of sepsets.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see SepsetMap
 */
public interface SepsetProducer {

    /**
     * Retrieves the sepset, which is the set of common neighbors between two given nodes.
     *
     * @param a     the first node
     * @param b     the second node
     * @param depth the depth of the search
     * @param order the order of the nodes, used for some implementations
     * @return the set of common neighbors between nodes a and b
     * @throws InterruptedException if any
     */
    Set<Node> getSepset(Node a, Node b, int depth, List<Node> order) throws InterruptedException;

    /**
     * Retrieves a sepset containing nodes in s from the given set of nodes.
     *
     * @param a     the first node
     * @param b     the second node
     * @param s     the set of nodes
     * @param depth the depth of the search
     * @return the sepset containing nodes a and b from the given set of nodes
     * @throws InterruptedException if any
     */
    Set<Node> getSepsetContaining(Node a, Node b, Set<Node> s, int depth) throws InterruptedException;

    /**
     * <p>isUnshieldedCollider.</p>
     *
     * @param i     a {@link Node} object
     * @param j     a {@link Node} object
     * @param k     a {@link Node} object
     * @param depth the depth of the search
     * @return a boolean
     * @throws InterruptedException if any
     */
    boolean isUnshieldedCollider(Node i, Node j, Node k, int depth) throws InterruptedException;

    /**
     * Returns the score of the object.
     *
     * @return the score value
     */
    double getScore();

    /**
     * Retrieves the list of variables.
     *
     * @return the list of variables as a {@link List} of {@link Node} objects.
     */
    List<Node> getVariables();

    /**
     * Sets the verbose mode of the SepsetProducer.
     *
     * @param verbose true if verbose mode is enabled, false otherwise
     */
    void setVerbose(boolean verbose);

    /**
     * Checks if node d is independent of node c given the set of nodes in sepset.
     *
     * @param d      the first node
     * @param c      the second node
     * @param sepset the set of common neighbors between d and c
     * @return true if d is independent of c, false otherwise
     * @throws InterruptedException if any
     */
    boolean isIndependent(Node d, Node c, Set<Node> sepset) throws InterruptedException;

    /**
     * Calculates the p-value for a statistical test a _||_ b | sepset.
     *
     * @param a      the first node
     * @param b      the second node
     * @param sepset the set of nodes
     * @return the p-value for the statistical test
     * @throws InterruptedException if any
     */
    double getPValue(Node a, Node b, Set<Node> sepset) throws InterruptedException;

    /**
     * Sets the graph for the SepsetProducer object.
     *
     * @param graph the graph to set
     */
    void setGraph(Graph graph);
}

