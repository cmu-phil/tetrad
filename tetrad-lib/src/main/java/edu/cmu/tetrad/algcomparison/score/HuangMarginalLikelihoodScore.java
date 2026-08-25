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

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.FfMlContinuous;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
// * Wrapper for Huang Marginal Likelihood Score.
// *
// * @author josephramsey
// * @version $Id: $Id
// */
//@edu.cmu.tetrad.annotation.Score(
//        name = "Huang ML Score",
//        command = "huang-ml-score",
//        dataType = {DataType.Continuous}
//)
//@General
//@Experimental
public class HuangMarginalLikelihoodScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the SemBicScore.
     */
    public HuangMarginalLikelihoodScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "Huang ML Score");
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.HuangMarginalLikelihoodScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.HuangMarginalLikelihoodScore((DataSet) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        score.setLambda(parameters.getDouble(Params.KML_LAMBDA));
        score.setJitter(parameters.getDouble(Params.KML_JITTER));
        score.setBandwidthMultiplier(parameters.getDouble(Params.KML_BANDWIDTH_MULTIPLIER));

        FfMlContinuous.FeatureType[] values
                = FfMlContinuous.FeatureType.values();


        return score;
    }

    /**
     * Returns the description of the Score.
     *
     * @return the description of the Score
     */
    @Override
    public String getDescription() {
        return "Huang ML Score";
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
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        parameters.add(Params.KML_LAMBDA);
        parameters.add(Params.KML_JITTER);
        parameters.add(Params.KML_BANDWIDTH_MULTIPLIER);

        parameters.add(Params.MISSING_DATA_POLICY);

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

