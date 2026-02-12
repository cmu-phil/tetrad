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
// You should have received a copy
// of the GNU General Public License along with this program.  If not, see   //
// <https://www.gnu.org/licenses/>.                                          //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
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

///**
// * Wrapper for FF-ML-Legendre (Legendre feature expansion + categorical product-kernel) score.
// *
// * Notes:
// * - Continuous parents use Legendre P1..Pt features (t = LEGENDRE_DEGREE) with degree discount alpha.
// * - Discrete parents use categorical kernel with similarity rho.
// * - Score uses n×n GP marginal likelihood (up to constants), and discrete targets are scored
// *   via centered one-hot Gaussian surrogate summed across levels.
// *
// * @author josephramsey
// * @version $Id: $Id
// */
//@edu.cmu.tetrad.annotation.Score(
//        name = "FFML-Legendre Score",
//        command = "ffml-legendre-score",
//        dataType = {DataType.Mixed}
//)
//@General
//@Mixed
//@Experimental
public class FfMlLegendre implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /** The data model (must be a DataSet). */
    private DataModel dataSet;

    public FfMlLegendre() {
    }

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        if (!(dataSet instanceof DataSet ds)) {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        edu.cmu.tetrad.search.score.FfMlLegendre score =
                new edu.cmu.tetrad.search.score.FfMlLegendre(ds);

        // Reuse existing KML_LAMBDA knob as sigma^2 (noise/ridge).
        score.setLambda(parameters.getDouble(Params.KML_LAMBDA));

        // New knobs for Legendre feature basis:
        //  - LEGENDRE_DEGREE: truncation t (P1..Pt per continuous parent)
        //  - LEGENDRE_ALPHA:  degree discount exponent (0=no discount; 1..2 recommended)
        score.setLegendreDegree(parameters.getInt(Params.TRUNCATION_LIMIT));
        score.setLegendreAlpha(parameters.getDouble(Params.PENALTY_DISCOUNT));

        // Discrete categorical-kernel similarity.
        score.setCatRho(parameters.getDouble(Params.KML_CAT_RHO));

        // Effective sample size.
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

        return score;
    }

    @Override
    public String getDescription() {
        return "FFML-Legendre Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.KML_LAMBDA);
//        parameters.add(Params.LEGENDRE_DEGREE);
//        parameters.add(Params.LEGENDRE_ALPHA);

        parameters.add(Params.TRUNCATION_LIMIT);
        parameters.add(Params.PENALTY_DISCOUNT);

        parameters.add(Params.KML_CAT_RHO);
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}