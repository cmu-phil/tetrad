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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.score.ScoredGraph;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Parameters;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Created by IntelliJ IDEA. User: jdramsey Date: Jun 28, 2009 Time: 8:59:41 AM To change this template use File |
 * Settings | File Templates.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IGesRunner {

    /**
     * <p>execute.</p>
     */
    void execute();

    /**
     * <p>getIndex.</p>
     *
     * @return a int
     */
    int getIndex();

    /**
     * <p>setIndex.</p>
     *
     * @param index a int
     */
    void setIndex(int index);

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph getGraph();

    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getTriplesClassificationTypes();

    /**
     * <p>getTriplesLists.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.util.List} object
     */
    List<List<Triple>> getTriplesLists(Node node);

    /**
     * <p>supportsKnowledge.</p>
     *
     * @return a boolean
     */
    boolean supportsKnowledge();

    /**
     * <p>getMeekRules.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.utils.MeekRules} object
     */
    MeekRules getMeekRules();

    /**
     * <p>propertyChange.</p>
     *
     * @param evt a {@link java.beans.PropertyChangeEvent} object
     */
    void propertyChange(PropertyChangeEvent evt);

    /**
     * <p>addPropertyChangeListener.</p>
     *
     * @param l a {@link java.beans.PropertyChangeListener} object
     */
    void addPropertyChangeListener(PropertyChangeListener l);

    /**
     * <p>getTopGraphs.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<ScoredGraph> getTopGraphs();

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph getResultGraph();

    /**
     * <p>getParams.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    Parameters getParams();

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph getSourceGraph();

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    DataModel getDataModel();
}



