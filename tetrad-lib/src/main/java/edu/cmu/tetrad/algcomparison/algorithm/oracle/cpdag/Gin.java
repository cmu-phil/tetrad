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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * <p><b>GIN (Generalized Independent Noise) Search.</b></p>
 *
 * <p>This is the algcomparison driver for the paper-compliant GIN implementation
 * in {@link edu.cmu.tetrad.search.Gin}. It:</p>
 *
 * <ul>
 *   <li>Obtains a {@link RawMarginalIndependenceTest} from the configured
 *       {@link IndependenceWrapper}.</li>
 *   <li>Constructs a {@link edu.cmu.tetrad.search.Gin} instance with the
 *       user-specified {@code alpha} and {@code verbose} parameters.</li>
 *   <li>Runs the GIN search and returns the resulting latent DAG as a graph.</li>
 * </ul>
 *
 * <p>The independence test’s own hyperparameters (e.g., kernel choices for KCI)
 * are configured separately via the independence-wrapper configuration.</p>
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GIN",
        command = "gin",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Gin extends AbstractBootstrapAlgorithm
        implements Algorithm, TakesIndependenceWrapper, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Independence-wrapper used to create a {@link RawMarginalIndependenceTest}
     * for GIN. The wrapper is configured by the algcomparison framework.
     */
    private IndependenceWrapper test;

    /**
     * Default constructor for the Gin class.
     * Required for reflective construction; do not remove.
     */
    public Gin() {
        // Used in reflection; do not delete.
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet dataSet = (DataSet) dataModel;
        IndependenceTest itest = this.test.getTest(dataSet, parameters);

        if (!(itest instanceof RawMarginalIndependenceTest rawTest)) {
            throw new IllegalArgumentException(
                    "GIN requires an independence test that implements RawMarginalIndependenceTest."
            );
        }

        edu.cmu.tetrad.search.Gin gin = new edu.cmu.tetrad.search.Gin(
                parameters.getDouble(Params.ALPHA),
                rawTest
        );

        gin.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return gin.search(dataSet);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        // For comparison, just return a copy of the learned graph.
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "GIN (Generalized Independent Noise) latent-variable search " +
               "for continuous LiNGLaM-style data.";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.VERBOSE);
        return params;
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