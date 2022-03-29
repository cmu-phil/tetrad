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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Estimates parameters of the given Bayes net from the given data using maximum
 * likelihood method.
 *
 * @author Frank Wimberly based on related classes by Shane Harwood, Joseph
 * Ramsey
 */
public final class EmBayesEstimator {
    private final BayesPm bayesPm;

    private final DataSet dataSet;
    //private DataSet ddsNm;
    private DataSet mixedData;   //Contains all variables with missing value columns for
    //latents

    private List<Node> allVariables;          //Variables in mixedData (observed and latents)

    private final Node[] nodes;
    private final Graph graph;
    private BayesPm bayesPmObs;
    private BayesIm observedIm;
    private BayesIm estimatedIm;

    /**
     * The main data structure of this class is the double[][][] array
     * estimatedCounts; it stores the values of all the estimated counts for
     * the Bayes net.  The first dimension is the node index, in the order of
     * 'nodes'.  The second dimension is the row index for the table of
     * parameters associated with node; the third dimension is the column index.
     * The row index is calculated by the function getRowIndex(int[] values)
     * where 'values' is an array of numerical indices for each of the parent
     * values; the order of the values in this array is the same as the order of
     * nodes in 'parents'; the value indices are obtained from the Bayes PM
     * for each node.  The column is the index of the value of N, where this
     * index is obtained from the Bayes PM.
     */
    private double[][][] estimatedCounts;

    /**
     * For each row of the conditional probability table for each node, this is
     * the estimated count of the number of occurrences of the corresponding set
     * of values of parents in the dataset.  The first dimension is the node
     * index and the second is the row number. Hence, the conditional
     * probabilities are computed by dividing estimatedCounts[node][row][column]
     * by estimateCountsDenom[node][row].
     */
    private double[][] estimatedCountsDenom;

    /**
     * The conditional proabilities are stored in this array.  As above, the
     * dimensions are node, row and column respectively.
     */
    private double[][][] condProbs;

    /**
     * Provides methods for estimating a Bayes IM from an existing BayesIM and a
     * discrete dataset using EM (Expectation Maximization).  The data columns
     * in the given data must be equal to a variable in the given Bayes IM but
     * the latter may contain variables which don't occur in the dataset (latent
     * variables). </p> The first argument of the constructoris the BayesPm
     * whose graph contains latent and observed variables.  The second is the
     * dataset of observed variables; missing value codes may be present.
     */
    public EmBayesEstimator(BayesPm bayesPm, DataSet dataSet) {

        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

        List<Node> observedVars = new ArrayList<>();

        this.bayesPm = bayesPm;
        this.dataSet = dataSet;

        this.graph = bayesPm.getDag();
        this.nodes = new Node[this.graph.getNumNodes()];

        Iterator<Node> it = this.graph.getNodes().iterator();

        for (int i = 0; i < this.nodes.length; i++) {
            this.nodes[i] = it.next();
        }

        for (Node node : this.nodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                observedVars.add(bayesPm.getVariable(node));
            }
        }

        //Make sure all measured variables in the BayesPm are in the discrete dataset
        for (Node observedVar : observedVars) {
            try {
                this.dataSet.getVariable(observedVar.getName());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Some observed ar in the Bayes net " +
                                "is not in the dataset: " + observedVar);
            }
        }

        findBayesNetObserved();   //Sets bayesPmObs

        initialize();

    }

    public EmBayesEstimator(BayesIm inputBayesIm, DataSet dataSet) {
        this(inputBayesIm.getBayesPm(), dataSet);
    }

    private void initialize() {
        DirichletBayesIm prior =
                DirichletBayesIm.symmetricDirichletIm(this.bayesPmObs, 0.5);

        this.observedIm = DirichletEstimator.estimate(prior, this.dataSet);

        //mixedData should be ddsNm with new columns for the latent variables.
        //Each such column should contain missing data for each case.

        int numFullCases = this.dataSet.getNumRows();
        List<Node> variables = new LinkedList<>();

        for (Node node : this.nodes) {
            if (node.getNodeType() == NodeType.LATENT) {
                int numCategories = this.bayesPm.getNumCategories(node);
                DiscreteVariable latentVar =
                        new DiscreteVariable(node.getName(), numCategories);
                latentVar.setNodeType(NodeType.LATENT);
                variables.add(latentVar);
            } else {
                String name = this.bayesPm.getVariable(node).getName();
                Node variable = this.dataSet.getVariable(name);
                variables.add(variable);
            }
        }

        DataSet dsMixed = new BoxDataSet(new DoubleDataBox(numFullCases, variables.size()), variables);

        for (int j = 0; j < this.nodes.length; j++) {
            if (this.nodes[j].getNodeType() == NodeType.LATENT) {
                for (int i = 0; i < numFullCases; i++) {
                    dsMixed.setInt(i, j, -99);
                }
            } else {
                String name = this.bayesPm.getVariable(this.nodes[j]).getName();
                Node variable = this.dataSet.getVariable(name);
                int index = this.dataSet.getColumn(variable);

                for (int i = 0; i < numFullCases; i++) {
                    dsMixed.setInt(i, j, this.dataSet.getInt(i, index));
                }
            }
        }

//        System.out.println(dsMixed);

        this.mixedData = dsMixed;
        this.allVariables = this.mixedData.getVariables();

        //Find the bayes net which is parameterized using mixedData or set randomly when that's
        //not possible.
        estimateIM(this.bayesPm, this.mixedData);

        this.estimatedCounts = new double[this.nodes.length][][];
        this.estimatedCountsDenom = new double[this.nodes.length][];
        this.condProbs = new double[this.nodes.length][][];

        for (int i = 0; i < this.nodes.length; i++) {
            //int numRows = bayesImMixed.getNumRows(i);
            int numRows = this.estimatedIm.getNumRows(i);
            this.estimatedCounts[i] = new double[numRows][];
            this.estimatedCountsDenom[i] = new double[numRows];
            this.condProbs[i] = new double[numRows][];
            //for(int j = 0; j < bayesImMixed.getNumRows(i); j++) {
            for (int j = 0; j < this.estimatedIm.getNumRows(i); j++) {
                //int numCols = bayesImMixed.getNumColumns(i);
                int numCols = this.estimatedIm.getNumColumns(i);
                this.estimatedCounts[i][j] = new double[numCols];
                this.condProbs[i][j] = new double[numCols];
            }
        }
    }

    /**
     * This method takes an instantiated Bayes net (BayesIm) whose graph include
     * all the variables (observed and latent) and computes estimated counts
     * using the data in the DataSet mixedData. </p> The counts that are
     * estimated correspond to cells in the conditional probability tables of
     * the Bayes net.  The outermost loop (indexed by j) is over the set of
     * variables.  If the variable has no parents, each case in the dataset is
     * examined and the count for the observed value of the variables is
     * increased by 1.0; if the value of the variable is missing the marginal
     * probabilities its values given the values of the variables that are
     * available for that case are used to increment the corresponding estimated
     * counts. </p> If a variable has parents then there is a loop which steps
     * through all possible sets of values of its parents.  This loop is indexed
     * by the variable "row".  Each case in the dataset is examined.  It the
     * variable and all its parents have values in the case the corresponding
     * estimated counts are incremented by 1.0.  If the variable or any of its
     * parents have missing values, the joint marginal is computed for the
     * variable and the set of values of its parents corresponding to "row" and
     * the corresponding estimated counts are incremented by the appropriate
     * probability. </p> The estimated counts are stored in the double[][][]
     * array estimatedCounts.  The count (possibly fractional) of the number of
     * times each combination of parent values occurs is stored in the
     * double[][] array estimatedCountsDenom.  These two arrays are used to
     * compute the estimated conditional probabilities of the output Bayes net.
     */
    private void expectation(BayesIm inputBayesIm) {
        int numCases = this.mixedData.getNumRows();
        int numVariables = this.allVariables.size();
        RowSummingExactUpdater rseu = new RowSummingExactUpdater(inputBayesIm);

        for (int j = 0; j < numVariables; j++) {
            DiscreteVariable var = (DiscreteVariable) this.allVariables.get(j);
            String varName = var.getName();
            Node varNode = this.graph.getNode(varName);
            int varIndex = inputBayesIm.getNodeIndex(varNode);
            int[] parentVarIndices = inputBayesIm.getParents(varIndex);

            //This segment is for variables with no parents:
            if (parentVarIndices.length == 0) {
                //System.out.println("No parents");
                for (int col = 0; col < var.getNumCategories(); col++) {
                    this.estimatedCounts[j][0][col] = 0.0;
                }

                for (int i = 0; i < numCases; i++) {
                    //System.out.println("Case " + i);
                    //If this case has a value for ar
                    if (this.mixedData.getInt(i, j) != -99) {
                        this.estimatedCounts[j][0][this.mixedData.getInt(i, j)] += 1.0;
                    } else {
                        //find marginal probability, given obs data in this case, p(v=0)
                        Evidence evidenceThisCase = Evidence.tautology(inputBayesIm);
                        boolean existsEvidence = false;

                        //Define evidence for updating by using the values of the other vars.
                        for (int k = 0; k < numVariables; k++) {
                            if (k == j) {
                                continue;
                            }
                            Node otherVar = this.allVariables.get(k);
                            if (this.mixedData.getInt(i, k) == -99) {
                                continue;
                            }
                            existsEvidence = true;
                            String otherVarName = otherVar.getName();
                            Node otherNode = this.graph.getNode(otherVarName);
                            int otherIndex =
                                    inputBayesIm.getNodeIndex(otherNode);

                            evidenceThisCase.getProposition().setCategory(
                                    otherIndex, this.mixedData.getInt(i, k));
                        }

                        if (!existsEvidence) {
                            continue; //No other variable contained useful data
                        }

                        rseu.setEvidence(evidenceThisCase);

                        for (int m = 0; m < var.getNumCategories(); m++) {
                            this.estimatedCounts[j][0][m] +=
                                    rseu.getMarginal(varIndex, m);
                        }
                    }
                }

                //Print estimated counts:
                //System.out.println("Estimated counts:  ");

                //Print counts for each value of this variable with no parents.
            } else {    //For variables with parents:
                int numRows = inputBayesIm.getNumRows(varIndex);
                for (int row = 0; row < numRows; row++) {
                    int[] parValues =
                            inputBayesIm.getParentValues(varIndex, row);
                    this.estimatedCountsDenom[varIndex][row] = 0.0;
                    for (int col = 0; col < var.getNumCategories(); col++) {
                        this.estimatedCounts[varIndex][row][col] = 0.0;
                    }

                    for (int i = 0; i < numCases; i++) {
                        //for a case where the parent values = parValues increment the estCount

                        boolean parentMatch = true;

                        for (int p = 0; p < parentVarIndices.length; p++) {
                            if (parValues[p] !=
                                    this.mixedData.getInt(i, parentVarIndices[p]) &&
                                    this.mixedData.getInt(i, parentVarIndices[p]) !=
                                            -99) {
                                parentMatch = false;
                                break;
                            }
                        }

                        if (!parentMatch) {
                            continue;  //Not a matching case; go to next.
                        }

                        boolean parentMissing = false;
                        for (int parentVarIndice : parentVarIndices) {
                            if (this.mixedData.getInt(i, parentVarIndice) == -99) {
                                parentMissing = true;
                                break;
                            }
                        }


                        if (this.mixedData.getInt(i, j) != -99 && !parentMissing) {
                            this.estimatedCounts[j][row][this.mixedData.getInt(i, j)] +=
                                    1.0;
                            this.estimatedCountsDenom[j][row] += 1.0;
                            continue;    //Next case
                        }

                        Evidence.tautology(inputBayesIm);
                    }
                }


            }
        }

        BayesIm outputBayesIm = new MlBayesIm(this.bayesPm);

        for (int j = 0; j < this.nodes.length; j++) {

            DiscreteVariable var = (DiscreteVariable) this.allVariables.get(j);
            String varName = var.getName();
            Node varNode = this.graph.getNode(varName);
            int varIndex = inputBayesIm.getNodeIndex(varNode);
//            int[] parentVarIndices = inputBayesIm.getParents(varIndex);

            int numRows = inputBayesIm.getNumRows(j);
            //System.out.println("Conditional probabilities for variable " + varName);

            int numCols = inputBayesIm.getNumColumns(j);
            if (numRows == 1) {
                double sum = 0.0;
                for (int m = 0; m < numCols; m++) {
                    sum += this.estimatedCounts[j][0][m];
                }

                for (int m = 0; m < numCols; m++) {
                    this.condProbs[j][0][m] = this.estimatedCounts[j][0][m] / sum;
                    outputBayesIm.setProbability(varIndex, 0, m,
                            this.condProbs[j][0][m]);
                }
                //System.out.println();
            } else {

                for (int row = 0; row < numRows; row++) {

                    for (int m = 0; m < numCols; m++) {
                        if (this.estimatedCountsDenom[j][row] != 0.0) {
                            this.condProbs[j][row][m] = this.estimatedCounts[j][row][m] /
                                    this.estimatedCountsDenom[j][row];
                        } else {
                            this.condProbs[j][row][m] = Double.NaN;
                        }
                        outputBayesIm.setProbability(varIndex, row, m,
                                this.condProbs[j][row][m]);
                    }
                }
            }

        }
    }

    /**
     * This method iteratively estimates the parameters of the Bayes net using
     * the dataset until the parameters don't change.  That is, the newly
     * estimated parameters are used in the estimate method to produce even more
     * accurate parameters (with respect to the dataset) etc.  The threshhold is
     * compared to the distance between successive parameter sets and when the
     * change is less than the threshhold, the process is considered to have
     * converged.  The distance between successive Bayes nets is the Euclidean
     * distance between vectors of sequences of their parameters.  See the
     * BayesImDistanceFunction class for details.
     */
    public BayesIm maximization(double threshhold) {
        double distance = Double.MAX_VALUE;
        BayesIm oldBayesIm = this.estimatedIm;
        BayesIm newBayesIm = null;

        while (Double.isNaN(distance) || distance > threshhold) {
            expectation(oldBayesIm);
            newBayesIm = getEstimatedIm();

            distance = BayesImDistanceFunction.distance(newBayesIm, oldBayesIm);

            oldBayesIm = newBayesIm;
        }
        return newBayesIm;
    }

    private void findBayesNetObserved() {

        Dag dagObs = new Dag(this.graph);
        for (Node node : this.nodes) {
            if (node.getNodeType() == NodeType.LATENT) {
                dagObs.removeNode(node);
            }
        }

        this.bayesPmObs = new BayesPm(dagObs, this.bayesPm);
    }

    /**
     * Estimates a Bayes IM using the variables, graph, and parameters in the
     * given Bayes PM and the data columns in the given data set. Each variable
     * in the given Bayes PM must be equal to a variable in the given data set.
     * The Bayes IM so estimated is used as the initial Bayes net in the
     * iterative procedure implemented in the maximize method.
     */
    private void estimateIM(BayesPm bayesPm, DataSet dataSet) {
        if (bayesPm == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

        BayesUtils.ensureVarsInData(bayesPm.getVariables(), dataSet);

        // Create a new Bayes IM to store the estimated values.
        this.estimatedIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        int numNodes = this.estimatedIm.getNumNodes();

        for (int node = 0; node < numNodes; node++) {

            int numRows = this.estimatedIm.getNumRows(node);
            int numCols = this.estimatedIm.getNumColumns(node);
            int[] parentVarIndices = this.estimatedIm.getParents(node);
            if (this.nodes[node].getNodeType() == NodeType.LATENT) {
                continue;
            }

            Node nodeObs = this.observedIm.getNode(this.nodes[node].getName());
            int nodeObsIndex = this.observedIm.getNodeIndex(nodeObs);

            boolean anyParentLatent = false;
            for (int parentVarIndice : parentVarIndices) {
                if (this.nodes[parentVarIndice].getNodeType() == NodeType.LATENT) {
                    anyParentLatent = true;
                    break;
                }
            }

            if (anyParentLatent) {
                continue;
            }

            //At this point node is measured in bayesPm and so are its parents.
            for (int row = 0; row < numRows; row++) {
                for (int col = 0; col < numCols; col++) {
                    double p =
                            this.observedIm.getProbability(nodeObsIndex, row, col);
                    this.estimatedIm.setProbability(node, row, col, p);
                }
            }
        }

    }

    public DataSet getMixedDataSet() {
        return this.mixedData;
    }

    public BayesIm getEstimatedIm() {
        return this.estimatedIm;
    }


}





