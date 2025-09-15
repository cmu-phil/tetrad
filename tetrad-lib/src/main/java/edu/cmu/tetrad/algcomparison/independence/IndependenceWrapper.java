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

import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.List;

/**
 * Interface that algorithm must implement.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IndependenceWrapper extends HasParameters, TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    @Serial
    long serialVersionUID = 23L;

    /**
     * Returns true iff x and y are independent conditional on z for the given data set.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The paramters of the test.
     * @return True iff independence holds.
     */
    IndependenceTest getTest(DataModel dataSet, Parameters parameters);

    /**
     * Returns a short of this independence test.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return This type.
     */
    DataType getDataType();

    /**
     * Returns the parameters that this search uses.
     *
     * @return A list of String names of parameters.
     */
    List<String> getParameters();
}

