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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.awt.*;
import java.util.Map;


/**
 * Interface to indicate a class that has a graph in it that can be laid out.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface LayoutEditable {

    /**
     * <p>getGraph.</p>
     *
     * @return the getModel graph. (Not necessarily a copy.)
     */
    Graph getGraph();

    /**
     * The display nodes.
     *
     * @return a {@link java.util.Map} object
     */
    Map<Edge, Object> getModelEdgesToDisplay();

    /**
     * <p>getModelNodesToDisplay.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<Node, Object> getModelNodesToDisplay();

    /**
     * <p>getKnowledge.</p>
     *
     * @return the getModel knowledge.
     */
    Knowledge getKnowledge();

    /**
     * <p>getSourceGraph.</p>
     *
     * @return the source graph.
     */
    Graph getSourceGraph();

    /**
     * Sets the graph according to which the given graph should be laid out.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    void layoutByGraph(Graph graph);

    /**
     * Lays out the graph in tiers according to knowledge.
     */
    void layoutByKnowledge();

    /**
     * <p>getVisibleRect.</p>
     *
     * @return the preferred size of the layout.
     */
    Rectangle getVisibleRect();
}





