///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014 by Peter Spirtes, Richard Scheines, Joseph   //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements the CPC-FdrLists search.
 *
 * @author Joseph Ramsey.
 */
public class CpcFdrLists implements GraphSearch {

    /**
     * The search graph. It is assumed going in that all of the true adjacencies of x are in this graph for every node
     * x. It is hoped (i.e. true in the large sample limit) that true adjacencies are never removed.
     */
    private Graph graph;

    /**
     * The independence test. This should be appropriate to the types
     */
    private IndependenceTest test;

    /**
     * Knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of variables conditioned on in any conditional independence test. If the depth is -1, it will
     * be taken to be the maximum value, which is 1000. Otherwise, it should be set to a non-negative integer.
     */
    private int depth = 1000;

    /**
     * The logger, by default the empty logger.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;

    /**
     * Verbose output is sent here.
     */
    private PrintStream out = System.out;

    /**
     * The elapsed time in milliseconds.
     */
    private long elapsedtime = 0;

    /**
     * The FDR q to use for the orientation search.
     */
    private double fdrQ = 0.05;


    //==========================CONSTRUCTORS=============================//

    public CpcFdrLists(IndependenceTest test) {
        this.test = test;
    }

    //==========================PUBLIC METHODS===========================//

    /**
     * Discovers all adjacencies in data.  The procedure is to remove edges in the graph which connect pairs of
     * variables which are independent conditional on some other set of variables in the graph (the "sepset"). These are
     * removed in tiers.  First, edges which are independent conditional on zero other variables are removed, then edges
     * which are independent conditional on one other variable are removed, then two, then three, and so on, until no
     * more edges can be removed from the graph.  The edges which remain in the graph after this procedure are the
     * adjacencies in the data.
     *
     * @return a SepSet, which indicates which variables are independent conditional on which other variables
     */
    public Graph search() {
        this.logger.log("info", "Starting Fast Adjacency Search.");
        return main();
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0.");
        }

        if (depth == -1) depth = 1000;
        this.depth = depth;
    }

    /**
     * The FDR q to use for the orientation search.
     */
    public void setFdrQ(double fdrQ) {
        if (fdrQ < 0 || fdrQ > 1) {
            throw new IllegalArgumentException("FDR q must be in [0, 1]: " + fdrQ);
        }

        this.fdrQ = fdrQ;
    }

    /**
     * Specification of which edges are forbidden or required.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Cannot set knowledge to null");
        }
        this.knowledge = knowledge;
    }
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    @Override
    public long getElapsedTime() {
        return elapsedtime;
    }

    //==============================PRIVATE METHODS======================//

    private Graph main() {
        long start = System.currentTimeMillis();

        findAdjacencies();

        orientTriples(depth, graph);

        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.orientImplied(graph);

        // Remove unnecessary marks.
        for (Triple triple : graph.getUnderLines()) {
            graph.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        for (Triple triple : graph.getAmbiguousTriples()) {
            if (graph.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getX())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (graph.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getZ())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (graph.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getY())
                    && graph.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getY())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }

        long stop = System.currentTimeMillis();
        this.elapsedtime = stop - start;

        return graph;
    }

    private static class PValue {
        private double p;
        private Set<Node> sepset;

        PValue(double p, List<Node> sepset) {
            this.p = p;
            this.sepset = new HashSet<>(sepset);
        }

        public Set<Node> getSepset() {
            return sepset;
        }

        public double getP() {
            return p;
        }

        public boolean equals(Object o) {
            if (!(o instanceof PValue)) return false;
            PValue _o = (PValue) o;
            return _o.getP() == getP() && _o.getSepset().equals(getSepset());
        }
    }

    private void findAdjacencies() {
        FasStable fas = new FasStable(test);
        fas.setKnowledge(knowledge);
        fas.setVerbose(verbose);
        this.graph = fas.search();
    }

    private void orientTriples(int depth, Graph graph) {
        List<Triple> colliders = new ArrayList<>();
        List<Triple> ambiguous = new ArrayList<>();
        List<Triple> noncolliders = new ArrayList<>();

        Map<NodePair, List<PValue>> notBMap = new HashMap<>();

        for (Node b : graph.getNodes()) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<PValue> pValues = getAllPValues(a, c, depth, this.graph, test);

                List<PValue> bPvals = new ArrayList<>();
                List<PValue> notbPvals = new ArrayList<>();

                for (PValue p : pValues) {
                    if (p.getSepset().contains(b)) {
                        bPvals.add(p);
                    } else {
                        notbPvals.add(p);
                    }
                }

                boolean existsb = existsSepsetFromList(bPvals, getFdrQ());
                boolean existsnotb = existsSepsetFromList(notbPvals, getFdrQ());

                if (existsb && !existsnotb) {
                    noncolliders.add(new Triple(a, b, c));
                    if (verbose) {
                        out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": noncollider"
                                + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                    }
                } else if (!existsb && existsnotb && knowledgeAllowsCollider(a, b, c)) {
                    colliders.add(new Triple(a, b, c));

                    notbPvals.sort((p1, p2) -> Double.compare(p2.getP(), p1.getP()));
                    notBMap.put(new NodePair(a, c), notbPvals);

                    if (verbose) {
                        out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": COLLIDER"
                                + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                    }
                } else {
                    ambiguous.add(new Triple(a, b, c));

                    if (verbose) {
                        out.println(a + " --- " + b + " --- " + c + " depth = " + depth + ": ...ambiguous"
                                + " bVals = " + bPvals.size() + " notbVals " + notbPvals.size());
                    }
                }
            }
        }

        colliders.sort(Comparator.comparingDouble(a -> -notBMap.get(new NodePair(a.getX(), a.getZ())).get(0).getP()));

        for (Triple triple : colliders) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
                graph.removeEdge(a, b);
                graph.removeEdge(c, b);
                graph.addDirectedEdge(a, b);
                graph.addDirectedEdge(c, b);
            }
        }

        for (Triple triple : noncolliders) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.addUnderlineTriple(a, b, c);
        }

        for (Triple triple : ambiguous) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.addAmbiguousTriple(a, b, c);
        }
    }

    private boolean knowledgeAllowsCollider(Node a, Node b, Node c) {
        return !knowledge.isForbidden(a.getName(), b.getName()) && !knowledge.isForbidden(c.getName(), b.getName());
    }

    private List<PValue> getAllPValues(Node a, Node c, int depth, Graph graph, IndependenceTest test) {
        List<Node> adja = graph.getAdjacentNodes(a);
        List<Node> adjc = graph.getAdjacentNodes(c);

        adja.remove(c);
        adjc.remove(a);

        List<List<Node>> adj = new ArrayList<>();
        adj.add(adja);
        adj.add(adjc);

        List<PValue> pValues = new ArrayList<>();

        for (List<Node> _adj : adj) {
            DepthChoiceGenerator cg1 = new DepthChoiceGenerator(_adj.size(), depth);
            int[] comb2;

            while ((comb2 = cg1.next()) != null) {
                List<Node> s = GraphUtils.asList(comb2, _adj);

                test.isIndependent(a, c, s);
                PValue _p = new PValue(test.getPValue(), s);
                if (pValues.contains(_p)) continue;
                pValues.add(_p);
            }
        }

        return pValues;
    }

    private boolean existsSepsetFromList(List<PValue> pValues, double alpha) {
        if (pValues.isEmpty()) return false;

        List<Double> _pValues = new ArrayList<>();

        for (PValue p : pValues) {
            _pValues.add(p.getP());
        }

        double cutoff = StatUtils.fdrCutoff(alpha, _pValues, false, false);

        for (double p : _pValues) {
            if (p > cutoff) return true;
        }

        return false;
    }

    /**
     * The FDR q to use for the orientation search.
     */
    private double getFdrQ() {
        return fdrQ;
    }
}

