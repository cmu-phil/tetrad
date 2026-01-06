/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * Calculates likelihood-based properties for discrete Bayes nets against data.
 * <p>
 * This implementation computes:
 * <ul>
 *   <li>Raw (unpenalized) log-likelihood of the BN: sum over local multinomials.</li>
 *   <li>Likelihood-ratio test (BN vs. saturated joint over the same variables).</li>
 *   <li>BIC under the convention BIC = 2*LL - k*log(N).</li>
 * </ul>
 * <p>
 * Notes:
 * <ul>
 *   <li>Degrees of freedom for the BN are \sum_i q_i (r_i - 1), where r_i is the cardinality of X_i
 *       and q_i is the product of parent cardinalities for X_i.</li>
 *   <li>The saturated model parameter count is (\prod_i r_i) - 1, over the graph's variables only.</li>
 * </ul>
 *
 * @author josephramsey (corrected implementation)
 */
public final class BayesProperties {

    /**
     * The data set.
     */
    private final DataSet dataSet;

    /**
     * The variables (same order as DataSet columns).
     */
    private final List<Node> variables;

    /**
     * Sample size.
     */
    private final int sampleSize;

    /**
     * Number of categories per variable (aligned with variables list).
     */
    private final int[] numCategories;

    /**
     * LR chi-square statistic.
     */
    private double chisq;

    /**
     * LR degrees of freedom.
     */
    private double dof;

    /**
     * BIC for the BN model (convention: 2*LL - k*log N).
     */
    private double bic;

    /**
     * BN log-likelihood (unpenalized).
     */
    private double likelihood;

    /**
     * Prevents parameterless construction.
     */
    private BayesProperties() {
        throw new UnsupportedOperationException();
    }

    /**
     * Constructs a new BayesProperties object for the given data set.
     *
     * @param dataSet The data set (discrete).
     */
    public BayesProperties(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }
        this.dataSet = dataSet;

        if (dataSet instanceof BoxDataSet) {
            DataBox dataBox = ((BoxDataSet) dataSet).getDataBox();
            this.variables = dataSet.getVariables();
            // Force materialization of int vectors to avoid boxing overhead later if needed
            VerticalIntDataBox box = new VerticalIntDataBox(dataBox);
            box.getVariableVectors();
        } else {
            this.variables = dataSet.getVariables();
        }

        this.sampleSize = dataSet.getNumRows();

        this.numCategories = new int[this.variables.size()];
        for (int i = 0; i < this.variables.size(); i++) {
            DiscreteVariable variable = getVariable(i);
            if (variable != null) {
                this.numCategories[i] = variable.getNumCategories();
            } else {
                this.numCategories[i] = 0;
            }
        }
    }

    /**
     * Likelihood-ratio p-value for BN vs. saturated joint over the same variables as {@code graph0}.
     *
     * @param graph0 The BN structure to evaluate.
     * @return p-value and related quantities.
     */
    public LikelihoodRet getLikelihoodRatioP(Graph graph0) {
        // BN model (smaller model)
        Ret rModel = getLikelihood(graph0);
        this.likelihood = rModel.lik(); // unpenalized BN LL

        // Saturated joint (larger model) over the SAME variables as graph0
        GraphScope scope = buildGraphScope(graph0);
        double llSat = saturatedJointLogLikelihood(scope);
        int paramsSat = saturatedParamCount(scope);

        // LR statistic: 2*(LL_larger - LL_smaller) >= 0
        double chisq = 2.0 * (llSat - rModel.lik());
        int df = paramsSat - rModel.dof();

        // Numerical & sanity guards
        if (chisq < 0 && Math.abs(chisq) < 1e-9) chisq = 0.0;
        if (chisq < 0) {
            throw new IllegalStateException("Negative LR statistic: check log-likelihood calculations.");
        }
        if (df < 0) {
            throw new IllegalStateException("Negative DOF: parameter counting mismatch.");
        }

        this.chisq = chisq;
        this.dof = df;

        // ... after computing chisq and df ...
        if (chisq < 0 && Math.abs(chisq) < 1e-9) chisq = 0.0;
        if (chisq < 0) throw new IllegalStateException("Negative LR statistic.");
        if (df < 0) throw new IllegalStateException("Negative DOF: parameter counting mismatch.");

        // BIC for BN model
        int N = this.dataSet.getNumRows();
        this.bic = 2 * rModel.lik() - rModel.dof() * FastMath.log(N);

        double p;
        if (df == 0) {
            // Degenerate chi-square at 0: P(Χ² >= x) is 1 if x==0, else 0
            p = (chisq <= 1e-12) ? 1.0 : 0.0;
        } else {
            p = StatUtils.getChiSquareP(df, chisq);
        }

        this.chisq = chisq;
        this.dof = df;

        LikelihoodRet _ret = new LikelihoodRet();
        _ret.p = p;
        _ret.bic = bic;
        _ret.chiSq = chisq;
        _ret.dof = df;
        return _ret;
    }

    /**
     * Retrieves the chi-squared (chisq) value for the current instance.
     * The chi-squared statistic is often used to measure the fit of a model
     * to observed data in statistics.
     *
     * @return the chi-squared value as a double
     */
    public double getChisq() {
        return this.chisq;
    }

    /**
     * Retrieves the degrees of freedom (dof) value for the current instance.
     *
     * @return the degrees of freedom as a double
     */
    public double getDof() {
        return this.dof;
    }

    /**
     * Retrieves the Bayesian Information Criterion (BIC) value for the current instance.
     *
     * @return the BIC value as a double
     */
    public double getBic() {
        return this.bic;
    }

    /**
     * Retrieves the likelihood value for the current instance.
     *
     * @return the likelihood value as a double
     */
    public double getLikelihood() {
        return this.likelihood;
    }

    /**
     * Retrieves the sample size for the current instance.
     *
     * @return the sample size as an integer
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Retrieves a variable from the list of variables that matches the specified target name.
     *
     * @param targetName the name of the variable to retrieve
     * @return the variable as a Node object if a match is found; otherwise, returns null
     */
    public Node getVariable(String targetName) {
        for (Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }
        return null;
    }

    // =========================
    // Core likelihood computing
    // =========================

    /**
     * Raw (unpenalized) BN log-likelihood and parameter count (k) for the given graph. Computes \ell = sum over nodes
     * of local multinomial log-likelihoods. Parameter count is \sum_i q_i (r_i - 1).
     */
    private Ret getLikelihood(Graph graph) {
        double ll = 0.0;
        int params = 0;

        for (Node node : graph.getNodes()) {
            List<Node> parents = new ArrayList<>(graph.getParents(node));

            // indices in DataSet/variables space (names are matched to align)
            int childIdx = this.variables.indexOf(getVariable(node.getName()));
            if (childIdx < 0) {
                throw new IllegalStateException("Child variable not found in DataSet: " + node.getName());
            }

            int[] parentIdx = new int[parents.size()];
            for (int j = 0; j < parents.size(); j++) {
                String pname = parents.get(j).getName();
                Node pnode = getVariable(pname);
                if (pnode == null) {
                    throw new IllegalStateException("Parent variable not found in DataSet: " + pname);
                }
                parentIdx[j] = this.variables.indexOf(pnode);
            }

            // Local raw log-likelihood
            double llNode = localLogLikelihood(childIdx, parentIdx);
            ll += llNode;

            // Parameter count: qi * (ri - 1)
            int ri = numCategories[childIdx];
            int qi = 1;
            for (int p : parentIdx) {
                int rpar = numCategories[p];
                if (rpar <= 0) {
                    throw new IllegalStateException("Non-positive cardinality for parent index " + p);
                }
                qi *= rpar;
            }
            params += qi * (ri - 1);
        }

        return new Ret(ll, params);
    }

    /**
     * Local multinomial log-likelihood for a node given parents: \sum_{rows r} \sum_{j} n_{rj} log(n_{rj}/n_r) (Skip
     * terms with n_{rj} = 0.)
     */
    private double localLogLikelihood(int childIdx, int[] parentIdx) {
        // Map: parent assignment -> counts over child states
        Map<ParentKey, int[]> table = new HashMap<>();
        int rChild = numCategories[childIdx];
        if (rChild <= 0) {
            throw new IllegalStateException("Non-positive cardinality for child index " + childIdx);
        }

        for (int row = 0; row < dataSet.getNumRows(); row++) {
            int[] keyVals = new int[parentIdx.length];
            for (int k = 0; k < parentIdx.length; k++) {
                keyVals[k] = dataSet.getInt(row, parentIdx[k]);
            }
            int y = dataSet.getInt(row, childIdx);
            ParentKey key = new ParentKey(keyVals);
            int[] counts = table.computeIfAbsent(key, _k -> new int[rChild]);
            counts[y] += 1;
        }

        double ll = 0.0;
        for (int[] counts : table.values()) {
            int nr = 0;
            for (int c : counts) nr += c;
            if (nr == 0) continue;
            for (int c : counts) {
                if (c > 0) {
                    ll += c * Math.log((double) c / nr);
                }
            }
        }
        return ll;
    }

    // =========================
    // Saturated model helpers
    // =========================

    /**
     * Build scope: map each graph node to its dataset column and cardinality.
     */
    private GraphScope buildGraphScope(Graph graph) {
        List<Node> gvars = graph.getNodes();
        int m = gvars.size();
        int[] dsCols = new int[m];
        int[] card = new int[m];

        for (int i = 0; i < m; i++) {
            Node g = gvars.get(i);

            // find dataset column with the same name
            int dsCol = -1;
            for (int c = 0; c < dataSet.getNumColumns(); c++) {
                if (dataSet.getVariable(c).getName().equals(g.getName())) {
                    dsCol = c;
                    break;
                }
            }
            if (dsCol < 0) {
                throw new IllegalStateException("DataSet is missing variable from graph: " + g.getName());
            }
            dsCols[i] = dsCol;

            // cardinality from the dataset's variable (assumed DiscreteVariable)
            DiscreteVariable dv = (DiscreteVariable) dataSet.getVariable(dsCol);
            card[i] = dv.getNumCategories();
            if (card[i] <= 0) {
                throw new IllegalStateException("Non-positive cardinality for variable: " + g.getName());
            }
        }
        return new GraphScope(dsCols, card);
    }

    /**
     * Computes the saturated joint log-likelihood for a given graph scope. The saturated joint log-likelihood
     * measures the total log-probability of all unique joint assignments of the variables defined by the provided
     * graph scope over the dataset.
     *
     * @param scope The graph scope defining the columns of the dataset (dsCols) and their respective cardinalities
     *              (card) to be considered for the computation.
     * @return The saturated joint log-likelihood value.
     */
    private double saturatedJointLogLikelihood(GraphScope scope) {
        // Count unique joint assignments over scope.dsCols
        Map<RowKey, Integer> counts = new HashMap<>();
        for (int r = 0; r < dataSet.getNumRows(); r++) {
            int[] vals = new int[scope.dsCols.length];
            for (int i = 0; i < scope.dsCols.length; i++) {
                vals[i] = dataSet.getInt(r, scope.dsCols[i]);
            }
            RowKey key = new RowKey(vals);
            counts.merge(key, 1, Integer::sum);
        }
        int N = dataSet.getNumRows();
        double ll = 0.0;
        for (int n : counts.values()) {
            if (n > 0) ll += n * Math.log((double) n / N);
        }
        return ll;
    }

    /**
     * Computes the saturated parameter count for the given graph scope. The method calculates the
     * maximum number of parameters that can be estimated for the given graph structure, ensuring
     * that the result does not exceed the maximum value of an integer.
     *
     * @param scope The graph scope containing the cardinality of the variables for the graph.
     *              This includes the dataset columns and their respective cardinalities.
     * @return The saturated parameter count. If the calculated value exceeds {@code Integer.MAX_VALUE},
     *         it returns {@code Integer.MAX_VALUE}.
     */
    private int saturatedParamCount(GraphScope scope) {
        long prod = 1L;
        for (int r : scope.card) prod *= r;
        long sat = prod - 1L;
        return sat > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sat;
    }

    /**
     * Retrieves the variable at the specified index from the list of variables
     * if it is an instance of DiscreteVariable.
     *
     * @param i the index of the variable in the variables list
     * @return the variable at the specified index as a DiscreteVariable if it exists
     *         and is of type DiscreteVariable; otherwise, returns null
     */
    private DiscreteVariable getVariable(int i) {
        if (this.variables.get(i) instanceof DiscreteVariable) {
            return (DiscreteVariable) this.variables.get(i);
        } else {
            return null;
        }
    }

    // =========================
    // Small helper records/classes
    // =========================

    /**
     * Scope of variables for the given graph within the current DataSet.
     */
    private static final class GraphScope {
        final int[] dsCols;  // DataSet column indices for graph vars, in graph's node iteration order
        final int[] card;    // cardinalities for those vars, same order

        /**
         * Constructs a GraphScope that defines the scope of variables for the given graph
         * within the current DataSet.
         *
         * @param dsCols an array of integers representing the DataSet column indices for graph variables, in the graph's node iteration order.
         * @param card an array of integers representing the cardinalities for the graph variables, in the same order as the column indices.
         */
        GraphScope(int[] dsCols, int[] card) {
            this.dsCols = dsCols;
            this.card = card;
        }
    }

    /**
     * Parent assignment key for local CPT counting.
     */
    private static final class ParentKey {
        final int[] vals;

        /**
         * Constructs a ParentKey instance with the given array of values.
         *
         * @param v   the array of values to be stored in this ParentKey
         */
        ParentKey(int[] v) {
            this.vals = v;
        }

        /**
         * Compares this object to the specified object for equality. The objects are considered equal if they are of
         * the same type and their internal arrays of values are equal.
         *
         * @param o the object to compare with this instance for equality
         * @return true if the specified object is equal to this instance; false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParentKey k)) return false;
            return Arrays.equals(vals, k.vals);
        }

        /**
         * Computes the hash code for this object based on its internal array of values.
         *
         * @return an integer hash code derived from the contents of the vals array
         */
        @Override
        public int hashCode() {
            return Arrays.hashCode(vals);
        }
    }

    /**
     * Hashable key for joint assignments (saturated counts).
     */
    private static final class RowKey {
        final int[] vals;

        /**
         * Constructs a RowKey instance with the given array of values.
         *
         * @param v   the array of values to be stored in this RowKey
         */
        RowKey(int[] v) {
            this.vals = v;
        }

        /**
         * Returns true if the given object is equal to this RowKey instance.
         *
         * @param o   the reference object with which to compare.
         * @return    true if the objects are equal, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RowKey k)) return false;
            return Arrays.equals(vals, k.vals);
        }

        /**
         * Returns the hash code for this object based on its internal array of values.
         *
         * @return an integer hash code derived from the contents of the vals array
         */
        @Override
        public int hashCode() {
            return Arrays.hashCode(vals);
        }
    }

    /**
     * Represents a record encapsulating the log-likelihood and degrees of freedom.
     * This class is used internally to manage and retrieve values related to
     * Bayesian Network likelihood computations.
     */
    private record Ret(double lik, int dof) {

        /**
         * Returns the log-likelihood value associated with this instance.
         *
         * @return the log-likelihood as a double.
         */
        @Override
        public double lik() {
            return this.lik;
        }

        /**
         * Returns the degrees of freedom associated with this instance.
         *
         * @return the degrees of freedom as an integer.
         */
        @Override
        public int dof() {
            return this.dof;
        }
    }

    /**
     * Result container for LR test.
     */
    public static class LikelihoodRet {

        /**
         * Represents the p-value from a statistical test, indicating the probability
         * of obtaining a result at least as extreme as the one observed, assuming
         * the null hypothesis is true. A smaller p-value suggests stronger evidence
         * against the null hypothesis.
         */
        public double p;

        /**
         * Represents the Bayesian Information Criterion (BIC), a model selection
         * criterion used in statistical analysis. BIC evaluates the goodness of
         * fit of a statistical model while introducing a penalty term for the number
         * of parameters in the model to prevent overfitting. A lower BIC value
         * indicates a better model, balancing complexity and fit.
         */
        public double bic;

        /**
         * Represents the chi-squared statistic value resulting from a statistical test.
         * It quantifies the disparity between observed and expected frequencies under
         * the null hypothesis. Higher values indicate a greater discrepancy, which may
         * signify a weaker fit of the model to the data or suggest that the null
         * hypothesis may not be valid.
         */
        public double chiSq;

        /**
         * Represents the degrees of freedom (dof) used in statistical tests or model
         * evaluations. Degrees of freedom refer to the number of independent values or
         * quantities that can vary in the analysis without violating any constraints.
         * It is often used to determine critical values or p-values in hypothesis testing.
         */
        public double dof;

        /**
         * Default constructor for the LikelihoodRet class.
         * Initializes an instance of the LikelihoodRet object, which serves
         * as a container for results of a likelihood ratio test statistical analysis.
         */
        public LikelihoodRet() {
        }
    }
}