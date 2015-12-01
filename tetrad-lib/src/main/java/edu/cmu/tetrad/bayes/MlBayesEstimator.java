///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Estimates parameters of the given Bayes net from the given data using maximum
 * likelihood method.
 *
 * @author Shane Harwood, Joseph Ramsey
 */
public final class MlBayesEstimator {
    private DataSet reorderedDataSetDiscrete;

    /**
     * 33 Estimates a Bayes IM using the variables, graph, and parameters in the
     * given Bayes PM and the data columns in the given data set. Each variable
     * in the given Bayes PM must be equal to a variable in the given data set.
     */
    public BayesIm estimate(BayesPm bayesPm, DataSet dataSet) {
        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

//        if (DataUtils.containsMissingValue(dataSet)) {
//            throw new IllegalArgumentException("Please remove or impute missing values.");
//        }

        // Make sure all of the variables in the PM are in the data set;
        // otherwise, estimation is impossible.
        BayesUtils.ensureVarsInData(bayesPm.getVariables(), dataSet);

        // Create a new Bayes IM to store the estimated values.
        BayesIm estimatedIm = new MlBayesIm(bayesPm);

        // Create a subset of the data set with the variables of the IM, in
        // the order of the IM.
        List<Node> variables = estimatedIm.getVariables();
        DataSet columnDataSet2 = dataSet.subsetColumns(variables);
        this.reorderedDataSetDiscrete = columnDataSet2;
        DiscreteProbs discreteProbs = new DataSetProbs(columnDataSet2);

        // We will use the same estimation methods as the updaters, to ensure
        // compatibility.
        Proposition assertion = Proposition.tautology(estimatedIm);
        Proposition condition = Proposition.tautology(estimatedIm);
        Evidence evidence2 = Evidence.tautology(estimatedIm);

        int numNodes = estimatedIm.getNumNodes();

        for (int node = 0; node < numNodes; node++) {
            int numRows = estimatedIm.getNumRows(node);
            int numCols = estimatedIm.getNumColumns(node);
            int[] parents = estimatedIm.getParents(node);

            for (int row = 0; row < numRows; row++) {
                int[] parentValues = estimatedIm.getParentValues(node, row);

                for (int col = 0; col < numCols; col++) {

                    // Remove values from the proposition in various ways; if
                    // a combination exists in the end, calculate a contitional
                    // probability.
                    assertion.setToTautology();
                    condition.setToTautology();

                    for (int i = 0; i < numNodes; i++) {
                        for (int j = 0; j < evidence2.getNumCategories(i); j++) {
                            if (!evidence2.getProposition().isAllowed(i, j)) {
                                condition.removeCategory(i, j);
                            }
                        }
                    }

                    assertion.disallowComplement(node, col);

                    for (int k = 0; k < parents.length; k++) {
                        condition.disallowComplement(parents[k], parentValues[k]);
                    }

                    if (condition.existsCombination()) {
                        double p = discreteProbs.getConditionalProb(assertion, condition);
                        estimatedIm.setProbability(node, row, col, p);
                    } else {
                        estimatedIm.setProbability(node, row, col, Double.NaN);
                    }
                }
            }
        }

        return estimatedIm;
    }

    public DataSet getReorderedDataSet() {
        return reorderedDataSetDiscrete;
    }
}





