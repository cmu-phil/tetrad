///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.search.ScoredGraph;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: jdramsey
 * Date: Jun 28, 2009
 * Time: 8:59:41 AM
 * To change this template use File | Settings | File Templates.
 */
public interface IGesRunner {

    void execute();

    void setIndex(int index);

    int getIndex();

    Graph getGraph();

    List<String> getTriplesClassificationTypes();

    List<List<Triple>> getTriplesLists(Node node);

    boolean supportsKnowledge();

    ImpliedOrientation getMeekRules();

    void propertyChange(PropertyChangeEvent evt);

    void addPropertyChangeListener(PropertyChangeListener l);

//    Map<Graph, Double> getDagsToScores();

    List<ScoredGraph> getTopGraphs();

    Graph getResultGraph();

    SearchParams getParams();

    Graph getSourceGraph();

    DataModel getDataModel();
}



