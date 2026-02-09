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

/**
 * Wrapper for Minimax binning test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Minimax Conditional Independence Test",
        command = "minimax-ci-test",
        dataType = DataType.Mixed
)
@General
public class MinimaxCITest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructor.
     */
    public MinimaxCITest() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a Minimax Binning test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {

        var test = new edu.cmu.tetrad.search.test.MinimaxCITest((DataSet) dataSet,
                parameters.getDouble(Params.ALPHA));

        test.setBinsPerContXY(parameters.getInt(Params.BINS_PER_CONT_XY));
        test.setBinsPerContZ(parameters.getInt(Params.BINS_PER_CONT_Z));
        test.setMaxCellsPerStratum(parameters.getInt(Params.MAX_CELLS_PER_STRATUM));
        test.setMaxObservedLevelsPerVar(parameters.getInt(Params.MAX_OBSERVED_LEVELS_PER_VAR));
        test.setMinStratumSize(parameters.getInt(Params.MIN_STRATUM_SIZE));
        test.setUseMaxAcrossStrata(parameters.getBoolean(Params.USE_MAX_ACROSS_STRATA));
        test.setVerbose(parameters.getBoolean(Params.VERBOSE));
        test.setPermutations(parameters.getInt(Params.GIN_PERMUTATIONS));
        test.setPermSeed(12345);

        test.setAlpha(parameters.getDouble(Params.ALPHA));

        return new CachedIndependenceQueries(test);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the test.
     */
    @Override
    public String getDescription() {
        return "Minimax Conditional Independence Test";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the test, which is continuous.
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
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.VERBOSE);
        params.add(Params.BINS_PER_CONT_XY);
        params.add(Params.BINS_PER_CONT_Z);
        params.add(Params.MAX_CELLS_PER_STRATUM);
        params.add(Params.MAX_OBSERVED_LEVELS_PER_VAR);
        params.add(Params.MIN_STRATUM_SIZE);
        params.add(Params.USE_MAX_ACROSS_STRATA);
        params.add(Params.MINIMAX_PERMUTATIONS);
        return params;
    }
}

