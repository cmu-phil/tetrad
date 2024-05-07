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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Gives a way of interpreting a score as an independence test. The contract is that the score returned will be negative
 * for independence and positive for dependence; this simply reports these differences.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ScoreIndTest implements IndependenceTest {
    /**
     * Represents a Score object. Contains methods to calculate the score of a node given its parents, retrieve the
     * variables of the score, get the sample size of the data, and convert the score to a string representation.
     */
    private final Score score;
    /**
     * A private final List of Node objects representing variables.
     */
    private final List<Node> variables;
    /**
     * Interface implemented by classes, instantiations of which can serve as data models in Tetrad. Data models may be
     * named if desired; if provided, these names will be used for display purposes.
     * <p>
     * This interface is relatively free of methods, mainly because classes that can serve as data models in Tetrad are
     * diverse, including continuous and discrete data sets, covariance and correlation matrices, graphs, and lists of
     * other data models. So this is primarily a tagging interface.
     */
    private final DataModel data;
    /**
     * A boolean variable indicating whether verbose output should be printed.
     */
    private boolean verbose;

    /**
     * <p>Constructor for ScoreIndTest.</p>
     *
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     */
    public ScoreIndTest(Score score) {
        this(score, null);
    }

    /**
     * <p>Constructor for ScoreIndTest.</p>
     *
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     * @param data  a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public ScoreIndTest(Score score, DataModel data) {
        if (score == null) throw new NullPointerException();
        this.score = score;
        this.variables = score.getVariables();
        this.data = data;
    }

    /**
     * Tests the independence between variables in a given sublist.
     *
     * @param vars The sublist of variables to test independence.
     * @return The result of the independence test.
     */
    public ScoreIndTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks the independence between two nodes given a set of additional nodes.
     *
     * @param x The first node
     * @param y The second node
     * @param z The set of additional nodes
     * @return The result of the independence test
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        List<Node> z1 = new ArrayList<>(z);
        Collections.sort(z1);

        double v = this.score.localScoreDiff(this.variables.indexOf(x), this.variables.indexOf(y),
                varIndices(z1));

        if (Double.isNaN(v)) {
            throw new RuntimeException("Undefined score bump encountered when testing " +
                                       LogUtilsSearch.independenceFact(x, y, z));
        }

        int N = score.getSampleSize();

        boolean independent = v <= 0;

        if (this.verbose) {
            if (independent) {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFact(x, y, z) + " score = " + nf.format(v));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, v, v);
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Retrieves the Node object with the specified name.
     *
     * @param name The name of the Node object to retrieve.
     * @return The Node object with the specified name, or null if not found.
     */
    public Node getVariable(String name) {
        for (Node node : this.variables) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        return null;
    }

    /**
     * Determines the result of an independence test between a set of variables and a target variable.
     *
     * @param z The set of variables to test for independence.
     * @param y The target variable to test against.
     * @return The result of the independence test.
     */
    public boolean determines(List<Node> z, Node y) {
        return this.score.determines(z, y);
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This level.
     * @throws java.lang.UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return -1;
    }

    /**
     * Sets the significance level for the independence test.
     *
     * @param alpha The level of significance to be set.
     */
    public void setAlpha(double alpha) {
    }

    /**
     * Retrieves the data model associated with this object.
     *
     * @return The data model object.
     */
    public DataModel getData() {
        return this.data;
    }

    /**
     * Returns the covariance matrix.
     *
     * @return This matrix.
     */
    public ICovarianceMatrix getCov() {
        return ((SemBicScore) this.score).getCovariances();
    }

    /**
     * @throws UnsupportedOperationException since not implemented.
     */
    public List<DataSet> getDataSets() {
        throw new UnsupportedOperationException("Method not implemented");
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.score.getSampleSize();
    }

    /**
     * Returns the score object that this test wraps.
     *
     * @return This score object.
     * @see Score
     */
    public Score getWrappedScore() {
        return this.score;
    }

    /**
     * Returns a boolean indicating whether verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output is enabled or not.
     *
     * @param verbose true if verbose output is enabled, false otherwise
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of the object.
     *
     * @return A string representing the object.
     */
    @Override
    public String toString() {
        return this.score.toString() + " Interpreted as a Test";
    }

    /**
     * Returns the indices of the given list of nodes within the variables of this class.
     *
     * @param z The list of nodes whose indices are to be determined.
     * @return The indices of the nodes within the variables of this class.
     */
    private int[] varIndices(List<Node> z) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = this.variables.indexOf(z.get(i));
        }

        return indices;
    }
}



