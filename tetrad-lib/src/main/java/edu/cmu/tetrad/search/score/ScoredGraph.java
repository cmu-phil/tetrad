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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Stores a graph with a score for the graph. The equals, hashcode, and compare methods are overridden so that it will
 * be easy to put these stored graphs into sets and lists.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ScoredGraph implements Comparable<ScoredGraph>, TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph.
     */
    private final Graph graph;
    /**
     * The score.
     */
    private final Double score;

    /**
     * Constructs a scored graph.
     *
     * @param graph The graph.
     * @param score The score.
     */
    public ScoredGraph(Graph graph, Double score) {
        this.graph = graph;
        this.score = score;
    }

    /**
     * Returns a serializable instance of this class.
     *
     * @return A serializable instance of this class.
     */
    public static ScoredGraph serializableInstance() {
        return new ScoredGraph(new EdgeListGraph(), 0.0);
    }

    /**
     * Returns the graph.
     *
     * @return The graph.
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * Returns the score.
     *
     * @return The score.
     */
    public double getScore() {
        return this.score;
    }

    /**
     * Return s the hashcode of the score.
     *
     * @return The hashcode of the score.
     */
    public int hashCode() {
        return this.score.hashCode();
    }

    /**
     * Returns true if the scoreed graph and this scored graph are equal.
     *
     * @param o The other scored graph.
     * @return True if the score and graph are equal.
     */
    public boolean equals(ScoredGraph o) {
        if (!this.score.equals(o.getScore())) {
            return false;
        }

        return this.graph.equals(o.getGraph());
    }

    /**
     * Returns a compare value for this scored graph compared ot the given scored graph.
     *
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
     * the specified object.
     */
    public int compareTo(@NotNull ScoredGraph o) {
        Double thisScore = getScore();
        Double otherScore = o.getScore();
        return thisScore.compareTo(otherScore);
    }
}



