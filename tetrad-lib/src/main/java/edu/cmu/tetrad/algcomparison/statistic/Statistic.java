/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.io.Serializable;

/**
 * The interface that each statistic needs to implement.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Statistic extends Serializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    @Serial
    long serialVersionUID = 23L;

    /**
     * The abbreviation for the statistic. This will be printed at the top of each column.
     *
     * @return This abbreviation.
     */
    String getAbbreviation();

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Calculates and returns the value of the statistic based on the provided graphs, data model,
     * and parameters.
     *
     * @param trueDag    The true directed acyclic graph (DAG), including any latent and selection nodes, or null if
     *                   no DAG is available. Statistics that ask DAG-level questions (ancestry, latent confounding,
     *                   conversion to CPDAG or PAG) should read them off this graph, via
     *                   {@link #trueDagOrGraph(Graph, Graph)}, not off trueGraph.
     * @param trueGraph  The comparison graph, which could be a DAG, a CPDAG, or a PAG derived from the true DAG.
     *                   Statistics that compare adjacencies or endpoints read them off this graph.
     * @param estGraph   The estimated graph, which corresponds to the same type as the trueGraph.
     * @param dataModel  The data model, which may be null.
     * @param parameters The parameters for the calculation, which may be null.
     * @return The computed value of the statistic as a double.
     */
    double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters);

    /**
     * Returns the graph a statistic should use for DAG-level questions--ancestry, latent confounding, or converting
     * the truth to a CPDAG or PAG. This is the true DAG when the caller supplied one; otherwise it falls back to the
     * comparison graph, which is all that callers of the four-argument getValue methods have. The fallback keeps
     * the pre-existing behavior for those callers; when the comparison graph is itself a CPDAG or PAG, DAG-level
     * questions asked of it are only approximate.
     *
     * @param trueDag   The true DAG, or null if none was supplied.
     * @param trueGraph The comparison graph.
     * @return trueDag if non-null, else trueGraph.
     */
    static Graph trueDagOrGraph(Graph trueDag, Graph trueGraph) {
        return trueDag != null ? trueDag : trueGraph;
    }

    /**
     * Returns the estimated graph with its nodes replaced, by name, with the true graph's node objects. Graph
     * traversal in Tetrad compares nodes by identity, so a statistic handed an estimated graph over different node
     * objects (an algorithm's output over data variables, say) would otherwise find no adjacencies or paths at all
     * and report zeros without complaint. Comparison and CompareTwoGraphs align their graphs before calling the
     * five-argument getValue; the four-argument defaults below do it here for everyone else.
     *
     * @param trueGraph The true graph; may be null, in which case estGraph is returned unchanged.
     * @param estGraph  The estimated graph; may be null, in which case null is returned.
     * @return The aligned estimated graph.
     */
    static Graph alignNodes(Graph trueGraph, Graph estGraph) {
        if (trueGraph == null || estGraph == null) {
            return estGraph;
        }

        return GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph. The estimated graph is
     * aligned to the true graph's node objects first; see {@link #alignNodes(Graph, Graph)}.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model (can be null).
     * @param parameters The parameters (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return getValue(null, trueGraph, alignNodes(trueGraph, estGraph), dataModel, parameters);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return getValue(trueGraph, estGraph, dataModel, null);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param parameters The parameters (can be null).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph, Parameters parameters) {
        return getValue(trueGraph, estGraph, null, parameters);
    }

    /**
     * Returns the value of this statistic, given the true graph and the estimated graph.
     *
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @return The value of the statistic.
     */
    default double getValue(Graph trueGraph, Graph estGraph) {
        return getValue(trueGraph, estGraph, null, null);
    }

    /**
     * Returns a mapping of the statistic to the interval [0, 1], with higher being better. This is used for a
     * calculation of a utility for an algorithm. If the statistic is already between 0 and 1, you can just return the
     * statistic.
     *
     * @param value The value of the statistic.
     * @return The weight of the statistic, 0 to 1, higher is better.
     */
    double getNormValue(double value);

    /**
     * Returns true if this statistics makes use of knowledge of the true graph. When analyzing
     * empirical data where the truth is not known, such statistic cannot be used, so we should
     * hide it from the user so they don't accidentally select it (and get a bunch of NaN's in
     * their results).
     *
     * @return True if this statistic uses the true graph, false otherwise.
     */
    default boolean usesTruth() {
        return true;
    }
}

