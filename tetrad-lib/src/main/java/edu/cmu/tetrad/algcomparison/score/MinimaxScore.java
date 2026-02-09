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

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.MinimaxTRffBicScore;
import edu.cmu.tetrad.search.score.MinimaxTRffBicScoreA;
import edu.cmu.tetrad.search.score.MinimaxTRffBicScoreD;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Minimax Histogram Score.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Minimax BIC Score",
        command = "minimax-bic-score",
        dataType = {DataType.Mixed}
)
@General
@Experimental
public class MinimaxScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the SemBicScore.
     */
    public MinimaxScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        MinimaxTRffBicScoreD score;

        if (dataSet instanceof DataSet) {
            score = new MinimaxTRffBicScoreD((DataSet) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        score.setNu(parameters.getDouble(Params.MINIMAX_NU));
        score.setScale(parameters.getDouble(Params.MINIMAX_SCALE));
        score.setRidge(parameters.getDouble(Params.MINIMAX_RIDGE));
        score.setRffFeatures(parameters.getInt(Params.MINIMAX_RFF_FEATURES));
        score.setRffSigma(parameters.getDouble(Params.MINIMAX_RFF_SIGMA));
        score.setIrlsIters(parameters.getInt(Params.MINIMAX_IRLS_ITERS));

        return score;
    }

    /**
     * Returns the description of the Score.
     *
     * @return the description of the Score
     */
    @Override
    public String getDescription() {
        return "Minimax BIC Score";
    }

    /**
     * Returns the data type of the current score.
     *
     * @return the data type of the score
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Returns a list of parameters applicable to this method.
     *
     * @return a list of parameters
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.MINIMAX_NU);
        parameters.add(Params.MINIMAX_SCALE);
        parameters.add(Params.MINIMAX_RIDGE);
        parameters.add(Params.MINIMAX_RFF_FEATURES);
        parameters.add(Params.MINIMAX_RFF_SIGMA);
        parameters.add(Params.MINIMAX_IRLS_ITERS);
        return parameters;
    }

    /**
     * d
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

