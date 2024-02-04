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
import org.apache.commons.math3.special.Gamma;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>Provides a method for computing the score of a model, called the BDe
 * metric (Bayesian Dirchlet likelihood equivalence), given a dataset (assumes no missing values) and a Bayes
 * parameterized network (assumes no latent variables).&gt; 0
 * <p>
 * This version has a method that computes the score for a given factor of a model, where a factor is determined by a
 * node and its parents.  It stores scores in a map whose argument is an ordered pair consisting of 1) a node and 2) set
 * of parents.  The score for the entire model is the product of the scores of its factors.  Since the log of the gamma
 * function is used here the sum of the logs is computed as the score. Compare this with the score method in the
 * BdeMetric class which computes the score for the entire model in one pass.  The advantage of the approach in this
 * class is that it is more efficient in the context of a search algorithm where different models are scored but where
 * many of them will have the same factors. This class stores the score (relative to the dataset) for any [node, set of
 * parents] pair and thus avoids the expensive log gamma function calls. Instead, it looks in the map scores to see if
 * it has already computed the score and, if so, returns the previously computed value.&gt; 0 <p>See "Learning Bayesian
 * Networks:  The Combination of Knowledge and Statistical Data" by David Heckerman, Dan Geiger, and David M.
 * Chickering. Microsoft Technical Report MSR-TR-94-09.&gt; 0
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

    /**
     * Constructs a BdeMetricCache object for a given dataset and BayesPm.
     *
     * @param dataSet The dataset for which the BDe metric is to be computed.
     * @param bayesPm The BayesPm that determines the list of variables (nodes) and the structure of the graph.
     */
    public BdeMetricCache(DataSet dataSet, BayesPm bayesPm) {
        this.bayesPm = bayesPm;
        this.dataSet = dataSet;
        this.scores = new HashMap<>();
        this.scoreCounts = new HashMap<>();
        this.variables = dataSet.getVariables();
    }

    /**
     * Computes the BDe score, using the logarithm of the gamma function, relative to the data, of the factor determined
     * by a node and its parents.
     *
     * @param node       The node of the factor.
     * @param parents    The parents of the node.
     * @param bayesPmMod The BayesPm that determines the list of variables (nodes) and the structure of the graph.
     * @param bayesIm    The BayesIm that determines the observed counts.
     * @return The score of the factor.
     */
    public double scoreLnGam(Node node, Set<Node> parents, BayesPm bayesPmMod,
                             BayesIm bayesIm) {

        //A factor of a model is determined by a node and its parents in that model.
        //The NodeParentsPair inner class provides a means to instantiate such
        //a pair so that it can be used as an argument to the Map scores.
        NodeParentsPair nodeAndParents = new NodeParentsPair(node, parents);
        double score;

        //If the score of this factor has already been computed and stored, retrieve it from the
        //Map scores.
        if (this.scores.containsKey(nodeAndParents)) {
            System.out.println(
                    node + " Score came from map--counts not computed.");
            score = this.scores.get(nodeAndParents);

            return score;
        } else {

            //Create arrays for storing observed counts and prior probabilities for
            //this factor.  In observedCounts and priorProbs, for instance, there will be as
            //many rows as there are combinations of values of the parents and
            //as many columns as there are categories of the child variable.
            Node[] parentArray = new Node[parents.size()];
            for (int i = 0; i < parentArray.length; i++) {
                parentArray[i] = (Node) (parents.toArray()[i]);
            }

            BayesIm bayesImMod = new MlBayesIm(bayesPmMod);
            int numRows = bayesImMod.getNumRows(bayesImMod.getNodeIndex(node));

            double[][] priorProbs;
            double[] priorProbsRowSum;

            this.observedCounts = new double[numRows][];
            priorProbs = new double[numRows][];

            double[] observedCountsRowSum = new double[numRows];
            priorProbsRowSum = new double[numRows];

            int numCols = this.bayesPm.getNumCategories(node);

            for (int j = 0; j < numRows; j++) {

                observedCountsRowSum[j] = 0;
                priorProbsRowSum[j] = 0;

                this.observedCounts[j] = new double[numCols];
                priorProbs[j] = new double[numCols];
            }

            if (bayesIm == null) {
                computeObservedCounts(node, parentArray);
            } else {
                computeObservedCountsMD(node, bayesPmMod, bayesIm);
            }

            //Set all priorProbs (i.e. estimated counts) to 1.0.  Eventually they may be
            //supplied as a parameter of the constructor of this class.
            for (int j = 0; j < numRows; j++) {
                for (int k = 0; k < numCols; k++) {
                    priorProbs[j][k] = 1.0 /
                            (numRows * numCols);   //Per David Danks 12/21/04
                }
            }

            for (int j = 0; j < numRows; j++) {
                for (int k = 0; k < numCols; k++) {
                    observedCountsRowSum[j] += this.observedCounts[j][k];
                    priorProbsRowSum[j] += priorProbs[j][k];
                }
            }

            //The loops below compute the products on page 25 of the Heckerman et al. paper.
            //Actually sums are computed since logarithms of the terms are used.  That is
            //why the log gamma function (lngamma) is used.
            double sum = 0.0;

            for (int j = 0; j < numRows; j++) {

                try {
                    double numerator = Gamma.logGamma(priorProbsRowSum[j]);
                    double denom = Gamma.logGamma(
                            priorProbsRowSum[j] + observedCountsRowSum[j]);
                    sum += (numerator - denom);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //double prodk = 1.0;
                double sumk = 0.0;
                for (int k = 0; k < numCols; k++) {
                    try {
                        sumk += Gamma.logGamma(
                                priorProbs[j][k] + this.observedCounts[j][k]) -
                                Gamma.logGamma(priorProbs[j][k]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                sum += sumk;
            }

            score = sum;

        }

        Double scoreDouble = score;
        this.scores.put(nodeAndParents, scoreDouble);

        return score;
    }


    private void computeObservedCountsMD(Node node, BayesPm bayesPmTest,
                                         BayesIm bayesIm) {
        int numCases = this.dataSet.getNumRows();
        int numVariables = this.variables.size();

        Graph graph = bayesIm.getBayesPm().getDag();

        RowSummingExactUpdater rseu = new RowSummingExactUpdater(bayesIm);

        String name = node.getName();

        int index = getVarIndex(name);

        int numCols = bayesPmTest.getNumCategories(node);

        BayesIm bayesImTest = new MlBayesIm(bayesPmTest);
        int nodeIndexImTest = bayesImTest.getNodeIndex(node);
        int numRows = bayesImTest.getNumRows(nodeIndexImTest);

        int varIndex = bayesImTest.getNodeIndex(node);
        int[] parentVarIndices = bayesImTest.getParents(varIndex);

        if (parentVarIndices.length == 0) {              //node has no parents

            for (int col = 0; col < numCols; col++) {
                this.observedCounts[0][col] = 0;
            }

            for (int i = 0; i < numCases; i++) {
                if (this.dataSet.getInt(i, index) != -99) {
                    this.observedCounts[0][this.dataSet.getInt(i, index)] += 1.0;
                } else {
                    Evidence evidenceThisCase = Evidence.tautology(bayesIm);
                    boolean existsEvidence = false;

                    //Define evidence for updating by using the values of the other vars.
                    for (int k = 0; k < numVariables; k++) {
                        if (this.dataSet.getInt(i, k) == -99) {
                            continue;
                        }

                        Node otherVar = this.variables.get(k);

                        existsEvidence = true;
                        String otherVarName = otherVar.getName();
                        Node otherNode = graph.getNode(otherVarName);
                        int otherIndex = bayesIm.getNodeIndex(otherNode);

                        evidenceThisCase.getProposition().setCategory(
                                otherIndex, this.dataSet.getInt(i, k));

                    }

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

                        this.observedCounts[0][m] += p;
                    }

                }
            }
        } else {
            for (int row = 0; row < numRows; row++) {
                int[] parValues = bayesImTest.getParentValues(varIndex, row);

                for (int col = 0; col < numCols; col++) {
                    this.observedCounts[row][col] = 0.0;
                }

                for (int i = 0; i < numCases; i++) {
                    boolean parentMatch = true;

                    for (int p = 0; p < parentVarIndices.length; p++) {
                        if (parValues[p] !=
                                this.dataSet.getInt(i, parentVarIndices[p]) &&
                                this.dataSet.getInt(i, parentVarIndices[p]) != -99) {
                            parentMatch = false;
                            break;
                        }
                    }

                    if (!parentMatch) {
                        continue;
                    }

                    boolean parentMissing = false;

                    for (int parentVarIndice : parentVarIndices) {
                        if (this.dataSet.getInt(i, parentVarIndice) == -99) {
                            parentMissing = true;
                            break;
                        }
                    }

                    if (this.dataSet.getInt(i, index) != -99 && !parentMissing) {
                        this.observedCounts[row][this.dataSet.getInt(i, index)] += 1.0;
                        continue;
                    }

                    //To compute marginals create the evidence
                    Evidence evidenceThisCase = Evidence.tautology(bayesIm);

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

                        double p = rseu.getJointMarginal(parPlusChildIndices,
                                parPlusChildValues);

                        if (Double.isNaN(p)) {
                            System.out.println(
                                    "existsEvidence = " + false);
                            System.out.println(
                                    "getJointMarginal returns NaN for ");
                            System.exit(0);
                        }

                        this.observedCounts[row][m] += p;
                    }
                }
            }
        }

        //else/*   Commented out on 11.22.04 to see if it causes a problem.
        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                this.observedCounts[row][col] *= numCases;
            }
        }

    }


    private void computeObservedCounts(Node node, Node[] parentArray) {

        String name = node.getName();
        int index = getVarIndex(name);
        int numCols = this.bayesPm.getNumCategories(node);

        int[] parentVarIndices = new int[parentArray.length];
        int[] parDims = new int[parentArray.length];

        // Calculate the number of rows in the table of counts for this variable.  It
        // will be the outerProduct of the numbers of categories of its parents.  This is
        // similar to the number of rows in a conditional probability table.
        int numRows = 1;
        for (int i = 0; i < parentArray.length; i++) {
            String parName = parentArray[i].getName();

            parentVarIndices[i] = getVarIndex(parName);

            int numCats = this.bayesPm.getNumCategories(parentArray[i]);
            parDims[i] = numCats;
            numRows *= numCats;
        }

        this.observedCounts = new double[numRows][];
        for (int j = 0; j < numRows; j++) {
            this.observedCounts[j] = new double[numCols];
        }

        //System.out.println("Updating estimated counts for node " + varName);
        //This segment is for variables with no parents:
        if (parentArray.length == 0) {
            for (int col = 0; col < numCols; col++) {
                this.observedCounts[0][col] = 0;
            }

            //Loop over the cases in the data set
            for (int i = 0; i < this.dataSet.getNumRows(); i++) {
                this.observedCounts[0][this.dataSet.getInt(i, index)] += 1.0;
            }

        } else {    //For variables with parents:

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
                    this.observedCounts[row][col] = 0;
                }

                //Loop over the cases in the dataset.
                for (int i = 0; i < this.dataSet.getNumRows(); i++) {
                    //for a case where the parent values = parValues increment the observed count.

                    boolean parentMatch = true;

                    //See if the values of the parents in this case match those for this row
                    //in the observed counts table.
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

                    //A match occurred so increment the count.
                    this.observedCounts[row][this.dataSet.getInt(i, index)] += 1;

                }

            }

        }

    }

    /**
     * @return the index of that variable (column) in the dataset associated with the String in the argument.  Usually
     * in the above code the name comes from a node in the graph of the BayesPm.
     */
    private int getVarIndex(String name) {
        return this.dataSet.getColumn(this.dataSet.getVariable(name));
    }

    /**
     * This method is used in testing and debugging and not in the BDe metric calculations.
     *
     * @param node    The node for which the observed counts are to be returned.
     * @param bayesPm The BayesPm that determines the list of variables (nodes) and the structure of the graph.
     * @param bayesIm The BayesIm that determines the observed counts.
     * @return The observed counts for the given node.
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
        return this.observedCounts;
    }

    /**
     * This is just for testing the operation of the inner class and the map from nodes and parent sets to scores.
     *
     * @param node    The node of the factor.
     * @param parents The parents of the node.
     * @return The score of the factor.
     */
    public int getScoreCount(Node node, Set<Node> parents) {
        NodeParentsPair nodeParents = new NodeParentsPair(node, parents);
        int count;

        if (this.scoreCounts.containsKey(nodeParents)) {
            System.out.println(node + " Score came from map.");
            count = this.scoreCounts.get(nodeParents);
        } else {
            count = nodeParents.calcCount();
            Integer countInt = count;
            this.scoreCounts.put(nodeParents, countInt);
        }

        return count;
    }

    /**
     * An inner class for storing and processing ordered pairs whose first element is a node and whose second element is
     * a set of parents of that node. Instances of this class are used as arguments of the Maps scores and scoreCounts
     * in BdeMetricCache.
     */
    private static final class NodeParentsPair {

        private final Node node;
        private final Set<Node> parents;


        /**
         * Constructs a NodeParentsPair object for a given node and set of parents.
         *
         * @param node    The node of the pair.
         * @param parents The parents of the node.
         */
        public NodeParentsPair(Node node, Set<Node> parents) {
            this.node = node;
            this.parents = parents;

        }

        /**
         * @return The number of elements in the set of parents plus 1.
         */
        public int calcCount() {
            return this.parents.size() + 1;
        }

        /**
         * @return The node of the pair.
         */
        public int hashCode() {
            int hash = 91;
            hash = 43 * hash + this.node.hashCode();
            hash = 43 * hash + this.parents.hashCode();

            return hash;
        }

        /**
         * Equals method for NodeParentsPair.
         *
         * @param other The other object to compare to.
         * @return True if the other object is a NodeParentsPair and has the same node and parents as this one.
         */
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }

            if (!(other instanceof NodeParentsPair)) {
                return false;
            }

            NodeParentsPair npp = (NodeParentsPair) other;

            return npp.node.equals(this.node) && npp.parents.equals(this.parents);

        }

    }


}





