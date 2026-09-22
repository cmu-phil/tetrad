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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.JoinDatasets;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Joins the selected data sets of two data parents on a key, using {@link JoinDatasets}. Which parent is the left
 * (primary) table is chosen by name in the parameter editor, since the session graph does not order parents. The
 * parameters are set by {@code JoinDatasetsParamsEditor}:
 * <ul>
 *     <li>{@value #LEFT_PARENT}: the name of the parent node supplying the left table;</li>
 *     <li>{@value #LEFT_KEYS}, {@value #RIGHT_KEYS}: comma-separated key variable names, paired by position;</li>
 *     <li>{@value #JOIN_TYPE}: a {@link JoinDatasets.JoinType} name (default LEFT);</li>
 *     <li>{@value #ALLOW_ONE_TO_MANY}: whether a right key may repeat (default false);</li>
 *     <li>{@value #LEFT_SUFFIX}, {@value #RIGHT_SUFFIX}: suffixes for colliding non-key column names.</li>
 * </ul>
 * The join's findings report (row and key counts, match rates, renamed columns) is written to the log.
 *
 * @author josephramsey
 */
public class JoinDatasetsWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Parameter key: the name of the parent node supplying the left table.
     */
    public static final String LEFT_PARENT = "joinLeftParent";
    /**
     * Parameter key: comma-separated left key variable names.
     */
    public static final String LEFT_KEYS = "joinLeftKeys";
    /**
     * Parameter key: comma-separated right key variable names.
     */
    public static final String RIGHT_KEYS = "joinRightKeys";
    /**
     * Parameter key: join type name.
     */
    public static final String JOIN_TYPE = "joinType";
    /**
     * Parameter key: whether to allow repeated right keys.
     */
    public static final String ALLOW_ONE_TO_MANY = "joinAllowOneToMany";
    /**
     * Parameter key: suffix for a left non-key column whose name collides.
     */
    public static final String LEFT_SUFFIX = "joinLeftSuffix";
    /**
     * Parameter key: suffix for a right non-key column whose name collides.
     */
    public static final String RIGHT_SUFFIX = "joinRightSuffix";

    /**
     * The report of the most recent join, for display.
     */
    private String report = "";

    /**
     * Joins the selected data sets of the two given data parents.
     *
     * @param data   The two data parents.
     * @param params The parameters (see the class comment).
     */
    public JoinDatasetsWrapper(DataWrapper[] data, Parameters params) {
        List<DataWrapper> wrappers = new ArrayList<>();
        for (DataWrapper w : data) if (w != null) wrappers.add(w);

        if (wrappers.size() != 2) {
            throw new IllegalArgumentException("Join needs exactly two data parents; found " + wrappers.size() + ".");
        }

        String leftName = params.getString(LEFT_PARENT, "");
        DataWrapper leftWrapper = wrappers.get(0);
        DataWrapper rightWrapper = wrappers.get(1);

        if (leftName != null && leftName.equals(rightWrapper.getName()) && !leftName.equals(leftWrapper.getName())) {
            leftWrapper = wrappers.get(1);
            rightWrapper = wrappers.get(0);
        }

        DataSet left = dataSet(leftWrapper);
        DataSet right = dataSet(rightWrapper);

        JoinDatasets.Result result = fromParameters(params).apply(left, right);
        this.report = result.report();

        DataSet joined = result.joined();
        joined.setName("Joined");
        setDataModel(joined);
        setSourceGraph(leftWrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Join of two data sets on a key.", getDataModelList());
        TetradLogger.getInstance().log(this.report);
    }

    private static DataSet dataSet(DataWrapper wrapper) {
        DataModel model = wrapper.getSelectedDataModel();

        if (!(model instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("Join needs tabular data sets; '" + wrapper.getName()
                    + "' supplies " + (model == null ? "nothing" : model.getClass().getSimpleName()) + ".");
        }

        return dataSet;
    }

    /**
     * Builds the join from the parameters.
     *
     * @param params The parameters.
     * @return The join.
     */
    public static JoinDatasets fromParameters(Parameters params) {
        List<String> leftKeys = split(params.getString(LEFT_KEYS, ""));
        List<String> rightKeys = split(params.getString(RIGHT_KEYS, ""));

        if (leftKeys.isEmpty() || rightKeys.isEmpty()) {
            throw new IllegalArgumentException("Join needs at least one key variable on each side; open the "
                    + "parameter editor to choose them.");
        }

        JoinDatasets join = new JoinDatasets(leftKeys, rightKeys);
        join.setJoinType(JoinDatasets.JoinType.valueOf(
                params.getString(JOIN_TYPE, JoinDatasets.JoinType.LEFT.name())));
        join.setAllowOneToMany(params.getBoolean(ALLOW_ONE_TO_MANY, false));
        join.setLeftSuffix(params.getString(LEFT_SUFFIX, "_left"));
        join.setRightSuffix(params.getString(RIGHT_SUFFIX, "_right"));
        return join;
    }

    /**
     * Splits a comma-separated list, trimming and dropping empties.
     *
     * @param s The list.
     * @return The items.
     */
    public static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String item : s.split(",")) {
            String t = item.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * Joins a list with commas.
     *
     * @param items The items.
     * @return The list.
     */
    public static String join(List<String> items) {
        return String.join(",", items);
    }

    /**
     * Returns the findings report of the join.
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
