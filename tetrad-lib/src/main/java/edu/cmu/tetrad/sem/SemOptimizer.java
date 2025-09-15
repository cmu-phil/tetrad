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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface for algorithm that optimize the fitting function of a SemIm model by adjusting its freeParameters in search
 * of a global maximum.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SemOptimizer extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Optimizes the fitting function of a Sem by adjusting its parameter values.
     *
     * @param sem The unoptimized Sem (will be optimized).
     */
    void optimize(SemIm sem);

    /**
     * <p>getNumRestarts.</p>
     *
     * @return a int
     */
    int getNumRestarts();

    /**
     * <p>setNumRestarts.</p>
     *
     * @param numRestarts a int
     */
    void setNumRestarts(int numRestarts);
}






