///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.Clusters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.Executable;
import edu.cmu.tetradapp.session.SessionModel;

/**
 * Specifies the methods that all algorithm runners must implement. All algorithm runners must know what their
 * parameters are, must know what their source graph is, and must know what their result graph is (if it has been
 * calculated).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface MimRunner extends SessionModel, Executable {

    /**
     * <p>getData.</p>
     *
     * @return the data used to execute this algorithm. Might possibly be a graph.
     */
    DataModel getData();

    /**
     * <p>getParams.</p>
     *
     * @return the search parameters for this algorithm.
     */
    Parameters getParams();

    /**
     * <p>getSourceGraph.</p>
     *
     * @return the graph from which data was originally generated, if such a graph is available. Otherwise, returns
     * null.
     */
    Graph getSourceGraph();

    /**
     * <p>getResultGraph.</p>
     *
     * @return the graph that results from executing the algorithm, if the algorithm has been successfully executed.
     */
    Graph getResultGraph();

    /**
     * <p>getClusters.</p>
     *
     * @return the clusters that resulted from executing the algorithm, if the algorithm was successfully executed.
     */
    Clusters getClusters();

    /**
     * <p>getStructureGraph.</p>
     *
     * @return the resulting strucure graph (that is, graph over latents only), if there is one; otherwise, null.
     */
    Graph getStructureGraph();

    /**
     * <p>getFullGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph getFullGraph();

    /**
     * Executes the algorithm.
     *
     * @throws java.lang.Exception if any.
     */
    void execute() throws Exception;
}





