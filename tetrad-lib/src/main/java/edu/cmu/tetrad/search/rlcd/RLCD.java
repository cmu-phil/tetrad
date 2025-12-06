package edu.cmu.tetrad.search.rlcd;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IGraphSearch;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Rank-based Latent Causal Discovery (RLCD).
 *
 * Port of: Dong et al., "A Versatile Causal Discovery Framework to Allow
 * Causally-Related Hidden Variables", ICLR 2024.
 *
 * This implementation follows the three-phase structure:
 *  - Phase 1: Find CI skeleton (using FGES/PC/etc.).
 *  - Phase 2: Find latent clusters within overlapping clique groups.
 *  - Phase 3: Refine clusters and orient remaining edges.
 *
 * For now, only Phase 1 (skeleton + partition into clique groups) is fully
 * implemented; Phases 2 and 3 are stubbed and can be filled in to match
 * the Python reference implementation in scm-identify.
 */
public final class RLCD implements IGraphSearch, Serializable {

    @Serial
    private static final long serialVersionUID = 42L;

    private final DataSet dataSet;
    private final RLCDParams params;

    public RLCD(DataSet dataSet, RLCDParams params) {
        if (dataSet == null) {
            throw new IllegalArgumentException("dataSet must not be null.");
        }
        this.dataSet = dataSet;
        this.params = params != null ? params : new RLCDParams();
    }

    @Override
    public Graph search() {
        return search(false);
    }

    /**
     * @param returnStage1Only if true, returns just the Phase-1 skeleton
     *                         (no latent variables), useful for debugging.
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

    public RLCDParams getParams() {
        return params;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public Phase1Result debugPhase1() {
        return Phase1.runPhase1(dataSet, params);
    }
}