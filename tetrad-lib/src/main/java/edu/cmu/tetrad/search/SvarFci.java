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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.SvarEdgeListGraph;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetMap;

/**
 * SVAR wrapper for FCI: runs the standard FCI on a SvarEdgeListGraph so that all edge mutations mirror across lags
 * automatically.
 * <p>
 * This class is intentionally thin: it delegates to the latest Fci implementation and only ensures the working graph
 * inside Fci is a SvarEdgeListGraph.
 */
public final class SvarFci implements IGraphSearch {

    private final IndependenceTest independenceTest;

    // Controls mirrored to Fci
    private Knowledge knowledge = new Knowledge();
    private int depth = -1;
    private boolean completeRuleSetUsed = false;
    private int maxDiscriminatingPathLength = -1;
    private boolean verbose = false;
    private boolean resolveAlmostCyclicPaths = false; // forwarded if supported by Fci
    private boolean guaranteePag = false;             // forwarded if supported by Fci

    // outputs
    private SepsetMap sepsets;

    public SvarFci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new IllegalArgumentException("IndependenceTest must not be null.");
        }
        this.independenceTest = independenceTest;
    }

    @Override
    public Graph search() throws InterruptedException {
        // If you have a preferred default FAS, use it here; SvarFas is fine too.
        IFas fas = new SvarFas(independenceTest); // or new Fas(independenceTest) if you prefer
        return search(fas);
    }

    /**
     * Runs FCI using the provided FAS, but ensures the skeleton graph that FCI receives is a SvarEdgeListGraph (so all
     * subsequent operations mirror across lags).
     */
    public Graph search(IFas fas) throws InterruptedException {
        if (fas == null) throw new IllegalArgumentException("FAS must not be null.");

        // 1) Wrap the FAS so its skeleton is converted into a SvarEdgeListGraph.
        IFas svarFas = new SvarWrapperFas(fas);

        // 2) Configure a fresh FCI with the latest implementation.
        Fci fci = new Fci(this.independenceTest);
        fci.setKnowledge(this.knowledge);
        fci.setDepth(this.depth);
        fci.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fci.setMaxDiscriminatingPathLength(this.maxDiscriminatingPathLength);
        fci.setVerbose(this.verbose);

        // Optional flags if available on your Fci build (guard with try so it compiles across minor versions)
//        try { fci.setResolveAlmostCyclicPaths(this.resolveAlmostCyclicPaths); } catch (Throwable ignore) {}
//        try { fci.setGuaranteePag(this.guaranteePag); } catch (Throwable ignore) {}

        // 3) Run the standard FCI over the SVAR graph.
        Graph result = fci.search(svarFas);

        // 4) Expose sepsets from the underlying (wrapped) FAS.
        this.sepsets = svarFas.getSepsets();

        return result;
    }

    /**
     * Exposes the SepsetMap computed during FAS.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    // ---------- Controls mirrored to Fci ----------

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
    }

    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public int getMaxDiscriminatingPathLength() {
        return maxDiscriminatingPathLength;
    }

    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Whether to run the almost-cycle resolution pass after orientation (if supported by your Fci build).
     */
    public void setResolveAlmostCyclicPaths(boolean resolveAlmostCyclicPaths) {
        this.resolveAlmostCyclicPaths = resolveAlmostCyclicPaths;
    }

    /**
     * Whether to guarantee a PAG (if supported by your Fci build).
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    // ---------- Helper: FAS wrapper that re-homes the skeleton into a SvarEdgeListGraph ----------

    /**
     * Wraps an IFas so that after building the skeleton it returns a SvarEdgeListGraph. All other behavior (sepsets,
     * parameters) is delegated unchanged.
     */
    private static final class SvarWrapperFas implements IFas {
        private final IFas base;

        SvarWrapperFas(IFas base) {
            this.base = base;
        }

        @Override
        public Graph search() throws InterruptedException {
            Graph skeleton = base.search();           // build skeleton as usual
            return new SvarEdgeListGraph(skeleton);   // ensure SVAR mirroring from here on
        }

        @Override
        public SepsetMap getSepsets() {
            return base.getSepsets();
        }

        @Override
        public void setKnowledge(Knowledge knowledge) {
            base.setKnowledge(knowledge);
        }

        @Override
        public void setDepth(int depth) {
            base.setDepth(depth);
        }

        @Override
        public void setVerbose(boolean verbose) {
            base.setVerbose(verbose);
        }

        @Override
        public void setStable(boolean stable) {
            base.setStable(stable);
        }
    }
}
