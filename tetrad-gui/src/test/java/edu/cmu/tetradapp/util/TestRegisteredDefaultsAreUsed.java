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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Params;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A registered parameter has one default, the one given in the parameter definitions. Code can read a parameter and
 * give a default of its own at the place it reads it, as in {@code parameters.getDouble("alpha", 0.001)}, and where
 * that default is not the registered one, the same parameter left alone means different things in different places:
 * the search panel of the interface fills in the registered default, a caller with an empty set of parameters gets
 * the other, and a session description calls the registered one "the default".
 * <p>
 * This test reads the sources of tetrad-lib and tetrad-gui and fails if any read of a registered parameter gives a
 * default that differs from the registered one. The cure is to read it without a default,
 * {@code parameters.getDouble("alpha")}, which takes the registered one; or, if the registered default is the one
 * that is wrong, to correct the definition.
 * <p>
 * A read can be let through for a stated reason by naming it in {@link #AWAITING_A_DECISION}. The test also fails if
 * a read named there no longer differs, so that the list does not outlive its reasons. Nothing is let through at
 * present.
 *
 * @author Claude (Anthropic)
 */
public class TestRegisteredDefaultsAreUsed {

    /**
     * Reads let through for now, as "File.java: parameter", each with why. There are none.
     */
    private static final Map<String, String> AWAITING_A_DECISION = new TreeMap<>(Map.of());

    /**
     * A read of a setting with a default: the receiver, the getter, the name as quoted text or a constant, and the
     * default as a literal or a name.
     */
    private static final Pattern READ = Pattern.compile(
            "([A-Za-z0-9_.()]*)\\.get(Int|Long|Double|Boolean|String)\\(\\s*(\"[^\"]+\"|[A-Za-z][A-Za-z0-9_.]*)\\s*,"
            + "\\s*(\"[^\"]*\"|[^(),;\"]+?)\\s*\\)");

    private static final Pattern STRING_CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*\"([^\"]+)\"\\s*;");

    /**
     * The value of a registered default, or of a default given in code, in a form two of them can be compared in:
     * a number as a double, a truth value or text as itself. Null if the text is not a literal.
     */
    private static Object value(String text) {
        String t = text.strip();

        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) return t.substring(1, t.length() - 1);
        if (t.equals("true") || t.equals("false")) return Boolean.valueOf(t);

        try {
            return Double.valueOf(t.replace("_", "").replaceAll("[LlDdFf]$", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object value(Object registered) {
        return registered instanceof Number number ? (Object) number.doubleValue() : registered;
    }

    /**
     * The names of the parameters the constants of {@link Params} stand for.
     */
    private static Map<String, String> paramsConstants() throws IllegalAccessException {
        Map<String, String> constants = new HashMap<>();

        for (Field field : Params.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                constants.put(field.getName(), (String) field.get(null));
            }
        }

        return constants;
    }

    @Test
    public void testNoReadGivesADefaultOtherThanTheRegisteredOne() throws IOException, IllegalAccessException {
        Path gui = Paths.get("src", "main", "java");
        Path lib = Paths.get("..", "tetrad-lib", "src", "main", "java");
        Assume.assumeTrue("Run from the tetrad-gui directory, beside tetrad-lib, to check the sources.",
                Files.isDirectory(gui) && Files.isDirectory(lib));

        ParamDescriptions registry = ParamDescriptions.getInstance();
        Map<String, String> paramsConstants = paramsConstants();
        Map<String, String> differences = new TreeMap<>();
        int reads = 0;

        List<Path> files = new ArrayList<>();
        for (Path root : List.of(gui, lib)) {
            try (Stream<Path> walk = Files.walk(root)) {
                files.addAll(walk.filter(p -> p.toString().endsWith(".java")).toList());
            }
        }

        for (Path file : files) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, String> localConstants = new HashMap<>();
            Matcher constant = STRING_CONSTANT.matcher(source);

            while (constant.find()) {
                localConstants.put(constant.group(1), constant.group(2));
            }

            Matcher read = READ.matcher(source);

            while (read.find()) {
                String receiver = read.group(1);

                // The interface's own remembered preferences have getters of the same shape; they are not
                // parameters.
                if (receiver.contains("Preferences") || receiver.toLowerCase().contains("prefs")
                    || receiver.contains("userRoot") || receiver.contains("userNodeForPackage")) {
                    continue;
                }

                String token = read.group(3);
                String name;

                if (token.startsWith("\"")) {
                    name = token.substring(1, token.length() - 1);
                } else if (token.startsWith("Params.")) {
                    name = paramsConstants.get(token.substring("Params.".length()));
                } else {
                    name = localConstants.get(token);
                }

                if (name == null || !registry.getNames().contains(name)) {
                    continue;
                }

                String given = read.group(4).strip();

                // A default given by name: a constant or a local of this file with a literal value.
                if (given.matches("[A-Za-z_][A-Za-z0-9_]*") && value(given) == null) {
                    Matcher definition = Pattern.compile("\\b(?:int|long|double|boolean|String)\\s+"
                                                         + Pattern.quote(given) + "\\s*=\\s*([^;]+);").matcher(source);
                    if (definition.find()) {
                        given = definition.group(1).strip();
                    }
                }

                Object used = value(given);

                if (used == null) {
                    continue;
                }

                reads++;
                Object registered = value(registry.get(name).getDefaultValue());

                if (!used.equals(registered)) {
                    differences.put(file.getFileName() + ": " + name,
                            "read with " + given + ", registered default " + registry.get(name).getDefaultValue());
                }
            }
        }

        assertTrue("Too few reads were found for this test to be looking at what it thinks it is: " + reads,
                reads > 150);

        Map<String, String> unexpected = new TreeMap<>(differences);
        unexpected.keySet().removeAll(AWAITING_A_DECISION.keySet());
        assertEquals("Reads of a registered parameter that give a default other than the registered one. Read the "
                     + "parameter without a default, or correct the parameter definition.", Map.of(), unexpected);

        List<String> settled = new ArrayList<>(AWAITING_A_DECISION.keySet());
        settled.removeAll(differences.keySet());
        assertEquals("These reads no longer differ from the registered default; take them off the list.", List.of(),
                settled);
    }
}
