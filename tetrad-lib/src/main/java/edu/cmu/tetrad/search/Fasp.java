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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is an optimization of the CCD (Cyclic Causal Discovery) algorithm by Thomas Richardson.
 *
 * @author Joseph Ramsey
 */
public final class Fasp implements GraphSearch {
    private IndependenceTest independenceTest;
    private int depth = -1;
    private long elapsed = 0;
    private IKnowledge knowledge = new Knowledge2();
    private DataSet dataSet = null;
    private boolean collapseTiers = false;

    public Fasp(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
        this.dataSet = (DataSet) independenceTest.getData();
        this.knowledge = new Knowledge2();
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {

        System.out.println("FAS");
        Graph graph = fastAdjacencySearch();

        System.out.println("R3 orientation orientation");

        Lofs2 lofs = new Lofs2(graph, Collections.singletonList(dataSet));
        lofs.setRule(Lofs2.Rule.R3);
        lofs.setKnowledge(knowledge);
        graph = lofs.orient();

        System.out.println("Done");

        if (collapseTiers) {
            return collapseGraph(graph);
        } else {
            return graph;
        }
    }

    private Graph collapseGraph(Graph graph) {
        List<Node> nodes = new ArrayList<>();

        for (String n : independenceTest.getVariableNames()) {
            String[] s = n.split(":");

            if (s.length == 1) {
                Node x = independenceTest.getVariable(s[0]);
                nodes.add(x);
            }
        }

        Graph _graph = new EdgeListGraph(nodes);

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            String[] sx = x.getName().split(":");
            String[] sy = y.getName().split(":");

            int lagx = sx.length == 1 ? 0 : new Integer(sx[1]);
            int lagy = sy.length == 1 ? 0 : new Integer(sy[1]);

            int maxInto = knowledge.getNumTiers() - 1;

            if (!((!edge.pointsTowards(x) && lagy < maxInto)
                    || (!edge.pointsTowards(y) && lagx < maxInto))) continue;

            String xName = sx[0];
            String yName = sy[0];

            Node xx = independenceTest.getVariable(xName);
            Node yy = independenceTest.getVariable(yName);

            if (xx == yy) continue;

            Edge _edge = new Edge(xx, yy, edge.getEndpoint1(), edge.getEndpoint2());

            if (!_graph.containsEdge(_edge)) {
                _graph.addEdge(_edge);
            }

            Edge undir = Edges.undirectedEdge(xx, yy);

            if (_graph.getEdges(xx, yy).size() > 1 && _graph.containsEdge(undir)) {
                _graph.removeEdge(undir);
            }
        }

        return _graph;
    }

    /**
     * @return The depth of search for the Fast Adjacency Search.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsed;
    }

    //======================================== PRIVATE METHODS ====================================//

    private Graph fastAdjacencySearch() {
        long start = System.currentTimeMillis();

        FasStableConcurrent fas = new FasStableConcurrent(null, independenceTest);
        fas.setDepth(getDepth());
        fas.setKnowledge(knowledge);
        fas.setVerbose(false);
        Graph graph = fas.search();

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        return new EdgeListGraph(graph);
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public void setCollapseTiers(boolean collapseTiers) {
        this.collapseTiers = collapseTiers;
    }
}






