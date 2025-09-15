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

package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import java.util.HashSet;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AdjacencyConfusion {

    /**
     * The true negative count.
     */
    private final int tn;

    /**
     * The true positive count.
     */
    private int tp;

    /**
     * The false positive count.
     */
    private int fp;


    private int fn;

    /**
     * Constructs a new AdjacencyConfusion object from the given graphs.
     *
     * @param truth The true graph.
     * @param est   The estimated graph.
     */
    public AdjacencyConfusion(Graph truth, Graph est) {
        this.tp = 0;
        this.fp = 0;
        this.fn = 0;

        Set<Edge> allUnoriented = new HashSet<>();
        for (Edge edge : truth.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : est.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : allUnoriented) {
            if (est.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                !truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fp++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                !est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fn++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tp++;
            }
        }

        int allEdges = truth.getNumNodes() * (truth.getNumNodes() - 1) / 2;

        this.tn = allEdges - this.fn - this.fp - this.fn;
    }

    /**
     * Returns the true positive count.
     *
     * @return the true positive count.
     */
    public int getTp() {
        return this.tp;
    }

    /**
     * Returns the false positive count.
     *
     * @return the false positive count.
     */
    public int getFp() {
        return this.fp;
    }

    /**
     * Returns the false negative count.
     *
     * @return the false negative count.
     */
    public int getFn() {
        return this.fn;
    }

    /**
     * Returns the true negative count.
     *
     * @return the true negative count.
     */
    public int getTn() {
        return this.tn;
    }

}

