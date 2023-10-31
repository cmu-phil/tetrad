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
 * <p>Gives a way of interpreting a score as an independence test. The contract is that
 * the score returned will be negative for independence and positive for dependence; this simply reports these
 * differences.</p>
 *
 * @author josephramsey
 */
public class ScoreIndTest implements IndependenceTest {

    private final Score score;
    private final List<Node> variables;
    private final DataModel data;
    private double bump = Double.NaN;
    private boolean verbose;

    public ScoreIndTest(Score score) {
        this(score, null);
    }

    public ScoreIndTest(Score score, DataModel data) {
        if (score == null) throw new NullPointerException();
        this.score = score;
        this.variables = score.getVariables();
        this.data = data;
    }

    /**
     * @return an Independence test for a subset of the variables.
     */
    public ScoreIndTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether x _||_ y | z
     *
     * @return The independence result.
     * @throws RuntimeException if a matrix singularity is encountered.
     * @see IndependenceResult
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
        this.bump = v;

        int N = score.getSampleSize();

        // No.
//        double p = 5 * exp(-2 * v - 2 * log(N)) / ((N) * log(N));

        boolean independent = v <= 0;

        if (this.verbose) {
            if (independent) {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFact(x, y, z) + " score = " + nf.format(bump));
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
     * Returns the variable by the given name.
     *
     * @return This variable.
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
     * Returns true if y is determined the variable in z.
     *
     * @return True is so.
     */
    public boolean determines(List<Node> z, Node y) {
        return this.score.determines(z, y);
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This level.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return -1;
    }

    /**
     * Sets the significance level.
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
    }

    /**
     * @return The data model for the independence test.
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
     * @throws UnsupportedOperationException Not implemented.
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
     * Returns true if verbose ouput should be printed.
     *
     * @return True, if so.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a String representation of this test.
     *
     * @return This string.
     */
    @Override
    public String toString() {
        return this.score.toString() + " Interpreted as a Test";
    }


    private int[] varIndices(List<Node> z) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = this.variables.indexOf(z.get(i));
        }

        return indices;
    }
}



