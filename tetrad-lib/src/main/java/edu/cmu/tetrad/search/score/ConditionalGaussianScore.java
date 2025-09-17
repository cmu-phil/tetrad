package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a conditional Gaussian BIC score for FGS, which calculates a BIC score for mixed
 * discrete/Gaussian data using the conditional Gaussian likelihood function.
 *
 * Reference:
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2018). Scoring Bayesian networks of mixed variables.
 * International Journal of Data Science and Analytics, 6, 3–18.
 *
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see ConditionalGaussianLikelihood
 * @see DegenerateGaussianScore
 */
public class ConditionalGaussianScore implements Score {

    // Dataset and variables.
    private final DataSet dataSet;
    private final List<Node> variables;

    // Likelihood engine (leave as-is; we just forward settings).
    private final ConditionalGaussianLikelihood likelihood;

    // BIC controls.
    private double penaltyDiscount;
    private double structurePrior = 0;

    // Discretization controls (forwarded to likelihood).
    private int numCategoriesToDiscretize = 3;

    /**
     * Constructs the score.
     *
     * @param dataSet         mixed (or all-continuous / all-discrete) dataset
     * @param penaltyDiscount BIC penalty multiplier
     * @param discretize      if true, use shadow discretization of continuous parents for discrete children
     */
    public ConditionalGaussianScore(DataSet dataSet, double penaltyDiscount, boolean discretize) {
        if (dataSet == null) throw new NullPointerException("dataSet");

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = penaltyDiscount;

        this.likelihood = new ConditionalGaussianLikelihood(dataSet);

        // Initial wiring to the engine
        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setDiscretize(discretize);
    }

    /** Local BIC score for child i with parents. */
    public double localScore(int i, int... parents) {
        List<Integer> rows = getRows(i, parents);
        if (rows.isEmpty()) return Double.NEGATIVE_INFINITY;

        this.likelihood.setRows(rows);

        ConditionalGaussianLikelihood.Ret ret = this.likelihood.getLikelihood(i, parents);

        double lik = ret.getLik();
        int k = ret.getDof();

        double score = 2.0 * (lik + getStructurePrior(parents))
                       - getPenaltyDiscount() * k * FastMath.log(rows.size());

        if (Double.isNaN(score) || Double.isInfinite(score)) return Double.NEGATIVE_INFINITY;
        return score;
    }

    /** Score difference localScore(y | z ∪ {x}) - localScore(y | z). */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /** Sample size. */
    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    /** FGES “effect edge” convention for this score bump. */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /** Recommended max degree (same heuristic used elsewhere). */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(FastMath.log(this.dataSet.getNumRows()));
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /** Forwarded: number of bins for shadow discretization. */
    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
        this.likelihood.setNumCategoriesToDiscretize(numCategoriesToDiscretize);
    }

    /** Forwarded: enable/disable shadow discretization. */
    public void setDiscretize(boolean discretize) {
        this.likelihood.setDiscretize(discretize);
    }

    /** Forwarded: minimum per-cell N to include a cell in the likelihood. */
    public void setMinSampleSizePerCell(int n) {
        this.likelihood.setMinSampleSizePerCell(n);
    }

    /** Optional ER prior on structure (sparse bias). */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Conditional Gaussian Score Penalty " + nf.format(this.penaltyDiscount);
    }

    // ------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------

    /**
     * Row filter that drops any row with missing values for the child or any parent.
     * For discrete variables we treat -99 as “missing”; for continuous we treat NaN as missing.
     */
    private List<Integer> getRows(int i, int[] parents) {
        List<Integer> rows = new ArrayList<>(this.dataSet.getNumRows());

        for (int r = 0; r < this.dataSet.getNumRows(); r++) {
            // child
            if (isMissing(variables.get(i), r)) continue;
            // parents
            boolean ok = true;
            for (int p : parents) {
                if (isMissing(variables.get(p), r)) { ok = false; break; }
            }
            if (ok) rows.add(r);
        }
        return rows;
    }

    /** Missingness by variable type. */
    private boolean isMissing(Node v, int row) {
        if (v instanceof DiscreteVariable) {
            int val = this.dataSet.getInt(row, this.dataSet.getColumn(v));
            return val == -99; // project convention
        } else if (v instanceof ContinuousVariable) {
            double val = this.dataSet.getDouble(row, this.dataSet.getColumn(v));
            return Double.isNaN(val);
        } else {
            // default conservative
            return false;
        }
    }

    private double getStructurePrior(int[] parents) {
        if (this.structurePrior <= 0) return 0.0;

        int k = parents.length;
        double n = this.dataSet.getNumColumns() - 1;
        double p = this.structurePrior / n;
        // log prior of ER(k; n, p) up to additive constant across families
        return k * FastMath.log(p) + (n - k) * FastMath.log(1.0 - p);
    }
}