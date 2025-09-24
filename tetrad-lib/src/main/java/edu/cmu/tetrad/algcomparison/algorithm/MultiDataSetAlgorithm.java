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

import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * Implements an algorithm that takes multiple data sets as input.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface MultiDataSetAlgorithm extends Algorithm {

    /**
     * Runs the search.
     *
     * @param dataSets   The data sets.
     * @param parameters The parameters.
     * @return The graph.
     * @throws InterruptedException if any.
     */
    Graph search(List<DataModel> dataSets, Parameters parameters) throws InterruptedException;

    /**
     * Sets a score wrapper if not null.
     *
     * @param score The wrapper
     */
    void setScoreWrapper(ScoreWrapper score);

    /**
     * Sets a test wrapper if not null.
     *
     * @param test The wrapper
     */
    void setIndTestWrapper(IndependenceWrapper test);
}

