/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
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

