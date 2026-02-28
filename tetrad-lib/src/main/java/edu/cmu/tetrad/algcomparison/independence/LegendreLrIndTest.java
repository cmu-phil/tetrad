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
import edu.cmu.tetrad.search.score.LegendreBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.LegendreLrIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the Legendre LR CI test built on {@link LegendreBicScore}.
 *
 * <p>Tests X ⟂ Y | Z by comparing local fits:
 * reduced: Y ~ Z
 * full:    Y ~ Z ∪ {X}
 * using an LR statistic with an EDF-based df approximation.</p>
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "Legendre-LR-Test",
        command = "legendre-lr-test",
        dataType = DataType.Mixed
)
@Mixed
@General
public final class LegendreLrIndTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Required no-arg ctor for algcomparison discovery/serialization.
     */
    public LegendreLrIndTest() {
    }

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        // Build the score from the mixed dataset
        LegendreBicScore score = new LegendreBicScore(SimpleDataLoader.getMixedDataSet(dataSet));

        // Core knobs (only set what exists in your Params; remove any you don't have)
        score.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

        // If you’ve defined these Params, wire them; otherwise delete these blocks.
        score.setLegendreDegree(parameters.getInt(Params.LEGENDRE_DEGREE));
        score.setLegendreClip(parameters.getDouble(Params.LEGENDRE_CLIP));
        score.setRidge(parameters.getDouble(Params.MINIMAX_RIDGE));
        score.setNu(parameters.getDouble(Params.LEGENDRE_NU));
        score.setIrlsIters(parameters.getInt(Params.MINIMAX_IRLS_ITERS));
        score.setIrlsTol(parameters.getDouble(Params.LEGENDRE_IRLS_TOL));

        score.setUseInteractions(true);
        score.setInteractionMaxParents(3);

        // CI test: default to disabling interactions during testing to preserve nesting.
        boolean disableInteractionsForTest = true;

        LegendreLrIndependenceTest test = new LegendreLrIndependenceTest(score, disableInteractionsForTest);

        // Standard knobs
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return test;
    }

    @Override
    public String getDescription() {
        return "Legendre LRT";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> p = new ArrayList<>();
        p.add(Params.ALPHA);

        // Keep this list in sync with what you actually support.
        p.add(Params.EFFECTIVE_SAMPLE_SIZE);

        // Optional score/test knobs — include only if you actually define these Params
        p.add(Params.LEGENDRE_DEGREE);
        p.add(Params.LEGENDRE_CLIP);
        p.add(Params.LEGENDRE_RIDGE);
        p.add(Params.LEGENDRE_NU);
        p.add(Params.MINIMAX_IRLS_ITERS);
        p.add(Params.MINIMAX_IRLS_ITERS);

        p.add(Params.PENALTY_DISCOUNT);
        p.add(Params.VERBOSE);

        return p;
    }
}