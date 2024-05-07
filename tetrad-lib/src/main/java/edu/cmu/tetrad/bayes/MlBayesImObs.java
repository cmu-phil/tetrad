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
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
 * node itself. Two methods allow these values to be set and retrieved: <ul> <li>getWordRatio(int nodeIndex, int
 * rowIndex, int colIndex); and, <li>setProbability(int nodeIndex, int rowIndex, int colIndex, int probability). </ul>
 * To determine the index of the node in question, use the method <ul> <li> getNodeIndex(Node node). </ul> To determine
 * the index of the row in question, use the method
 * <ul> <li>getRowIndex(int[] parentVals). </ul> To determine the order of the
 * parent values for a given node so that you can build the parentVals[] array,
 * use the method <ul> <li> getParents(int nodeIndex) </ul> To determine the
 * index of a category, use the method <ul> <li> getCategoryIndex(Node node)
 * </ul> in BayesPm. The rest of the methods in this class are easily understood
 * as variants of the methods above.
 * <p>
 * Thanks to Pucktada Treeratpituk, Frank Wimberly, and Willie Wheeler for
 * advise and earlier versions.&gt; 0
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class MlBayesImObs implements BayesIm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Tolerance.
     */
    private static final double ALLOWABLE_DIFFERENCE = 1.0e-10;

    /**
     * Inidicates that new rows in this BayesIm should be initialized as unknowns, forcing them to be specified
     * manually. This is the default.
     */
    private static final int MANUAL = 0;

    /**
     * Indicates that new rows in this BayesIm should be initialized randomly.
     */
    private static final int RANDOM = 1;

    /**
     * The associated Bayes PM model.
     */
    private final BayesPm bayesPm;

    /**
     * The array of nodes from the graph. Order is important.
     */
    private final Node[] nodes;

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
    private double[][][] probs;  // this is left in for compatibility

    /**
     * joint probability table
     */
    private StoredCellProbsObs jpd;

    /**
     * a regular MlBayesIm used to create randomized CPDs so that the values can be marginalized to a consistent
     * observed jpd
     */
    private BayesIm bayesImRandomize;

    /**
     * BayesIm containing only the observed variables.  Only used to 1) construct propositions (mapped from the original
     * allowUnfaithfulness bayesIm) in Identifiability 2) to avoid summing over rows in jpd when only the latent
     * variables have changed values (only sum when all the latent variables have value 0) This is a MlBayesIm instead
     * of a MlBayesImObs because otherwise there will be an infinite loop attempting to creating the MlBayesImObs
     */
    private BayesIm bayesImObs;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new BayesIm from the given BayesPm, initializing all values as Double.NaN ("?").
     *
     * @param bayesPm the given Bayes PM. Carries with it the underlying graph model.
     * @throws java.lang.IllegalArgumentException if the array of nodes provided is not a permutation of the nodes
     *                                            contained in the bayes parametric model provided.
     */
    public MlBayesImObs(BayesPm bayesPm) throws IllegalArgumentException {
        //this(bayesPm, null, MANUAL);
        this(bayesPm, MlBayesImObs.MANUAL);
    }

    /**
     * Constructs a new BayesIm from the given BayesPm, initializing values either as MANUAL or RANDOM. If initialized
     * manually, all values will be set to Double.NaN ("?") in each row; if initialized randomly, all values will
     * distributed randomly in each row.
     *
     * @param bayesPm              the given Bayes PM. Carries with it the underlying graph model.
     * @param initializationMethod either MANUAL or RANDOM.
     * @throws java.lang.IllegalArgumentException if the array of nodes provided is not a permutation of the nodes
     *                                            contained in the bayes parametric model provided.
     */
    public MlBayesImObs(BayesPm bayesPm, int initializationMethod)
            throws IllegalArgumentException {
        //this(bayesPm, null, initializationMethod);
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
        initialize(null, initializationMethod);

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
    public MlBayesImObs(BayesPm bayesPm, BayesIm oldBayesIm,
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

    /*
     * construct from a allowUnfaithfulness MlBayesIm using marginalized probaiblities,
     * or copy from another MlBayesImObs
     */

    /**
     * <p>Constructor for MlBayesImObs.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @throws java.lang.IllegalArgumentException if any.
     */
    public MlBayesImObs(BayesIm bayesIm) throws IllegalArgumentException {
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
        //initialize(bayesIm, MlBayesIm.MANUAL);
        initialize(bayesIm, MlBayesImObs.MANUAL);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.bayes.MlBayesImObs} object
     */
    public static MlBayesImObs serializableInstance() {
        return new MlBayesImObs(BayesPm.serializableInstance());
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

        double[] row = new double[size];
        double sum = 0.0;

        // If I put most of the mass in each row on one of the categories,
        // I get lovely classification results for Bayes nets with all
        // 4-category variables. To include a bias, set 'bias' to a positive
        // number.
        final double bias = 0;

        int randomCell = RandomUtil.getInstance().nextInt(size);

        for (int i = 0; i < size; i++) {
            row[i] = RandomUtil.getInstance().nextDouble();

            if (i == randomCell) {
                row[i] += bias;
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
     * {@inheritDoc}
     */
    public int getNumColumns(int nodeIndex) {
        return this.probs[nodeIndex][0].length;
    }

    /**
     * {@inheritDoc}
     */
    public int getNumRows(int nodeIndex) {
        return this.probs[nodeIndex].length;
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
    public int getParentValue(int nodeIndex, int rowIndex, int colIndex) {
        return getParentValues(nodeIndex, rowIndex)[colIndex];
    }

    /**
     * {@inheritDoc}
     */
    public double getProbability(int nodeIndex, int rowIndex, int colIndex) {
        return this.probs[nodeIndex][rowIndex][colIndex];
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
     * {@inheritDoc}
     * <p>
     * Sets the probability for the given node. The matrix row represent row index, the row in the table for this for
     * node which represents the combination of parent values in question. of the CPT. The matrix column represent
     * column index, the column in the table for this node which represents the value of the node in question.
     */
    @Override
    public void setProbability(int nodeIndex, double[][] probMatrix) {
        for (int i = 0; i < probMatrix.length; i++) {
            System.arraycopy(probMatrix[i], 0, this.probs[nodeIndex][i], 0, probMatrix[i].length);
        }
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
        if (colIndex >= getNumColumns(nodeIndex)) {
            throw new IllegalArgumentException("Column out of range: "
                                               + colIndex + " >= " + getNumColumns(nodeIndex));
        }

        if (!(0.0 <= value && value <= 1.0) && !Double.isNaN(value)) {
            throw new IllegalArgumentException("Probability value must be "
                                               + "between 0.0 and 1.0 or Double.NaN.");
        }

        this.probs[nodeIndex][rowIndex][colIndex] = value;
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
     * Assigns random probability values to the child values of this row that add to 1.
     */
    public void randomizeRow(int nodeIndex, int rowIndex) {
        int size = getNumColumns(nodeIndex);
        this.probs[nodeIndex][rowIndex] = MlBayesImObs.getRandomWeights(size);
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
     * Randomizes every row in the table for the given node index.
     */
    public void randomizeTable(int nodeIndex) {
        for (int rowIndex = 0; rowIndex < getNumRows(nodeIndex); rowIndex++) {
            randomizeRow(nodeIndex, rowIndex);
        }
        //        randomizeTable2(nodeIndex);
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
     * <p>
     * Simulates a sample with the given sample size.
     */
    public DataSet simulateData(int sampleSize, boolean latentDataSaved) {
        if (getBayesPm().getDag().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize);
        }

        return simulateDataHelper(sampleSize, latentDataSaved);
    }

    /**
     * {@inheritDoc}
     */
    public DataSet simulateData(DataSet dataSet, boolean latentDataSaved) {
        return simulateDataHelper(dataSet, latentDataSaved);
    }

//    /**
//     * Simulates a sample with the given sample size.
//     *
//     * @param sampleSize the sample size.
//     * @param seed       the random number generator seed allows you recreate the
//     *                   simulated data by passing in the same seed (so you don't have to store
//     *                   the sample data
//     * @return the simulated sample as a DataSet.
//     */
//    public DataSet simulateData(int sampleSize, long seed, boolean latentDataSaved) {
//        RandomUtil random = RandomUtil.getInstance();
//        random.setSeed(seed);
//        return simulateData(sampleSize, latentDataSaved);
//    }

//    public DataSet simulateData(DataSet dataSet, long seed, boolean latentDataSaved) {
//        RandomUtil random = RandomUtil.getInstance();
//        random.setSeed(seed);
//        return simulateDataHelper(dataSet, latentDataSaved);
//    }

    private DataSet simulateTimeSeries(int sampleSize) {
        TimeLagGraph timeSeriesGraph = getBayesPm().getDag().getTimeLagGraph();

        List<Node> variables = new ArrayList<>();

        for (Node node : timeSeriesGraph.getLag0Nodes()) {
            variables.add(new DiscreteVariable(timeSeriesGraph.getNodeId(node).getName()));
        }

        List<Node> lag0Nodes = timeSeriesGraph.getLag0Nodes();

        DataSet fullData = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

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
    private DataSet simulateDataHelper(int sampleSize, boolean latentDataSaved) {
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

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);
        constructSample(sampleSize, numMeasured, dataSet, map);
        return dataSet;
    }

    /**
     * Constructs a random sample using the given already allocated data set, to avoid allocating more memory.
     */
    private DataSet simulateDataHelper(DataSet dataSet, boolean latentDataSaved) {
        if (dataSet.getNumColumns() != this.nodes.length) {
            throw new IllegalArgumentException("When rewriting the old data set, "
                                               + "number of variables in data set must equal number of variables "
                                               + "in Bayes net.");
        }

        int sampleSize = dataSet.getNumRows();

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

        for (int i = 0; i < variables.size(); i++) {
            Node node = dataSet.getVariable(i);
            Node _node = variables.get(i);
            dataSet.changeVariable(node, _node);
        }

        constructSample(sampleSize, numMeasured, dataSet, map);
        return dataSet;
    }

    private void constructSample(int sampleSize,
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

            for (int j = 0; j < numMeasured; j++) {
                dataSet.setInt(i, j, point[map[j]]);
            }
        }
    }

    /**
     * {@inheritDoc}
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

                    if (FastMath.abs(prob - otherProb) > MlBayesImObs.ALLOWABLE_DIFFERENCE) {
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

        return "MlBayesImObs\n";
    }

    ///////////////////////////////////////////////////////
    // methods added for MlBayesImObs

    /**
     * <p>Getter for the field <code>bayesImObs</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getBayesImObs() {
        return this.bayesImObs;
    }

    /**
     * <p>getJPD.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.StoredCellProbsObs} object
     */
    public StoredCellProbsObs getJPD() {
        return this.jpd;
    }

    /**
     * <p>getNumRows.</p>
     *
     * @return a int
     */
    public int getNumRows() {
        return this.jpd.getNumRows();
    }

    // translate rowIndex into the variable values

    /**
     * <p>getRowValues.</p>
     *
     * @param rowIndex a int
     * @return an array of {@link int} objects
     */
    public int[] getRowValues(int rowIndex) {
        return this.jpd.getVariableValues(rowIndex);
    }

    /**
     * <p>getProbability.</p>
     *
     * @param rowIndex a int
     * @return a double
     */
    public double getProbability(int rowIndex) {
        return this.jpd.getCellProb(getRowValues(rowIndex));
    }

    /**
     * <p>setProbability.</p>
     *
     * @param rowIndex a int
     * @param value    a double
     */
    public void setProbability(int rowIndex, double value) {
        if (!(0.0 <= value && value <= 1.0) && !Double.isNaN(value)) {
            throw new IllegalArgumentException("Probability value must be "
                                               + "between 0.0 and 1.0 or Double.NaN.");
        }
        this.jpd.setCellProbability(getRowValues(rowIndex), value);
    }

    /**
     * <p>createRandomCellTable.</p>
     */
    public void createRandomCellTable() {
        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {

            this.bayesImRandomize.randomizeTable(nodeIndex);
        }
        this.jpd.createCellTable((MlBayesIm) this.bayesImRandomize);
    }

    //=============================PRIVATE METHODS=======================//
    ///////////////////////////////////////
    // initialization: the JPD and a BayesIm with only the observed variables
    // the data structure for the CPD are left in for compatibility
    private void initialize(BayesIm oldBayesIm, int initializationMethod) {
        this.parents = new int[this.nodes.length][];
        this.parentDims = new int[this.nodes.length][];
        this.probs = new double[this.nodes.length][][];

        // initialize parents, parentDims, probs, even if probs is not used
        for (int nodeIndex = 0; nodeIndex < this.nodes.length; nodeIndex++) {
            initializeNode(nodeIndex);
        }

        ///////////////////////////////////////////////////////////////////////
        // used for randomizing the jpd
        this.bayesImRandomize = new MlBayesIm(this.bayesPm);

        ///////////////////////////////////////////////////////////////////////
        // construct a BayesIm with only observed variables
        // This is used for making Proposition with only the observed variables
        Dag dag = new Dag(this.bayesPm.getDag());
        for (Node node : this.nodes) {
            if (node.getNodeType() == NodeType.LATENT) {
                dag.removeNode(node);
            }
        }
        BayesPm bayesPmObs = new BayesPm(dag, this.bayesPm);

        // not a MlBayesImObs to avoid an infinite loop of constructing
        // an MlBayesImObs inside an MlBayesImObs
        this.bayesImObs = new MlBayesIm(bayesPmObs);

        ///////////////////////////////////////////////////////////////////////
        // construct the jpd
        List<Node> obsNodes = new ArrayList<>();
        for (Node node1 : this.nodes) {
            Node node = this.bayesPm.getVariable(node1);
            if (node.getNodeType() == NodeType.MEASURED) {
                obsNodes.add(node);
            }
        }
        // this does not work: different ordering of nodes
        // graph is the DAG restricted to only observed variables
        //List<Node> obsNodes = bayesPmObs.getVariable();

        this.jpd = new StoredCellProbsObs(obsNodes);
        // this does not work: different ordering of nodes
        //jpd = new StoredCellProbsObs(getMeasuredNodes());

        ///////////////////////////////////////////////////////////////////////
        // initialize the jpd
        if (initializationMethod == MlBayesImObs.RANDOM) {
            // this does not work: assigning arbitrary random values to the jpd
            // will violate the constraints imposed by the graphical structure
            //jpd.createRandomCellTable();

            if (oldBayesIm == null) {
                createRandomCellTable();
            } else if (oldBayesIm.getClass().getSimpleName().equals("MlBayesIm")) {
                this.jpd.createCellTable((MlBayesIm) oldBayesIm);
            } else if (oldBayesIm.getClass().getSimpleName().equals("MlBayesImObs")) {
                if (this.bayesPm.equals(oldBayesIm.getBayesPm())) {
                    this.jpd.createCellTable((MlBayesImObs) oldBayesIm);
                } else {
                    createRandomCellTable();
                }
            }
        } else if (initializationMethod == MlBayesImObs.MANUAL) {
            if (oldBayesIm == null) {
                this.jpd.clearCellTable();
            } else if (oldBayesIm.getClass().getSimpleName().equals("MlBayesIm")) {
                this.jpd.createCellTable((MlBayesIm) oldBayesIm);
            } else if (oldBayesIm.getClass().getSimpleName().equals("MlBayesImObs")) {
                if (this.bayesPm.equals(oldBayesIm.getBayesPm())) {
                    this.jpd.createCellTable((MlBayesImObs) oldBayesIm);
                } else {
                    this.jpd.clearCellTable();
                }
            }

        } else {
            throw new IllegalArgumentException("Unrecognized state.");
        }

    }

    /**
     * This method initializes the node indicated.
     */
    private void initializeNode(int nodeIndex) {
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
            if (numRows > 1000000 /* Integer.MAX_VALUE / dim*/) {
                throw new IllegalArgumentException(
                        "The number of rows in the "
                        + "conditional probability table for "
                        + this.nodes[nodeIndex]
                        + " is greater than 1,000,000 and cannot be "
                        + "represented.");
            }

            numRows *= dim;
        }

        int numCols = getBayesPm().getNumCategories(node);

        this.parentDims[nodeIndex] = dims;
        this.probs[nodeIndex] = new double[numRows][numCols];

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

        if (this.probs == null) {
            throw new NullPointerException();
        }
    }
}
