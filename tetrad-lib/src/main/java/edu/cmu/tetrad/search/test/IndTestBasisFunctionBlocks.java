package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.utils.Embedding;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * IndTestBasisFunctionBlocks - Builds a per-variable truncated basis expansion (via Embedding) - Constructs the blocks
 * mapping (original var -> list of embedded column indices) - Delegates CI testing to IndTestBlocks over those blocks
 * <p>
 * CONTRACT: 'blocks' maps each ORIGINAL variable index v (0..V-1) to the list of embedded column indices in the
 * embedded matrix produced here.
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
     * Constructs an instance of {@code IndTestBasisFunctionBlocks} to perform block-based
     * conditional independence testing using basis function embedding from a given dataset.
     *
     * @param raw the input dataset, where each variable (node) is represented as a column
     * @param degree the degree of the basis function embedding; must be non-negative
     * @throws IllegalArgumentException if {@code raw} is null or {@code degree} is negative
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
        this.blocksTest = new IndTestBlocks(new BlockSpec(embeddedDs, this.blocks, this.variables));
    }

    // ====== IndependenceTest API ======

    /**
     * Checks for statistical independence between two given variables (nodes),
     * conditioned on a set of other variables.
     *
     * @param x the first variable (node) to test for independence
     * @param y the second variable (node) to test for independence
     * @param z the set of conditioning variables (nodes) for the independence test
     * @return an IndependenceResult containing details about the test result,
     *         including whether the variables are independent, the p-value,
     *         and the significance level used
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
     * Retrieves the list of nodes (variables) associated with this instance.
     * The nodes returned are exactly the ones used internally by the delegate,
     * ensuring identity consistency and preventing "unknown node" issues.
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
     * Indicates whether this instance is operating in verbose mode.
     * Verbose mode allows for detailed output or logging to assist in debugging or monitoring execution.
     *
     * @return true if verbose mode is enabled; false otherwise
     */
    @Override
    public boolean isVerbose() {
        return this.blocksTest.isVerbose();
    }

    /**
     * Sets the verbosity mode for this instance. Enabling verbosity
     * allows for more detailed output or logging during execution.
     *
     * @param verbose true to enable verbose output, false to disable it
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.blocksTest.setVerbose(verbose);
    }

    /**
     * Computes the p-value for statistical tests involving two arrays of data.
     * This method is not supported for block-based tests and will always throw an exception.
     * Use the checkIndependence(Node, Node, Set) method with the dataset instead.
     *
     * @param x the first array of data values
     * @param y the second array of data values
     * @return the p-value as a double
     * @throws UnsupportedOperationException this method is unsupported for block-based tests
     */
    @Override
    public double computePValue(double[] x, double[] y) {
        throw new UnsupportedOperationException(
                "Use checkIndependence(Node,Node,Set) with the dataset; array version is unsupported for block tests.");
    }

    /**
     * Retrieves the alpha value currently set for this instance. The alpha value
     * is typically used as a threshold or parameter in statistical tests or computations.
     *
     * @return the alpha value, which is a double strictly between 0 and 1
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the alpha value used as a threshold or parameter for statistical tests or computations.
     * The alpha value must be within the range (0, 1), exclusive.
     *
     * @param alpha the desired alpha value, strictly between 0 and 1
     * @throws IllegalArgumentException if alpha is less than or equal to 0 or greater than or equal to 1
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    /**
     * Retrieves the list of blocks, where each block is represented as a list of integers.
     * These blocks may correspond to partitions or groupings derived from the data or configuration.
     *
     * @return a list of blocks, with each block being a list of integers
     */
    public List<List<Integer>> getBlocks() {
        return blocks;
    }

    /**
     * Retrieves the embedded data matrix corresponding to the transformed or processed data
     * based on the configuration of this instance.
     *
     * @return the embedded data matrix as a SimpleMatrix object
     */
    public SimpleMatrix getEmbeddedData() {
        return Xphi;
    }
}