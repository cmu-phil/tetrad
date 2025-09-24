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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

/**
 * The FCI orientation rules are almost entirely taken up with an examination of the FCI graph, but there are two rules
 * that require looking at the distribution. The first is the R0 rule, which orients unshielded colliders in the graph.
 * The second is the R4 rule, which orients certain colliders or tails based on an examination of discriminating paths.
 * For the discriminating path rule, we need to know the sepset for two nodes, e and c, which can only be determined by
 * looking at the distribution.
 * <p>
 * Note that for searches from Oracle, the distribution is not available, but these rules can be applied using knowledge
 * of the true DAG (with latents).
 * <p>
 * Since this can be done in various ways, we separate out a Strategy here for this purpose.
 *
 * @author josephramsey
 */
public interface R0R4Strategy {

    /**
     * Determines if a given triple is an unshielded collider based on an examination of the data.
     *
     * @param graph the graph representation
     * @param a     the first node of the collider path
     * @param b     the second node of the collider path
     * @param c     the third node of the collider path
     * @return true if the collider is unshielded, false otherwise
     */
    boolean isUnshieldedCollider(Graph graph, Node a, Node b, Node c);

    /**
     * Does a discriminating path orientation based on an examination of the data.
     *
     * @param discriminatingPath the discriminating path construct
     * @param graph              the graph to be oriented.
     * @param vNodes             the set of nodes that are v-structures in the graph.
     * @return a pair of the discriminating path construct and a boolean indicating whether the orientation was
     * determined.
     * @throws InterruptedException if the operation is interrupted
     * @see DiscriminatingPath
     */
    Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph, Set<Node> vNodes) throws InterruptedException;

    /**
     * Sets the knowledge object to be used by the strategy.
     *
     * @param knowledge the knowledge object.
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * Returns the knowledge object used by the strategy.
     *
     * @return the knowledge object.
     */
    Knowledge getknowledge();
}

