///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
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





