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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.TripleClassifier;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.session.Executable;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Parameters;

/**
 * Specifies the methods that all algorithm runners must implement. All algorithm runners must know what their
 * parameters are, must know what their source graph is, and must know what their result graph is (if it has been
 * calculated).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface AlgorithmRunner extends SessionModel, Executable, GraphSource,
        TripleClassifier, SimulationParamsSource, MultipleGraphSource {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getDataModel.</p>
     *
     * @return the data used to execute this algorithm. Might possibly be a graph.
     */
    DataModel getDataModel();

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
     * Executes the algorithm.
     */
    void execute();

    /**
     * <p>supportsKnowledge.</p>
     *
     * @return true if the algorithm supports knowledge.
     */
    boolean supportsKnowledge();

    /**
     * <p>getMeekRules.</p>
     *
     * @return the orientation rules for this search.
     */
    MeekRules getMeekRules();

    /**
     * <p>getExternalGraph.</p>
     *
     * @return the initial graph, if there is one, or null if not.
     */
    Graph getExternalGraph();

    /**
     * Sets the initial graph for the algorithm, if feasible.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    void setExternalGraph(Graph graph);

    /**
     * <p>getAlgorithmName.</p>
     *
     * @return the name of the algorithm.
     */
    String getAlgorithmName();
}





