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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.NtadExplorer;
import edu.cmu.tetrad.search.ntad_test.Cca;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.DataWrapper;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for the N-tad explorer. This is a data manipulation component that:
 * <ul>
 *   <li>takes a DataWrapper as input,</li>
 *   <li>keeps the underlying DataModelList unchanged, and</li>
 *   <li>runs NtadExplorer on a (possibly) selected subset of variables.</li>
 * </ul>
 * <p>
 * The results are stored as a list of NtadResult objects that the editor can
 * display in a JTable.
 */
public class NtadExplorerWrapper extends DataWrapper implements TetradSerializable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The underlying dataset we will analyze. We assume the first DataModel is a DataSet.
     */
    private final DataModel dataModel;

    /**
     * Latest list of N-tad results, computed by runNtadSearch(...).
     */
    private final List<NtadExplorer.NtadResult> results = new ArrayList<>();
    /**
     * Represents the size of each block used in N-tad search operations. This value determines the number of variables
     * included in each block (A, B), with each N-tad utilizing 2 * blockSize variables in total.
     * <p>
     * The block size is a critical parameter in the decomposition and ranking process, directly impacting the
     * computational effort and the granularity of N-tads identified. A smaller block size may allow for finer
     * granularity in results, but at the cost of increased computational complexity.
     * <p>
     * Default value is set to 2.
     */
    private int blockSize = 2;
    /**
     * Specifies the maximum number of rank-deficient n-tad results to return during the N-tad search process.
     * <p>
     * This variable determines the upper limit on the number of results stored and retrievable after the execution of
     * the N-tad search. It serves as a control to limit computational overhead and ensure manageable result sizes. By
     * default, it is set to 100.
     * <p>
     * The value of this field can be modified using the corresponding getter and setter methods.
     */
    private int maxResults = 100;
    /**
     * Represents the significance level for Wilks' rank test. This threshold is used in statistical testing within the
     * context of N-tad searches to determine the level of evidence required to reject the null hypothesis of rank
     * sufficiency.
     */
    private double alpha = 0.05;
    /**
     * Stores the most recently selected subset of variables used in the N-tad search. This allows for tracking or
     * reusing the last selected variable configuration.
     */
    private List<Node> lastSelectedVars = new ArrayList<>();

    /**
     * Construct the wrapper from an existing DataWrapper. We simply pass through the input DataModelList; the action of
     * computing N-tads is triggered by the editor via runNtadSearch(...).
     *
     * @param data   The source data wrapper.
     * @param params Parameters object (not heavily used here but kept for consistency).
     */
    public NtadExplorerWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList originals = data.getDataModelList();
        if (originals == null || originals.isEmpty()) {
            throw new IllegalArgumentException("No data models in the provided DataWrapper.");
        }

        this.dataModel = originals.getFirst();

        // Pass-through: we just reuse the original DataModelList so downstream boxes
        // see the same data.
        setDataModel(originals);
        setSourceGraph(data.getSourceGraph());

        // Optionally you could stash params if you want them serialized, but it’s not
        // strictly necessary for this gadget.
    }

    /**
     * Runs the N-tad search using the given variables and parameters, storing results internally so that the editor can
     * retrieve them via getResults().
     *
     * @param vars       Subset of variables to consider; if null or empty, use all variables.
     * @param blockSize  Size m of each block (A,B); each n-tad uses 2m variables.
     * @param maxResults Maximum number of rank-deficient n-tads to return.
     * @param alpha      Significance level for Wilks' rank test.
     */
    public void runNtadSearch(List<Node> vars, int blockSize, int maxResults, double alpha) {

        List<Node> useVars;

        if (vars == null || vars.isEmpty()) {
            useVars = new ArrayList<>(dataModel.getVariables());
        } else {
            useVars = new ArrayList<>(vars);
        }

        // Construct a CCA test from the dataset. Adjust constructor args to match your Cca.
        boolean correlations = true;
        int ess;
        Cca ccaTest;
        CorrelationMatrix cm;

        if (dataModel instanceof CovarianceMatrix) {
            cm = new CorrelationMatrix((CovarianceMatrix) dataModel);
            ess = ((CovarianceMatrix) dataModel).getSampleSize();
            ccaTest = new Cca(cm.getMatrix().getData(), correlations, ess);
        } else if (dataModel instanceof DataSet) {
            cm = new CorrelationMatrix((DataSet) dataModel);
            ess = ((DataSet) dataModel).getNumRows();
            ccaTest = new Cca(cm.getMatrix().getData(), correlations, ess);
        } else {
            throw new IllegalArgumentException("Unsupported data model type: " + dataModel.getClass().getName());
        }

        List<NtadExplorer.NtadResult> found = NtadExplorer.listRankDeficientNtads(cm, useVars, blockSize, maxResults, alpha, ccaTest);

        results.clear();
        results.addAll(found);

        TetradLogger.getInstance().log("NtadExplorer found " + results.size() + " rank-deficient blocks (block size m = " + blockSize + ").");
    }

    /**
     * Returns an unmodifiable view of the most recent N-tad results.
     */
    public List<NtadExplorer.NtadResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Convenience accessor for the dataset.
     */
    public DataModel getDataModel() {
        return dataModel;
    }

//    /**
//     * Generates a simple exemplar of this class to test serialization.
//     */
//    public static NtadExplorerWrapper serializableInstance() {
//        // Just wrap a serializable PcRunner as a placeholder for tests where a wrapper is needed.
//        // You can adjust this if you have a better generic instance.
//        return new NtadExplorerWrapper(PcRunner.serializableInstance(), new edu.cmu.tetrad.util.Parameters());
//    }

    //====================  Serialization  ====================//

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves the current block size used in N-tad search operations.
     *
     * @return The current block size.
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Sets the block size for N-tad search operations.
     *
     * @param blockSize The new block size to be used.
     */
    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    /**
     * Retrieves the maximum number of rank-deficient N-tad results to return during the search.
     *
     * @return The maximum number of results.
     */
    public int getMaxResults() {
        return maxResults;
    }

    /**
     * Sets the maximum number of rank-deficient N-tad results to return during the search.
     *
     * @param maxResults The new maximum number of results.
     */
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    /**
     * Retrieves the significance level for Wilks' rank test.
     *
     * @return The significance level.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level for Wilks' rank test.
     *
     * @param alpha The new significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the list of the most recently selected variables.
     *
     * @return A list of Node objects representing the last selected variables.
     */
    public List<Node> getLastSelectedVars() {
        return lastSelectedVars;
    }

    /**
     * Sets the list of the most recently selected variables.
     *
     * @param lastSelectedVars The new list of selected variables.
     */
    public void setLastSelectedVars(List<Node> lastSelectedVars) {
        this.lastSelectedVars = lastSelectedVars;
    }
}