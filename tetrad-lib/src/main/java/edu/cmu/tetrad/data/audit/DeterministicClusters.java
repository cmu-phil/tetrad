///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data.audit;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Matrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Fits and reports the linear equations behind deterministic (and near-deterministic) relations among the continuous
 * variables of a dataset. Used by the audit's determinism-removal flow to show, for each variable a user removes, the
 * fitted equation expressing it in terms of the retained variables, so that removed definitional variables (BMI from
 * height and weight, totals from parts, region aggregates) remain reconstructible and the report carries the relation
 * explicitly.
 * <p>
 * A variable is judged deterministic when the fraction of its variance left unexplained by a regression on the other
 * continuous variables falls below a threshold: residual variance / marginal variance &lt; threshold, a scale-free
 * criterion. Which member of a deterministic cluster is written on the left-hand side is a representation choice, not
 * a discovery -- every member is an exact function of the rest -- so callers presenting equations should invite the
 * user to identify the DERIVED (definitional) variable where one exists.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see DeterminismRemovalSuggester
 */
public final class DeterministicClusters {

    /**
     * The ridge used to keep support-submatrix solves finite; detection decisions are made on the threshold, not on
     * this constant.
     */
    private static final double SOLVE_RIDGE = 1e-10;

    private DeterministicClusters() {
    }

    /**
     * One fitted constraint: determined = sum(coefficients * support) + intercept.
     *
     * @param determined       The name of the variable written on the left-hand side (a representation choice).
     * @param support          The names of the variables in the (greedily minimized) determining set.
     * @param coefficients     Raw-scale regression coefficients, aligned with support.
     * @param intercept        Raw-scale intercept.
     * @param fractionResidual Residual variance of the determined variable given the support, as a fraction of its
     *                         marginal variance; near zero for an exact relation.
     */
    public record Constraint(String determined, List<String> support, double[] coefficients, double intercept,
                             double fractionResidual) {

        /**
         * Renders the constraint as a fitted equation, e.g. "X3 = 1.2000*X1 + 0.8000*X2 + 0.0000".
         *
         * @return The equation string.
         */
        public String equation() {
            NumberFormat nf = new DecimalFormat("0.0000");
            StringBuilder sb = new StringBuilder(determined).append(" =");
            for (int k = 0; k < support.size(); k++) {
                double b = coefficients[k];
                if (k == 0) {
                    sb.append(" ").append(nf.format(b));
                } else {
                    sb.append(b < 0 ? " - " : " + ").append(nf.format(Math.abs(b)));
                }
                sb.append("*").append(support.get(k));
            }
            if (support.isEmpty()) {
                sb.append(" ").append(nf.format(intercept));
            } else {
                sb.append(intercept < 0 ? " - " : " + ").append(nf.format(Math.abs(intercept)));
            }
            return sb.toString();
        }

        /**
         * The full cluster this constraint speaks about: the determined variable together with its support.
         *
         * @return The member names, determined first.
         */
        public List<String> cluster() {
            List<String> all = new ArrayList<>();
            all.add(determined);
            all.addAll(support);
            return all;
        }
    }

    /**
     * Finds deterministic constraints among the continuous variables of the dataset: while some continuous variable
     * has residual fraction below the threshold given the other active continuous variables, its support is pruned
     * greedily to a minimal set still meeting the threshold, the constraint is recorded, and the variable is removed
     * from the active set -- so multiple and overlapping constraints are each reported once. Discrete variables are
     * ignored.
     *
     * @param data      The dataset.
     * @param threshold The residual-fraction threshold; 1e-8 detects exact relations up to rounding.
     * @return The detected constraints, one per independent deterministic relation; empty if none.
     */
    public static List<Constraint> find(DataSet data, double threshold) {
        Cov cov = Cov.of(data);
        if (cov == null) return new ArrayList<>();

        List<Constraint> constraints = new ArrayList<>();
        List<Integer> active = new ArrayList<>();
        for (int k = 0; k < cov.p; k++) active.add(k);

        boolean found = true;
        while (found && active.size() >= 2) {
            found = false;
            for (int pos = 0; pos < active.size(); pos++) {
                int i = active.get(pos);
                List<Integer> support = new ArrayList<>(active);
                support.remove(pos);
                if (cov.residualFraction(i, support) < threshold) {
                    boolean pruned = true;
                    while (pruned) {
                        pruned = false;
                        for (int q = 0; q < support.size(); q++) {
                            List<Integer> smaller = new ArrayList<>(support);
                            smaller.remove(q);
                            if (cov.residualFraction(i, smaller) < threshold) {
                                support = smaller;
                                pruned = true;
                                break;
                            }
                        }
                    }
                    constraints.add(cov.constraint(i, support));
                    active.remove(pos);
                    found = true;
                    break;
                }
            }
        }

        return constraints;
    }

    /**
     * Fits the equation writing the given variable as a function of the other continuous variables of the dataset.
     * The support is pruned greedily: a variable is dropped when the residual fraction stays below
     * max(threshold, twice the full-support residual fraction), so an exact relation keeps only the variables needed
     * to stay exact, and a near-deterministic relation keeps only the variables that matter to the fit. The caller
     * should check fractionResidual against its own threshold to label the equation exact or approximate.
     *
     * @param data      The dataset.
     * @param varName   The name of the variable to write on the left-hand side.
     * @param threshold The exactness threshold used in the pruning rule.
     * @return The fitted constraint, or null if the variable is not a continuous variable of the dataset or fewer
     * than two continuous variables exist.
     */
    public static Constraint constraintFor(DataSet data, String varName, double threshold) {
        Cov cov = Cov.of(data);
        if (cov == null) return null;
        int target = cov.indexOf(varName);
        if (target < 0) return null;

        List<Integer> support = new ArrayList<>();
        for (int k = 0; k < cov.p; k++) if (k != target) support.add(k);

        double pruneThreshold = Math.max(threshold, 2.0 * cov.residualFraction(target, support));

        boolean pruned = true;
        while (pruned) {
            pruned = false;
            for (int q = 0; q < support.size(); q++) {
                List<Integer> smaller = new ArrayList<>(support);
                smaller.remove(q);
                if (cov.residualFraction(target, smaller) <= pruneThreshold) {
                    support = smaller;
                    pruned = true;
                    break;
                }
            }
        }

        return cov.constraint(target, support);
    }

    /**
     * The covariance matrix and means of the continuous variables of a dataset, with the regression arithmetic used
     * above.
     */
    private static final class Cov {
        final int p;
        private final DataSet data;
        private final List<Integer> cols;
        private final double[] means;
        private final Matrix cov;

        private Cov(DataSet data, List<Integer> cols, double[] means, Matrix cov) {
            this.data = data;
            this.cols = cols;
            this.means = means;
            this.cov = cov;
            this.p = cols.size();
        }

        static Cov of(DataSet data) {
            List<Integer> cols = new ArrayList<>();
            for (int j = 0; j < data.getNumColumns(); j++) {
                if (data.getVariable(j) instanceof ContinuousVariable) {
                    cols.add(j);
                }
            }
            int n = data.getNumRows();
            int p = cols.size();
            if (p < 2 || n < 2) return null;

            double[] means = new double[p];
            double[][] x = new double[n][p];
            for (int k = 0; k < p; k++) {
                int col = cols.get(k);
                double s = 0.0;
                for (int i = 0; i < n; i++) {
                    double v = data.getDouble(i, col);
                    x[i][k] = v;
                    s += v;
                }
                means[k] = s / n;
            }
            double[][] c = new double[p][p];
            for (int a = 0; a < p; a++) {
                for (int b = a; b < p; b++) {
                    double s = 0.0;
                    for (int i = 0; i < n; i++) {
                        s += (x[i][a] - means[a]) * (x[i][b] - means[b]);
                    }
                    c[a][b] = s / (n - 1);
                    c[b][a] = c[a][b];
                }
            }
            return new Cov(data, cols, means, new Matrix(c));
        }

        int indexOf(String name) {
            for (int k = 0; k < p; k++) {
                if (data.getVariable(cols.get(k)).getName().equals(name)) return k;
            }
            return -1;
        }

        double residualFraction(int i, List<Integer> support) {
            double vii = cov.get(i, i);
            if (vii <= 0) return 0.0;      // A constant column is determined by the empty set.
            if (support.isEmpty()) return 1.0;
            int[] s = support.stream().mapToInt(Integer::intValue).toArray();
            int[] ii = {i};
            Matrix css = cov.view(s, s). mat();
            Matrix csi = cov.view(s, ii).mat();
            Matrix b = css.chooseInverse(SOLVE_RIDGE).times(csi);
            double explained = csi.transpose().times(b).get(0, 0);
            return Math.max(vii - explained, 0.0) / vii;
        }

        Constraint constraint(int i, List<Integer> support) {
            String name = data.getVariable(cols.get(i)).getName();
            List<String> supportNames = new ArrayList<>();
            double[] coefs = new double[support.size()];
            double intercept = means[i];
            if (!support.isEmpty()) {
                int[] s = support.stream().mapToInt(Integer::intValue).toArray();
                int[] ii = {i};
                Matrix css = cov.view(s, s).mat();
                Matrix csi = cov.view(s, ii).mat();
                Matrix b = css.chooseInverse(SOLVE_RIDGE).times(csi);
                for (int k = 0; k < s.length; k++) {
                    supportNames.add(data.getVariable(cols.get(s[k])).getName());
                    coefs[k] = b.get(k, 0);
                    intercept -= coefs[k] * means[s[k]];
                }
            }
            return new Constraint(name, supportNames, coefs, intercept, residualFraction(i, support));
        }
    }
}
