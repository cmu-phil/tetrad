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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * GesSearch is an implementation of the GES algorithm, as specified in
 * Chickering (2002) "Optimal structure identification with greedy search"
 * Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p>
 * Some code optimization could be done for the scoring part of the graph for
 * discrete models (method scoreGraphChange). Some of Andrew Moore's approaches
 * for caching sufficient statistics, for instance.
 * <p>
 * To speed things up, it has been assumed that variables X and Y with zero
 * correlation do not correspond to edges in the graph. This is a restricted
 * form of the heuristicSpeedup assumption, something GES does not assume. This
 * the graph. This is a restricted form of the heuristicSpeedup assumption,
 * something GES does not assume. This heuristicSpeedup assumption needs to be
 * explicitly turned on using setHeuristicSpeedup(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 */
public final class Bridges2 implements GraphSearch, GraphScorer {

    /**
     * Specification of forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * List of variables in the data set, in order.
     */
    private final List<Node> variables;

    /**
     * The totalScore for discrete searches.
     */
    private final Score score;
    // Map from variables to their column indices in the data set.
    private final HashMap<Node, Integer> hashIndices;

    // Where printed output is sent.
    private PrintStream out = System.out;

    // A initial adjacencies graph.
    private Graph adjacencies = null;

    // Bounds the degree of the graph.
    private int maxDegree = -1;

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative values in
     * case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion. This by default uses all of the processors on
     * the machine.
     */
    public Bridges2(Score score) {
        if (score == null) {
            throw new NullPointerException();
        }

        this.score = score;
        this.variables = score.getVariables();

        this.hashIndices = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            hashIndices.put(variables.get(i), i);
        }
    }

    //==========================PUBLIC METHODS==========================//

    public Graph search() {

        Fges fges = new Fges(score);
        Graph g0 = fges.search();
        double s0 = fges.getModelScore();

        boolean flag = true;

        while (flag) {
            if (Thread.interrupted()) break;

            flag = false;
            Iterator<Edge> edges = g0.getEdges().iterator();

            while (!flag && edges.hasNext()) {
                Edge edge = edges.next();

                if (edge.isDirected()) {
                    Graph g = new EdgeListGraph(g0);
                    Node a = Edges.getDirectedEdgeHead(edge);
                    Node b = Edges.getDirectedEdgeTail(edge);

                    // This code performs "pre-tuck" operation
                    // that makes anterior nodes of the distal
                    // node into parents of the proximal node

                    for (Node c : g.getAdjacentNodes(b)) {
                        if (g.paths().existsSemidirectedPath(c, a)) {
                            g.removeEdge(g.getEdge(b, c));
                            g.addDirectedEdge(c, b);
                        }
                    }

                    Edge reversed = edge.reverse();

                    g.removeEdge(edge);
                    g.addEdge(reversed);

                    fges.setInitialGraph(g);
                    Graph g1 = fges.search();
                    double s1 = fges.getModelScore();

                    if (s1 > s0) {
                        flag = true;
                        g0 = g1;
                        s0 = s1;
                        getOut().println(g0.getNumEdges());
                    }
                }
            }
        }

        return g0;
    }

    /**
     * @return the background knowledge.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required
     *                  edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * @return the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public Graph getAdjacencies() {
        return adjacencies;
    }

    /**
     * Sets the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public void setAdjacencies(Graph adjacencies) {
        this.adjacencies = adjacencies;
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the getters on the individual scores instead.
     */
    public double getPenaltyDiscount() {
        if (score instanceof ISemBicScore) {
            return ((ISemBicScore) score).getPenaltyDiscount();
        } else {
            return 2.0;
        }
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (score instanceof ISemBicScore) {
            ((ISemBicScore) score).setPenaltyDiscount(penaltyDiscount);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setSamplePrior(double samplePrior) {
        if (score instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) score).setSamplePrior(samplePrior);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setStructurePrior(double expectedNumParents) {
        if (score instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) score).setStructurePrior(expectedNumParents);
        }
    }

    /**
     * The maximum of parents any nodes can have in output pattern.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    /**
     * The maximum of parents any nodes can have in output pattern.
     *
     * @param maxDegree -1 for unlimited.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException();
        }
        this.maxDegree = maxDegree;
    }

    public double scoreDag(Graph dag) {
        if (score instanceof GraphScore) return 0.0;
        dag = GraphUtils.replaceNodes(dag, getVariables());

        Score score = this.score.defaultScore();

        double _score = 0;

        for (Node node : getVariables()) {
            List<Node> x = dag.getParents(node);

            int[] parentIndices = new int[x.size()];

            int count = 0;
            for (Node parent : x) {
                parentIndices[count++] = hashIndices.get(parent);
            }

            final double nodeScore = score.localScore(hashIndices.get(node), parentIndices);

            node.addAttribute("Score", nodeScore);

            _score += nodeScore;
        }

        dag.addAttribute("Score", _score);

        return _score;
    }

    private List<Node> getVariables() {
        return variables;
    }
}
