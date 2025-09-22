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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.utils.Embedding;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BasisFunctionBlocksBicScore: - Builds an embedded design via Embedding (e.g., truncated Legendre basis per variable)
 * - Constructs blocks mapping (original var -> list of embedded column indices) - Delegates local scoring to
 * BlocksBicScore using the SAME Node instances as the caller's dataset
 * <p>
 * CONTRACT: 'blocks' maps each ORIGINAL variable index v (0..V-1) to the list of embedded column indices in the
 * embedded matrix produced here.
 */
public class BasisFunctionBlocksBicScore implements Score {

    // ---- Source data / variables (block-level nodes are the caller's originals) ----
    private final DataSet raw;
    private final List<Node> variables;

    // ---- Derived (embedded) ----
    private final DataSet embeddedDataSet;
    private final SimpleMatrix Xphi;                 // n x D embedded design (for debugging)
    private final SimpleMatrix Sphi;                 // D x D covariance in embedded space (for debugging)
    private final List<List<Integer>> blocks;        // mapping original var -> embedded col indices

    // ---- Delegate ----
    private final BlocksBicScore delegate;

    /**
     * Constructs a new instance of the {@code BasisFunctionBlocksBicScore} class. This constructor initializes the
     * object based on the raw dataset and the specified polynomial degree for the basis function expansion. It also
     * prepares embedded data, blocks, and other internal structures used for subsequent scoring computations.
     *
     * @param raw    the original dataset used for generating embedded data and computing scores; must not be null
     * @param degree the degree of the polynomial basis functions to be used; must be greater than or equal to 0
     * @throws IllegalArgumentException if {@code raw} is null
     * @throws IllegalArgumentException if {@code degree} is less than 0
     */
    public BasisFunctionBlocksBicScore(DataSet raw, int degree, int basisType) {
        if (raw == null) throw new IllegalArgumentException("raw == null");
        if (degree < 0) throw new IllegalArgumentException("degree must be >= 0");

        this.raw = raw;
        this.variables = new ArrayList<>(raw.getVariables());

        // 1) Build embedded matrix + blocks using your existing Embedding utility
        //    Adjust flags as needed (here: includeIntercept=1, standardize=1).
        Embedding.EmbeddedData emb = Objects.requireNonNull(
                Embedding.getEmbeddedData(raw, degree, basisType, 1),
                "Embedding.getEmbeddedData returned null");

        this.embeddedDataSet = emb.embeddedData();

        // blocks: one per ORIGINAL variable, in the same order
        this.blocks = new ArrayList<>(raw.getNumColumns());
        for (int i = 0; i < raw.getNumColumns(); i++) {
            this.blocks.add(emb.embedding().get(i));
        }

        // Optional: keep embedded matrices around for debugging/inspection
        this.Xphi = embeddedDataSet.getDoubleData().getSimpleMatrix();
        this.Sphi = DataUtils.cov(this.Xphi);

        // 2) Delegate to BlocksBicScore with the SAME Node instances as the caller uses
        this.delegate = new BlocksBicScore(new BlockSpec(embeddedDataSet, this.blocks, this.variables));
    }

    // ---- Score interface ----

    /**
     * Computes the local score difference for a given pair of variables and their parent set. Delegates the computation
     * to an underlying scoring mechanism.
     *
     * @param x the index of the variable being considered as an additional parent
     * @param y the index of the variable for which the local score difference is being computed
     * @param z the array of parent indices currently associated with the variable y
     * @return the difference in local score caused by adding x as a parent to variable y
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        // delegate uses the same node list; indices align with our variables
        return delegate.localScoreDiff(x, y, z);
    }

    /**
     * Retrieves a list of variables associated with this instance. The list contains the original {@link Node}
     * instances, copied into a new list to ensure the integrity of the caller's data.
     *
     * @return a list of {@link Node} objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        // return exactly the caller's original Node instances
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size of the dataset by delegating to the underlying raw dataset's method to get the number
     * of rows.
     *
     * @return the number of rows in the raw dataset, representing the sample size.
     */
    @Override
    public int getSampleSize() {
        return raw.getNumRows();
    }

    /**
     * Updates the penalty discount value used in scoring calculations by delegating the value to the underlying
     * computation. Altering the penalty discount influences the scoring mechanism, potentially modifying how scores are
     * calculated.
     *
     * @param c the new penalty discount value to be applied
     */
    public void setPenaltyDiscount(double c) {
        delegate.setPenaltyDiscount(c);
    }

    /**
     * Sets the ridge parameter by delegating the value to the underlying computation. The ridge parameter is used to
     * control regularization in the underlying model.
     *
     * @param ridge the value of the ridge parameter to be set
     */
    public void setRidge(double ridge) {
        delegate.setRidge(ridge);
    }

    /**
     * Computes the local score for a given node (y) and its specified parent nodes. This method delegates the scoring
     * computation to an underlying implementation.
     *
     * @param y       the target node for which the local score is being computed; must not be null
     * @param parents a list of nodes representing the parent set of the target node; must not be null
     * @return the computed local score as a double value
     */
    public double localScore(Node y, List<Node> parents) {
        return delegate.localScore(y, parents);
    }

    /**
     * Computes the local score for a given target node specified by its index and a set of parent node indices. The
     * computation is delegated to the underlying scoring mechanism.
     *
     * @param i       the index of the target node for which the local score is being computed
     * @param parents the indices of the parent nodes influencing the target node; can be none or multiple
     * @return the computed local score as a double value for the specified target node and its parent nodes
     */
    public double localScore(int i, int... parents) {
        return delegate.localScore(i, parents);
    }

    /**
     * Computes the local score delta for a given node (y) based on its initial parent set, a specified parent node
     * change, and whether the change involves adding or removing the parent. This method delegates the computation to
     * an underlying scoring mechanism.
     *
     * @param y             the target node for which the local score delta is being computed; must not be null
     * @param oldParents    a list of nodes representing the current parent set of the target node; must not be null
     * @param changedParent the parent node being added or removed; must not be null
     * @param adding        a boolean flag indicating whether the change involves adding (true) or removing (false) the
     *                      parent
     * @return the computed change in local score resulting from the parent modification
     */
    public double localScoreDelta(Node y, List<Node> oldParents, Node changedParent, boolean adding) {
        return delegate.localScoreDelta(y, oldParents, changedParent, adding);
    }

    /**
     * Retrieves the blocks of integers associated with this instance. Each block is represented as a list of integers,
     * and the blocks are organized in a list structure.
     *
     * @return a list of lists of integers, where each inner list represents a block.
     */
    public List<List<Integer>> getBlocks() {
        return blocks;
    }

    /**
     * Retrieves the embedded dataset associated with this instance.
     *
     * @return the embedded dataset.
     */
    public DataSet getEmbeddedDataSet() {
        return embeddedDataSet;
    }

    /**
     * Retrieves the embedded data matrix associated with the instance.
     *
     * @return a {@link SimpleMatrix} instance representing the embedded data.
     */
    public SimpleMatrix getEmbeddedData() {
        return Xphi;
    }

    /**
     * Retrieves the embedded covariance matrix associated with this instance. The covariance matrix represents the
     * relationships between variables in the embedded data space.
     *
     * @return a {@code SimpleMatrix} instance representing the embedded covariance matrix.
     */
    public SimpleMatrix getEmbeddedCovariance() {
        return Sphi;
    }
}
