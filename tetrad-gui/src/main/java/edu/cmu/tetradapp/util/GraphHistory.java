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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;

import java.util.LinkedList;

/**
 * Stores a history of graph objects.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphHistory {

    /**
     * The history.
     */
    private final LinkedList<Graph> graphs;

    /**
     * The index of the getModel graph.
     */
    private int index;

    /**
     * Constructs a graph history.
     */
    public GraphHistory() {
        this.graphs = new LinkedList<>();
        this.index = -1;
    }

    /**
     * <p>add.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void add(Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        if (this.graphs.size() > this.index + 1) {
            this.graphs.subList(this.index + 1, this.graphs.size()).clear();
        }

        this.graphs.addLast(new EdgeListGraph(graph));
        this.index++;
    }

    /**
     * <p>next.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph next() {
        if (this.index == -1) {
            throw new IllegalArgumentException("Graph history has not been " +
                                               "initialized yet.");
        }

        if (this.index < this.graphs.size() - 1) {
            this.index++;
        }

        return this.graphs.get(this.index);
    }

    /**
     * <p>previous.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph previous() {
        if (this.index == -1) {
            throw new IllegalArgumentException("Graph history has not been " +
                                               "initialized yet.");
        }

        if (this.index > 0) {
            this.index--;
        }

        return this.graphs.get(this.index);
    }

    /**
     * <p>clear.</p>
     */
    public void clear() {
        this.graphs.clear();
        this.index = -1;
    }
}



