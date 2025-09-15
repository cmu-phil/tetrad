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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.TimeSeries;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.utils.TsDagToPag;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The SvarFci class is an implementation of the SVAR Fast Causal Inference algorithm.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "SvarFCI",
        command = "svar-fci",
        algoType = AlgType.allow_latent_common_causes
)
@TimeSeries
@Bootstrapping
public class SvarFci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * Represents the private variable `knowledge` in the class `SvarFci`.
     * <p>
     * This variable stores the knowledge used by the `SvarFci` algorithm. The type of `knowledge` is `Knowledge`.
     *
     * @see SvarFci
     * @see Knowledge
     */
    private Knowledge knowledge;

    /**
     * Represents a constructor for the SvarFci class.
     */
    public SvarFci() {
    }

    /**
     * Represents a constructor for the SvarFci class.
     *
     * @param test The IndependenceWrapper object used in the constructor.
     */
    public SvarFci(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Executes the search algorithm to find a graph structure that best fits the given dataset and parameters.
     *
     * @param dataModel  The dataset to perform the search on.
     * @param parameters The parameters to configure the search.
     * @return The graph structure that best fits the dataset.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        dataModel.setKnowledge(this.knowledge);
        edu.cmu.tetrad.search.SvarFci search = new edu.cmu.tetrad.search.SvarFci(this.test.getTest(dataModel, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Graph graph;
        double fdrQ = parameters.getDouble(Params.FDR_Q);

        if (fdrQ == 0.0) {
            graph = search.search();
        } else {
            boolean negativelyCorrelated = true;
            boolean verbose = parameters.getBoolean(Params.VERBOSE);
            double alpha = parameters.getDouble(Params.ALPHA);
            graph = IndTestFdrWrapper.doFdrLoop(search, negativelyCorrelated, alpha, fdrQ, verbose);
        }

        return graph;
    }

    /**
     * Returns a comparison graph based on the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    /**
     * Returns the description of the method. The description is a combination of "SvarFCI (SVAR Fast Causal Inference)
     * using" and the description of the independence test object.
     *
     * @return the description of the method.
     */
    public String getDescription() {
        return "SvarFCI (SVAR Fast Causal Inference) using " + this.test.getDescription();
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the list of parameters required for this method.
     *
     * @return The list of parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.TIME_LAG);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Retrieves the knowledge object associated with this algorithm.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object associated with this algorithm.
     *
     * @param knowledge The knowledge object to be set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the IndependenceWrapper object associated with this algorithm.
     *
     * @return The IndependenceWrapper object.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper for the algorithm.
     *
     * @param test The independence wrapper to set.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}

