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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * GUI-free engine for the Data Subset tool. Given a source data set and a {@link Spec} (selected variables, row
 * specification, row conditions, sampling mode, sample size, seed), produces the subset data set.
 * <p>
 * This logic used to live inside {@code DataSubsetEditor}, and the editor baked the resulting {@code DataSet} into the
 * box's {@code Parameters}. That meant {@code DataSubsetModel} never actually subset anything: on propagation it
 * simply reinstalled the stale baked data set, so changes upstream (e.g. a variable removed from the parent data) were
 * never reflected. The model now recomputes the subset from the parent data and the stored spec via this class; the
 * editor delegates here as well so the two cannot drift.
 * <p>
 * Behavior when the spec no longer matches the source data:
 * <ul>
 *   <li>Selected variable names that no longer exist are dropped silently, matching what the editor's restore-state
 *   logic has always done. If none of the named variables survive, an exception is thrown rather than silently
 *   falling back to all variables.</li>
 *   <li>An empty selection means all variables, as in the editor.</li>
 *   <li>A row specification that is out of bounds for the new data throws. (The editor separately chooses to warn
 *   and fall back to all rows in this case; the model does not, because there is no user to warn.)</li>
 *   <li>A condition naming a variable or category that no longer exists throws. This is deliberate: silently dropping
 *   a condition would analyze the whole data set while the user believed it had been restricted.</li>
 * </ul>
 */
public final class DataSubsetter {

    /**
     * Parameter key under which the editor stored the fully materialized subset. Now read only as a fallback for
     * sessions saved before the spec keys below existed.
     */
    public static final String KEY_LEGACY_SUBSET = "dataSubsetParamsEditorSubset";
    /**
     * Parameter key: list of selected variable names, in output column order.
     */
    public static final String KEY_SELECTED_VAR_NAMES = "dataSubsetSelectedVarNames";
    /**
     * Parameter key: row specification string.
     */
    public static final String KEY_ROW_SPEC = "dataSubsetRowSpec";
    /**
     * Parameter key: row condition specification string.
     */
    public static final String KEY_CONDITION_SPEC = "dataSubsetConditionSpec";
    /**
     * Parameter key: sampling mode enum name.
     */
    public static final String KEY_SAMPLING_MODE = "dataSubsetSamplingMode";
    /**
     * Parameter key: sample size.
     */
    public static final String KEY_SAMPLE_SIZE = "dataSubsetSampleSize";
    /**
     * Parameter key: seed text.
     */
    public static final String KEY_SEED = "dataSubsetSeed";

    private DataSubsetter() {
    }

    // ------------------------------------------------------------------------
    // Spec
    // ------------------------------------------------------------------------

    /**
     * Sampling modes for subset creation.
     */
    public enum SamplingMode {

        /**
         * Use rows as they are, in row-specification order.
         */
        USE_AS_IS("Use rows as-is"),

        /**
         * Randomize the order of the rows.
         */
        SHUFFLE("Shuffle rows"),

        /**
         * Draw a sample without replacement.
         */
        SUBSAMPLE("Subsample (without replacement)"),

        /**
         * Draw a sample with replacement.
         */
        BOOTSTRAP("Bootstrap (with replacement)");

        private final String label;

        SamplingMode(String label) {
            this.label = label;
        }

        /**
         * Returns the display label.
         *
         * @return the label.
         */
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * A complete description of a subset, independent of any particular source data set. Any field may be null,
     * meaning "default": all variables, all rows, no conditions, {@code USE_AS_IS}, sample size = number of rows,
     * unseeded randomness.
     *
     * @param selectedVarNames names of variables to keep, in output order; null or empty means all.
     * @param rowSpec          1-based row ranges, e.g. "1-100, 150"; null or blank means all rows.
     * @param conditionSpec    row conditions, e.g. "A = cat1 and X in (1, 2]"; null or blank means none.
     * @param samplingMode     sampling mode; null means {@code USE_AS_IS}.
     * @param sampleSize       sample size for SUBSAMPLE/BOOTSTRAP; null or non-positive means number of rows.
     * @param seedText         random seed as text; null, blank, or unparsable means unseeded.
     */
    public record Spec(List<String> selectedVarNames, String rowSpec, String conditionSpec,
                       SamplingMode samplingMode, Integer sampleSize, String seedText) {

        /**
         * Reads a spec from the given parameters, or returns null if none of the spec keys is present (i.e. the
         * parameters predate the spec keys and only the legacy baked subset is available).
         *
         * @param params the parameters.
         * @return the spec, or null.
         */
        public static Spec fromParameters(Parameters params) {
            // Parameters.get(name) falls back to (and records) a ParamDescriptions default - 0 for unknown keys -
            // so presence must be tested explicitly, or a fresh Parameters would look like a spec with sample size 0.
            List<String> names = null;
            Object namesObj = present(params, KEY_SELECTED_VAR_NAMES);
            if (namesObj instanceof List<?> list) {
                names = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) names.add(o.toString());
                }
            }

            String rowSpec = present(params, KEY_ROW_SPEC) instanceof String s ? s : null;
            String conditionSpec = present(params, KEY_CONDITION_SPEC) instanceof String s ? s : null;

            SamplingMode mode = null;
            if (present(params, KEY_SAMPLING_MODE) instanceof String s) {
                try {
                    mode = SamplingMode.valueOf(s);
                } catch (IllegalArgumentException ignored) {
                    // unknown; leave null
                }
            }

            Integer sampleSize = present(params, KEY_SAMPLE_SIZE) instanceof Number n ? n.intValue() : null;
            String seedText = present(params, KEY_SEED) instanceof String s ? s : null;

            boolean anyPresent = names != null || rowSpec != null || conditionSpec != null
                    || mode != null || sampleSize != null || seedText != null;

            return anyPresent ? new Spec(names, rowSpec, conditionSpec, mode, sampleSize, seedText) : null;
        }

        /**
         * The stored value for the key if one is present, else null; never consults or records a default.
         */
        private static Object present(Parameters params, String key) {
            return params.getParametersNames().contains(key) ? params.get(key, null) : null;
        }

        /**
         * Writes this spec into the given parameters under the standard keys.
         *
         * @param params the parameters.
         */
        public void storeIn(Parameters params) {
            params.set(KEY_SELECTED_VAR_NAMES, selectedVarNames == null ? new ArrayList<String>()
                    : new ArrayList<>(selectedVarNames));
            params.set(KEY_ROW_SPEC, rowSpec == null ? "" : rowSpec);
            params.set(KEY_CONDITION_SPEC, conditionSpec == null ? "" : conditionSpec);
            params.set(KEY_SAMPLING_MODE, (samplingMode == null ? SamplingMode.USE_AS_IS : samplingMode).name());
            params.set(KEY_SAMPLE_SIZE, sampleSize == null ? 0 : sampleSize);
            params.set(KEY_SEED, seedText == null ? "" : seedText);
        }
    }

    // ------------------------------------------------------------------------
    // Subsetting
    // ------------------------------------------------------------------------

    /**
     * Produces the subset of {@code source} described by {@code spec}.
     *
     * @param source the source data set.
     * @param spec   the subset spec; null means the whole data set.
     * @return the subset.
     * @throws IllegalArgumentException if the row specification or a condition is invalid for this data set, or if
     *                                  none of the selected variables exists in it.
     */
    public static DataSet subset(DataSet source, Spec spec) {
        Objects.requireNonNull(source, "source");
        if (spec == null) spec = new Spec(null, null, null, null, null, null);

        List<Node> selectedVars = selectVariables(source, spec.selectedVarNames());

        List<Integer> baseRows = parseRowSpec(spec.rowSpec(), source.getNumRows());

        // Conditions are applied before sampling, so a requested sample size is drawn from the rows satisfying the
        // conditions rather than being thinned by them afterwards.
        List<Integer> conditionedRows;
        try {
            conditionedRows = applyConditions(source, baseRows, parseConditions(source, spec.conditionSpec()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid condition: " + e.getMessage(), e);
        }

        List<Integer> finalRows = applySampling(conditionedRows, spec.samplingMode(), spec.sampleSize(),
                spec.seedText());

        DataSet columnSubset = source.subsetColumns(selectedVars);
        return columnSubset.subsetRows(finalRows);
    }

    /**
     * Resolves the selected variable names against the source. Names that no longer exist are dropped. An empty or
     * null selection means all variables.
     *
     * @param source the source data set.
     * @param names  the selected names, in output order; may be null.
     * @return the resolved variables, in order.
     * @throws IllegalArgumentException if names were given but none of them exists in the source.
     */
    public static List<Node> selectVariables(DataSet source, List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>(source.getVariables());
        }

        Map<String, Node> byName = new LinkedHashMap<>();
        for (Node v : source.getVariables()) {
            byName.put(v.getName(), v);
        }

        LinkedHashSet<Node> selected = new LinkedHashSet<>();
        for (String name : names) {
            Node v = byName.get(name);
            if (v != null) selected.add(v);
        }

        if (selected.isEmpty()) {
            throw new IllegalArgumentException("None of the selected variables exists in the data set: " + names);
        }

        return new ArrayList<>(selected);
    }

    /**
     * Parses the row specification (1-based ranges) into a sorted, duplicate-free list of 0-based row indices. A null
     * or blank spec returns all rows.
     *
     * @param spec    the row specification.
     * @param numRows the number of rows in the source.
     * @return the row indices.
     * @throws IllegalArgumentException if the spec is invalid or out of bounds.
     */
    public static List<Integer> parseRowSpec(String spec, int numRows) {
        if (spec == null || spec.trim().isEmpty()) {
            List<Integer> all = new ArrayList<>(numRows);
            for (int i = 0; i < numRows; i++) {
                all.add(i);
            }
            return all;
        }

        Set<Integer> indices = new TreeSet<>();

        String[] parts = spec.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            if (p.contains("-")) {
                String[] ab = p.split("-");
                if (ab.length != 2) {
                    throw new IllegalArgumentException("Invalid range: \"" + p + "\"");
                }
                String aStr = ab[0].trim();
                String bStr = ab[1].trim();
                if (aStr.isEmpty() || bStr.isEmpty()) {
                    throw new IllegalArgumentException("Invalid range: \"" + p + "\"");
                }

                int a;
                int b;
                try {
                    a = Integer.parseInt(aStr);
                    b = Integer.parseInt(bStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid range: \"" + p + "\"");
                }
                if (a < 1 || b < 1 || a > b || b > numRows) {
                    throw new IllegalArgumentException("Row range out of bounds: \"" + p + "\"");
                }

                for (int r = a; r <= b; r++) {
                    indices.add(r - 1);
                }
            } else {
                int r;
                try {
                    r = Integer.parseInt(p);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid row index: \"" + p + "\"");
                }
                if (r < 1 || r > numRows) {
                    throw new IllegalArgumentException("Row index out of bounds: " + r);
                }
                indices.add(r - 1);
            }
        }

        return new ArrayList<>(indices);
    }

    /**
     * Applies the sampling mode to the given rows.
     * <p>
     * If {@code seedText} parses as a long, sampling uses a private {@code java.util.Random} seeded with it, so the
     * same spec on the same data reproduces the same sample. Otherwise the global {@link RandomUtil} is used, as
     * before. (Previously the seed was parsed and then ignored.)
     *
     * @param baseRows   the rows to sample from.
     * @param mode       the sampling mode; null means {@code USE_AS_IS}.
     * @param sampleSize the sample size; null or non-positive means the number of base rows.
     * @param seedText   the seed as text; may be null.
     * @return the sampled rows.
     */
    public static List<Integer> applySampling(List<Integer> baseRows, SamplingMode mode, Integer sampleSize,
                                              String seedText) {
        if (mode == null) mode = SamplingMode.USE_AS_IS;

        int n = baseRows.size();
        if (n == 0) return new ArrayList<>(baseRows);

        int size = sampleSize == null ? n : sampleSize;
        if (size <= 0) size = n;

        Random seeded = null;
        if (seedText != null && !seedText.trim().isEmpty()) {
            try {
                seeded = new Random(Long.parseLong(seedText.trim()));
            } catch (NumberFormatException ignored) {
                // unparsable seed: unseeded, as before.
            }
        }

        switch (mode) {
            case USE_AS_IS:
                return new ArrayList<>(baseRows);

            case SHUFFLE: {
                List<Integer> shuffled = new ArrayList<>(baseRows);
                shuffle(shuffled, seeded);
                return shuffled;
            }

            case SUBSAMPLE: {
                if (size > n) size = n;
                List<Integer> temp = new ArrayList<>(baseRows);
                shuffle(temp, seeded);
                return new ArrayList<>(temp.subList(0, size));
            }

            case BOOTSTRAP: {
                List<Integer> boot = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    int idx = seeded == null ? RandomUtil.getInstance().nextInt(n) : seeded.nextInt(n);
                    boot.add(baseRows.get(idx));
                }
                return boot;
            }

            default:
                return new ArrayList<>(baseRows);
        }
    }

    private static void shuffle(List<Integer> list, Random seeded) {
        if (seeded == null) {
            RandomUtil.shuffle(list);
        } else {
            Collections.shuffle(list, seeded);
        }
    }

    /**
     * Counts the rows of {@code source} surviving the row specification and conditions (before sampling).
     *
     * @param source        the source data set.
     * @param rowSpec       the row specification.
     * @param conditionSpec the condition specification.
     * @return the number of kept rows, and the number of base rows, as a two-element array.
     * @throws IllegalArgumentException if either spec is invalid.
     */
    public static int[] countRows(DataSet source, String rowSpec, String conditionSpec) {
        List<Integer> base = parseRowSpec(rowSpec, source.getNumRows());
        List<RowCondition> conditions = parseConditions(source, conditionSpec);
        int kept = applyConditions(source, base, conditions).size();
        return new int[]{kept, base.size()};
    }

    // ------------------------------------------------------------------------
    // Row conditions
    // ------------------------------------------------------------------------

    /**
     * The comparison used by a single row condition.
     */
    private enum ConditionOp {
        EQ, NE, LT, LE, GT, GE, IN_SET, NOT_IN_SET, IN_INTERVAL, NOT_IN_INTERVAL
    }

    /**
     * A single parsed row condition, bound to a column of the source data set.
     * <p>
     * Rows whose value for the condition's variable is missing never satisfy the condition, for any operator,
     * including the negated ones. A missing value is not knowably outside a set or a range, so {@code A != cat1}
     * excludes missing rows just as {@code A = cat1} does.
     */
    private static final class RowCondition {
        private final int column;
        private final ConditionOp op;
        private final boolean discrete;
        private final Set<Integer> categoryIndices;
        private final double[] values;
        private final double low;
        private final double high;
        private final boolean lowClosed;
        private final boolean highClosed;

        private RowCondition(int column, ConditionOp op, boolean discrete,
                             Set<Integer> categoryIndices, double[] values,
                             double low, double high, boolean lowClosed, boolean highClosed) {
            this.column = column;
            this.op = op;
            this.discrete = discrete;
            this.categoryIndices = categoryIndices;
            this.values = values;
            this.low = low;
            this.high = high;
            this.lowClosed = lowClosed;
            this.highClosed = highClosed;
        }

        static RowCondition discrete(int column, ConditionOp op, Set<Integer> indices) {
            return new RowCondition(column, op, true, indices, null, 0, 0, false, false);
        }

        static RowCondition continuous(int column, ConditionOp op, double[] values) {
            return new RowCondition(column, op, false, null, values, 0, 0, false, false);
        }

        static RowCondition interval(int column, ConditionOp op,
                                     double low, double high, boolean lowClosed, boolean highClosed) {
            return new RowCondition(column, op, false, null, null, low, high, lowClosed, highClosed);
        }

        boolean holds(DataSet data, int row) {
            if (discrete) {
                int v = data.getInt(row, column);
                if (v == DiscreteVariable.MISSING_VALUE || v < 0) return false;

                boolean in = categoryIndices.contains(v);
                return (op == ConditionOp.NOT_IN_SET || op == ConditionOp.NE) != in;
            }

            double v = data.getDouble(row, column);
            if (Double.isNaN(v)) return false;

            switch (op) {
                case EQ:
                    return v == values[0];
                case NE:
                    return v != values[0];
                case LT:
                    return v < values[0];
                case LE:
                    return v <= values[0];
                case GT:
                    return v > values[0];
                case GE:
                    return v >= values[0];
                case IN_SET:
                case NOT_IN_SET: {
                    boolean in = false;
                    for (double value : values) {
                        if (v == value) {
                            in = true;
                            break;
                        }
                    }
                    return (op == ConditionOp.NOT_IN_SET) != in;
                }
                case IN_INTERVAL:
                case NOT_IN_INTERVAL: {
                    boolean in = (lowClosed ? v >= low : v > low)
                            && (highClosed ? v <= high : v < high);
                    return (op == ConditionOp.NOT_IN_INTERVAL) != in;
                }
                default:
                    return false;
            }
        }
    }

    /**
     * Parses a condition specification into a list of conditions, all of which must hold for a row to be kept.
     * <p>
     * Conditions are joined by the keyword {@code and}; {@code in}, {@code not in} and {@code and} are recognized in
     * any capitalization. Variable names and category values are matched as typed, falling back to a case-insensitive
     * match when that is unambiguous. Names or values containing spaces, commas or keywords may be double-quoted.
     *
     * <pre>
     *   A = cat1                     discrete equality
     *   A != cat1                    discrete inequality
     *   A in {cat1, cat2}            discrete set membership
     *   A not in {cat1, cat2}        discrete set exclusion
     *   X = 1                        continuous equality (exact)
     *   X &lt; 1   X &lt;= 1  X &gt; 1  X &gt;= 1  continuous comparison
     *   X in (1, 2)                  open interval; [ or ] closes an endpoint
     *   X not in [1, 2]              interval exclusion
     *   X in {1, 2.5}                exact match against any listed value
     *   A = cat1 and X in (1, 2]     conjunction
     * </pre>
     */
    private static List<RowCondition> parseConditions(DataSet source, String spec) {
        List<RowCondition> conditions = new ArrayList<>();
        if (spec == null || spec.trim().isEmpty()) return conditions;

        for (String clause : splitOnAnd(spec)) {
            String c = clause.trim();
            if (c.isEmpty()) continue;
            conditions.add(parseCondition(source, c));
        }

        return conditions;
    }

    /**
     * Splits a specification on the keyword {@code and}, ignoring occurrences inside quotes or inside a bracketed set
     * or interval, and requiring word boundaries so that a variable named e.g. "brand" is not mistaken for a
     * separator.
     */
    private static List<String> splitOnAnd(String spec) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        int depth = 0;
        boolean inQuotes = false;

        for (int i = 0; i < spec.length(); i++) {
            char ch = spec.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
                current.append(ch);
                continue;
            }

            if (!inQuotes) {
                if (ch == '{' || ch == '(' || ch == '[') depth++;
                else if (ch == '}' || ch == ')' || ch == ']') depth--;

                if (depth == 0 && (ch == 'a' || ch == 'A') && matchesKeywordAt(spec, i, "and")) {
                    parts.add(current.toString());
                    current.setLength(0);
                    i += 2;
                    continue;
                }
            }

            current.append(ch);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unbalanced quotation marks.");
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unbalanced brackets.");
        }

        parts.add(current.toString());
        return parts;
    }

    private static boolean matchesKeywordAt(String s, int i, String keyword) {
        int end = i + keyword.length();
        if (end > s.length()) return false;
        if (!s.substring(i, end).equalsIgnoreCase(keyword)) return false;
        if (i > 0 && isWordChar(s.charAt(i - 1))) return false;
        return end >= s.length() || !isWordChar(s.charAt(end));
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    private static RowCondition parseCondition(DataSet source, String clause) {
        int opIndex = -1;
        int opLength = 0;
        ConditionOp op = null;
        boolean negatedKeyword = false;

        boolean inQuotes = false;

        for (int i = 0; i < clause.length() && opIndex < 0; i++) {
            char ch = clause.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (inQuotes) continue;

            if (clause.startsWith("!=", i)) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.NE;
            } else if (clause.startsWith("<=", i)) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.LE;
            } else if (clause.startsWith(">=", i)) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.GE;
            } else if (ch == '=') {
                opIndex = i;
                opLength = 1;
                op = ConditionOp.EQ;
            } else if (ch == '<') {
                opIndex = i;
                opLength = 1;
                op = ConditionOp.LT;
            } else if (ch == '>') {
                opIndex = i;
                opLength = 1;
                op = ConditionOp.GT;
            } else if (matchesKeywordAt(clause, i, "not")) {
                int j = i + 3;
                while (j < clause.length() && Character.isWhitespace(clause.charAt(j))) j++;
                if (!matchesKeywordAt(clause, j, "in")) {
                    throw new IllegalArgumentException(
                            "Expected \"not in\" in condition: \"" + clause + "\"");
                }
                opIndex = i;
                opLength = (j + 2) - i;
                op = ConditionOp.IN_SET;
                negatedKeyword = true;
            } else if (matchesKeywordAt(clause, i, "in")) {
                opIndex = i;
                opLength = 2;
                op = ConditionOp.IN_SET;
            }
        }

        if (opIndex < 0) {
            throw new IllegalArgumentException(
                    "No comparison found in condition: \"" + clause + "\". Expected one of "
                            + "=, !=, <, <=, >, >=, in, not in.");
        }

        String nameText = clause.substring(0, opIndex).trim();
        String operandText = clause.substring(opIndex + opLength).trim();

        if (nameText.isEmpty()) {
            throw new IllegalArgumentException("Missing variable name in condition: \"" + clause + "\"");
        }
        if (operandText.isEmpty()) {
            throw new IllegalArgumentException("Missing value in condition: \"" + clause + "\"");
        }

        Node variable = lookUpVariable(source, unquote(nameText));
        int column = source.getColumnIndex(variable);
        boolean isDiscrete = variable instanceof DiscreteVariable;

        boolean keywordOp = (opLength >= 2 && (op == ConditionOp.IN_SET))
                && (operandText.startsWith("{") || operandText.startsWith("(")
                || operandText.startsWith("["));

        if (op == ConditionOp.IN_SET && !keywordOp) {
            throw new IllegalArgumentException(
                    "After \"in\", expected a set in braces or an interval in brackets: \""
                            + clause + "\"");
        }

        if (operandText.startsWith("{")) {
            List<String> items = parseBracketedList(operandText, '{', '}', clause);
            ConditionOp setOp = negatedKeyword ? ConditionOp.NOT_IN_SET : ConditionOp.IN_SET;

            if (isDiscrete) {
                Set<Integer> indices = new LinkedHashSet<>();
                for (String item : items) {
                    indices.add(lookUpCategory((DiscreteVariable) variable, unquote(item)));
                }
                return RowCondition.discrete(column, setOp, indices);
            } else {
                double[] values = new double[items.size()];
                for (int i = 0; i < items.size(); i++) {
                    values[i] = parseNumber(unquote(items.get(i)), clause);
                }
                return RowCondition.continuous(column, setOp, values);
            }
        }

        if (operandText.startsWith("(") || operandText.startsWith("[")) {
            if (isDiscrete) {
                throw new IllegalArgumentException(
                        "Interval conditions do not apply to the discrete variable \""
                                + variable.getName() + "\"; use = , != , in {...} or not in {...}.");
            }

            boolean lowClosed = operandText.startsWith("[");
            char close = operandText.charAt(operandText.length() - 1);
            if (close != ')' && close != ']') {
                throw new IllegalArgumentException(
                        "Interval must end with ) or ]: \"" + clause + "\"");
            }
            boolean highClosed = close == ']';

            List<String> bounds = parseBracketedList(operandText, operandText.charAt(0), close, clause);
            if (bounds.size() != 2) {
                throw new IllegalArgumentException(
                        "An interval needs exactly two endpoints: \"" + clause + "\"");
            }

            double low = parseNumber(unquote(bounds.get(0)), clause);
            double high = parseNumber(unquote(bounds.get(1)), clause);

            if (low > high) {
                throw new IllegalArgumentException(
                        "Interval lower bound exceeds upper bound: \"" + clause + "\"");
            }

            ConditionOp intervalOp = negatedKeyword ? ConditionOp.NOT_IN_INTERVAL : ConditionOp.IN_INTERVAL;
            return RowCondition.interval(column, intervalOp, low, high, lowClosed, highClosed);
        }

        String value = unquote(operandText);

        if (isDiscrete) {
            if (op != ConditionOp.EQ && op != ConditionOp.NE) {
                throw new IllegalArgumentException(
                        "The operator in \"" + clause + "\" does not apply to the discrete variable \""
                                + variable.getName() + "\"; use = , != , in {...} or not in {...}.");
            }
            Set<Integer> indices = new LinkedHashSet<>();
            indices.add(lookUpCategory((DiscreteVariable) variable, value));
            return RowCondition.discrete(column, op, indices);
        }

        return RowCondition.continuous(column, op, new double[]{parseNumber(value, clause)});
    }

    private static List<String> parseBracketedList(String text, char open, char close, String clause) {
        if (text.length() < 2 || text.charAt(0) != open || text.charAt(text.length() - 1) != close) {
            throw new IllegalArgumentException("Malformed list or interval: \"" + clause + "\"");
        }

        String inner = text.substring(1, text.length() - 1);
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                current.append(ch);
            } else if (ch == ',' && !inQuotes) {
                items.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        items.add(current.toString().trim());

        for (String item : items) {
            if (item.isEmpty()) {
                throw new IllegalArgumentException("Empty entry in: \"" + clause + "\"");
            }
        }

        return items;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static double parseNumber(String s, String clause) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number but found \"" + s + "\" in: \""
                    + clause + "\"");
        }
    }

    /**
     * Resolves a variable name against the source data set: exact match first, then a unique case-insensitive match.
     */
    private static Node lookUpVariable(DataSet source, String name) {
        Node exact = source.getVariable(name);
        if (exact != null) return exact;

        Node found = null;
        for (Node node : source.getVariables()) {
            if (node.getName().equalsIgnoreCase(name)) {
                if (found != null) {
                    throw new IllegalArgumentException(
                            "The variable name \"" + name + "\" is ambiguous apart from case.");
                }
                found = node;
            }
        }

        if (found == null) {
            throw new IllegalArgumentException("No such variable in this data set: \"" + name + "\"");
        }

        return found;
    }

    /**
     * Resolves a category name against a discrete variable: exact match first, then a unique case-insensitive match.
     */
    private static int lookUpCategory(DiscreteVariable variable, String category) {
        List<String> categories = variable.getCategories();

        int index = categories.indexOf(category);
        if (index >= 0) return index;

        int found = -1;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equalsIgnoreCase(category)) {
                if (found >= 0) {
                    throw new IllegalArgumentException("The category \"" + category
                            + "\" of \"" + variable.getName() + "\" is ambiguous apart from case.");
                }
                found = i;
            }
        }

        if (found < 0) {
            throw new IllegalArgumentException("\"" + category + "\" is not a category of \""
                    + variable.getName() + "\". Categories are: " + categories);
        }

        return found;
    }

    private static List<Integer> applyConditions(DataSet source, List<Integer> baseRows,
                                                 List<RowCondition> conditions) {
        if (conditions.isEmpty()) return baseRows;

        List<Integer> kept = new ArrayList<>(baseRows.size());

        outer:
        for (int row : baseRows) {
            for (RowCondition condition : conditions) {
                if (!condition.holds(source, row)) continue outer;
            }
            kept.add(row);
        }

        return kept;
    }
}
