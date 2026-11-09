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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reshapes each long-format data set of the parent (one row per unit per occasion) into wide format (one row per
 * unit, one column per occasion per value variable) using {@link LongToWide}. The parameters are set by
 * {@code LongToWideParamsEditor}:
 * <ul>
 *     <li>{@value #ROW_KEYS}: comma-separated names of the row key variable(s);</li>
 *     <li>{@value #COLUMN_KEY}: name of the (discrete) column key variable;</li>
 *     <li>{@value #VALUE_VARIABLES}: comma-separated names of the value variables to spread (empty: all others);</li>
 *     <li>{@value #AGGREGATIONS}: comma-separated {@code name=AGGREGATION} overrides;</li>
 *     <li>{@value #DEFAULT_AGGREGATION}: the {@link LongToWide.Aggregation} for variables without an override;</li>
 *     <li>{@value #SANITIZE_NAMES}, {@value #KEEP_ROW_KEYS}, {@value #INCLUDE_VALUE_NAME}: booleans;
 *     {@value #SEPARATOR}: a string; {@value #LEVEL_NAMES}: newline-separated {@code level=shortName} pairs.</li>
 * </ul>
 * The transform's findings report (units, levels, fill rates, duplicates) is written to the log.
 *
 * @author josephramsey
 */
public class LongToWideWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Parameter key: comma-separated row key variable names.
     */
    public static final String ROW_KEYS = "longToWideRowKeys";
    /**
     * Parameter key: column key variable name.
     */
    public static final String COLUMN_KEY = "longToWideColumnKey";
    /**
     * Parameter key: comma-separated value variable names (empty for all non-key variables).
     */
    public static final String VALUE_VARIABLES = "longToWideValueVariables";
    /**
     * Parameter key: comma-separated {@code name=AGGREGATION} per-variable overrides.
     */
    public static final String AGGREGATIONS = "longToWideAggregations";
    /**
     * Parameter key: default aggregation name.
     */
    public static final String DEFAULT_AGGREGATION = "longToWideDefaultAggregation";
    /**
     * Parameter key: whether to sanitize output names.
     */
    public static final String SANITIZE_NAMES = "longToWideSanitizeNames";
    /**
     * Parameter key: whether to keep the row keys as leading columns.
     */
    public static final String KEEP_ROW_KEYS = "longToWideKeepRowKeys";
    /**
     * Parameter key: the separator between value name and level.
     */
    public static final String SEPARATOR = "longToWideSeparator";
    /**
     * Parameter key: whether to prefix level names with the value variable's name (always done with more than one
     * value variable).
     */
    public static final String INCLUDE_VALUE_NAME = "longToWideIncludeValueName";
    /**
     * Parameter key: newline-separated {@code level=shortName} pairs renaming levels of the column key.
     */
    public static final String LEVEL_NAMES = "longToWideLevelNames";

    /**
     * The report of the most recent transform, for display.
     */
    private String report = "";

    /**
     * Reshapes the parent's data sets to wide form.
     *
     * @param wrapper The data to reshape.
     * @param params  The parameters (see the class comment).
     */
    public LongToWideWrapper(DataWrapper wrapper, Parameters params) {
        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();
        StringBuilder reports = new StringBuilder();

        LongToWide transform = fromParameters(params);

        for (DataModel model : inList) {
            if (!(model instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            LongToWide.Result result = transform.apply(dataSet);
            outList.add(result.wide());
            reports.append(result.report()).append('\n');
        }

        this.report = reports.toString();
        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Long-to-wide reshaping of data.", getDataModelList());
        TetradLogger.getInstance().log(this.report);
    }

    /**
     * Builds the transform from the parameters.
     *
     * @param params The parameters.
     * @return The transform.
     */
    public static LongToWide fromParameters(Parameters params) {
        List<String> rowKeys = split(params.getString(ROW_KEYS, ""));
        String columnKey = params.getString(COLUMN_KEY, "").trim();

        if (rowKeys.isEmpty() || columnKey.isEmpty()) {
            throw new IllegalArgumentException("Long-to-wide needs at least one row key variable and a column "
                    + "key variable; open the parameter editor to choose them.");
        }

        LongToWide t = new LongToWide(columnKey, rowKeys.toArray(new String[0]));
        t.setValueVariables(split(params.getString(VALUE_VARIABLES, "")));
        t.setDefaultAggregation(LongToWide.Aggregation.valueOf(
                params.getString(DEFAULT_AGGREGATION, LongToWide.Aggregation.FAIL.name())));

        for (String pair : split(params.getString(AGGREGATIONS, ""))) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            t.setAggregation(pair.substring(0, eq).trim(),
                    LongToWide.Aggregation.valueOf(pair.substring(eq + 1).trim()));
        }

        t.setSanitizeNames(params.getBoolean(SANITIZE_NAMES, true));
        t.setKeepRowKeys(params.getBoolean(KEEP_ROW_KEYS, false));
        t.setSeparator(params.getString(SEPARATOR, "."));
        t.setIncludeValueName(params.getBoolean(INCLUDE_VALUE_NAME, true));
        t.setLevelNames(decodeLevelNames(params.getString(LEVEL_NAMES, "")));
        return t;
    }

    /**
     * Decodes newline-separated {@code level=name} pairs (levels may contain any character but a newline or a
     * leading '=').
     *
     * @param encoded The parameter string.
     * @return The map, in encoded order.
     */
    public static java.util.Map<String, String> decodeLevelNames(String encoded) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (encoded == null) return map;
        for (String line : encoded.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String name = line.substring(eq + 1).trim();
            if (!name.isEmpty()) map.put(line.substring(0, eq), name);
        }
        return map;
    }

    /**
     * Encodes a level-name map as newline-separated {@code level=name} pairs, the inverse of
     * {@link #decodeLevelNames(String)}.
     *
     * @param names The map.
     * @return The parameter string.
     */
    public static String encodeLevelNames(java.util.Map<String, String> names) {
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<String, String> e : names.entrySet()) {
            if (e.getValue() == null || e.getValue().trim().isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(e.getKey()).append('=').append(e.getValue().trim());
        }
        return b.toString();
    }

    /**
     * Splits a comma-separated parameter, dropping blanks. Variable names containing commas are not supported by
     * this encoding.
     *
     * @param s The parameter string.
     * @return The items.
     */
    public static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String item : s.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /**
     * Joins items with commas, the inverse of {@link #split(String)}.
     *
     * @param items The items.
     * @return The parameter string.
     */
    public static String join(List<String> items) {
        return String.join(",", items);
    }

    /**
     * Returns the findings report of the transform.
     *
     * @return The report.
     */
    public String getReport() {
        return this.report;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }
}
