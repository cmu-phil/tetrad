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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

/**
 * <p>
 * Stores Dirichlet pseudocounts for the distributions of each variable conditional on particular combinations of its
 * parent values and, together with Bayes Pm and Dag, provides methods to manipulate these tables. The division of labor
 * is as follows. The Dag is responsible for manipulating the basic graphical structure of the Dirichlet Bayes net. Dag
 * also stores and manipulates the names of the nodes in the graph; there are no method in either BayesPm or
 * DiriculetBayesIm to do this. BayesPm stores and manipulates the *values* of each node in a DAG, considered as a
 * variable in a Bayes net. The number of values for a variable can be changed there as well as the names for those
 * values. This class, DirichletBayesIm, stores the actual tables of parameter pseudocounts whose structures are implied
 * by the structures in the other two classes. The implied parameters take the form of conditional probabilities--e.g.,
 * P(V=v0|P1=v1, P2=v2, ...), for all nodes and all combinations of their parent values. The set of all such
 * probabilities is organized in this class as a three-dimensional table of double values. The first dimension
 * corresponds to the nodes in the DAG. For each such node, the second dimension corresponds to a flat list of
 * combinations of parent values for that node. The third dimension corresponds to the list of pseudocounts for each
 * node/row combination. Two methods in this class allow these values to be set and retrieved: <ul>
 * <li>getPseudocount(int nodeIndex, int rowIndex, int colIndex); and, <li>setPseudocount(int nodeIndex, int rowIndex,
 * int colIndex, int pValue). </ul> A third method, getRowPseudocount, calculates the total pseudocount in a given row
 * on the fly. Maximum likelihood probabilities may be computed on the fly using the method getWordRatio. In order to
 * use these methods, one needs to know the index of the node in question and the index of the row in question. (The
 * index of the column is the same as the index of the node value.) To determine the index of the node in question, use
 * the method <ul> <li> getNodeIndex(Node node). </ul> To determine the index of the row in question, use the method
 * <ul>
 * <li>getRowIndex(int[] parentVals). </ul> To determine the order of the parent
 * values for a given node so that you can build the parentVals[] array, use the method <ul> <li> getParents(int
 * nodeIndex) </ul> To determine the index of a value, use the method <ul> <li> getCategoryIndex(Node node) </ul> in
 * BayesPm. The rest of the methods in this class are easily understood as variants of the methods above.
 * <p>
 * Thanks to Bill Taysom for an earlier version.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class DirichletBayesIm implements BayesIm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Tolerance.
     */
    private static final double ALLOWABLE_DIFFERENCE = 1.0e-10;

    /**
     * The associated Bayes PM model.
     *
     * @serial
     */
    private final BayesPm bayesPm;
    /**
     * The array of nodes from the graph. Order is important.
     *
     * @serial
     */
    private final Node[] nodes;
    /**
     * 1.0000 The default row size for randomly creating new rows.
     *
     * @serial
     */
    private double nextRowTotal = 100.0;
    /**
     * The array of dimensionality (number of values for each node) for each of the subarrays of 'parents'.
     *
     * @serial
     */
    private int[][] parentDims;

    /**
     * The list of parents for each node from the graph. Order or nodes corresponds to the order of nodes in 'nodes',
     * and order in subarrays is important.
     *
     * @serial
     */
    private int[][] parents;

    /**
     * The main data structure; stores the values of all of the pseudocounts for the Dirichlet Bayes net of the form row
     * by row, for each node. The first dimension is the node N, in the order of 'nodes'. The second dimension is the
     * row index for the table of parameters associated with node N; the third dimension is the column index. The row
     * index is calculated by the function getRowIndex(int[] values) where 'values' is an array of numerical indices for
     * each of the parent values; the order of the values in this array is the same as the order of node in 'parents';
     * the value indices are obtained from the Bayes PM for each node. The column is the index of the value of N, where
     * this index is obtained from the Bayes PM. If a pseudocount is -1, that means its value should be filled in
     * manually.
     *
     * @serial
     */
    private double[][][] pseudocounts;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new DirichletBayesIm from the given BayesPm, initializing all values to -1.0, forcing them to be set
     * manually.
     *
     * @param bayesPm the given Bayes PM. Carries with it the underlying graph model.
     * @throws IllegalArgumentException if the array of nodes provided is not a permutation of the nodes contained in
     *                                  the bayes parametric model provided.
     */
    private DirichletBayesIm(BayesPm bayesPm) throws IllegalArgumentException {
        this(bayesPm, Double.NaN);
    }

    /**
     * Constructs a new DirichletBayesIm from the given BayesPm, initializing all parameters to the given symmetric
     * alpha, but using values from the old DirichletBayesIm provided where possible. If initialized manually (as
     * indicated by setting the symmetric alpha to Double.NaN), all values that cannot be retrieved from oldBayesIm will
     * be set to Double.NaN in each such row, forcing them to set manually; if initialized randomly, all values that
     * cannot be retrieved from oldBayesIm will distributed randomly in each such row.
     *
     * @param bayesPm        the given Bayes PM. Carries with it the underlying graph model.
     * @param symmetricAlpha the value that all Dirichlet parameters are initially set to, which must be nonnegative, or
     *                       Double.naN if all parameters should be set initially to "unspecified."
     * @throws IllegalArgumentException if the array of nodes provided is not a permutation of the nodes contained in
     *                                  the bayes parametric model provided.
     */
    private DirichletBayesIm(BayesPm bayesPm, double symmetricAlpha) throws IllegalArgumentException {
        if (bayesPm == null) {
            throw new NullPointerException("BayesPm must not be null.");
        }

        this.bayesPm = new BayesPm(bayesPm);

        // Get the nodes from the BayesPm, fixing on an order. (This is
        // important; the nodes must always be in the same order for this
        // DirichletBayesIm.)
        Graph graph = bayesPm.getDag();
        this.nodes = new Node[graph.getNumNodes()];
        Iterator<Node> it = graph.getNodes().iterator();

        for (int i = 0; i < this.nodes.length; i++) {
            this.nodes[i] = it.next();
        }

        // Initialize.
        initialize(null, symmetricAlpha);
    }

    /**
     * Copy constructor.
     *
     * @param dirichletBayesIm a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    public DirichletBayesIm(DirichletBayesIm dirichletBayesIm)
            throws IllegalArgumentException {
        if (dirichletBayesIm == null) {
            throw new NullPointerException(
                    "DirichletBayesIm must not be null.");
        }

        this.bayesPm = dirichletBayesIm.getBayesPm();
        this.nextRowTotal = dirichletBayesIm.nextRowTotal;

        // Get the nodes from the BayesPm, fixing on an order. (This is
        // important; the nodes must always be in the same order for this
        // DirichletBayesIm.)
        this.nodes = new Node[dirichletBayesIm.getNumNodes()];
        for (int i = 0; i < dirichletBayesIm.getNumNodes(); i++) {
            this.nodes[i] = dirichletBayesIm.getNode(i);
        }

        // Copy all the old values over.
        initialize(dirichletBayesIm, Double.NaN);
    }

    /**
     * <p>blankDirichletIm.</p>
     *
     * @param bayesPm a {@link edu.cmu.tetrad.bayes.BayesPm} object
     * @return a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     */
    public static DirichletBayesIm blankDirichletIm(BayesPm bayesPm) {
        return new DirichletBayesIm(bayesPm);
    }

    /**
     * <p>symmetricDirichletIm.</p>
     *
     * @param bayesPm        a {@link edu.cmu.tetrad.bayes.BayesPm} object
     * @param symmetricAlpha a double
     * @return a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     */
    public static DirichletBayesIm symmetricDirichletIm(BayesPm bayesPm,
                                                        double symmetricAlpha) {
        return new DirichletBayesIm(bayesPm, symmetricAlpha);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     */
    public static DirichletBayesIm serializableInstance() {
        return DirichletBayesIm.blankDirichletIm(BayesPm.serializableInstance());
    }

    //===============================PUBLIC METHODS========================//

    /**
     * This method chooses random probabilities for a row which add up to 1.0. Random doubles are drawn from a random
     * distribution, and the final row is then normalized.
     *
     * @param size the length of the row.
     * @return an array with randomly distributed probabilities of this length.
     * @see #randomizeRow
     */
    private static double[] getRandomWeights(int size) {
        assert size >= 0;

        double[] weights = new double[size];
        double sum = 0.0;

        for (int i = 0; i < size; i++) {
            RandomUtil randomUtil = RandomUtil.getInstance();
            weights[i] = randomUtil.nextDouble();
            sum += weights[i];
        }

        for (int i = 0; i < size; i++) {
            weights[i] /= sum;
        }

        return weights;
    }

    /**
     * <p>Getter for the field <code>bayesPm</code>.</p>
     *
     * @return this PM.
     */
    public BayesPm getBayesPm() {
        return this.bayesPm;
    }

    /**
     * {@inheritDoc}
     */
    public int getCorrespondingNodeIndex(int nodeIndex, BayesIm otherBayesIm) {
        String nodeName = getNode(nodeIndex).getName();
        Node oldNode = otherBayesIm.getNode(nodeName);
        return otherBayesIm.getNodeIndex(oldNode);
    }

    /**
     * <p>getDag.</p>
     *
     * @return the DAG.
     */
    public Graph getDag() {
        return this.bayesPm.getDag();
    }

    /**
     * The row total that will be used for the next randomized row. This should be set before calling a randomize
     * method. The default value is 100.
     */
    private double getNextRowTotal() {
        return this.nextRowTotal;
    }

    /**
     * The row total that will be used for the next randomized row. This should be set before calling a randomize
     * method. The default value is 100.
     */
    private void setNextRowTotal(double nextRowTotal) {
        this.nextRowTotal = nextRowTotal;
    }

    /**
     * {@inheritDoc}
     */
    public Node getNode(int nodeIndex) {
        return this.nodes[nodeIndex];
    }

    /**
     * <p>getNode.</p>
     *
     * @param name the name of the node.
     * @return the node.
     */
    public Node getNode(String name) {
        return getDag().getNode(name);
    }

    /**
     * {@inheritDoc}
     */
    public int getNodeIndex(Node node) {
        for (int i = 0; i < this.nodes.length; i++) {
            if (node == this.nodes[i]) {
                return i;
            }
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    public int getNumColumns(int nodeIndex) {
        return this.pseudocounts[nodeIndex][0].length;
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return the number of nodes in the model.
     */
    public int getNumNodes() {
        return this.nodes.length;
    }

    /**
     * {@inheritDoc}
     */
    public int getNumParents(int nodeIndex) {
        return this.parents[nodeIndex].length;
    }

    /**
     * {@inheritDoc}
     */
    public int getNumRows(int nodeIndex) {
        return this.pseudocounts[nodeIndex].length;
    }

    /**
     * {@inheritDoc}
     */
    public int getParent(int nodeIndex, int parentIndex) {
        return this.parents[nodeIndex][parentIndex];
    }

    /**
     * {@inheritDoc}
     */
    public int getParentDim(int nodeIndex, int parentIndex) {
        return this.parentDims[nodeIndex][parentIndex];
    }

    /**
     * {@inheritDoc}
     */
    public int[] getParentDims(int nodeIndex) {
        int[] dims = this.parentDims[nodeIndex];
        int[] copy = new int[dims.length];
        System.arraycopy(dims, 0, copy, 0, dims.length);
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    public int[] getParents(int nodeIndex) {
        int[] nodeParents = this.parents[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    public int getParentValue(int nodeIndex, int rowIndex, int colIndex) {
        return getParentValues(nodeIndex, rowIndex)[colIndex];
    }

    /**
     * {@inheritDoc}
     */
    public int[] getParentValues(int nodeIndex, int rowIndex) {
        int[] dims = getParentDims(nodeIndex);
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    /**
     * {@inheritDoc}
     */
    public double getProbability(int nodeIndex, int rowIndex, int colIndex) {
        double parameterPseudocount
                = getPseudocount(nodeIndex, rowIndex, colIndex);
        double rowPseudocount = getRowPseudocount(nodeIndex, rowIndex);

        if (parameterPseudocount < 0.0) {
            return Double.NaN;
        } else {
            return parameterPseudocount / rowPseudocount;
        }
    }

    /**
     * <p>getPseudocount.</p>
     *
     * @param nodeIndex a int
     * @param rowIndex  a int
     * @param colIndex  a int
     * @return a double
     */
    public double getPseudocount(int nodeIndex, int rowIndex, int colIndex) {
        return this.pseudocounts[nodeIndex][rowIndex][colIndex];
    }

    /**
     * <p>getRowIndex.</p>
     *
     * @param nodeIndex a int
     * @param values    an array of {@link int} objects
     * @return the row in the table for the given node and combination of parent values.
     * @see #getParentValues
     */
    public int getRowIndex(int nodeIndex, int[] values) {
        int[] dim = getParentDims(nodeIndex);
        int rowIndex = 0;

        for (int i = 0; i < dim.length; i++) {
            rowIndex *= dim[i];
            rowIndex += values[i];
        }

        return rowIndex;
    }

    /**
     * <p>getRowPseudocount.</p>
     *
     * @param nodeIndex a int
     * @param rowIndex  a int
     * @return a double
     */
    public double getRowPseudocount(int nodeIndex, int rowIndex) {
        double sum = 0;

        for (int i = 0; i < getNumColumns(nodeIndex); i++) {
            sum += getPseudocount(nodeIndex, rowIndex, i);
        }

        return sum > 0 ? sum : 0;
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        List<String> variableNames = new LinkedList<>();

        for (int i = 0; i < getNumNodes(); i++) {
            Node node = getNode(i);
            variableNames.add(this.bayesPm.getVariable(node).getName());
        }

        return variableNames;
    }

    //=============================PRIVATE METHODS=======================//

    /**
     * <p>getMeasuredNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getMeasuredNodes() {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        List<Node> variables = new LinkedList<>();

        for (int i = 0; i < getNumNodes(); i++) {
            Node node = getNode(i);
            variables.add(this.bayesPm.getVariable(node));
        }

        return variables;
    }

    /**
     * This method initializes the probability tables for all of the nodes in the Bayes net.
     *
     * @see #initializeNode
     * @see #randomizeRow
     */
    private void initialize(DirichletBayesIm oldBayesIm,
                            double symmetricAlpha) {
        this.parents = new int[this.nodes.length][];
        this.parentDims = new int[this.nodes.length][];
        this.pseudocounts = new double[this.nodes.length][][];

        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {
            initializeNode(nodeIndex, oldBayesIm, symmetricAlpha);
        }
    }

    /**
     * This method initializes the node indicated.
     */
    private void initializeNode(int nodeIndex, DirichletBayesIm oldBayesIm,
                                double symmetricAlpha) {
        Node node = this.nodes[nodeIndex];

        // Set up parents array.  Should store the parents of
        // each node as ints in a particular order.
        Graph graph = getBayesPm().getDag();
        List<Node> parentList = new ArrayList<>(graph.getParents(node));
        int[] parentArray = new int[parentList.size()];

        for (int i = 0; i < parentList.size(); i++) {
            parentArray[i] = getNodeIndex(parentList.get(i));
        }

        // Sort parent array.
        Arrays.sort(parentArray);

        this.parents[nodeIndex] = parentArray;

        // Setup dimensions array for parents.
        int[] dims = new int[parentArray.length];

        for (int i = 0; i < dims.length; i++) {
            Node parNode = this.nodes[parentArray[i]];
            dims[i] = getBayesPm().getNumCategories(parNode);
        }

        // Calculate dimensions of table.
        int numRows = 1;

        for (int dim : dims) {
            numRows *= dim;
        }

        int numCols = getBayesPm().getNumCategories(node);

        this.parentDims[nodeIndex] = dims;
        this.pseudocounts[nodeIndex] = new double[numRows][numCols];

        // Initialize each row.
        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            if (oldBayesIm == null) {
                overwriteRow(nodeIndex, rowIndex, symmetricAlpha);
            } else {
                retainOldRowIfPossible(nodeIndex, rowIndex, oldBayesIm,
                        symmetricAlpha);
            }
        }
    }

    private void initializeRowAsBlank(int nodeIndex, int rowIndex) {
        int size = getNumColumns(nodeIndex);
        double[] row = new double[size];
        Arrays.fill(row, Double.NaN);
        this.pseudocounts[nodeIndex][rowIndex] = row;
    }

    private void initializeRowSymmetrically(int nodeIndex, int rowIndex,
                                            double symmetricAlpha) {
        int size = getNumColumns(nodeIndex);
        double[] row = new double[size];
        Arrays.fill(row, symmetricAlpha);
        this.pseudocounts[nodeIndex][rowIndex] = row;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isIncomplete(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            if (isIncomplete(nodeIndex, rowIndex)) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isIncomplete(int nodeIndex, int rowIndex) {
        for (int colIndex = 0; colIndex < getNumColumns(nodeIndex); colIndex++) {
            double p = getProbability(nodeIndex, rowIndex, colIndex);

            if (Double.isNaN(p)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Normalizes all rows in the tables associated with each of node in turn.
     */
    public void normalizeAll() {
        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {
            normalizeNode(nodeIndex);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Normalizes all rows in the table associated with a given node.
     */
    public void normalizeNode(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            normalizeRow(nodeIndex, rowIndex);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Normalizes the given row.
     */
    public void normalizeRow(int nodeIndex, int rowIndex) {
        int numColumns = getNumColumns(nodeIndex);
        double total = 0.0;

        for (int colIndex = 0; colIndex < numColumns; colIndex++) {
            total += getProbability(nodeIndex, rowIndex, colIndex);
        }

        if (total != 0.0) {
            for (int colIndex = 0; colIndex < numColumns; colIndex++) {
                double probability = getProbability(nodeIndex, rowIndex, colIndex);
                double prob = probability / total;
                setProbability(nodeIndex, rowIndex, colIndex, prob);
            }
        } else {
            double prob = 1.0 / numColumns;

            for (int colIndex = 0; colIndex < numColumns; colIndex++) {
                setProbability(nodeIndex, rowIndex, colIndex, prob);
            }
        }
    }

    private void overwriteRow(int nodeIndex, int rowIndex,
                              double symmetricAlpha) {
        if (Double.isNaN(symmetricAlpha)) {
            initializeRowAsBlank(nodeIndex, rowIndex);
        } else if (symmetricAlpha >= 0.0) {
            initializeRowSymmetrically(nodeIndex, rowIndex, symmetricAlpha);
        } else {
            throw new IllegalArgumentException(
                    "Illegal symmetric alpha: " + symmetricAlpha);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Randomizes any row in the table for the given node index that has a Double.NaN value in it.
     */
    public void randomizeIncompleteRows(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            if (isIncomplete(nodeIndex, rowIndex)) {
                randomizeRow(nodeIndex, rowIndex);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Assigns random probability values to the child values of this row that add to 1.
     */
    public void randomizeRow(int nodeIndex, int rowIndex) {
        int size = getNumColumns(nodeIndex);
        setNextRowTotal(getRowPseudocount(nodeIndex, rowIndex));
        this.pseudocounts[nodeIndex][rowIndex] = getRandomPseudocounts(size);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Randomizes every row in the table for the given node index.
     */
    public void randomizeTable(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            randomizeRow(nodeIndex, rowIndex);
        }
    }

    /**
     * This method initializes the node indicated.
     */
    private void retainOldRowIfPossible(int nodeIndex, int rowIndex,
                                        DirichletBayesIm oldBayesIm, double symmetricAlpha) {
        int oldNodeIndex = getCorrespondingNodeIndex(nodeIndex, oldBayesIm);

        if (oldNodeIndex == -1) {
            overwriteRow(nodeIndex, rowIndex, symmetricAlpha);
        } else if (getNumColumns(nodeIndex) != oldBayesIm.getNumColumns(oldNodeIndex)) {
            overwriteRow(nodeIndex, rowIndex, symmetricAlpha);
        } else {
            int oldRowIndex
                    = getUniqueCompatibleOldRow(nodeIndex, rowIndex, oldBayesIm);

            if (oldRowIndex >= 0) {
                copyValuesFromOldToNew(oldNodeIndex, oldRowIndex, nodeIndex,
                        rowIndex, oldBayesIm);
            } else {
                overwriteRow(nodeIndex, rowIndex, symmetricAlpha);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the probability for the given node. The matrix row represent row index, the row in the table for this for
     * node which represents the combination of parent values in question. of the CPT. The matrix column represent
     * column index, the column in the table for this node which represents the value of the node in question.
     */
    @Override
    public void setProbability(int nodeIndex, double[][] probMatrix) {
        throw new UnsupportedOperationException("Please set pseudocounts and "
                                                + "not probabilities for this Dirichlet Bayes IM.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the probability for the given node at a given row and column in the table for that node. To get the node
     * index, use getNodeIndex(). To get the row index, use getRowIndex(). To get the column index, use
     * getCategoryIndex() from the underlying BayesPm(). The value returned will represent a conditional probability of
     * the form P(N=v0 | P1=v1, P2=v2, ... , Pn=vn), where N is the node referenced by nodeIndex, v0 is the value
     * referenced by colIndex, and the combination of parent values indicated is the combination indicated by rowIndex.
     *
     * @see #getProbability
     */
    public void setProbability(int nodeIndex, int rowIndex, int colIndex,
                               double value) {
        throw new UnsupportedOperationException("Please set pseudocounts and "
                                                + "not probabilities for this Dirichlet Bayes IM.");
    }

    /**
     * <p>setPseudocount.</p>
     *
     * @param nodeIndex   a int
     * @param rowIndex    a int
     * @param colIndex    a int
     * @param pseudocount a double
     */
    public void setPseudocount(int nodeIndex, int rowIndex, int colIndex,
                               double pseudocount) {
        if (pseudocount < 0) {
            throw new IllegalArgumentException(
                    "Pseudocounts must be nonnegative.");
        }

        this.pseudocounts[nodeIndex][rowIndex][colIndex] = pseudocount;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Simulates and returns a dataset with number of cases equal to
     * <code>sampleSize</code>. if <code>latentDataSaved</code> is true, data
     * for latent variables is included in the simulated dataset.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        return simulateDataHelper(sampleSize,
                RandomUtil.getInstance(), latentDataSaved);
    }

//    /**
//     * Simulates a random sample with the number of cases equal to
//     * <code>sampleSize</code>.
//     *
//     * @param sampleSize      the sample size.
//     * @param seed            the random number generator seed allows you recreate the
//     *                        simulated data by passing in the same seed (so you don't have to store
//     *                        the sample data
//     * @param latentDataSaved true iff data for latent variables should be
//     *                        included in the simulated data set.
//     * @return the simulated sample as a DataSet.
//     */
//    public DataSet simulateData(int sampleSize, long seed,
//                                boolean latentDataSaved) {
//        RandomUtil random = RandomUtil.getInstance();
//        random.setSeed(seed);
//        return simulateData(sampleSize, latentDataSaved);
//    }

    /**
     * {@inheritDoc}
     * <p>
     * Would be nice to have this method supported, but no one's using it, so it's not.
     */
    public DataSet simulateData(DataSet dataSet, boolean latentDataSaved) {
        throw new UnsupportedOperationException();
    }

    /**
     * Simulates a sample with the given sample size.
     *
     * @param sampleSize      the sample size.
     * @param randomUtil      optional random number generator to use when creating the data
     * @param latentDataSaved true iff data for latent variables should be saved.
     * @return the simulated sample as a DataSet.
     */
    private DataSet simulateDataHelper(int sampleSize,
                                       RandomUtil randomUtil,
                                       boolean latentDataSaved) {
        int numMeasured = 0;
        int[] map = new int[this.nodes.length];
        List<Node> variables = new LinkedList<>();

        for (int j = 0; j < this.nodes.length; j++) {
            if (!latentDataSaved && this.nodes[j].getNodeType() != NodeType.MEASURED) {
                continue;
            }

            int numCategories = this.bayesPm.getNumCategories(this.nodes[j]);
            List<String> categories = new LinkedList<>();

            for (int k = 0; k < numCategories; k++) {
                categories.add(this.bayesPm.getCategory(this.nodes[j], k));
            }

            DiscreteVariable var
                    = new DiscreteVariable(this.nodes[j].getName(), categories);
            variables.add(var);
            int index = ++numMeasured - 1;
            map[index] = j;
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(sampleSize, variables.size()), variables);
        constructSample(sampleSize, randomUtil, numMeasured, dataSet, map);
        return dataSet;
    }

    private void constructSample(int sampleSize, RandomUtil randomUtil,
                                 int numMeasured, DataSet dataSet,
                                 int[] map) {
        // Get a tier ordering and convert it to an int array.
        Graph graph = getBayesPm().getDag();
        Dag dag = new Dag(graph);
        Paths paths = dag.paths();
        List<Node> initialOrder = dag.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);
        int[] tiers = new int[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            tiers[i] = getNodeIndex(tierOrdering.get(i));
        }

        // Construct the sample.
        int[] combination = new int[this.nodes.length];

        for (int i = 0; i < sampleSize; i++) {
            int[] point = new int[this.nodes.length];

            for (int nodeIndex : tiers) {
                double cutoff = randomUtil.nextDouble();

                for (int k = 0; k < getNumParents(nodeIndex); k++) {
                    combination[k] = point[getParent(nodeIndex, k)];
                }

                int rowIndex = getRowIndex(nodeIndex, combination);
                double sum = 0.0;

                for (int k = 0; k < getNumColumns(nodeIndex); k++) {
                    double probability = getProbability(nodeIndex, rowIndex, k);

                    if (Double.isNaN(probability)) {
                        throw new IllegalStateException("Some probability "
                                                        + "values in the BayesIm are not filled in; "
                                                        + "cannot simulate data.");
                    }

                    sum += probability;

                    if (sum >= cutoff) {
                        point[nodeIndex] = k;
                        break;
                    }
                }
            }

            for (int j = 0; j < numMeasured; j++) {
                dataSet.setInt(i, j, point[map[j]]);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Assigns random probability values to the child values of this row that add to 1.
     */
    public void clearRow(int nodeIndex, int rowIndex) {
        for (int colIndex = 0; colIndex < getNumColumns(nodeIndex); colIndex++) {
            setProbability(nodeIndex, rowIndex, colIndex, Double.NaN);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Randomizes every row in the table for the given node index.
     */
    public void clearTable(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            clearRow(nodeIndex, rowIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof DirichletBayesIm otherIm)) {
            return false;
        }

        if (getNumNodes() != otherIm.getNumNodes()) {
            return false;
        }

        for (int i = 0; i < getNumNodes(); i++) {
            int otherIndex = otherIm.getCorrespondingNodeIndex(i, otherIm);

            if (otherIndex == -1) {
                return false;
            }

            if (getNumColumns(i) != otherIm.getNumColumns(otherIndex)) {
                return false;
            }

            if (getNumRows(i) != otherIm.getNumRows(otherIndex)) {
                return false;
            }

            for (int j = 0; j < getNumRows(i); j++) {
                for (int k = 0; k < getNumColumns(i); k++) {
                    double probability = getProbability(i, j, k);
                    double otherProbability = otherIm.getPseudocount(i, j, k);

                    if (Double.isNaN(probability)
                        && Double.isNaN(otherProbability)) {
                        continue;
                    }

                    if (FastMath.abs(probability - otherProbability)
                        > DirichletBayesIm.ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Prints out the probability table for each variable.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        for (int i = 0; i < getNumNodes(); i++) {
            buf.append("\n\nNode: ").append(getNode(i));

            if (getNumParents(i) == 0) {
                buf.append("\n");
            } else {
                buf.append("\n\n");
                for (int k = 0; k < getNumParents(i); k++) {
                    buf.append(getNode(getParent(i, k))).append("\t");
                }
            }

            for (int j = 0; j < getNumRows(i); j++) {
                buf.append("\n");
                for (int k = 0; k < getNumParents(i); k++) {
                    buf.append(getParentValue(i, j, k));

                    if (k < getNumParents(i) - 1) {
                        buf.append("\t");
                    }
                }

                if (getNumParents(i) > 0) {
                    buf.append("\t");
                }

                for (int k = 0; k < getNumColumns(i); k++) {
                    buf.append(nf.format(getProbability(i, j, k))).append("\t");
                }
            }
        }

        buf.append("\n");

        return buf.toString();
    }

    //=============================PRIVATE METHODS=========================//
    private void copyValuesFromOldToNew(int oldNodeIndex, int oldRowIndex,
                                        int nodeIndex, int rowIndex, DirichletBayesIm oldBayesIm) {
        if (getNumColumns(nodeIndex) != oldBayesIm.getNumColumns(oldNodeIndex)) {
            throw new IllegalArgumentException("It's only possible to copy "
                                               + "one row of probability values to another in a Bayes IM "
                                               + "if the number of columns in the table are the same.");
        }

        for (int colIndex = 0; colIndex < getNumColumns(nodeIndex); colIndex++) {
            this.pseudocounts[nodeIndex][rowIndex][colIndex]
                    = oldBayesIm.getPseudocount(oldNodeIndex, oldRowIndex,
                    colIndex);
        }
    }

    /**
     * This method chooses random probabilities for a row which add up to 1.0. The method is as follows. If n
     * probabilities are required for the row, random values are chosen from a uniform distribution over [0.0, 1.0); the
     * final value is set to 1.0. Next, these values are sorted from least to greatest. Finally, the difference of each
     * value with its predecessor is calculated, and this array of differences is returned.
     *
     * @param size the length of the row.
     * @return an array with randomly distributed probabilities of this length.
     * @see #randomizeRow
     */
    private double[] getRandomPseudocounts(int size) {
        assert size >= 0;

        double[] weights = DirichletBayesIm.getRandomWeights(size);
        double[] row = new double[size];
        int sum = 0;

        for (int i = 0; i < row.length - 1; i++) {
            row[i] = (int) (getNextRowTotal() * weights[i]);
            sum += row[i];
        }

        row[size - 1] = getNextRowTotal() - sum;

        return row;
    }

    /**
     * @return the unique rowIndex in the old DirichletBayesIm for the given node that is compatible with the given
     * rowIndex in the new DirichletBayesIm for that node, if one exists. Otherwise, returns -1. A compatible rowIndex
     * is one in which all the parents that the given node has in common between the old DirichletBayesIm and the new
     * DirichletBayesIm are assigned the values they have in the new rowIndex. If a parent node is removed in the new
     * DirichletBayesIm, there may be more than one such compatible rowIndex in the old DirichletBayesIm, in which case
     * -1 is returned. Likewise, there may be no compatible rows, in which case -1 is returned.
     */
    private int getUniqueCompatibleOldRow(int nodeIndex, int rowIndex,
                                          BayesIm oldBayesIm) {
        int oldNodeIndex = getCorrespondingNodeIndex(nodeIndex, oldBayesIm);
        int oldNumParents = oldBayesIm.getNumParents(oldNodeIndex);

        int[] oldParentValues = new int[oldNumParents];
        Arrays.fill(oldParentValues, -1);

        int[] parentValues = getParentValues(nodeIndex, rowIndex);

        // Go through each parent of the node in the new DirichletBayesIm.
        for (int i = 0; i < getNumParents(nodeIndex); i++) {

            // Get the index of the parent in the new graph and in the old
            // graph. If it's no longer in the new graph, skip to the next
            // parent.
            int parentNodeIndex = getParent(nodeIndex, i);
            int oldParentNodeIndex
                    = getCorrespondingNodeIndex(parentNodeIndex, oldBayesIm);
            int oldParentIndex = -1;

            for (int j = 0; j < oldBayesIm.getNumParents(oldNodeIndex); j++) {
                if (oldParentNodeIndex == oldBayesIm.getParent(oldNodeIndex, j)) {
                    oldParentIndex = j;
                    break;
                }
            }

            if (oldParentIndex == -1
                || oldParentIndex >= oldBayesIm.getNumParents(oldNodeIndex)) {
                continue;
            }

            // Look up that value index for the new DirichletBayesIm for that parent.
            // If it was a valid value index in the old DirichletBayesIm, record
            // that value in oldParentValues. Otherwise return -1.
            int newParentValue = parentValues[i];
            int oldParentDim
                    = oldBayesIm.getParentDim(oldNodeIndex, oldParentIndex);

            if (newParentValue < oldParentDim) {
                oldParentValues[oldParentIndex] = newParentValue;
            } else {
                return -1;
            }
        }

        // If there are any -1's in the combination at this point, return -1.
        for (int oldParentValue : oldParentValues) {
            if (oldParentValue == -1) {
                return -1;
            }
        }

        // Otherwise, return the combination, which will be a row in the
        // old DirichletBayesIm.
        return oldBayesIm.getRowIndex(oldNodeIndex, oldParentValues);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesPm == null) {
            throw new NullPointerException();
        }

        if (this.nodes == null) {
            throw new NullPointerException();
        }

        if (this.parents == null) {
            throw new NullPointerException();
        }

        if (this.parentDims == null) {
            throw new NullPointerException();
        }

        if (this.pseudocounts == null) {
            throw new NullPointerException();
        }
    }
}
