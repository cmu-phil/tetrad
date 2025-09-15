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

package edu.cmu.tetrad.study.gene.tetrad.gene.history;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * A distribution from which noise values are drawn.  Each call to nextRandom() draws a new noise value from the
 * distribution.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface RandomDistribution extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Draws a new noise value from the underlying distribution.
     *
     * @return this value.
     */
    double nextRandom();
}






