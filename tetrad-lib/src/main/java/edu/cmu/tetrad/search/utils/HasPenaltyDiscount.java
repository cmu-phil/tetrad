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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.search.score.Score;

/**
 * Provides an interface for an algorithm can can get/set a value for penalty disoucnt.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface HasPenaltyDiscount extends Score {
    /**
     * <p>getPenaltyDiscount.</p>
     *
     * @return a double
     */
    double getPenaltyDiscount();

    /**
     * <p>setPenaltyDiscount.</p>
     *
     * @param penaltyDiscount a double
     */
    void setPenaltyDiscount(double penaltyDiscount);
}

