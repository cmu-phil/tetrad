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

import cern.jet.stat.Gamma;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

/**
 * <p>Provides a static method for computing the score of a model, called the
 * BDe metric, given a dataset (assumes no missing values) and a Bayes
 * parameterized network (assumes no latent variables).</p> <p>See "Learning
 * Bayesian Networks:  The Combination of Knowledge and Statistical Data" by
 * David Heckerman, Dan Geiger, and David M. Chickering. Microsoft Technical
 * Report MSR-TR-94-09.</p>
 *
 * @author Frank Wimberly
 */
final class BdeMetric {
    private final DataSet dataSet;
    private final BayesPm bayesPm;
    private BayesIm bayesIm;

    private int[][][] observedCounts;

    public BdeMetric(DataSet dataSet, BayesPm bayesPm) {

        this.dataSet = dataSet;
        this.bayesPm = bayesPm;
    }

    /**
     * This method computes the BDe score, which is the probability of the data
     * given the model and the priors.  See (35) in the above-referenced paper.
     */
    public double score() {


        double[][][] priorProbs;
        double[][] priorProbsRowSum;

        Graph graph = this.bayesPm.getDag();

        int n = graph.getNumNodes();

        this.observedCounts = new int[n][][];
        priorProbs = new double[n][][];

        int[][] observedCountsRowSum = new int[n][];
        priorProbsRowSum = new double[n][];

        this.bayesIm = new MlBayesIm(this.bayesPm);

        for (int i = 0; i < n; i++) {
            //int numRows = bayesImMixed.getNumRows(i);
            int numRows = this.bayesIm.getNumRows(i);
            this.observedCounts[i] = new int[numRows][];
            priorProbs[i] = new double[numRows][];

            observedCountsRowSum[i] = new int[numRows];
            priorProbsRowSum[i] = new double[numRows];

            //for(int j = 0; j < bayesImMixed.getNumRows(i); j++) {
            for (int j = 0; j < numRows; j++) {

                observedCountsRowSum[i][j] = 0;
                priorProbsRowSum[i][j] = 0;

                //int numCols = bayesImMixed.getNumColumns(i);
                int numCols = this.bayesIm.getNumColumns(i);
                this.observedCounts[i][j] = new int[numCols];
                priorProbs[i][j] = new double[numCols];
            }
        }

        //At this point set values in both observedCounts and priorProbs
        computeObservedCounts();
        //Set all priorProbs (i.e. estimated counts) to 1.0.  Eventually they may be
        //supplied as a parameter of the constructor of this class.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < this.bayesIm.getNumRows(i); j++) {
                for (int k = 0; k < this.bayesIm.getNumColumns(i); k++) {
                    priorProbs[i][j][k] = 1.0;
                }
            }
        }


        for (int i = 0; i < n; i++) {
            for (int j = 0; j < this.bayesIm.getNumRows(i); j++) {
                for (int k = 0; k < this.bayesIm.getNumColumns(i); k++) {
                    observedCountsRowSum[i][j] += this.observedCounts[i][j][k];
                    priorProbsRowSum[i][j] += priorProbs[i][j][k];
                }
            }
        }

        double product = 1.0;

        for (int i = 0; i < n; i++) {

            int qi = this.bayesIm.getNumRows(i);
            double prodj = 1.0;
            for (int j = 0; j < qi; j++) {

                try {
                    double numerator = Gamma.gamma(priorProbsRowSum[i][j]);
                    double denom = Gamma.gamma(priorProbsRowSum[i][j] +
                            observedCountsRowSum[i][j]);
                    //System.out.println("num = " + numerator + " denom = " + denom);
                    prodj *= (numerator / denom);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                int ri = this.bayesIm.getNumColumns(i);
                double prodk = 1.0;
                for (int k = 0; k < ri; k++) {
                    try {
                        prodk *= Gamma.gamma(
                                priorProbs[i][j][k] + this.observedCounts[i][j][k]) /
                                Gamma.gamma(priorProbs[i][j][k]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                prodj *= prodk;
            }
            product *= prodj;
        }

        return product;
    }

    private void computeObservedCounts() {
        for (int j = 0; j < this.dataSet.getNumColumns(); j++) {
            DiscreteVariable var = (DiscreteVariable) this.dataSet.getVariables()
                    .get(j);
            String varName = var.getName();
            Node varNode = this.bayesPm.getDag().getNode(varName);
            int varIndex = this.bayesIm.getNodeIndex(varNode);

            int[] parentVarIndices = this.bayesIm.getParents(varIndex);
            //System.out.println("graph = " + graph);

            //for(int col = 0; col < ar.getNumSplits(); col++)
            //    System.out.println("Category " + col + " = " + ar.getCategory(col));

            //System.out.println("Updating estimated counts for node " + varName);
            //This segment is for variables with no parents:
            if (parentVarIndices.length == 0) {
                //System.out.println("No parents");
                for (int col = 0; col < var.getNumCategories(); col++) {
                    this.observedCounts[j][0][col] = 0;
                }


                for (int i = 0; i < this.dataSet.getNumRows(); i++) {
                    this.observedCounts[j][0][this.dataSet.getInt(i, j)] += 1.0;
                }
            } else {    //For variables with parents:
                int numRows = this.bayesIm.getNumRows(varIndex);

                for (int row = 0; row < numRows; row++) {
                    int[] parValues = this.bayesIm.getParentValues(varIndex, row);

                    for (int col = 0; col < var.getNumCategories(); col++) {
                        this.observedCounts[varIndex][row][col] = 0;
                    }

                    for (int i = 0; i < this.dataSet.getNumRows(); i++) {
                        //for a case where the parent values = parValues increment the estCount

                        boolean parentMatch = true;

                        for (int p = 0; p < parentVarIndices.length; p++) {
                            if (parValues[p] !=
                                    this.dataSet.getInt(i, parentVarIndices[p])) {
                                parentMatch = false;
                                break;
                            }
                        }

                        if (!parentMatch) {
                            continue;  //Not a matching case; go to next.
                        }

                        this.observedCounts[j][row][this.dataSet.getInt(i, j)] += 1;
                    }
                }

            }
        }
    }


}





