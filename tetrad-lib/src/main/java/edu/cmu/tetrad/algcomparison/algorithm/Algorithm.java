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

package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface that algorithm must implement.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Algorithm extends HasParameters, TetradSerializable {

    /**
     * Runs the search.
     *
     * @param dataSet    The data set to run to the search on.
     * @param parameters The paramters of the search.
     * @return The result graph.
     * @throws InterruptedException if any.
     */
    Graph search(DataModel dataSet, Parameters parameters) throws InterruptedException;

    /**
     * Returns that graph that the result should be compared to.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    Graph getComparisonGraph(Graph graph);

    /**
     * Returns a short, one-line description of this algorithm. This will be printed in the report.
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

}

