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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appends missingness indicators to a dataset. For each selected variable X, an indicator X_missing is added that
 * records, for every case, whether X was missing in that case. The original columns are left exactly as they are, so
 * the substantive variables still carry their missing values and are still handled by whatever missing-data policy
 * the score or test is using.
 *
 * <p>The point of doing this is that the missingness mechanism becomes part of the graph rather than a nuisance the
 * score has to work around. Edges into an indicator say what the mechanism depends on; edges among indicators say how
 * the losses are coupled across variables. The one thing this cannot recover is self-masking, the edge from X to its
 * own indicator, because X is observed on exactly the cases where the indicator says "observed" and is therefore
 * constant on every case the score can use. That edge is unscorable, not merely hard, and by default it is forbidden
 * in the knowledge this class attaches.</p>
 *
 * <p>Two conventions matter and are easy to get backwards. The indicator is 1, or category "missing", when the value
 * is <em>absent</em>; this matches the name. And an indicator is only worth adding when it actually varies: a
 * variable with no missing values, or with nothing but missing values, yields a constant column that no score can use
 * and that will make some scores singular. The rate window in the spec is what keeps those out.</p>
 *
 * <p>Indicators are discrete by default, which requires a score that accepts mixed data, such as the degenerate
 * Gaussian or conditional Gaussian score. Setting {@code discreteIndicators} to false emits them as continuous 0/1
 * columns so that a purely continuous score can be used; that is a linear-probability approximation to a binary
 * variable and should be read as such.</p>
 *
 * @author josephramsey
 */
public final class MissingnessIndicatorAdder {

    /**
     * Utility class; not instantiable.
     */
    private MissingnessIndicatorAdder() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Adds missingness indicators using the default spec.
     *
     * @param dataSet The original dataset.
     * @return A new dataset with indicators appended.
     */
    public static DataSet addMissingnessIndicators(DataSet dataSet) {
        return add(dataSet, Spec.defaults()).data();
    }

    /**
     * Adds missingness indicators under the given spec.
     *
     * @param dataSet The original dataset. Not modified.
     * @param spec    The options to use.
     * @return The augmented dataset plus a record of what was added and what was skipped.
     */
    public static Result add(DataSet dataSet, Spec spec) {
        if (dataSet == null) {
            throw new IllegalArgumentException("Dataset must not be null.");
        }
        if (spec == null) {
            throw new IllegalArgumentException("Spec must not be null.");
        }

        int numRows = dataSet.getNumRows();
        int numCols = dataSet.getNumColumns();

        if (numRows == 0) {
            throw new IllegalArgumentException("Dataset has no rows.");
        }

        List<Node> originalVariables = dataSet.getVariables();

        // Missingness pattern of the input, computed once.
        boolean[][] missing = new boolean[numCols][numRows];
        int[] missingCounts = new int[numCols];

        for (int j = 0; j < numCols; j++) {
            for (int i = 0; i < numRows; i++) {
                boolean m = MissingDataAudit.isMissing(dataSet, i, j);
                missing[j][i] = m;
                if (m) missingCounts[j]++;
            }
        }

        Map<String, Double> rates = new LinkedHashMap<>();
        List<Integer> selected = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (int j = 0; j < numCols; j++) {
            String name = originalVariables.get(j).getName();
            double rate = missingCounts[j] / (double) numRows;
            rates.put(name, rate);

            if (missingCounts[j] == 0) {
                continue; // Nothing missing; skip quietly, since an indicator would be constant.
            }

            if (missingCounts[j] == numRows) {
                skipped.add(name + " (entirely missing; indicator would be constant)");
                continue;
            }

            if (rate < spec.minRate() || rate > spec.maxRate()) {
                skipped.add(String.format("%s (rate %.3f outside [%.3f, %.3f])",
                        name, rate, spec.minRate(), spec.maxRate()));
                continue;
            }

            selected.add(j);
        }

        // Names, made unique against the original variables and against each other.
        Set<String> used = new HashSet<>();
        for (Node node : originalVariables) used.add(node.getName());

        List<String> indicatorNames = new ArrayList<>();
        List<Node> newVariables = new ArrayList<>(originalVariables);

        for (int j : selected) {
            String base = originalVariables.get(j).getName() + spec.suffix();
            String name = base;
            int k = 2;
            while (used.contains(name)) name = base + "_" + k++;
            used.add(name);
            indicatorNames.add(name);

            // Category 0 is "observed" and category 1 is "missing", so the value matches the name.
            newVariables.add(spec.discreteIndicators()
                    ? new DiscreteVariable(name, List.of("observed", "missing"))
                    : new ContinuousVariable(name));
        }

        DataSet out = new BoxDataSet(new MixedDataBox(newVariables, numRows), newVariables);

        // Copy the original columns verbatim, using the right accessor for each type.
        for (int j = 0; j < numCols; j++) {
            if (originalVariables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < numRows; i++) {
                    out.setInt(i, j, dataSet.getInt(i, j));
                }
            } else {
                for (int i = 0; i < numRows; i++) {
                    out.setDouble(i, j, dataSet.getDouble(i, j));
                }
            }
        }

        // Append the indicators.
        for (int c = 0; c < selected.size(); c++) {
            int source = selected.get(c);
            int target = numCols + c;

            for (int i = 0; i < numRows; i++) {
                int value = missing[source][i] ? 1 : 0;

                if (spec.discreteIndicators()) {
                    out.setInt(i, target, value);
                } else {
                    out.setDouble(i, target, value);
                }
            }
        }

        if (spec.attachKnowledge() && !indicatorNames.isEmpty()) {
            Knowledge knowledge = new Knowledge();

            for (Node node : originalVariables) {
                knowledge.addToTier(0, node.getName());
            }

            for (String name : indicatorNames) {
                knowledge.addToTier(1, name);
            }

            if (spec.forbidSelfMasking()) {
                for (int c = 0; c < selected.size(); c++) {
                    knowledge.setForbidden(originalVariables.get(selected.get(c)).getName(),
                            indicatorNames.get(c));
                }
            }

            out.setKnowledge(knowledge);
        }

        return new Result(out, indicatorNames, rates, skipped);
    }

    /**
     * Options controlling which indicators are added and what is attached to the result.
     *
     * @param minRate            Minimum missingness rate for a variable to get an indicator.
     * @param maxRate            Maximum missingness rate for a variable to get an indicator.
     * @param discreteIndicators If true, indicators are binary discrete variables with categories "observed" and
     *                           "missing"; if false, continuous 0/1 columns.
     * @param attachKnowledge    If true, knowledge is attached to the result placing the substantive variables in tier
     *                           0 and the indicators in tier 1, so that no indicator can be a cause of a substantive
     *                           variable.
     * @param forbidSelfMasking  If true, and knowledge is attached, the edge from each variable to its own indicator
     *                           is forbidden, since it cannot be scored from the observed cases.
     * @param suffix             Suffix appended to the variable name to form the indicator name.
     */
    public record Spec(double minRate, double maxRate, boolean discreteIndicators, boolean attachKnowledge,
                       boolean forbidSelfMasking, String suffix) {

        /**
         * Canonical constructor, with validation.
         */
        public Spec {
            if (!(minRate >= 0.0) || !(minRate <= 1.0)) {
                throw new IllegalArgumentException("minRate must be in [0, 1]: " + minRate);
            }
            if (!(maxRate >= 0.0) || !(maxRate <= 1.0)) {
                throw new IllegalArgumentException("maxRate must be in [0, 1]: " + maxRate);
            }
            if (minRate > maxRate) {
                throw new IllegalArgumentException("minRate must not exceed maxRate.");
            }
            if (suffix == null || suffix.isBlank()) {
                throw new IllegalArgumentException("Suffix must be a non-blank string.");
            }
        }

        /**
         * Returns the default spec: discrete indicators for every variable whose missingness rate lies between 0.02
         * and 0.98, with tier knowledge attached and self-masking forbidden.
         *
         * @return the default spec.
         */
        public static Spec defaults() {
            return new Spec(0.02, 0.98, true, true, true, "_missing");
        }

        /**
         * Returns a copy of this spec with the given rate window.
         *
         * @param minRate the minimum rate.
         * @param maxRate the maximum rate.
         * @return the modified copy.
         */
        public Spec withRateWindow(double minRate, double maxRate) {
            return new Spec(minRate, maxRate, discreteIndicators, attachKnowledge, forbidSelfMasking, suffix);
        }

        /**
         * Returns a copy of this spec with the given indicator type.
         *
         * @param discrete true for discrete indicators, false for continuous 0/1 columns.
         * @return the modified copy.
         */
        public Spec withDiscreteIndicators(boolean discrete) {
            return new Spec(minRate, maxRate, discrete, attachKnowledge, forbidSelfMasking, suffix);
        }

        /**
         * Returns a copy of this spec with the given knowledge settings.
         *
         * @param attach            whether to attach tier knowledge.
         * @param forbidSelfMasking whether to forbid each variable to cause its own indicator.
         * @return the modified copy.
         */
        public Spec withKnowledge(boolean attach, boolean forbidSelfMasking) {
            return new Spec(minRate, maxRate, discreteIndicators, attach, forbidSelfMasking, suffix);
        }

        /**
         * Returns a copy of this spec with the given indicator name suffix.
         *
         * @param suffix the suffix.
         * @return the modified copy.
         */
        public Spec withSuffix(String suffix) {
            return new Spec(minRate, maxRate, discreteIndicators, attachKnowledge, forbidSelfMasking, suffix);
        }
    }

    /**
     * The augmented dataset together with a record of what was done, for logging and for the session box to display.
     *
     * @param data           The augmented dataset, with knowledge attached if the spec asked for it.
     * @param indicatorNames The names of the indicators added, in column order.
     * @param rates          The missingness rate of every variable in the input, in column order.
     * @param skipped        Human-readable reasons for variables that did not get an indicator.
     */
    public record Result(DataSet data, List<String> indicatorNames, Map<String, Double> rates, List<String> skipped) {

        /**
         * Returns a short summary suitable for a log entry.
         *
         * @return the summary.
         */
        public String summary() {
            StringBuilder b = new StringBuilder();
            b.append("Added ").append(indicatorNames.size()).append(" missingness indicator(s).");

            if (!indicatorNames.isEmpty()) {
                b.append("\n  Added: ").append(String.join(", ", indicatorNames));
            }

            if (!skipped.isEmpty()) {
                b.append("\n  Skipped: ").append(String.join("; ", skipped));
            }

            return b.toString();
        }
    }
}
