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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Performs a test of conditional independence X _||_ Y | Z1...Zn where all searchVariables
 * are either continuous or discrete. This test is valid for both ordinal and non-ordinal
 * discrete searchVariables.
 * <p>
 * Assumes a conditional Gaussain model and uses a likelihood ratio test.
 *
 * @author josephramsey
 */
public class IndTestConditionalGaussianLrt implements IndependenceTest {
    private final DataSet data;
    private final Map<Node, Integer> nodesHash;
    private double alpha;

    // Likelihood function
    private final ConditionalGaussianLikelihood likelihood;
    private double pValue = Double.NaN;

    private boolean verbose;
    private int numCategoriesToDiscretize = 3;

    /**
     * Consstructor.
     *
     * @param data       The data to analyze.
     * @param alpha      The signifcance level.
     * @param discretize Whether discrete children of continuous parents should be discretized.
     */
    public IndTestConditionalGaussianLrt(DataSet data, double alpha, boolean discretize) {
        this.data = data;
        this.likelihood = new ConditionalGaussianLikelihood(data);
        this.likelihood.setDiscretize(discretize);
        this.nodesHash = new ConcurrentSkipListMap<>();

        List<Node> variables = data.getVariables();

        for (int i = 0; i < variables.size(); i++) {
            this.nodesHash.put(variables.get(i), i);
        }

        this.alpha = alpha;
    }

    /**
     * @return an Independence test for a subset of the searchVariables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return True if the given independence question is judged true, False if not.
     * The independence question is of the form x _||_ y | z, z = z1,...,zn, where
     * x, y, z1,...,zn are searchVariables in the list returned by getVariableNames().
     */
    public IndependenceResult checkIndependence(Node x, Node y, List<Node> z) {
        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);

        List<Node> allVars = new ArrayList<>(z);
        allVars.add(x);
        allVars.add(y);

        this.likelihood.setRows(getRows(allVars, this.nodesHash));

        int _x = this.nodesHash.get(x);
        int _y = this.nodesHash.get(y);

        int[] list0 = new int[z.size() + 1];
        int[] list2 = new int[z.size()];

        list0[0] = _x;

        for (int i = 0; i < z.size(); i++) {
            int _z = this.nodesHash.get(z.get(i));
            list0[i + 1] = _z;
            list2[i] = _z;
        }

        ConditionalGaussianLikelihood.Ret ret1 = likelihood.getLikelihood(_y, list0);
        ConditionalGaussianLikelihood.Ret ret2 = this.likelihood.getLikelihood(_y, list2);

        double lik0 = ret1.getLik() - ret2.getLik();
        double dof0 = ret1.getDof() - ret2.getDof();

        if (dof0 <= 0) return new IndependenceResult(new IndependenceFact(x, y, z), false, Double.NaN);
        if (this.alpha == 0) return new IndependenceResult(new IndependenceFact(x, y, z), false, Double.NaN);
        if (this.alpha == 1) return new IndependenceResult(new IndependenceFact(x, y, z), false, Double.NaN);
        if (lik0 == Double.POSITIVE_INFINITY)
            return new IndependenceResult(new IndependenceFact(x, y, z), false, Double.NaN);

        double pValue;

        if (Double.isNaN(lik0)) {
            pValue = Double.NaN;
        } else {
            pValue = 1.0 - new ChiSquaredDistribution(dof0).cumulativeProbability(2.0 * lik0);
        }

        this.pValue = pValue;

        boolean independent = this.pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        SearchLogUtils.independenceFactMsg(x, y, z, this.pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue);
    }

    /**
     * @return the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * @return the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.data.getVariables());
    }

    /**
     * @return true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * @return the significance level of the independence test.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the data.
     *
     * @return This.
     */
    public DataSet getData() {
        return this.data;
    }

    /**
     * Returns a number that is higher for stronger judgments of dependence
     * and negative for judgments of independence.
     *
     * @return This number.
     */
    @Override
    public double getScore() {
        return getAlpha() - getPValue();
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Conditional Gaussian LRT, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     *
     * @return This.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the nubmer of categories used to discretize variables.
     *
     * @param numCategoriesToDiscretize This number, by default 3.
     */
    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    private List<Integer> getRows(List<Node> allVars, Map<Node, Integer> nodesHash) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.data.getNumRows(); k++) {
            for (Node node : allVars) {
                if (node instanceof ContinuousVariable) {
                    if (Double.isNaN(this.data.getDouble(k, nodesHash.get(node)))) continue K;
                } else if (node instanceof DiscreteVariable) {
                    if (this.data.getInt(k, nodesHash.get(node)) == -99) continue K;
                }
            }

            rows.add(k);
        }
        return rows;
    }
}