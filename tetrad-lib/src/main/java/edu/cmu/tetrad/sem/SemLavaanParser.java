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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a linear SEM IM from lavaan model syntax, the inverse of {@link SemLavaanRenderer}. The parameter values are
 * taken from the numeric modifiers in the syntax, either a fixed value, as in {@code 0.5*x}, or a starting value, as
 * in {@code start(0.5)*x}; both are loaded simply as the value of the parameter.
 * <p>
 * The statements read are regressions ({@code y ~ x1 + x2}), intercepts ({@code y ~ 1}), variances and covariances
 * ({@code x ~~ x}, {@code x ~~ y}), and latent variable definitions ({@code f =~ y1 + y2}, read as edges from the
 * latent f to its indicators). Comments, statements split over several lines, several statements on a line separated
 * by semicolons, several variables on the left-hand side, and repeated terms are handled. Labels and the other
 * modifiers that carry no value are ignored. Everything else in lavaan syntax (defined parameters, constraints,
 * thresholds, formative composites, multiple-group vectors) is skipped with a warning.
 * <p>
 * Lavaan model syntax need not give a value for every parameter. A parameter with no value in the syntax is given a
 * fixed default (0 for a coefficient, covariance, or intercept, 1 for a variance) and reported in the warnings, so
 * that nothing in the loaded model is random.
 *
 * @author josephramsey
 * @author Claude
 * @see SemLavaanRenderer
 */
public final class SemLavaanParser {

    /**
     * The operators of lavaan model syntax; the ones that share a first character are listed longest first.
     */
    private static final Pattern STATEMENT = Pattern.compile("^(.+?)(=~|==|<~|<|>|~\\*~|~~|~|:=|\\|)(.*)$");

    private static final Pattern START = Pattern.compile("^start\\((.*)\\)$");

    /**
     * The most parameters that a warning lists by name.
     */
    private static final int MAX_LISTED = 10;

    /**
     * Prevent instantiation.
     */
    private SemLavaanParser() {
    }

    /**
     * Reads a SEM IM from lavaan model syntax, discarding the warnings.
     *
     * @param lavaan the lavaan model syntax.
     * @return the SEM IM.
     * @throws IllegalArgumentException if the syntax contains no model or cannot be read.
     * @see #parse(String)
     */
    public static SemIm lavaanToSemIm(String lavaan) {
        return parse(lavaan).semIm();
    }

    /**
     * Reads a SEM IM from a file of lavaan model syntax.
     *
     * @param file the file.
     * @return the SEM IM, together with warnings about anything in the file that was skipped or defaulted.
     * @throws IOException              if the file cannot be read.
     * @throws IllegalArgumentException if the file contains no model or cannot be read as lavaan syntax.
     */
    public static Result parse(File file) throws IOException {
        return parse(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * Reads a SEM IM from lavaan model syntax.
     *
     * @param lavaan the lavaan model syntax.
     * @return the SEM IM, together with warnings about anything in the syntax that was skipped or defaulted.
     * @throws IllegalArgumentException if the syntax contains no model or cannot be read.
     */
    public static Result parse(String lavaan) {
        if (lavaan == null) {
            throw new NullPointerException("The lavaan syntax must not be null.");
        }

        List<String> warnings = new ArrayList<>();

        // Names in order of first appearance, then the terms of the model, each with its value if one was given.
        Set<String> names = new LinkedHashSet<>();
        Set<String> latents = new LinkedHashSet<>();
        Map<List<String>, Double> coefs = new LinkedHashMap<>();
        Map<List<String>, Double> covars = new LinkedHashMap<>();
        Map<String, Double> variances = new LinkedHashMap<>();
        Map<String, Double> intercepts = new LinkedHashMap<>();

        for (String statement : statements(lavaan)) {
            Matcher matcher = STATEMENT.matcher(statement);

            if (!matcher.matches()) {
                warnings.add("Skipped a line that is not a lavaan formula: " + statement);
                continue;
            }

            String op = matcher.group(2);

            if (!(op.equals("~") || op.equals("~~") || op.equals("=~"))) {
                warnings.add("Skipped a '" + op + "' statement, which has no counterpart in a SEM IM: " + statement);
                continue;
            }

            List<String> lhs = split(matcher.group(1), '+');
            List<String> rhs = split(matcher.group(3), '+');

            if (lhs.contains("") || rhs.contains("")) {
                throw new IllegalArgumentException("Incomplete lavaan formula: " + statement);
            }

            for (String left : lhs) {
                if (isNumber(left)) {
                    throw new IllegalArgumentException("Expecting a variable name on the left-hand side: " + statement);
                }

                names.add(left);

                if (op.equals("=~")) {
                    latents.add(left);
                }

                for (String term : rhs) {
                    List<String> factors = split(term, '*');
                    String right = factors.getLast();
                    Double value = null;

                    if (right.isEmpty()) {
                        throw new IllegalArgumentException("Incomplete lavaan formula: " + statement);
                    }

                    for (String modifier : factors.subList(0, factors.size() - 1)) {
                        Double _value = value(modifier, statement, warnings);

                        if (_value != null) {
                            value = _value;
                        }
                    }

                    if (op.equals("~") && (right.equals("1") || right.equals("0"))) {

                        // "y ~ 1" is the intercept of y, and "y ~ 0" fixes it to zero.
                        merge(intercepts, left, right.equals("0") ? Double.valueOf(0.0) : value);
                        continue;
                    } else if (isNumber(right)) {
                        throw new IllegalArgumentException("Expecting a variable name on the right-hand side: " + statement);
                    }

                    names.add(right);

                    if (left.equals(right)) {
                        if (op.equals("~~")) {
                            merge(variances, left, value);
                        } else {
                            warnings.add("Skipped an edge from " + left + " to itself: " + statement);
                        }
                    } else if (op.equals("~~")) {
                        List<String> reversed = List.of(right, left);
                        merge(covars, covars.containsKey(reversed) ? reversed : List.of(left, right), value);
                    } else if (op.equals("~")) {
                        merge(coefs, List.of(right, left), value);
                    } else {
                        merge(coefs, List.of(left, right), value);
                    }
                }
            }
        }

        if (names.isEmpty()) {
            throw new IllegalArgumentException("No lavaan model was found.");
        }

        Graph graph = new EdgeListGraph();
        Map<String, Node> nodes = new LinkedHashMap<>();

        for (String name : names) {
            GraphNode node = new GraphNode(name);
            node.setNodeType(latents.contains(name) ? NodeType.LATENT : NodeType.MEASURED);
            nodes.put(name, node);
            graph.addNode(node);
        }

        for (List<String> edge : coefs.keySet()) {
            graph.addDirectedEdge(nodes.get(edge.get(0)), nodes.get(edge.get(1)));
        }

        for (List<String> edge : covars.keySet()) {
            graph.addBidirectedEdge(nodes.get(edge.get(0)), nodes.get(edge.get(1)));
        }

        SemIm semIm = new SemIm(new SemPm(graph));
        List<String> noValue = new ArrayList<>();

        for (Parameter parameter : semIm.getSemPm().getParameters()) {
            String a = parameter.getNodeA().getName();
            String b = parameter.getNodeB().getName();
            Double value;
            double _default = 0.0;

            if (parameter.getType() == ParamType.COEF) {
                value = coefs.get(List.of(a, b));
            } else if (parameter.getType() == ParamType.COVAR) {
                value = covars.containsKey(List.of(a, b)) ? covars.get(List.of(a, b)) : covars.get(List.of(b, a));
            } else if (parameter.getType() == ParamType.VAR) {
                value = variances.get(a);
                _default = 1.0;
            } else {
                continue;
            }

            if (value == null) {
                noValue.add(describe(parameter) + " (set to " + (_default == 0.0 ? "0" : "1") + ")");
            }

            setValue(semIm, parameter, value == null ? _default : value);
        }

        Map<Node, Double> _intercepts = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : intercepts.entrySet()) {
            if (entry.getValue() != null) {
                _intercepts.put(semIm.getVariableNode(entry.getKey()), entry.getValue());
            } else {
                noValue.add("the intercept of " + entry.getKey() + " (set to 0)");
            }
        }

        if (!noValue.isEmpty()) {
            int shown = Math.min(noValue.size(), MAX_LISTED);
            warnings.add("No value was given for " + noValue.size() + " of the parameters, so each was set to a "
                         + "default: " + String.join(", ", noValue.subList(0, shown))
                         + (shown < noValue.size() ? ", and " + (noValue.size() - shown) + " more." : "."));
        }

        if (!_intercepts.isEmpty()) {
            if (semIm.isCyclic()) {
                warnings.add("The model is cyclic, so the intercepts were not loaded; the means are left at zero.");
            } else {
                semIm.setIntercepts(_intercepts);
            }
        }

        return new Result(semIm, Collections.unmodifiableList(warnings));
    }

    /**
     * Breaks lavaan syntax into its statements, removing comments, splitting lines at semicolons, and joining a
     * formula that is continued over several lines.
     */
    private static List<String> statements(String lavaan) {
        List<String> statements = new ArrayList<>();

        for (String line : lavaan.split("\\R")) {
            for (String piece : line.replaceFirst("[#!].*$", "").split(";")) {
                piece = piece.trim();

                if (piece.isEmpty()) {
                    continue;
                }

                // A formula carries over when a line ends, or the next line starts, with something that cannot end,
                // or start, a formula.
                boolean continues = !statements.isEmpty() && statements.getLast().matches(".*[+*~]$");

                if (continues || piece.startsWith("+")) {
                    if (statements.isEmpty()) {
                        throw new IllegalArgumentException("Incomplete lavaan formula: " + piece);
                    }

                    statements.set(statements.size() - 1, statements.getLast() + " " + piece);
                } else {
                    statements.add(piece);
                }
            }
        }

        return statements;
    }

    /**
     * Splits the given string at the given separator, trimming the pieces, but not inside parentheses, and not at the
     * sign of an exponent, as in 1.5e+10.
     */
    private static List<String> split(String s, char separator) {
        List<String> pieces = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == separator && depth == 0 && !(c == '+' && isExponentSign(s, start, i))) {
                pieces.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }

        pieces.add(s.substring(start).trim());
        return pieces;
    }

    private static boolean isExponentSign(String s, int start, int i) {
        return i + 1 < s.length() && Character.isDigit(s.charAt(i + 1))
               && s.substring(start, i).trim().matches("[-+]?(\\d+\\.?\\d*|\\.\\d+)[eE]");
    }

    /**
     * Returns the value that the given modifier carries, or null if it carries none.
     */
    private static Double value(String modifier, String statement, List<String> warnings) {
        Matcher matcher = START.matcher(modifier);
        String number = matcher.matches() ? matcher.group(1).trim() : modifier;

        if (isNumber(number)) {
            double value = Double.parseDouble(number.replace(',', '.'));
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } else if (matcher.matches()) {
            throw new IllegalArgumentException("Expecting a number in '" + modifier + "': " + statement);
        } else if (modifier.matches("^c\\(.*\\)$")) {
            warnings.add("Skipped a vector of values for multiple groups, '" + modifier + "': " + statement);
        }

        return null;
    }

    /**
     * True if the given string is a number. A decimal comma is accepted, since the lavaan syntax that Tetrad saves
     * is written in the number format of the user's locale.
     */
    private static boolean isNumber(String s) {
        if (!s.matches("[-+]?(\\d+[.,]?\\d*|[.,]\\d+)([eE][-+]?\\d+)?|[-+]?(NaN|Infinity)")) {
            return false;
        }

        try {
            Double.parseDouble(s.replace(',', '.'));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Records the value of a term, keeping an earlier value if a repeat of the term gives none.
     */
    private static <K> void merge(Map<K, Double> values, K key, Double value) {
        if (value != null || !values.containsKey(key)) {
            values.put(key, value);
        }
    }

    private static void setValue(SemIm semIm, Parameter parameter, double value) {
        if (semIm.getFixedParameters().contains(parameter)) {
            semIm.setFixedParamValue(parameter, value);
        } else {
            semIm.setParamValue(parameter, value);
        }
    }

    private static String describe(Parameter parameter) {
        String a = parameter.getNodeA().getName();
        String b = parameter.getNodeB().getName();

        if (parameter.getType() == ParamType.COEF) {
            return "the coefficient of " + a + " --> " + b;
        } else if (parameter.getType() == ParamType.COVAR) {
            return "the covariance of " + a + " and " + b;
        } else {
            return "the variance of " + a;
        }
    }

    /**
     * A SEM IM read from lavaan syntax.
     *
     * @param semIm    the SEM IM.
     * @param warnings a description of each thing in the syntax that was skipped or given a default value; empty if
     *                 everything was read.
     */
    public record Result(SemIm semIm, List<String> warnings) {
    }
}
