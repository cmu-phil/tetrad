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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.score.TRffBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MinimaxTRffTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

///**
// * Wrapper for the Minimax-t RFF LR CI test built on {@link MinimaxTRffBicScore}.
// *
// * <p>Tests X ⟂ Y | Z by comparing local fits on a common row subset:
// * reduced: child ~ Z
// * full:    child ~ Z ∪ {other}
// * using an LR statistic with a ΔEDF-based df approximation. The test is
// * symmetrized by computing both directions (Y|Z,X) and (X|Z,Y) and taking
// * the more conservative p-value.</p>
// *
// * @author josephramsey
// */
//@TestOfIndependence(
//        name = "Minimax-t-RFF-LR-Test",
//        command = "minimax-t-rff-lr-test",
//        dataType = DataType.Mixed
//)
//@Mixed
//@General
public final class MinimaxTRffIndTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Required no-arg ctor for algcomparison discovery/serialization.
     */
    public MinimaxTRffIndTest() {
    }

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        // Build the score from the mixed dataset
        TRffBicScore score = new TRffBicScore(SimpleDataLoader.getMixedDataSet(dataSet));

        // Standard knobs
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        score.setRidge(parameters.getDouble(Params.MINIMAX_RIDGE));
        score.setRffFeatures(parameters.getInt(Params.MINIMAX_FF_FEATURES));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        // CI test
        MinimaxTRffTest test = new MinimaxTRffTest(score);

        // Standard knobs
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        // If you added verbose support to the test, wire it here:
        // test.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return test;
    }

    @Override
    public String getDescription() {
        return "Minimax-t RFF LR (MinimaxTRffBicScore)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> p = new ArrayList<>();
        p.add(Params.ALPHA);
        p.add(Params.EFFECTIVE_SAMPLE_SIZE);
        p.add(Params.MINIMAX_RIDGE);
        p.add(Params.MINIMAX_FF_FEATURES);
        p.add(Params.PENALTY_DISCOUNT);
        p.add(Params.VERBOSE);

        return p;
    }
}