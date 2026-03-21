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
import edu.cmu.tetrad.algcomparison.independence.TakesGraph;
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
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "Vertex Repair (CPDAG)",
//        command = "vertex_repair_cpdag",
//        algoType = AlgType.forbid_latent_common_causes
//)
//@Bootstrapping
public class VertexRepairCpdag extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm,
        TakesGraph
{

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
     * A private member variable representing the graph structure utilized
     * within the VertexRepairCpdag algorithm. The graph serves as the primary
     * data structure for representing nodes (vertices) and directed/undirected
     * edges within the context of causal discovery or structural inference
     * operations performed by the algorithm.
     */
    private Graph graph;

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

    /**
     * Executes the Vertex Repair algorithm to search for a repaired causal graph
     * based on the given data model and parameters.
     * This method builds an independence test, initializes a starting graph, and
     * applies the Vertex Repair Search with specified configuration settings.
     *
     * @param dataModel the data model containing variables and their associated data
     * @param parameters the set of configuration parameters to control algorithm behavior
     * @return the repaired causal graph resulting from the Vertex Repair algorithm
     * @throws InterruptedException if the execution is interrupted during processing
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        // Build independence test (oracle wrapper or statistical), then cache queries.
        IndependenceTest it = getIndependenceWrapper().getTest(dataModel, parameters);
        it = new CachedIndependenceQueries(it);

        // Start from empty graph on the variables in the test.
        List<Node> vars = it.getVariables();
        Graph start = this.graph == null ? new EdgeListGraph(vars): graph; // no edges

        // Configure VertexRepairSearch.
        VertexRepairSearch vr = new VertexRepairSearch(it, start, this.knowledge,
                ConditioningSetType.RECURSIVE_BLOCKING);

        Graph repaired = vr.search(
                start,
                VertexRepairSearch.RepairGraphType.CPDAG,
                4, 50, 200);
//                parameters.getInt(Params.MAX_STEPS_PER_NODE),
//                parameters.getInt(Params.MAX_SWEEPS),
//                parameters.getInt(Params.MAX_EDITS)
//        );

        stampWithBic(repaired, dataModel);
        return repaired;
    }

    /**
     * Returns a comparison graph based on the given graph by creating
     * a defensive copy of it. The method ensures that modifications
     * to the returned graph do not affect the original input graph.
     *
     * @param graph the input graph to be used for creating the comparison graph
     * @return a defensive copy of the input graph in the form of an EdgeListGraph
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        // VertexRepairSearch already returns a CPDAG in this wrapper,
        // so just return a defensive copy.
        return new EdgeListGraph(graph);
    }

    /**
     * Provides a textual description of the Vertex Repair (CPDAG) algorithm,
     * including details about the underlying independence test used.
     *
     * @return A string describing the Vertex Repair (CPDAG) algorithm,
     *         with a reference to the description of the associated independence test.
     */
    @Override
    public String getDescription() {
        return "Vertex Repair (CPDAG) using " + this.test.getDescription();
    }

    /**
     * Returns the data type supported by the search algorithm. The data type can be continuous,
     * discrete, mixed, or other types as defined in the {@link DataType} enumeration.
     *
     * @return the data type required for the search.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves a list of parameter names used for configuring the algorithm.
     *
     * @return a list of strings representing the names of the available parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
//        parameters.add(Params.CONDITIONING_SET_TYPE);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Retrieves the knowledge object associated with the current instance.
     *
     * @return the {@code Knowledge} object representing domain-specific or prior knowledge
     *         related to the causal discovery process.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Updates the knowledge object for the current instance by creating
     * a defensive copy of the provided knowledge object.
     *
     * @param knowledge the {@code Knowledge} object representing domain-specific
     *                  or prior knowledge to be associated with the current instance
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the {@code IndependenceWrapper} instance associated with the current object.
     * The returned {@code IndependenceWrapper} allows for conducting independence tests
     * and managing related configurations required by the algorithm.
     *
     * @return the {@code IndependenceWrapper} associated with the current instance.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Updates the {@code IndependenceWrapper} instance associated with this class.
     * The provided {@code IndependenceWrapper} is used for conducting independence tests
     * and managing related configurations required by the algorithm.
     *
     * @param test the {@code IndependenceWrapper} instance to be associated with this object
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Updates the internal graph for this instance with the specified graph.
     * This method replaces the current graph with the provided one, allowing
     * the instance to operate on the new graph for subsequent processing.
     *
     * @param graph the {@code Graph} object to be set as the internal graph
     */
    @Override
    public void setGraph(Graph graph) {
        this.graph = graph;
    }
}