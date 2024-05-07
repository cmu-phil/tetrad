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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.FgesOrienter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the MMHC algorithm.
 *
 * @author josephramsey (this version).
 * @version $Id: $Id
 */
public class Mmhc implements IGraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;
    private final DataSet data;
    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth;
    private Knowledge knowledge = new Knowledge();

    //=============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for Mmhc.</p>
     *
     * @param test    a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public Mmhc(IndependenceTest test, DataSet dataSet) {
        this.depth = -1;
        this.independenceTest = test;
        this.data = dataSet;
    }

    //==============================PUBLIC METHODS========================//


    /**
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * <p>Getter for the field <code>depth</code>.</p>
     *
     * @return a int
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * <p>Setter for the field <code>depth</code>.</p>
     *
     * @param depth a int
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * <p>getElapsedTime.</p>
     *
     * @return a long
     */
    public long getElapsedTime() {
        return 0;
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph search() {
        List<Node> variables = this.independenceTest.getVariables();
        Mmmb mmmb = new Mmmb(this.independenceTest, getDepth(), true);
        Map<Node, List<Node>> pc = new HashMap<>();

        for (Node x : variables) {
            pc.put(x, mmmb.getPc(x));
        }

        Graph graph = new EdgeListGraph();

        for (Node x : variables) {
            graph.addNode(x);
        }

        for (Node x : variables) {
            for (Node y : pc.get(x)) {
                if (!graph.isAdjacentTo(x, y)) {
                    graph.addUndirectedEdge(x, y);
                }
            }
        }

        FgesOrienter orienter = new FgesOrienter(this.data);
        orienter.orient(graph);
        return graph;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Setter for the field <code>knowledge</code>.</p>
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}



