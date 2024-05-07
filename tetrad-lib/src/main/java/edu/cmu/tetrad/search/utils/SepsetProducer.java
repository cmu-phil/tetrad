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

package edu.cmu.tetrad.search.utils;

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
     * <p>getSepset.</p>
     *
     * @param a a {@link edu.cmu.tetrad.graph.Node} object
     * @param b a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.Set} object
     */
    Set<Node> getSepset(Node a, Node b);

    /**
     * Returns the subset for a and b, where this sepset is expected to contain all the nodes in s. The behavior is
     * morphed depending on whether sepsets are calculated using an independence test or not. If sepsets are calculated
     * using an independence test, and a sepset is not found containing all the nodes in s, then the method will return
     * null. Otherwise, if the discovered sepset does not contain all the nodes in s, the method will throw an
     * exception.
     *
     * @param a the first node
     * @param b the second node
     * @param s the set of nodes
     * @return the set of nodes that sepsets for a and b are expected to contain.
     */
    Set<Node> getSepsetContaining(Node a, Node b, Set<Node> s);

    /**
     * <p>isUnshieldedCollider.</p>
     *
     * @param i a {@link edu.cmu.tetrad.graph.Node} object
     * @param j a {@link edu.cmu.tetrad.graph.Node} object
     * @param k a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean isUnshieldedCollider(Node i, Node j, Node k);

    /**
     * <p>getScore.</p>
     *
     * @return a double
     */
    double getScore();

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getVariables();

    /**
     * <p>setVerbose.</p>
     *
     * @param verbose a boolean
     */
    void setVerbose(boolean verbose);

    /**
     * <p>isIndependent.</p>
     *
     * @param d      a {@link edu.cmu.tetrad.graph.Node} object
     * @param c      a {@link edu.cmu.tetrad.graph.Node} object
     * @param sepset a {@link java.util.Set} object
     * @return a boolean
     */
    boolean isIndependent(Node d, Node c, Set<Node> sepset);

    /**
     * Calculates the p-value for a statistical test a _||_ b | sepset.
     *
     * @param a      the first node
     * @param b      the second node
     * @param sepset the set of nodes
     * @return the p-value for the statistical test
     */
    double getPValue(Node a, Node b, Set<Node> sepset);
}

