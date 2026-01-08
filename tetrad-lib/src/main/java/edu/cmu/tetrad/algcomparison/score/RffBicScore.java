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

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian SEM BIC score.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "RFF BIC Score",
        command = "rff-bic-score",
        dataType = {DataType.Continuous}
)
@LinearGaussian
public class RffBicScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the SemBicScore.
     */
    public RffBicScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.RffBicScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.RffBicScore((DataSet) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        score.setLambda(parameters.getDouble(Params.RCIT_LAMBDA));
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setCenterFeatures(parameters.getBoolean(Params.RCIT_CENTER_FEATURES));

        return score;
    }

    /**
     * Returns the description of the Sem BIC Score.
     *
     * @return the description of the Sem BIC Score
     */
    @Override
    public String getDescription() {
        return "RFF BIC Score";
    }

    /**
     * Returns the data type of the current score.
     *
     * @return the data type of the score
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns a list of parameters applicable to this method.
     *
     * @return a list of parameters
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.RCIT_LAMBDA);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.RCIT_CENTER_FEATURES);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return parameters;
    }

    /**
     * Retrieves the variable with the given name from the data set.
     *
     * @param name the name of the variable
     * @return the variable with the given name, or null if no such variable exists
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }

}

