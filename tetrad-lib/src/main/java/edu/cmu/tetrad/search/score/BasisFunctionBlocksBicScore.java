package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BasisFunctionBlocksBicScore:
 * - Builds an embedded design via Embedding (e.g., truncated Legendre basis per variable)
 * - Constructs blocks mapping (original var -> list of embedded column indices)
 * - Delegates local scoring to BlocksBicScore using the SAME Node instances as the caller's dataset
 *
 * CONTRACT: 'blocks' maps each ORIGINAL variable index v (0..V-1) to the list of
 *           embedded column indices in the embedded matrix produced here.
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

    public BasisFunctionBlocksBicScore(DataSet raw, int degree) {
        if (raw == null) throw new IllegalArgumentException("raw == null");
        if (degree < 0) throw new IllegalArgumentException("degree must be >= 0");

        this.raw = raw;
        this.variables = new ArrayList<>(raw.getVariables());

        // 1) Build embedded matrix + blocks using your existing Embedding utility
        //    Adjust flags as needed (here: includeIntercept=1, standardize=1).
        Embedding.EmbeddedData emb = Objects.requireNonNull(
                Embedding.getEmbeddedData(raw, degree, /*includeIntercept*/ 2, /*standardize*/ 1),
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
        this.delegate = new BlocksBicScore(embeddedDataSet, this.blocks, this.variables);
    }

    // ---- Score interface ----

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        // delegate uses the same node list; indices align with our variables
        return delegate.localScoreDiff(x, y, z);
    }

    @Override
    public List<Node> getVariables() {
        // return exactly the caller's original Node instances
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return raw.getNumRows();
    }

    // ---- Convenience passthroughs to the delegate (optional knobs) ----
    public void setPenaltyDiscount(double c) {
        delegate.setPenaltyDiscount(c);
    }

    public void setRidge(double ridge) {
        delegate.setRidge(ridge);
    }

    // ---- Optional helpers if you want direct localScore access (not required by Score) ----
    public double localScore(Node y, List<Node> parents) {
        return delegate.localScore(y, parents);
    }

    public double localScore(int i, int... parents) {
        return delegate.localScore(i, parents);
    }

    public double localScoreDelta(Node y, List<Node> oldParents, Node changedParent, boolean adding) {
        return delegate.localScoreDelta(y, oldParents, changedParent, adding);
    }

    // ---- Debug/inspection getters (handy for tests) ----
    public List<List<Integer>> getBlocks() { return blocks; }
    public DataSet getEmbeddedDataSet() { return embeddedDataSet; }
    public SimpleMatrix getEmbeddedData() { return Xphi; }
    public SimpleMatrix getEmbeddedCovariance() { return Sphi; }
}