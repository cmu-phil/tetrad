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
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * Fast adjacency search followed by pairwise orientation. If run with time series data and
 * knowledge, the graph can optionally be collapsed. The R3 algorithm was selected for
 * pairwise, after trying several such algorithms. The independence test needs to return its
 * data using the getData method.
 *
 * @author Joseph Ramsey
 */
public final class Fang implements GraphSearch {
    private IndependenceTest independenceTest;
    private int depth = -1;
    private long elapsed = 0;
    private double r3Cutoff = 0.0;
    private IKnowledge knowledge = new Knowledge2();
    private DataSet dataSet = null;
    private boolean collapseTiers = false;
    private List<DataSet> dataSets = null;

    public Fang(IndependenceTest test, List<DataSet> dataSets) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
        this.dataSet = (DataSet) independenceTest.getData();
        this.knowledge = new Knowledge2();
        this.dataSets = dataSets;
    }

    //======================================== PUBLIC METHODS ====================================//

    public Graph search() {

        System.out.println("FAS");
        Graph graph = fastAdjacencySearch();

        System.out.println("R3 orientation orientation");

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            if (knowledge.isForbidden(y.getName(), x.getName()) || knowledge.isRequired(x.getName(), y.getName())) {
                graph.removeEdge(x, y);
                graph.addDirectedEdge(x, y);
            } else if (knowledge.isForbidden(x.getName(), y.getName()) || knowledge.isRequired(y.getName(), x.getName())) {
                graph.removeEdge(y, x);
                graph.addDirectedEdge(y, x);
            }
        }

        List<Node> tier0 = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            if (getLag(node) == 0) {
                tier0.add(node);
            }
        }

        Graph graph2 = new EdgeListGraph(tier0);

        for (Edge edge : graph.getEdges()) {
            if (edge.getNode1() == edge.getNode2()) continue;

            if (getLag(edge.getNode1()) == 0 && getLag(edge.getNode2()) == 0) {
                graph2.addEdge(edge);
            }
        }


//        bishopsHat(graph);
//        Graph graph2 = graph;

        Lofs2 lofs = new Lofs2(graph2, dataSets);//.singletonList(dataSet));
        lofs.setRule(Lofs2.Rule.RSkew);
//        lofs.setKnowledge(knowledge);
        lofs.setEpsilon(r3Cutoff);
        graph2 = lofs.orient();

        System.out.println("Done");

        if (collapseTiers) {
            return collapseGraph(graph2);
        } else {
            return graph2;
        }
    }

    private void bishopsHat(Graph graph) {
        for (Node c : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(c);

            for (int i = 0; i < adj.size(); i++) {
                Node a = adj.get(i);

                for (int j = i + 1; j < adj.size(); j++) {
                    Node b = adj.get(j);
                    if (a == b) continue;
                    if (graph.isAdjacentTo(a, b)) continue;

                    for (Node d : adj) {
                        if (d == a || d == b) continue;

                        if (graph.isAdjacentTo(d, a) && graph.isAdjacentTo(d, b)) {
                            if (knowledge.isForbidden(c.getName(), d.getName()) || knowledge.isForbidden(d.getName(), c.getName())) {
                                continue;
                            }

                            if (sepset(graph, a, b, set(c, d), set()) != null) {
                                addFeedback(graph, c, d);
                            }
                        }
                    }
                }
            }
        }
    }

    private Set<Node> set(Node... n) {
        Set<Node> S = new HashSet<>();
        Collections.addAll(S, n);
        return S;
    }

    private void addFeedback(Graph graph, Node a, Node b) {
        graph.removeEdges(a, b);
        graph.addEdge(Edges.directedEdge(a, b));
        graph.addEdge(Edges.directedEdge(b, a));
    }

    private List<Node> sepset(Graph graph, Node a, Node c, Set<Node> containing, Set<Node> notContaining) {
        List<Node> adj = graph.getAdjacentNodes(a);
        adj.addAll(graph.getAdjacentNodes(c));
        adj.remove(c);
        adj.remove(a);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), Math.max(adj.size(), adj.size())); d++) {
            if (d <= adj.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v2 = GraphUtils.asSet(choice, adj);
                    v2.addAll(containing);
                    v2.removeAll(notContaining);
                    v2.remove(a);
                    v2.remove(c);

                    independenceTest.isIndependent(a, c, new ArrayList<>(v2));
                    double p2 = independenceTest.getScore();

                    if (p2 < 0) {
                        return new ArrayList<>(v2);
                    }
                }
            }
        }

        return null;
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
            int lagx = getLag(edge.getNode1());
            int lagy = getLag(edge.getNode2());

//            if (graph.getEdges(x, y).size() != 2) {
//            if (!((!edge.pointsTowards(x) && lagy < maxInto)
//                    || (!edge.pointsTowards(y) && lagx < maxInto))) continue;
//            }

            if (lagx != 0 || lagy != 0) continue;

            String xName = getName(x);
            String yName = getName(y);

            Node xx = independenceTest.getVariable(xName);
            Node yy = independenceTest.getVariable(yName);

            if (xx == yy) continue;

            Edge _edge = new Edge(xx, yy, edge.getEndpoint1(), edge.getEndpoint2());

            if (!_graph.containsEdge(_edge)) {
                _graph.addEdge(_edge);
            }

//            Edge undir = Edges.undirectedEdge(xx, yy);
//
//            if (_graph.getEdges(xx, yy).size() > 1 && _graph.containsEdge(undir)) {
//                _graph.removeEdge(undir);
//            }
        }

        return _graph;
    }

    private Graph collapseGraph2(Graph graph) {
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

    private String getName(Node x) {
        return x.getName().split(":")[0];
    }

    private int getLag(Node y) {
        String[] sy1 = y.getName().split(":");
        return sy1.length == 1 ? 0 : new Integer(sy1[1]);
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

    public double getR3Cutoff() {
        return r3Cutoff;
    }

    public void setR3Cutoff(double r3Cutoff) {
        this.r3Cutoff = r3Cutoff;
    }
}






