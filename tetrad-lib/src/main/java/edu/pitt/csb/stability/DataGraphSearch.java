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

package edu.pitt.csb.stability;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by ajsedgewick on 9/4/15.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public abstract class DataGraphSearch {

    /**
     * <p>searchParams.</p>
     */
    public final double[] searchParams;

    /**
     * <p>Constructor for DataGraphSearch.</p>
     *
     * @param params a double
     */
    public DataGraphSearch(double... params) {
        this.searchParams = params;
    }

    /**
     * <p>copy.</p>
     *
     * @return a {@link edu.pitt.csb.stability.DataGraphSearch} object
     */
    public abstract DataGraphSearch copy();

    /**
     * <p>search.</p>
     *
     * @param data a {@link edu.cmu.tetrad.data.DataSet} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws java.lang.InterruptedException if any.
     */
    public abstract Graph search(DataSet data) throws InterruptedException;
}


