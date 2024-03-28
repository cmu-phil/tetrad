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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Paths;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.Vector;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;

/**
 * Stores a table of probabilities for a Bayes net and, together with BayesPm and Dag, provides methods to manipulate
 * this table. The division of labor is as follows. The Dag is responsible for manipulating the basic graphical
 * structure of the Bayes net. Dag also stores and manipulates the names of the nodes in the graph; there are no method
 * in either BayesPm or BayesIm to do this. BayesPm stores and manipulates the *categories* of each node in a DAG,
 * considered as a variable in a Bayes net. The number of categories for a variable can be changed there as well as the
 * names for those categories. This class, BayesIm, stores the actual probability tables which are implied by the
 * structures in the other two classes. The implied parameters take the form of conditional probabilities--e.g.,
 * P(N=v0|P1=v1, P2=v2, ...), for all nodes and all combinations of their parent categories. The set of all such
 * probabilities is organized in this class as a three-dimensional table of double values. The first dimension
 * corresponds to the nodes in the Bayes net. For each such node, the second dimension corresponds to a flat list of
 * combinations of parent categories for that node. The third dimension corresponds to the list of categories for that
 * node itself. Two methods allow these values to be set and retrieved: getWordRatio(int nodeIndex, int rowIndex, int
 * colIndex); and, setProbability(int nodeIndex, int rowIndex, int colIndex, int probability). To determine the index of
 * the node in question, use the method getNodeIndex(Node node). To determine the index of the row in question, use the
 * method getRowIndex(int[] parentVals). To determine the order of the parent values for a given node so that you can
 * build the parentVals[] array, use the method getParents(int nodeIndex). To determine the index of a category, use the
 * method  getCategoryIndex(Node node) in BayesPm. The rest of the methods in this class are easily understood as
 * variants of the methods above.
 * <p>
 * This version uses a different method for storing the probabilities. The previous version stored the probabilities in
 * a three-dimensional array, where the first dimension was the node, the second dimension was the row index, and the
 * third dimension was the column index. This version stores and array of Matrix objects, where each Matrix object
 * represents the conditional probability table for a node. This will allow us in the future to represent this as an of
 * Maps from Integers to Doubles and store only the non-NaN values. This will save space and time in the case of sparse
 * tables.
 * <p>
 * Thanks to Pucktada Treeratpituk, Frank Wimberly, and Willie Wheeler for advice and earlier versions.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class MlBayesIm implements BayesIm {
    /**
     * Inidicates that new rows in this BayesIm should be initialized as unknowns, forcing them to be specified
     * manually. This is the default.
     */
    public static final int MANUAL = 0;
    /**
     * Indicates that new rows in this BayesIm should be initialized randomly.
     */
    public static final int RANDOM = 1;
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Tolerance.
     */
    private static final double ALLOWABLE_DIFFERENCE = 1.0e-3;
    /**
     * Random number generator.
     */
    static private final Random random = new Random();
    /**
     * The associated Bayes PM model.
     */
    private final BayesPm bayesPm;
    /**
     * The array of nodes from the graph. Order is important.
     */
    private final Node[] nodes;
    /**
     * A flag indicating whether to use probability matrices or not.
     */
    boolean useProbMatrices = true;
    /**
     * The list of parents for each node from the graph. Order or nodes corresponds to the order of nodes in 'nodes',
     * and order in subarrays is important.
     */
    private int[][] parents;
    /**
     * The array of dimensionality (number of categories for each node) for each of the subarrays of 'parents'.
     */
    private int[][] parentDims;
    /**
     * The main data structure; stores the values of all of the conditional probabilities for the Bayes net of the form
     * P(N=v0 | P1=v1, P2=v2,...). The first dimension is the node N, in the order of 'nodes'. The second dimension is
     * the row index for the table of parameters associated with node N; the third dimension is the column index. The
     * row index is calculated by the function getRowIndex(int[] values) where 'values' is an array of numerical indices
     * for each of the parent values; the order of the values in this array is the same as the order of node in
     * 'parents'; the value indices are obtained from the Bayes PM for each node. The column is the index of the value
     * of N, where this index is obtained from the Bayes PM.
     *
     * @serial
     */
    private double[][][] probs;
    /**
     * The array of probability maps for each node. The index of the node corresponds to the index of the probability
     * map in this array. The probability map is a map from a unique integer index for a particular node to the
     * probability of that node taking on that value, where NaN's are not stored. Replaces the probs array.
     */
    private CptMap[] probMatrices;

    /**
     * Constructs a new BayesIm from the given BayesPm, initializing all values as Double.NaN ("?").
     *
     * @param bayesPm the given Bayes PM. Carries with it the underlying graph model.
     * @throws java.lang.IllegalArgumentException if the array of nodes provided is not a permutation of the nodes
     *                                            contained in the bayes parametric model provided.
     */
    public MlBayesIm(BayesPm bayesPm) throws IllegalArgumentException {
        this(bayesPm, null, MlBayesIm.MANUAL);
    }

    /**
     * Constructs a new BayesIm from the given BayesPm, initializing values either as MANUAL or RANDOM. If initialized
     * manually, all values will be set to Double.NaN ("?") in each row; if initialized randomly, all values will
     * distribute randomly in each row.
     *
     * @param bayesPm              the given Bayes PM. Carries with it the underlying graph model.
     * @param initializationMethod either MANUAL or RANDOM.
     * @throws java.lang.IllegalArgumentException if the array of nodes provided is not a permutation of the nodes
     *                                            contained in the bayes parametric model provided.
     */
    public MlBayesIm(BayesPm bayesPm, int initializationMethod)
            throws IllegalArgumentException {
        this(bayesPm, null, initializationMethod);
    }

    /**
     * Constructs a new BayesIm from the given BayesPm, initializing values either as MANUAL or RANDOM, but using values
     * from the old BayesIm provided where posssible. If initialized manually, all values that cannot be retrieved from
     * oldBayesIm will be set to Double.NaN ("?") in each such row; if initialized randomly, all values that cannot be
     * retrieved from oldBayesIm will distributed randomly in each such row.
     *
     * @param bayesPm              the given Bayes PM. Carries with it the underlying graph model.
     * @param oldBayesIm           an already-constructed BayesIm whose values may be used where possible to initialize
     *                             this BayesIm. May be null.
     * @param initializationMethod either MANUAL or RANDOM.
     * @throws java.lang.IllegalArgumentException if the array of nodes provided is not a permutation of the nodes
     *                                            contained in the bayes parametric model provided.
     */
    public MlBayesIm(BayesPm bayesPm, BayesIm oldBayesIm,
                     int initializationMethod) throws IllegalArgumentException {
        if (bayesPm == null) {
            throw new NullPointerException("BayesPm must not be null.");
        }

        this.bayesPm = new BayesPm(bayesPm);

        // Get the nodes from the BayesPm. This fixes the order of the nodes
        // in the BayesIm, independently of any change to the BayesPm.
        // (This order must be maintained.)
        Graph graph = bayesPm.getDag();
        this.nodes = graph.getNodes().toArray(new Node[0]);

        // Initialize.
        initialize(oldBayesIm, initializationMethod);
    }

    /**
     * Copy constructor.
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    public MlBayesIm(BayesIm bayesIm) throws IllegalArgumentException {
        if (bayesIm == null) {
            throw new NullPointerException("BayesIm must not be null.");
        }

        this.bayesPm = bayesIm.getBayesPm();

        // Get the nodes from the BayesPm, fixing on an order. (This is
        // important; the nodes must always be in the same order for this
        // BayesIm.)
        this.nodes = new Node[bayesIm.getNumNodes()];

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            this.nodes[i] = bayesIm.getNode(i);
        }

        // Copy all the old values over.
        initialize(bayesIm, MlBayesIm.MANUAL);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.MlBayesIm} object
     */
    public static MlBayesIm serializableInstance() {
        return new MlBayesIm(BayesPm.serializableInstance());
    }

    //===============================PUBLIC METHODS========================//

    /**
     * <p>getParameterNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public static List<String> getParameterNames() {
        return new ArrayList<>();
    }

    private static double[] getRandomWeights(int size) {
        assert size > 0;

        double[] row = new double[size];
        double sum = 0.0;

        int strong = (int) Math.floor(random.nextDouble() * size);

        for (int i = 0; i < size; i++) {
            if (i == strong) {
                row[i] = 1.0;
            } else {
                row[i] = RandomUtil.getInstance().nextDouble() * 0.3;
            }

            sum += row[i];
        }

        for (int i = 0; i < size; i++) {
            row[i] /= sum;
        }

        return row;
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
     * <p>getDag.</p>
     *
     * @return the DAG.
     */
    public Graph getDag() {
        return this.bayesPm.getDag();
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
     * Retrieves the node at the specified index.
     *
     * @param nodeIndex the index of the node.
     * @return the node at the specified index.
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
     * Returns the index of the given node in the nodes array.
     *
     * @param node the given node.
     * @return the index of the node in the nodes array, or -1 if the node is not found.
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
     * <p>getMeasuredNodes.</p>
     *
     * @return the list of measured variableNodes.
     */
    public List<Node> getMeasuredNodes() {
        return this.bayesPm.getMeasuredNodes();
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

    /**
     * Returns the number of columns in the specified node.
     *
     * @param nodeIndex the index of the node.
     * @return the number of columns.
     */
    public int getNumColumns(int nodeIndex) {
        if (useProbMatrices) {
            return probMatrices[nodeIndex].getNumColumns();
        } else {
            return this.probs[nodeIndex][0].length;
        }
    }

    /**
     * Retrieves the number of rows in the specified node.
     *
     * @param nodeIndex the index of the node.
     * @return the number of rows in the node.
     */
    public int getNumRows(int nodeIndex) {
        if (useProbMatrices) {
            return probMatrices[nodeIndex].getNumRows();
        } else {
            return this.probs[nodeIndex].length;
        }
    }

    /**
     * Returns the number of parents for the given node.
     *
     * @param nodeIndex the index of the node.
     * @return the number of parents.
     */
    public int getNumParents(int nodeIndex) {
        return this.parents[nodeIndex].length;
    }

    /**
     * Retrieves the parent of a node at the specified index.
     *
     * @param nodeIndex   the index of the node.
     * @param parentIndex the index of the parent.
     * @return the parent of the node.
     */
    public int getParent(int nodeIndex, int parentIndex) {
        return this.parents[nodeIndex][parentIndex];
    }

    /**
     * Retrieves the value of the parent dimension for a given node and parent index.
     *
     * @param nodeIndex   the index of the node.
     * @param parentIndex the index of the parent.
     * @return the parent dimension value.
     */
    public int getParentDim(int nodeIndex, int parentIndex) {
        return this.parentDims[nodeIndex][parentIndex];
    }

    /**
     * Returns a copy of the dimensions of the parent node at the specified index.
     *
     * @param nodeIndex the index of the node.
     * @return an array containing the dimensions of the parent node.
     */
    public int[] getParentDims(int nodeIndex) {
        int[] dims = this.parentDims[nodeIndex];
        int[] copy = new int[dims.length];
        System.arraycopy(dims, 0, copy, 0, dims.length);
        return copy;
    }

    /**
     * Returns an array containing the parents of the specified node.
     *
     * @param nodeIndex the index of the node.
     * @return an array of integers representing the parents of the specified node.
     */
    public int[] getParents(int nodeIndex) {
        int[] nodeParents = this.parents[nodeIndex];
        int[] copy = new int[nodeParents.length];
        System.arraycopy(nodeParents, 0, copy, 0, nodeParents.length);
        return copy;
    }

    /**
     * Returns an integer array containing the parent values for a given node index and row index.
     *
     * @param nodeIndex the index of the node.
     * @param rowIndex  the index of the row in question.
     * @return an integer array containing the parent values.
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
     * Retrieves the value of the parent node at the specified row and column index.
     *
     * @param nodeIndex the index of the node.
     * @param rowIndex  the index of the row in question.
     * @param colIndex  the index of the column in question.
     * @return the value of the parent node at the specified row and column index.
     */
    public int getParentValue(int nodeIndex, int rowIndex, int colIndex) {
        return getParentValues(nodeIndex, rowIndex)[colIndex];
    }

    /**
     * Returns the probability for a given node in the table.
     *
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the row in the table for this node which represents the combination of parent values in
     *                  question.
     * @param colIndex  the column in the table for this node which represents the value of the node in question.
     * @return the probability value for the given node.
     */
    public double getProbability(int nodeIndex, int rowIndex, int colIndex) {
        if (useProbMatrices) {
            return probMatrices[nodeIndex].get(rowIndex, colIndex);
        } else {
            return this.probs[nodeIndex][rowIndex][colIndex];
        }
    }

    /**
     * Returns the row index corresponding to the given node index and combination of parent values.
     *
     * @param nodeIndex the index of the node in question.
     * @param values    the combination of parent values in question.
     * @return the row index corresponding to the given node index and combination of parent values.
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
     * Normalizes all rows in the tables associated with each of node in turn.
     */
    public void normalizeAll() {
        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {
            normalizeNode(nodeIndex);
        }
    }

    /**
     * Normalizes the specified node by invoking the {@link #normalizeRow(int, int)} method on each row of the node.
     *
     * @param nodeIndex the index of the node to be normalized.
     */
    public void normalizeNode(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            normalizeRow(nodeIndex, rowIndex);
        }
    }

    /**
     * Normalizes the probabilities of a given row in a node.
     *
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the index of the row in question.
     */
    public void normalizeRow(int nodeIndex, int rowIndex) {
        int numColumns = getNumColumns(nodeIndex);
        double total = 0.0;

        for (int colIndex = 0; colIndex < numColumns; colIndex++) {
            total += getProbability(nodeIndex, rowIndex, colIndex);
        }

        if (total != 0.0) {
            for (int colIndex = 0; colIndex < numColumns; colIndex++) {
                double probability
                        = getProbability(nodeIndex, rowIndex, colIndex);
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

    /**
     * Sets the probability for the given node. The matrix row represent row index, the row in the table for this for
     * node which represents the combination of parent values in question. of the CPT. The matrix column represent
     * column index, the column in the table for this node which represents the value of the node in question.
     *
     * @param nodeIndex  The index of the node.
     * @param probMatrix The matrix of probabilities.
     */
    @Override
    public void setProbability(int nodeIndex, double[][] probMatrix) {
        if (useProbMatrices) {
            probMatrices[nodeIndex] = new CptMap(probMatrix);
        } else {
            for (int i = 0; i < probMatrix.length; i++) {
                System.arraycopy(probMatrix[i], 0, this.probs[nodeIndex][i], 0, probMatrix[i].length);
            }
        }
    }

    /**
     * Sets the probability value for a specific node, row, and column in the probability table.
     *
     * @param nodeIndex the index of the node in question.
     * @param rowIndex  the row in the table for this node which represents the combination of parent values in
     *                  question.
     * @param colIndex  the column in the table for this node which represents the value of the node in question.
     * @param value     the desired probability to be set. Must be between 0.0 and 1.0, or Double.NaN.
     * @throws IllegalArgumentException if the column index is out of range for the given node, or if the probability
     *                                  value is not between 0.0 and 1.0 or Double.NaN.
     */
    public void setProbability(int nodeIndex, int rowIndex, int colIndex,
                               double value) {
        if (colIndex >= getNumColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: "
                                               + colIndex + " >= " + getNumColumns(nodeIndex));
        }

        if (!(0.0 <= value && value <= 1.0) && !Double.isNaN(value)) {
            throw new IllegalArgumentException("Probability value must be "
                                               + "between 0.0 and 1.0 or Double.NaN.");
        }

        if (useProbMatrices) {
            probMatrices[nodeIndex].set(rowIndex, colIndex, value);
        } else {
            this.probs[nodeIndex][rowIndex][colIndex] = value;
        }
    }

    /**
     * Returns the corresponding node index in the given BayesIm based on the node index in this BayesIm.
     *
     * @param nodeIndex    the index of the node in this BayesIm.
     * @param otherBayesIm the BayesIm in which the node is to be found.
     * @return the corresponding node index in the given BayesIm.
     */
    public int getCorrespondingNodeIndex(int nodeIndex, BayesIm otherBayesIm) {
        String nodeName = getNode(nodeIndex).getName();
        Node oldNode = otherBayesIm.getNode(nodeName);
        return otherBayesIm.getNodeIndex(oldNode);
    }

    /**
     * Clears all values in the specified row of a table.
     *
     * @param nodeIndex the index of the node for the table that this row belongs to
     * @param rowIndex  the index of the row to be cleared
     */
    public void clearRow(int nodeIndex, int rowIndex) {
        for (int colIndex = 0; colIndex < getNumColumns(nodeIndex); colIndex++) {
            setProbability(nodeIndex, rowIndex, colIndex, Double.NaN);
        }
    }

    /**
     * Randomizes the values of a row in a table for a given node.
     *
     * @param nodeIndex the index of the node for the table that this row belongs to.
     * @param rowIndex  the index of the row to be randomized.
     */
    public void randomizeRow(int nodeIndex, int rowIndex) {
        int size = getNumColumns(nodeIndex);
        double[] row = getRandomWeights(size);

        if (useProbMatrices) {
            for (int colIndex = 0; colIndex < size; colIndex++) {
                probMatrices[nodeIndex].set(rowIndex, colIndex, row[colIndex]);
            }
        } else {
            this.probs[nodeIndex][rowIndex] = row;
        }

//        this.probs[nodeIndex][rowIndex] = MlBayesIm.getRandomWeights(size);
    }

    /**
     * Randomizes the incomplete rows in the specified node's table.
     *
     * @param nodeIndex the index of the node for the table whose incomplete rows are to be randomized
     */
    public void randomizeIncompleteRows(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            if (isIncomplete(nodeIndex, rowIndex)) {
                randomizeRow(nodeIndex, rowIndex);
            }
        }
    }

    /**
     * Randomizes the table for a given node.
     *
     * @param nodeIndex the index of the node for the table to be randomized
     */
    public void randomizeTable(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            randomizeRow(nodeIndex, rowIndex);
        }
    }

    /**
     * Clears the table by clearing all rows for the given node.
     *
     * @param nodeIndex The index of the node for the table to be cleared.
     */
    public void clearTable(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            clearRow(nodeIndex, rowIndex);
        }
    }

    /**
     * Checks if the specified row of a table is incomplete, i.e., if any of the columns have a NaN value.
     *
     * @param nodeIndex the index of the table node to check.
     * @param rowIndex  the index of the row to check.
     * @return true if the row is incomplete, false otherwise.
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
     * Checks if the specified table has any incomplete rows.
     *
     * @param nodeIndex the index of the node for the table
     * @return true if the table has any incomplete rows, false otherwise
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
     * Simulates a sample with the given sample size.
     *
     * @param sampleSize      the sample size.
     * @param latentDataSaved a boolean
     * @param tiers           an array of {@link int} objects
     * @return the simulated sample as a DataSet.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved, int[] tiers) {
        if (getBayesPm().getDag().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        }

        return simulateDataHelper(sampleSize, latentDataSaved, tiers);
    }

    /**
     * Simulates a data set.
     *
     * @param sampleSize      The number of rows to simulate.
     * @param latentDataSaved If set to true, latent variables are saved in the data set.
     * @return The simulated data set.
     * @throws IllegalArgumentException If the graph contains a directed cycle.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        if (getBayesPm().getDag().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        }

        // Get a tier ordering and convert it to an int array.
        Graph graph = getBayesPm().getDag();

        if (graph.paths().existsDirectedCycle()) {
            throw new IllegalArgumentException("Graph must be acyclic to simulate from discrete Bayes net.");
        }

        Paths paths = graph.paths();
        List<Node> initialOrder = graph.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);
        int[] tiers = new int[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            tiers[i] = getNodeIndex(tierOrdering.get(i));
        }

        return simulateDataHelper(sampleSize, latentDataSaved, tiers);
    }

    /**
     * <p>simulateData.</p>
     *
     * @param dataSet         a {@link edu.cmu.tetrad.data.DataSet} object
     * @param latentDataSaved a boolean
     * @param tiers           an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet simulateData(DataSet dataSet, boolean latentDataSaved, int[] tiers) {
        return simulateDataHelper(dataSet, latentDataSaved, tiers);
    }

    /**
     * Simulates data for the given data set.
     *
     * @param dataSet         The data set to simulate data for.
     * @param latentDataSaved Indicates whether latent data should be saved during simulation.
     * @return The modified data set after simulating the data.
     */
    public DataSet simulateData(DataSet dataSet, boolean latentDataSaved) {
        // Get a tier ordering and convert it to an int array.
        Graph graph = getBayesPm().getDag();
        Paths paths = graph.paths();
        List<Node> initialOrder = graph.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);
        int[] tiers = new int[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            tiers[i] = getNodeIndex(tierOrdering.get(i));
        }

        return simulateDataHelper(dataSet, latentDataSaved, tiers);
    }

    private DataSet simulateTimeSeries(int sampleSize) {
        TimeLagGraph timeSeriesGraph = getBayesPm().getDag().getTimeLagGraph();

        List<Node> variables = new ArrayList<>();

        for (Node node : timeSeriesGraph.getLag0Nodes()) {
            DiscreteVariable e = new DiscreteVariable(timeSeriesGraph.getNodeId(node).getName());
            e.setNodeType(node.getNodeType());
            variables.add(e);
        }

        List<Node> lag0Nodes = timeSeriesGraph.getLag0Nodes();

//        DataSet fullData = new ColtDataSet(sampleSize, variables);
        DataSet fullData = new BoxDataSet(new VerticalIntDataBox(sampleSize, variables.size()), variables);

        Graph contemporaneousDag = timeSeriesGraph.subgraph(lag0Nodes);
        Paths paths = contemporaneousDag.paths();
        List<Node> initialOrder = contemporaneousDag.getNodes();
        List<Node> tierOrdering = paths.getValidOrder(initialOrder, true);
        int[] tiers = new int[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            tiers[i] = getNodeIndex(tierOrdering.get(i));
        }

        // Construct the sample.
        int[] combination = new int[tierOrdering.size()];

        for (int i = 0; i < sampleSize; i++) {
            int[] point = new int[this.nodes.length];

            for (int nodeIndex : tiers) {
                double cutoff = RandomUtil.getInstance().nextDouble();

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
        }

        return fullData;
    }

    /**
     * Simulates a sample with the given sample size.
     *
     * @param sampleSize the sample size.
     * @return the simulated sample as a DataSet.
     */
    private DataSet simulateDataHelper(int sampleSize, boolean latentDataSaved, int[] tiers) {
        int numMeasured = 0;
        int[] map = new int[this.nodes.length];
        List<Node> variables = new LinkedList<>();

        for (int j = 0; j < this.nodes.length; j++) {

            int numCategories = this.bayesPm.getNumCategories(this.nodes[j]);
            List<String> categories = new LinkedList<>();

            for (int k = 0; k < numCategories; k++) {
                categories.add(this.bayesPm.getCategory(this.nodes[j], k));
            }

            DiscreteVariable var
                    = new DiscreteVariable(this.nodes[j].getName(), categories);
            var.setNodeType(this.nodes[j].getNodeType());
            variables.add(var);
            int index = ++numMeasured - 1;
            map[index] = j;
        }

        DataSet dataSet = new BoxDataSet(new VerticalIntDataBox(sampleSize, variables.size()), variables);
        constructSample(sampleSize, dataSet, map, tiers);

        if (!latentDataSaved) {
            dataSet = DataTransforms.restrictToMeasured(dataSet);
        }

        return dataSet;
    }

    /**
     * Constructs a random sample using the given already allocated data set, to avoid allocating more memory.
     */
    private DataSet simulateDataHelper(DataSet dataSet, boolean latentDataSaved, int[] tiers) {
        if (dataSet.getNumColumns() != this.nodes.length) {
            throw new IllegalArgumentException("When rewriting the old data set, "
                                               + "number of variables in data set must equal number of variables "
                                               + "in Bayes net.");
        }

        int sampleSize = dataSet.getNumRows();

        int numVars = 0;
        int[] map = new int[this.nodes.length];
        List<Node> variables = new LinkedList<>();

        for (int j = 0; j < this.nodes.length; j++) {

            int numCategories = this.bayesPm.getNumCategories(this.nodes[j]);
            List<String> categories = new LinkedList<>();

            for (int k = 0; k < numCategories; k++) {
                categories.add(this.bayesPm.getCategory(this.nodes[j], k));
            }

            DiscreteVariable var
                    = new DiscreteVariable(this.nodes[j].getName(), categories);
            var.setNodeType(this.nodes[j].getNodeType());
            variables.add(var);
            int index = ++numVars - 1;
            map[index] = j;
        }

        for (int i = 0; i < variables.size(); i++) {
            Node node = dataSet.getVariable(i);
            Node _node = variables.get(i);
            dataSet.changeVariable(node, _node);
        }

        constructSample(sampleSize, dataSet, map, tiers);

        if (latentDataSaved) {
            return dataSet;
        } else {
            return DataTransforms.restrictToMeasured(dataSet);
        }
    }

    private void constructSample(int sampleSize, DataSet dataSet, int[] map, int[] tiers) {

//        //Do the simulation.
//        class SimulationTask extends RecursiveTask<Boolean> {
//            private int chunk;
//            private int from;
//            private int to;
//            private int[] tiers;
//            private DataSet dataSet;
//            private int[] map;
//
//            public SimulationTask(int chunk, int from, int to, int[] tiers, DataSet dataSet, int[] map) {
//                this.chunk = chunk;
//                this.from = from;
//                this.to = to;
//                this.tiers = tiers;
//                this.dataSet = dataSet;
//                this.map = map;
//            }
//
//            @Override
//            protected Boolean compute() {
//                if (to - from <= chunk) {
//                    RandomGenerator randomGenerator = new Well1024a(++seed[0]);
//
//                    for (int row = from; row < to; row++) {
//                        for (int t : tiers) {
//                            int[] parentValues = new int[parents[t].length];
//
//                            for (int k = 0; k < parentValues.length; k++) {
//                                parentValues[k] = dataSet.getInt(row, parents[t][k]);
//                            }
//
//                            int rowIndex = getRowIndex(t, parentValues);
//                            double sum = 0.0;
//                            double r;
//
//                            r = randomGenerator.nextDouble();
//
//                            for (int k = 0; k < getNumColumns(t); k++) {
//                                double probability = getProbability(t, rowIndex, k);
//                                sum += probability;
//
//                                if (sum >= r) {
//                                    dataSet.setInt(row, map[t], k);
//                                    break;
//                                }
//                            }
//                        }
//                    }
//
//                    return true;
//                } else {
//                    int mid = (to + from) / 2;
//                    SimulationTask left = new SimulationTask(chunk, from, mid, tiers, dataSet, map);
//                    SimulationTask right = new SimulationTask(chunk, mid, to, tiers, dataSet, map);
//
//                    left.fork();
//                    right.compute();
//                    left.join();
//
//                    return true;
//                }
//            }
//        }
//
//        int chunk = 25;
//
//        ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();
//        SimulationTask task = new SimulationTask(chunk, 0, sampleSize, tiers, dataSet, map);
//        pool.invoke(task);
        // Construct the sample.
        for (int i = 0; i < sampleSize; i++) {
            for (int t : tiers) {
                int[] parentValues = new int[this.parents[t].length];

                for (int k = 0; k < parentValues.length; k++) {
                    parentValues[k] = dataSet.getInt(i, this.parents[t][k]);
                }

                int rowIndex = getRowIndex(t, parentValues);
                double sum = 0.0;

                double r = RandomUtil.getInstance().nextDouble();

                for (int k = 0; k < getNumColumns(t); k++) {
                    double probability = getProbability(t, rowIndex, k);
                    sum += probability;

                    if (sum >= r) {
                        dataSet.setInt(i, map[t], k);
                        break;
                    }
                }
            }
        }

//        System.out.println(dataSet);
    }

    /**
     * Determines whether the specified object is equal to this Bayes net.
     *
     * @param o the object to be compared to this Bayes net
     * @return true if the specified object is equal to this Bayes net, false otherwise
     */
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof BayesIm otherIm)) {
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
                    double prob = getProbability(i, j, k);
                    double otherProb = otherIm.getProbability(i, j, k);

                    if (Double.isNaN(prob) && Double.isNaN(otherProb)) {
                        continue;
                    }

                    if (abs(prob - otherProb) > MlBayesIm.ALLOWABLE_DIFFERENCE) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    //=============================PRIVATE METHODS=======================//

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

    /**
     * This method initializes the probability tables for all of the nodes in the Bayes net.
     *
     * @see #initializeNode
     * @see #randomizeRow
     */
    private void initialize(BayesIm oldBayesIm, int initializationMethod) {
        this.parents = new int[this.nodes.length][];
        this.parentDims = new int[this.nodes.length][];
        this.probs = new double[this.nodes.length][][];
        this.probMatrices = new CptMap[this.nodes.length];

        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {
            initializeNode(nodeIndex, oldBayesIm, initializationMethod);
        }
    }

    /**
     * This method initializes the node indicated.
     */
    private void initializeNode(int nodeIndex, BayesIm oldBayesIm,
                                int initializationMethod) {
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
//            if (numRows > 1000000 /* Integer.MAX_VALUE / dim*/) {
//                throw new IllegalArgumentException(
//                        "The number of rows in the "
//                        + "conditional probability table for "
//                        + this.nodes[nodeIndex]
//                        + " is greater than 1,000,000 and cannot be "
//                        + "represented.");
//            }

            numRows *= dim;
        }

        int numCols = getBayesPm().getNumCategories(node);

        this.parentDims[nodeIndex] = dims;
        this.probs[nodeIndex] = new double[numRows][numCols];
        this.probMatrices[nodeIndex] = new CptMap(numRows, numCols);

        // Initialize each row.
        if (initializationMethod == MlBayesIm.RANDOM) {
            randomizeTable(nodeIndex);
        } else {
            for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
                if (oldBayesIm == null) {
                    overwriteRow(nodeIndex, rowIndex, initializationMethod);
                } else {
                    retainOldRowIfPossible(nodeIndex, rowIndex, oldBayesIm,
                            initializationMethod);
                }
            }
        }
    }

    private void overwriteRow(int nodeIndex, int rowIndex,
                              int initializationMethod) {
        if (initializationMethod == MlBayesIm.RANDOM) {
            randomizeRow(nodeIndex, rowIndex);
        } else if (initializationMethod == MlBayesIm.MANUAL) {
            initializeRowAsUnknowns(nodeIndex, rowIndex);
        } else {
            throw new IllegalArgumentException("Unrecognized state.");
        }
    }

    private void initializeRowAsUnknowns(int nodeIndex, int rowIndex) {
        int size = getNumColumns(nodeIndex);
        double[] row = new double[size];
        Arrays.fill(row, Double.NaN);

        if (useProbMatrices) {
            probMatrices[nodeIndex].assignRow(rowIndex, new Vector(row));
        } else {
            this.probs[nodeIndex][rowIndex] = row;
        }
    }

    /**
     * This method initializes the node indicated.
     */
    private void retainOldRowIfPossible(int nodeIndex, int rowIndex,
                                        BayesIm oldBayesIm, int initializationMethod) {

        int oldNodeIndex = getCorrespondingNodeIndex(nodeIndex, oldBayesIm);

        if (oldNodeIndex == -1) {
            overwriteRow(nodeIndex, rowIndex, initializationMethod);
        } else if (getNumColumns(nodeIndex) != oldBayesIm.getNumColumns(oldNodeIndex)) {
            overwriteRow(nodeIndex, rowIndex, initializationMethod);
//        } else if (parentsChanged(nodeIndex, this, oldBayesIm)) {
//            overwriteRow(nodeIndex, rowIndex, initializationMethod);
        } else {
            int oldRowIndex = getUniqueCompatibleOldRow(nodeIndex, rowIndex, oldBayesIm);

            if (oldRowIndex >= 0) {
                copyValuesFromOldToNew(oldNodeIndex, oldRowIndex, nodeIndex,
                        rowIndex, oldBayesIm);
            } else {
                overwriteRow(nodeIndex, rowIndex, initializationMethod);
            }
        }
    }

    /**
     * @return the unique rowIndex in the old BayesIm for the given node that is compatible with the given rowIndex in
     * the new BayesIm for that node, if one exists. Otherwise, returns -1. A compatible rowIndex is one in which all
     * the parents that the given node has in common between the old BayesIm and the new BayesIm are assigned the values
     * they have in the new rowIndex. If a parent node is removed in the new BayesIm, there may be more than one such
     * compatible rowIndex in the old BayesIm, in which case -1 is returned. Likewise, there may be no compatible rows,
     * in which case -1 is returned.
     */
    private int getUniqueCompatibleOldRow(int nodeIndex, int rowIndex,
                                          BayesIm oldBayesIm) {
        int oldNodeIndex = getCorrespondingNodeIndex(nodeIndex, oldBayesIm);
        int oldNumParents = oldBayesIm.getNumParents(oldNodeIndex);

        int[] oldParentValues = new int[oldNumParents];
        Arrays.fill(oldParentValues, -1);

        int[] parentValues = getParentValues(nodeIndex, rowIndex);

        // Go through each parent of the node in the new BayesIm.
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
                return -1;
            }

            // Look up that value index for the new BayesIm for that parent.
            // If it was a valid value index in the old BayesIm, record
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
        // old BayesIm.
        return oldBayesIm.getRowIndex(oldNodeIndex, oldParentValues);
    }

    private void copyValuesFromOldToNew(int oldNodeIndex, int oldRowIndex,
                                        int nodeIndex, int rowIndex, BayesIm oldBayesIm) {
        if (getNumColumns(nodeIndex) != oldBayesIm.getNumColumns(oldNodeIndex)) {
            throw new IllegalArgumentException("It's only possible to copy "
                                               + "one row of probability values to another in a Bayes IM "
                                               + "if the number of columns in the table are the same.");
        }

        for (int colIndex = 0; colIndex < getNumColumns(nodeIndex); colIndex++) {
            double prob = oldBayesIm.getProbability(oldNodeIndex, oldRowIndex,
                    colIndex);
            setProbability(nodeIndex, rowIndex, colIndex, prob);
        }
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
     * @throws ClassNotFoundException If any. *
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

        copyDataToProbMatrices();
    }

    /**
     * Copies data from the `probs` array to the `probMatrices` array. If the lengths of both arrays are equal, the
     * `probMatrices` array is initialized with `ProbMap` objects, each containing the corresponding `probs` element.
     * The `probs` array is then set to null and the `useProbMatrices` flag is set to true.
     * <p>
     * Note: This method should only be called after the `probs` array has been properly initialized.
     */
    private void copyDataToProbMatrices() {
        if (!this.useProbMatrices && this.probs != null && this.probs.length == this.nodes.length) {
            this.probMatrices = new CptMap[this.probs.length];

            for (int i = 0; i < this.nodes.length; i++) {
                probMatrices[i] = new CptMap(this.probs[i]);
            }

            this.probs = null;
            this.useProbMatrices = true;
        }
    }
}
