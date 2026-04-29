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
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
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
 * Implements the CPW (Causal Pairwise) algorithm for causal discovery from
 * continuous, causally sufficient observational data. CPW combines FCI with
 * a pairwise left-right orientation rule to resolve edge directions that FCI
 * leaves ambiguous, under the assumption of no latent common causes and no
 * selection bias.
 *
 * <p>The algorithm proceeds iteratively until the PAG stabilizes:
 * <ol>
 *   <li><b>Pairwise forbidden knowledge construction.</b> For every pair of
 *       variables (X, Y), a pairwise left-right statistic is computed on
 *       standardized data. If the statistic favors X → Y, the reverse edge
 *       Y → X is added to a forbidden knowledge object. This internal
 *       knowledge is passed to FCI to bias skeleton orientation without
 *       hard-constraining the independence tests.</li>
 *   <li><b>FCI search.</b> FCI is run with the pairwise forbidden knowledge,
 *       producing a PAG. The collider orientation strategy (SEPSETS,
 *       CONSERVATIVE, or MAX_P) and other FCI parameters are configurable.
 *       Optionally an FDR correction loop is applied to the independence
 *       tests.</li>
 *   <li><b>Pairwise edge orientation.</b> Edges left ambiguous by FCI are
 *       further oriented using the pairwise left-right rule on standardized
 *       data, subject to the following case analysis:
 *       <ul>
 *         <li><b>Tail–tail (—):</b> oriented fully as X → Y or Y → X per
 *             the pairwise statistic.</li>
 *         <li><b>Tail–circle (—o) or circle–tail (o—):</b> the circle end
 *             is resolved to a tail or arrow if the pairwise statistic
 *             provides clear evidence.</li>
 *         <li><b>Circle–circle (o–o):</b> one circle end is converted to an
 *             arrow in the preferred direction.</li>
 *         <li><b>Circle–arrow (o→) or arrow–circle (←o):</b> under causal
 *             sufficiency, the circle is resolved to a tail.</li>
 *       </ul>
 *       Bidirected (↔) edges are never altered, and existing tails and
 *       arrows are never flipped.</li>
 * </ol>
 *
 * <p>The pairwise left-right rule is selected via the {@code PAIRWISE_RULE}
 * parameter (1=FASK1, 2=FASK2, 3=RSKEW, 4=SKEW, 5=TANH; default 2).
 * Time-lagged data is supported via the {@code TIME_LAG} parameter, which
 * prepends lag columns before search.
 *
 * <p>This algorithm is marked {@link Experimental} and is intended for
 * linear, non-Gaussian, causally sufficient data.
 *
 * @see edu.cmu.tetrad.search.Fci
 * @see edu.cmu.tetrad.search.Fask
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CPW",
        command = "cpw",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Continuous
)
@Bootstrapping
@Experimental
public class Cpw extends AbstractBootstrapAlgorithm implements Algorithm, TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Independence test wrapper.
     */
    private IndependenceWrapper test;

    /**
     * Default constructor for the Cpw class.
     * This constructor initializes the instance without any specific parameters
     * or configurations. It is typically used for
     */
    public Cpw() {
    }

    /**
     * Constructor for the Cpw class that initializes the algorithm with a specified
     * independence test wrapper.
     *
     * @param test The IndependenceWrapper instance used for conditional independence testing.
     */
    public Cpw(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Executes the search algorithm on a given data model and set of parameters,
     * producing a partially directed acyclic graph (PAG) that represents the
     * causal structure inferred from the data.
     * <p>
     * The underlying functionality includes handling time-lagged data, standardizing,
     * generating internal knowledge, performing conditional independence tests,
     * and refining the graph using pairwise adjustments based on standardization and rules.
     *
     * @param dataModel  The data model to analyze, typically a {@link DataSet}, which
     *                   contains the data from which causal relationships are inferred.
     *                   Must be continuous for proper functioning.
     * @param parameters Algorithm parameter settings that control various aspects of the
     *                   computation, such as time lagging, collider orientation style,
     *                   pairwise rules, and limits on graph structure exploration.
     * @return A {@link Graph} representing the causal structure inferred by the search algorithm,
     * encoded as a PAG.
     * @throws InterruptedException If the search process is interrupted during execution,
     *                              possibly due to thread interruption.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }
            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) timeSeries.setName(dataSet.getName());
            dataModel = timeSeries;
        }

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("CPW expects a DataSet.");
        }
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("CPW currently supports linear data (skewed).");
        }

        // Default rule is FASK 2 from harness.
        int pwRule = parameters.getInt(Params.FASK_LEFT_RIGHT_RULE, 2);
        boolean verbose = parameters.getBoolean(Params.VERBOSE);

        // Standardize once; reuse for knowledge + all pairwise decisions
        DataSet z = DataTransforms.standardizeData(dataSet);
        double[][] data = z.getDoubleData().transpose().toArray(); // vars x N
        List<Node> nodes = z.getVariables();

        // Fast name->index map (robust across Node instance identity)
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int k = 0; k < nodes.size(); k++) nameToIdx.put(nodes.get(k).getName(), k);

        Graph pag = new EdgeListGraph();

        Graph _pag;

        do {
            _pag = new EdgeListGraph(pag);

            // --- Phase 0: Build PW-forbidden knowledge (internal only) ---
            Knowledge internalKnowledge = buildPwForbiddenKnowledge(pwRule, pag, nodes, data, verbose);

            // --- Phase 1: Run FCI with that knowledge ---
            edu.cmu.tetrad.search.Fci.ColliderRule colliderOrientationStyle = switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
                case 1 -> edu.cmu.tetrad.search.Fci.ColliderRule.SEPSETS;
                case 2 -> edu.cmu.tetrad.search.Fci.ColliderRule.CONSERVATIVE;
                case 3 -> edu.cmu.tetrad.search.Fci.ColliderRule.MAX_P;
                default -> throw new IllegalArgumentException("Invalid collider orientation style");
            };

            IndependenceTest test1 = this.test.getTest(dataModel, parameters);
            test1 = new CachedIndependenceQueries(test1);
            edu.cmu.tetrad.search.Fci fci = new edu.cmu.tetrad.search.Fci(test1);
            fci.setDepth(parameters.getInt(Params.DEPTH));
            fci.setR0ColliderRule(colliderOrientationStyle);
            fci.setKnowledge(internalKnowledge);
            fci.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
            fci.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            fci.setDoPossibleDsep(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
            fci.setVerbose(verbose);
            fci.setStable(parameters.getBoolean(Params.STABLE_FAS));

            double fdrQ = parameters.getDouble(Params.FDR_Q);
            if (fdrQ == 0.0) {
                pag = fci.search();
            } else {
                boolean negativelyCorrelated = true;
                double alpha = parameters.getDouble(Params.ALPHA);
                pag = IndTestFdrWrapper.doFdrLoop(fci, negativelyCorrelated, alpha, fdrQ, verbose);
            }

            for (int s = 0; s < 2; s++) {
                for (Edge e : new ArrayList<>(pag.getEdges())) {
                    Node x = e.getNode1();
                    Node y = e.getNode2();

                    Endpoint exy = pag.getEndpoint(x, y); // endpoint at y
                    Endpoint eyx = pag.getEndpoint(y, x); // endpoint at x

                    Integer ix = nameToIdx.get(x.getName());
                    Integer iy = nameToIdx.get(y.getName());
                    if (ix == null || iy == null) continue;

                    double diff = Fask.leftRightDiff(data[ix], data[iy], pwRule);

                    // Catch both true undirected edges AND tail-tail edges from FCI
                    if (exy == Endpoint.TAIL && eyx == Endpoint.TAIL) {
                        pag.removeEdge(x, y);
                        if (diff > 0) {
                            pag.addDirectedEdge(x, y);
                            if (verbose) TetradLogger.getInstance().log("CPW — : " + x + "→" + y + " (diff=" + diff + ")");
                        } else {
                            pag.addDirectedEdge(y, x);
                            if (verbose) TetradLogger.getInstance().log("CPW — : " + y + "→" + x + " (diff=" + diff + ")");
                        }
                    }

                    // Case: x —o y  (tail at x, circle at y)
                    // exy = endpoint at y = CIRCLE; eyx = endpoint at x = TAIL
                    if (exy == Endpoint.CIRCLE && eyx == Endpoint.TAIL) {
                        if (diff > 0) {
                            pag.removeEdge(x, y);
                            pag.addDirectedEdge(x, y);
                            if (verbose)
                                TetradLogger.getInstance().log("CPW —o: " + x + "-->" + y + " (diff=" + diff + ")");
                        } else {
                            pag.removeEdge(x, y);
                            pag.addUndirectedEdge(x, y);
                            if (verbose)
                                TetradLogger.getInstance().log("CPW —o: " + x + "---" + y + " (diff=" + diff + ")");

                        }
                    }

                    // Case: x o— y  (circle at x, tail at y)
                    // exy = endpoint at y = TAIL; eyx = endpoint at x = CIRCLE
                    if (exy == Endpoint.TAIL && eyx == Endpoint.CIRCLE) {
                        if (diff < 0) {
                            pag.removeEdge(x, y);
                            pag.addDirectedEdge(y, x);
                            if (verbose)
                                TetradLogger.getInstance().log("CPW o—: " + y + "→" + x + " (diff=" + diff + ")");
                        } else {
                            pag.removeEdge(y, x);
                            pag.addUndirectedEdge(y, x);
                            if (verbose)
                                TetradLogger.getInstance().log("CPW —o: " + x + "---" + y + " (diff=" + diff + ")");
                        }
                    }

                    // Case: x o-o y
                    if (exy == Endpoint.CIRCLE && eyx == Endpoint.CIRCLE) {
                        if (diff > 0) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                        } else {
                            pag.setEndpoint(y, x, Endpoint.ARROW);
                        }
                    }

                    // Case: x o-> y; causally sufficient → x --> y
                    // exy = ARROW (at y), eyx = CIRCLE (at x)
                    if (exy == Endpoint.ARROW && eyx == Endpoint.CIRCLE) {
                        pag.setEndpoint(y, x, Endpoint.TAIL);
                    }

                    // Case: x <-o y; causally sufficient → x <-- y
                    // exy = CIRCLE (at y), eyx = ARROW (at x)
                    if (exy == Endpoint.CIRCLE && eyx == Endpoint.ARROW) {
                        pag.setEndpoint(x, y, Endpoint.TAIL);
                    }
                }
            }
        } while (!pag.equals(_pag));

        return pag;
    }

    // --------------------------- Internals ---------------------------

    /**
     * Build forbidden knowledge from standardized data using pairwise left-right: For each pair (i,j), if
     * diff(i,j,pwRule) > 0 forbid j->i; else forbid i->j. (No thresholding.)
     */
    private Knowledge buildPwForbiddenKnowledge(int pwRule, Graph graph,
                                                List<Node> nodes, double[][] data, boolean verbose) {
        Knowledge knowledge = new Knowledge();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node xi = nodes.get(i);
                Node xj = nodes.get(j);

                // In buildPwForbiddenKnowledge, gate on adjacency:
                if (!graph.isAdjacentTo(xi, xj)) continue; // skip non-adjacent pairs

                int i_xi = nodes.indexOf(xi);
                int i_xj = nodes.indexOf(xj);

                double diff = Fask.leftRightDiff(data[i_xi], data[i_xj], pwRule);

                if (diff > 0) {
                    // prefer i_xi -> i_xj  ⇒ forbid xj -> xi
                    knowledge.setForbidden(xj.getName(), xi.getName());
                    if (verbose)
                        TetradLogger.getInstance().log("CPW-K: forbid " + xj + "→" + xi + " (prefer " + xi + "→" + xj + ", diff=" + diff + ")");
                } else {
                    // prefer i_xj -> i_xi  ⇒ forbid xi -> yj
                    knowledge.setForbidden(xi.getName(), xj.getName());
                    if (verbose)
                        TetradLogger.getInstance().log("CPW-K: forbid " + xi + "→" + xj + " (prefer " + xj + "→" + xi + ", diff=" + diff + ")");
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
     * acyclic graph (PAG) based on the input graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph, false);
    }

    /**
     * Provides a textual description of the CPW algorithm, including its
     * functionality and distinguishing features, such as the use of pairwise-derived
     * forbidden knowledge and pairwise orientation for certain edges.
     *
     * @return A string describing the CPW algorithm and its characteristics.
     */
    @Override
    public String getDescription() {
        return "CPW: Causal Pairwise (causally sufficient case)";
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
     * CPW algorithm. These parameters control various aspects of the
     * algorithm's execution, such as graph exploration limits, orientation styles,
     * and additional settings affecting the causal inference process.
     *
     * @return A list of strings, where each string represents a parameter name
     * used by the CPW algorithm.
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
        parameters.add(Params.FASK_LEFT_RIGHT_RULE);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.VERBOSE);
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