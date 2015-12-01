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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

/**
 * Estimates a DirichletBayesIm from a DirichletBayesIm (the prior) and a data
 * set.
 *
 * @author Joseph Ramsey
 */
public final class DirichletEstimator {
    public static DirichletBayesIm estimate(DirichletBayesIm prior,
                                            DataSet dataSet) {
        if (prior == null) {
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
        BayesUtils.ensureVarsInData(prior.getVariables(), dataSet);

        // Create the posterior.
        BayesPm bayesPm = prior.getBayesPm();
        DirichletBayesIm posterior = DirichletBayesIm.blankDirichletIm(bayesPm);

        // Number of rows of data
        int numPoints = dataSet.getNumRows();

        // Loop over all nodes in prior.
        for (int n = 0; n < prior.getNumNodes(); ++n) {

            // Make any easy access table of node data @ 0 and parent data @ p+1.
            int[] varIndices = new int[prior.getNumParents(n) + 1];

            Node node = prior.getNode(n);
            String name = node.getName();
            varIndices[0] = dataSet.getColumn(dataSet.getVariable(name));

            for (int p = 0; p < prior.getNumParents(n); p++) {
                Node parentNode = prior.getNode(prior.getParent(n, p));
                name = parentNode.getName();
                varIndices[p + 1] =
                        dataSet.getColumn(dataSet.getVariable(name));
            }

            // Loop over conditioning set.
            for (int row = 0; row < prior.getNumRows(n); row++) {
                int numCategories = bayesPm.getNumCategories(node);

                // Count occurrences of category.
                int[] nCount = new int[numCategories];
                int[] pVals = prior.getParentValues(n, row);

                // Count the occurrence of each category satisfying the
                // various condition in the data.
                for (int i = 0; i < numPoints; i++) {

                    //first make sure conditions are satisfied
                    boolean satisfied = true;

                    for (int p = 0; p < prior.getNumParents(n); p++) {

                        // Ignore cases where one of the parents has a
                        // missing value.
                        if (dataSet.getInt(i, varIndices[p + 1]) ==
                                DiscreteVariable.MISSING_VALUE) {
                            satisfied = false;
                            break;
                        }

                        if (pVals[p] != dataSet.getInt(i, varIndices[p + 1])) {
                            satisfied = false;
                            break;
                        }
                    }

                    if (dataSet.getInt(i, varIndices[0]) == DiscreteVariable
                            .MISSING_VALUE) {
                        satisfied = false;
                    }

                    if (satisfied) {
                        nCount[dataSet.getInt(i, varIndices[0])]++;
                    }
                }

                // include prior
                for (int i = 0; i < numCategories; ++i) {
                    double priorValue = prior.getPseudocount(n, row, i);
                    double value = nCount[i] + priorValue;
                    posterior.setPseudocount(n, row, i, value);
                }
            }
        }

        return posterior;
    }
}





