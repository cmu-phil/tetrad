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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * IndTestBasisFunctionBlocks - Builds a per-variable truncated basis expansion (via Embedding) - Constructs the blocks
 * mapping (original var -> list of embedded column indices) - Delegates CI testing to IndTestBlocks over those blocks
 * <p>
 * CONTRACT: 'blocks' maps each ORIGINAL variable index v (0..V-1) to the list of embedded column indices in the
 * embedded matrix produced here.
 */
public class IndTestBasisFunctionBlocks implements IndependenceTest, RawMarginalIndependenceTest,
        EffectiveSampleSizeSettable {

    // ---- Source data ----
    private final DataSet raw;
    private final List<Node> variables;   // the block-level variables (exactly the caller's originals)

    // ---- Derived (embedded) ----
    private final SimpleMatrix Xphi;                 // n x D embedded design
    private final SimpleMatrix Sphi;                 // D x D covariance of embedded data
    private final List<List<Integer>> blocks;        // mapping original var -> embedded column indices
    private final IndTestBlocksWilkes blocksTest;          // delegate
    private final int degree;
    private final int sampleSize;
    private int basisType = 1;
    // ---- Knobs ----
    private double alpha = 0.01;
    private int nEff;

    /**
     * Constructs an instance of IndTestBasisFunctionBlocks. This class is designed to perform independence
     * tests based on basis function transformations of the provided dataset, using specified degree and basis type.
     *
     * @param raw the input dataset, which provides the raw data to be analyzed. Cannot be null.
     * @param degree the degree of the basis function transformation. Must be a non-negative integer.
     * @param basisType the type of basis functions to use for transformations (e.g., polynomial, Fourier, etc.).
     *                  This value determines the embedding style and configuration specifics.
     * @throws IllegalArgumentException if the raw dataset is null or if the degree is negative.
     */
    public IndTestBasisFunctionBlocks(DataSet raw, int degree, int basisType) {
        if (raw == null) throw new IllegalArgumentException("raw == null");
        if (degree < 0) throw new IllegalArgumentException("degree must be >= 0");

        this.raw = raw;
        this.degree = degree;
        this.basisType = basisType;
        // Keep the exact Node instances from the caller's dataset
        this.variables = new ArrayList<>(raw.getVariables());

        this.sampleSize = raw.getNumRows();
        setEffectiveSampleSize(-1);

        // 1) Build embedded matrix + blocks using your existing Embedding utility
        //    (Adjust the extra args (e.g., intercept/standardization) to your Embedding API as needed.)
        Embedding.EmbeddedData embeddedData = Objects.requireNonNull(
                Embedding.getEmbeddedData(raw, degree,  basisType, 1),
                "Embedding.getEmbeddedData returned null");

        // blocks: one per ORIGINAL variable, in the same order
        this.blocks = new ArrayList<>(raw.getNumColumns());
        for (int i = 0; i < raw.getNumColumns(); i++) {
            this.blocks.add(embeddedData.embedding().get(i));
        }

        // Embedded dataset and design/cov (handy for debugging)
        DataSet embeddedDs = embeddedData.embeddedData();
        this.Xphi = embeddedDs.getDoubleData().getSimpleMatrix();
        this.Sphi = DataUtils.cov(this.Xphi);

        // 2) Delegate CI testing to IndTestBlocks:
        //    IMPORTANT: pass *this.variables* (the same Node objects you expose)
        this.blocksTest = new IndTestBlocksWilkes(new BlockSpec(embeddedDs, this.blocks, this.variables));
        this.blocksTest.setEffectiveSampleSize(-1);
    }

    // ====== IndependenceTest API ======

    private static DataSet twoColumnDataSet(String nameX, double[] x,
                                            String nameY, double[] y) {
        int n = x.length;
        double[][] m = new double[n][2];
        for (int i = 0; i < n; i++) {
            m[i][0] = x[i];
            m[i][1] = y[i];
        }
        List<Node> vars = new ArrayList<>(2);
        vars.add(new ContinuousVariable(nameX));
        vars.add(new ContinuousVariable(nameY));

        DoubleDataBox dataBox = new DoubleDataBox(m);

        return new BoxDataSet(dataBox, vars);
    }

    /**
     * Checks for statistical independence between two given variables (nodes), conditioned on a set of other
     * variables.
     *
     * @param x the first variable (node) to test for independence
     * @param y the second variable (node) to test for independence
     * @param z the set of conditioning variables (nodes) for the independence test
     * @return an IndependenceResult containing details about the test result, including whether the variables are
     * independent, the p-value, and the significance level used
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        // Delegate to the block test to get the p-value, then apply this class's alpha.
        IndependenceResult r = this.blocksTest.checkIndependence(x, y, z);
        double p = r.getPValue();
        boolean indep = p > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), indep, p, alpha - p);
    }

    /**
     * Retrieves the list of nodes (variables) associated with this instance. The nodes returned are exactly the ones
     * used internally by the delegate, ensuring identity consistency and preventing "unknown node" issues.
     *
     * @return a list of nodes representing the variables
     */
    @Override
    public List<Node> getVariables() {
        // Return exactly the variables the delegate was constructed with
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the original dataset represented by this instance.
     *
     * @return the underlying data as a {@link DataModel} object
     */
    @Override
    public DataModel getData() {
        return raw;
    }

    /**
     * Indicates whether this instance is operating in verbose mode. Verbose mode allows for detailed output or logging
     * to assist in debugging or monitoring execution.
     *
     * @return true if verbose mode is enabled; false otherwise
     */
    @Override
    public boolean isVerbose() {
        return this.blocksTest.isVerbose();
    }

    /**
     * Sets the verbosity mode for this instance. Enabling verbosity allows for more detailed output or logging during
     * execution.
     *
     * @param verbose true to enable verbose output, false to disable it
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.blocksTest.setVerbose(verbose);
    }

    @Override
    public double computePValue(double[] x, double[] y) throws InterruptedException {
        if (x == null || y == null) return 1.0;
        int n = x.length;
        if (y.length != n || n < 3) return 1.0;

        // Build a tiny 2-column dataset for BFIT
        DataSet ds = twoColumnDataSet("X", x, "Y", y);

        // Build the BFIT test bound to this dataset
        IndTestBasisFunctionBlocks test = new IndTestBasisFunctionBlocks(ds, degree, basisType);

        // Resolve nodes and run the marginal test
        Node X = ds.getVariable("X");
        Node Y = ds.getVariable("Y");

        IndependenceResult r = test.checkIndependence(X, Y, Collections.emptySet());
        double p = (r != null) ? r.getPValue() : 1.0;

        // Clamp for numeric robustness
        if (!Double.isFinite(p)) return 1.0;
        return Math.max(0.0, Math.min(p, 1.0));
    }

    // --- helper: build a 2-column continuous DataSet (rows = samples) ---

    /**
     * Default multivariate fallback: run BFIT on each column of Y and combine with Fisher. If you later add a true
     * multivariate BFIT, override this to call it directly.
     */
    @Override
    public double computePValue(double[] x, double[][] Y) throws InterruptedException {
        if (Y == null || Y.length == 0) return 1.0;
        final int n = x.length;
        if (Y.length != n) return 1.0;

        double stat = 0.0;
        int k = 0;

        int m = Y[0].length;
        for (int j = 0; j < m; j++) {
            double[] yj = new double[n];
            for (int i = 0; i < n; i++) yj[i] = Y[i][j];
            double pj = computePValue(x, yj);
            if (Double.isNaN(pj)) continue;
            double pc = Math.max(pj, 1e-300); // avoid log(0)
            stat += -2.0 * Math.log(pc);
            k++;
        }
        if (k == 0) return 1.0;

        int df = 2 * k;
        // Chi-square upper tail CDF (use Apache Commons if on classpath)
        org.apache.commons.math3.distribution.ChiSquaredDistribution chi2 =
                new org.apache.commons.math3.distribution.ChiSquaredDistribution(df);
        double cdf = chi2.cumulativeProbability(stat);
        double p = 1.0 - cdf;
        if (!Double.isFinite(p)) return 1.0;
        if (p < 0.0) return 0.0;
        if (p > 1.0) return 1.0;
        return p;
    }

    /**
     * Retrieves the alpha value currently set for this instance. The alpha value is typically used as a threshold or
     * parameter in statistical tests or computations.
     *
     * @return the alpha value, which is a double strictly between 0 and 1
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the alpha value used as a threshold or parameter for statistical tests or computations. The alpha value must
     * be within the range (0, 1), exclusive.
     *
     * @param alpha the desired alpha value, strictly between 0 and 1
     * @throws IllegalArgumentException if alpha is less than or equal to 0 or greater than or equal to 1
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    /**
     * Retrieves the list of blocks, where each block is represented as a list of integers. These blocks may correspond
     * to partitions or groupings derived from the data or configuration.
     *
     * @return a list of blocks, with each block being a list of integers
     */
    public List<List<Integer>> getBlocks() {
        return blocks;
    }

    /**
     * Retrieves the embedded data matrix corresponding to the transformed or processed data based on the configuration
     * of this instance.
     *
     * @return the embedded data matrix as a SimpleMatrix object
     */
    public SimpleMatrix getEmbeddedData() {
        return Xphi;
    }

    @Override
    public int getEffectiveSampleSize() {
        return this.nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.sampleSize : nEff;
    }
}
