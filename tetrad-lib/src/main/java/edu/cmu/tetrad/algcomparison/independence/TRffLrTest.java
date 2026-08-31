/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.search.score.TRffBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the TRFF likelihood-ratio CI test built on {@link TRffBicScore}.
 *
 * <p>Tests X &perp; Y | Z by comparing nested local fits on a common row subset:
 * reduced child ~ Z versus full child ~ Z + extra(X), using an LR statistic with a
 * &Delta;EDF-based df approximation. The reduced model's design columns are a prefix of the
 * full model's, so the models are nested by construction and the LR statistic is
 * nonnegative. The test is symmetrized: both directions (child Y, added X) and (child X,
 * added Y) are computed and the more conservative p-value is reported.</p>
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "TRFF-LR-Test",
        command = "trff-lr-test",
        dataType = DataType.Mixed
)
@Mixed
@General
public final class TRffLrTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Required no-arg ctor for algcomparison discovery/serialization.
     */
    public TRffLrTest() {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a TRFF LR test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "TRFF-LR-Test");
        // Build the score from the mixed dataset
        TRffBicScore score = new TRffBicScore(SimpleDataLoader.getMixedDataSet(dataSet));

        // Standard knobs
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        score.setRidge(parameters.getDouble(Params.MINIMAX_RIDGE));
        score.setRffFeatures(parameters.getInt(Params.MINIMAX_FF_FEATURES));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        // CI test
        edu.cmu.tetrad.search.test.TRffLrTest test =
                new edu.cmu.tetrad.search.test.TRffLrTest(score);

        // Standard knobs
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setSymmetrized(parameters.getBoolean(Params.TRFF_SYMMETRIZED));
        test.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return test;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the test.
     */
    @Override
    public String getDescription() {
        return "TRFF LR Test (TRffBicScore)";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the test, which is mixed.
     *
     * @see DataType
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters of the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> p = new ArrayList<>();
        p.add(Params.ALPHA);
        p.add(Params.EFFECTIVE_SAMPLE_SIZE);
        p.add(Params.MINIMAX_RIDGE);
        p.add(Params.MINIMAX_FF_FEATURES);
        p.add(Params.PENALTY_DISCOUNT);
        p.add(Params.TRFF_SYMMETRIZED);
        p.add(Params.VERBOSE);

        p.add(Params.MISSING_DATA_POLICY);

        return p;
    }
}
