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
import edu.cmu.tetrad.algcomparison.independence.BlocksIndTestTs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Algorithm-comparison wrapper for Boss-Trek-MIMIC.
 *
 * <p>This wrapper delegates the actual work to
 * {@link edu.cmu.tetrad.search.BossTrekMimic}, which performs the full search
 * through its constructor-driven {@code search()} method.</p>
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Boss-Trek-MIMIC",
        command = "boss-trek-mimic",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class BossTrekMimic extends AbstractBootstrapAlgorithm
        implements Algorithm, HasKnowledge, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Independence wrapper used for metadata only.
     */
    private final IndependenceWrapper test;

    /**
     * Background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional observed variables known to be inputs to the latent structure.
     * Stored by name so they remain stable across node replacement.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional observed variables known to be outputs from the latent structure.
     * Stored by name so they remain stable across node replacement.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();

    /**
     * Constructs the wrapper.
     */
    public BossTrekMimic() {
        this.test = new BlocksIndTestTs();
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
        if (!(dataModel instanceof DataSet)) {
            throw new IllegalArgumentException("Boss-Trek-MIMIC requires a DataSet.");
        }

        DataSet data = (DataSet) dataModel;

        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        edu.cmu.tetrad.search.BossTrekMimic search =
                new edu.cmu.tetrad.search.BossTrekMimic(data, parameters, score);

        search.setKnowledge(this.knowledge);
        search.setInputNames(this.inputNames);
        search.setOutputNames(this.outputNames);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

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
        return "BOSS-Trek-MIMIC using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
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
    public void setInputs(Collection<Node> inputs) {
        this.inputNames.clear();

        if (inputs != null) {
            for (Node node : inputs) {
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
    public void setOutputs(Collection<Node> outputs) {
        this.outputNames.clear();

        if (outputs != null) {
            for (Node node : outputs) {
                if (node != null) {
                    this.outputNames.add(node.getName());
                }
            }
        }

        validateInputOutputKnowledge();
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
     * Returns the currently specified input variable names.
     *
     * @return the input variable names
     */
    public List<String> getInputNames() {
        return new ArrayList<>(this.inputNames);
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