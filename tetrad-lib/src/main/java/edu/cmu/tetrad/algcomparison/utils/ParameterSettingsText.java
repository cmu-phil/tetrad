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
