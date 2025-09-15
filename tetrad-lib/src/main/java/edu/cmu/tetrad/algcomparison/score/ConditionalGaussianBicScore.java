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

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ConditionalGaussianScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "CG-BIC (Conditional Gaussian BIC Score)",
        command = "cg-bic-score",
        dataType = DataType.Mixed
)
@Mixed
public class ConditionalGaussianBicScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Initializes a new instance of the FisherZ class.
     */
    public ConditionalGaussianBicScore() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        ConditionalGaussianScore conditionalGaussianScore =
                new ConditionalGaussianScore(SimpleDataLoader.getMixedDataSet(dataSet),
                        parameters.getDouble("penaltyDiscount"),
                        parameters.getBoolean("discretize"));
        conditionalGaussianScore.setNumCategoriesToDiscretize(parameters.getInt(Params.NUM_CATEGORIES_TO_DISCRETIZE));
        conditionalGaussianScore.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));
        conditionalGaussianScore.setNumCategoriesToDiscretize(parameters.getInt(Params.MIN_SAMPLE_SIZE_PER_CELL));
        return conditionalGaussianScore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Conditional Gaussian BIC Score";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.STRUCTURE_PRIOR);
        parameters.add(Params.DISCRETIZE);
        parameters.add(Params.NUM_CATEGORIES_TO_DISCRETIZE);
        parameters.add(Params.MIN_SAMPLE_SIZE_PER_CELL);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}

