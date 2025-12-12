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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Double.NaN;

/**
 * Implements degenerate Gaussian test as a likelihood ratio test.
 * <p>
 * Reference: Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2019). Learning high-dimensional DAGs with mixed data-types.
 * <p>
 * Row-subsetting support: If RowsSettable.setRows(...) is used, the covariance matrix is recomputed over the embedded
 * data restricted to those rows, and the effective sample size defaults to the subset size (unless explicitly
 * overridden).
 *
 * @author Bryan Andrews
 * @author Joseph Ramsey refactoring 2024-12-26; RowsSettable update 2025-12-12
 */
public class IndTestDegenerateGaussianLrt implements IndependenceTest, EffectiveSampleSizeSettable, RowsSettable {

    // ---- Source data ----
    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;

    // ---- Embedded ----
    private final Map<Integer, List<Integer>> embedding;
    private final DataSet embeddedDataSetFull;   // full embedded dataset (all rows)
    // ---- Params / state ----
    private final int sampleSizeFull;
    private SimpleMatrix covarianceMatrix;       // covariance of embedded data (full or subset)
    // ---- Row subset ----
    private List<Integer> rows = null;           // null => all rows
    private int sampleSize;                      // current sample size (full or subset)
    private int nEff;

    private double lambda = 0.0;
    private double alpha = 0.01;
    private double pValue = NaN;
    private boolean verbose;

    /**
     * Constructs the test using the given (mixed) data set.
     *
     * @param dataSet The data being analyzed.
     */
    public IndTestDegenerateGaussianLrt(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet == null");

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();

        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) nodesHash.put(this.variables.get(j), j);
        this.nodeHash = nodesHash;

        // Expand discrete columns -> indicators; continuous get truncation=1 (contract: first basis term is x).
        // basis scale = -1 => no scaling.
        Embedding.EmbeddedData embeddedData = Objects.requireNonNull(Embedding.getEmbeddedData(dataSet, 1, 1, -1), "Embedding.getEmbeddedData returned null");

        this.embedding = embeddedData.embedding();
        this.embeddedDataSetFull = embeddedData.embeddedData();

        this.sampleSizeFull = dataSet.getNumRows();
        this.sampleSize = sampleSizeFull;
        setEffectiveSampleSize(-1);

        // Default: covariance on full embedded data.
        this.covarianceMatrix = DataUtils.cov(this.embeddedDataSetFull.getDoubleData().getSimpleMatrix());

        // Keep lambda behavior as before.
        this.setLambda(lambda);
    }

    // -------------------------------------------------------------------------
    // IndependenceTest
    // -------------------------------------------------------------------------

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double pValue = getPValue(x, y, z);
        boolean independent = pValue > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue, alpha - pValue);
    }

    private double getPValue(Node x, Node y, Set<Node> z) {
        List<Node> zList = new ArrayList<>(z);
        Collections.sort(zList);

        Integer _xObj = this.nodeHash.get(x);
        Integer _yObj = this.nodeHash.get(y);
        if (_xObj == null || _yObj == null) {
            // Unknown node: be conservative.
            this.pValue = 1.0;
            return 1.0;
        }

        int _x = _xObj;
        int _y = _yObj;

        int[] _z = new int[zList.size()];
        for (int i = 0; i < zList.size(); i++) {
            Integer zi = this.nodeHash.get(zList.get(i));
            if (zi == null) {
                this.pValue = 1.0;
                return 1.0;
            }
            _z[i] = zi;
        }

        // Embedded indices for X, Y, Z.
        List<Integer> embedded_x = embedding.get(_x);
        List<Integer> embedded_y = embedding.get(_y);
        if (embedded_x == null || embedded_y == null) {
            this.pValue = 1.0;
            return 1.0;
        }

        List<Integer> embedded_z = new ArrayList<>();
        for (int value : _z) {
            List<Integer> embeddedValues = embedding.get(value);
            if (embeddedValues != null) embedded_z.addAll(embeddedValues);
        }

        int[] xIndices = embedded_x.stream().mapToInt(Integer::intValue).toArray();
        int[] yIndices = embedded_y.stream().mapToInt(Integer::intValue).toArray();
        int[] zIndices = embedded_z.stream().mapToInt(Integer::intValue).toArray();

        // Guardrails
        if (xIndices.length == 0 || yIndices.length == 0) {
            this.pValue = 1.0;
            return 1.0;
        }

        // Variance estimates
        double eps = 1e-10;
        double sigma0_sq = Math.max(eps, computeResidualVariance(xIndices, zIndices));
        double sigma1_sq = Math.max(eps, computeResidualVariance(xIndices, concatArrays(yIndices, zIndices)));

        // LR statistic
        double LR_stat = nEff * Math.log(sigma0_sq / sigma1_sq);

        int df = yIndices.length;
        if (df == 0) {
            this.pValue = 1.0;
            return 1.0;
        }

        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double p = 1.0 - chi2.cumulativeProbability(LR_stat);

        // Clamp a bit for numeric weirdness
        if (!Double.isFinite(p)) p = 1.0;
        if (p < 0.0) p = 0.0;
        if (p > 1.0) p = 1.0;

        this.pValue = p;
        return p;
    }

    /**
     * Computes the variance of residuals given the indices of predictors.
     */
    private double computeResidualVariance(int[] xIndices, int[] predictorIndices) {
        if (predictorIndices.length == 0) {
            return StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices).trace() / xIndices.length;
        }

        SimpleMatrix Sigma_XX = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, xIndices);
        SimpleMatrix Sigma_XP = StatUtils.extractSubMatrix(covarianceMatrix, xIndices, predictorIndices);
        SimpleMatrix Sigma_PP = StatUtils.extractSubMatrix(covarianceMatrix, predictorIndices, predictorIndices);

        // OLS estimate of X given predictors P (with chosen inverse)
        SimpleMatrix beta = (new Matrix(Sigma_PP).chooseInverse(lambda)).getData().mult(Sigma_XP.transpose());

        // Residual variance
        return Sigma_XX.minus(Sigma_XP.mult(beta)).trace() / xIndices.length;
    }

    private int[] concatArrays(int[] first, int[] second) {
        int[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    // -------------------------------------------------------------------------
    // RowsSettable
    // -------------------------------------------------------------------------

    @Override
    public List<Integer> getRows() {
        return rows;
    }

    @Override
    public void setRows(List<Integer> rows) {
        // rows == null => use all rows
        if (rows == null) {
            this.rows = null;
            this.sampleSize = sampleSizeFull;
            setEffectiveSampleSize(-1);
            this.covarianceMatrix = DataUtils.cov(this.embeddedDataSetFull.getDoubleData().getSimpleMatrix());
            return;
        }

        // Validate
        int n0 = dataSet.getNumRows();
        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0 || r >= n0) throw new IllegalArgumentException("Row " + i + " out of range: " + r);
        }

        this.rows = new ArrayList<>(rows);
        this.sampleSize = this.rows.size();
        setEffectiveSampleSize(-1);

        DataSet subEmbedded = subsetRows(this.embeddedDataSetFull, this.rows);
        this.covarianceMatrix = DataUtils.cov(subEmbedded.getDoubleData().getSimpleMatrix());
    }

    private DataSet subsetRows(DataSet ds, List<Integer> rows) {
        int m = rows.size();
        int p = ds.getNumColumns();
        double[][] data = new double[m][p];

        for (int i = 0; i < m; i++) {
            int r = rows.get(i);
            for (int j = 0; j < p; j++) {
                data[i][j] = ds.getDouble(r, j);
            }
        }

        DoubleDataBox box = new DoubleDataBox(data);
        return new BoxDataSet(box, ds.getVariables());
    }

    // -------------------------------------------------------------------------
    // Getters/setters + boilerplate
    // -------------------------------------------------------------------------

    public double getPValue() {
        return this.pValue;
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    @Override
    public DataSet getData() {
        return this.dataSet.copy();
    }

    public double getAlpha() {
        return this.alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be between 0 and 1.");
        this.alpha = alpha;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    @Override
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.sampleSize : nEff;
    }

    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Degenerate Gaussian, alpha = " + nf.format(getAlpha());
    }

    // Not implemented in the original; keep as-is.
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }
}