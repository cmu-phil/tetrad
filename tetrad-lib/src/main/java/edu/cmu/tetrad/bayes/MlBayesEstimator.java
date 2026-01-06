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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estimates parameters of the given Bayes net from the given data using maximum likelihood method.
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
        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

        MlBayesIm im = new MlBayesIm(bayesPm, true);

        // Get the nodes from the BayesPm. This fixes the order of the nodes
        // in the BayesIm, independently of any change to the BayesPm.
        // (This order must be maintained.)
//        Graph graph = bayesPm.getDag();

        // Build a mapping from BayesIm node index -> DataSet column index
        int numNodes = im.getNumNodes();
        int[] dsColForImIdx = new int[numNodes];
        for (int k = 0; k < numNodes; k++) {
            String name = im.getNode(k).getName();
            // Find the dataset column with the same name
            int dsCol = -1;
            for (int c = 0; c < dataSet.getNumColumns(); c++) {
                if (dataSet.getVariable(c).getName().equals(name)) {
                    dsCol = c; break;
                }
            }
            if (dsCol < 0) {
                throw new IllegalStateException("DataSet is missing variable: " + name);
            }
            dsColForImIdx[k] = dsCol;
        }

        Graph graph = bayesPm.getDag();

        for (int nodeIndex = 0; nodeIndex < im.getNumNodes(); nodeIndex++) {
            Node node = im.getNode(nodeIndex);

            // Use the same parent order for dims, parentValues, and im.getRowIndex
            List<Node> parentList = new ArrayList<>(graph.getParents(node));
            Collections.sort(parentList); // keep if BayesIm uses sorted parent order; otherwise remove

            int[] parentArray = new int[parentList.size()];
            for (int i = 0; i < parentList.size(); i++) {
                parentArray[i] = im.getNodeIndex(parentList.get(i)); // indices in IM space
            }

            // Parent dims
            int[] dims = new int[parentArray.length];
            for (int i = 0; i < dims.length; i++) {
                Node parNode = im.getNode(parentArray[i]);
                dims[i] = bayesPm.getNumCategories(parNode);
            }

            // Table shape
            int numRows = 1;
            for (int dim : dims) numRows *= dim;
            int numCols = bayesPm.getNumCategories(node);

            CptMapCounts counts = new CptMapCounts(numRows, numCols);
            counts.setPriorCount(prior);

            // Fill counts using the **DataSet column index** mapped from IM index
            int childDsCol = dsColForImIdx[nodeIndex];

            for (int row = 0; row < dataSet.getNumRows(); row++) {
                int[] parentValues = new int[parentArray.length];
                for (int i = 0; i < parentArray.length; i++) {
                    int parImIdx = parentArray[i];
                    int parDsCol = dsColForImIdx[parImIdx];
                    int v = dataSet.getInt(row, parDsCol);
                    // optional: sanity-check range
                    // if (v < 0 || v >= dims[i]) throw new IllegalArgumentException("Parent value out of range");
                    parentValues[i] = v;
                }

                int value = dataSet.getInt(row, childDsCol);
                // optional: sanity-check range
                // if (value < 0 || value >= numCols) throw new IllegalArgumentException("Child value out of range");

                int cptRow = im.getRowIndex(nodeIndex, parentValues);
                counts.addCounts(cptRow, value, 1);
            }

            im.setCountMap(nodeIndex, counts);
        }

//        for (int nodeIndex = 0; nodeIndex < im.getNumNodes(); nodeIndex++) {
//            Node node = im.getNode(nodeIndex);
//
//            // Set up parents array.  Should store the parents of
//            // each node as ints in a particular order.
//            List<Node> parentList = new ArrayList<>(graph.getParents(node));
//            Collections.sort(parentList);
//            int[] parentArray = new int[parentList.size()];
//
//            for (int i = 0; i < parentList.size(); i++) {
//                parentArray[i] = im.getNodeIndex(parentList.get(i));
//            }
//
//            // Sort the parent array.
////            Arrays.sort(parentArray);
//
//            // Setup dimensions array for parents.
//            int[] dims = new int[parentArray.length];
//
//            for (int i = 0; i < dims.length; i++) {
//                Node parNode = im.getNode(parentArray[i]);
//                dims[i] = bayesPm.getNumCategories(parNode);
//            }
//
//            // Calculate dimensions of table.
//            int numRows = 1;
//
//            for (int dim : dims) {
//                numRows *= dim;
//            }
//
//            int numCols = bayesPm.getNumCategories(node);
//
//            CptMapCounts counts = new CptMapCounts(numRows, numCols);
//            counts.setPriorCount(prior);
//
//            for (int row = 0; row < dataSet.getNumRows(); row++) {
//                int[] parentValues = new int[parentArray.length];
//
//                for (int i = 0; i < parentValues.length; i++) {
//                    parentValues[i] = dataSet.getInt(row, parentArray[i]);
//                }
//
//                int value = dataSet.getInt(row, nodeIndex);
//
//                counts.addCounts(im.getRowIndex(nodeIndex, parentValues), value, 1);
//            }
//
//            im.setCountMap(nodeIndex, counts);
//        }

        return im;
    }
}






