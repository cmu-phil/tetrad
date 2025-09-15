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
import edu.cmu.tetrad.search.test.IndTestGSquare;
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
        name = "G Square Test",
        command = "g-square-test",
        dataType = DataType.Discrete
)
public class GSquare implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * GSquare class represents a wrapper for the G Square test, which is a statistical test for independence between
     * two variables conditional on a third variable.
     * <p>
     * This class implements the IndependenceWrapper interface, which requires the implementation of several methods.
     */
    public GSquare() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestGSquare test = new IndTestGSquare(SimpleDataLoader.getDiscreteDataSet(dataSet), parameters.getDouble("test"));
        test.setMinCountPerCell(parameters.getDouble(Params.MIN_COUNT_PER_CELL));

        if (parameters.getInt(Params.CELL_TABLE_TYPE) == 1) {
            test.setCellTableType(ChiSquareTest.CellTableType.AD_TREE);
        } else {
            test.setCellTableType(ChiSquareTest.CellTableType.COUNT_SAMPLE);
        }

        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "G Square Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.MIN_COUNT_PER_CELL);
        params.add(Params.CELL_TABLE_TYPE);
        return params;
    }
}

