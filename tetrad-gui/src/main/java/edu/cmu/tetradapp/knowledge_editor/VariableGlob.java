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

package edu.cmu.tetradapp.knowledge_editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches variable names against a shell-style wildcard expression, for the knowledge editor's
 * pattern fields.
 *
 * <p>The expression is ordinary text in which two characters are special: {@code *} matches any
 * string of characters (including the empty string) and {@code ?} matches exactly one character.
 * Every other character, including regex metacharacters such as {@code .} and {@code (}, is matched
 * literally. So {@code X*} matches {@code X1} and {@code X_lag2}; {@code *age*} matches
 * {@code age} and {@code mean_age_at_entry}; {@code V??} matches {@code V10} but not {@code V1}
 * or {@code V100}.
 *
 * <p>Several alternatives may be given, separated by commas: {@code X*, Y?} matches anything the
 * first alternative matches together with anything the second matches. Whitespace around the
 * alternatives is ignored, and empty alternatives are dropped, so a trailing comma is harmless.
 *
 * <p>Matching is against the whole name, not a substring of it — use {@code *} on both ends for a
 * substring search — and is case sensitive, since variable names in Tetrad are case sensitive.
 *
 * <p>This class deliberately does its own matching rather than going through
 * {@link edu.cmu.tetrad.data.Knowledge}'s internal spec handling, so that callers can resolve a
 * wildcard to a list of names that are known to exist and then pass only real names to the
 * knowledge object.
 *
 * @author josephramsey
 */
final class VariableGlob {

    private VariableGlob() {
    }

    /**
     * Translates a wildcard expression into an equivalent regular expression, returning null if the
     * expression contains no usable alternative (it is empty, or only commas and whitespace).
     */
    static Pattern toPattern(String spec) {
        if (spec == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        boolean any = false;

        for (String part : spec.split(",")) {
            String alternative = part.trim();

            if (alternative.isEmpty()) {
                continue;
            }

            if (any) {
                sb.append('|');
            }

            sb.append(globToRegex(alternative));
            any = true;
        }

        if (!any) {
            return null;
        }

        try {
            return Pattern.compile(sb.toString());
        } catch (PatternSyntaxException e) {
            // Everything non-wildcard is quoted, so this should not happen; failing closed is
            // better than propagating an exception into an action listener.
            return null;
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder out = new StringBuilder("(?:");
        StringBuilder literal = new StringBuilder();

        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);

            if (c == '*' || c == '?') {
                if (!literal.isEmpty()) {
                    out.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }

                out.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }

        if (!literal.isEmpty()) {
            out.append(Pattern.quote(literal.toString()));
        }

        return out.append(')').toString();
    }

    /**
     * Returns those of the given names that the wildcard expression matches, in the order in which
     * they were given. Returns an empty list if the expression is unusable or nothing matches.
     *
     * @param spec  a wildcard expression, as described in the class comment
     * @param names the names to match against
     * @return the matching names, in their original order
     */
    static List<String> match(String spec, List<String> names) {
        Pattern pattern = toPattern(spec);

        if (pattern == null || names == null) {
            return Collections.emptyList();
        }

        List<String> matched = new ArrayList<>();

        for (String name : names) {
            if (name != null && pattern.matcher(name).matches()) {
                matched.add(name);
            }
        }

        return matched;
    }

    /**
     * Formats a count of matched names for a status message, e.g. "1 variable" or "7 variables".
     *
     * @param count the number of variables
     * @return the count with a correctly pluralized noun
     */
    static String countPhrase(int count) {
        return count + (count == 1 ? " variable" : " variables");
    }
}
