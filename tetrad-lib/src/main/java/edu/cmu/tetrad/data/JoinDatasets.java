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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;

import java.text.NumberFormat;
import java.util.*;

/**
 * Joins two tabular data sets on a key, in the manner of a database join: each row of the left data set is looked
 * up in the right data set by its key value(s), and the right data set's non-key columns are appended to it. This
 * is the operation needed to attach, say, a table of per-student background variables to a wide table with one row
 * per student, when the two tables share an (anonymized) student identifier.
 * <p>
 * The join is specified by:
 * <ul>
 *     <li><b>left keys</b> and <b>right keys</b>: one or more variables on each side, paired by position (the
 *     first left key matches the first right key, and so on), so the names need not agree and composite keys such
 *     as student-plus-course are allowed;</li>
 *     <li>a {@link JoinType}: {@link JoinType#LEFT} (the default) keeps every left row, filling the right columns
 *     with missing values where there is no match; {@link JoinType#INNER} keeps only matched rows;
 *     {@link JoinType#FULL_OUTER} additionally appends the unmatched right rows at the end, with the left columns
 *     missing;</li>
 *     <li>whether a key may repeat on the right ({@link #setAllowOneToMany(boolean)}, off by default). When a
 *     right key repeats, each matching left row is emitted once per right row, which multiplies rows and breaks
 *     the independence of cases; so by default this is reported as an error rather than done silently. Repeated
 *     keys on the left are the ordinary many-to-one lookup and are always allowed.</li>
 * </ul>
 * Key values are matched by their string representation, not by variable type, so an identifier loaded as a
 * discrete variable in one file and as an integer-valued continuous variable in the other still matches: numeric
 * strings are canonicalized so that "1234" and 1234.0 agree. A row whose key is missing (in any component) never
 * matches anything, on either side, as in SQL.
 * <p>
 * The key is emitted once, as the left data set's key variable(s). Non-key columns of the two tables that share a
 * name are both renamed with suffixes ({@link #setLeftSuffix(String)}, {@link #setRightSuffix(String)}; default
 * "_left" and "_right"). Left row order is preserved; unmatched right rows, if kept, follow in right row order.
 * <p>
 * The result carries a findings-only {@link Result#report()} (row and key counts, match rates, missing keys,
 * repeated keys, renamed columns) in the manner of the data audit.
 */
public final class JoinDatasets {

    /**
     * Which unmatched rows to keep.
     */
    public enum JoinType {
        /**
         * Keep every left row; unmatched left rows get missing values in the right columns. Unmatched right rows
         * are discarded. The default.
         */
        LEFT,
        /**
         * Keep only rows with a match on both sides.
         */
        INNER,
        /**
         * Keep every row of both sides; unmatched rows get missing values in the other side's columns.
         */
        FULL_OUTER
    }

    private final List<String> leftKeys;
    private final List<String> rightKeys;
    private JoinType joinType = JoinType.LEFT;
    private boolean allowOneToMany = false;
    private String leftSuffix = "_left";
    private String rightSuffix = "_right";

    /**
     * Constructs a join on the given keys, paired by position.
     *
     * @param leftKeys  The key variable names in the left data set.
     * @param rightKeys The key variable names in the right data set, the same number as the left keys.
     */
    public JoinDatasets(List<String> leftKeys, List<String> rightKeys) {
        if (leftKeys == null || rightKeys == null) throw new NullPointerException("Key lists must not be null.");
        if (leftKeys.isEmpty()) throw new IllegalArgumentException("At least one key variable is required.");
        if (leftKeys.size() != rightKeys.size()) {
            throw new IllegalArgumentException("The left and right key lists must have the same length; got "
                    + leftKeys.size() + " and " + rightKeys.size() + ".");
        }
        if (new HashSet<>(leftKeys).size() != leftKeys.size()) {
            throw new IllegalArgumentException("A left key variable is listed twice: " + leftKeys);
        }
        if (new HashSet<>(rightKeys).size() != rightKeys.size()) {
            throw new IllegalArgumentException("A right key variable is listed twice: " + rightKeys);
        }
        this.leftKeys = List.copyOf(leftKeys);
        this.rightKeys = List.copyOf(rightKeys);
    }

    /**
     * Constructs a join on a single key with the same name on both sides.
     *
     * @param key The key variable name.
     */
    public JoinDatasets(String key) {
        this(List.of(key), List.of(key));
    }

    /**
     * Sets the join type (default {@link JoinType#LEFT}).
     *
     * @param joinType The join type.
     * @return This, for chaining.
     */
    public JoinDatasets setJoinType(JoinType joinType) {
        this.joinType = Objects.requireNonNull(joinType);
        return this;
    }

    /**
     * Sets whether a key value may occur in more than one right row (default false). If false, a repeated right
     * key is an error; if true, each matching left row is emitted once per right row.
     *
     * @param allowOneToMany Whether to allow repeated right keys.
     * @return This, for chaining.
     */
    public JoinDatasets setAllowOneToMany(boolean allowOneToMany) {
        this.allowOneToMany = allowOneToMany;
        return this;
    }

    /**
     * Sets the suffix appended to a left non-key column whose name collides with a right non-key column (default
     * "_left").
     *
     * @param leftSuffix The suffix.
     * @return This, for chaining.
     */
    public JoinDatasets setLeftSuffix(String leftSuffix) {
        this.leftSuffix = leftSuffix == null ? "" : leftSuffix;
        return this;
    }

    /**
     * Sets the suffix appended to a right non-key column whose name collides with a left column (default
     * "_right").
     *
     * @param rightSuffix The suffix.
     * @return This, for chaining.
     */
    public JoinDatasets setRightSuffix(String rightSuffix) {
        this.rightSuffix = rightSuffix == null ? "" : rightSuffix;
        return this;
    }

    /**
     * Applies the join.
     *
     * @param left  The left (primary) data set.
     * @param right The right (lookup) data set.
     * @return The joined data set with its report.
     * @throws IllegalArgumentException If a key variable is absent, a column is of an unsupported type, or a right
     *                                  key repeats and one-to-many joins are not allowed.
     */
    public Result apply(DataSet left, DataSet right) {
        if (left == null) throw new NullPointerException("left == null");
        if (right == null) throw new NullPointerException("right == null");

        // ---- Resolve key columns ----
        int k = this.leftKeys.size();
        int[] leftKeyCols = new int[k];
        int[] rightKeyCols = new int[k];

        for (int i = 0; i < k; i++) {
            leftKeyCols[i] = column(left, this.leftKeys.get(i), "left");
            rightKeyCols[i] = column(right, this.rightKeys.get(i), "right");
        }

        for (Node v : left.getVariables()) checkSupported(v, "left");
        for (Node v : right.getVariables()) checkSupported(v, "right");

        // ---- Key tuples ----
        int nLeft = left.getNumRows();
        int nRight = right.getNumRows();

        List<List<String>> leftTuples = new ArrayList<>(nLeft);
        int leftMissingKey = 0;
        for (int i = 0; i < nLeft; i++) {
            List<String> t = keyTuple(left, i, leftKeyCols);
            if (t == null) leftMissingKey++;
            leftTuples.add(t);
        }

        List<List<String>> rightTuples = new ArrayList<>(nRight);
        Map<List<String>, List<Integer>> rightIndex = new LinkedHashMap<>();
        int rightMissingKey = 0;
        for (int j = 0; j < nRight; j++) {
            List<String> t = keyTuple(right, j, rightKeyCols);
            rightTuples.add(t);
            if (t == null) {
                rightMissingKey++;
            } else {
                rightIndex.computeIfAbsent(t, x -> new ArrayList<>()).add(j);
            }
        }

        int rightRepeatedKeys = 0;
        int rightExtraRows = 0;
        for (List<Integer> rows : rightIndex.values()) {
            if (rows.size() > 1) {
                rightRepeatedKeys++;
                rightExtraRows += rows.size() - 1;
            }
        }

        if (rightRepeatedKeys > 0 && !this.allowOneToMany) {
            throw new IllegalArgumentException("The right data set has " + rightRepeatedKeys + " key value"
                    + (rightRepeatedKeys == 1 ? "" : "s") + " occurring in more than one row (" + rightExtraRows
                    + " extra row" + (rightExtraRows == 1 ? "" : "s") + " in all). A left join would duplicate "
                    + "the matching left rows once per repeat, so the joined cases would no longer be "
                    + "independent. Remove the repeats, or allow one-to-many joins if the duplication is intended.");
        }

        Set<List<String>> leftDistinct = new HashSet<>();
        for (List<String> t : leftTuples) if (t != null) leftDistinct.add(t);
        int leftRepeatedKeys = 0;
        {
            Map<List<String>, Integer> counts = new HashMap<>();
            for (List<String> t : leftTuples) if (t != null) counts.merge(t, 1, Integer::sum);
            for (int c : counts.values()) if (c > 1) leftRepeatedKeys++;
        }

        // ---- Row pairing (left row index or -1, right row index or -1) ----
        List<int[]> pairs = new ArrayList<>();
        boolean[] rightMatched = new boolean[nRight];
        int leftMatchedRows = 0;
        int leftUnmatchedRows = 0;

        for (int i = 0; i < nLeft; i++) {
            List<String> t = leftTuples.get(i);
            List<Integer> matches = t == null ? null : rightIndex.get(t);

            if (matches == null) {
                leftUnmatchedRows++;
                if (this.joinType != JoinType.INNER) pairs.add(new int[]{i, -1});
            } else {
                leftMatchedRows++;
                for (int j : matches) {
                    pairs.add(new int[]{i, j});
                    rightMatched[j] = true;
                }
            }
        }

        int rightUnmatchedRows = 0;
        for (int j = 0; j < nRight; j++) if (!rightMatched[j]) rightUnmatchedRows++;

        boolean rightOnlyRowsEmitted = false;
        if (this.joinType == JoinType.FULL_OUTER) {
            for (int j = 0; j < nRight; j++) {
                if (!rightMatched[j]) {
                    pairs.add(new int[]{-1, j});
                    rightOnlyRowsEmitted = true;
                }
            }
        }

        int nOut = pairs.size();

        // ---- Output variables ----
        List<Node> leftVars = left.getVariables();
        List<Node> rightVars = right.getVariables();
        Set<Integer> leftKeySet = new HashSet<>();
        for (int c : leftKeyCols) leftKeySet.add(c);
        Set<Integer> rightKeySet = new HashSet<>();
        for (int c : rightKeyCols) rightKeySet.add(c);

        // Names, with collision suffixes.
        String[] leftNames = new String[leftVars.size()];
        for (int c = 0; c < leftVars.size(); c++) leftNames[c] = leftVars.get(c).getName();
        List<Integer> rightOutCols = new ArrayList<>();
        for (int c = 0; c < rightVars.size(); c++) if (!rightKeySet.contains(c)) rightOutCols.add(c);
        String[] rightNames = new String[rightVars.size()];
        for (int c : rightOutCols) rightNames[c] = rightVars.get(c).getName();

        Map<String, Integer> leftNameToCol = new HashMap<>();
        for (int c = 0; c < leftVars.size(); c++) leftNameToCol.put(leftNames[c], c);

        List<String> renamed = new ArrayList<>();
        for (int c : rightOutCols) {
            String name = rightNames[c];
            Integer lc = leftNameToCol.get(name);
            if (lc == null) continue;

            if (leftKeySet.contains(lc)) {
                rightNames[c] = name + this.rightSuffix;
                renamed.add(name + " (right) -> " + rightNames[c]);
            } else {
                leftNames[lc] = name + this.leftSuffix;
                rightNames[c] = name + this.rightSuffix;
                renamed.add(name + " -> " + leftNames[lc] + ", " + rightNames[c]);
            }
        }

        Set<String> used = new HashSet<>();
        for (int c = 0; c < leftVars.size(); c++) leftNames[c] = uniqueName(used, leftNames[c]);
        for (int c : rightOutCols) rightNames[c] = uniqueName(used, rightNames[c]);

        // Key columns: possibly widened to hold right-only rows' key values.
        KeyColumn[] keyColumns = new KeyColumn[k];
        List<String> keyNotes = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            keyColumns[i] = keyColumn(left, leftKeyCols[i], leftNames[leftKeyCols[i]], right, rightKeyCols[i],
                    pairs, rightOnlyRowsEmitted, keyNotes);
        }

        List<Node> outVars = new ArrayList<>();
        int[] leftOutIndex = new int[leftVars.size()];
        for (int c = 0; c < leftVars.size(); c++) {
            int ki = indexOf(leftKeyCols, c);
            Node v = ki >= 0 ? keyColumns[ki].variable : copyVariable(leftVars.get(c), leftNames[c]);
            leftOutIndex[c] = outVars.size();
            outVars.add(v);
        }
        int[] rightOutIndex = new int[rightVars.size()];
        Arrays.fill(rightOutIndex, -1);
        for (int c : rightOutCols) {
            rightOutIndex[c] = outVars.size();
            outVars.add(copyVariable(rightVars.get(c), rightNames[c]));
        }

        // ---- Fill ----
        DataSet joined = new BoxDataSet(new MixedDataBox(outVars, nOut), outVars);

        for (int r = 0; r < nOut; r++) {
            int li = pairs.get(r)[0];
            int rj = pairs.get(r)[1];

            for (int c = 0; c < leftVars.size(); c++) {
                int oc = leftOutIndex[c];
                int ki = indexOf(leftKeyCols, c);

                if (ki >= 0) {
                    keyColumns[ki].write(joined, r, oc, left, li, right, rj);
                } else if (li >= 0) {
                    copyCell(left, li, c, joined, r, oc);
                } else {
                    setMissing(joined, r, oc);
                }
            }

            for (int c : rightOutCols) {
                int oc = rightOutIndex[c];
                if (rj >= 0) copyCell(right, rj, c, joined, r, oc);
                else setMissing(joined, r, oc);
            }
        }

        // ---- Report ----
        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);
        StringBuilder rep = new StringBuilder();
        rep.append("Join: ").append(this.joinType).append(" on ").append(this.leftKeys)
                .append(" (left) = ").append(this.rightKeys).append(" (right)\n");
        rep.append("Left table: ").append(nLeft).append(" rows, ").append(leftDistinct.size())
                .append(" distinct keys, ").append(count(leftMissingKey, "row")).append(" with missing key");
        if (leftRepeatedKeys > 0) {
            rep.append("; ").append(count(leftRepeatedKeys, "key")).append(" repeated (many-to-one lookup)");
        }
        rep.append('\n');
        rep.append("Right table: ").append(nRight).append(" rows, ").append(rightIndex.size())
                .append(" distinct keys, ").append(count(rightMissingKey, "row")).append(" with missing key");
        if (rightRepeatedKeys > 0) {
            rep.append("; ").append(count(rightRepeatedKeys, "key")).append(" repeated (")
                    .append(count(rightExtraRows, "extra row")).append("; one-to-many allowed)");
        }
        rep.append('\n');
        rep.append("Left rows matched: ").append(leftMatchedRows).append(" of ").append(nLeft);
        if (nLeft > 0) rep.append(" (").append(pct.format(leftMatchedRows / (double) nLeft)).append(")");
        rep.append("; unmatched left rows ")
                .append(this.joinType == JoinType.INNER ? "dropped: " : "kept with missing right values: ")
                .append(leftUnmatchedRows).append('\n');
        rep.append("Right rows matched: ").append(nRight - rightUnmatchedRows).append(" of ").append(nRight);
        if (nRight > 0) {
            rep.append(" (").append(pct.format((nRight - rightUnmatchedRows) / (double) nRight)).append(")");
        }
        rep.append("; unmatched right rows ")
                .append(this.joinType == JoinType.FULL_OUTER ? "kept with missing left values: " : "dropped: ")
                .append(rightUnmatchedRows).append('\n');
        rep.append("Output: ").append(nOut).append(" rows, ").append(outVars.size()).append(" columns (")
                .append(leftVars.size()).append(" from the left, ").append(rightOutCols.size())
                .append(" from the right)\n");
        if (!renamed.isEmpty()) {
            rep.append("Columns renamed on name collision: ").append(String.join("; ", renamed)).append('\n');
        }
        for (String note : keyNotes) rep.append(note).append('\n');

        return new Result(joined, rep.toString());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Key columns
    // ---------------------------------------------------------------------------------------------------------

    /**
     * The output form of one key column. In a LEFT or INNER join, or a FULL_OUTER join with no right-only rows, it
     * is a copy of the left key variable and values are copied straight from the left rows. Otherwise it must also
     * hold key values from right-only rows: a discrete left key has its category list extended with the new values;
     * a continuous left key stays continuous if the new values are all numeric and is otherwise converted to a
     * discrete variable over the observed key strings.
     */
    private static final class KeyColumn {
        Node variable;
        /**
         * If non-null, values are written as category indices looked up by canonical token.
         */
        Map<String, Integer> tokenToIndex;
        /**
         * If tokenToIndex is null and the variable is discrete, left values are copied by index (categories are
         * a prefix-preserving extension of the left variable's).
         */
        int leftCol;
        int rightCol;

        void write(DataSet out, int r, int oc, DataSet left, int li, DataSet right, int rj) {
            if (this.tokenToIndex != null) {
                String token = li >= 0 ? keyToken(left, li, this.leftCol) : keyToken(right, rj, this.rightCol);
                Integer idx = token == null ? null : this.tokenToIndex.get(token);
                out.setInt(r, oc, idx == null ? DiscreteVariable.MISSING_VALUE : idx);
            } else if (this.variable instanceof DiscreteVariable) {
                if (li >= 0) out.setInt(r, oc, left.getInt(li, this.leftCol));
                else out.setInt(r, oc, rightIndexInExtended(right, rj));
            } else {
                if (li >= 0) out.setDouble(r, oc, left.getDouble(li, this.leftCol));
                else out.setDouble(r, oc, rightAsDouble(right, rj));
            }
        }

        /**
         * For a discrete output key with extended categories: the index of the right row's key value.
         */
        Map<String, Integer> extendedTokenToIndex;

        private int rightIndexInExtended(DataSet right, int rj) {
            String token = keyToken(right, rj, this.rightCol);
            Integer idx = token == null ? null : this.extendedTokenToIndex.get(token);
            return idx == null ? DiscreteVariable.MISSING_VALUE : idx;
        }

        private double rightAsDouble(DataSet right, int rj) {
            Node v = right.getVariable(this.rightCol);
            if (v instanceof DiscreteVariable dv) {
                int idx = right.getInt(rj, this.rightCol);
                if (idx == DiscreteVariable.MISSING_VALUE) return Double.NaN;
                try {
                    return Double.parseDouble(dv.getCategory(idx).trim());
                } catch (NumberFormatException e) {
                    return Double.NaN;
                }
            }
            return right.getDouble(rj, this.rightCol);
        }
    }

    private static KeyColumn keyColumn(DataSet left, int leftCol, String outName, DataSet right, int rightCol,
                                       List<int[]> pairs, boolean rightOnlyRowsEmitted, List<String> notes) {
        KeyColumn kc = new KeyColumn();
        kc.leftCol = leftCol;
        kc.rightCol = rightCol;
        Node leftVar = left.getVariable(leftCol);

        if (!rightOnlyRowsEmitted) {
            kc.variable = copyVariable(leftVar, outName);
            return kc;
        }

        // Tokens of right-only rows, in order of first appearance.
        LinkedHashSet<String> rightOnlyTokens = new LinkedHashSet<>();
        Map<String, String> rightOnlyDisplay = new HashMap<>();
        for (int[] p : pairs) {
            if (p[0] >= 0) continue;
            String token = keyToken(right, p[1], rightCol);
            if (token == null) continue;
            if (rightOnlyTokens.add(token)) rightOnlyDisplay.put(token, keyDisplay(right, p[1], rightCol));
        }

        if (leftVar instanceof DiscreteVariable ldv) {
            List<String> categories = new ArrayList<>(ldv.getCategories());
            Map<String, Integer> tokenToIndex = new HashMap<>();
            for (int i = 0; i < categories.size(); i++) {
                tokenToIndex.putIfAbsent(canonical(categories.get(i)), i);
            }
            int added = 0;
            for (String token : rightOnlyTokens) {
                if (tokenToIndex.containsKey(token)) continue;
                String display = rightOnlyDisplay.get(token);
                String cat = display;
                int n = 2;
                while (categories.contains(cat)) cat = display + "_" + (n++);
                categories.add(cat);
                tokenToIndex.put(token, categories.size() - 1);
                added++;
            }
            kc.variable = new DiscreteVariable(outName, categories);
            kc.extendedTokenToIndex = tokenToIndex;
            if (added > 0) {
                notes.add("Key '" + outName + "': " + added + " categor" + (added == 1 ? "y" : "ies")
                        + " added for right-only rows");
            }
            return kc;
        }

        boolean allNumeric = true;
        for (String token : rightOnlyTokens) {
            if (!isNumeric(token)) {
                allNumeric = false;
                break;
            }
        }

        if (allNumeric) {
            kc.variable = new ContinuousVariable(outName);
            return kc;
        }

        // Convert the key to discrete over the observed key strings, left values first.
        List<String> categories = new ArrayList<>();
        Map<String, Integer> tokenToIndex = new LinkedHashMap<>();
        for (int i = 0; i < left.getNumRows(); i++) {
            String token = keyToken(left, i, leftCol);
            if (token == null || tokenToIndex.containsKey(token)) continue;
            tokenToIndex.put(token, categories.size());
            categories.add(keyDisplay(left, i, leftCol));
        }
        for (String token : rightOnlyTokens) {
            if (tokenToIndex.containsKey(token)) continue;
            tokenToIndex.put(token, categories.size());
            categories.add(rightOnlyDisplay.get(token));
        }
        kc.variable = new DiscreteVariable(outName, categories);
        kc.tokenToIndex = tokenToIndex;
        notes.add("Key '" + outName + "': converted from continuous to discrete, since right-only rows carry "
                + "non-numeric key values");
        return kc;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Cell helpers
    // ---------------------------------------------------------------------------------------------------------

    /**
     * The canonical string tokens of a row's key, or null if any component is missing.
     */
    private static List<String> keyTuple(DataSet data, int row, int[] keyCols) {
        List<String> tuple = new ArrayList<>(keyCols.length);
        for (int c : keyCols) {
            String token = keyToken(data, row, c);
            if (token == null) return null;
            tuple.add(token);
        }
        return tuple;
    }

    /**
     * The canonical string token of one key cell, or null if missing.
     */
    private static String keyToken(DataSet data, int row, int col) {
        Node v = data.getVariable(col);
        if (v instanceof DiscreteVariable dv) {
            int idx = data.getInt(row, col);
            if (idx == DiscreteVariable.MISSING_VALUE) return null;
            return canonical(dv.getCategory(idx));
        }
        double d = data.getDouble(row, col);
        if (Double.isNaN(d)) return null;
        return canonical(d);
    }

    /**
     * The display string of one key cell (category name, or canonical number), or "*" if missing.
     */
    private static String keyDisplay(DataSet data, int row, int col) {
        Node v = data.getVariable(col);
        if (v instanceof DiscreteVariable dv) {
            int idx = data.getInt(row, col);
            return idx == DiscreteVariable.MISSING_VALUE ? "*" : dv.getCategory(idx);
        }
        double d = data.getDouble(row, col);
        return Double.isNaN(d) ? "*" : canonical(d);
    }

    /**
     * Canonicalizes a key string: numeric strings are rendered as by {@link #canonical(double)} so that "1234",
     * "1234.0", and 1234.0 agree; other strings are trimmed.
     */
    static String canonical(String s) {
        String t = s.trim();
        try {
            double d = Double.parseDouble(t);
            if (!Double.isNaN(d) && !Double.isInfinite(d)) return canonical(d);
        } catch (NumberFormatException ignored) {
        }
        return t;
    }

    /**
     * Canonicalizes a numeric key: integral values without a fractional part, others by Double.toString.
     */
    static String canonical(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return Double.toString(d);
    }

    private static boolean isNumeric(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void copyCell(DataSet from, int fr, int fc, DataSet to, int tr, int tc) {
        if (from.getVariable(fc) instanceof DiscreteVariable) to.setInt(tr, tc, from.getInt(fr, fc));
        else to.setDouble(tr, tc, from.getDouble(fr, fc));
    }

    private static void setMissing(DataSet data, int row, int col) {
        if (data.getVariable(col) instanceof DiscreteVariable) data.setInt(row, col, DiscreteVariable.MISSING_VALUE);
        else data.setDouble(row, col, Double.NaN);
    }

    private static Node copyVariable(Node v, String name) {
        if (v instanceof DiscreteVariable dv) return new DiscreteVariable(name, dv.getCategories());
        if (v instanceof ContinuousVariable) return new ContinuousVariable(name);
        throw new IllegalArgumentException("Unsupported variable type for '" + v.getName() + "': "
                + v.getClass().getSimpleName());
    }

    private static void checkSupported(Node v, String side) {
        if (!(v instanceof DiscreteVariable) && !(v instanceof ContinuousVariable)) {
            throw new IllegalArgumentException("Unsupported variable type in the " + side + " data set for '"
                    + v.getName() + "': " + v.getClass().getSimpleName());
        }
    }

    private static int column(DataSet data, String name, String side) {
        Node v = data.getVariable(name);
        if (v == null) throw new IllegalArgumentException("No variable named '" + name + "' in the " + side + " data set.");
        return data.getColumnIndex(v);
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static int indexOf(int[] a, int x) {
        for (int i = 0; i < a.length; i++) if (a[i] == x) return i;
        return -1;
    }

    private static String uniqueName(Set<String> used, String name) {
        String candidate = name;
        int n = 2;
        while (!used.add(candidate)) candidate = name + "_" + (n++);
        return candidate;
    }

    /**
     * The joined data set and a findings-only report.
     *
     * @param joined The joined data set.
     * @param report The report.
     */
    public record Result(DataSet joined, String report) {
    }
}
