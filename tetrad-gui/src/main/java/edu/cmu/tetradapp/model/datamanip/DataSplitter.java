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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;

/**
 * GUI-free engine for the Split Data tool. Splits one tabular data set into several, one per row condition, for use
 * by multi-data-set methods such as IMaGES.
 * <p>
 * The split is defined by a list of lines, each of which is a row condition in the language of the Data Subset tool
 * (see {@link DataSubsetter#rowsSatisfying(DataSet, String)}), optionally preceded by a name and a vertical bar:
 *
 * <pre>
 *   Region = Bejaia
 *   Region = "Sidi-Bel Abbes"
 *   young | Age &lt; 30
 *   old   | Age &gt;= 30 and Sex = F
 * </pre>
 * <p>
 * A line may instead be just the name of a variable, or several names separated by commas:
 *
 * <pre>
 *   anon_student_id
 *   Region, Sex
 * </pre>
 * <p>
 * Such a line expands, against the data as it is when the split is applied, into one split per observed value of
 * the variable (per observed combination, for several), named by the value(s); and the named variables are removed
 * from every split, since each is constant within its splits. A continuous variable expands to one split per
 * distinct non-missing value, which is what is wanted for numeric identifiers loaded as continuous. Unlike lines
 * written out by the generator, a bare-variable line follows the data: a value added upstream gets a split.
 * <p>
 * Each line yields one data set holding exactly the rows satisfying its condition. The lines are independent: a row
 * satisfying two conditions appears in both data sets, and a row satisfying none appears in no data set unless a
 * remainder data set is requested. Rows with a missing value for a conditioned variable never satisfy a condition,
 * so with a remainder requested they land there. Blank lines and lines beginning with {@code #} are ignored. Lines
 * whose condition matches no row produce no data set.
 * <p>
 * The variables named in {@link Spec#dropVarNames()} are removed from every split. The intended use is the
 * splitting variables themselves: a variable that is constant within a split has zero variance there and breaks
 * most scores and tests.
 * <p>
 * By default every split keeps the parent's discrete variables as they are, so a category never observed in a split
 * is still in that variable's category list. That is what multi-data-set scores need, since they require the same
 * category counts in every data set. With {@link Spec#keepAllCategories()} false, each split's discrete variables
 * are instead rebuilt with only the categories observed in that split, for when the splits are analyzed separately
 * and empty categories would distort a score's prior.
 * <p>
 * The generator methods ({@link #generateLines}) write lines for the common cases - one split per observed category
 * of one or more discrete variables, one per bin of a continuous variable, or the product of these - so that the
 * user can start from them and edit rather than typing conditions by hand. Bin boundaries are written into the
 * lines as numbers, so the split is fully described by its text and re-applies identically to updated data.
 */
public final class DataSplitter {

    /**
     * Parameter key: the split lines, a {@code List<String>}.
     */
    public static final String KEY_LINES = "dataSplitLines";
    /**
     * Parameter key: the names of variables to remove from every split, a {@code List<String>}.
     */
    public static final String KEY_DROP_VAR_NAMES = "dataSplitDropVarNames";
    /**
     * Parameter key: whether to emit a data set of the rows matching no condition, a Boolean.
     */
    public static final String KEY_INCLUDE_REMAINDER = "dataSplitIncludeRemainder";
    /**
     * Parameter key: the name of the remainder data set, a String.
     */
    public static final String KEY_REMAINDER_NAME = "dataSplitRemainderName";

    /**
     * Parameter key: whether discrete variables keep every category of the parent data, a Boolean.
     */
    public static final String KEY_KEEP_ALL_CATEGORIES = "dataSplitKeepAllCategories";

    /**
     * The default name of the remainder data set.
     */
    public static final String DEFAULT_REMAINDER_NAME = "Remainder";

    private DataSplitter() {
    }

    // ------------------------------------------------------------------------
    // Spec
    // ------------------------------------------------------------------------

    /**
     * One split: a name and the row condition defining it.
     *
     * @param name       the name of the resulting data set.
     * @param condition  the row condition, in the Data Subset condition language.
     * @param lineNumber the 1-based number of the line this came from, for error messages.
     */
    public record SplitDef(String name, String condition, int lineNumber) {
    }

    /**
     * The full specification of a split, as stored in the parameters.
     *
     * @param lines            the split lines; see the class comment for the syntax.
     * @param dropVarNames     the names of variables removed from every split; null or empty means none.
     * @param includeRemainder whether rows matching no condition form a data set of their own.
     * @param remainderName    the name of that data set; null or blank means {@link #DEFAULT_REMAINDER_NAME}.
     * @param keepAllCategories whether each split's discrete variables keep every category of the parent data
     *                          (true, the default) or only the categories observed in that split.
     */
    public record Spec(List<String> lines, List<String> dropVarNames, boolean includeRemainder,
                       String remainderName, boolean keepAllCategories) {

        /**
         * Convenience constructor keeping all categories.
         *
         * @param lines            the split lines.
         * @param dropVarNames     the names of variables removed from every split.
         * @param includeRemainder whether to emit a remainder data set.
         * @param remainderName    its name.
         */
        public Spec(List<String> lines, List<String> dropVarNames, boolean includeRemainder,
                    String remainderName) {
            this(lines, dropVarNames, includeRemainder, remainderName, true);
        }

        /**
         * Reads a spec from the parameters, or returns null if no split lines are stored.
         *
         * @param params the parameters.
         * @return the spec, or null.
         */
        public static Spec fromParameters(Parameters params) {
            Object linesObj = present(params, KEY_LINES);
            if (!(linesObj instanceof List<?> lineList)) return null;

            List<String> lines = new ArrayList<>();
            for (Object o : lineList) if (o != null) lines.add(o.toString());

            List<String> drop = new ArrayList<>();
            if (present(params, KEY_DROP_VAR_NAMES) instanceof List<?> dropList) {
                for (Object o : dropList) if (o != null) drop.add(o.toString());
            }

            boolean includeRemainder = present(params, KEY_INCLUDE_REMAINDER) instanceof Boolean b && b;
            String remainderName = present(params, KEY_REMAINDER_NAME) instanceof String s ? s : null;
            boolean keepAll = !(present(params, KEY_KEEP_ALL_CATEGORIES) instanceof Boolean k) || k;

            return new Spec(lines, drop, includeRemainder, remainderName, keepAll);
        }

        private static Object present(Parameters params, String key) {
            return params.getParametersNames().contains(key) ? params.get(key, null) : null;
        }

        /**
         * Writes this spec into the parameters under the standard keys.
         *
         * @param params the parameters.
         */
        public void storeIn(Parameters params) {
            params.set(KEY_LINES, lines == null ? new ArrayList<String>() : new ArrayList<>(lines));
            params.set(KEY_DROP_VAR_NAMES, dropVarNames == null ? new ArrayList<String>()
                    : new ArrayList<>(dropVarNames));
            params.set(KEY_INCLUDE_REMAINDER, includeRemainder);
            params.set(KEY_REMAINDER_NAME, effectiveRemainderName());
            params.set(KEY_KEEP_ALL_CATEGORIES, keepAllCategories);
        }

        /**
         * @return the remainder name, defaulted if blank.
         */
        public String effectiveRemainderName() {
            return remainderName == null || remainderName.isBlank() ? DEFAULT_REMAINDER_NAME : remainderName.trim();
        }
    }

    // ------------------------------------------------------------------------
    // Parsing the lines
    // ------------------------------------------------------------------------

    /**
     * Parses the split lines into split definitions. A line is {@code name | condition} or just {@code condition},
     * in which case the condition text is the name. Blank lines and {@code #} comment lines are skipped. A vertical
     * bar inside double quotes is not a separator.
     *
     * @param lines the lines.
     * @return the split definitions, in order.
     * @throws IllegalArgumentException if a line has a name but no condition, or an empty name.
     */
    public static List<SplitDef> parseLines(List<String> lines) {
        List<SplitDef> defs = new ArrayList<>();
        if (lines == null) return defs;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int bar = indexOfUnquoted(line, '|');

            if (bar < 0) {
                defs.add(new SplitDef(line, line, i + 1));
            } else {
                String name = line.substring(0, bar).trim();
                String condition = line.substring(bar + 1).trim();

                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": empty name before \"|\".");
                }
                if (condition.isEmpty()) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": no condition after \"|\".");
                }

                defs.add(new SplitDef(name, condition, i + 1));
            }
        }

        return defs;
    }

    private static int indexOfUnquoted(String s, char target) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            else if (!inQuotes && ch == target) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------------
    // Preview and split
    // ------------------------------------------------------------------------

    /**
     * The result of evaluating a spec against a data set without building the splits.
     *
     * @param splits          the split definitions, in order (empty ones included).
     * @param rowCounts       the number of rows matching each split.
     * @param unmatched       the number of rows matching no split.
     * @param multiplyMatched the number of rows matching more than one split.
     * @param numRows         the number of rows in the source.
     * @param impliedDropVarNames the variables removed from every split because a bare-variable line expanded them.
     * @param numSplitsWithUnobservedCategories the number of non-empty splits in which some kept discrete variable
     *                                          has a category it never takes; these are the splits that
     *                                          {@code keepAllCategories = false} would change.
     */
    public record Preview(List<SplitDef> splits, int[] rowCounts, int unmatched, int multiplyMatched,
                          int numRows, List<String> impliedDropVarNames, int numSplitsWithUnobservedCategories) {

        /**
         * @return the number of splits with at least one row.
         */
        public int numNonEmpty() {
            int n = 0;
            for (int c : rowCounts) if (c > 0) n++;
            return n;
        }
    }

    /**
     * Evaluates every condition of {@code spec} against {@code source} and reports row counts, without building
     * any data set.
     *
     * @param source the source data set.
     * @param spec   the spec.
     * @return the preview.
     * @throws IllegalArgumentException if a line cannot be parsed or a condition is invalid for this data set; the
     *                                  message begins with the offending line's number.
     */
    public static Preview preview(DataSet source, Spec spec) {
        Expansion expansion = expand(source, parseLines(spec.lines()));
        List<SplitDef> defs = expansion.defs();
        List<List<Integer>> rows = evaluate(source, defs);

        int[] counts = new int[defs.size()];
        int[] hits = new int[source.getNumRows()];

        for (int i = 0; i < defs.size(); i++) {
            counts[i] = rows.get(i).size();
            for (int r : rows.get(i)) hits[r]++;
        }

        int unmatched = 0;
        int multiple = 0;
        for (int h : hits) {
            if (h == 0) unmatched++;
            else if (h > 1) multiple++;
        }

        List<String> drop = new ArrayList<>();
        if (spec.dropVarNames() != null) drop.addAll(spec.dropVarNames());
        drop.addAll(expansion.dropVarNames());
        List<Node> kept = keptVariables(source, drop);

        int trimmed = 0;
        for (List<Integer> r : rows) {
            if (!r.isEmpty() && hasUnobservedCategory(source, kept, r)) trimmed++;
        }

        return new Preview(defs, counts, unmatched, multiple, source.getNumRows(), expansion.dropVarNames(),
                trimmed);
    }

    private static boolean hasUnobservedCategory(DataSet source, List<Node> kept, List<Integer> rows) {
        for (Node v : kept) {
            if (!(v instanceof DiscreteVariable dv)) continue;
            if (observedCategories(source, source.getColumnIndex(dv), dv.getNumCategories(), rows).size()
                    < dv.getNumCategories()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The category indices a discrete column takes over the given rows, in increasing order; missing values are
     * not categories.
     */
    private static List<Integer> observedCategories(DataSet source, int column, int numCategories,
                                                    List<Integer> rows) {
        boolean[] seen = new boolean[numCategories];
        for (int row : rows) {
            int value = source.getInt(row, column);
            if (value >= 0 && value < numCategories) seen[value] = true;
        }
        List<Integer> observed = new ArrayList<>();
        for (int i = 0; i < seen.length; i++) if (seen[i]) observed.add(i);
        return observed;
    }

    /**
     * Builds the splits of {@code source} described by {@code spec}. Empty splits are omitted; the remainder, if
     * requested and non-empty, is last. Every split has the same variables, in the source's order, minus the dropped
     * ones. Each split carries a copy of the source's knowledge.
     *
     * @param source the source data set.
     * @param spec   the spec.
     * @return the splits, at least one.
     * @throws IllegalArgumentException if no line is given, a condition is invalid, or every split is empty.
     */
    public static DataModelList split(DataSet source, Spec spec) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(spec, "spec");

        List<SplitDef> parsed = parseLines(spec.lines());
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("No split conditions given; each non-blank line defines one split.");
        }

        Expansion expansion = expand(source, parsed);
        List<SplitDef> defs = expansion.defs();
        List<List<Integer>> rows = evaluate(source, defs);

        List<String> drop = new ArrayList<>();
        if (spec.dropVarNames() != null) drop.addAll(spec.dropVarNames());
        drop.addAll(expansion.dropVarNames());

        List<Node> keptVars = keptVariables(source, drop);
        if (keptVars.isEmpty()) {
            throw new IllegalArgumentException("Every variable would be removed from the splits.");
        }
        DataSet columnSubset = source.subsetColumns(keptVars);

        Set<String> usedNames = new HashSet<>();
        DataModelList out = new DataModelList();

        for (int i = 0; i < defs.size(); i++) {
            List<Integer> r = rows.get(i);
            if (r.isEmpty()) continue;
            out.add(makeSplit(source, columnSubset, r, uniqueName(defs.get(i).name(), usedNames),
                    spec.keepAllCategories()));
        }

        if (spec.includeRemainder()) {
            boolean[] hit = new boolean[source.getNumRows()];
            for (List<Integer> r : rows) for (int row : r) hit[row] = true;

            List<Integer> rest = new ArrayList<>();
            for (int row = 0; row < hit.length; row++) if (!hit[row]) rest.add(row);

            if (!rest.isEmpty()) {
                out.add(makeSplit(source, columnSubset, rest, uniqueName(spec.effectiveRemainderName(), usedNames),
                        spec.keepAllCategories()));
            }
        }

        if (out.isEmpty()) {
            throw new IllegalArgumentException("No split matches any row of the data.");
        }

        return out;
    }

    private static DataSet makeSplit(DataSet source, DataSet columnSubset, List<Integer> rows, String name,
                                     boolean keepAllCategories) {
        DataSet split = columnSubset.subsetRows(rows);
        if (!keepAllCategories) split = trimCategories(split);
        split.setName(name);

        Knowledge knowledge = source.getKnowledge();
        if (knowledge != null && !knowledge.isEmpty()) {
            split.setKnowledge(knowledge.copy());
        }

        return split;
    }

    /**
     * Returns a copy of {@code data} in which every discrete variable with a category it never takes is replaced by
     * a new variable, of the same name, whose categories are just the observed ones in their original order, with
     * the integer codes remapped. Variables with all categories observed, and continuous variables, are kept as
     * the same objects, as is a discrete variable that is missing in every row. Missing values stay missing. If
     * nothing needs trimming, {@code data} itself is returned.
     *
     * @param data the data set.
     * @return the trimmed data set, or {@code data}.
     */
    public static DataSet trimCategories(DataSet data) {
        List<Integer> allRows = new ArrayList<>(data.getNumRows());
        for (int i = 0; i < data.getNumRows(); i++) allRows.add(i);

        List<Node> newVars = new ArrayList<>(data.getNumColumns());
        Map<Integer, int[]> remaps = new HashMap<>();

        for (int j = 0; j < data.getNumColumns(); j++) {
            Node v = data.getVariable(j);

            if (v instanceof DiscreteVariable dv) {
                List<Integer> observed = observedCategories(data, j, dv.getNumCategories(), allRows);

                // A variable missing in every row has nothing to trim to; it keeps its list.
                if (!observed.isEmpty() && observed.size() < dv.getNumCategories()) {
                    List<String> categories = new ArrayList<>();
                    int[] remap = new int[dv.getNumCategories()];
                    Arrays.fill(remap, -1);
                    for (int k = 0; k < observed.size(); k++) {
                        categories.add(dv.getCategory(observed.get(k)));
                        remap[observed.get(k)] = k;
                    }
                    DiscreteVariable trimmed = new DiscreteVariable(dv.getName(), categories);
                    trimmed.setNodeType(dv.getNodeType());
                    newVars.add(trimmed);
                    remaps.put(j, remap);
                    continue;
                }
            }

            newVars.add(v);
        }

        if (remaps.isEmpty()) return data;

        DataSet out = new BoxDataSet(new MixedDataBox(newVars, data.getNumRows()), newVars);

        for (int j = 0; j < data.getNumColumns(); j++) {
            int[] remap = remaps.get(j);
            if (newVars.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < data.getNumRows(); i++) {
                    int value = data.getInt(i, j);
                    if (remap != null && value >= 0 && value < remap.length) value = remap[value];
                    out.setInt(i, j, value);
                }
            } else {
                for (int i = 0; i < data.getNumRows(); i++) {
                    out.setDouble(i, j, data.getDouble(i, j));
                }
            }
        }

        out.setName(data.getName());
        return out;
    }

    private static String uniqueName(String base, Set<String> used) {
        String name = base;
        int k = 2;
        while (!used.add(name)) name = base + " (" + k++ + ")";
        return name;
    }

    /**
     * The result of expanding bare-variable lines: the final definitions and the variables they imply dropping.
     */
    private record Expansion(List<SplitDef> defs, List<String> dropVarNames) {
    }

    /**
     * Replaces each bare-variable line (one or more variable names, comma-separated, no operator) by one definition
     * per observed value or combination of values, named by the value(s). Other lines pass through unchanged.
     */
    private static Expansion expand(DataSet source, List<SplitDef> defs) {
        List<SplitDef> out = new ArrayList<>();
        Set<String> drop = new LinkedHashSet<>();

        for (SplitDef def : defs) {
            List<Node> variables = bareVariables(source, def.condition());

            if (variables == null) {
                out.add(def);
                continue;
            }

            List<List<String>> clausesPerVar = new ArrayList<>();
            List<List<String>> labelsPerVar = new ArrayList<>();

            for (Node v : variables) {
                List<String> clauses = v instanceof DiscreteVariable dv
                        ? discreteClauses(source, dv) : distinctValueClauses(source, v);
                clausesPerVar.add(clauses);
                labelsPerVar.add(valueLabels(source, v));
                drop.add(v.getName());
            }

            List<String> conditions = new ArrayList<>();
            product(clausesPerVar, 0, new ArrayList<>(), conditions);
            List<String> labels = new ArrayList<>();
            productLabels(labelsPerVar, 0, new ArrayList<>(), labels);

            String prefix = def.name().equals(def.condition()) ? "" : def.name() + ": ";

            for (int i = 0; i < conditions.size(); i++) {
                if (DataSubsetter.rowsSatisfying(source, conditions.get(i)).isEmpty()) continue;
                out.add(new SplitDef(prefix + labels.get(i), conditions.get(i), def.lineNumber()));
            }
        }

        return new Expansion(out, new ArrayList<>(drop));
    }

    /**
     * If {@code text} is a comma-separated list of variable names of {@code source} (quoted names allowed) and
     * contains no operator, returns those variables; otherwise null.
     */
    private static List<Node> bareVariables(DataSet source, String text) {
        for (String op : new String[]{"=", "<", ">", "!", "{", "[", "("}) {
            if (indexOfUnquoted(text, op.charAt(0)) >= 0) return null;
        }

        List<Node> variables = new ArrayList<>();

        for (String token : splitUnquoted(text, ',')) {
            String name = unquote(token.trim());
            if (name.isEmpty()) return null;
            Node v = lookUpVariable(source, name);
            if (v == null) return null;
            variables.add(v);
        }

        return variables.isEmpty() ? null : variables;
    }

    private static Node lookUpVariable(DataSet source, String name) {
        Node exact = source.getVariable(name);
        if (exact != null) return exact;

        Node found = null;
        for (Node v : source.getVariables()) {
            if (v.getName().equalsIgnoreCase(name)) {
                if (found != null) return null;   // ambiguous
                found = v;
            }
        }
        return found;
    }

    private static List<String> splitUnquoted(String s, char sep) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            if (!inQuotes && ch == sep) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }

    /**
     * The observed values of a variable as display labels, in the same order as its clauses.
     */
    private static List<String> valueLabels(DataSet source, Node v) {
        List<String> labels = new ArrayList<>();
        if (v instanceof DiscreteVariable dv) {
            int column = source.getColumnIndex(dv);
            boolean[] seen = new boolean[dv.getNumCategories()];
            for (int row = 0; row < source.getNumRows(); row++) {
                int value = source.getInt(row, column);
                if (value >= 0 && value < seen.length) seen[value] = true;
            }
            for (int i = 0; i < seen.length; i++) if (seen[i]) labels.add(dv.getCategory(i));
        } else {
            for (double x : distinctValues(source, v)) labels.add(exact(x));
        }
        return labels;
    }

    private static void productLabels(List<List<String>> labelsPerVar, int depth, List<String> prefix,
                                      List<String> out) {
        if (depth == labelsPerVar.size()) {
            out.add(String.join(" / ", prefix));
            return;
        }
        for (String label : labelsPerVar.get(depth)) {
            prefix.add(label);
            productLabels(labelsPerVar, depth + 1, prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    /**
     * One equality clause per distinct non-missing value of a continuous variable, in increasing order. Values are
     * written exactly, so that the equality test in the condition language matches them.
     */
    private static List<String> distinctValueClauses(DataSet source, Node v) {
        String name = DataSubsetter.quoteIfNeeded(v.getName());
        List<String> clauses = new ArrayList<>();
        for (double x : distinctValues(source, v)) clauses.add(name + " = " + exact(x));
        return clauses;
    }

    private static double[] distinctValues(DataSet source, Node v) {
        int column = source.getColumnIndex(v);
        double[] values = new double[source.getNumRows()];
        int n = 0;
        for (int row = 0; row < source.getNumRows(); row++) {
            double x = source.getDouble(row, column);
            if (!Double.isNaN(x)) values[n++] = x;
        }
        values = Arrays.copyOf(values, n);
        Arrays.sort(values);
        return dedupe(values);
    }

    /**
     * A value written so that parsing it back gives exactly the same double: integers as integers, everything else
     * in Java's shortest round-trip form.
     */
    static String exact(double x) {
        if (x == Math.rint(x) && Math.abs(x) < 1e15) return Long.toString((long) x);
        return Double.toString(x);
    }

    /**
     * Evaluates each definition's condition; on failure, rethrows with the 1-based line number of the offending
     * line prepended.
     */
    private static List<List<Integer>> evaluate(DataSet source, List<SplitDef> defs) {
        List<List<Integer>> rows = new ArrayList<>(defs.size());

        for (SplitDef def : defs) {
            try {
                rows.add(DataSubsetter.rowsSatisfying(source, def.condition()));
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                if (message != null && message.startsWith("No comparison found")) {
                    message = "\"" + def.condition() + "\" is neither a condition (like A = cat1 or X < 5) "
                            + "nor the name of a variable in this data set.";
                }
                throw new IllegalArgumentException("Line " + def.lineNumber() + ": " + message, e);
            }
        }

        return rows;
    }

    /**
     * The source's variables, in order, minus those named in {@code dropNames}. Names that do not exist in the
     * source are ignored: the user's intent - that the variable not be in the splits - already holds.
     *
     * @param source    the source data set.
     * @param dropNames the names to remove; may be null.
     * @return the kept variables.
     */
    public static List<Node> keptVariables(DataSet source, List<String> dropNames) {
        Set<String> drop = new HashSet<>();
        if (dropNames != null) for (String n : dropNames) if (n != null) drop.add(n.trim());

        List<Node> kept = new ArrayList<>();
        for (Node v : source.getVariables()) {
            if (!drop.contains(v.getName())) kept.add(v);
        }
        return kept;
    }

    // ------------------------------------------------------------------------
    // Generating lines
    // ------------------------------------------------------------------------

    /**
     * How a continuous variable is cut into bins by {@link #generateLines}.
     */
    public enum BinMode {
        /**
         * Bins holding (as nearly as ties allow) equal numbers of rows; boundaries are sample quantiles.
         */
        EQUAL_COUNT,
        /**
         * Bins of equal width between the observed minimum and maximum.
         */
        EQUAL_WIDTH,
        /**
         * Bins between user-supplied cut points.
         */
        CUT_POINTS
    }

    /**
     * Generates split lines for the given variables: one line per observed category of a discrete variable, one per
     * bin of a continuous variable, and for several variables one per combination, joined with {@code and}. A
     * continuous variable with cut points {@code c1 < ... < cm} yields the bins {@code X < c1}, {@code X in [c1,
     * c2)}, ..., {@code X >= cm}. Combinations matching no row are left out; a variable with fewer than two
     * distinct non-missing values is skipped since it cannot split anything.
     *
     * @param source    the source data set.
     * @param variables the variables to split by, in the order the conditions should appear.
     * @param mode      how continuous variables are binned.
     * @param numBins   the number of bins for {@link BinMode#EQUAL_COUNT} and {@link BinMode#EQUAL_WIDTH}; at least 2.
     * @param cutPoints the cut points for {@link BinMode#CUT_POINTS}; applied to every continuous variable given.
     * @return the lines; empty if no variable can split the data.
     * @throws IllegalArgumentException if the arguments are inconsistent with the mode.
     */
    public static List<String> generateLines(DataSet source, List<Node> variables, BinMode mode, int numBins,
                                             double[] cutPoints) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(variables, "variables");
        Objects.requireNonNull(mode, "mode");

        List<List<String>> clausesPerVar = new ArrayList<>();

        for (Node v : variables) {
            List<String> clauses = v instanceof DiscreteVariable dv
                    ? discreteClauses(source, dv)
                    : continuousClauses(source, v, mode, numBins, cutPoints);
            if (clauses.size() >= 2) clausesPerVar.add(clauses);
        }

        if (clausesPerVar.isEmpty()) return new ArrayList<>();

        List<String> lines = new ArrayList<>();
        product(clausesPerVar, 0, new ArrayList<>(), lines);

        // Drop combinations no row satisfies (only possible with two or more variables, or with equal-width bins).
        List<String> nonEmpty = new ArrayList<>();
        for (String line : lines) {
            if (!DataSubsetter.rowsSatisfying(source, line).isEmpty()) nonEmpty.add(line);
        }

        return nonEmpty;
    }

    private static void product(List<List<String>> clausesPerVar, int depth, List<String> prefix,
                                List<String> out) {
        if (depth == clausesPerVar.size()) {
            out.add(String.join(" and ", prefix));
            return;
        }
        for (String clause : clausesPerVar.get(depth)) {
            prefix.add(clause);
            product(clausesPerVar, depth + 1, prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    private static List<String> discreteClauses(DataSet source, DiscreteVariable v) {
        int column = source.getColumnIndex(v);
        boolean[] seen = new boolean[v.getNumCategories()];

        for (int row = 0; row < source.getNumRows(); row++) {
            int value = source.getInt(row, column);
            if (value >= 0 && value < seen.length) seen[value] = true;
        }

        String name = DataSubsetter.quoteIfNeeded(v.getName());
        List<String> clauses = new ArrayList<>();

        for (int i = 0; i < seen.length; i++) {
            if (seen[i]) clauses.add(name + " = " + DataSubsetter.quoteIfNeeded(v.getCategory(i)));
        }

        return clauses;
    }

    private static List<String> continuousClauses(DataSet source, Node v, BinMode mode, int numBins,
                                                  double[] cutPoints) {
        int column = source.getColumnIndex(v);
        double[] values = new double[source.getNumRows()];
        int n = 0;

        for (int row = 0; row < source.getNumRows(); row++) {
            double x = source.getDouble(row, column);
            if (!Double.isNaN(x)) values[n++] = x;
        }

        if (n == 0) return new ArrayList<>();
        values = Arrays.copyOf(values, n);
        Arrays.sort(values);
        if (values[0] == values[n - 1]) return new ArrayList<>();   // constant: nothing to split on

        double[] cuts = switch (mode) {
            case EQUAL_COUNT -> equalCountCuts(values, numBins);
            case EQUAL_WIDTH -> equalWidthCuts(values, numBins);
            case CUT_POINTS -> {
                if (cutPoints == null || cutPoints.length == 0) {
                    throw new IllegalArgumentException("Cut points mode needs at least one cut point.");
                }
                double[] c = cutPoints.clone();
                Arrays.sort(c);
                yield dedupe(c);
            }
        };

        if (cuts.length == 0) return new ArrayList<>();

        String name = DataSubsetter.quoteIfNeeded(v.getName());
        List<String> clauses = new ArrayList<>();

        clauses.add(name + " < " + fmt(cuts[0]));
        for (int i = 0; i + 1 < cuts.length; i++) {
            clauses.add(name + " in [" + fmt(cuts[i]) + ", " + fmt(cuts[i + 1]) + ")");
        }
        clauses.add(name + " >= " + fmt(cuts[cuts.length - 1]));

        return clauses;
    }

    /**
     * Interior sample quantiles at i / numBins for i = 1..numBins-1, with duplicates (from ties) removed, as are
     * cuts equal to the minimum. Each boundary is an observed value, so the bin {@code [c_i, c_{i+1})} starts on a
     * data point. With heavy ties there may be fewer bins than asked for.
     */
    static double[] equalCountCuts(double[] sorted, int numBins) {
        if (numBins < 2) throw new IllegalArgumentException("Number of bins must be at least 2.");
        List<Double> cuts = new ArrayList<>();
        for (int i = 1; i < numBins; i++) {
            int index = (int) Math.floor((double) i * sorted.length / numBins);
            double c = sorted[Math.min(index, sorted.length - 1)];
            // A cut at the minimum would make the lowest bin (X < min) empty; ties can push a quantile there.
            if (c > sorted[0]) cuts.add(c);
        }
        double[] array = new double[cuts.size()];
        for (int i = 0; i < array.length; i++) array[i] = cuts.get(i);
        return dedupe(array);
    }

    /**
     * Interior boundaries dividing [min, max] into numBins equal-width intervals.
     */
    static double[] equalWidthCuts(double[] sorted, int numBins) {
        if (numBins < 2) throw new IllegalArgumentException("Number of bins must be at least 2.");
        double min = sorted[0];
        double max = sorted[sorted.length - 1];
        if (max <= min) return new double[0];
        double[] cuts = new double[numBins - 1];
        for (int i = 1; i < numBins; i++) {
            cuts[i - 1] = min + i * (max - min) / numBins;
        }
        return dedupe(cuts);
    }

    private static double[] dedupe(double[] sorted) {
        List<Double> out = new ArrayList<>();
        for (double c : sorted) {
            if (out.isEmpty() || out.get(out.size() - 1) != c) out.add(c);
        }
        double[] result = new double[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    /**
     * Formats a boundary for a condition line: integers as integers, otherwise up to six significant digits with
     * trailing zeros removed. The formatted values are what the split uses from then on, so adjacent bins always
     * share exactly the same boundary text.
     *
     * @param x the value.
     * @return the text.
     */
    public static String fmt(double x) {
        if (x == Math.rint(x) && Math.abs(x) < 1e15) return Long.toString((long) x);

        String s = String.format(Locale.ROOT, "%.6g", x);
        int e = s.indexOf('e');
        String mantissa = e < 0 ? s : s.substring(0, e);
        String exponent = e < 0 ? "" : s.substring(e);

        if (mantissa.contains(".")) {
            mantissa = mantissa.replaceAll("0+$", "");
            if (mantissa.endsWith(".")) mantissa = mantissa.substring(0, mantissa.length() - 1);
        }

        return mantissa + exponent;
    }
}
