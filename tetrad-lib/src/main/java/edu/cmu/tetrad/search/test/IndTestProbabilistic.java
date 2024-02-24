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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.bayesian.constraint.inference.BCInference;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uses BCInference by Cooper and Bui to calculate probabilistic conditional independence judgments.
 *
 * @author josephramsey 3/2014
 * @version $Id: $Id
 */
public class IndTestProbabilistic implements IndependenceTest {

    /**
     * A cache of results for independence facts, used only if threshold is false.
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The data set for which conditional  independence judgments are requested.
     */
    private final DataSet data;
    /**
     * The nodes of the data set.
     */
    private final List<Node> nodes;
    /**
     * Indices of the nodes.
     */
    private final Map<Node, Integer> indices;
    /**
     * A map from independence facts to their probabilities of independence.
     */
    private final Map<IndependenceFact, Double> H;
    /**
     * The BCInference object.
     */
    private final BCInference bci;
    /**
     * True if the independence test should be thresholded, false if it should be randomized.
     */
    private boolean threshold;
    /**
     * The posterior probability of the last independence test.
     */
    private double posterior;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;
    /**
     * The cutoff for the independence test.
     */
    private double cutoff = 0.5;
    /**
     * The prior equivalent sample size.
     */
    private double priorEquivalentSampleSize = 10;

    /**
     * Initializes the test using a discrete data sets.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public IndTestProbabilistic(DataSet dataSet) {
        if (!dataSet.isDiscrete()) {
            throw new IllegalArgumentException("Not a discrete data set.");

        }

        this.nodes = dataSet.getVariables();

        this.indices = new HashMap<>();

        for (int i = 0; i < this.nodes.size(); i++) {
            this.indices.put(this.nodes.get(i), i);
        }

        this.data = dataSet;
        this.H = new HashMap<>();

        int[] _cols = new int[this.nodes.size()];
        for (int i = 0; i < _cols.length; i++) _cols[i] = this.indices.get(this.nodes.get(i));

        int[] _rows = new int[dataSet.getNumRows()];
        for (int i = 0; i < dataSet.getNumRows(); i++) _rows[i] = i;

        DataSet _data = this.data.subsetRowsColumns(_rows, _cols);

        List<Node> nodes = _data.getVariables();

        for (int i = 0; i < nodes.size(); i++) {
            this.indices.put(nodes.get(i), i);
        }

        this.bci = setup(_data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns an independence result that states whether x _||_y | z and what the p-value of the test is.
     *
     * @see IndependenceResult
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        if (!threshold && facts.containsKey(new IndependenceFact(x, y, _z))) {
            return facts.get(new IndependenceFact(x, y, _z));
        }

        // Notice that we do not cache the results of the independence tests here. This is because
        // these results have a random component and so caching them would be inappropriate.
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        Node[] nodes = new Node[z.size()];
        for (int i = 0; i < z.size(); i++) nodes[i] = z.get(i);
        IndependenceResult independenceResult = checkIndependence(x, y, nodes);
        if (!threshold) facts.put(new IndependenceFact(x, y, _z), independenceResult);
        return independenceResult;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns an independence result that states whether x _||_y | z and what the p-value of the test is.
     *
     * @see IndependenceResult
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Node... z) {
        IndependenceFact key = new IndependenceFact(x, y, z);

        List<Node> allVars = new ArrayList<>();
        allVars.add(x);
        allVars.add(y);
        Collections.addAll(allVars, z);

        List<Integer> rows = getRows(this.data, allVars, this.indices);
        if (rows.isEmpty())
            return new IndependenceResult(new IndependenceFact(x, y, GraphUtils.asSet(z)),
                    true, Double.NaN, Double.NaN);

        BCInference bci;
        Map<Node, Integer> indices;

        if (rows.size() == this.data.getNumRows()) {
            bci = this.bci;
            indices = this.indices;
        } else {

            int[] _cols = new int[allVars.size()];
            for (int i = 0; i < _cols.length; i++) _cols[i] = this.indices.get(allVars.get(i));

            int[] _rows = new int[rows.size()];
            for (int i = 0; i < rows.size(); i++) _rows[i] = rows.get(i);

            DataSet _data = this.data.subsetRowsColumns(_rows, _cols);

            List<Node> nodes = _data.getVariables();

            indices = new HashMap<>();

            for (int i = 0; i < nodes.size(); i++) {
                indices.put(nodes.get(i), i);
            }

            bci = setup(_data);
        }

        double pInd;

        if (!this.H.containsKey(key)) {
            pInd = probConstraint(bci, BCInference.OP.independent, x, y, z, indices);
            H.put(key, pInd);
        } else {
            pInd = H.get(key);
        }

        double p = pInd;

        if (Double.isNaN(p)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, GraphUtils.asSet(z)));
        }

        posterior = p;

        boolean ind;

        if (threshold) {
            ind = (p >= cutoff);
        } else {
            ind = RandomUtil.getInstance().nextDouble() < p;
        }

        if (this.verbose) {
            if (ind) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, GraphUtils.asSet(z), p));
            }
        }

        // Note p here is not a p-value but rather a posterior probability.
        return new IndependenceResult(new IndependenceFact(x, y, z), ind, p, Double.NaN);
    }


    /**
     * Returns the probability of the constraint x op y | z.
     *
     * @param bci     The BCInference object.
     * @param op      The operator.
     * @param x       The first variable.
     * @param y       The second variable.
     * @param z       The conditioning set.
     * @param indices A map from nodes to their indices.
     * @return The probability.
     */
    public double probConstraint(BCInference bci, BCInference.OP op, Node x, Node y, Node[] z, Map<Node, Integer> indices) {

        int _x = indices.get(x) + 1;
        int _y = indices.get(y) + 1;

        int[] _z = new int[z.length + 1];
        _z[0] = z.length;
        for (int i = 0; i < z.length; i++) {
            _z[i + 1] = indices.get(z[i]) + 1;
        }

        return bci.probConstraint(op, _x, _y, _z);
    }

    /**
     * Returns the list of variables used in this object.
     *
     * @return A List of Node objects representing the variables used.
     */
    @Override
    public List<Node> getVariables() {
        return this.nodes;
    }

    /**
     * Retrieves the Node object that matches the given name from the list of nodes.
     *
     * @param name The name of the variable to retrieve.
     * @return The Node object matching the given name, or null if no match is found.
     */
    @Override
    public Node getVariable(String name) {
        for (Node node : this.nodes) {
            if (name.equals(node.getName())) return node;
        }

        return null;
    }

    /**
     * Determines whether a given set of nodes, z, determines another node, y.
     *
     * @param z A Set of nodes representing the conditioning set.
     * @param y The node for which determination is checked.
     * @return true if z determines y, false otherwise.
     */
    @Override
    public boolean determines(Set<Node> z, Node y) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the alpha parameter for the probabilistic test.
     *
     * @return The alpha parameter.
     */
    @Override
    public double getAlpha() {
        throw new UnsupportedOperationException("The Probabiistic Test doesn't use an alpha parameter");
    }

    /**
     * Sets the alpha parameter for the probabilistic test.
     *
     * @param alpha The alpha parameter to set.
     */
    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the data model associated with this instance.
     *
     * @return The data model.
     */
    @Override
    public DataModel getData() {
        return this.data;
    }

    /**
     * Returns a map from independence facts to their probabilities of independence.
     *
     * @return The map.
     */
    public Map<IndependenceFact, Double> getH() {
        return new HashMap<>(this.H);
    }

    /**
     * Returns the posterior probability of the last independence test.
     *
     * @return The posterior probability.
     */
    public double getPosterior() {
        return this.posterior;
    }

    /**
     * Returns the verbose flag indicating whether verbose output should be printed.
     *
     * @return The verbose flag.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose flag indicating whether verbose output should be printed.
     *
     * @param verbose true if verbose output should be printed, false otherwise.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether the independence test should be thresholded (true) or randomized (false).
     *
     * @param threshold true if the independence test should be thresholded, false if it should be randomized.
     */
    public void setThreshold(boolean threshold) {
        this.threshold = threshold;
    }

    /**
     * Sets the cutoff for the independence test.
     *
     * @param cutoff the cutoff for the independence test.
     */
    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    /**
     * Sets the prior equivalent sample size for the independence test. The prior equivalent sample size is a parameter
     * used in the calculation of the test statistic. A higher sample size will make the test more conservative, while a
     * lower sample size will make the test more liberal.
     *
     * @param priorEquivalentSampleSize the prior equivalent sample size to set
     */
    public void setPriorEquivalentSampleSize(double priorEquivalentSampleSize) {
        this.priorEquivalentSampleSize = priorEquivalentSampleSize;
    }

    /**
     * Sets up and initializes a BCInference object using the given DataSet.
     *
     * @param dataSet the DataSet object to be used for setting up the BCInference object
     * @return the initialized BCInference object
     */
    private BCInference setup(DataSet dataSet) {
        int[] nodeDimensions = new int[dataSet.getNumColumns() + 2];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            DiscreteVariable variable = (DiscreteVariable) (dataSet.getVariable(j));
            int numCategories = variable.getNumCategories();
            nodeDimensions[j + 1] = numCategories;
        }

        int[][] cases = new int[dataSet.getNumRows() + 1][dataSet.getNumColumns() + 2];

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                cases[i + 1][j + 1] = dataSet.getInt(i, j) + 1;
            }
        }

        BCInference bci = new BCInference(cases, nodeDimensions);
        bci.setPriorEqivalentSampleSize(this.priorEquivalentSampleSize);
        return bci;
    }

    /**
     * Retrieves the list of row indices where all variables in `allVars` have non-null values in `dataSet`. If any
     * variable has a null value for a particular row, the row is skipped.
     *
     * @param dataSet   The DataSet object containing the data.
     * @param allVars   The list of variables to check for non-null values.
     * @param nodesHash A mapping of variables to their corresponding indices in the DataSet.
     * @return A List of Integer objects representing the row indices where all variables have non-null values.
     */
    private List<Integer> getRows(DataSet dataSet, List<Node> allVars, Map<Node, Integer> nodesHash) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < dataSet.getNumRows(); k++) {
            for (Node node : allVars) {
                if (dataSet.getInt(k, nodesHash.get(node)) == -99) continue K;
            }

            rows.add(k);
        }

        return rows;
    }
}



