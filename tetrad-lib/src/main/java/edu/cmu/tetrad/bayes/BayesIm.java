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
import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.Simulator;
import edu.cmu.tetrad.util.IM;

import java.util.List;

/**
 * Interface implemented by Bayes instantiated models. For purposes of
 * clarification, we distinguish a Bayes parametric model from a Bayes
 * instantiated model. The former provides enough information for us to know
 * what the parameters of the Bayes net are, given that we know the graph of the
 * Bayes net--i.e., it tells us how many categories each variable has and what
 * the names of those categories are. It does not, however, tell us what the
 * value of each parameter is; information about the value of each
 * parameter in the Bayes net is provided in the Bayes instantiated model. This
 * information is organized, variable by variable, in conditional probability
 * tables. For each variable, a table is stored representing enough information
 * to recover the conditional probability of each value of each variable
 * given each combination of values of the parents of the variable in the graph.
 * The rows of the table are the combinations of parent values of the variable,
 * and the columns of the table are variable values of the variable. Most of the
 * method in this interface are designed mainly to allow these values to be set
 * and retrieved. A few methods are dedicated to bookkeeping chores, like
 * clearing tables or initializing them randomly. One special method
 * (simulateData) is dedicated to the task of generating randomly simulated data
 * sets consistent with the conditional probabilities implied by the information
 * stored in the conditional probability tables of the Bayes net. See
 * implementations for details.
 *
 * @author Joseph Ramsey
 * @see edu.cmu.tetrad.graph.Dag
 * @see BayesPm
 */
public interface BayesIm extends VariableSource, IM, Simulator {

    /**
     * @return the underlying Bayes PM.
     */
    BayesPm getBayesPm();

    /**
     * @return the underlying DAG.
     */
    Dag getDag();

    /**
     * @return the number of nodes in the model.
     */
    int getNumNodes();

    /**
     * @return the node corresponding to the given node index.
     */
    Node getNode(int nodeIndex);

    /**
     * @param name the name of the node.
     * @return the node with the given name in the associated graph.
     */
    Node getNode(String name);

    /**
     * @param node the given node.
     * @return the index for that node, or -1 if the node is not in the
     * BayesIm.
     */
    int getNodeIndex(Node node);

    /**
     * @return the list of variable for this Bayes net.
     */
    List<Node> getVariables();

    /**
     * @return the list of variable names for this Bayes net.
     */
    List<String> getVariableNames();

    /**
     * @return the list of measured variableNodes.
     */
    List<Node> getMeasuredNodes();


    /**
     * @return the number of columns in the table of the given node N with index
     * 'nodeIndex'--that is, the number of possible values that N can take on.
     * That is, if P(N=v0 | P1=v1, P2=v2, ... Pn=vn) is a conditional
     * probability stored in 'probs', then the maximum number of rows in the
     * table for N is #vals(N).
     * @see #getNumRows
     */
    int getNumColumns(int nodeIndex);

    /**
     * @return the number of rows in the table of the given node, which would be
     * the total number of possible combinations of parent values for a given
     * node.  That is, if P(N=v0 | P1=v1, P2=v2, ... Pn=vn) is a conditional
     * probability stored in 'probs', then the maximum number of rows in the
     * table for N is #vals(P1) x #vals(P2) x ... x #vals(Pn).
     * @see #getRowIndex
     * @see #getNumColumns
     */
    int getNumRows(int nodeIndex);

    /**
     * @param nodeIndex the given node.
     * @return the number of parents of the given node.
     */
    int getNumParents(int nodeIndex);

    /**
     * @return the given parent of the given node.
     */
    int getParent(int nodeIndex, int parentIndex);

    /**
     * @return the dimension of the given parent for the given node.
     */
    int getParentDim(int nodeIndex, int parentIndex);

    /**
     * @return (a defensive copy of) the array representing the dimensionality
     * of each parent of a node, that is, the number of values which that node
     * can take on.  The order of entries in this array is the same as the order
     * of entries of nodes returned by getParents() for that node.
     * @see #getParents
     */
    int[] getParentDims(int nodeIndex);

    /**
     * @return (a defensive copy of) the array containing all of the parents of
     * a given node in the order in which they are stored internally.
     * @see #getParentDims
     */
    int[] getParents(int nodeIndex);

    /**
     * @param nodeIndex the index of the node.
     * @param rowIndex  the index of the row in question.
     * @return an array containing the combination of parent values for a given
     * node and given row in the probability table for that node.  To get the
     * combination of parent values from the row number, the row number is
     * represented using a variable-base place value system, where the bases
     * for each place value are the dimensions of the parents in the order in
     * which they are given by getParentDims().  For instance, if the row number
     * (base 10) is 103 and the parent dimension array is [3 5 7], we calculate
     * the first value as 103 / 7 = 14 with a remainder of 5.  We then divide
     * 14 / 5 = 2 with a remainder of 4.  We then divide 2 / 3 = 0 with a
     * remainder of 2.  The variable place value representation is [2 4 5],
     * which is the combination of parent values.  This is the inverse function
     * of getRowIndex().
     * @see #getNodeIndex
     * @see #getRowIndex
     */
    int[] getParentValues(int nodeIndex, int rowIndex);

    /**
     * @return the value in the probability table for the given node, at the
     * given row and column.
     */
    int getParentValue(int nodeIndex, int rowIndex, int colIndex);

    /**
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the row in the table for this for node which represents
     *                  the combination of parent values in question.
     * @param colIndex  the column in the table for this node which represents
     *                  the value of the node in question.
     * @return the probability for the given node at the given row and column in
     * the table for that node.  To get the node index, use getNodeIndex().  To
     * get the row index, use getRowIndex().  To get the column index, use
     * getCategoryIndex() from the underlying BayesPm().  The value returned
     * will represent a conditional probability of the form P(N=v0 | P1=v1,
     * P2=v2, ... , Pn=vn), where N is the node referenced by nodeIndex, v0 is
     * the value referenced by colIndex, and the combination of parent values
     * indicated is the combination indicated by rowIndex.
     * @see #getNodeIndex
     * @see #getRowIndex
     */
    double getProbability(int nodeIndex, int rowIndex, int colIndex);

    /**
     * @return the row in the table at which the given combination of parent
     * values is represented for the given node.  The row is calculated as a
     * variable-base place-value number.  For instance, if the array of
     * parent dimensions is [3, 5, 7] and the parent value combination is [2,
     * 4, 5], then the row number is (7 * (5 * (3 * 0 + 2) + 4)) + 5 = 103. This
     * is the inverse function to getVariableValues().  <p> Note: If the node
     * has n values, the length of 'values' must be >= the number of parents.
     * Only the first n values are used.
     * @see #getParentValues
     */
    int getRowIndex(int nodeIndex, int[] values);

    /**
     * Normalizes all rows in the tables associated with each of node in turn.
     */
    void normalizeAll();

    /**
     * Normalizes all rows in the table associated with a given node.
     */
    void normalizeNode(int nodeIndex);

    /**
     * Normalizes the given row.
     */
    void normalizeRow(int nodeIndex, int rowIndex);

    /**
     * Sets the probability for the given node at a given row and column in the
     * table for that node.  To get the node index, use getNodeIndex().  To get
     * the row index, use getRowIndex().  To get the column index, use
     * getCategoryIndex() from the underlying BayesPm().  The value returned
     * will represent a conditional probability of the form P(N=v0 | P1=v1,
     * P2=v2, ... , Pn=vn), where N is the node referenced by nodeIndex, v0 is
     * the value referenced by colIndex, and the combination of parent values
     * indicated is the combination indicated by rowIndex.
     *
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the row in the table for this for node which represents
     *                  the combination of parent values in question.
     * @param colIndex  the column in the table for this node which represents
     *                  the value of the node in question.
     * @param value     the desired probability to be set.
     * @see #getProbability
     */
    void setProbability(int nodeIndex, int rowIndex, int colIndex,
                        double value);

    /**
     * @return the index of the node with the given name in the specified
     * BayesIm.
     */
    int getCorrespondingNodeIndex(int nodeIndex, BayesIm otherBayesIm);

    /**
     * Assigns random probability values to the child values of this row that
     * add to 1.
     *
     * @param nodeIndex the node for the table that this row belongs to.
     * @param rowIndex  the index of the row.
     */
    void clearRow(int nodeIndex, int rowIndex);

    /**
     * Assigns random probability values to the child values of this row that
     * add to 1.
     *
     * @param nodeIndex the node for the table that this row belongs to.
     * @param rowIndex  the index of the row.
     */
    void randomizeRow(int nodeIndex, int rowIndex);

    /**
     * Randomizes any row in the table for the given node index that has a
     * Double.NaN value in it.
     *
     * @param nodeIndex the node for the table whose incomplete rows are to be
     *                  randomized.
     */
    void randomizeIncompleteRows(int nodeIndex);

    /**
     * Randomizes every row in the table for the given node index.
     *
     * @param nodeIndex the node for the table to be randomized.
     */
    void randomizeTable(int nodeIndex);

    /**
     * Randomizes every row in the table for the given node index.
     *
     * @param nodeIndex the node for the table to be randomized.
     */
    void clearTable(int nodeIndex);

    /**
     * @return true iff one of the values in the given row is Double.NaN.
     */
    boolean isIncomplete(int nodeIndex, int rowIndex);

    /**
     * @return true iff any value in the table for the given node is
     * Double.NaN.
     */
    boolean isIncomplete(int nodeIndex);

    /**
     * Simulates a sample with the given sample size.
     *
     * @param sampleSize the sample size.
     * @return the simulated sample as a DataSet.
     */
    DataSet simulateData(int sampleSize, boolean latentDataSaved);

    /**
     * Simulates a sample with the given sample size.
     *
     * @param sampleSize the sample size.
     * @return the simulated sample as a DataSet.
     */
    DataSet simulateData(int sampleSize, long seed, boolean latentDataSaved);

    /**
     * Overwrites the given dataSet with a new simulated dataSet, to avoid
     * allocating memory. The given dataSet must have the necessary number of
     * columns.
     *
     * @return the simulated sample as a DataSet.
     */
    DataSet simulateData(DataSet dataSet, boolean latentDataSaved);

    /**
     * @return true iff this bayes net is equal to the given Bayes net. The
     * sense of equality may vary depending on the type of Bayes net.
     */
    boolean equals(Object o);

    /**
     * @return a string representation for this Bayes net.
     */
    String toString();
}





