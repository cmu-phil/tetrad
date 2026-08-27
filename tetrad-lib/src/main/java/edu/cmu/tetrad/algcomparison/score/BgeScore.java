///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the BGe (Bayesian Gaussian equivalent) score, the Gaussian analog of BDeu: the local score is the exact
 * log marginal likelihood of a family under a Normal-Wishart prior rather than a BIC-penalized maximum likelihood.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.score.BgeScore
 */
@edu.cmu.tetrad.annotation.Score(
        name = "BGe Score",
        command = "bge-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class BgeScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the BGe score wrapper.
     */
    public BgeScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "BGe Score");
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.BgeScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.BgeScore((DataSet) this.dataSet);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.score.BgeScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setAlphaMu(parameters.getDouble(Params.BGE_ALPHA_MU));
        score.setAlphaWOffset(parameters.getDouble(Params.BGE_ALPHA_W_OFFSET));

        int effectiveSampleSize = parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE);
        if (effectiveSampleSize >= 0) score.setEffectiveSampleSize(effectiveSampleSize);

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BGe Score";
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
        parameters.add(Params.BGE_ALPHA_MU);
        parameters.add(Params.BGE_ALPHA_W_OFFSET);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        parameters.add(Params.MISSING_DATA_POLICY);
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
