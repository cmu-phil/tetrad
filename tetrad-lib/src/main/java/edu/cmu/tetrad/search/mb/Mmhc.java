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

package edu.cmu.tetrad.search.mb;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.FgsOrienter;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the MMHC algorithm.
 *
 * @author Joseph Ramsey (this version).
 */
public class Mmhc implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = Integer.MAX_VALUE;
    private DataSet data;
    private IKnowledge knowledge = new Knowledge2();

    //=============================CONSTRUCTORS==========================//

    public Mmhc(IndependenceTest test) {
        this.depth = -1;
        this.independenceTest = test;
        this.data = (DataSet) test.getData();
    }

    //==============================PUBLIC METHODS========================//


    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public int getDepth() {
        return depth;
    }

    public long getElapsedTime() {
        return 0;
    }


    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     */
    public Graph search() {
        List<Node> variables = independenceTest.getVariables();
        Mmmb mmmb = new Mmmb(independenceTest, getDepth(), true);
        Map<Node, List<Node>> pc = new HashMap<Node, List<Node>>();

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

        FgsOrienter orienter = new FgsOrienter(data);
        orienter.orient(graph);
        return graph;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}



