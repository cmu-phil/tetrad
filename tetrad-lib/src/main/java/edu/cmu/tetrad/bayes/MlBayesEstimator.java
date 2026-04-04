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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataSet;

import java.util.HashMap;
import java.util.Map;

/**
 * Estimates parameters of the given Bayes net from the given data using maximum likelihood method.
 *
 * IMPORTANT:
 * - Uses the BayesIm's OWN canonical parent order (im.getParents(nodeIndex)).
 * - Looks up DataSet columns BY VARIABLE NAME (not by BayesIm index).
 * - Skips rows with missing values (negative int codes).
 *
 * This prevents out-of-bounds row/column errors when counting.
 *
 * @author Shane Harwood, Joseph Ramsey
 * @version $Id: $Id
 */
public final class MlBayesEstimator {

    private final double prior;

    /**
     * Create an instance of MlBayesEstimator with the given prior.
     *
     * @param prior the prior value used in the ML estimation
     */
    public MlBayesEstimator(double prior) {
        this.prior = prior;
    }

    /**
     * Estimates parameters of the given Bayes net from the given data using maximum likelihood method.
     *
     * @param bayesPm The BayesPm object representing the Bayes net.
     * @param dataSet The DataSet object containing the data.
     * @return A BayesIm object representing the estimated Bayes Information Matrix (Bayes IM).
     * @throws NullPointerException if either bayesPm or dataSet is null.
     */
    public BayesIm estimate(BayesPm bayesPm, DataSet dataSet) {
        if (bayesPm == null) throw new NullPointerException("bayesPm");
        if (dataSet == null) throw new NullPointerException("dataSet");

        // COUNT_MAP BayesIm: allocates CPT shapes & canonical parent order inside MlBayesIm.
        MlBayesIm im = new MlBayesIm(bayesPm, true);

        // Build DataSet column lookup by variable name (fast + robust to column order).
        Map<String, Integer> dsColByName = new HashMap<>();
        for (int c = 0; c < dataSet.getNumColumns(); c++) {
            dsColByName.put(dataSet.getVariable(c).getName(), c);
        }

        // Optional: ensure all BayesPm variables exist in dataSet.
        for (int k = 0; k < im.getNumNodes(); k++) {
            String name = im.getNode(k).getName();
            if (!dsColByName.containsKey(name)) {
                throw new IllegalStateException("DataSet is missing variable: " + name);
            }
        }

        final int n = dataSet.getNumRows();

        for (int nodeIndex = 0; nodeIndex < im.getNumNodes(); nodeIndex++) {

            // CPT shape MUST match MlBayesIm's allocation for this node.
            final int numRows = im.getNumRows(nodeIndex);
            final int numCols = im.getNumColumns(nodeIndex);

            CptMapCounts counts = new CptMapCounts(numRows, numCols);
            counts.setPriorCount(prior);

            // Child column in the data set.
            final String childName = im.getNode(nodeIndex).getName();
            final int childDsCol = dsColByName.get(childName);

            // Parents in the *exact* order MlBayesIm expects.
            final int[] parents = im.getParents(nodeIndex);
            final int numParents = parents.length;

            // Map parent indices -> dataset columns (in the same parent order).
            final int[] parentDsCols = new int[numParents];
            for (int p = 0; p < numParents; p++) {
                String parName = im.getNode(parents[p]).getName();
                parentDsCols[p] = dsColByName.get(parName);
            }

            // Parent dims in the same order (for range checks / debugging safety).
            final int[] parentDims = im.getParentDims(nodeIndex);

            // Scratch parent-values vector (in im parent order).
            final int[] parentValues = new int[numParents];

            for (int r = 0; r < n; r++) {
                // Read child category index.
                int value = dataSet.getInt(r, childDsCol);
                if (value < 0) continue;               // missing
                if (value >= numCols) {
                    throw new IllegalArgumentException(
                            "Child value out of range for '" + childName + "': " + value +
                                    " not in [0," + (numCols - 1) + "] at row " + r
                    );
                }

                boolean skip = false;

                // Read parents in canonical parent order.
                for (int p = 0; p < numParents; p++) {
                    int v = dataSet.getInt(r, parentDsCols[p]);
                    if (v < 0) { // missing parent -> skip this row for this CPT
                        skip = true;
                        break;
                    }
                    int dim = parentDims[p];
                    if (v >= dim) {
                        String parName = im.getNode(parents[p]).getName();
                        throw new IllegalArgumentException(
                                "Parent value out of range for '" + parName + "' (parent of '" + childName + "'): " + v +
                                        " not in [0," + (dim - 1) + "] at row " + r
                        );
                    }
                    parentValues[p] = v;
                }

                if (skip) continue;

                // Compute CPT row index using MlBayesIm's own dims + order.
                int cptRow = im.getRowIndex(nodeIndex, parentValues);

                // Defensive (should be redundant if getRowIndex is correct).
                if (cptRow < 0 || cptRow >= numRows) {
                    throw new IllegalStateException(
                            "Computed CPT row out of bounds for '" + childName + "': " + cptRow +
                                    " not in [0," + (numRows - 1) + "]. This indicates a parent-order/dims mismatch."
                    );
                }

                counts.addCounts(cptRow, value, 1);
            }

            im.setCountMap(nodeIndex, counts);
        }

        return im;
    }
}