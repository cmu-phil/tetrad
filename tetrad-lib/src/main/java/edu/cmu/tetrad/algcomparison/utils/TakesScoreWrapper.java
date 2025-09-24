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

package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;

/**
 * Tags an algorithm as using a score wrapper.
 * <p>
 * Author : Jeremy Espino MD Created  7/6/17 2:19 PM
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface TakesScoreWrapper {

    /**
     * Returns the score wrapper.
     *
     * @return the score wrapper.
     */
    ScoreWrapper getScoreWrapper();

    /**
     * Sets the score wrapper.
     *
     * @param score the score wrapper.
     */
    void setScoreWrapper(ScoreWrapper score);
}

