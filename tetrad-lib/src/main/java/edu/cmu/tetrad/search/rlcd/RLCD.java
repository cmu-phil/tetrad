package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IGraphSearch;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * <p><b>Rank-based Latent Causal Discovery (RLCD).</b></p>
 *
 * <p>Port of Dong et&nbsp;al., “<i>A Versatile Causal Discovery Framework to Allow
 * Causally-Related Hidden Variables</i>,” ICLR&nbsp;2024.</p>
 *
 * <p>This implementation follows the three-phase structure described in the paper:</p>
 *
 * <ul>
 *   <li><b>Phase 1:</b> Find a conditional-independence (CI) skeleton
 *       (typically via FGES, PC, or a comparable method).</li>
 *   <li><b>Phase 2:</b> Identify latent clusters within overlapping clique groups.</li>
 *   <li><b>Phase 3:</b> Refine clusters and orient remaining edges.</li>
 * </ul>
 *
 * <p>Currently, only Phase&nbsp;1 (skeleton construction and partitioning nodes
 * into overlapping clique groups) is fully implemented. Phases&nbsp;2 and&nbsp;3
 * are included as stubs and may be extended to match the Python reference
 * implementation in <code>scm-identify</code>.</p>
 */
public final class RLCD implements IGraphSearch, Serializable {

    @Serial
    private static final long serialVersionUID = 42L;

    /**
     * Represents the dataset on which the RLCD causal discovery process operates.
     * This field is initialized during the construction of an RLCD instance and
     * must not be null. It is used as the primary source of data for causal
     * structure learning.
     */
    private final DataSet dataSet;
    /**
     * Represents the parameters used to configure the RLCD causal discovery process.
     * This field is immutable and is initialized during the construction of the RLCD instance.
     * If no parameters are provided during construction, default parameters are used.
     *
     * The {@code params} object influences the behavior of various phases of the
     * RLCD discovery process, such as the construction of the CI skeleton, and may also
     * contain settings for latent cluster identification or edge orientation (if applicable).
     */
    private final RLCDParams params;

    /**
     * Constructs an instance of the RLCD class. This constructor initializes
     * the RLCD causal discovery process with the specified dataset and parameters.
     *
     * @param dataSet the dataset on which the RLCD causal discovery process will operate.
     *                This parameter must not be null; otherwise, an IllegalArgumentException is thrown.
     * @param params  the parameters for configuring the RLCD causal discovery process.
     *                If this parameter is null, default parameters will be used instead.
     */
    public RLCD(DataSet dataSet, RLCDParams params) {
        if (dataSet == null) {
            throw new IllegalArgumentException("dataSet must not be null.");
        }
        this.dataSet = dataSet;
        this.params = params != null ? params : new RLCDParams();
    }

    /**
     * Executes the RLCD causal discovery process and returns the resulting graph.
     * This method acts as a shortcut for invoking the {@link #search(boolean)}
     * method with the default behavior.
     *
     * @return a {@code Graph} object representing the output of the RLCD causal
     *         discovery process. The returned graph encapsulates the results of
     *         the causal structure learning performed by this method.
     */
    @Override
    public Graph search() {
        return search(false);
    }

    /**
     * <p>Executes a three-phase causal discovery process and returns the resulting graph.</p>
     *
     * <p>The process consists of the following phases:</p>
     *
     * <ol>
     *   <li><b>Phase 1:</b> Construct a conditional independence (CI) skeleton or
     *       score-based skeleton (e.g., using the FGES or PC algorithm).</li>
     *   <li><b>Phase 2:</b> Identify latent clusters within overlapping clique groups
     *       (currently stubbed).</li>
     *   <li><b>Phase 3:</b> Refine clusters and orient remaining edges
     *       (currently stubbed).</li>
     * </ol>
     *
     * <p>Optionally, the method can return only the results of Phase&nbsp;1.</p>
     *
     * @param returnStage1Only a boolean flag indicating whether to return only the
     *                         result of Phase&nbsp;1 (the CI skeleton). If {@code true},
     *                         the method returns the CI skeleton constructed in Phase&nbsp;1.
     *                         If {@code false}, it proceeds to Phases&nbsp;2 and&nbsp;3 and
     *                         returns the final graph with additional latent discovery steps.
     * @return a {@code Graph} object representing the output of the discovery process.
     *         If {@code returnStage1Only} is {@code true}, only the CI skeleton
     *         (Phase&nbsp;1 result) is returned. If {@code false}, the method returns
     *         the full graph resulting from Phases&nbsp;2 and&nbsp;3 (if implemented).
     */
    public Graph search(boolean returnStage1Only) {
        // Phase 1: CI skeleton or score-based skeleton (FGES/PC/etc.).
        Phase1Result phase1 = Phase1.runPhase1(dataSet, params);

        if (returnStage1Only) {
            return phase1.getSkeleton();
        }

        // Phase 2 & 3: latent cluster discovery (stubbed for now).
        LatentDiscoveryResult latentResult =
                LatentDiscovery.runPhase2And3(dataSet, phase1, params);

        return latentResult.toTetradGraph();
    }

    /**
     * Retrieves the parameters associated with the RLCD causal discovery process.
     *
     * @return an instance of {@code RLCDParams} containing the configuration
     *         parameters for running the RLCD algorithm.
     */
    public RLCDParams getParams() {
        return params;
    }

    /**
     * Retrieves the dataset associated with the RLCD process.
     *
     * @return an instance of {@code DataSet} representing the data used by the RLCD algorithm.
     */
    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * Executes Phase 1 of the RLCD causal discovery process.
     * Phase 1 involves constructing a conditional independence (CI) skeleton and
     * identifying clique partitions based on the provided dataset and parameters.
     *
     * @return a {@code Phase1Result} object containing the skeleton graph and
     *         clique partitions derived in Phase 1 of the RLCD discovery process.
     */
    public Phase1Result debugPhase1() {
        return Phase1.runPhase1(dataSet, params);
    }
}