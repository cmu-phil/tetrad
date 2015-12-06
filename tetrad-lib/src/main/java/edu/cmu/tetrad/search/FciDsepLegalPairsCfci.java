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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;

import java.util.List;
import java.util.Set;

/**
 * Specifies what coefs as a legal pair of edges X---Y---Z for purposes of calculating possible d-separation sets for
 * the FCI algorithm. In this case, legal initial edges are those adjacent to initial nodes, and legal pairs of edges
 * are those for which either X-->Y<--Z or X is adjacent to Z--i.e. X, Y, and Z form a triangle. (It is assumed (and
 * checked) that is adjacent to Y and Y is adjacent to Z.)
 *
 * @author Joseph Ramsey
 */
class FciDsepLegalPairsCfci implements LegalPairs {

    /**
     * Graph with respect to which graph properties are tested.
     */
    private Graph graph;
    private Set<Triple> ambiguousTriples;

    /**
     * Constructs a new legal pairs object. See class level doc.
     *
     * @param graph The graph with respect to which legal pairs will be tested.
     */
    public FciDsepLegalPairsCfci(Graph graph, Set<Triple> unfaithfulTriples) {
        if (graph == null) {
            throw new NullPointerException();
        }

        this.graph = graph;
        this.ambiguousTriples = unfaithfulTriples;
    }

    /**
     * @return true iff x is adjacent to y.
     */
    public boolean isLegalFirstEdge(Node x, Node y) {
        return this.graph.isAdjacentTo(x, y);
    }

    /**
     * @return true iff x-->y<--z or else x is adjacent to z.
     * @throws IllegalArgumentException if x is not adjacent to y or y is not adjacent to z.
     */
    public boolean isLegalPair(Node x, Node y, Node z, List<Node> c,
                               List<Node> d) {
        if (!(graph.isAdjacentTo(x, y)) || !(graph.isAdjacentTo(y, z))) {
            throw new IllegalArgumentException();
        }

        if (graph.isDefCollider(x, y, z)) {
            return true;
        }

//        if (graph.getEndpoint(x, y) == Endpoint.TAIL || graph.getEndpoint(z, y) == Endpoint.TAIL) {
//            return false;
//        }

        if (ambiguousTriples.contains(new Triple(x, y, z))) {
            return true;
        }

        return graph.isAdjacentTo(x, z);
    }
}



