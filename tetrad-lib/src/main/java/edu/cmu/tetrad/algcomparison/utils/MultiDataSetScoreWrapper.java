///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * A score wrapper that can construct its per-data-set scores jointly, coordinating any
 * data-dependent representation choices across the data sets. Multi-data-set algorithms that
 * aim at a SINGLE COMMON MODEL over all data sets (e.g. IMaGES) should construct scores
 * through this interface when the wrapper implements it, so that every data set scores the
 * identical parameterization. Constructing scores one data set at a time lets data-dependent
 * choices (such as adaptive basis-column selection) come out differently per data set, in
 * which case the summed score compares models whose effective parameterizations differ
 * across data sets. Algorithms that legitimately fit different models per data set need not
 * use this interface.
 */
public interface MultiDataSetScoreWrapper extends ScoreWrapper {

    /**
     * Constructs one score per data model, coordinating data-dependent representation
     * choices across the data models so that all scores share a common parameterization.
     * The returned list is aligned with the input list.
     *
     * @param dataModels the data models, all with the same variables.
     * @param parameters the parameters.
     * @return one score per data model, in the same order.
     */
    List<Score> getScores(List<DataModel> dataModels, Parameters parameters);
}
