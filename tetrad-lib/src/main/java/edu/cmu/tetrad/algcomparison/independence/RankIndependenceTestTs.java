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

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Rank Independence Test TS, the former name of the test now called Rank Independence Test TS Singletons.
 * <p>
 * <b>Backward compatibility only.</b> This class is retained under its original name so that sessions saved by
 * Tetrad 7.6.10 and earlier, which refer to it by this class name, continue to load. Its registration annotations
 * are commented out, so it does not appear in the test chooser; new work should use
 * {@link RankIndependenceTestTsSingletons}, to which every method delegates.
 * <p>
 * One difference from 7.6.10 is unavoidable: the old class set an effective sample size on the underlying test, and
 * the current class offers no such setter, so that parameter is no longer applied.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@TestOfIndependence(
//        name = "Rank Independence Test TS",
//        command = "rank-test-ts",
//        dataType = DataType.Continuous
//)
@LinearGaussian
public class RankIndependenceTestTs implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * No-arg constructor. Used for reflection; do not delete.
     */
    public RankIndependenceTestTs() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        return new RankIndependenceTestTsSingletons().getTest(dataModel, parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Rank Independence Test TS (retained for sessions saved by Tetrad 7.6.10 and earlier)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return new RankIndependenceTestTsSingletons().getDataType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        return new RankIndependenceTestTsSingletons().getParameters();
    }
}
