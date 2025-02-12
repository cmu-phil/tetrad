/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Double.NaN;

/**
 * Implements degenerate Gaussian test as a likelihood ratio test. The reference is here:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2019, July). Learning high-dimensional directed acyclic graphs with
 * mixed data-types. In The 2019 ACM SIGKDD Workshop on Causal Discovery (pp. 4-21). PMLR.
 * <p>
 * This version does not yield uniform p-values under the null and has been replaced by a new version of
 * IndTestBasisFunctionLrt that uses generalized likelihood ratio tests and does.
 *
 * @author Bryan Andrews
 * @author Joseph Ramsey refactoring 2024-12-26
 * @version $Id: $Id
 * @see IndTestBasisFunctionLrt
 */
public class IndTestBasisFunctionLrt_Old implements IndependenceTest {
    /**
     * A hash of nodes to indices.
     */
    private final Map<Node, Integer> nodeHash;
    /**
     * The data set.
     */
    private final DataSet dataSet;
    /**
     * The mixed variables of the original dataset.
     */
    private final List<Node> variables;
    /**
     * The embedding map.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * The SEM BIC score, used to calculate local likelihoods.
     */
    private final SemBicScore bic;
    /**
     * The alpha level.
     */
    private double alpha = 0.01;
    /**
     * The p value.
     */
    private double pValue = NaN;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Constructs an instance of IndTestBasisFunctionLrt to perform independence testing. This method initializes the
     * independence test with the provided dataset, truncation limit, basis type, and basis scale. It processes the
     * dataset to expand discrete columns into appropriate representations and sets up necessary configurations for the
     * test.
     *
     * @param dataSet         The input dataset to be used for the independence test.
     * @param truncationLimit An integer representing the truncation limit for the basis functions.
     * @param basisType       An integer indicating the type of basis to be used in the analysis.
     * @param basisScale      A double value specifying the scale parameter for the basis functions.
     * @throws NullPointerException if the provided dataset is null.
     */
    public IndTestBasisFunctionLrt_Old(DataSet dataSet, int truncationLimit,
                                       int basisType, double basisScale) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodeHash = nodesHash;
        boolean usePseudoInverse = false;

        // Expand the discrete columns to give indicators for each category. We want to leave a category out if
        // we're not using the pseudoinverse option.
        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, truncationLimit, basisType, basisScale, usePseudoInverse);
        DataSet convertedData = embeddedData.embeddedData();
        this.embedding = embeddedData.embedding();
        this.bic = new SemBicScore(convertedData, false);
        this.bic.setUsePseudoInverse(usePseudoInverse);
        this.bic.setStructurePrior(0);
    }

    /**
     * Subsets the variables used in the independence test.
     *
     * @param vars The sublist of variables.
     * @return The IndependenceTest object with subset of variables.
     * @throws UnsupportedOperationException if the method is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Returns an independence result specifying whether x _||_ y | Z and what its p-values are.
     *
     * @param x  the first node.
     * @param y  the second node.
     * @param _z the conditioning set.
     * @return an independence result specifying whether x _||_ y | Z and what its p-values are.
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        int _x = this.nodeHash.get(x);
        int _y = this.nodeHash.get(y);

        int[] list0 = new int[z.size()];
        int[] list1 = new int[z.size() + 1];

        list1[0] = _x;

        for (int i = 0; i < z.size(); i++) {
            int __z = this.nodeHash.get(z.get(i));
            list0[i] = __z;
            list1[i + 1] = __z;
        }

        Ret ret0 = getlldof(_y, list0);
        Ret ret1 = getlldof(_y, list1);

        double lik_diff = ret0.lik() - ret1.lik();
        double dof_diff = ret1.dof() - ret0.dof();

        if (dof_diff <= 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (lik_diff == Double.POSITIVE_INFINITY) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);

        double pValue;

        if (Double.isNaN(lik_diff)) {
            throw new RuntimeException("Undefined likelihood encountered for test: " + LogUtilsSearch.independenceFact(x, y, _z));
        } else {
            try {
                pValue = StatUtils.getChiSquareP(dof_diff, -2 * lik_diff);
            } catch (Exception e) {
                TetradLogger.getInstance().log("Exception when trying to determine " + LogUtilsSearch.independenceFact(x, y, _z)
                                               + " with lik_diff = " + lik_diff + " and dof_diff = " + dof_diff
                                               + " (" + e.getMessage() + ")");
                throw new RuntimeException("Exception when trying to determine " + LogUtilsSearch.independenceFact(x, y, _z), e);
            }
        }

        this.pValue = pValue;

        boolean independent = this.pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z),
                independent, pValue, alpha - pValue);
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for this test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return this level, default 0.01.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1.");
        }
        this.alpha = alpha;
    }

    /**
     * Returns a copy of the dataset being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet.copy();
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Degenerate Gaussian, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Calculates the sample log likelihood
     */
    private Ret getlldof(int i, int... parents) {
        double score = 0;

        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        List<Integer> B = new ArrayList<>();
        for (int p : parents) {
            B.addAll(this.embedding.get(p));
        }

        int aLength = A.size();
        int bLength = B.size();

        double dof = (bLength * (bLength + 1) - aLength * (aLength + 1)) / 2.0;

        for (int a : A) {
            int[] parents_ = new int[B.size()];
            for (int b = 0; b < B.size(); b++) {
                parents_[b] = B.get(b);
            }

            double likelihood = this.bic.getLikelihood(a, parents_);

            if (Double.isNaN(likelihood)) {
                break;
            }

            score += likelihood;
//            B.add(a);
        }

        double lik = score;
        return new Ret(lik, dof);
    }

    /**
     * Represents a record that holds results for a log-likelihood ratio test.
     *
     * @param lik The log-likelihood ratio.
     * @param dof The degrees of freedom.
     */
    public record Ret(double lik, double dof) {
    }
}
