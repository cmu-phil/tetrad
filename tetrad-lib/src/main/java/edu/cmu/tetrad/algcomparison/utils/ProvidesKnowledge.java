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

import edu.cmu.tetrad.data.Knowledge;

/**
 * A simulation whose design implies background knowledge that an analyst of the resulting data
 * would legitimately have - typically tiers given by the roles of the variables (design factors
 * before derived quantities before responses; context before system before indices before
 * outcomes) - and that can supply it for each emitted data model. This is knowledge about the
 * study design, not about the true graph: it never encodes the random structure drawn within a
 * role. Attaching a knowledge box to a simulation implementing this interface populates the box
 * with this knowledge.
 * <p>
 * Distinct from {@link AcceptsKnowledge}, which is for simulations that take knowledge as an
 * input.
 *
 * @author josephramsey
 */
public interface ProvidesKnowledge {

    /**
     * Returns the design-implied knowledge for the given data model, over that data model's
     * variables. A fresh object each call; callers may modify it.
     *
     * @param index the data model index.
     * @return the knowledge.
     */
    Knowledge getKnowledge(int index);
}
