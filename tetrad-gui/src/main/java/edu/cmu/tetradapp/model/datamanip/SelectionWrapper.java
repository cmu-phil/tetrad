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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * The {@code SelectionWrapper} class provides functionality for selecting a subset of rows
 * from a {@link DataSet} within a {@link DataWrapper} object. This class serves as a
 * specialized wrapper that processes a single tabular dataset by retaining only those rows
 * where all user-specified variable constraints are satisfied simultaneously.
 *
 * <p>Constraints are keyed by variable name (to survive node-object recreation) and stored
 * in {@link SelectionSpec} instances. A {@link SelectionSpec} may express either:
 * <ul>
 *   <li>For a <em>continuous</em> variable: a union of closed intervals
 *       {@code [lo, hi] ∪ [lo, hi] ∪ …}</li>
 *   <li>For a <em>discrete</em> variable: a set of accepted category indices.</li>
 * </ul>
 *
 * <p>A row passes the filter if and only if <em>every</em> constrained variable satisfies
 * its {@link SelectionSpec}. Variables that are not mentioned in the specs are included
 * in the output unchanged.
 *
 * <p>This class extends {@link DataWrapper} and follows the same serialization conventions
 * as {@link DiscretizationWrapper}.
 */
public class SelectionWrapper extends DataWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code SelectionWrapper} by filtering rows of the selected
     * {@link DataSet} according to the {@link SelectionSpec} map stored in {@code params}.
     *
     * @param data   the parent {@link DataWrapper}; must contain exactly one {@link DataSet}
     * @param params must contain a {@code "selectionSpecs"} entry of type
     *               {@code Map<Node, SelectionSpec>}
     * @throws NullPointerException     if {@code data} or {@code params} is {@code null}
     * @throws IllegalArgumentException if the data model is not a rectangular {@link DataSet},
     *                                  if no specs are provided, if no specs match any variable,
     *                                  or if the resulting selection is empty
     */
    public SelectionWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data wrapper must not be null.");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null.");
        }

        // Retrieve the selection specs produced by the editor.
        @SuppressWarnings("unchecked")
        Map<Node, SelectionSpec> selectionSpecs =
                (Map<Node, SelectionSpec>) params.get("selectionSpecs", new HashMap<Node, SelectionSpec>());

        if (selectionSpecs == null || selectionSpecs.isEmpty()) {
            throw new IllegalArgumentException("No selection specifications have been provided.");
        }

        // Build a name-keyed lookup so that node object identity does not matter
        // (the simulator may recreate node objects between the editor run and here).
        Map<String, SelectionSpec> specsByName = new HashMap<>();
        for (Map.Entry<Node, SelectionSpec> entry : selectionSpecs.entrySet()) {
            specsByName.put(entry.getKey().getName(), entry.getValue());
        }

        // Only one dataset is supported for selection (mirroring the editor restriction).
        DataModelList sourceList = data.getDataModelList();
        if (sourceList == null || sourceList.isEmpty()) {
            throw new IllegalArgumentException("The parent data wrapper contains no data sets.");
        }
        if (sourceList.size() > 1) {
            throw new IllegalArgumentException("Selection currently supports exactly one data set; "
                    + sourceList.size() + " were found.");
        }

        DataModel dataModel = sourceList.get(0);
        if (!(dataModel instanceof DataSet originalData)) {
            throw new IllegalArgumentException("Only rectangular (tabular) data sets can be used for selection.");
        }

        // Map spec names to actual Node objects present in this dataset.
        Map<Integer, SelectionSpec> colIndexToSpec = new LinkedHashMap<>();
        for (Node node : originalData.getVariables()) {
            SelectionSpec spec = specsByName.get(node.getName());
            if (spec != null) {
                colIndexToSpec.put(originalData.getColumnIndex(node), spec);
            }
        }

        if (colIndexToSpec.isEmpty()) {
            throw new IllegalArgumentException("No selection specifications matched any variable in the dataset. "
                    + "Check that variable names are consistent.");
        }

        // Identify which row indices satisfy ALL constraints simultaneously.
        List<Integer> selectedRows = new ArrayList<>();

        for (int row = 0; row < originalData.getNumRows(); row++) {
            boolean passes = true;

            for (Map.Entry<Integer, SelectionSpec> entry : colIndexToSpec.entrySet()) {
                int col = entry.getKey();
                SelectionSpec spec = entry.getValue();
                Node var = originalData.getVariable(col);

                if (var instanceof ContinuousVariable) {
                    double value = originalData.getDouble(row, col);
                    if (!spec.acceptsContinuous(value)) {
                        passes = false;
                        break;
                    }
                } else if (var instanceof DiscreteVariable) {
                    int value = originalData.getInt(row, col);
                    if (!spec.acceptsDiscrete(value)) {
                        passes = false;
                        break;
                    }
                }
                // Variables of other types are not constrained.
            }

            if (passes) {
                selectedRows.add(row);
            }
        }

        if (selectedRows.isEmpty()) {
            throw new IllegalArgumentException("The specified selection constraints produced an empty dataset. "
                    + "Please relax one or more constraints.");
        }

        // Determine which columns to include in the output.
        // When "removeSelectionVariables" is true, any column whose name appears
        // in the specs map (i.e. was used as a selection filter) is dropped.
        boolean removeSelectionVars = Boolean.TRUE.equals(
                params.get("removeSelectionVariables", false));

        List<Node> outputVariables = new ArrayList<>();
        List<Integer> outputColIndices = new ArrayList<>();   // parallel list of source column indices

        for (int col = 0; col < originalData.getNumColumns(); col++) {
            Node var = originalData.getVariable(col);
            // A column is a "selection variable" if its NodeType is SELECTION.
            // We do NOT use specsByName membership here: specs cover every variable
            // the editor touched (including ordinary variables whose ranges were left
            // at the full observed range), so using specs as the criterion would
            // incorrectly drop non-SELECTION columns.
            boolean isSelectionVar = var.getNodeType() == NodeType.SELECTION;
            if (removeSelectionVars && isSelectionVar) {
                continue;   // drop this column from the output
            }
            outputVariables.add(var);
            outputColIndices.add(col);
        }

        // Build the output dataset: selected rows × (possibly reduced) columns.
        DataSet result = new BoxDataSet(
                new VerticalDoubleDataBox(selectedRows.size(), outputVariables.size()),
                outputVariables);

        for (int newRow = 0; newRow < selectedRows.size(); newRow++) {
            int originalRow = selectedRows.get(newRow);
            for (int newCol = 0; newCol < outputColIndices.size(); newCol++) {
                int srcCol = outputColIndices.get(newCol);
                Node var = originalData.getVariable(srcCol);
                if (var instanceof ContinuousVariable) {
                    result.setDouble(newRow, newCol, originalData.getDouble(originalRow, srcCol));
                } else if (var instanceof DiscreteVariable) {
                    result.setInt(newRow, newCol, originalData.getInt(originalRow, srcCol));
                }
            }
        }

        result.setName(originalData.getName() + " [selection]");

        DataModelList resultList = new DataModelList();
        resultList.add(result);

        setDataModel(resultList);
        setSourceGraph(data.getSourceGraph());

        String removedNote = removeSelectionVars
                ? ", " + (originalData.getNumColumns() - outputVariables.size()) + " selection variable(s) removed"
                : "";
        LogDataUtils.logDataModelList(
                "Row-selection subset of data in the parent node ("
                        + selectedRows.size() + " of " + originalData.getNumRows() + " rows retained"
                        + removedNote + ").",
                getDataModelList());
    }

    // -------------------------------------------------------------------------
    // Serialization exemplar
    // -------------------------------------------------------------------------

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link PcRunner} instance
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    // -------------------------------------------------------------------------
    // Custom serialization
    // -------------------------------------------------------------------------

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: "
                    + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: "
                    + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    // =========================================================================
    // Inner class: SelectionSpec
    // =========================================================================

    /**
     * Captures the row-selection constraint for a single variable.
     *
     * <ul>
     *   <li>For <em>continuous</em> variables the constraint is a non-empty list of
     *       closed intervals {@code [lo, hi]}. A value passes if it falls inside
     *       <em>at least one</em> interval (union semantics).</li>
     *   <li>For <em>discrete</em> variables the constraint is a non-empty set of
     *       accepted category indices. A value passes if it is a member of that set.</li>
     * </ul>
     */
    public static final class SelectionSpec implements java.io.Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * For continuous variables: list of [lo, hi] pairs.
         * {@code null} when this spec is for a discrete variable.
         */
        private final List<double[]> continuousIntervals;

        /**
         * For discrete variables: set of accepted category indices.
         * {@code null} when this spec is for a continuous variable.
         */
        private final Set<Integer> acceptedCategories;

        // ------------------------------------------------------------------
        // Factory constructors
        // ------------------------------------------------------------------

        /**
         * Creates a {@link SelectionSpec} for a <em>continuous</em> variable.
         *
         * @param intervals a list of two-element arrays {@code {lo, hi}}; must not be empty
         */
        public static SelectionSpec continuous(List<double[]> intervals) {
            if (intervals == null || intervals.isEmpty()) {
                throw new IllegalArgumentException("At least one interval is required.");
            }
            return new SelectionSpec(new ArrayList<>(intervals), null);
        }

        /**
         * Creates a {@link SelectionSpec} for a <em>discrete</em> variable.
         *
         * @param categories the set of accepted category indices; must not be empty
         */
        public static SelectionSpec discrete(Set<Integer> categories) {
            if (categories == null || categories.isEmpty()) {
                throw new IllegalArgumentException("At least one category must be accepted.");
            }
            return new SelectionSpec(null, new HashSet<>(categories));
        }

        private SelectionSpec(List<double[]> continuousIntervals, Set<Integer> acceptedCategories) {
            this.continuousIntervals = continuousIntervals;
            this.acceptedCategories = acceptedCategories;
        }

        // ------------------------------------------------------------------
        // Acceptance predicates
        // ------------------------------------------------------------------

        /**
         * Returns {@code true} if {@code value} falls within at least one of the
         * stored intervals (union of closed intervals).
         *
         * @throws IllegalStateException if this spec was constructed for a discrete variable
         */
        public boolean acceptsContinuous(double value) {
            if (continuousIntervals == null) {
                throw new IllegalStateException("This SelectionSpec is for a discrete variable.");
            }
            for (double[] interval : continuousIntervals) {
                if (value >= interval[0] && value <= interval[1]) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns {@code true} if {@code categoryIndex} is among the accepted categories.
         *
         * @throws IllegalStateException if this spec was constructed for a continuous variable
         */
        public boolean acceptsDiscrete(int categoryIndex) {
            if (acceptedCategories == null) {
                throw new IllegalStateException("This SelectionSpec is for a continuous variable.");
            }
            return acceptedCategories.contains(categoryIndex);
        }

        // ------------------------------------------------------------------
        // Accessors (used by the editor to restore state)
        // ------------------------------------------------------------------

        /** Returns the continuous intervals, or {@code null} for discrete specs. */
        public List<double[]> getContinuousIntervals() {
            return continuousIntervals == null ? null : Collections.unmodifiableList(continuousIntervals);
        }

        /** Returns the accepted discrete categories, or {@code null} for continuous specs. */
        public Set<Integer> getAcceptedCategories() {
            return acceptedCategories == null ? null : Collections.unmodifiableSet(acceptedCategories);
        }

        /** Returns {@code true} if this spec describes a continuous constraint. */
        public boolean isContinuous() {
            return continuousIntervals != null;
        }
    }
}
