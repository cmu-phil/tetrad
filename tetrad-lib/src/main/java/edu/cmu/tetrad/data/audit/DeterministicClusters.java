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
import java.util.Collection;
import java.util.Comparator;
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
 * <p>
 * Fitting many equations on a wide dataset goes through {@link Fitter}, which computes the correlation matrix ONCE and
 * gets every leave-one-out regression at once from a single ridge-regularized precision matrix (the identity b_j =
 * -&Omega;_ij / &Omega;_ii, residual fraction 1 / (&Omega;_ii r_ii)), then screens each support down to the few
 * variables with non-negligible coefficients and refines exactly on that small set. One O(p^3) inversion serves all
 * variables; the per-variable work is a handful of small solves. The previous implementation recomputed the covariance
 * per call and pruned supports by greedy backward elimination (O(p^5) worst case per variable), which was unusable on
 * wide data with many deterministic relations.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see DeterminismRemovalSuggester
 */
public final class DeterministicClusters {

    /**
     * The ridge used to keep submatrix solves finite on degenerate correlation matrices; detection decisions are made
     * on the threshold, not on this constant.
     */
    private static final double SOLVE_RIDGE = 1e-10;

    /**
     * Screening keeps a candidate support variable when its standardized coefficient exceeds this fraction of the
     * largest standardized coefficient for the target . . . (relative floor).
     */
    private static final double SCREEN_RELATIVE = 1e-3;

    /**
     * . . . and this absolute floor.
     */
    private static final double SCREEN_ABSOLUTE = 1e-8;

    /**
     * The most support variables screening passes to the exact refinement stage. Definitional relations in practice
     * have small supports; the cap bounds the refinement cost on pathological inputs.
     */
    private static final int SCREEN_CAP = 25;

    private DeterministicClusters() {
    }

    /**
     * One fitted constraint: determined = sum(coefficients * support) + intercept.
     *
     * @param determined       The name of the variable written on the left-hand side (a representation choice).
     * @param support          The names of the variables in the (screened and pruned) determining set.
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
     * has residual fraction below the threshold given the other active continuous variables, its (screened) support
     * is pruned to a minimal set still meeting the threshold, the constraint is recorded, and the variable is removed
     * from the active set -- so multiple and overlapping constraints are each reported once. Discrete variables are
     * ignored.
     *
     * @param data      The dataset.
     * @param threshold The residual-fraction threshold; 1e-8 detects exact relations up to rounding.
     * @return The detected constraints, one per independent deterministic relation; empty if none.
     */
    public static List<Constraint> find(DataSet data, double threshold) {
        Fitter fitter = new Fitter(data, threshold);
        return fitter.findAll();
    }

    /**
     * Fits the equation writing the given variable as a function of the other continuous variables of the dataset.
     * The caller should check fractionResidual against its own threshold to label the equation exact or approximate.
     * For fitting MANY equations on one dataset, construct a {@link Fitter} once instead: this convenience method
     * rebuilds the shared matrices on every call.
     *
     * @param data      The dataset.
     * @param varName   The name of the variable to write on the left-hand side.
     * @param threshold The exactness threshold used in the pruning rule.
     * @return The fitted constraint, or null if the variable is not a continuous variable of the dataset or fewer
     * than two continuous variables exist.
     */
    public static Constraint constraintFor(DataSet data, String varName, double threshold) {
        return new Fitter(data, threshold).leaveOneOut(varName);
    }

    /**
     * A reusable equation fitter for one dataset: the correlation matrix and its ridge-regularized inverse are
     * computed once, and each equation costs a few small solves. See the class Javadoc for the method.
     */
    public static final class Fitter {

        private final double threshold;
        private final DataSet data;
        /**
         * Analyzed columns: continuous with positive variance. Constant continuous columns are excluded from every
         * support and reported, as targets, as determined by the empty set.
         */
        private final List<Integer> cols = new ArrayList<>();
        private final List<Integer> constants = new ArrayList<>();
        private double[] means;
        private double[] sds;
        /**
         * Correlation matrix of the analyzed columns.
         */
        private Matrix corr;
        /**
         * Ridge-regularized inverse of corr, computed lazily on the first leave-one-out fit.
         */
        private Matrix precision;

        /**
         * Constructs the fitter, computing means, standard deviations, and the correlation matrix once.
         *
         * @param data      The dataset.
         * @param threshold The exactness threshold; see {@link #find(DataSet, double)}.
         */
        public Fitter(DataSet data, double threshold) {
            this.data = data;
            this.threshold = threshold;

            List<Integer> continuous = new ArrayList<>();
            for (int j = 0; j < data.getNumColumns(); j++) {
                if (data.getVariable(j) instanceof ContinuousVariable) {
                    continuous.add(j);
                }
            }
            int n = data.getNumRows();
            int pAll = continuous.size();
            if (pAll < 2 || n < 2) {
                return;   // No analyzed columns; every fit returns null.
            }

            double[] meansAll = new double[pAll];
            double[] sdsAll = new double[pAll];
            double[][] x = new double[n][pAll];
            for (int k = 0; k < pAll; k++) {
                int col = continuous.get(k);
                double s = 0.0;
                for (int i = 0; i < n; i++) {
                    double v = data.getDouble(i, col);
                    x[i][k] = v;
                    s += v;
                }
                meansAll[k] = s / n;
                double ss = 0.0;
                for (int i = 0; i < n; i++) {
                    double dvi = x[i][k] - meansAll[k];
                    ss += dvi * dvi;
                }
                sdsAll[k] = Math.sqrt(ss / (n - 1));
            }

            List<Integer> keep = new ArrayList<>();
            for (int k = 0; k < pAll; k++) {
                if (sdsAll[k] > 0) {
                    keep.add(k);
                } else {
                    this.constants.add(continuous.get(k));
                }
            }
            int p = keep.size();
            this.means = new double[p];
            this.sds = new double[p];
            for (int k = 0; k < p; k++) {
                this.cols.add(continuous.get(keep.get(k)));
                this.means[k] = meansAll[keep.get(k)];
                this.sds[k] = sdsAll[keep.get(k)];
            }
            if (p < 2) {
                this.cols.clear();
                return;
            }

            double[][] r = new double[p][p];
            for (int a = 0; a < p; a++) {
                r[a][a] = 1.0;
                for (int b = a + 1; b < p; b++) {
                    double s = 0.0;
                    int ka = keep.get(a), kb = keep.get(b);
                    for (int i = 0; i < n; i++) {
                        s += (x[i][ka] - meansAll[ka]) * (x[i][kb] - meansAll[kb]);
                    }
                    double val = s / ((n - 1) * sdsAll[ka] * sdsAll[kb]);
                    r[a][b] = val;
                    r[b][a] = val;
                }
            }
            this.corr = new Matrix(r);
        }

        /**
         * Fits the equation writing the named variable in terms of ALL other analyzed variables (screened to the ones
         * that matter).
         *
         * @param varName The variable for the left-hand side.
         * @return The fitted constraint; a constant variable comes back with empty support and residual fraction 0;
         * null if the variable is not an analyzed or constant continuous variable of the dataset.
         */
        public Constraint leaveOneOut(String varName) {
            for (int col : this.constants) {
                if (this.data.getVariable(col).getName().equals(varName)) {
                    return new Constraint(varName, new ArrayList<>(), new double[0],
                            this.data.getDouble(0, col), 0.0);
                }
            }
            int i = indexOf(varName);
            if (i < 0) return null;
            ensurePrecision();

            int p = this.cols.size();
            double[] bStd = new double[p];
            double omII = this.precision.get(i, i);
            for (int j = 0; j < p; j++) {
                if (j != i) bStd[j] = -this.precision.get(i, j) / omII;
            }
            List<Integer> screened = screen(i, bStd);
            return refine(i, screened, bStd);
        }

        /**
         * Fits, for each target, its equation in terms of the SAME support universe -- the audit's retained-form
         * report, where every removed variable is written in terms of the retained variables. One inverse of the
         * support submatrix serves all targets.
         *
         * @param targetNames  The variables for the left-hand sides.
         * @param supportNames The common support universe (the targets themselves are excluded automatically).
         * @return One constraint per target, in order; null entries for targets that are not analyzed continuous
         * variables or when fewer than one support variable is analyzed.
         */
        public List<Constraint> onCommonSupport(List<String> targetNames, Collection<String> supportNames) {
            List<Constraint> out = new ArrayList<>();
            List<Integer> universe = new ArrayList<>();
            for (String name : supportNames) {
                int k = indexOf(name);
                if (k >= 0) universe.add(k);
            }
            if (universe.isEmpty()) {
                for (String ignored : targetNames) out.add(null);
                return out;
            }
            int[] u = universe.stream().mapToInt(Integer::intValue).toArray();
            Matrix ruuInv = this.corr.view(u, u).mat().chooseInverse(SOLVE_RIDGE);

            for (String targetName : targetNames) {
                int i = indexOf(targetName);
                if (i < 0 || universe.contains(i)) {
                    out.add(null);
                    continue;
                }
                Matrix rui = this.corr.view(u, new int[]{i}).mat();
                Matrix b = ruuInv.times(rui);
                double[] bStd = new double[this.cols.size()];
                for (int k = 0; k < u.length; k++) {
                    bStd[u[k]] = b.get(k, 0);
                }
                List<Integer> screened = new ArrayList<>();
                for (int k : u) {
                    screened.add(k);
                }
                screened = screenList(screened, bStd);
                out.add(refine(i, screened, bStd));
            }
            return out;
        }

        /**
         * The iterative detector behind {@link #find(DataSet, double)}: repeatedly finds an active variable whose
         * residual fraction given the other active variables is below threshold (screened from a per-round precision
         * matrix, verified exactly), records it, and deactivates it.
         */
        List<Constraint> findAll() {
            List<Constraint> constraints = new ArrayList<>();
            if (this.cols.size() < 2) return constraints;

            List<Integer> active = new ArrayList<>();
            for (int k = 0; k < this.cols.size(); k++) active.add(k);

            boolean found = true;
            while (found && active.size() >= 2) {
                found = false;
                int[] a = active.stream().mapToInt(Integer::intValue).toArray();
                Matrix omA = this.corr.view(a, a).mat().chooseInverse(SOLVE_RIDGE);

                for (int pos = 0; pos < a.length; pos++) {
                    // Approximate residual fraction from the ridge precision; a factor-10 safety margin, then
                    // exact verification on the screened support.
                    double approx = 1.0 / omA.get(pos, pos);
                    if (approx >= 10 * this.threshold) continue;

                    int i = a[pos];
                    double[] bStd = new double[this.cols.size()];
                    for (int q = 0; q < a.length; q++) {
                        if (q != pos) bStd[a[q]] = -omA.get(pos, q) / omA.get(pos, pos);
                    }
                    List<Integer> screened = new ArrayList<>();
                    for (int q = 0; q < a.length; q++) {
                        if (q != pos) screened.add(a[q]);
                    }
                    screened = screenList(screened, bStd);
                    Constraint c = refine(i, screened, bStd);
                    if (c.fractionResidual() < this.threshold) {
                        constraints.add(c);
                        active.remove((Integer) i);
                        found = true;
                        break;
                    }
                }
            }
            return constraints;
        }

        private void ensurePrecision() {
            if (this.precision == null) {
                this.precision = this.corr.chooseInverse(SOLVE_RIDGE);
            }
        }

        private int indexOf(String name) {
            for (int k = 0; k < this.cols.size(); k++) {
                if (this.data.getVariable(this.cols.get(k)).getName().equals(name)) return k;
            }
            return -1;
        }

        private List<Integer> screen(int i, double[] bStd) {
            List<Integer> all = new ArrayList<>();
            for (int j = 0; j < bStd.length; j++) {
                if (j != i) all.add(j);
            }
            return screenList(all, bStd);
        }

        /**
         * Keeps the candidates whose standardized coefficients pass the relative and absolute floors, capped at
         * SCREEN_CAP by magnitude.
         */
        private List<Integer> screenList(List<Integer> candidates, double[] bStd) {
            double max = 0.0;
            for (int j : candidates) max = Math.max(max, Math.abs(bStd[j]));
            double floor = Math.max(SCREEN_RELATIVE * max, SCREEN_ABSOLUTE);
            List<Integer> kept = new ArrayList<>();
            for (int j : candidates) {
                if (Math.abs(bStd[j]) > floor) kept.add(j);
            }
            kept.sort(Comparator.comparingDouble(j -> -Math.abs(bStd[(int) j])));
            if (kept.size() > SCREEN_CAP) {
                kept = new ArrayList<>(kept.subList(0, SCREEN_CAP));
            }
            return kept;
        }

        /**
         * Exact residual fraction of analyzed variable i given the analyzed support, from the correlation matrix.
         */
        private double exactFraction(int i, List<Integer> support) {
            if (support.isEmpty()) return 1.0;
            int[] s = support.stream().mapToInt(Integer::intValue).toArray();
            int[] ii = {i};
            Matrix rss = this.corr.view(s, s).mat();
            Matrix rsi = this.corr.view(s, ii).mat();
            Matrix b = rss.chooseInverse(SOLVE_RIDGE).times(rsi);
            double explained = rsi.transpose().times(b).get(0, 0);
            return Math.max(1.0 - explained, 0.0);
        }

        /**
         * One backward sweep over the screened support in ascending coefficient magnitude: a variable is dropped when
         * the exact residual fraction stays below max(threshold, twice the screened-support fraction), so an exact
         * relation keeps only the variables needed to stay exact, and a near-deterministic relation keeps only the
         * variables that matter to the fit. Then the constraint is assembled from an exact solve on what remains.
         */
        private Constraint refine(int i, List<Integer> screened, double[] bStd) {
            List<Integer> keep = new ArrayList<>(screened);
            keep.sort(Comparator.comparingDouble(j -> Math.abs(bStd[(int) j])));
            double pruneThreshold = Math.max(this.threshold, 2.0 * exactFraction(i, keep));

            for (int j : new ArrayList<>(keep)) {
                List<Integer> trial = new ArrayList<>(keep);
                trial.remove((Integer) j);
                if (exactFraction(i, trial) <= pruneThreshold) {
                    keep = trial;
                }
            }
            keep.sort(Comparator.naturalOrder());

            String name = this.data.getVariable(this.cols.get(i)).getName();
            List<String> supportNames = new ArrayList<>();
            double[] coefs = new double[keep.size()];
            double intercept = this.means[i];
            if (!keep.isEmpty()) {
                int[] s = keep.stream().mapToInt(Integer::intValue).toArray();
                int[] ii = {i};
                Matrix rss = this.corr.view(s, s).mat();
                Matrix rsi = this.corr.view(s, ii).mat();
                Matrix b = rss.chooseInverse(SOLVE_RIDGE).times(rsi);
                for (int k = 0; k < s.length; k++) {
                    supportNames.add(this.data.getVariable(this.cols.get(s[k])).getName());
                    coefs[k] = b.get(k, 0) * this.sds[i] / this.sds[s[k]];
                    intercept -= coefs[k] * this.means[s[k]];
                }
            }
            return new Constraint(name, supportNames, coefs, intercept, exactFraction(i, keep));
        }
    }
}
