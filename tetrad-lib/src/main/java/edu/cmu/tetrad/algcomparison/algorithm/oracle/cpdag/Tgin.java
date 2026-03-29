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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.*;

/**
 * Algorithm-comparison wrapper for the Trek-MIMIC search.
 *
 * <p>This wrapper delegates the actual work to
 * {@link edu.cmu.tetrad.search.mimic.TrekMimic}, which now performs the full search
 * through its {@code search()} method.</p>
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "TGIN",
        command = "tgin",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Tgin extends AbstractBootstrapAlgorithm
        implements Algorithm, HasKnowledge, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Optional observed variables known to be inputs to the latent structure.
     * Stored by name so they remain stable across node replacement.
     */
    private Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional observed variables known to be outputs from the latent structure.
     * Stored by name so they remain stable across node replacement.
     */
    private Set<String> outputNames = new LinkedHashSet<>();

    /**
     * Background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs the wrapper.
     */
    public Tgin() {
    }

    /**
     * Runs the search.
     *
     * @param dataModel  the data model
     * @param parameters the parameters
     * @return the resulting graph
     * @throws InterruptedException if interrupted
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        edu.cmu.tetrad.search.Tgin search =
                new edu.cmu.tetrad.search.Tgin(data, parameters);

        return search.search();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "TGIN";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.DEPTH);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        parameters.add(Params.MAX_RANK);
        parameters.add(Params.TSC_MIN_REDUNDANCY);
        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the observed variables known to be inputs to the latent structure.
     *
     * @param inputs the input variables
     */
    public void setInputs(Collection<edu.cmu.tetrad.graph.Node> inputs) {
        this.inputNames.clear();

        if (inputs != null) {
            for (edu.cmu.tetrad.graph.Node node : inputs) {
                if (node != null) {
                    this.inputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Sets the observed variables known to be outputs from the latent structure.
     *
     * @param outputs the output variables
     */
    public void setOutputs(Collection<edu.cmu.tetrad.graph.Node> outputs) {
        this.outputNames.clear();

        if (outputs != null) {
            for (edu.cmu.tetrad.graph.Node node : outputs) {
                if (node != null) {
                    this.outputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Returns the currently specified input variable names.
     *
     * @return the input variable names
     */
    public List<String> getInputNames() {
        return new ArrayList<>(this.inputNames);
    }

    /**
     * Sets the input variable names directly.
     *
     * @param inputNames the input variable names
     */
    public void setInputNames(Collection<String> inputNames) {
        this.inputNames.clear();

        if (inputNames != null) {
            for (String name : inputNames) {
                if (name != null) {
                    this.inputNames.add(name);
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Returns the currently specified output variable names.
     *
     * @return the output variable names
     */
    public List<String> getOutputNames() {
        return new ArrayList<>(this.outputNames);
    }

    /**
     * Sets the output variable names directly.
     *
     * @param outputNames the output variable names
     */
    public void setOutputNames(Collection<String> outputNames) {
        this.outputNames.clear();

        if (outputNames != null) {
            for (String name : outputNames) {
                if (name != null) {
                    this.outputNames.add(name);
                }
            }
        }

        validateInputOutputKnowledge();
    }

    /**
     * Clears any supplied input/output role knowledge.
     */
    public void clearInputOutputKnowledge() {
        this.inputNames.clear();
        this.outputNames.clear();
    }

    /**
     * Ensures that no observed variable is simultaneously declared as both an input and an output.
     */
    private void validateInputOutputKnowledge() {
        Set<String> intersection = new LinkedHashSet<>(this.inputNames);
        intersection.retainAll(this.outputNames);

        if (!intersection.isEmpty()) {
            throw new IllegalArgumentException(
                    "The same variables cannot be declared as both inputs and outputs: " + intersection
            );
        }
    }
}