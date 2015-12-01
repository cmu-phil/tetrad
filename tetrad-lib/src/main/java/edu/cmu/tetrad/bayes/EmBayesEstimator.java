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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
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
 *         Ramsey
 */
public final class EmBayesEstimator {
    private BayesPm bayesPm;

    private DataSet dataSet;
    //private DataSet ddsNm;
    private DataSet mixedData;   //Contains all variables with missing value columns for
    //latents

    private List<Node> allVariables;          //Variables in mixedData (observed and latents)

    private Node[] nodes;
    private Graph graph;
    private BayesPm bayesPmObs;
    private BayesIm observedIm;
    private BayesIm estimatedIm;
    //private BayesIm bayesImMixed;

    /**
     * The main data structure of this class is the double[][][] array
     * estimatedCounts; it stores the values of all of the estimated counts for
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
     * index and the second is the row number. Hence the conditional
     * probabilities are computed by dividing estimatedCounts[node][row][column]
     * by estimateCountsDenom[node][row].
     */
    private double[][] estimatedCountsDenom;

    /**
     * The conditional proabilities are stored in this array.  As above, the
     * dimensions are node, row and column respectively.
     */
    private double[][][] condProbs;

//    /**
//     * In case the constructor whose argument list includes a Bayes IM is used
//     * this member variable will be set to that and will not be null.
//     */
//    private BayesIm inputBayesIm;

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

        //this.variables = Collections.unmodifiableList(vars);
        graph = bayesPm.getDag();
        this.nodes = new Node[graph.getNumNodes()];

        Iterator<Node> it = graph.getNodes().iterator();

        for (int i = 0; i < this.nodes.length; i++) {
            this.nodes[i] = it.next();
            //System.out.println("node " + i + " " + nodes[i]);
        }

        for (Node node : this.nodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                observedVars.add(bayesPm.getVariable(node));
            }
        }

        //Make sure variables in dataset are measured variables in the BayesPM
//        for (Node dataSetVariable : this.dataSet.getVariables()) {
//            if (!observedVars.contains(dataSetVariable)) {
//                throw new IllegalArgumentException(
//                        "Some ar in the dataset is not a " +
//                                "measured variable in the Bayes net");
//            }
//        }

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
        //this.inputBayesIm = inputBayesIm;
    }

    private void initialize() {
        DirichletBayesIm prior =
                DirichletBayesIm.symmetricDirichletIm(bayesPmObs, 0.5);

        observedIm = DirichletEstimator.estimate(prior, dataSet);

        //        MLBayesEstimator dirichEst = new MLBayesEstimator();
        //        observedIm = dirichEst.estimate(bayesPmObs, dataSet);

//        System.out.println("Estimated Bayes IM for Measured Variables:  ");
//        System.out.println(observedIm);

        //mixedData should be ddsNm with new columns for the latent variables.
        //Each such column should contain missing data for each case.

        int numFullCases = dataSet.getNumRows();
        List<Node> variables = new LinkedList<>();

        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.LATENT) {
                int numCategories = bayesPm.getNumCategories(node);
                DiscreteVariable latentVar =
                        new DiscreteVariable(node.getName(), numCategories);
                latentVar.setNodeType(NodeType.LATENT);
                variables.add(latentVar);
            } else {
                String name = bayesPm.getVariable(node).getName();
                Node variable = dataSet.getVariable(name);
                variables.add(variable);
            }
        }

        DataSet dsMixed = new ColtDataSet(numFullCases, variables);

        for (int j = 0; j < nodes.length; j++) {
            if (nodes[j].getNodeType() == NodeType.LATENT) {
                for (int i = 0; i < numFullCases; i++) {
                    dsMixed.setInt(i, j, -99);
                }
            } else {
                String name = bayesPm.getVariable(nodes[j]).getName();
                Node variable = dataSet.getVariable(name);
                int index = dataSet.getColumn(variable);

                for (int i = 0; i < numFullCases; i++) {
                    dsMixed.setInt(i, j, dataSet.getInt(i, index));
                }
            }
        }

//        System.out.println(dsMixed);

        mixedData = dsMixed;
        allVariables = mixedData.getVariables();

        //Find the bayes net which is parameterized using mixedData or set randomly when that's
        //not possible.
        estimateIM(bayesPm, mixedData);

        //The following DEBUG section tests a case specified by P. Spirtes
        //DEBUG TAIL:   For use with embayes_l1x1x2x3V3.dat
        /*
        Node l1Node = graph.getNode("L1");
        //int l1Index = bayesImMixed.getNodeIndex(l1Node);
        int l1index = estimatedIm.getNodeIndex(l1Node);
        Node x1Node = graph.getNode("X1");
        //int x1Index = bayesImMixed.getNodeIndex(x1Node);
        int x1Index = estimatedIm.getNodeIndex(x1Node);
        Node x2Node = graph.getNode("X2");
        //int x2Index = bayesImMixed.getNodeIndex(x2Node);
        int x2Index = estimatedIm.getNodeIndex(x2Node);
        Node x3Node = graph.getNode("X3");
        //int x3Index = bayesImMixed.getNodeIndex(x3Node);
        int x3Index = estimatedIm.getNodeIndex(x3Node);

        estimatedIm.setProbability(l1index, 0, 0, 0.5);
        estimatedIm.setProbability(l1index, 0, 1, 0.5);

        //bayesImMixed.setProbability(x1Index, 0, 0, 0.33333);
        //bayesImMixed.setProbability(x1Index, 0, 1, 0.66667);
        estimatedIm.setProbability(x1Index, 0, 0, 0.6);      //p(x1 = 0 | l1 = 0)
        estimatedIm.setProbability(x1Index, 0, 1, 0.4);      //p(x1 = 1 | l1 = 0)
        estimatedIm.setProbability(x1Index, 1, 0, 0.4);      //p(x1 = 0 | l1 = 1)
        estimatedIm.setProbability(x1Index, 1, 1, 0.6);      //p(x1 = 1 | l1 = 1)

        //bayesImMixed.setProbability(x2Index, 1, 0, 0.66667);
        //bayesImMixed.setProbability(x2Index, 1, 1, 0.33333);
        estimatedIm.setProbability(x2Index, 1, 0, 0.4);      //p(x2 = 0 | l1 = 1)
        estimatedIm.setProbability(x2Index, 1, 1, 0.6);      //p(x2 = 1 | l1 = 1)
        estimatedIm.setProbability(x2Index, 0, 0, 0.6);      //p(x2 = 0 | l1 = 0)
        estimatedIm.setProbability(x2Index, 0, 1, 0.4);      //p(x2 = 1 | l1 = 0)

        //bayesImMixed.setProbability(x3Index, 1, 0, 0.66667);
        //bayesImMixed.setProbability(x3Index, 1, 1, 0.33333);
        estimatedIm.setProbability(x3Index, 1, 0, 0.4);      //p(x3 = 0 | l1 = 1)
        estimatedIm.setProbability(x3Index, 1, 1, 0.6);      //p(x3 = 1 | l1 = 1)
        estimatedIm.setProbability(x3Index, 0, 0, 0.6);      //p(x3 = 0 | l1 = 0)
        estimatedIm.setProbability(x3Index, 0, 1, 0.4);      //p(x3 = 1 | l1 = 0)
        */
        //END of TAIL

        //System.out.println("bayes IM estimated by estimateIM");
        //System.out.println(bayesImMixed);
        //System.out.println(estimatedIm);

        estimatedCounts = new double[nodes.length][][];
        estimatedCountsDenom = new double[nodes.length][];
        condProbs = new double[nodes.length][][];

        for (int i = 0; i < nodes.length; i++) {
            //int numRows = bayesImMixed.getNumRows(i);
            int numRows = estimatedIm.getNumRows(i);
            estimatedCounts[i] = new double[numRows][];
            estimatedCountsDenom[i] = new double[numRows];
            condProbs[i] = new double[numRows][];
            //for(int j = 0; j < bayesImMixed.getNumRows(i); j++) {
            for (int j = 0; j < estimatedIm.getNumRows(i); j++) {
                //int numCols = bayesImMixed.getNumColumns(i);
                int numCols = estimatedIm.getNumColumns(i);
                estimatedCounts[i][j] = new double[numCols];
                condProbs[i][j] = new double[numCols];
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
        //System.out.println("Entered method expectation.");

        int numCases = mixedData.getNumRows();
        //StoredCellEstCounts estCounts = new StoredCellEstCounts(variables);

        int numVariables = allVariables.size();
        RowSummingExactUpdater rseu = new RowSummingExactUpdater(inputBayesIm);

        for (int j = 0; j < numVariables; j++) {
            DiscreteVariable var = (DiscreteVariable) allVariables.get(j);
            String varName = var.getName();
            Node varNode = graph.getNode(varName);
            int varIndex = inputBayesIm.getNodeIndex(varNode);
            int[] parentVarIndices = inputBayesIm.getParents(varIndex);
            //System.out.println("graph = " + graph);

            //for(int col = 0; col < ar.getNumSplits(); col++)
            //    System.out.println("Category " + col + " = " + ar.getCategory(col));

            //System.out.println("Updating estimated counts for node " + varName);
            //This segment is for variables with no parents:
            if (parentVarIndices.length == 0) {
                //System.out.println("No parents");
                for (int col = 0; col < var.getNumCategories(); col++) {
                    estimatedCounts[j][0][col] = 0.0;
                }

                for (int i = 0; i < numCases; i++) {
                    //System.out.println("Case " + i);
                    //If this case has a value for ar
                    if (mixedData.getInt(i, j) != -99) {
                        estimatedCounts[j][0][mixedData.getInt(i, j)] += 1.0;
                        //System.out.println("Adding 1.0 to " + varName +
                        //        " row 0 category " + mixedData[j][i]);
                    } else {
                        //find marginal probability, given obs data in this case, p(v=0)
                        Evidence evidenceThisCase = Evidence.tautology(inputBayesIm);
                        boolean existsEvidence = false;

                        //Define evidence for updating by using the values of the other vars.
                        for (int k = 0; k < numVariables; k++) {
                            if (k == j) {
                                continue;
                            }
                            Node otherVar = allVariables.get(k);
                            if (mixedData.getInt(i, k) == -99) {
                                continue;
                            }
                            existsEvidence = true;
                            String otherVarName = otherVar.getName();
                            Node otherNode = graph.getNode(otherVarName);
                            int otherIndex =
                                    inputBayesIm.getNodeIndex(otherNode);

                            evidenceThisCase.getProposition().setCategory(
                                    otherIndex, mixedData.getInt(i, k));
                        }

                        if (!existsEvidence) {
                            continue; //No other variable contained useful data
                        }

                        rseu.setEvidence(evidenceThisCase);

                        for (int m = 0; m < var.getNumCategories(); m++) {
                            estimatedCounts[j][0][m] +=
                                    rseu.getMarginal(varIndex, m);
                            //System.out.println("Adding " + p + " to " + varName +
                            //        " row 0 category " + m);

                            //find marginal probability, given obs data in this case, p(v=1)
                            //estimatedCounts[j][0][1] += 0.5;
                        }
                    }
                }

                //Print estimated counts:
                //System.out.println("Estimated counts:  ");

                //Print counts for each value of this variable with no parents.
                //for(int m = 0; m < ar.getNumSplits(); m++)
                //    System.out.print("    " + m + " " + estimatedCounts[j][0][m]);
                //System.out.println();
            } else {    //For variables with parents:
                int numRows = inputBayesIm.getNumRows(varIndex);
                for (int row = 0; row < numRows; row++) {
                    int[] parValues =
                            inputBayesIm.getParentValues(varIndex, row);
                    estimatedCountsDenom[varIndex][row] = 0.0;
                    for (int col = 0; col < var.getNumCategories(); col++) {
                        estimatedCounts[varIndex][row][col] = 0.0;
                    }

                    for (int i = 0; i < numCases; i++) {
                        //for a case where the parent values = parValues increment the estCount

                        boolean parentMatch = true;

                        for (int p = 0; p < parentVarIndices.length; p++) {
                            if (parValues[p] !=
                                    mixedData.getInt(i, parentVarIndices[p]) &&
                                    mixedData.getInt(i, parentVarIndices[p]) !=
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
                            if (mixedData.getInt(i, parentVarIndice) == -99) {
                                parentMissing = true;
                                break;
                            }
                        }


                        if (mixedData.getInt(i, j) != -99 && !parentMissing) {
                            estimatedCounts[j][row][mixedData.getInt(i, j)] +=
                                    1.0;
                            estimatedCountsDenom[j][row] += 1.0;
                            continue;    //Next case
                        }

                        //for a case with missing data (either ar or one of its parents)
                        //compute the joint marginal
                        //distribution for ar & this combination of values of its parents
                        //and update the estCounts accordingly

                        //To compute marginals create the evidence
                        boolean existsEvidence = false;

                        Evidence evidenceThisCase = Evidence.tautology(inputBayesIm);

                        // "evidenceVars" not used.
//                        List<String> evidenceVars = new LinkedList<String>();
//                        for (int k = 0; k < numVariables; k++) {
//                            //if(k == j) continue;
//                            Variable otherVar = allVariables.get(k);
//                            if (mixedData.getInt(i, k) == -99) {
//                                continue;
//                            }
//                            existsEvidence = true;
//                            String otherVarName = otherVar.getName();
//                            Node otherNode = graph.getNode(otherVarName);
//                            int otherIndex = inputBayesIm.getNodeIndex(
//                                    otherNode);
//                            evidenceThisCase.getProposition().setCategory(
//                                    otherIndex, mixedData.getInt(i, k));
//                            evidenceVars.add(otherVarName);
//                        }

                        if (!existsEvidence) {
                            continue;
                        }

                        rseu.setEvidence(evidenceThisCase);

                        estimatedCountsDenom[j][row] += rseu.getJointMarginal(
                                parentVarIndices, parValues);

                        int[] parPlusChildIndices =
                                new int[parentVarIndices.length + 1];
                        int[] parPlusChildValues =
                                new int[parentVarIndices.length + 1];

                        parPlusChildIndices[0] = varIndex;
                        for (int pc = 1; pc < parPlusChildIndices.length; pc++) {
                            parPlusChildIndices[pc] = parentVarIndices[pc - 1];
                            parPlusChildValues[pc] = parValues[pc - 1];
                        }

                        for (int m = 0; m < var.getNumCategories(); m++) {

                            parPlusChildValues[0] = m;

                            /*
                            if(varName.equals("X1") && i == 0 ) {
                                System.out.println("Calling getJointMarginal with parvalues");
                                for(int k = 0; k < parPlusChildIndices.length; k++) {
                                    int pIndex = parPlusChildIndices[k];
                                    Node pNode = inputBayesIm.getIndex(pIndex);
                                    String pName = pNode.getName();
                                    System.out.println(pName + " " + parPlusChildValues[k]);
                                }
                            }
                            */

                            /*
                            if(varName.equals("X1") && i == 0 ) {
                                System.out.println("Evidence = " + evidenceThisCase);
                                //int[] vars = {l1Index, x1Index};
                                Node nodex1 = inputBayesIm.getIndex("X1");
                                int x1Index = inputBayesIm.getNodeIndex(nodex1);
                                Node nodel1 = inputBayesIm.getIndex("L1");
                                int l1Index = inputBayesIm.getNodeIndex(nodel1);

                                int[] vars = {l1Index, x1Index};
                                int[] vals = {0, 0};
                                double ptest = rseu.getJointMarginal(vars, vals);
                                System.out.println("Joint marginal (X1=0, L1 = 0) = " + p);
                            }
                            */

                            estimatedCounts[j][row][m] += rseu.getJointMarginal(
                                    parPlusChildIndices, parPlusChildValues);

                            //System.out.println("Case " + i + " parent values ");
                            //for (int pp = 0; pp < parentVarIndices.length; pp++) {
                            //    Variable par = (Variable) allVariables.get(parentVarIndices[pp]);
                            //    System.out.print("    " + par.getName() + " " + parValues[pp]);
                            //}

                            //System.out.println();
                            //System.out.println("Adding " + p + " to " + varName +
                            //        " row " + row + " category " + m);

                        }
                        //}
                    }

                    //Print estimated counts:
                    //System.out.println("Estimated counts:  ");
                    //System.out.println("    Parent values:  ");
                    //for (int i = 0; i < parentVarIndices.length; i++) {
                    //    Variable par = (Variable) allVariables.get(parentVarIndices[i]);
                    //    System.out.print("    " + par.getName() + " " + parValues[i] + "    ");
                    //}
                    //System.out.println();

                    //for(int m = 0; m < ar.getNumSplits(); m++)
                    //    System.out.print("    " + m + " " + estimatedCounts[j][row][m]);
                    //System.out.println();

                }


            }        //else
        }      // j < numVariables

        BayesIm outputBayesIm = new MlBayesIm(bayesPm);

        for (int j = 0; j < nodes.length; j++) {

            DiscreteVariable var = (DiscreteVariable) allVariables.get(j);
            String varName = var.getName();
            Node varNode = graph.getNode(varName);
            int varIndex = inputBayesIm.getNodeIndex(varNode);
//            int[] parentVarIndices = inputBayesIm.getParents(varIndex);

            int numRows = inputBayesIm.getNumRows(j);
            //System.out.println("Conditional probabilities for variable " + varName);

            int numCols = inputBayesIm.getNumColumns(j);
            if (numRows == 1) {
                double sum = 0.0;
                for (int m = 0; m < numCols; m++) {
                    sum += estimatedCounts[j][0][m];
                }

                for (int m = 0; m < numCols; m++) {
                    condProbs[j][0][m] = estimatedCounts[j][0][m] / sum;
                    //System.out.print("  " + condProbs[j][0][m]);
                    outputBayesIm.setProbability(varIndex, 0, m,
                            condProbs[j][0][m]);
                }
                //System.out.println();
            } else {

                for (int row = 0; row < numRows; row++) {
//                    int[] parValues = inputBayesIm.getParentValues(varIndex,
//                            row);
                    //int numCols = inputBayesIm.getNumColumns(j);

                    //for (int p = 0; p < parentVarIndices.length; p++) {
                    //    Variable par = (Variable) allVariables.get(parentVarIndices[p]);
                    //    System.out.print("    " + par.getName() + " " + parValues[p]);
                    //}

                    //double sum = 0.0;
                    //for(int m = 0; m < numCols; m++)
                    //    sum += estimatedCounts[j][row][m];

                    for (int m = 0; m < numCols; m++) {
                        if (estimatedCountsDenom[j][row] != 0.0) {
                            condProbs[j][row][m] = estimatedCounts[j][row][m] /
                                    estimatedCountsDenom[j][row];
                        } else {
                            condProbs[j][row][m] = Double.NaN;
                        }
                        //System.out.print("  " + condProbs[j][row][m]);
                        outputBayesIm.setProbability(varIndex, row, m,
                                condProbs[j][row][m]);
                    }
                    //System.out.println();

                }
            }

        }
    }

    /**
     * This method is for use in the unit test for EmBayesEstimator.  It tests
     * the expectation method without iterating.
     */
    public void expectationOnly() {
        expectation(estimatedIm);
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
        BayesIm oldBayesIm = estimatedIm;
        BayesIm newBayesIm = null;
        int iteration = 0;

        while (Double.isNaN(distance) || distance > threshhold) {
            expectation(oldBayesIm);
            newBayesIm = getEstimatedIm();

            distance = BayesImDistanceFunction.distance(newBayesIm, oldBayesIm);
            iteration++;

            oldBayesIm = newBayesIm;
// For some reason this won't log. 7/20/2009
//            TetradLogger.getInstance().log("optimization",
//                    "Distance = " + distance + " at iteration " + iteration);
//            System.out.println(
//                    "Distance = " + distance + " at iteration " + iteration);
        }
        return newBayesIm;
    }

    private void findBayesNetObserved() {

        Dag dagObs = new Dag(graph);
        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.LATENT) {
                dagObs.removeNode(node);
            }
        }

        bayesPmObs = new BayesPm(dagObs, bayesPm);
        //System.out.println("bayesPm after deleting edges involving latents:");
        //System.out.println(bayesPmObs);
        //Graph g = bayesPmObs.getDag();
        //System.out.println(g);
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

        // Make sure all of the variables in the PM are in the data set;
        // otherwise, estimation is impossible.
//        List pmvars = bayesPm.getVariables();
//        List dsvars = dataSet.getVariables();
//        List obsVars = observedIm.getBayesPm().getVariables();

        //System.out.println("Bayes PM as received by estimateMixedIM:  ");
        //System.out.println(bayesPm);
//        Graph g = bayesPm.getDag();
        //System.out.println(g);

        //DEBUG Prints:
        //System.out.println("PM VARS " + pmvars);
        //System.out.println("DS VARS " + dsvars);
        //System.out.println("OBS IM Vars" + obsVars);

        BayesUtils.ensureVarsInData(bayesPm.getVariables(), dataSet);
        //        DataUtils.ensureVariablesExist(bayesPm, dataSet);

        // Create a new Bayes IM to store the estimated values.
        this.estimatedIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        int numNodes = estimatedIm.getNumNodes();

        for (int node = 0; node < numNodes; node++) {

            int numRows = estimatedIm.getNumRows(node);
            int numCols = estimatedIm.getNumColumns(node);
            int[] parentVarIndices = estimatedIm.getParents(node);
            if (nodes[node].getNodeType() == NodeType.LATENT) {
                continue;
            }

            //int nodeObsIndex = estimatedIm.getCorrespondingNodeIndex(node, observedIm);
            //System.out.println("nodes[node] name = " + nodes[node].getName());
            Node nodeObs = observedIm.getNode(nodes[node].getName());
            //System.out.println("nodeObs name = " + nodeObs.getName());
            int nodeObsIndex = observedIm.getNodeIndex(nodeObs);
//            int[] parentsObs = observedIm.getParents(nodeObsIndex);

            //System.out.println("For node " + nodes[node] + " parents are:  ");
            boolean anyParentLatent = false;
            for (int parentVarIndice : parentVarIndices) {
                //System.out.println(nodes[parentVarIndices[p]]);
                if (nodes[parentVarIndice].getNodeType() == NodeType.LATENT) {
                    anyParentLatent = true;
                    break;
                }
            }

            if (anyParentLatent) {
                continue;
            }

            //At this point node is measured in bayesPm and so are its parents.
            for (int row = 0; row < numRows; row++) {
                //                int[] parentValues = estimatedIm.getParentValues(node, row);

                //estimatedIm.randomizeRow(node, row);

                //if the node and all its parents are measured get the probs
                //from observedIm

                //loop:
                for (int col = 0; col < numCols; col++) {
                    double p =
                            observedIm.getProbability(nodeObsIndex, row, col);
                    estimatedIm.setProbability(node, row, col, p);
                }
            }
        }

    }

    public DataSet getMixedDataSet() {
        return mixedData;
    }

    public BayesIm getEstimatedIm() {
        return estimatedIm;
    }

    public double[][][] getEstimatedCounts() {
        return estimatedCounts;
    }

    public double[][][] getCondProbs() {
        return condProbs;
    }


    /**
     * This method is useful in computing all combinations of possible values of
     * a set of variables.  The int array sizes contains the number of values
     * (categories) of each of n variables.  For example suppose sizes = {2, 3,
     * 4, 2, 2}. Then consider the table: 0   0 0 0 0 0 1   1 0 0 0 0 2   0 1 0
     * 0 0 3   1 1 0 0 0 4   0 2 0 0 0 5   1 2 0 0 0 . . . 94   0 2 3 1 1 95   1
     * 2 3 1 1 </p> In this example the method returns the 5 values
     * corresponding to the index ind which ranges from 0 to 95. E.g.
     * indexToValues(94, {2, 3, 4, 2, 2}, 5) = {0, 2, 3, 1, 1}
     */
    public static int[] indexToValues(int ind, int[] sizes) {
        int n = sizes.length;
        int[] rep = new int[n];

        for (int i = 0; i < n; i++) {
            rep[i] = (byte) 0;
        }

        for (int i = 0; i < n; i++) {
            int rem = ind % sizes[i];
            if (rem != 0) {
                //rep[n - i - 1] = (byte) rem;
                rep[i] = rem;
                ind -= 1;
            }
            ind /= sizes[i];
        }

        return rep;
    }

    /**
     * Inverse of the function indexToValues.  Given a set of values this method
     * computes the corresonding index.  Hence for the example above
     * valuesToIndex({2, 3, 4, 2, 2}, {0, 2, 3, 1, 1}, 5) = 94.
     *
     * @param sizes
     * @param values
     * @return index
     */
    public static int valuesToIndex(int[] sizes, int[] values) {
        int n = sizes.length;
        int index = values[0];

        int prod = 1;
        for (int i = 1; i < n; i++) {
            prod *= sizes[i - 1];
            index += values[i] * prod;
        }
        return index;
    }

}





