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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the Poisson prior score (Bryan)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Poisson Prior Score",
        command = "poisson-prior-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class PoissonPriorScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the PoissonPriorScore.
     */
    public PoissonPriorScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.PoissonPriorScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.PoissonPriorScore((DataSet) this.dataSet, parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES));
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.score.PoissonPriorScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setLambda(parameters.getDouble(Params.POISSON_LAMBDA));
        score.setSingularityLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Poisson Prior Score";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.POISSON_LAMBDA);
        parameters.add(Params.SINGULARITY_LAMBDA);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
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

