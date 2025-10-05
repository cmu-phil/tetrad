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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.test.IndTestBlocksWilkes;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Rank independence test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Rank Independence Test Lemma 10 Singletons",
        command = "rank-test-lemma10-singletons",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@Deprecated
@LinearGaussian
public class RankIndependenceTestLemma10Singletons implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public RankIndependenceTestLemma10Singletons() {
    }

    /**
     * Gets an independence test based on the given data model and parameters.
     *
     * @param dataModel  The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An IndependenceTest object.
     * @throws IllegalArgumentException if the dataModel is not a dataset or a covariance matrix.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        List<Node> nodes = dataModel.getVariables();
        List<Node> blockVars = new ArrayList<>();
        List<List<Integer>> blocks = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            blockVars.add(nodes.get(i));
            blocks.add(Collections.singletonList(i));
        }

        // If youâre using the Wilks-rank test:
        IndTestBlocksWilkes ind = new IndTestBlocksWilkes(new BlockSpec((DataSet) dataModel, blocks, blockVars));
        ind.setAlpha(parameters.getDouble(Params.ALPHA));

        return ind;
    }

    /**
     * Retrieves the description of the Fisher Z test.
     *
     * @return The description of the Fisher Z test.
     */
    @Override
    public String getDescription() {
        return "Rank test Lemma 10 Singletons";
    }

    /**
     * Retrieves the data type of the independence test.
     *
     * @return The data type of the independence test.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the parameters of the Fisher Z test.
     *
     * @return A list of strings representing the parameters of the Fisher Z test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        return params;
    }
}

