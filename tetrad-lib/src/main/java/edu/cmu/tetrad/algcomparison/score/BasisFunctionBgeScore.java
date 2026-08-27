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

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for BF-BGe, the BGe (Normal-Wishart marginal likelihood) score over the basis-function embedding that BF-BIC
 * uses. Mixed data.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.score.BasisFunctionBgeScore
 */
@edu.cmu.tetrad.annotation.Score(name = "BF-BGe (Basis Function BGe)", command = "bf-bge-score", dataType = DataType.Mixed)
@Mixed
@General
public class BasisFunctionBgeScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the wrapper.
     */
    public BasisFunctionBgeScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "BF-BGe (Basis Function BGe)");
        this.dataSet = dataSet;

        if (!(dataSet instanceof DataSet)) {
            throw new IllegalArgumentException("BF-BGe requires a tabular data set.");
        }

        edu.cmu.tetrad.search.score.BasisFunctionBgeScore score = new edu.cmu.tetrad.search.score.BasisFunctionBgeScore(
                (DataSet) dataSet,
                parameters.getInt(Params.TRUNCATION_LIMIT),
                parameters.getBoolean(Params.ADAPTIVE_BASIS_SELECTION));

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
        return "BF-BGe (Basis Function BGe)";
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
        parameters.add(Params.TRUNCATION_LIMIT);
        parameters.add(Params.ADAPTIVE_BASIS_SELECTION);
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
