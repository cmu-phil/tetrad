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

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * CCD (Cyclic Causal Discovery)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CCD",
        command = "ccd",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Ccd extends AbstractBootstrapAlgorithm implements Algorithm, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * Constructs a new CCD algorithm.
     */
    public Ccd() {
        // Used in reflection; do not delete.
    }

    /**
     * Constructs a new CCD algorithm with the given independence test.
     *
     * @param test the independence test
     */
    public Ccd(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Runs the CCD (Cyclic Causal Discovery) search algorithm on the given data set using the specified parameters.
     *
     * @param dataModel  the data set to search on
     * @param parameters the parameters for the search algorithm
     * @return the resulting graph from the search
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        IndependenceTest _test = test.getTest(dataModel, parameters);
        edu.cmu.tetrad.search.Ccd search = new edu.cmu.tetrad.search.Ccd(_test);
        search.setApplyR1(parameters.getBoolean(Params.APPLY_R1));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Graph graph;
        double fdrQ = parameters.getDouble(Params.FDR_Q);

        if (fdrQ == 0.0) {
            graph = search.search();
        } else {
            boolean negativelyCorrelated = true;
            boolean verbose = parameters.getBoolean(Params.VERBOSE);
            double alpha = _test.getAlpha();
            graph = IndTestFdrWrapper.doFdrLoop(search, negativelyCorrelated, alpha, fdrQ, verbose);
        }

        return graph;
    }

    /**
     * Retrieves the comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The true DAG.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. This description will be printed in the report.
     *
     * @return The description of this algorithm.
     */
    @Override
    public String getDescription() {
        return "CCD (Cyclic Causal Discovery using " + test.getDescription();
    }

    /**
     * Retrieves the data type that the search requires.
     *
     * @return The data type required by the search.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the parameters for the search algorithm. This method combines the parameters obtained from the
     * underlying test with additional parameters specific to the CCD (Cyclic Causal Discovery) algorithm.
     *
     * @return A list of String names for parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.APPLY_R1);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.VERBOSE);
        return parameters;
    }


    /**
     * Returns the IndependenceWrapper object associated with this instance.
     *
     * @return the IndependenceWrapper object
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Updates the independence wrapper for this algorithm.
     *
     * @param independenceWrapper the independence wrapper to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}

