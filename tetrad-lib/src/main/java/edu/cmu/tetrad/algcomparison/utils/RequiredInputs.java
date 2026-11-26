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
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for the inputs that some algorithms cannot run without, beyond data: target variables, a graph to work on, or
 * clusters of measured variables. Each check either returns the input ready to use or throws an
 * IllegalArgumentException that says which algorithm needs what, where to supply it, and, for target names, which
 * names were not found and what the data's variables are.
 * <p>
 * These replace per-algorithm checks that disagreed with one another and, in several cases, were missing, so that a
 * forgotten input surfaced as a null-pointer or array-index error from deep inside the search.
 *
 * @author Claude
 */
public final class RequiredInputs {

    /**
     * The most variable names listed in a message.
     */
    private static final int MAX_NAMES_LISTED = 12;

    private RequiredInputs() {
    }

    /**
     * Turns the value of the "targets" parameter into the list of target variables.
     * <p>
     * Names are separated by commas, or, when the value contains no comma, by spaces. Spaces around a name are
     * dropped, so "X1, X2" names the same two variables as "X1,X2" and "X1 X2". Names are matched exactly, including
     * case. A name given twice is used once.
     *
     * @param algorithm   the name of the algorithm, for the message
     * @param targetNames the value of the "targets" parameter; may be null or blank, which is reported as no targets
     * @param variables   the variables of the data, or of the test or score, being searched over
     * @return the target variables, in the order given; never empty
     * @throws IllegalArgumentException if no target is given, or if a name is not the name of one of the variables
     */
    public static List<Node> targets(String algorithm, String targetNames, List<Node> variables) {
        Map<String, Node> byName = new LinkedHashMap<>();
        for (Node variable : variables) {
            byName.putIfAbsent(variable.getName(), variable);
        }

        String given = targetNames == null ? "" : targetNames.trim();
        String[] names = given.contains(",") ? given.split(",") : given.split("\\s+");

        Set<Node> targets = new LinkedHashSet<>();
        Set<String> unknown = new LinkedHashSet<>();

        for (String name : names) {
            name = name.trim();
            if (name.isEmpty()) continue;

            Node variable = byName.get(name);
            if (variable == null) {
                unknown.add("'" + name + "'");
            } else {
                targets.add(variable);
            }
        }

        String where = "the 'Target names' parameter (" + Params.TARGETS + ")";
        String available = "The variables in this data are: " + listed(new ArrayList<>(byName.keySet())) + ".";

        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(algorithm + " was given "
                    + (unknown.size() == 1 ? "a target that is not a variable" : "targets that are not variables")
                    + " in the data: " + String.join(", ", unknown) + ".\n"
                    + "Check " + where + ". Names must match exactly, including upper and lower case, and are"
                    + " separated by commas or spaces.\n" + available);
        }

        if (targets.isEmpty()) {
            throw new IllegalArgumentException(algorithm + " needs at least one target variable, and none was given.\n"
                    + "Set " + where + " to one or more variable names from the data, separated by commas or"
                    + " spaces.\n" + available);
        }

        return new ArrayList<>(targets);
    }

    /**
     * Returns the graph source of an algorithm that orients or otherwise works on a graph it is given, refusing a
     * missing one.
     *
     * @param algorithm the name of the algorithm, for the message
     * @param source    the algorithm that supplies the graph, or null if none was given
     * @return the source, which is not null
     * @throws IllegalArgumentException if the source is null
     */
    public static Algorithm graphSource(String algorithm, Algorithm source) {
        if (source == null) {
            throw new IllegalArgumentException(graphSourceMessage(algorithm));
        }

        return source;
    }

    /**
     * The message for an algorithm that was not given the graph it needs. Each sentence is on its own line, since the
     * interface shows this text in a dialog that does not wrap lines.
     *
     * @param algorithm the name of the algorithm
     * @return the message
     */
    public static String graphSourceMessage(String algorithm) {
        return algorithm + " needs a graph as well as data: it orients the edges of that graph using the data.\n"
                + "In the interface, connect a Graph box holding the graph to this Search box, along with the Data box.\n"
                + "In causal-cmd, give the file holding the graph with --external-graph.\n"
                + "In code, pass the algorithm that supplies the graph to the constructor or to setExternalGraph.\n"
                + "SingleGraphAlg wraps a graph you already have.";
    }

    /**
     * Returns the clusters of an algorithm that searches for structure among latent variables, refusing missing ones
     * and ones made for another data set.
     *
     * @param algorithm the name of the algorithm, for the message
     * @param blockSpec the clusters, or null if none were given
     * @param dataSet   the data set being searched
     * @return the clusters, which are not null and were made for this data set
     * @throws IllegalArgumentException if the clusters are missing or were made for a different data set
     */
    public static BlockSpec blockSpec(String algorithm, BlockSpec blockSpec, DataSet dataSet) {
        if (blockSpec == null) {
            throw new IllegalArgumentException(blockSpecMessage(algorithm));
        }

        if (!dataSet.equals(blockSpec.dataSet())) {
            throw new IllegalArgumentException("The clusters given to " + algorithm + " were made for a different data"
                    + " set than the one being searched.\n"
                    + "In the interface, connect the same Data box to the Latent Clusters box and to this Search box.\n"
                    + "In code, make the BlockSpec from the data set that is passed to the search.");
        }

        return blockSpec;
    }

    /**
     * The message for an algorithm that was not given the clusters it needs. Each sentence is on its own line, as for
     * {@link #graphSourceMessage(String)}.
     *
     * @param algorithm the name of the algorithm
     * @return the message
     */
    public static String blockSpecMessage(String algorithm) {
        return algorithm + " needs clusters as well as data: which measured variables measure which latent variable.\n"
                + "In the interface, connect a Latent Clusters box to this Search box, along with the Data box.\n"
                + "In code, call setBlockSpec with a BlockSpec made from the same data set.\n"
                + "BlockSpecTextCodec.parse makes one from text.";
    }

    private static String listed(List<String> names) {
        if (names.isEmpty()) {
            return "(none)";
        }

        if (names.size() <= MAX_NAMES_LISTED) {
            return String.join(", ", names);
        }

        return String.join(", ", names.subList(0, MAX_NAMES_LISTED)) + ", and " + (names.size() - MAX_NAMES_LISTED)
                + " more";
    }
}
