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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.tetrad.util.TMath;

/**
 * The {@code Embedding} class provides utilities for transforming datasets into embedded representations through basis
 * expansions and one-hot encoding. This process is commonly used in preprocessing steps for machine learning or
 * statistical analysis, enabling enhanced variable representations.
 *
 * @author josephramsey
 * @author bandrews
 */
public class Embedding {

    /**
     * Utility class for embedding data.
     */
    private Embedding() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Computes the embedded data representation based on the provided dataset and parameters.
     *
     * @param dataSet         The original dataset to be embedded; must not be null.
     * @param truncationLimit The maximum number of basis expansions for continuous variables; must be a positive
     *                        integer.
     * @param basisType       The type of basis function to use for continuous variable expansions. The function types
     *                        are as follows:
     *                        <ul>
     *                             <li> 0 = `g(x) = x^index [Polynomial basis]</li>
     *                             <li> 1 = `g(x) = legendre(index, x) [Legendre polynomial]</li>
     *                             <li> 2 = `g(x) = hermite1(index, x) [Probabilist's Hermite polynomial]</li>
     *                             <li> 3 = `g(x) = chebyshev(index, x) [Chebyshev polynomial]</li>
     *                         </ul>
     * @param basisScale      The scaling factor for data transformation. Set to 0 for standardization, positive for
     *                        min-max scaling to [-basisScale, basisScale], -1 to skip scaling, or
     *                        {@link #RANK_TRANSFORM} to rank-transform each continuous column to [-1, 1] first.
     * @return An instance of {@code EmbeddedData}, containing the original dataset, the embedded dataset, and a mapping
     * from original variable indices to their respective transformed indices in the embedded dataset.
     * @throws IllegalArgumentException If the dataset is null, the truncation limit is less than 1, or the basis scale
     *                                  parameter is invalid.
     */
    /**
     * Sentinel for {@code basisScale}: rank-transform each continuous column to a uniform grid on [-1, 1] before
     * applying the basis. With the Legendre basis this makes the basis exactly orthogonal under the empirical
     * measure, gives every row the same leverage, and makes the null distribution of the basis-function LRT
     * chi-square on its nominal degrees of freedom. Min-max scaling (basisScale = 1) instead concentrates the
     * higher-order columns on the few most extreme rows; the resulting LRT null has the chi-square mean but a
     * power-law tail (generalized Pareto shape about 0.37 at truncation 3, N = 1000), so a few high-leverage rows
     * can manufacture a spurious nonlinear edge at any penalty. Rank transformation discards marginal shape and
     * models the copula, which is the appropriate target for detecting nonlinear dependence; it is invariant to
     * monotone marginal transforms and costs about 6 percent of the LRT on Gaussian data.
     */
    public static final double RANK_TRANSFORM = -2.0;

    /**
     * Replaces each continuous column by its mid-ranks mapped to a uniform grid on [-1, 1]: the k-th smallest
     * value (0-based) becomes -1 + 2 (k + 0.5) / N. Tied values receive the average of the positions they occupy,
     * so a column with m distinct values still has exactly m distinct values afterwards -- a 0/1 column stays
     * two-valued and its higher-order basis columns are then dropped as rank-deficient, which is the correct
     * behaviour. (Breaking ties by row order would instead spread identical values across the grid and inject
     * row-order noise; on integer-valued features with many ties that is not harmless.) Discrete columns are left
     * untouched.
     *
     * @param dataSet The data.
     * @return A copy with continuous columns rank-transformed.
     */
    public static DataSet rankTransformToUnitInterval(DataSet dataSet) {
        DataSet out = dataSet.copy();
        int n = dataSet.getNumRows();
        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            if (!(dataSet.getVariable(j) instanceof ContinuousVariable)) continue;
            final int col = j;
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            java.util.Arrays.sort(order, (u, v) -> Double.compare(dataSet.getDouble(u, col), dataSet.getDouble(v, col)));
            int k = 0;
            while (k < n) {
                int m = k;
                double v = dataSet.getDouble(order[k], col);
                while (m + 1 < n && dataSet.getDouble(order[m + 1], col) == v) m++;
                double midRank = 0.5 * (k + m);  // average 0-based position of the tie group
                double value = -1.0 + 2.0 * (midRank + 0.5) / n;
                for (int t = k; t <= m; t++) out.setDouble(order[t], j, value);
                k = m + 1;
            }
        }
        return out;
    }

    /**
     * Generates an embedding of the given dataset based on the specified parameters.
     * This method transforms the dataset using basis functions according to the
     * truncation limit, basis type, and basis scaling parameters. It supports the handling
     * of both discrete and continuous variables with tailored transformations.
     *
     * @param dataSet the input dataset to be transformed. Must not be null.
     * @param truncationLimit the maximum number of basis function orders to include
     *                        in the transformation. Must be a positive integer.
     * @param basisType the type of basis function to use in the transformation.
     * @param basisScale the scaling mode for the dataset. Must be a positive number,
     *                   0 to standardize the dataset, -1 to skip scaling, or RANK_TRANSFORM
     *                   to apply a rank transformation to the unit interval.
     *
     * @return the transformed dataset represented as an instance of EmbeddedData.
     * @throws IllegalArgumentException if the dataset is null, if truncationLimit is not
     *                                  positive, or if basisScale is invalid.
     */
    public static @NotNull EmbeddedData getEmbeddedData(DataSet dataSet, int truncationLimit, int basisType, double basisScale) {
        if (dataSet == null) {
            throw new IllegalArgumentException("Data set must not be null.");
        }

        if (truncationLimit < 1) {
            throw new IllegalArgumentException("Truncation limit must be a positive integer.");
        }

        int n = dataSet.getNumRows();
        List<Node> variables = dataSet.getVariables();

        if (basisScale == RANK_TRANSFORM) {
            dataSet = rankTransformToUnitInterval(dataSet);
        } else if (basisScale == 0.0) {
            dataSet = DataTransforms.standardizeData(dataSet);
        } else if (basisScale > 0.0) {
            dataSet = DataTransforms.scale(dataSet, -basisScale, basisScale);
        } else if (basisScale != -1) {
            throw new IllegalArgumentException("Basis scale must be a positive number, 0 if the data should be standardized, -1 if the data should not be scaled, or Embedding.RANK_TRANSFORM.");
        }

        Map<Integer, List<Integer>> embedding = new HashMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        // Index of embedded variables in new data set...
        int i = -1;

        for (int i_ = 0; i_ < variables.size(); i_++) {
            Node v = variables.get(i_);

            if (v instanceof DiscreteVariable) {
                Map<List<Integer>, Integer> keys = new HashMap<>();

                int numCategories = ((DiscreteVariable) v).getNumCategories();

//                for (int c = 0; c < numCategories; c++) {
                for (int c = 0; c < numCategories - 1; c++) {
                    List<Integer> key = new ArrayList<>();
                    i++;
                    key.add(c);
                    keys.put(key, i);

                    Node v_ = new ContinuousVariable(v.getName() + "." + ((DiscreteVariable) v).getCategory(c));
                    A.add(v_);
                    B.add(new double[n]);

                    for (int j = 0; j < n; j++) {
                        B.get(i)[j] = dataSet.getInt(j, i_) == c ? 1 : 0;
                    }
                }

                embedding.put(i_, new ArrayList<>(keys.values()));
            } else {
                List<Integer> indexList = new ArrayList<>();

                // Build the raw basis columns for this variable; raw[p - 1] corresponds to
                // basis function p.
                double[][] raw = new double[truncationLimit][n];

                for (int p = 1; p <= truncationLimit; p++) {
                    for (int j = 0; j < n; j++) {
                        raw[p - 1][j] = StatUtils.basisFunctionValue(basisType, p, dataSet.getDouble(j, i_));
                    }
                }

                // Orthonormalize within this variable's block (only meaningful if >1 column),
                // keeping the natural low-order-first ordering so that the first embedded
                // column always spans the linear term. (Callers such as doOneEquationOnly and
                // the degenerate Gaussian classes rely on this ordering; truncationLimit = 1
                // is left untouched, preserving the DG contract that the single column is x
                // itself.)
                //
                // Changes from the pre-2026-8 implementation: the block was previously
                // orthonormalized with a plain Householder QR, which for a rank-deficient
                // block (e.g., a "continuous" variable with only a few distinct values, where
                // higher-order polynomial columns are exactly linearly dependent on lower ones
                // given an intercept) filled the trailing columns of Q with numerically
                // arbitrary orthonormal vectors unrelated to the data. Those junk columns then
                // entered every downstream regression as if they were real basis functions.
                // The block is now orthonormalized by modified Gram-Schmidt in the natural
                // order, and a column is DROPPED when its residual - after projecting out the
                // intercept and the previously kept columns - is numerically zero relative to
                // its centered magnitude. A binary-coded column thus keeps only its linear
                // term; a c-valued column keeps at most c - 1 terms. The first column is
                // always kept. This is a numerical-rank decision (relative tolerance 1e-8);
                // statistical near-collinearity remains the job of the downstream singularity
                // lambda / ridge machinery.
                if (truncationLimit > 1) {
                    final double relTol = 1e-8;

                    // Orthonormal basis for the DROP test: the normalized intercept direction
                    // plus, for each kept column, its component orthogonal to everything kept
                    // so far. Because this set is orthonormal, projections onto its span are
                    // exact (a second pass is applied for numerical hygiene only).
                    List<double[]> dropBasis = new ArrayList<>();
                    double[] ones = new double[n];
                    double invSqrtN = 1.0 / TMath.sqrt(n);
                    for (int j = 0; j < n; j++) ones[j] = invSqrtN;
                    dropBasis.add(ones);

                    // Kept columns as stored in the embedded data set: orthonormalized against
                    // the previously kept columns only (not the intercept), matching the span
                    // produced by the previous QR-based implementation for full-rank blocks.
                    List<double[]> keptStored = new ArrayList<>();
                    List<Integer> keptOrders = new ArrayList<>();

                    for (int p = 1; p <= truncationLimit; p++) {
                        double[] col = raw[p - 1];

                        // Centered magnitude of the original column, for the relative drop
                        // test. A constant column has centered magnitude ~0.
                        double mean = 0.0;
                        for (double val : col) mean += val;
                        mean /= n;
                        double centeredNormSq = 0.0;
                        for (double val : col) centeredNormSq += (val - mean) * (val - mean);
                        double centeredNorm = TMath.sqrt(centeredNormSq);

                        // Residual after projecting onto span{intercept, kept columns}.
                        double[] resid = col.clone();
                        for (int pass = 0; pass < 2; pass++) {
                            for (double[] u : dropBasis) {
                                double dot = 0.0;
                                for (int j = 0; j < n; j++) dot += u[j] * resid[j];
                                for (int j = 0; j < n; j++) resid[j] -= dot * u[j];
                            }
                        }
                        double residNormSq = 0.0;
                        for (double val : resid) residNormSq += val * val;
                        double residNorm = TMath.sqrt(residNormSq);

                        boolean dependent = residNorm <= relTol * TMath.max(centeredNorm, 1e-12);

                        // The first column (the linear term) is always kept.
                        if (p > 1 && dependent) {
                            continue;
                        }

                        // Extend the drop-test basis with the new independent direction.
                        if (residNorm > 0.0) {
                            double[] u = resid; // resid is not reused below; safe to normalize in place
                            for (int j = 0; j < n; j++) u[j] /= residNorm;
                            dropBasis.add(u);
                        }

                        // Stored value: modified Gram-Schmidt against the previously kept
                        // (orthonormal) stored columns, then normalize.
                        double[] qCol = col.clone();
                        for (int pass = 0; pass < 2; pass++) {
                            for (double[] q : keptStored) {
                                double dot = 0.0;
                                for (int j = 0; j < n; j++) dot += q[j] * qCol[j];
                                for (int j = 0; j < n; j++) qCol[j] -= dot * q[j];
                            }
                        }
                        double qNormSq = 0.0;
                        for (double val : qCol) qNormSq += val * val;
                        double qNorm = TMath.sqrt(qNormSq);
                        if (qNorm > 0.0) {
                            for (int j = 0; j < n; j++) qCol[j] /= qNorm;
                        }

                        keptStored.add(qCol);
                        keptOrders.add(p);
                    }

                    for (int k = 0; k < keptStored.size(); k++) {
                        i++;
                        Node vFunctional = new ContinuousVariable(v.getName() + ".P(" + keptOrders.get(k) + ")");
                        A.add(vFunctional);
                        B.add(keptStored.get(k));
                        indexList.add(i);
                    }
                } else {
                    i++;
                    Node vFunctional = new ContinuousVariable(v.getName() + ".P(1)");
                    A.add(vFunctional);
                    B.add(raw[0]);
                    indexList.add(i);
                }

                embedding.put(i_, indexList);
            }
        }

        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        SimpleMatrix D = new SimpleMatrix(B_);
        BoxDataSet embeddedData = new BoxDataSet(new DoubleDataBox(D.toArray2()), A);
        return new EmbeddedData(dataSet.copy(), embeddedData, embedding);
    }

    /**
     * Prunes uninformative basis columns from an embedding using a BIC-crossing screen, so that the effective
     * truncation of each continuous variable is chosen by the data rather than by the truncationLimit parameter.
     * <p>
     * For each continuous variable's embedded block (columns in low-order-first order, orthonormalized within the
     * block by {@link #getEmbeddedData}), the first column (spanning the linear term) is always kept. A higher-order
     * column is kept if and only if there exists at least one embedded column of some OTHER variable with which its
     * squared sample correlation crosses the single-regressor BIC threshold, adjusted for the number of partner
     * columns scanned (an RIC/EBIC-style multiplicity guard):
     * <pre>
     *     n * log(1 / (1 - r^2))  &gt;  log(n) + 2 * log(M),
     * </pre>
     * where n is the sample size and M is the number of partner columns. The left side is the deviance gain of a
     * single-regressor Gaussian regression in either direction, so the screen keeps exactly those basis directions
     * that could produce a BIC-positive pairwise association somewhere in the system at unit penalty discount. A
     * column that fails the screen for every partner can only ever contribute penalty, never a BIC-positive
     * likelihood gain, in any pairwise relation; dropping it discounts the uninformative basis function to zero.
     * <p>
     * The screen is computed once, from the full-sample correlation matrix of the embedded data, independently of any
     * particular graph or test. Because the pruned embedding is a single fixed embedding of the data, downstream
     * scores computed over it remain (penalized) joint likelihoods, and score equivalence across Markov-equivalent
     * DAGs is preserved. As the truncation limit is raised past the data-supported order, newly added columns are
     * either dropped as numerically rank-deficient by {@link #getEmbeddedData} or screened out here, so the pruned
     * embedding -- and hence the score and test -- converge rather than degrade.
     * <p>
     * Limitations, by design: (1) the screen is marginal (pairwise, unconditional), so a basis direction that is
     * uncorrelated with every other variable's block marginally but relevant conditionally (a suppression pattern)
     * will be dropped; (2) the screen uses unit penalty discount, which is deliberately more inclusive than any
     * penalty discount &ge; 1 used by a downstream score; (3) the screen uses the full-sample size, not any
     * caller-set effective sample size. Discrete variables' indicator blocks are not nested expansions and are left
     * untouched, as is any continuous block with a single column.
     *
     * @param dataSet      the ORIGINAL (pre-embedding) dataset, used to distinguish discrete from continuous
     *                     variables; must have variable indices aligned with the keys of {@code embedding}.
     * @param embedding    the embedding map produced by {@link #getEmbeddedData}, mapping each original variable
     *                     index to its embedded column indices in low-order-first order.
     * @param embeddedCorr the correlation matrix of the embedded dataset (e.g., {@code new
     *                     CorrelationMatrix(embeddedData)}); its sample size is used for the BIC threshold.
     * @return a new map with the same keys, in which each continuous variable's column list has been pruned to its
     * screened-in columns (first column always retained). The input map is not modified.
     */
    public static Map<Integer, List<Integer>> pruneUninformativeBasisColumns(
            DataSet dataSet, Map<Integer, List<Integer>> embedding, ICovarianceMatrix embeddedCorr) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (embedding == null) throw new IllegalArgumentException("embedding == null");
        if (embeddedCorr == null) throw new IllegalArgumentException("embeddedCorr == null");

        int n = embeddedCorr.getSampleSize();
        List<Node> variables = dataSet.getVariables();

        // Owner of each embedded column, so partners can exclude same-variable columns.
        Map<Integer, Integer> owner = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : embedding.entrySet()) {
            for (Integer c : e.getValue()) owner.put(c, e.getKey());
        }

        List<Integer> allColumns = new ArrayList<>(owner.keySet());

        Map<Integer, List<Integer>> pruned = new HashMap<>();

        for (Map.Entry<Integer, List<Integer>> e : embedding.entrySet()) {
            int v = e.getKey();
            List<Integer> cols = e.getValue();

            // Discrete indicator blocks and single-column blocks are left untouched.
            if (variables.get(v) instanceof DiscreteVariable || cols.size() <= 1) {
                pruned.put(v, new ArrayList<>(cols));
                continue;
            }

            // Partner columns: all embedded columns belonging to other variables.
            List<Integer> partners = new ArrayList<>();
            for (Integer c : allColumns) {
                if (owner.get(c) != v) partners.add(c);
            }

            int m = TMath.max(partners.size(), 1);
            double threshold = TMath.log(n) + 2.0 * TMath.log(m);

            List<Integer> kept = new ArrayList<>();
            kept.add(cols.get(0)); // The linear-spanning column is always kept.

            for (int k = 1; k < cols.size(); k++) {
                int c = cols.get(k);

                boolean informative = false;
                for (Integer f : partners) {
                    double r = embeddedCorr.getValue(c, f);
                    double r2 = TMath.min(r * r, 1.0 - 1e-12);
                    double deviance = n * TMath.log(1.0 / (1.0 - r2));
                    if (deviance > threshold) {
                        informative = true;
                        break;
                    }
                }

                if (informative) kept.add(c);
            }

            pruned.put(v, kept);
        }

        return pruned;
    }

    /**
     * Represents the embedded data result, holding the original dataset, the transformed embedded dataset, and a
     * mapping between the indices of original variables and their corresponding transformed variables.
     * <p>
     * This record is a lightweight container for storing: - The original dataset (`originalData`) before
     * transformation. - The embedded dataset (`embeddedData`) after applying transformations such as basis expansions
     * and scaling. - A mapping (`embedding`) that associates each original variable with its expanded or encoded
     * indices in the embedded dataset.
     * <p>
     * This class is primarily used to encapsulate the result of a dataset transformation and provide easy access to
     * both the raw and embedded data representations, as well as the transformation metadata.
     *
     * @param originalData The original dataset before transformation.
     * @param embeddedData The embedded dataset after applying transformations.
     * @param embedding    A mapping from original variable indices to their corresponding transformed indices.
     */
    public record EmbeddedData(DataSet originalData, DataSet embeddedData, Map<Integer, List<Integer>> embedding) {
    }
}

