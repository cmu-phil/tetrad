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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FCI-Max. This is FCI with the first collider orientation rule (R0) replaced by the PC-Max orientation: for each
 * unshielded triple X *-* Z *-* Y with X and Y nonadjacent, the sepset S maximizing the p-value of the fact X _||_ Y |
 * S is selected, and Z is oriented as a collider just in case Z is not in that S.
 * <p>
 * This is a thin wrapper over {@link edu.cmu.tetrad.search.Fci} with the R0 collider rule pinned to
 * {@code ColliderRule.MAX_P}; the collider orientation style is therefore deliberately not offered as a parameter
 * here. The equivalent search can also be had from the FCI box by setting the collider orientation style to Max-P.
 * This class is retained under its own name because FCI-Max is a published algorithm, and because sessions saved by
 * Tetrad 7.6.10 and earlier refer to it by this class name.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.search.Fci
 * @see Fci
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCI-Max",
        command = "fci-max",
        algoType = AlgType.allow_latent_common_causes,
        dataType = DataType.All
)
@Bootstrapping
public class FciMax extends AbstractBootstrapAlgorithm implements Algorithm, AcceptsKnowledge,
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
     * Constructor.
     */
    public FciMax() {
    }

    /**
     * Constructor.
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public FciMax(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Runs the search.
     *
     * @param dataModel  the data model containing the dataset
     * @param parameters the parameters for the search algorithm
     * @return the resulting PAG
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        IndependenceTest test = getIndependenceWrapper().getTest(dataModel, parameters);
        test = new CachingIndependenceTest(test);

        edu.cmu.tetrad.search.Fci search = new edu.cmu.tetrad.search.Fci(test);
        search.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));
        search.setDepth(parameters.getInt(Params.DEPTH));

        // FCI-Max is FCI with the PC-Max R0 rule, decided triple by triple, as in PcMax.
        search.setR0ColliderRule(edu.cmu.tetrad.search.Fci.ColliderRule.MAX_P);
        search.setMaxPGlobalOrder(false);
        search.setMaxPDepthStratified(false);

        search.setKnowledge(this.knowledge);
        search.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDoPossibleDsep(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setGuaranteePag(parameters.getBoolean(Params.GUARANTEE_PAG));
        search.setExcludeSelectionBias(parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));

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
     * Returns the comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph, false);
    }

    /**
     * Returns a short, one-line description of this algorithm.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "FCI-Max (Fast Causal Inference with Max-P collider orientation) using " + this.test.getDescription();
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the list of parameters used by this algorithm.
     *
     * @return The list of parameters used by this algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        parameters.add(Params.DO_POSSIBLE_DSEP);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.EXCLUDE_SELECTION_BIAS);
        parameters.add(Params.GUARANTEE_PAG);

        parameters.add(Params.TIME_LAG);
        parameters.add(Params.TIME_LAG_REPLICATING_GRAPH);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Retrieves the knowledge object.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object for this method.
     *
     * @param knowledge The knowledge object to set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the {@link IndependenceWrapper} object associated with this method.
     *
     * @return The IndependenceWrapper object associated with this method.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the IndependenceWrapper object associated with this method.
     *
     * @param test the independence wrapper to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
