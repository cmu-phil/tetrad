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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;

/**
 * FCI-CPW: Run FCI with internally constructed PW-forbidden knowledge, then orient edges using a pairwise left-right
 * rule on standardized data in cases that are safe under no-selection-bias cyclic semantics:
 *
 * <ol>
 *   <li>Tail–tail (—) edges
 *     <ul>
 *       <li>Orient per skewness.</li>
 *     </ul>
 *   </li>
 *   <li>Tail–circle (—o) edges
 *     <ul>
 *       <li>If skewness prefers x→y, set x→y.</li>
 *     </ul>
 *   </li>
 *   <li>Circle–tail (o—) edges
 *     <ul>
 *       <li>If skewness prefers y→x, set y→x.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>We never alter &lt;-&gt; (two heads), never flip existing tails/heads,
 * and never touch o→ / ←o or o–o edges.</p>
 */
@edu.cmu.tetrad.annotation.Algorithm(name = "FCI-CPW", command = "fci-cpw", algoType = AlgType.allow_latent_common_causes)
@Bootstrapping
public class FciCyclicPw extends AbstractBootstrapAlgorithm implements Algorithm, TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Independence test wrapper (same as FCI wrapper).
     */
    private IndependenceWrapper test;
    /**
     * The minimum left-right difference to register a direction,
     */
    private double minDiff = 0.01;

    public FciCyclicPw() {
    }

    public FciCyclicPw(IndependenceWrapper test) {
        this.test = test;
    }

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

        // Standardize once; reuse for knowledge + all skewness decisions
        DataSet z = DataTransforms.standardizeData(dataSet);
        double[][] data = z.getDoubleData().transpose().toArray(); // vars x N
        List<Node> nodes = z.getVariables();

        // Fast name->index map (robust across Node instance identity)
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int k = 0; k < nodes.size(); k++) nameToIdx.put(nodes.get(k).getName(), k);

        // --- Phase 0: Build PW-forbidden knowledge (internal only) ---
        Knowledge internalKnowledge = buildPwForbiddenKnowledge(data, nodes);

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
        fci.setVerbose(parameters.getBoolean(Params.VERBOSE));
        fci.setStable(parameters.getBoolean(Params.STABLE_FAS));
        fci.setGuaranteePag(parameters.getBoolean(Params.GUARANTEE_PAG));

        Graph pag;
        double fdrQ = parameters.getDouble(Params.FDR_Q);
        if (fdrQ == 0.0) {
            pag = fci.search();
        } else {
            boolean negativelyCorrelated = true;
            boolean verbose = parameters.getBoolean(Params.VERBOSE);
            double alpha = parameters.getDouble(Params.ALPHA);
            pag = IndTestFdrWrapper.doFdrLoop(fci, negativelyCorrelated, alpha, fdrQ, verbose);
        }

        // --- Phase 2a: Orient tail–tail (—) edges using PW left-right on standardized data ---
        for (Edge e : new ArrayList<>(pag.getEdges())) { // snapshot to allow mutation
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();

            Node x = e.getNode1();
            Node y = e.getNode2();

            Endpoint exy = pag.getEndpoint(x, y);
            Endpoint eyx = pag.getEndpoint(y, x);

            Integer i = nameToIdx.get(n1.getName());
            Integer j = nameToIdx.get(n2.getName());
            if (i == null || j == null) continue; // defensive: mismatch in variable sets

            double[] _x = data[i];
            double[] _y = data[j];

            double diff = Fask.corrExp(_x, _y, _x) - Fask.corrExp(_x, _y, _y);

            if (abs(diff) > minDiff) {
                if (Edges.isUndirectedEdge(e)) {
                    if (diff > 0) {
                        pag.removeEdge(n1, n2);
                        pag.addDirectedEdge(n1, n2);
                    } else {
                        pag.removeEdge(n2, n1);
                        pag.addDirectedEdge(n2, n1);
                    }
                }

                // Case: x — o y  (TAIL at x toward y; CIRCLE at y toward x)
                else if (eyx == Endpoint.TAIL && exy == Endpoint.CIRCLE) {
                    if (diff > 0) {
                        pag.removeEdge(n1, n2);
                        pag.addDirectedEdge(n1, n2);
                    }
                }

                // Case: x o — y  (CIRCLE at x toward y; TAIL at y toward x)
                else if (eyx == Endpoint.CIRCLE && exy == Endpoint.TAIL) {
                    if (diff < 0) {
                        pag.removeEdge(n2, n1);
                        pag.addDirectedEdge(n2, n1);
                    }
                }
            }
        }

        return pag;
    }

    // --------------------------- Internals ---------------------------

    /**
     * Build forbidden knowledge from standardized data using pairwise left-right: For each pair (i,j), if
     * leftRightV2(data[i], data[j]) is true, forbid i -> j; else forbid j -> i.
     */
    private Knowledge buildPwForbiddenKnowledge(double[][] data, List<Node> nodes) {
        Knowledge knowledge = new Knowledge();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                double[] _x = data[i];
                double[] _y = data[j];

                double diff = Fask.corrExp(_x, _y, _x) - Fask.corrExp(_x, _y, _y);

                if (abs(diff) > minDiff) {
                    if (diff > 0) {
                        knowledge.setForbidden(y.getName(), x.getName());
                    } else {
                        knowledge.setForbidden(x.getName(), y.getName());
                    }
                }
            }
        }
        return knowledge;
    }

    // --------------------------- Boilerplate parity with Fci ---------------------------

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    @Override
    public String getDescription() {
        return "FCI-CPW: FCI with pairwise-derived forbidden knowledge and pairwise orientation of —, —o, and o— edges";
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

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
        parameters.add(Params.GUARANTEE_PAG);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}