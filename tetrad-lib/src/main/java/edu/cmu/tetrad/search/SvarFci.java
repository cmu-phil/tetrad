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

    /**
     * Constructs an instance of the SvarFci class with a specified {@link IndependenceTest}.
     *
     * @param independenceTest The independence test to be used in the SvarFci algorithm. This must not be null.
     * @throws IllegalArgumentException If the independenceTest argument is null.
     */
    public SvarFci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new IllegalArgumentException("IndependenceTest must not be null.");
        }
        this.independenceTest = independenceTest;
    }

    /**
     * Performs a search to produce a graphical model using the SvarFci algorithm.
     * This method utilizes an independence test to conduct the search process,
     * internally employing a default fast adjacency search (FAS) algorithm.
     *
     * @return A {@link Graph} representing the learned model structure.
     * @throws InterruptedException If the search process is interrupted during execution.
     */
    @Override
    public Graph search() throws InterruptedException {
        // If you have a preferred default FAS, use it here; SvarFas is fine too.
        IFas fas = new SvarFas(independenceTest); // or new Fas(independenceTest) if you prefer
        return search(fas);
    }

    /**
     * Performs a search using the specified Fast Adjacency Search (FAS) algorithm
     * to produce a graph structure. This method configures and executes the
     * Fast Causal Inference (FCI) algorithm, leveraging the FAS as input.
     *
     * @param fas The input Fast Adjacency Search (FAS) object to be wrapped
     *            and used in the FCI algorithm. Must not be null.
     * @return A {@link Graph} representing the result of the FCI search.
     * @throws IllegalArgumentException If the provided FAS is null.
     * @throws InterruptedException If the search is interrupted during execution.
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
     * Retrieves the SepsetMap associated with the current instance of the SvarFci class.
     * A SepsetMap maintains information about separating sets determined during the search process.
     *
     * @return The SepsetMap used in the SvarFci algorithm, containing separating sets of nodes.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    // ---------- Controls mirrored to Fci ----------

    /**
     * Retrieves the depth parameter associated with the SvarFci algorithm.
     * The depth parameter typically controls the maximal depth of consideration during the
     * search process in the causal discovery algorithm.
     *
     * @return The depth value currently set for the SvarFci algorithm.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the depth parameter for the SvarFci algorithm.
     * The depth parameter controls the maximal depth of consideration during the
     * search process in the causal discovery algorithm.
     *
     * @param depth The new depth value to be set for the SvarFci algorithm.`
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Retrieves the knowledge object associated with the SvarFci algorithm.
     * The knowledge object contains domain-specific information that guides the search process.
     *
     * @return The knowledge object currently set for the SvarFci algorithm.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge object for the SvarFci algorithm.
     * The knowledge object contains domain-specific information that guides the search process.
     *
     * @param knowledge The new knowledge object to be set for the SvarFci algorithm.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
    }

    /**
     * Retrieves whether the complete rule set is used in the SvarFci algorithm.
     * The complete rule set includes additional rules for causal discovery.
     *
     * @return True if the complete rule set is used, false otherwise.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * Sets whether the SvarFci algorithm uses the complete rule set.
     * The complete rule set includes additional rules that may enhance
     * causal discovery during the algorithm's execution.
     *
     * @param completeRuleSetUsed A boolean value indicating whether the
     *                            complete rule set should be used.
     *                            True enables the use of the complete rule set,
     *                            while false disables it.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Retrieves the maximum length of discriminating paths used in the SvarFci algorithm.
     * Discriminating paths are used to determine causal relationships between variables.
     *
     * @return The maximum length of discriminating paths.
     */
    public int getMaxDiscriminatingPathLength() {
        return maxDiscriminatingPathLength;
    }

    /**
     * Sets the maximum length of discriminating paths used in the SvarFci algorithm.
     * Discriminating paths are utilized to infer causal relationships between variables
     * in the algorithm. This parameter defines the maximum allowable length for such paths.
     *
     * @param maxDiscriminatingPathLength The maximum length of discriminating paths to be set.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Retrieves whether verbose output is enabled for the SvarFci algorithm.
     * Verbose output provides detailed information during the search process.
     *
     * @return True if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is enabled for the SvarFci algorithm.
     * When enabled, detailed information is provided during the search process,
     * which can help in debugging or analyzing the algorithm's behavior.
     *
     * @param verbose A boolean value indicating whether verbose output should be enabled.
     *                True enables verbose output, while false disables it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether the algorithm resolves almost cyclic paths.
     * This setting determines the behavior of the algorithm when handling paths
     * that are nearly cyclic, influencing the resulting graphical structure.
     *
     * @param resolveAlmostCyclicPaths A boolean indicating whether to resolve almost cyclic paths.
     *                                 True enables this feature, while false disables it.
     */
    public void setResolveAlmostCyclicPaths(boolean resolveAlmostCyclicPaths) {
        this.resolveAlmostCyclicPaths = resolveAlmostCyclicPaths;
    }

    /**
     * Sets whether the SvarFci algorithm guarantees the constraint-based Partially
     * Oriented Acyclic Graph (PAG) will be oriented according to specific rules.
     * This setting determines the enforcement of guaranteed properties in the resulting graph.
     *
     * @param guaranteePag A boolean value indicating whether to guarantee PAG orientation.
     *                     True enables the guarantee, while false disables it.
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

        /**
         * Constructs a SvarWrapperFas that wraps the given IFas.
         * The SvarWrapperFas ensures that after building the skeleton,
         * the returned graph is a SvarEdgeListGraph while delegating
         * all other behavior, such as sepsets and parameters, to the wrapped IFas.
         *
         * @param base the IFas instance to be wrapped
         */
        SvarWrapperFas(IFas base) {
            this.base = base;
        }

        /**
         * Executes a search operation on the underlying IFas instance to construct the skeleton
         * of a graph and wraps the resulting graph as a SvarEdgeListGraph, ensuring SVAR mirroring
         * starting from the returned graph. Delegates the actual skeleton search to the wrapped IFas instance.
         *
         * @return a SvarEdgeListGraph instance wrapping the skeleton generated by the underlying IFas instance
         * @throws InterruptedException if the thread executing the search is interrupted
         */
        @Override
        public Graph search() throws InterruptedException {
            Graph skeleton = base.search();           // build skeleton as usual
            return new SvarEdgeListGraph(skeleton);   // ensure SVAR mirroring from here on
        }

        /**
         * Retrieves the separation sets (sepsets) from the underlying IFas instance.
         * A sepset represents a set of nodes that separate two other nodes in the context of
         * conditional independence tests during skeleton construction.
         *
         * @return a SepsetMap containing the separation sets as determined by the underlying IFas instance
         */
        @Override
        public SepsetMap getSepsets() {
            return base.getSepsets();
        }

        /**
         * Sets the knowledge in the underlying IFas instance.
         *
         * @param knowledge the Knowledge object to be applied, which may include constraints
         *                  or prior information guiding the behavior of the underlying IFas instance
         */
        @Override
        public void setKnowledge(Knowledge knowledge) {
            base.setKnowledge(knowledge);
        }

        /**
         * Sets the depth parameter in the underlying IFas instance. The depth represents
         * the maximum number of edges allowed between two nodes for certain operations
         * in the causal structure learning process.
         *
         * @param depth the maximum number of edges allowed between nodes, where a value
         *              of -1 generally indicates no limit on the depth
         */
        @Override
        public void setDepth(int depth) {
            base.setDepth(depth);
        }

        /**
         * Sets the verbose mode for the underlying IFas instance.
         * The verbose mode determines whether detailed output or logging is enabled
         * during the execution of operations within the wrapped IFas instance.
         *
         * @param verbose true to enable verbose mode, false to disable it
         */
        @Override
        public void setVerbose(boolean verbose) {
            base.setVerbose(verbose);
        }

        /**
         * Sets the stability mode for the underlying IFas instance.
         * Stability mode ensures consistent behavior in the algorithm's execution,
         * possibly impacting the reproducibility of results or the handling of causal
         * structure search processes.
         *
         * @param stable true to enable stability mode, false to disable it
         */
        @Override
        public void setStable(boolean stable) {
            base.setStable(stable);
        }
    }
}
