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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

///**
// * Wrapper for Neykov-style minimax optimal conditional independence test.
// *
// * @author josephramsey
// * @version $Id: $Id
// */
//@TestOfIndependence(
//        name = "Neykov Minimax Conditional Independence Test",
//        command = "neykov-minimax-ci-test",
//        dataType = DataType.Mixed
//)
//@General
//@Mixed
public class NeykovMinimaxCITest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 24L;

    /**
     * Constructor.
     */
    public NeykovMinimaxCITest() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a Neykov-style Minimax test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {

        var test = new edu.cmu.tetrad.search.test.NeykovMinimaxCITest((DataSet) dataSet,
                parameters.getDouble(Params.ALPHA));

        // shared minimax knobs
        test.setBinsPerContXY(parameters.getInt(Params.BINS_PER_CONT_XY));
        test.setMaxCellsPerStratum(parameters.getInt(Params.MAX_CELLS_PER_STRATUM));
        test.setMaxObservedLevelsPerVar(parameters.getInt(Params.MAX_OBSERVED_LEVELS_PER_VAR));
        test.setMinStratumSize(parameters.getInt(Params.MIN_STRATUM_SIZE));
        test.setVerbose(parameters.getBoolean(Params.VERBOSE));
        test.setPermutations(parameters.getInt(Params.MINIMAX_PERMUTATIONS));
        test.setPermSeed(12345);

        // Neykov-specific Z binning mode:
        // If adaptive Z bins are enabled, BINS_PER_CONT_Z is ignored (kept for UI consistency).
        // If you prefer fixed, set USE_ADAPTIVE_Z_BINS=false and then BINS_PER_CONT_Z is used.
        //
        // NOTE: This assumes you will add Params.USE_ADAPTIVE_Z_BINS (boolean) to Params.
        // If you don’t want to add a new param yet, you can hard-code adaptive=true here.
        boolean useAdaptiveZBins = true;// parameters.getBoolean(Params.USE_ADAPTIVE_Z_BINS);

        if (true) {
            test.setUseAdaptiveZBins(true);
        } else {
            test.setUseAdaptiveZBins(false);
            test.setBinsPerContZ(parameters.getInt(Params.BINS_PER_CONT_Z));
        }

        // ensure alpha is applied after permutations are set (enforces p-floor logic in test)
        test.setAlpha(parameters.getDouble(Params.ALPHA));

        return new CachedIndependenceQueries(test);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name/description of the test.
     */
    @Override
    public String getDescription() {
        return "Neykov-Minimax-Test";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the test.
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
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.VERBOSE);
        params.add(Params.BINS_PER_CONT_XY);

        // keep BINS_PER_CONT_Z for fixed-Z mode (and UI parity with existing minimax wrapper)
        params.add(Params.BINS_PER_CONT_Z);

        // Neykov switch (add to Params)
//        params.add(Params.USE_ADAPTIVE_Z_BINS);

        params.add(Params.MAX_CELLS_PER_STRATUM);
        params.add(Params.MAX_OBSERVED_LEVELS_PER_VAR);
        params.add(Params.MIN_STRATUM_SIZE);
        params.add(Params.MINIMAX_PERMUTATIONS);
        return params;
    }
}