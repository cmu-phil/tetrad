///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StratifiedRankTests;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A conditional independence test for data gathered in several domains (batches, sites, cohorts, countries) whose
 * causal structure is shared but whose linear coefficients, means, and noise variances may differ by domain -- the
 * Phase 1 test of LCD-NOD (Dong, Dai, Ng, Spirtes, and Zhang, ICML 2026). The hypothesis x _||_ y | Z is tested as
 * the vanishing of the partial cross-covariance of x and y given Z <i>within</i> each domain, pooling the Bartlett
 * statistic over domains through {@link StratifiedRankTests}; by Theorem 1 of that paper, the within-domain
 * covariance constraints are those of the homogeneous linear model with the same graph, while the covariance of the
 * domains mixed together is not to be trusted.
 * <p>
 * Construct the test either from one data set per domain (all with the same variables in the same order), or from a
 * single data set together with the name of a discrete column holding the domain index; that column is removed from
 * the variable list and used only to split the rows.
 * <p>
 * Any algorithm taking an {@link IndependenceTest} -- PC, CPC, FCI and its variants, the Markov checker -- gets a
 * domain-aware version by being handed this test. Each domain needs enough rows for its own covariance matrix;
 * domains too small to discriminate are skipped by the underlying statistic, and if none is usable the result is
 * marked invalid.
 *
 * @author josephramsey
 */
public class IndTestStratifiedRank implements IndependenceTest {

    /**
     * The engine computing pooled rank statistics over the strata.
     */
    private final StratifiedRankTests stratified;

    /**
     * The variables of the test, in the common column order of the strata.
     */
    private final List<Node> variables;

    /**
     * Map from variable to its column index.
     */
    private final Map<Node, Integer> nodeHash;

    /**
     * The per-domain data sets.
     */
    private final List<DataSet> dataSets;

    /**
     * The significance level.
     */
    private double alpha;

    /**
     * Whether verbose output is printed.
     */
    private boolean verbose = false;

    /**
     * Constructs the test from one data set per domain. The data sets must all have the same variables in the same
     * order.
     *
     * @param dataSets one continuous data set per domain.
     * @param alpha    the significance level.
     */
    public IndTestStratifiedRank(List<DataSet> dataSets, double alpha) {
        if (dataSets == null || dataSets.isEmpty()) throw new IllegalArgumentException("No data sets given.");
        this.dataSets = new ArrayList<>(dataSets);
        this.stratified = StratifiedRankTests.fromDataSets(this.dataSets);
        this.variables = new ArrayList<>(this.dataSets.get(0).getVariables());
        this.nodeHash = hash(this.variables);
        setAlpha(alpha);
    }

    /**
     * Constructs the test from a single data set containing a discrete domain column. The domain column is removed
     * from the variable list and used to split the rows into strata.
     *
     * @param dataSet          the data set, containing the domain column.
     * @param domainColumnName the name of the domain column.
     * @param alpha            the significance level.
     */
    public IndTestStratifiedRank(DataSet dataSet, String domainColumnName, double alpha) {
        if (dataSet == null) throw new IllegalArgumentException("Data set is null.");
        Node domain = dataSet.getVariable(domainColumnName);
        if (domain == null) {
            throw new IllegalArgumentException("No column named " + domainColumnName + " in the data set.");
        }
        this.dataSets = StratifiedRankTests.split(dataSet, domain);
        this.stratified = StratifiedRankTests.fromDataSets(this.dataSets);
        this.variables = new ArrayList<>(this.dataSets.get(0).getVariables());
        this.nodeHash = hash(this.variables);
        setAlpha(alpha);
    }

    private static Map<Node, Integer> hash(List<Node> variables) {
        Map<Node, Integer> hash = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            if (hash.put(variables.get(i), i) != null) {
                throw new IllegalArgumentException("Duplicate variable: " + variables.get(i).getName());
            }
        }
        return hash;
    }

    /**
     * Checks the independence of x and y given z, pooling over domains. Independent if the pooled test fails to
     * reject the vanishing, given z within each domain, of the cross-covariance of x and y.
     *
     * @param x The first variable.
     * @param y The second variable.
     * @param z The conditioning set.
     * @return the result, marked invalid if no domain was large enough to contribute.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        Integer xi = nodeHash.get(x);
        Integer yi = nodeHash.get(y);
        if (xi == null) throw new IllegalArgumentException("Not a variable of this test: " + x.getName());
        if (yi == null) throw new IllegalArgumentException("Not a variable of this test: " + y.getName());

        int[] zIdx = new int[z.size()];
        int k = 0;
        for (Node node : z) {
            Integer zi = nodeHash.get(node);
            if (zi == null) throw new IllegalArgumentException("Not a variable of this test: " + node.getName());
            zIdx[k++] = zi;
        }

        double pValue = stratified.pValueRankLeConditioned(new int[]{xi}, new int[]{yi}, zIdx, 0);

        IndependenceFact fact = new IndependenceFact(x, y, z);

        if (Double.isNaN(pValue)) {
            if (verbose) {
                TetradLogger.getInstance().log("No domain large enough for " + fact + "; result marked invalid.");
            }
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN, false);
        }

        boolean independent = pValue > alpha;

        if (verbose && independent) {
            TetradLogger.getInstance().log(fact + " INDEPENDENT, p = " + pValue + " (pooled over "
                                           + stratified.getNumStrata() + " domains)");
        }

        return new IndependenceResult(fact, independent, pValue, alpha - pValue);
    }

    /**
     * Returns the variables of the test: the common variables of the strata, with the domain column removed.
     *
     * @return the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Returns the first of the per-domain data sets, anchoring the test to a data model.
     *
     * @return the first per-domain data set.
     */
    @Override
    public DataModel getData() {
        return dataSets.get(0);
    }

    /**
     * Returns the per-domain data sets.
     *
     * @return the per-domain data sets.
     */
    @Override
    public List<DataSet> getDataSets() {
        return new ArrayList<>(dataSets);
    }

    /**
     * Returns the total sample size over all domains.
     *
     * @return the total sample size.
     */
    @Override
    public int getSampleSize() {
        return stratified.getTotalSampleSize();
    }

    /**
     * Returns the significance level.
     *
     * @return the significance level.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level.
     *
     * @param alpha the significance level, in (0, 1).
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0, 1): " + alpha);
        this.alpha = alpha;
    }

    /**
     * Returns whether verbose output is printed.
     *
     * @return true if verbose.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose true to print verbose output.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the engine computing the pooled statistics, for use by rank-based consumers over the same variable
     * order (FOFC, TSC).
     *
     * @return the stratified rank test engine.
     */
    public StratifiedRankTests getStratifiedRankTests() {
        return stratified;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return the string.
     */
    @Override
    public String toString() {
        return "Stratified Rank (Bartlett pooled over " + stratified.getNumStrata() + " domains), alpha = " + alpha;
    }
}
