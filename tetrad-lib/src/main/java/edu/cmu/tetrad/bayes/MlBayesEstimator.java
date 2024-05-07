///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

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
        Graph graph = bayesPm.getDag();

        for (int nodeIndex = 0; nodeIndex < im.getNumNodes(); nodeIndex++) {
            Node node = im.getNode(nodeIndex);

            // Set up parents array.  Should store the parents of
            // each node as ints in a particular order.
            List<Node> parentList = new ArrayList<>(graph.getParents(node));
            Collections.sort(parentList);
            int[] parentArray = new int[parentList.size()];

            for (int i = 0; i < parentList.size(); i++) {
                parentArray[i] = im.getNodeIndex(parentList.get(i));
            }

            // Sort the parent array.
//            Arrays.sort(parentArray);

            // Setup dimensions array for parents.
            int[] dims = new int[parentArray.length];

            for (int i = 0; i < dims.length; i++) {
                Node parNode = im.getNode(parentArray[i]);
                dims[i] = bayesPm.getNumCategories(parNode);
            }

            // Calculate dimensions of table.
            int numRows = 1;

            for (int dim : dims) {
                numRows *= dim;
            }

            int numCols = bayesPm.getNumCategories(node);

            CptMapCounts counts = new CptMapCounts(numRows, numCols);
            counts.setPriorCount(prior);

            for (int row = 0; row < dataSet.getNumRows(); row++) {
                int[] parentValues = new int[parentArray.length];

                for (int i = 0; i < parentValues.length; i++) {
                    parentValues[i] = dataSet.getInt(row, parentArray[i]);
                }

                int value = dataSet.getInt(row, nodeIndex);

                counts.addCounts(im.getRowIndex(nodeIndex, parentValues), value, 1);
            }

            im.setCountMap(nodeIndex, counts);
        }

        return im;
    }
}





