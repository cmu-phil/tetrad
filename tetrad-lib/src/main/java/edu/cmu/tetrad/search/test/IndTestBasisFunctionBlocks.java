package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * IndTestBasisFunctionBlocks
 * - Builds a per-variable truncated basis expansion (via Embedding)
 * - Constructs the blocks mapping (original var -> list of embedded column indices)
 * - Delegates CI testing to IndTestBlocks over those blocks
 *
 * CONTRACT: 'blocks' maps each ORIGINAL variable index v (0..V-1) to the list of
 * embedded column indices in the embedded matrix produced here.
 */
public class IndTestBasisFunctionBlocks implements IndependenceTest, RawMarginalIndependenceTest {

    // ---- Source data ----
    private final DataSet raw;
    private final List<Node> variables;   // the block-level variables (exactly the caller's originals)

    // ---- Derived (embedded) ----
    private final SimpleMatrix Xphi;                 // n x D embedded design
    private final SimpleMatrix Sphi;                 // D x D covariance of embedded data
    private final List<List<Integer>> blocks;        // mapping original var -> embedded column indices
    private final IndTestBlocks blocksTest;          // delegate

    // ---- Knobs ----
    private double alpha = 0.01;

    /**
     * @param raw    original dataset to embed
     * @param degree truncation degree for the basis (delegated to Embedding)
     *
     * NOTE: The Nodes returned by getVariables() are EXACTLY the ones used inside IndTestBlocks,
     * so identity matches and there are no "unknown node" issues.
     */
    public IndTestBasisFunctionBlocks(DataSet raw, int degree) {
        if (raw == null) throw new IllegalArgumentException("raw == null");
        if (degree < 0) throw new IllegalArgumentException("degree must be >= 0");

        this.raw = raw;
        // Keep the exact Node instances from the caller's dataset
        this.variables = new ArrayList<>(raw.getVariables());

        // 1) Build embedded matrix + blocks using your existing Embedding utility
        //    (Adjust the extra args (e.g., intercept/standardization) to your Embedding API as needed.)
        Embedding.EmbeddedData embeddedData = Objects.requireNonNull(
                Embedding.getEmbeddedData(raw, degree, /*includeIntercept?*/ 1, /*standardize?*/ 1),
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
        this.blocksTest = new IndTestBlocks(embeddedDs, this.blocks, this.variables);
    }

    // ====== IndependenceTest API ======

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        // Delegate to the block test to get the p-value, then apply this class's alpha.
        IndependenceResult r = this.blocksTest.checkIndependence(x, y, z);
        double p = r.getPValue();
        boolean indep = p > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), indep, p, alpha - p);
    }

    @Override
    public List<Node> getVariables() {
        // Return exactly the variables the delegate was constructed with
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return raw;
    }

    @Override
    public boolean isVerbose() {
        return this.blocksTest.isVerbose();
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.blocksTest.setVerbose(verbose);
    }

    @Override
    public double computePValue(double[] x, double[] y) {
        throw new UnsupportedOperationException(
                "Use checkIndependence(Node,Node,Set) with the dataset; array version is unsupported for block tests.");
    }

    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    // ---- Expose the built blocks & embedded matrices (useful for debugging) ----
    public List<List<Integer>> getBlocks() { return blocks; }
    public SimpleMatrix getEmbeddedData() { return Xphi; }
    public SimpleMatrix getEmbeddedCovariance() { return Sphi; }
}