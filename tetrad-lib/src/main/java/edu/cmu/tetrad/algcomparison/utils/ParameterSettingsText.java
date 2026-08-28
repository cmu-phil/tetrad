///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.Set;

/**
 * Renders the effective parameter settings of a configured algorithm as plain text suitable
 * for copying into an email, a lab notebook, or a script - one {@code name = value} line per
 * parameter, grouped into the same sections the parameter panel displays (algorithm, score,
 * test, bootstrapping), with section titles taken from the annotations. Values are the
 * effective ones: the value stored in the {@link Parameters} object if the user set one,
 * otherwise the registered default. This is the reproducibility companion to the GUI
 * parameter panel: what it prints is what the run will use.
 *
 * <p>Algorithms with hard-coded parameter groupings in the panel (e.g. RFCI-BSC) are rendered
 * through the same generic sections here; the set of parameters is the same, only the
 * grouping differs.
 */
public final class ParameterSettingsText {

    private ParameterSettingsText() {
    }

    /**
     * Renders the settings for the given configured algorithm.
     *
     * @param algorithm           the configured algorithm (with score/test wrappers attached).
     * @param parameters          the parameters object backing the panel.
     * @param sourceGraphSupplied whether a source graph is supplied (in which case
     *                            bootstrapping is unavailable and noted as such).
     * @return the settings as plain text.
     */
    public static String render(Algorithm algorithm, Parameters parameters, boolean sourceGraphSupplied) {
        StringBuilder sb = new StringBuilder();

        Set<String> algParams = Params.getAlgorithmParameters(algorithm);
        appendSection(sb, algorithmName(algorithm), algParams, parameters);

        Set<String> scoreParams = Params.getScoreParameters(algorithm);
        if (!scoreParams.isEmpty()) {
            appendSection(sb, scoreName(algorithm), scoreParams, parameters);
        }

        Set<String> testParams = Params.getTestParameters(algorithm);
        if (!testParams.isEmpty()) {
            appendSection(sb, testName(algorithm), testParams, parameters);
        }

        if (sourceGraphSupplied) {
            sb.append("Bootstrapping\n  (unavailable: a source graph is supplied)\n");
        } else if (algorithm instanceof AbstractBootstrapAlgorithm) {
            Set<String> bootParams = Params.getBootstrappingParameters(algorithm);
            if (!bootParams.isEmpty()) {
                appendSection(sb, "Bootstrapping", bootParams, parameters);
            }
        }

        return sb.toString();
    }

    /**
     * Renders a single titled section of parameter settings as plain text, in the same format
     * as the algorithm rendering: the title on its own line, then one "  name = value" line per
     * parameter, in the iteration order of the given collection. Used by the simulation editor's
     * "Settings as Text..." button.
     *
     * @param title      the section title (e.g., the simulation and graph type).
     * @param params     the parameter names, in display order.
     * @param parameters the parameter values.
     * @return the rendered text.
     */
    public static String render(String title, java.util.Collection<String> params,
                                Parameters parameters) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append('\n');
        ParamDescriptions descriptions = ParamDescriptions.getInstance();
        for (String param : params) {
            ParamDescription description = descriptions.get(param);
            Object defaultValue = (description == null) ? null : description.getDefaultValue();
            Object value = parameters.get(param, defaultValue);
            sb.append("  ").append(param).append(" = ").append(value).append('\n');
        }
        return sb.toString();
    }

    /**
     * The result of applying pasted settings text: the section titles encountered, the names of
     * parameters whose values were applied, and the lines that were skipped (with reasons).
     */
    public static final class ApplyResult {
        /**
         * The section titles encountered, in order (lines without an equals sign).
         */
        public final java.util.List<String> titles = new java.util.ArrayList<>();

        /**
         * The names of parameters whose values were applied, in order.
         */
        public final java.util.List<String> applied = new java.util.ArrayList<>();

        /**
         * Skipped lines with reasons, e.g., "foo (unknown parameter)".
         */
        public final java.util.List<String> skipped = new java.util.ArrayList<>();

        /**
         * Constructs an empty result.
         */
        public ApplyResult() {
        }
    }

    /**
     * Parses settings text in the format produced by the render methods ("  name = value" lines
     * under title lines) and applies the parameter values to the given Parameters object. Lines
     * without an equals sign, or whose name part contains spaces, are treated as section titles
     * and collected. Parameter values are parsed according to the type of the parameter's
     * default value in the manual (Boolean, Integer, Long, Double, or String); parameters not
     * documented in the manual, and values that fail to parse, are skipped and reported. This
     * is deliberately conservative: nothing is applied for a line unless the parameter is known
     * and the value parses cleanly.
     *
     * @param text       the pasted settings text.
     * @param parameters the Parameters object to apply values to.
     * @return the result, listing titles, applied parameters, and skipped lines.
     */
    public static ApplyResult applySettingsText(String text, Parameters parameters) {
        ApplyResult result = new ApplyResult();
        if (text == null) return result;

        ParamDescriptions descriptions = ParamDescriptions.getInstance();
        // Known = documented in the manual. (ParamDescriptions.get() silently fabricates a
        // placeholder for unknown names, so containment must be checked against getNames().)
        Set<String> known = descriptions.getNames();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            int eq = line.indexOf('=');
            String name = eq > 0 ? line.substring(0, eq).trim() : "";

            if (eq <= 0 || name.isEmpty() || name.contains(" ")) {
                result.titles.add(line);
                continue;
            }

            String valueText = line.substring(eq + 1).trim();

            if (!known.contains(name)) {
                result.skipped.add(name + " (unknown parameter)");
                continue;
            }

            ParamDescription description = descriptions.get(name);
            Object defaultValue = (description == null) ? null : description.getDefaultValue();

            try {
                if (defaultValue instanceof Boolean) {
                    if (!valueText.equalsIgnoreCase("true") && !valueText.equalsIgnoreCase("false")) {
                        throw new NumberFormatException(valueText);
                    }
                    parameters.set(name, Boolean.parseBoolean(valueText));
                } else if (defaultValue instanceof Integer) {
                    parameters.set(name, Integer.parseInt(valueText));
                } else if (defaultValue instanceof Long) {
                    parameters.set(name, Long.parseLong(valueText));
                } else if (defaultValue instanceof Double) {
                    parameters.set(name, Double.parseDouble(valueText));
                } else {
                    parameters.set(name, valueText);
                }
                result.applied.add(name);
            } catch (NumberFormatException e) {
                result.skipped.add(name + " (could not parse value \"" + valueText + "\")");
            }
        }

        return result;
    }

    private static void appendSection(StringBuilder sb, String title, Set<String> params,
                                      Parameters parameters) {
        if (params.isEmpty()) return;
        if (sb.length() > 0) sb.append('\n');
        sb.append(title).append('\n');
        ParamDescriptions descriptions = ParamDescriptions.getInstance();
        for (String param : params) {
            ParamDescription description = descriptions.get(param);
            Object defaultValue = (description == null) ? null : description.getDefaultValue();
            Object value = parameters.get(param, defaultValue);
            sb.append("  ").append(param).append(" = ").append(value).append('\n');
        }
    }

    private static String algorithmName(Algorithm algorithm) {
        edu.cmu.tetrad.annotation.Algorithm annotation =
                algorithm.getClass().getAnnotation(edu.cmu.tetrad.annotation.Algorithm.class);
        return (annotation == null) ? algorithm.getClass().getSimpleName() : annotation.name();
    }

    private static String scoreName(Algorithm algorithm) {
        if (algorithm instanceof TakesScoreWrapper) {
            Object wrapper = ((TakesScoreWrapper) algorithm).getScoreWrapper();
            if (wrapper != null) {
                Score annotation = wrapper.getClass().getAnnotation(Score.class);
                if (annotation != null) return annotation.name();
                return wrapper.getClass().getSimpleName();
            }
        }
        return "Score";
    }

    private static String testName(Algorithm algorithm) {
        if (algorithm instanceof TakesIndependenceWrapper) {
            Object wrapper = ((TakesIndependenceWrapper) algorithm).getIndependenceWrapper();
            if (wrapper != null) {
                TestOfIndependence annotation = wrapper.getClass().getAnnotation(TestOfIndependence.class);
                if (annotation != null) return annotation.name();
                return wrapper.getClass().getSimpleName();
            }
        }
        return "Test";
    }
}
