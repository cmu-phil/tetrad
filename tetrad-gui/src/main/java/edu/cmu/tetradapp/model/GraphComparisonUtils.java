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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Shared logic for the graph-graph comparison session models (TabularComparison, Misclassifications,
 * EdgewiseComparisonModel) when they are given an arbitrary number of graph sources. One of the sources is the
 * reference (true) graph; the rest are targets, each compared against it. The reference is chosen by the
 * "referenceGraphName" parameter (set by the params editor), falling back to a Simulation box if one is attached,
 * and the targets are ordered by the "targetGraphNames" parameter, falling back to name order. Session parents
 * arrive from a HashSet, so every fallback here must be deterministic.
 *
 * @author josephramsey
 */
public final class GraphComparisonUtils {

    /**
     * No constructor for utility class.
     */
    private GraphComparisonUtils() {
    }

    /**
     * Returns the given graph sources sorted by name, for a deterministic default order.
     *
     * @param models the graph sources.
     * @return a new list, sorted by name.
     */
    public static List<GraphSource> sortByName(GraphSource[] models) {
        List<GraphSource> sources = new ArrayList<>(Arrays.asList(models));
        sources.sort(Comparator.comparing(GraphSource::getName));
        return sources;
    }

    /**
     * Picks the reference (true) graph source from the given sources. Preference order: the source named by the
     * "referenceGraphName" parameter; else a source whose name starts with "Simulation"; else the first source.
     * The chosen name is written back to the "referenceGraphName" parameter so that editors reading the parameter
     * see the name actually used.
     *
     * @param sources the graph sources, nonempty.
     * @param params  the parameters.
     * @return the chosen reference source.
     */
    public static GraphSource resolveReference(List<GraphSource> sources, Parameters params) {
        String referenceName = params.getString("referenceGraphName", null);

        if (referenceName != null) {
            for (GraphSource source : sources) {
                if (referenceName.equals(source.getName())) {
                    return source;
                }
            }
        }

        for (GraphSource source : sources) {
            if (source.getName() != null && source.getName().startsWith("Simulation")) {
                TetradLogger.getInstance().log("Reference graph name '" + referenceName
                                               + "' did not match an input; using " + source.getName() + ".");
                params.set("referenceGraphName", source.getName());
                return source;
            }
        }

        GraphSource source = sources.getFirst();
        TetradLogger.getInstance().log("Reference graph name '" + referenceName
                                       + "' did not match an input; using " + source.getName() + ".");
        params.set("referenceGraphName", source.getName());
        return source;
    }

    /**
     * Returns the non-reference sources in display order: the order given by the "targetGraphNames" parameter where
     * available, with any sources not mentioned there following in name order. The first target's name is written
     * back to the "targetGraphName" parameter for compatibility with the single-target code paths.
     *
     * @param sources   the graph sources, sorted by name.
     * @param reference the reference source, one of the sources.
     * @param params    the parameters.
     * @return the targets, in display order; may be empty.
     */
    public static List<GraphSource> orderTargets(List<GraphSource> sources, GraphSource reference, Parameters params) {
        List<GraphSource> targets = new ArrayList<>();

        for (GraphSource source : sources) {
            if (source != reference) {
                targets.add(source);
            }
        }

        if (params.getNumValues("targetGraphNames") > 0) {
            Object[] values = params.getValues("targetGraphNames");
            List<String> order = new ArrayList<>();

            for (Object value : values) {
                if (value != null) {
                    order.add(value.toString());
                }
            }

            targets.sort(Comparator.comparingInt(source -> {
                int index = order.indexOf(source.getName());
                return index == -1 ? Integer.MAX_VALUE : index;
            }));
        }

        if (!targets.isEmpty()) {
            params.set("targetGraphName", targets.getFirst().getName());
        }

        return targets;
    }
}
