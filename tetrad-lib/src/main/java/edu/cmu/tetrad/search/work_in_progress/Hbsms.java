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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.sem.SemIm;

/**
 * Interface for Bff (Heuristic Best Significant Model Search) algorithm. See implementations.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Hbsms {
    /**
     * <p>setAlpha.</p>
     *
     * @param alpha a double
     */
    void setAlpha(double alpha);

    /**
     * <p>setBeamWidth.</p>
     *
     * @param beamWidth a int
     */
    void setBeamWidth(int beamWidth);

    /**
     * <p>setHighPValueAlpha.</p>
     *
     * @param alpha a double
     */
    void setHighPValueAlpha(double alpha);

    /**
     * <p>setKnowledge.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * <p>search.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph search();

    /**
     * <p>getOriginalSemIm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    SemIm getOriginalSemIm();

    /**
     * <p>getNewSemIm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    SemIm getNewSemIm();
}




