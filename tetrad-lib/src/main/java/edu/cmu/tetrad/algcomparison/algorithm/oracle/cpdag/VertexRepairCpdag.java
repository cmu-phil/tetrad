///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.vertex_repair.VertexRepairSearch;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Vertex Repair (oracle/independence-test driven), starting from an empty graph
 * over the observed variables, and returning a repaired CPDAG.
 *
 * This is a thin AlgComparison wrapper around {@link VertexRepairSearch}.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Vertex Repair (CPDAG)",
        command = "vertex_repair_cpdag",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class VertexRepairCpdag extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for VertexRepairCpdag.</p>
     */
    public VertexRepairCpdag() {
    }

    /**
     * <p>Constructor for VertexRepairCpdag.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public VertexRepairCpdag(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        // Build independence test (oracle wrapper or statistical), then cache queries.
        IndependenceTest it = getIndependenceWrapper().getTest(dataModel, parameters);
        it = new CachedIndependenceQueries(it);

        // Start from empty graph on the variables in the test.
        List<Node> vars = it.getVariables();
        Graph start = new EdgeListGraph(vars); // no edges

        // Configure VertexRepairSearch.
        VertexRepairSearch vr = new VertexRepairSearch(it, start, this.knowledge,
                ConditioningSetType.ORDERED_LOCAL_MARKOV_MAG);

        // Conditioning-set type: VertexRepairSearch needs it; we expose it as an algcomparison param.
        // If you already have a Params constant for this in your VertexCheck UI model, reuse it here.
        // Otherwise, define one and map ints -> enum in VertexRepairSearch.
        //
        // Convention: 0 = LOCAL_MARKOV, 1 = ALL_ADJACENT, 2 = ADJACENT_OR_ANCESTORS, etc.
        // Replace with your actual mapping.

        // Optional knobs, only if your VertexRepairSearch supports them.
        // vr.setMaxEdits(parameters.getInt(Params.MAX_EDITS));
        // vr.setDepth(parameters.getInt(Params.DEPTH));
        // vr.setAlpha(parameters.getDouble(Params.ALPHA));

        Graph repaired = vr.search();

        stampWithBic(repaired, dataModel);
        return repaired;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        // VertexRepairSearch already returns a CPDAG in this wrapper,
        // so just return a defensive copy.
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "VertexRepairSearch (CPDAG) using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
//        parameters.add(Params.CONDITIONING_SET_TYPE);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
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