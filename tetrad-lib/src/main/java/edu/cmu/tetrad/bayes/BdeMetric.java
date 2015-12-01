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

import cern.jet.stat.Gamma;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ProbUtils;

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

        Graph graph = bayesPm.getDag();

        int n = graph.getNumNodes();

        observedCounts = new int[n][][];
        priorProbs = new double[n][][];

        int[][] observedCountsRowSum = new int[n][];
        priorProbsRowSum = new double[n][];

        bayesIm = new MlBayesIm(bayesPm);

        for (int i = 0; i < n; i++) {
            //int numRows = bayesImMixed.getNumRows(i);
            int numRows = bayesIm.getNumRows(i);
            observedCounts[i] = new int[numRows][];
            priorProbs[i] = new double[numRows][];

            observedCountsRowSum[i] = new int[numRows];
            priorProbsRowSum[i] = new double[numRows];

            //for(int j = 0; j < bayesImMixed.getNumRows(i); j++) {
            for (int j = 0; j < numRows; j++) {

                observedCountsRowSum[i][j] = 0;
                priorProbsRowSum[i][j] = 0;

                //int numCols = bayesImMixed.getNumColumns(i);
                int numCols = bayesIm.getNumColumns(i);
                observedCounts[i][j] = new int[numCols];
                priorProbs[i][j] = new double[numCols];
            }
        }

        //At this point set values in both observedCounts and priorProbs
        computeObservedCounts();
        //Set all priorProbs (i.e. estimated counts) to 1.0.  Eventually they may be
        //supplied as a parameter of the constructor of this class.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < bayesIm.getNumRows(i); j++) {
                for (int k = 0; k < bayesIm.getNumColumns(i); k++) {
                    priorProbs[i][j][k] = 1.0;
                }
            }
        }


        for (int i = 0; i < n; i++) {
            for (int j = 0; j < bayesIm.getNumRows(i); j++) {
                for (int k = 0; k < bayesIm.getNumColumns(i); k++) {
                    observedCountsRowSum[i][j] += observedCounts[i][j][k];
                    priorProbsRowSum[i][j] += priorProbs[i][j][k];
                }
            }
        }

        double product = 1.0;

        //Debug print
        //System.out.println("counts and priors");
        //for(int i = 0; i < n; i++)
        //    for(int j = 0; j < bayesIm.getNumRows(i); j++) {
        //        System.out.println(observedCountsRowSum[i][j] + " " + priorProbsRowSum[i][j]);
        //    }

        for (int i = 0; i < n; i++) {

            int qi = bayesIm.getNumRows(i);
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

                int ri = bayesIm.getNumColumns(i);
                double prodk = 1.0;
                for (int k = 0; k < ri; k++) {
                    try {
                        prodk *= Gamma.gamma(
                                priorProbs[i][j][k] + observedCounts[i][j][k]) /
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

    public double scoreLnGam() {

        double[][][] priorProbs;
        double[][] priorProbsRowSum;

        Graph graph = bayesPm.getDag();

        int n = graph.getNumNodes();

        observedCounts = new int[n][][];
        priorProbs = new double[n][][];

        int[][] observedCountsRowSum = new int[n][];
        priorProbsRowSum = new double[n][];

        bayesIm = new MlBayesIm(bayesPm);

        for (int i = 0; i < n; i++) {
            //int numRows = bayesImMixed.getNumRows(i);
            int numRows = bayesIm.getNumRows(i);
            observedCounts[i] = new int[numRows][];
            priorProbs[i] = new double[numRows][];

            observedCountsRowSum[i] = new int[numRows];
            priorProbsRowSum[i] = new double[numRows];

            //for(int j = 0; j < bayesImMixed.getNumRows(i); j++) {
            for (int j = 0; j < numRows; j++) {

                observedCountsRowSum[i][j] = 0;
                priorProbsRowSum[i][j] = 0;

                //int numCols = bayesImMixed.getNumColumns(i);
                int numCols = bayesIm.getNumColumns(i);
                observedCounts[i][j] = new int[numCols];
                priorProbs[i][j] = new double[numCols];
            }
        }

        //At this point set values in both observedCounts and priorProbs
        computeObservedCounts();
        //Set all priorProbs (i.e. estimated counts) to 1.0.  Eventually they may be
        //supplied as a parameter of the constructor of this class.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < bayesIm.getNumRows(i); j++) {
                for (int k = 0; k < bayesIm.getNumColumns(i); k++) {
                    priorProbs[i][j][k] = 1.0;
                }
            }
        }


        for (int i = 0; i < n; i++) {
            for (int j = 0; j < bayesIm.getNumRows(i); j++) {
                for (int k = 0; k < bayesIm.getNumColumns(i); k++) {
                    observedCountsRowSum[i][j] += observedCounts[i][j][k];
                    priorProbsRowSum[i][j] += priorProbs[i][j][k];
                }
            }
        }

        //double outerProduct = 1.0;
        double sum = 0.0;

        //Debug print
        //System.out.println("counts and priors");
        //for(int i = 0; i < n; i++)
        //    for(int j = 0; j < bayesIm.getNumRows(i); j++) {
        //        System.out.println(observedCountsRowSum[i][j] + " " + priorProbsRowSum[i][j]);
        //    }

        for (int i = 0; i < n; i++) {

            int qi = bayesIm.getNumRows(i);
            //double prodj = 1.0;
            double sumj = 0.0;
            for (int j = 0; j < qi; j++) {

                try {
                    double numerator =
                            ProbUtils.lngamma(priorProbsRowSum[i][j]);
                    double denom = ProbUtils.lngamma(priorProbsRowSum[i][j] +
                            observedCountsRowSum[i][j]);
                    //System.out.println("num = " + numerator + " denom = " + denom);
                    sumj += (numerator - denom);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                int ri = bayesIm.getNumColumns(i);

                //double prodk = 1.0;
                double sumk = 0.0;
                for (int k = 0; k < ri; k++) {
                    try {
                        sumk += ProbUtils.lngamma(
                                priorProbs[i][j][k] + observedCounts[i][j][k]) -
                                ProbUtils.lngamma(priorProbs[i][j][k]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                sumj += sumk;
            }
            sum += sumj;
        }

        return sum;
    }

    private void computeObservedCounts() {
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            DiscreteVariable var = (DiscreteVariable) dataSet.getVariables()
                    .get(j);
            String varName = var.getName();
            Node varNode = bayesPm.getDag().getNode(varName);
            int varIndex = bayesIm.getNodeIndex(varNode);

            int[] parentVarIndices = bayesIm.getParents(varIndex);
            //System.out.println("graph = " + graph);

            //for(int col = 0; col < ar.getNumSplits(); col++)
            //    System.out.println("Category " + col + " = " + ar.getCategory(col));

            //System.out.println("Updating estimated counts for node " + varName);
            //This segment is for variables with no parents:
            if (parentVarIndices.length == 0) {
                //System.out.println("No parents");
                for (int col = 0; col < var.getNumCategories(); col++) {
                    observedCounts[j][0][col] = 0;
                }


                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    //System.out.println("Case " + i);
                    //If this case has a value for ar

                    observedCounts[j][0][dataSet.getInt(i, j)] += 1.0;
                    //System.out.println("Adding 1.0 to " + varName +
                    //        " row 0 category " + mixedData[j][i]);


                }

                //Print estimated counts:
                //System.out.println("Estimated counts:  ");

                //Print counts for each value of this variable with no parents.
                //for(int m = 0; m < ar.getNumSplits(); m++)
                //    System.out.print("    " + m + " " + observedCounts[j][0][m]);
                //System.out.println();
            } else {    //For variables with parents:
                int numRows = bayesIm.getNumRows(varIndex);

                for (int row = 0; row < numRows; row++) {
                    int[] parValues = bayesIm.getParentValues(varIndex, row);

                    for (int col = 0; col < var.getNumCategories(); col++) {
                        observedCounts[varIndex][row][col] = 0;
                    }

                    for (int i = 0; i < dataSet.getNumRows(); i++) {
                        //for a case where the parent values = parValues increment the estCount

                        boolean parentMatch = true;

                        for (int p = 0; p < parentVarIndices.length; p++) {
                            if (parValues[p] !=
                                    dataSet.getInt(i, parentVarIndices[p])) {
                                parentMatch = false;
                                break;
                            }
                        }

                        if (!parentMatch) {
                            continue;  //Not a matching case; go to next.
                        }

                        observedCounts[j][row][dataSet.getInt(i, j)] += 1;
                    }

                    //}

                    //Print estimated counts:
                    //System.out.println("Estimated counts:  ");
                    //System.out.println("    Parent values:  ");
                    //for (int i = 0; i < parentVarIndices.length; i++) {
                    //    Variable par = (Variable) dataSet.getVariableNames().get(parentVarIndices[i]);
                    //    System.out.print("    " + par.getName() + " " + parValues[i] + "    ");
                    //}
                    //System.out.println();

                    //for(int m = 0; m < ar.getNumSplits(); m++)
                    //    System.out.print("    " + m + " " + observedCounts[j][row][m]);
                    //System.out.println();
                }

            }


        }        //else
    }


}





