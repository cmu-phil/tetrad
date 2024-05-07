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

/**
 * Determines whether nodes indexed as (n1, center, n2) form a legal pair of edges in a graph for purposes of some
 * algorithm that uses this information. The pair would be n1---center---n2.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface LegalPairs {

    /**
     * <p>isLegalFirstEdge.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return true iff x*-*y is a legal first edge for the base case.
     */
    boolean isLegalFirstEdge(Node x, Node y);

    /**
     * <p>isLegalPair.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @param c a {@link java.util.List} object
     * @param d a {@link java.util.List} object
     * @return true iff n1---center---n2 is a legal pair.
     */
    boolean isLegalPair(Node x, Node y, Node z, List<Node> c, List<Node> d);
}




