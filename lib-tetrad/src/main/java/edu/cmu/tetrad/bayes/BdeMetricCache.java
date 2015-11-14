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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ProbUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>Provides a method for computing the score of a model, called the BDe
 * metric (Bayesian Dirchlet likelihood equivalence), given a dataset (assumes
 * no missing values) and a Bayes parameterized network (assumes no latent
 * variables).</p> <p>This version has a method that computes the score for a
 * given factor of a model, where a factor is determined by a node and its
 * parents.  It stores scores in a map whose argument is an ordered pair
 * consisting of 1) a node and 2) set of parents.  The score for the entire
 * model is the product of the scores of its factors.  Since the log of the
 * gamma function is used here the sum of the logs is computed as the score.
 * Compare this with the score method in the BdeMetric class which computes the
 * score for the entire model in one pass.  The advantage of the approach in
 * this class is that it is more efficient in the context of a search algorithm
 * where different models are scored but where many of them will have the same
 * factors. This class stores the score (relative to the dataset) for any <node,
 * set of parents> pair and thus avoids the expensive log gamma function calls.
 * Instead it looks in the map scores to see if it has already computed the
 * score and, if so, returns the previously computed value.</p> <p>See
 * "Learning Bayesian Networks:  The Combination of Knowledge and Statistical
 * Data" by David Heckerman, Dan Geiger, and David M. Chickering. Microsoft
 * Technical Report MSR-TR-94-09.</p>
 *
 * @author Frank Wimberly
 */
public final class BdeMetricCache {
    private final DataSet dataSet;
    private final List<Node> variables;

    private final BayesPm bayesPm;  //Determines the list of variables (nodes)

    private final Map<NodeParentsPair, Double> scores;
    private final Map<NodeParentsPair, Integer> scoreCounts;

    private double[][] observedCounts;

    public BdeMetricCache(DataSet dataSet, BayesPm bayesPm) {
        this.bayesPm = bayesPm;
        this.dataSet = dataSet;
        this.scores = new HashMap<NodeParentsPair, Double>();
        this.scoreCounts = new HashMap<NodeParentsPair, Integer>();
        this.variables = dataSet.getVariables();
    }

    /**
     * Computes the BDe score, using the logarithm of the gamma function,
     * relative to the data, of the factor determined by a node and its
     * parents.
     */
    public double scoreLnGam(Node node, Set<Node> parents, BayesPm bayesPmMod,
            BayesIm bayesIm) {
        //        System.out.println("In Cache scoreLnGam ");
        //        System.out.print("Processing node " + node.getName() + " with parents ");

        //        for (Iterator itp = parents.iterator(); itp.hasNext();) {
        //            System.out.print(((Node) itp.next()).getName() + " ");
        //        }
        //        System.out.println();

        //A factor of a model is determined by a node and its parents in that model.
        //The NodeParentsPair inner class provides a means to instantiate such
        //a pair so that it can be used as an argument to the Map scores.
        NodeParentsPair nodeAndParents = new NodeParentsPair(node, parents);
        double score;

        //If the score of this factor has already been computed and stored, retrieve it from the
        //Map scores.
        if (scores.containsKey(nodeAndParents)) {
            System.out.println(
                    node + " Score came from map--counts not computed.");
            score = scores.get(nodeAndParents);

            return score;
        }
        else {      //otherwise compute it and store it in the map.

            //String[] varNames = dataSet.getVariableNames();

            //Create arrays for storing observed counts and prior probabilities for
            //this factor.  In observedCounts and priorProbs, for instance, there will be as
            //many rows as there are combinations of values of the parents and
            //as many columns as there are categories of the child variable.
            Node[] parentArray = new Node[parents.size()];
            for (int i = 0; i < parentArray.length; i++) {
                parentArray[i] = (Node) (parents.toArray()[i]);
            }

            /*
            int numRows = 1;
            for (int i = 0; i < parentArray.length; i++) {
                String name = parentArray[i].getName();

                int index = getVarIndex(name);
                //int numCats = ((DiscreteVariable) variables.get(index)).getNumSplits();
                int numCats = bayesPm.getNumSplits(parentArray[i]);
                numRows *= numCats;
            }
            */

            BayesIm bayesImMod = new MlBayesIm(bayesPmMod);
            int numRows = bayesImMod.getNumRows(bayesImMod.getNodeIndex(node));

            double[][] priorProbs;
            double[] priorProbsRowSum;

            observedCounts = new double[numRows][];
            priorProbs = new double[numRows][];

            //The following two arrays are used to store the sum of the counts and
            //prior probablities for each row.
            double[] observedCountsRowSum = new double[numRows];
            priorProbsRowSum = new double[numRows];

            //The int index will be the index of the variable associated with node
            //in dataset.
//            int index = getVarIndex(name);
            //System.out.println("NAME:  " + name);
            //int numCols = ((DiscreteVariable) variables.get(index)).getNumSplits();
            int numCols = bayesPm.getNumCategories(node);

            //Initialize the observed counts and prior probabilities arrays.
            for (int j = 0; j < numRows; j++) {

                observedCountsRowSum[j] = 0;
                priorProbsRowSum[j] = 0;

                observedCounts[j] = new double[numCols];
                priorProbs[j] = new double[numCols];
            }

            //At this point set values in observedCounts according to the cases in the dataset.
            if (bayesIm == null) {
                computeObservedCounts(node, parentArray);
            }
            else
            //computeObservedCountsMD(node, bayesPmMod, bayesIm);
            {
                computeObservedCountsMD(node, bayesPmMod, bayesIm);
            }

            //Set all priorProbs (i.e. estimated counts) to 1.0.  Eventually they may be
            //supplied as a parameter of the constructor of this class.
            for (int j = 0; j < numRows; j++) {
                for (int k = 0; k < numCols; k++)
                //priorProbs[j][k] = 1.0;
                {
                    priorProbs[j][k] = 1.0 /
                            (numRows * numCols);   //Per David Danks 12/21/04
                }
            }

            for (int j = 0; j < numRows; j++) {
                //                System.out.print("Observed counts row " + j);
                for (int k = 0; k < numCols; k++) {

                    //DEBUG PRINT:
                    //                    System.out.print(" " + observedCounts[j][k]);

                    observedCountsRowSum[j] += observedCounts[j][k];
                    priorProbsRowSum[j] += priorProbs[j][k];
                }
                //                System.out.println();
            }

            //The loops below compute the products on page 25 of the Heckerman et al. paper.
            //Actually sums are computed since logarithms of the terms are used.  That is
            //why the log gamma function (lngamma) is used.
            double sum = 0.0;

//            int n = nodes.length; //The number of factors (i.e. variables).

            for (int j = 0; j < numRows; j++) {

                try {
                    double numerator = ProbUtils.lngamma(priorProbsRowSum[j]);
                    double denom = ProbUtils.lngamma(
                            priorProbsRowSum[j] + observedCountsRowSum[j]);

                    //gammln is a slightly more accurate method than lngamma.
                    //double numerator = ProbUtils.gammln(priorProbsRowSum[j]);
                    //double denom =
                    //        ProbUtils.gammln(priorProbsRowSum[j] + observedCountsRowSum[j]);

                    //System.out.println("num = " + numerator + " denom = " + denom);
                    sum += (numerator - denom);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                //double prodk = 1.0;
                double sumk = 0.0;
                for (int k = 0; k < numCols; k++) {
                    try {
                        sumk += ProbUtils.lngamma(
                                priorProbs[j][k] + observedCounts[j][k]) -
                                ProbUtils.lngamma(priorProbs[j][k]);

                        /*      Debug print
                        if(Double.isNaN(sumk)) {
                            System.out.println("sumk is Nan");
                            System.out.println(priorProbs[j][k] + " " + observedCounts[j][k]);
                        }
                        */

                        //sumk += ProbUtils.gammln(priorProbs[j][k] + observedCounts[j][k]) -
                        //        ProbUtils.gammln(priorProbs[j][k]);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                sum += sumk;
            }

            score = sum;

        }

        //Store the node and parents pair and the score in the scores Map.
        //Also return the score.
        Double scoreDouble = score;
        scores.put(nodeAndParents, scoreDouble);

        //System.out.println("For node " + node.getName() + " with parents " + parents);
        //System.out.println("Score = " + score);
        return score;
    }


    private void computeObservedCountsMD(Node node, BayesPm bayesPmTest,
            BayesIm bayesIm) {
        //private void computeObservedCountsMD(Node node, BayesPm bayesPm, BayesIm bayesIm) {
        //        System.out.println("Using the new  obs counts MD");
        int numCases = dataSet.getNumRows();
        int numVariables = variables.size();

        //Graph graph1 = bayesIm.getBayesPm().getGraph();    //The original model
        //Graph graph2 = bayesPm.getGraph();                 //The variation
        Graph graph = bayesIm.getBayesPm().getDag();

//        int[][] rawData = dataSet.getIntMatrixTransposedTrimmed();
        RowSummingExactUpdater rseu = new RowSummingExactUpdater(bayesIm);
        //BayesImProbs imProbs = new BayesImProbs(bayesIm);   XXX

        String name = node.getName();

        int index = getVarIndex(name);   //Index of the variable in the dataset.
//        DiscreteVariable ar = (DiscreteVariable) variables.get(index);

        //int numCols = ar.getNumSplits();
        int numCols = bayesPmTest.getNumCategories(node);

        BayesIm bayesImTest = new MlBayesIm(bayesPmTest);
        int nodeIndexImTest = bayesImTest.getNodeIndex(node);
        int numRows = bayesImTest.getNumRows(nodeIndexImTest);

        int varIndex = bayesImTest.getNodeIndex(
                node);    //Index of the variable in the Bayes net
        int[] parentVarIndices = bayesImTest.getParents(varIndex);

        //For variables with parents
        //int numRows = bayesImTest.getNumRows(varIndex);
        if (parentVarIndices.length == 0) {              //node has no parents

            for (int col = 0; col < numCols; col++) {
                observedCounts[0][col] = 0;
            }

            for (int i = 0; i < numCases; i++) {
//                if (rawData[index][i] != -99) {
                if (dataSet.getInt(i, index) != -99) {
                    observedCounts[0][dataSet.getInt(i, index)] += 1.0;
                }
                else {
                    //Find marginal probability, given the obs data in this case p(v=0)
                    Evidence evidenceThisCase = Evidence.tautology(bayesIm);
                    boolean existsEvidence = false;

                    //Define evidence for updating by using the values of the other vars.
                    for (int k = 0; k < numVariables; k++) {
                        /*
                        if(k == index) continue;
                        Variable otherVar = (Variable) variables.get(k);
                        if(rawData[k][i] == -99) continue;
                        */

                        if (dataSet.getInt(i, k) == -99) {
                            continue;
                        }
                        Node otherVar = variables.get(k);

                        existsEvidence = true;
                        String otherVarName = otherVar.getName();
                        //Node otherNode = graph1.getNode(otherVarName);
                        Node otherNode = graph.getNode(otherVarName);
                        int otherIndex = bayesIm.getNodeIndex(otherNode);

                        evidenceThisCase.getProposition().setCategory(
                                otherIndex, dataSet.getInt(i, k));

                    }

                    //if(!existsEvidence) continue;  //No other variable contained useful data.

                    rseu.setEvidence(evidenceThisCase);

                    //Compute marginal probabilities of each value  of ar and increase
                    //observed counts accordingly
                    for (int m = 0; m < numCols; m++) {
                        double p = rseu.getMarginal(varIndex, m);
                        if (Double.isNaN(p)) {
                            System.out.println(
                                    "esixtsEvidence = " + existsEvidence);
                            System.out.println("getMarginal returns NaN for ");
                            System.exit(0);
                        }
                        //double p = imProbs.getProb(evidenceThisCase.getProposition());  XXX
                        observedCounts[0][m] += p;
                    }

                }
            }
        }
        else {
            for (int row = 0; row < numRows; row++) {
                int[] parValues = bayesImTest.getParentValues(varIndex, row);

                for (int col = 0; col < numCols; col++) {
                    observedCounts[row][col] = 0.0;
                }

                for (int i = 0; i < numCases; i++) {
                    //For a case where the parent values = parValues increment the counts

                    boolean parentMatch = true;

                    for (int p = 0; p < parentVarIndices.length; p++) {
                        if (parValues[p] !=
                                dataSet.getInt(i, parentVarIndices[p]) &&
                                dataSet.getInt(i, parentVarIndices[p]) != -99) {
                            parentMatch = false;
                            break;
                        }
                    }

                    if (!parentMatch) {
                        continue;
                    }

                    boolean parentMissing = false;

                    for (int parentVarIndice : parentVarIndices) {
                        if (dataSet.getInt(i, parentVarIndice) == -99) {
                            parentMissing = true;
                            break;
                        }
                    }

                    if (dataSet.getInt(i, index) != -99 && !parentMissing) {
                        observedCounts[row][dataSet.getInt(i, index)] += 1.0;
                        continue;    //Next case
                    }

                    //For a case with missing data (either ar or one of its parents)
                    //compute the joint marginal distrubiton for ar and this combination
                    //of values of its parents and update observedCounts accordingly.

                    //To compute marginals create the evidence
                    boolean existsEvidence = false;

                    Evidence evidenceThisCase = Evidence.tautology(bayesIm);

                    // "evidenceVars" not used.
//                List<String> evidenceVars = new LinkedList<String>();
//                for (int k = 0; k < numVariables; k++) {
//                    Variable otherVar = variables.get(k);
//                    if (dataSet.getInt(i, k) == -99) {
//                        continue;
//                    }
//                    existsEvidence = true;
//                    String otherVarName = otherVar.getName();
//                    //Node otherNode = graph1.getNode(otherVarName);
//                    Node otherNode = graph.getNode(otherVarName);
//                    int otherIndex = bayesIm.getNodeIndex(otherNode);
//                    evidenceThisCase.getProposition().setCategory(
//                            otherIndex, dataSet.getInt(i, k));
//                    evidenceVars.add(otherVarName);
//                }

                    //if(!existsEvidence) continue;

                    rseu.setEvidence(evidenceThisCase);

                    int[] parPlusChildIndices =
                            new int[parentVarIndices.length + 1];
                    int[] parPlusChildValues =
                            new int[parentVarIndices.length + 1];

                    parPlusChildIndices[0] = varIndex;
                    for (int pc = 1; pc < parPlusChildIndices.length; pc++) {
                        parPlusChildIndices[pc] = parentVarIndices[pc - 1];
                        parPlusChildValues[pc] = parValues[pc - 1];
                    }

                    for (int m = 0; m < numCols; m++) {
                        parPlusChildValues[0] = m;

                        //double p = imProbs.getProb(evidenceThisCase.getProposition());  XXX
                        //next stmt commented out on 11/29/04 in favor of following stmt.
                        double p = rseu.getJointMarginal(parPlusChildIndices,
                                parPlusChildValues);
                        //double p = rseu.getMarginal(varIndex, m);
                        if (Double.isNaN(p)) {
                            System.out.println(
                                    "existsEvidence = " + existsEvidence);
                            System.out.println(
                                    "getJointMarginal returns NaN for ");
                            System.exit(0);
                        }
                        observedCounts[row][m] += p;

                    }

                }       //numCases


            }    //row loop
        }

        //else/*   Commented out on 11.22.04 to see if it causes a problem.
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col ++) {
                observedCounts[row][col] *= numCases;
            }
        }

    }

//    //This is the version that uses BayesImProbs
//    private void computeObservedCountsMD0(Node node, BayesPm bayesPmTest,
//            BayesIm bayesIm) {
//        Graph graph1 = bayesIm.getBayesPm().getDag();
//        Graph graph2 = bayesPmTest.getDag();
//
//        BayesIm bayesImTest = new MlBayesIm(bayesPmTest);
//
//        BayesImProbs imProbs = new BayesImProbs(bayesIm);
//
//        int nodeIndexPm = bayesImTest.getNodeIndex(node);
//        int nodeIndexIm = bayesIm.getNodeIndex(node);
//
//        int numRows = bayesImTest.getNumRows(nodeIndexPm);
//        int numCols = bayesImTest.getNumColumns(nodeIndexPm);
//
//
//        observedCounts = new double[numRows][];
//        for (int j = 0; j < numRows; j++) {
//            observedCounts[j] = new double[numCols];
//        }
//
//
//        /*if (graph2.equals(graph1)) {
//
//            //System.out.println("For node " + node.getName());
//            //System.out.println("in computeObservedCountsMD num rows = " + numRows);
//
//            for (int i = 0; i < numRows; i++)  {
//
//                for (int j = 0; j < numCols; j++)
//                    observedCounts[i][j] = bayesIm.getWordRatio(nodeIndexIm, i, j) * numCases;
//            }
//        }
//
//        else */
//        {
//
//            Evidence evidence = new Evidence(bayesIm);
//
//            int nodeIndex = evidence.getNodeIndex(node.getName());
//
//            for (int val = 0; val < numCols; val++) {
//                evidence.getProposition().setCategory(nodeIndex, val);
//
//                for (int row = 0; row < numRows; row++) {
//
//                    int[] parentIndices = bayesImTest.getParents(nodeIndexPm);
//                    int[] parVals = bayesImTest.getParentValues(nodeIndexPm,
//                            row);
//
//                    for (int j = 0; j < parentIndices.length; j++) {
//                        Node nodeJ = bayesImTest.getNode(parentIndices[j]);
//                        int indexOfJ = bayesIm.getNodeIndex(nodeJ);
//                        evidence.getProposition().setCategory(indexOfJ,
//                                parVals[j]);
//                    }
//
//                    double p = imProbs.getProb(evidence.getProposition());
//
//                    observedCounts[row][val] = p * numCases;
//                }
//            }
//
//        }
//    }

//    //David's "simple" algorithm.
//    private void computeObservedCountsMD1(Node node, BayesPm bayesPmTest,
//            BayesIm bayesIm) {
//        Graph graph1 = bayesIm.getBayesPm().getDag();
//        Graph graph2 = bayesPmTest.getDag();
//
//        RowSummingExactUpdater rseu1 = new RowSummingExactUpdater(bayesIm);
//        RowSummingExactUpdater rseu2 = new RowSummingExactUpdater(bayesIm);
//
//        //System.out.println("Two graphs used in computing estimated counts:  ");
//        //System.out.println("Original:  " + graph1 + "\n\n");
//        //System.out.println("Modified:  " + graph2);
//
//        BayesIm bayesImTest = new MlBayesIm(bayesPmTest);
//
//        int nodeIndexPm = bayesImTest.getNodeIndex(node);
//        int nodeIndexIm = bayesIm.getNodeIndex(node);
//
//        int numRows = bayesImTest.getNumRows(nodeIndexPm);
//        int numCols = bayesImTest.getNumColumns(nodeIndexPm);
//
//        observedCounts = new double[numRows][];
//        for (int j = 0; j < numRows; j++) {
//            observedCounts[j] = new double[numCols];
//        }
//
//
//        /*
//        double[] probsRowSum = new double[numRows];
//        for(int i = 0; i < numRows; i++)
//            for(int j = 0; j < numCols; j++)
//                probsRowSum[i] += bayesIm.getWordRatio(nodeIndexIm, i, j);
//        */
//
//        if (graph2.equals(graph1)) {
//
//            //System.out.println("For node " + node.getName());
//            //System.out.println("in computeObservedCountsMD num rows = " + numRows);
//
//            for (int i = 0; i < numRows; i++) {
//                for (int j = 0; j < numCols; j++) {
//                    observedCounts[i][j] = bayesIm.getWordRatio(nodeIndexIm,
//                            i, j) * numCases;
//                }
//            }
//        }
//        else {
//            List<Node> parsIn1 = graph1.getParents(node);
//            List<Node> parsIn2 = graph2.getParents(node);
//
//            Set<Node> parentsIn1 = new HashSet<Node>(parsIn1);
//            Set<Node> parentsIn2 = new HashSet<Node>(parsIn2);
//
//            Set<Node> overlap = new HashSet<Node>();
//
//            for (Iterator<Node> it1 = parentsIn1.iterator(); it1.hasNext();) {
//                Node in1 = it1.next();
//                if (parentsIn2.contains(in1)) {
//                    overlap.add(in1);
//                }
//            }
//
//            //COMPUTE THE SET 1_ONLY which contains those nodes which are parents of node in
//            //graph1 and not in graph2
//            Set<Node> oneOnly = new HashSet<Node>();
//            for (Iterator<Node> itOne = parentsIn1.iterator(); itOne.hasNext();) {
//                Node in1 = itOne.next();
//                if (!overlap.contains(in1)) {
//                    oneOnly.add(in1);
//                }
//            }
//
//            int ncatsNode = bayesPmTest.getNumSplits(node);
//            int nRows = bayesImTest.getNumRows(nodeIndexPm);
//
//            for (int k = 0; k < ncatsNode; k++) {
//
//                //double prob = 0.0;   Too early.
//
//                //For each combination of values for nodes in parentsIn2
//
//                for (int row = 0; row < nRows; row++) {
//
//                    Evidence evidencePA_2RHS = new Evidence(bayesIm);
//                    Evidence evidencePA_1RHS = new Evidence(bayesIm);
//
//                    int[] parentIn2Indices;
//                    parentIn2Indices = bayesImTest.getParents(nodeIndexPm);
//
//                    int[] parentIn2Values;
//                    parentIn2Values =
//                            bayesImTest.getParentValues(nodeIndexPm, row);
//
//                    for (int m = 0; m < parentIn2Indices.length; m++) {
//
//                        int indexNodemInIm = bayesIm.getNodeIndex(
//                                bayesImTest.getNode(parentIn2Indices[m]));
//
//                        evidencePA_2RHS.getProposition().setCategory(
//                                indexNodemInIm, parentIn2Values[m]);
//
//                        if (parentsIn1.contains(
//                                bayesImTest.getNode(parentIn2Indices[m]))) {
//                            evidencePA_1RHS.getProposition().setCategory(
//                                    indexNodemInIm, parentIn2Values[m]);
//                        }
//                    }
//
//
//                    //rseu.setEvidence(evidenceParIn1);
//                    //double probNodeGivenPar1 = rseu.getMarginal(nodeIndexIm, k);
//
//                    double prob = 0.0;
//                    for (Iterator<Node> it1Only = oneOnly.iterator();
//                         it1Only.hasNext();) {
//                        Node nodeIn1Only = it1Only.next();
//                        int indexIn1Only = bayesIm.getNodeIndex(nodeIn1Only);
//                        int ncatsIn1 = bayesPmTest.getNumSplits(
//                                nodeIn1Only);
//
//                        for (int cat = 0; cat < ncatsIn1; cat++) {
//                            evidencePA_1RHS.getProposition().setCategory(
//                                    nodeIndexIm, cat);
//
//                            rseu1.setEvidence(evidencePA_2RHS);
//                            double p1 = rseu1.getMarginal(indexIn1Only, cat);
//
//                            rseu2.setEvidence(evidencePA_1RHS);
//                            double p2 = rseu2.getMarginal(nodeIndexIm, k); //k is value of node
//
//                            prob += p1 * p2;
//
//                            //Store prob*numCases in observedCounts
//                            observedCounts[row][cat] = prob * numCases;      //<== HERE
//                            //observedCounts[row][cat] = prob;
//                        }
//
//
//                    }
//                    observedCounts[row][k] = prob * numCases;
//                    //observedCounts[row][k] *= numCases;
//                }
//
//                /*
//                double[] probsRowSum = new double[numRows];
//                 for(int i = 0; i < nRows; i++) {
//                     for(int j = 0; j < nCols; j++)
//                         probsRowSum[i] += bayesIm.getWordRatio(nodeIndexIm, i, j);
//
//                     for(int j = 0; j < nCols; j++)
//                         observedCounts[i][j]
//                 }
//                 */
//
//            }
//        }
//
//
//        //System.exit(0);
//    }


    private void computeObservedCounts(Node node, Node[] parentArray) {

        String name = node.getName();
        //int index = variables.indexOf(name);
        int index = getVarIndex(name);
        //int numCols = ((DiscreteVariable) variables.get(index)).getNumSplits();
        int numCols = bayesPm.getNumCategories(node);

        int[] parentVarIndices = new int[parentArray.length];
        int[] parDims = new int[parentArray.length];

        // Calculate the number of rows in the table of counts for this variable.  It
        // will be the outerProduct of the numbers of categories of its parents.  This is
        // similar to the number of rows in a conditional probability table.
        int numRows = 1;
        for (int i = 0; i < parentArray.length; i++) {
            String parName = parentArray[i].getName();

            parentVarIndices[i] = getVarIndex(parName);

            //int numCats = ((DiscreteVariable) variables.get(index)).getNumSplits();
            int numCats = bayesPm.getNumCategories(parentArray[i]);
            parDims[i] = numCats;
            numRows *= numCats;
        }

        observedCounts = new double[numRows][];
        for (int j = 0; j < numRows; j++) {
            observedCounts[j] = new double[numCols];
        }

        //System.out.println("Updating estimated counts for node " + varName);
        //This segment is for variables with no parents:
        if (parentArray.length == 0) {
            //System.out.println("No parents");
            for (int col = 0; col < numCols; col++) {
                observedCounts[0][col] = 0;
            }

            //Loop over the cases in the data set
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                //System.out.println("Case " + i);
                //If this case has a value for ar

                observedCounts[0][dataSet.getInt(i, index)] += 1.0;
                //System.out.println("Adding 1.0 to " + varName +
                //        " row 0 category " + mixedData[j][i]);
            }

        }
        else {    //For variables with parents:

            for (int row = 0; row < numRows; row++) {

                int[] parValues = new int[parDims.length];

                //The following loop was adapted from the method in MLBayesIm that calculates
                //the row number in the CPT corresponding to a set of values of parents.
                int thisRow = row;
                for (int i = parDims.length - 1; i >= 0; i--) {
                    parValues[i] = thisRow % parDims[i];
                    thisRow /= parDims[i];
                }

                for (int col = 0; col < numCols; col++) {
                    observedCounts[row][col] = 0;
                }

                //Loop over the cases in the dataset.
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    //for a case where the parent values = parValues increment the observed count.

                    boolean parentMatch = true;

                    //See if the values of the parents in this case match those for this row
                    //in the observed counts table.
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

                    //A match occurred so increment the count.
                    observedCounts[row][dataSet.getInt(i, index)] += 1;

                }

            }

        }

    }

    /**
     * @return the index of that variable (column) in the dataset associated
     * with the String in the argument.  Usually in the above code the name
     * comes from a node in the graph of the BayesPm.
     */
    private int getVarIndex(String name) {
        return dataSet.getColumn(dataSet.getVariable(name));
    }

    /**
     * This method is used in testing and debugging and not in the BDe metric
     * calculations.
     */
    public double[][] getObservedCounts(Node node, BayesPm bayesPm,
            BayesIm bayesIm) {
        System.out.println("In getObservedCounts for node = " + node.getName());

        BayesIm pmIm = new MlBayesIm(bayesPm);

        int inode = pmIm.getNodeIndex(node);
        int numPars = pmIm.getNumParents(inode);
        int numRows = pmIm.getNumRows(inode);

        System.out.println(
                "Has " + numPars + " parents " + numRows + " rows in CPT.");
        //computeObservedCountsMD(node, bayesPm, bayesIm);
        computeObservedCountsMD(node, bayesPm, bayesIm);
        return observedCounts;
    }

    /**
     * This is just for testing the operation of the inner class and the map
     * from nodes and parent sets to scores.
     */
    public int getScoreCount(Node node, Set<Node> parents) {
        NodeParentsPair nodeParents = new NodeParentsPair(node, parents);
        int count;

        if (scoreCounts.containsKey(nodeParents)) {
            System.out.println(node + " Score came from map.");
            count = scoreCounts.get(nodeParents);
        }
        else {
            count = nodeParents.calcCount();
            Integer countInt = count;
            scoreCounts.put(nodeParents, countInt);
        }

        return count;
    }

    /**
     * An inner class for storing and processing ordered pairs whose first
     * element is a node and whose second element is a set of parents of that
     * node. Instances of this class are used as arguments of the Maps scores
     * and scoreCounts in BdeMetricCache.
     */
    private final class NodeParentsPair {

        private final Node node;
        private final Set<Node> parents;

        public NodeParentsPair(Node node, Set<Node> parents) {
            this.node = node;
            this.parents = parents;

        }

        public final int calcCount() {
            return parents.size() + 1;
        }

        public final int hashCode() {
            int hash = 91;
            hash = 43 * hash + node.hashCode();
            hash = 43 * hash + parents.hashCode();

            return hash;
        }

        public final boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof NodeParentsPair)) {
                return false;
            }

            NodeParentsPair npp = (NodeParentsPair) other;

            if (!npp.node.equals(this.node)) {
                return false;
            }

            return npp.parents.equals(this.parents);

        }

    }


}





