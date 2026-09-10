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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

import java.text.NumberFormat;
import java.util.*;

/**
 * Reshapes a long-format data set (one row per unit per occasion, e.g., one row per student per assessment) into a
 * wide-format data set (one row per unit, one column per occasion per measured variable). This is the reshaping a
 * causal search needs when the rows of the file are not the units of analysis: searching on the long table would
 * treat each unit's repeated rows as independent cases.
 * <p>
 * The transform is specified by:
 * <ul>
 *     <li><b>row keys</b>: one or more variables whose joint values identify a unit; each distinct combination
 *     becomes one output row, in order of first appearance;</li>
 *     <li><b>column key</b>: one discrete variable whose levels become column suffixes;</li>
 *     <li><b>value variables</b>: the variables to spread, by default every variable that is neither a row key nor
 *     the column key. Each value variable {@code v} yields one output column {@code v.level} per level of the column
 *     key, of the same type (and, for discrete variables, the same categories) as {@code v}.</li>
 * </ul>
 * A (unit, level) combination absent from the long data becomes a missing value in the wide data. A combination
 * present more than once (a second attempt, say) is resolved by the {@link Aggregation} policy, which defaults to
 * {@link Aggregation#FAIL} so that duplicates are never silently collapsed.
 * <p>
 * The result carries a findings-only {@link Result#report()} (units, levels, per-column fill rates, duplicates
 * resolved) in the manner of the data audit; what to do about sparse columns is a decision for the user.
 */
public final class LongToWide {

    /**
     * How multiple long rows for the same (unit, level) combination are combined into one wide cell.
     */
    public enum Aggregation {
        /**
         * Throw an exception naming the number of duplicated combinations. The default.
         */
        FAIL,
        /**
         * Keep the first row in file order.
         */
        FIRST,
        /**
         * Keep the last row in file order.
         */
        LAST,
        /**
         * The mean of the values (continuous only; missing values ignored).
         */
        MEAN,
        /**
         * The minimum of the values (continuous only; missing values ignored).
         */
        MIN,
        /**
         * The maximum of the values (continuous only; missing values ignored).
         */
        MAX,
        /**
         * The sum of the values (continuous only; missing values ignored).
         */
        SUM,
        /**
         * The number of long rows for the combination, as a continuous column (the value variable's own values are
         * ignored; zero rows gives 0, not missing).
         */
        COUNT
    }

    private final List<String> rowKeys = new ArrayList<>();
    private String columnKey;
    private final List<String> valueVariables = new ArrayList<>();
    private final Map<String, Aggregation> aggregationByVariable = new HashMap<>();
    private Aggregation defaultAggregation = Aggregation.FAIL;
    private boolean sanitizeNames = true;
    private boolean keepRowKeys = false;
    private String separator = ".";

    /**
     * Constructs a transform with the given row key(s) and column key. Value variables default to every other
     * variable of the data set; see {@link #setValueVariables(List)}.
     *
     * @param columnKey The name of the discrete variable whose levels become column suffixes.
     * @param rowKeys   The names of the variable(s) identifying a unit.
     */
    public LongToWide(String columnKey, String... rowKeys) {
        if (columnKey == null) throw new NullPointerException("columnKey == null");
        if (rowKeys == null || rowKeys.length == 0) throw new IllegalArgumentException("At least one row key is required.");
        this.columnKey = columnKey;
        this.rowKeys.addAll(Arrays.asList(rowKeys));
    }

    /**
     * Sets the value variables to spread. An empty list (the default) means every variable that is not a row key
     * or the column key.
     *
     * @param names The variable names.
     * @return This transform.
     */
    public LongToWide setValueVariables(List<String> names) {
        this.valueVariables.clear();
        if (names != null) this.valueVariables.addAll(names);
        return this;
    }

    /**
     * Sets the aggregation used for a value variable whose (unit, level) combinations are duplicated.
     *
     * @param variable    The value variable.
     * @param aggregation The aggregation.
     * @return This transform.
     */
    public LongToWide setAggregation(String variable, Aggregation aggregation) {
        this.aggregationByVariable.put(variable, aggregation);
        return this;
    }

    /**
     * Sets the aggregation used for value variables not given one by {@link #setAggregation(String, Aggregation)}.
     * The default is FAIL.
     *
     * @param aggregation The aggregation.
     * @return This transform.
     */
    public LongToWide setDefaultAggregation(Aggregation aggregation) {
        this.defaultAggregation = aggregation;
        return this;
    }

    /**
     * If true (the default), output column names have runs of characters other than letters, digits, underscore,
     * period, and hyphen replaced by a single underscore, so that they survive whitespace-delimited contexts such as
     * knowledge files.
     *
     * @param sanitizeNames Whether to sanitize.
     * @return This transform.
     */
    public LongToWide setSanitizeNames(boolean sanitizeNames) {
        this.sanitizeNames = sanitizeNames;
        return this;
    }

    /**
     * If true, the row key variable(s) are kept as the leading column(s) of the wide data set (as discrete
     * variables, one category per observed value). The default is false, since a unit identifier has one category
     * per row and is of no use to a search; the keys are always available from {@link Result#unitKeys()}.
     *
     * @param keepRowKeys Whether to keep the row keys.
     * @return This transform.
     */
    public LongToWide setKeepRowKeys(boolean keepRowKeys) {
        this.keepRowKeys = keepRowKeys;
        return this;
    }

    /**
     * Sets the separator between a value variable's name and the level suffix. The default is ".".
     *
     * @param separator The separator.
     * @return This transform.
     */
    public LongToWide setSeparator(String separator) {
        this.separator = separator;
        return this;
    }

    /**
     * Applies the transform.
     *
     * @param data The long-format data set.
     * @return The wide data set with its report.
     * @throws IllegalArgumentException If a named variable is absent, the column key is not discrete, or duplicated
     *                                  combinations exist under the FAIL aggregation.
     */
    public Result apply(DataSet data) {
        if (data == null) throw new NullPointerException("data == null");

        // ---- Resolve variables ----
        List<Integer> keyCols = new ArrayList<>();
        for (String k : this.rowKeys) keyCols.add(column(data, k));

        int colKeyIdx = column(data, this.columnKey);
        if (!(data.getVariable(colKeyIdx) instanceof DiscreteVariable colKeyVar)) {
            throw new IllegalArgumentException("The column key '" + this.columnKey + "' must be a discrete variable "
                    + "(its levels become the column suffixes); found " + data.getVariable(colKeyIdx).getClass().getSimpleName() + ".");
        }

        List<Integer> valueCols = new ArrayList<>();
        if (this.valueVariables.isEmpty()) {
            for (int j = 0; j < data.getNumColumns(); j++) {
                if (j == colKeyIdx || keyCols.contains(j)) continue;
                valueCols.add(j);
            }
        } else {
            for (String v : this.valueVariables) {
                int j = column(data, v);
                if (j == colKeyIdx || keyCols.contains(j)) {
                    throw new IllegalArgumentException("Value variable '" + v + "' is also a key.");
                }
                valueCols.add(j);
            }
        }

        // ---- Units (row keys, in order of first appearance) and levels (in category order, observed only) ----
        int n = data.getNumRows();
        Map<List<String>, Integer> unitIndex = new LinkedHashMap<>();
        int[] unitOfRow = new int[n];

        for (int i = 0; i < n; i++) {
            List<String> key = new ArrayList<>(keyCols.size());
            for (int c : keyCols) key.add(cellAsString(data, i, c));
            unitOfRow[i] = unitIndex.computeIfAbsent(key, k -> unitIndex.size());
        }

        int numUnits = unitIndex.size();
        boolean[] levelObserved = new boolean[colKeyVar.getNumCategories()];
        int[] levelOfRow = new int[n];

        for (int i = 0; i < n; i++) {
            int level = data.getInt(i, colKeyIdx);
            levelOfRow[i] = level;
            if (level >= 0 && level < levelObserved.length) levelObserved[level] = true;
        }

        List<Integer> levels = new ArrayList<>();
        for (int l = 0; l < levelObserved.length; l++) if (levelObserved[l]) levels.add(l);

        // ---- Group long rows by (unit, level) ----
        Map<Long, List<Integer>> rowsByCell = new HashMap<>();
        int numMissingLevel = 0;

        for (int i = 0; i < n; i++) {
            if (levelOfRow[i] < 0) {
                numMissingLevel++;
                continue;
            }
            long cell = ((long) unitOfRow[i] << 32) | (levelOfRow[i] & 0xffffffffL);
            rowsByCell.computeIfAbsent(cell, k -> new ArrayList<>()).add(i);
        }

        int numDuplicatedCells = 0;
        for (List<Integer> rows : rowsByCell.values()) if (rows.size() > 1) numDuplicatedCells++;

        // ---- Build output variables ----
        List<Node> outVars = new ArrayList<>();
        List<int[]> outSpec = new ArrayList<>(); // {valueCol, level} per output column, or {-1, keyIndex} for kept keys
        Set<String> usedNames = new HashSet<>();

        if (this.keepRowKeys) {
            for (int k = 0; k < keyCols.size(); k++) {
                List<String> cats = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (List<String> key : unitIndex.keySet()) {
                    String s = key.get(k);
                    if (seen.add(s)) cats.add(s);
                }
                String name = uniqueName(usedNames, data.getVariable(keyCols.get(k)).getName());
                outVars.add(new DiscreteVariable(name, cats));
                outSpec.add(new int[]{-1, k});
            }
        }

        for (int vc : valueCols) {
            Node v = data.getVariable(vc);
            Aggregation agg = aggregationFor(v.getName());

            if (v instanceof DiscreteVariable && !(agg == Aggregation.FAIL || agg == Aggregation.FIRST
                    || agg == Aggregation.LAST || agg == Aggregation.COUNT)) {
                throw new IllegalArgumentException("Aggregation " + agg + " is not defined for the discrete value "
                        + "variable '" + v.getName() + "'; use FIRST, LAST, or COUNT.");
            }

            for (int level : levels) {
                String name = uniqueName(usedNames, v.getName() + this.separator + colKeyVar.getCategory(level));

                if (agg == Aggregation.COUNT || v instanceof ContinuousVariable) {
                    outVars.add(new ContinuousVariable(name));
                } else {
                    DiscreteVariable dv = (DiscreteVariable) v;
                    List<String> cats = new ArrayList<>();
                    for (int c = 0; c < dv.getNumCategories(); c++) cats.add(dv.getCategory(c));
                    outVars.add(new DiscreteVariable(name, cats));
                }

                outSpec.add(new int[]{vc, level});
            }
        }

        // ---- Fill ----
        DataSet wide = new BoxDataSet(new MixedDataBox(outVars, numUnits), outVars);
        int[] filled = new int[outVars.size()];
        List<List<String>> unitKeys = new ArrayList<>(unitIndex.keySet());

        for (int u = 0; u < numUnits; u++) {
            for (int j = 0; j < outVars.size(); j++) {
                int[] spec = outSpec.get(j);

                if (spec[0] < 0) {
                    String s = unitKeys.get(u).get(spec[1]);
                    wide.setInt(u, j, ((DiscreteVariable) outVars.get(j)).getIndex(s));
                    filled[j]++;
                    continue;
                }

                int vc = spec[0];
                int level = spec[1];
                Node v = data.getVariable(vc);
                Aggregation agg = aggregationFor(v.getName());
                List<Integer> rows = rowsByCell.get(((long) u << 32) | (level & 0xffffffffL));

                if (agg == Aggregation.COUNT) {
                    wide.setDouble(u, j, rows == null ? 0.0 : rows.size());
                    filled[j]++;
                    continue;
                }

                if (rows == null) {
                    setMissing(wide, u, j, outVars.get(j));
                    continue;
                }

                if (rows.size() > 1 && agg == Aggregation.FAIL) {
                    throw new IllegalArgumentException("Long data has " + numDuplicatedCells + " (unit, "
                            + this.columnKey + ") combinations with more than one row (e.g., unit "
                            + unitKeys.get(u) + " at level '" + colKeyVar.getCategory(level) + "' has "
                            + rows.size() + " rows). Choose an aggregation (FIRST, LAST, MEAN, MIN, MAX, SUM, or "
                            + "COUNT) to resolve them.");
                }

                if (v instanceof ContinuousVariable) {
                    double value = aggregateContinuous(data, rows, vc, agg);
                    wide.setDouble(u, j, value);
                    if (!Double.isNaN(value)) filled[j]++;
                } else {
                    int row = agg == Aggregation.LAST ? rows.get(rows.size() - 1) : rows.get(0);
                    int value = data.getInt(row, vc);
                    wide.setInt(u, j, value);
                    if (value != DiscreteVariable.MISSING_VALUE) filled[j]++;
                }
            }
        }

        wide.setName((data.getName() == null ? "data" : data.getName()) + " (wide by " + this.columnKey + ")");

        // ---- Report ----
        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);
        StringBuilder r = new StringBuilder();
        r.append("Long-to-wide: ").append(n).append(" long rows -> ").append(numUnits).append(" units (row key")
                .append(this.rowKeys.size() > 1 ? "s " : " ").append(this.rowKeys).append(") x ")
                .append(levels.size()).append(" levels of ").append(this.columnKey)
                .append(" x ").append(valueCols.size()).append(" value variable(s) = ")
                .append(outVars.size()).append(" columns.\n");
        if (numMissingLevel > 0) {
            r.append("  ").append(numMissingLevel).append(" long rows had a missing ").append(this.columnKey)
                    .append(" and were dropped.\n");
        }
        if (levels.size() < levelObserved.length) {
            r.append("  ").append(levelObserved.length - levels.size()).append(" declared level(s) of ")
                    .append(this.columnKey).append(" never occur and produce no columns.\n");
        }
        r.append("  Duplicated (unit, level) combinations: ").append(numDuplicatedCells)
                .append(numDuplicatedCells > 0 ? " (resolved by aggregation)" : "").append(".\n");
        r.append("  Fill rate per column (fraction of units with a value):\n");
        for (int j = 0; j < outVars.size(); j++) {
            r.append("    ").append(outVars.get(j).getName()).append(": ")
                    .append(pct.format(filled[j] / (double) numUnits)).append(" (").append(filled[j]).append("/")
                    .append(numUnits).append(")\n");
        }

        return new Result(wide, unitKeys, r.toString());
    }

    private Aggregation aggregationFor(String variable) {
        return this.aggregationByVariable.getOrDefault(variable, this.defaultAggregation);
    }

    private static double aggregateContinuous(DataSet data, List<Integer> rows, int col, Aggregation agg) {
        if (rows.size() == 1 || agg == Aggregation.FIRST) return data.getDouble(rows.get(0), col);
        if (agg == Aggregation.LAST) return data.getDouble(rows.get(rows.size() - 1), col);

        double acc = switch (agg) {
            case MIN -> Double.POSITIVE_INFINITY;
            case MAX -> Double.NEGATIVE_INFINITY;
            default -> 0.0;
        };
        int count = 0;

        for (int row : rows) {
            double v = data.getDouble(row, col);
            if (Double.isNaN(v)) continue;
            count++;
            acc = switch (agg) {
                case MIN -> Math.min(acc, v);
                case MAX -> Math.max(acc, v);
                default -> acc + v;
            };
        }

        if (count == 0) return Double.NaN;
        return agg == Aggregation.MEAN ? acc / count : acc;
    }

    private static void setMissing(DataSet wide, int row, int col, Node var) {
        if (var instanceof DiscreteVariable) {
            wide.setInt(row, col, DiscreteVariable.MISSING_VALUE);
        } else {
            wide.setDouble(row, col, Double.NaN);
        }
    }

    private String uniqueName(Set<String> used, String raw) {
        String name = this.sanitizeNames ? raw.trim().replaceAll("[^A-Za-z0-9_.\\-]+", "_") : raw;
        String candidate = name;
        int k = 2;
        while (!used.add(candidate)) candidate = name + "_" + (k++);
        return candidate;
    }

    private static int column(DataSet data, String name) {
        Node v = data.getVariable(name);
        if (v == null) throw new IllegalArgumentException("No variable named '" + name + "' in the data.");
        return data.getColumnIndex(v);
    }

    private static String cellAsString(DataSet data, int row, int col) {
        Node v = data.getVariable(col);
        if (v instanceof DiscreteVariable dv) {
            int idx = data.getInt(row, col);
            return idx == DiscreteVariable.MISSING_VALUE ? "*" : dv.getCategory(idx);
        }
        double d = data.getDouble(row, col);
        return Double.isNaN(d) ? "*" : Double.toString(d);
    }

    /**
     * The wide data set, the unit keys in row order (one list of row-key values per wide row), and a findings-only
     * report.
     *
     * @param wide     The wide data set.
     * @param unitKeys The row-key values of each wide row, in order.
     * @param report   The report.
     */
    public record Result(DataSet wide, List<List<String>> unitKeys, String report) {
    }
}
