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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Interface for a score. Most methods are given defaults so that such a score will be easy to implement in Python usign
 * JPype.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Score {

    /**
     * The score of a node given its parents.
     *
     * @param node    The node.
     * @param parents The parents.
     * @return The score.
     */
    double localScore(int node, int... parents);

    /**
     * The variables of the score.
     *
     * @return This list.
     */
    List<Node> getVariables();

    /**
     * The sample size of the data.
     *
     * @return This size.
     */
    int getSampleSize();

    /**
     * A string representation of the score.
     *
     * @return This string.
     */
    String toString();


    /**
     * Returns the score difference of the graph.
     *
     * @param x A node.
     * @param y TAhe node.
     * @param z A set of nodes.
     * @return The score difference.
     */
    default double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Appends an extra int to a list of ints.
     *
     * @param parents The list of ints.
     * @param extra   The extra int.
     * @return The new list of ints.
     */
    default int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Returns the local score difference of the graph.
     *
     * @param x A node.
     * @param y The node.
     * @return The local score difference.
     */
    default double localScoreDiff(int x, int y) {
        return localScore(y, x) - localScore(y);
    }

    /**
     * Returns the local score of the graph.
     *
     * @param node   A node.
     * @param parent A parent.
     * @return The local score.
     */
    default double localScore(int node, int parent) {
        return localScore(node, new int[]{parent});
    }

    /**
     * Returns the local score of the gien node in the graph.
     *
     * @param node A node.
     * @return The local score.
     */
    default double localScore(int node) {
        return localScore(node, new int[0]);
    }

    /**
     * Returns the variable with the given name.
     *
     * @param targetName The name.
     * @return The variable.
     */
    default Node getVariable(String targetName) {
        for (Node node : getVariables()) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    /**
     * Returns true iff the edge between x and y is an effect edge.
     *
     * @param bump The bump.
     * @return True iff the edge between x and y is an effect edge.
     */
    default boolean isEffectEdge(double bump) {
        return false;
    }

    /**
     * Returns the max degree, by default 1000.
     *
     * @return The max degree.
     */
    default int getMaxDegree() {
        return 1000;
    }

    /**
     * Returns true iff the score determines the edge between x and y.
     *
     * @param z The set of nodes.
     * @param y The node.
     * @return True iff the score determines the edge between x and y.
     */
    default boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("Method determines() is not implemented for this score.");
    }
}

