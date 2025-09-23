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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4Strategy;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FCI-CPW: Run FCI with internally constructed PW-forbidden knowledge, then orient edges
 * using a pairwise left-right rule on standardized data in cases that are safe under
 * no-selection-bias cyclic semantics:
 *
 * <ol>
 *   <li>Tail–tail (—) edges
 *     <ul><li>Orient per pairwise rule.</li></ul>
 *   </li>
 *   <li>Tail–circle (—o) edges
 *     <ul><li>If pairwise prefers x→y, set x→y (symmetrically, if prefers y→x, set y→x).</li></ul>
 *   </li>
 *   <li>Circle–circle (o–o) edges
 *     <ul><li>If pairwise prefers x→y, set x o→y (symmetrically for y→x).</li></ul>
 *   </li>
 * </ol>
 *
 * <p>We never alter &lt;-&gt; (two heads), never flip existing tails/heads,
 * and never touch o→ or ←o edges.</p>
 *
 * <p><b>Parameter:</b> PAIRWISE_RULE ∈ {1..5}, default 3 (RSKEW).
 * 1=FASK1, 2=FASK2, 3=RSKEW, 4=SKEW, 5=TANH.</p>
 */
@edu.cmu.tetrad.annotation.Algorithm(name = "FCI-CPW", command = "fci-cpw", algoType = AlgType.allow_latent_common_causes)
@Bootstrapping
public class FciCyclicPw extends AbstractBootstrapAlgorithm implements Algorithm, TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Optional name for pairwise rule param (read if present).
     */
    private static final String PARAM_PAIRWISE_RULE = "PAIRWISE_RULE";

    /**
     * Independence test wrapper (same as FCI wrapper).
     */
    private IndependenceWrapper test;

    /**
     * Default constructor for the FciCyclicPw class.
     * This constructor initializes the instance without any specific parameters
     * or configurations. It is typically used for*/
    public FciCyclicPw() {
    }

    /**
     * Constructor for the FciCyclicPw class that initializes the algorithm with a specified
     * independence test wrapper.
     *
     * @param test The IndependenceWrapper instance used for conditional independence testing
     *             during the FCI algorithm execution.
     */
    public FciCyclicPw(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Executes the search algorithm on a given data model and set of parameters,
     * producing a partially directed acyclic graph (PAG) that represents the
     * causal structure inferred from the data.
     *
     * The underlying functionality includes handling time-lagged data, standardizing,
     * generating internal knowledge, performing conditional independence tests,
     * and refining the graph using pairwise adjustments based on standardization and rules.
     *
     * @param dataModel The data model to analyze, typically a {@link DataSet}, which
     *                  contains the data from which causal relationships are inferred.
     *                  Must be continuous for proper functioning.
     * @param parameters Algorithm parameter settings that control various aspects of the
     *                   computation, such as time lagging, collider orientation style,
     *                   pairwise rules, and limits on graph structure exploration.
     * @return A {@link Graph} representing the causal structure inferred by the search algorithm,
     *         encoded as a PAG.
     * @throws InterruptedException If the search process is interrupted during execution,
     *                              possibly due to thread interruption.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        // --- Handle time-lagging exactly as in Fci wrapper ---
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }
            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) timeSeries.setName(dataSet.getName());
            dataModel = timeSeries;
        }

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("FCI-CPW expects a DataSet.");
        }
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("FCI-CPW currently supports linear skewed data (skewed).");
        }

        // Pairwise rule: default 3 (RSKEW). Read PARAM_PAIRWISE_RULE if provided.
        int pwRule = 3;
        try {
            pwRule = parameters.getInt(PARAM_PAIRWISE_RULE);
        } catch (Throwable ignored) {
            // keep default 3
        }
        if (pwRule < 1 || pwRule > 5) pwRule = 3;

        boolean verbose = parameters.getBoolean(Params.VERBOSE);

        // Standardize once; reuse for knowledge + all pairwise decisions
        DataSet z = DataTransforms.standardizeData(dataSet);
        double[][] data = z.getDoubleData().transpose().toArray(); // vars x N
        List<Node> nodes = z.getVariables();

        // Fast name->index map (robust across Node instance identity)
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int k = 0; k < nodes.size(); k++) nameToIdx.put(nodes.get(k).getName(), k);

        // --- Phase 0: Build PW-forbidden knowledge (internal only) ---
        Knowledge internalKnowledge = buildPwForbiddenKnowledge(data, nodes, pwRule, verbose);

        // --- Phase 1: Run FCI with that knowledge ---
        edu.cmu.tetrad.search.Fci.ColliderRule colliderOrientationStyle = switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
            case 1 -> edu.cmu.tetrad.search.Fci.ColliderRule.SEPSETS;
            case 2 -> edu.cmu.tetrad.search.Fci.ColliderRule.CONSERVATIVE;
            case 3 -> edu.cmu.tetrad.search.Fci.ColliderRule.MAX_P;
            default -> throw new IllegalArgumentException("Invalid collider orientation style");
        };

        edu.cmu.tetrad.search.Fci fci = new edu.cmu.tetrad.search.Fci(this.test.getTest(dataModel, parameters));
        fci.setDepth(parameters.getInt(Params.DEPTH));
        fci.setR0ColliderRule(colliderOrientationStyle);
        fci.setKnowledge(internalKnowledge);
        fci.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        fci.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        fci.setDoPossibleDsep(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
        fci.setVerbose(verbose);
        fci.setStable(parameters.getBoolean(Params.STABLE_FAS));
//        fci.setGuaranteePag(parameters.getBoolean(Params.GUARANTEE_PAG));

        Graph pag;
        double fdrQ = parameters.getDouble(Params.FDR_Q);
        if (fdrQ == 0.0) {
            pag = fci.search();
        } else {
            boolean negativelyCorrelated = true;
            double alpha = parameters.getDouble(Params.ALPHA);
            pag = IndTestFdrWrapper.doFdrLoop(fci, negativelyCorrelated, alpha, fdrQ, verbose);
        }

        // --- Phase 2a: Orient tail–tail (—) edges using PW left-right on standardized data ---
        for (Edge e : new ArrayList<>(pag.getEdges())) { // snapshot to allow mutation
            Node x = e.getNode1();
            Node y = e.getNode2();

            Integer ix = nameToIdx.get(x.getName());
            Integer iy = nameToIdx.get(y.getName());
            if (ix == null || iy == null) continue; // defensive: mismatch

            double diff = Fask.leftRightDiff(data[ix], data[iy], pwRule);

            if (Edges.isUndirectedEdge(e)) { // x — y
                pag.removeEdge(x, y);
                if (diff > 0) {
                    pag.addDirectedEdge(x, y);  // x → y
                    if (verbose) TetradLogger.getInstance().log("CPW — : " + x + "→" + y + " (diff=" + diff + ")");
                } else {
                    pag.addDirectedEdge(y, x);  // y → x
                    if (verbose) TetradLogger.getInstance().log("CPW — : " + y + "→" + x + " (diff=" + diff + ")");
                }
            }
        }

        // --- Phase 2b: Tail–circle (—o) and circle–tail (o—) safe refinements ---
        for (Edge e : new ArrayList<>(pag.getEdges())) { // snapshot again; we'll mutate
            Node x = e.getNode1();
            Node y = e.getNode2();

            Endpoint exy = pag.getEndpoint(x, y); // endpoint at y from x
            Endpoint eyx = pag.getEndpoint(y, x); // endpoint at x from y

            Integer ix = nameToIdx.get(x.getName());
            Integer iy = nameToIdx.get(y.getName());
            if (ix == null || iy == null) continue;

            double diff = Fask.leftRightDiff(data[ix], data[iy], pwRule);

            // Case: x — o y  (TAIL at x→y; CIRCLE at y→x)
            if (exy == Endpoint.TAIL && eyx == Endpoint.CIRCLE) {
                if (diff > 0) { // x → y preferred
                    pag.removeEdge(x, y);
                    pag.addDirectedEdge(x, y);
                    if (verbose) TetradLogger.getInstance().log("CPW —o: " + x + "→" + y + " (diff=" + diff + ")");
                }
                continue;
            }

            // Case: x o — y  (CIRCLE at x→y; TAIL at y→x)
            if (exy == Endpoint.CIRCLE && eyx == Endpoint.TAIL) {
                if (diff < 0) { // y → x preferred
                    pag.removeEdge(x, y);
                    pag.addDirectedEdge(y, x);
                    if (verbose) TetradLogger.getInstance().log("CPW o—: " + y + "→" + x + " (diff=" + diff + ")");
                }
            }

            // Case x o-o y
            if (eyx == Endpoint.CIRCLE && exy == Endpoint.CIRCLE) {
                if (diff > 0) {
                    pag.setEndpoint(x, y, Endpoint.ARROW);
                } else {
                    pag.setEndpoint(y, x, Endpoint.ARROW);
                }
            }
        }

        return pag;
    }

    // --------------------------- Internals ---------------------------

    /**
     * Build forbidden knowledge from standardized data using pairwise left-right: For each pair (i,j), if
     * diff(i,j,pwRule) > 0 forbid j->i; else forbid i->j. (No thresholding.)
     */
    private Knowledge buildPwForbiddenKnowledge(double[][] data, List<Node> nodes, int pwRule, boolean verbose) {
        Knowledge knowledge = new Knowledge();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node xi = nodes.get(i);
                Node yj = nodes.get(j);

                double diff = Fask.leftRightDiff(data[i], data[j], pwRule);

                if (diff > 0) {
                    // prefer xi -> yj  ⇒ forbid yj -> xi
                    knowledge.setForbidden(yj.getName(), xi.getName());
                    if (verbose)
                        TetradLogger.getInstance().log("CPW-K: forbid " + yj + "→" + xi + " (prefer " + xi + "→" + yj + ", diff=" + diff + ")");
                } else {
                    // prefer yj -> xi  ⇒ forbid xi -> yj
                    knowledge.setForbidden(xi.getName(), yj.getName());
                    if (verbose)
                        TetradLogger.getInstance().log("CPW-K: forbid " + xi + "→" + yj + " (prefer " + yj + "→" + xi + ", diff=" + diff + ")");
                }
            }
        }
        return knowledge;
    }

    // --------------------------- Boilerplate parity with Fci ---------------------------

    /**
     * Generates a comparison graph for the given graph by transforming it into
     * a partially directed acyclic graph (PAG) representation.
     *
     * @param graph The input {@link Graph} to be transformed into a comparison graph.
     *              This graph serves as the basis for creating the resulting PAG.
     * @return A {@link Graph} representing the transformed partially directed
     *         acyclic graph (PAG) based on the input graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /**
     * Provides a textual description of the FCI-CPW algorithm, including its
     * functionality and distinguishing features, such as the use of pairwise-derived
     * forbidden knowledge and pairwise orientation for certain edges.
     *
     * @return A string describing the FCI-CPW algorithm and its characteristics.
     */
    @Override
    public String getDescription() {
        return "FCI-CPW: FCI with pairwise-derived forbidden knowledge and pairwise orientation of —, —o, and o— edges (rule selectable)";
    }

    /**
     * Retrieves the data type associated with the current instance of the algorithm.
     * The data type defines whether the dataset is continuous, discrete, mixed, or another recognized type.
     *
     * @return The {@link DataType} representing the type of dataset required or handled by the algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves a list of parameter names required for the configuration of the
     * FCI-Cyclic-PW algorithm. These parameters control various aspects of the
     * algorithm's execution, such as graph exploration limits, orientation styles,
     * and additional settings affecting the causal inference process.
     *
     * @return A list of strings, where each string represents a parameter name
     *         used by the FCI-Cyclic-PW algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
        parameters.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        parameters.add(Params.DO_POSSIBLE_DSEP);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.TIME_LAG);
//        parameters.add(Params.GUARANTEE_PAG);
        parameters.add(Params.VERBOSE);
        // Note: PAIRWISE_RULE is read if provided; not registered as a Params constant here.
        return parameters;
    }

    /**
     * Retrieves the current instance of the {@link IndependenceWrapper} used for
     * conditional independence testing in the algorithm.
     *
     * @return The {@link IndependenceWrapper} instance being used by this algorithm.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the {@link IndependenceWrapper} instance used for conditional
     * independence testing in the algorithm.
     *
     * @param test The IndependenceWrapper instance to be used for testing
     *             conditional independence relations.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}