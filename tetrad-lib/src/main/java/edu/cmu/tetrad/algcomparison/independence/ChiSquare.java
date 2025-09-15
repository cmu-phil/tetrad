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

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.ChiSquareTest;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Chi Square Test",
        command = "chi-square-test",
        dataType = DataType.Discrete
)
public class ChiSquare implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the ChiSquare class.
     */
    public ChiSquare() {
    }

    /**
     * Retrieves an instance of the IndependenceTest interface that performs a Chi Square Test for independence.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An instance of the IndependenceTest interface that performs a Chi Square Test for independence.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestChiSquare test = new IndTestChiSquare(SimpleDataLoader.getDiscreteDataSet(dataSet), parameters.getDouble(Params.ALPHA));
        test.setMinCountPerCell(parameters.getDouble(Params.MIN_COUNT_PER_CELL));

        if (parameters.getInt(Params.CELL_TABLE_TYPE) == 1) {
            test.setCellTableType(ChiSquareTest.CellTableType.AD_TREE);
        } else {
            test.setCellTableType(ChiSquareTest.CellTableType.COUNT_SAMPLE);
        }

        test.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));

        return test;
    }

    /**
     * Returns a short description of the Chi Square Test.
     *
     * @return A String representing the short description of the Chi Square Test.
     */
    @Override
    public String getDescription() {
        return "Chi Square Test";
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * Retrieves the parameters required by the Chi Square Test.
     *
     * @return A list of strings representing the parameters required by the Chi Square Test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.MIN_COUNT_PER_CELL);
        params.add(Params.CELL_TABLE_TYPE);
        params.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return params;
    }
}

