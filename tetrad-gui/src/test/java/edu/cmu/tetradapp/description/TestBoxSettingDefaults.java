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

package edu.cmu.tetradapp.description;

import edu.cmu.tetrad.data.JoinDatasets;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.CovarianceEstimateDataWrapper;
import edu.cmu.tetradapp.model.ImputeMissingDataWrapper;
import edu.cmu.tetradapp.model.VertexCheckIndTestModel;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The settings of the data-handling, estimator and checking boxes that have a registered default, so that a
 * session description can say "changed from the default" about them.
 * <p>
 * A box reads such a setting as {@code params.getInt("thinRowsK", 2)}, giving its own default at the place it
 * reads it. The registered default has to be the same value, or a description would call a setting changed
 * when the box is using its default, or leave out one that is not. These tests hold the two together.
 *
 * @author Claude (Anthropic)
 */
public class TestBoxSettingDefaults {

    /**
     * The box settings registered for session descriptions.
     */
    private static final List<String> NAMES = List.of(
            "a", "base", "bayesEstimatorCellPrior", "concatAddSourceColumn", "concatSourceColumnName",
            "covarianceEstimateCeiling", "covarianceEstimateEssMode", "covarianceEstimateMethod", "dataShuffled",
            "fractionSampleParam", "imputationMethod", "imputationSeed", "joinAllowOneToMany", "joinLeftSuffix",
            "joinRightSuffix", "joinType", "latentClusterRunnerAlgorithm", "latentClusterRunnerTest",
            "lowerBoundNumVals", "miceNumDonors", "miceNumSweeps", "miceRowsPerPredictor",
            "missingnessIndicatorDiscrete", "missingnessIndicatorForbidSelfMasking",
            "missingnessIndicatorKnowledge", "missingnessIndicatorMaxRate", "missingnessIndicatorMinRate",
            "missingnessRowPropensity", "numImputations", "numRestarts", "numSplits", "numTimeLags", "prob",
            "semOptimizerType", "symmetricAlpha", "thinRowsK", "thinRowsOffset", "tolerance",
            "uniformityTestParam", "unlog", "upperBoundNumVals");

    /**
     * A read of a setting with its default given as a plain literal: a number, true or false, or quoted text.
     * The setting is named either in quotes or by a constant, as in {@code JoinDatasetsWrapper.LEFT_SUFFIX}.
     */
    private static final Pattern READ = Pattern.compile(
            "get(Int|Long|Double|Boolean|String)\\(\\s*(\"[A-Za-z0-9_.]+\"|[A-Za-z][A-Za-z0-9_.]*)\\s*,\\s*"
            + "(-?[0-9][0-9.eE+-]*[LlDdFf]?|true|false|\"[^\"]*\")\\s*\\)");

    /**
     * A constant that names a setting.
     */
    private static final Pattern CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*\"([A-Za-z0-9_.]+)\"\\s*;");

    /**
     * The setting a read names: the quoted text, or the value of the constant, looked up in the class the read
     * qualifies it with, or in the class the read is in when it is not qualified. Null if it cannot be told.
     */
    private static String settingName(String token, String className, Map<String, Map<String, String>> constants) {
        if (token.startsWith("\"")) return token.substring(1, token.length() - 1);

        int dot = token.lastIndexOf('.');
        String constant = token.substring(dot + 1);
        String owner = className;

        if (dot >= 0) {
            String qualifier = token.substring(0, dot);
            owner = qualifier.substring(qualifier.lastIndexOf('.') + 1);
        }

        return constants.getOrDefault(owner, Map.of()).get(constant);
    }

    private static String className(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    private static Object literal(String text) {
        if (text.startsWith("\"")) return text.substring(1, text.length() - 1);
        if (text.equals("true") || text.equals("false")) return Boolean.valueOf(text);
        return Double.valueOf(text.replaceAll("[LlDdFf]$", ""));
    }

    private static Object comparable(Object value) {
        return value instanceof Number number ? (Object) number.doubleValue() : value;
    }

    @Test
    public void testEachSettingIsRegistered() {
        ParamDescriptions registry = ParamDescriptions.getInstance();

        for (String name : NAMES) {
            assertTrue(name + " is not registered.", registry.getNames().contains(name));
        }
    }

    /**
     * Wherever the interface's own code reads one of these settings with a literal default, that default is the
     * registered one.
     */
    @Test
    public void testRegisteredDefaultsAreTheOnesTheBoxesUse() throws IOException {
        Path sources = Paths.get("src", "main", "java");
        Assume.assumeTrue("Run from the tetrad-gui directory to check the sources.", Files.isDirectory(sources));

        ParamDescriptions registry = ParamDescriptions.getInstance();
        List<String> differences = new ArrayList<>();
        List<String> seen = new ArrayList<>();

        List<Path> javaFiles;
        try (Stream<Path> files = Files.walk(sources)) {
            javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
        }

        Map<String, Map<String, String>> constants = new HashMap<>();
        for (Path file : javaFiles) {
            Matcher matcher = CONSTANT.matcher(Files.readString(file, StandardCharsets.UTF_8));

            while (matcher.find()) {
                constants.computeIfAbsent(className(file), k -> new HashMap<>())
                        .put(matcher.group(1), matcher.group(2));
            }
        }

        for (Path file : javaFiles) {
            Matcher matcher = READ.matcher(Files.readString(file, StandardCharsets.UTF_8));

            while (matcher.find()) {
                String name = settingName(matcher.group(2), className(file), constants);
                if (name == null || !NAMES.contains(name)) continue;
                seen.add(name);

                Object used = comparable(literal(matcher.group(3)));
                Object registered = comparable(registry.get(name).getDefaultValue());

                if (!used.equals(registered)) {
                    differences.add(name + ": " + file.getFileName() + " uses " + matcher.group(3)
                                    + ", registered default is " + registry.get(name).getDefaultValue());
                }
            }
        }

        assertEquals("Registered defaults that differ from the ones the boxes use.", List.of(), differences);

        // The four settings below are only ever read with a constant for the default; they are checked in
        // testDefaultsGivenAsConstants. Every other one must have been met at least once, or this test is not
        // looking at what it thinks it is.
        List<String> readWithAConstant = List.of("imputationMethod", "joinType", "covarianceEstimateMethod",
                "uniformityTestParam");

        for (String name : NAMES) {
            if (readWithAConstant.contains(name)) continue;
            assertTrue("No read of " + name + " with a literal default was found.", seen.contains(name));
        }
    }

    @Test
    public void testDefaultsGivenAsConstants() {
        ParamDescriptions registry = ParamDescriptions.getInstance();

        assertEquals(ImputeMissingDataWrapper.Method.AUTO.name(),
                registry.get("imputationMethod").getDefaultValue());
        assertEquals(CovarianceEstimateDataWrapper.Method.EM.name(),
                registry.get("covarianceEstimateMethod").getDefaultValue());
        assertEquals(JoinDatasets.JoinType.LEFT.name(), registry.get("joinType").getDefaultValue());
        assertEquals(VertexCheckIndTestModel.KOLMOGOROV_SMIRNOFF,
                registry.get("uniformityTestParam").getDefaultValue());
    }

    /**
     * The stored kind of value matters as well as the value: a description that is read back makes a value of
     * the registered kind.
     */
    @Test
    public void testKindsOfValue() {
        ParamDescriptions registry = ParamDescriptions.getInstance();

        assertEquals(Long.class, registry.get("imputationSeed").getDefaultValue().getClass());
        assertEquals(Integer.class, registry.get("numTimeLags").getDefaultValue().getClass());
        assertEquals(Double.class, registry.get("prob").getDefaultValue().getClass());
        assertEquals(Boolean.class, registry.get("unlog").getDefaultValue().getClass());
        assertEquals(String.class, registry.get("joinType").getDefaultValue().getClass());

        for (String name : NAMES) {
            ParamDescription description = registry.get(name);
            assertFalse(name + " has no short description.", description.getShortDescription().isBlank());
            assertFalse(name + " has no long description.",
                    description.getLongDescription().startsWith("Missing long description"));
        }
    }

    /**
     * The log transform box reads its three settings without giving a default, so what it gets when a setting
     * has not been stored is the registered default. These are the values its editor starts from.
     */
    @Test
    public void testLogTransformReadsWithoutADefault() {
        Parameters parameters = new Parameters();

        assertEquals(10.0, parameters.getDouble("a"), 0.0);
        assertEquals(0, parameters.getInt("base"));
        assertFalse(parameters.getBoolean("unlog"));
    }
}
