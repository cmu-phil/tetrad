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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.GrowShrinkTree;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface for suborder searches for various types of permutation algorithms. A "suborder search" is a search for
 * permutation &lt;x1a,...x1n, x2a,...,x2m, x3a,...,x3l&gt;> that searches for a good permutation of x2a,...,x2m with
 * x1a,...,x1n as a prefix. This is used by PermutationSearch to form a complete permutation search algorithm, where
 * PermutationSearch handles an optimization for tiered knowledge where each tier can be searched separately in order.
 * (See the documentation for that class.)
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author bryanandrews
 * @version $Id: $Id
 * @see PermutationSearch
 * @see Boss
 * @see Sp
 * @see Knowledge
 */
public interface SuborderSearch {

    /**
     * Searches the suborder.
     *
     * @param prefix   The prefix of the suborder.
     * @param suborder The suborder.
     * @param gsts     The GrowShrinkTree being used to do caching of scores.
     * @throws InterruptedException If the search is interrupted.
     * @see GrowShrinkTree
     */
    void searchSuborder(List<Node> prefix, List<Node> suborder, Map<Node, GrowShrinkTree> gsts) throws InterruptedException;

    /**
     * The knowledge being used.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * The list of all variables, in order. They should satisfy the suborder requirements.
     *
     * @return This list.
     * @see Node
     * @see edu.cmu.tetrad.data.Variable
     */
    List<Node> getVariables();

    /**
     * The map from nodes to parents resulting from the search.
     *
     * @return This map.
     */
    Map<Node, Set<Node>> getParents();

    /**
     * The score being used.
     *
     * @return This score.
     * @see Score
     */
    Score getScore();
}

