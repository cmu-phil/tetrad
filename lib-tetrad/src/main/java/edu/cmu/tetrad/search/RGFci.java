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

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * <p></p>This is experimental; you should use it. It will probably be
 * removed from the repository.</p>
 * <p>Not sure what this does.</p>
 * Replaces the FAS search in the previous version with GES followed by PC adjacency removals for more accuracy.
 * Uses conservative collider orientation. Gets sepsets for X---Y from among adjacencies of X or of Y. -jdramsey 3/10/2015
 * <p/>
 * Previous:
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in Fci.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p/>
 * This class is based off a copy of Fci.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 * @deprecated
 */
public final class RGFci {

    private double alpha1 = 0.001;
    /**
     * The PAG being constructed.
     */
    private Graph graph;

    /**
     * The background knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The variables to search over (optional)
     */
    private List<Node> variables = new ArrayList<Node>();

    private IndependenceTest independenceTest;

    /**
     * change flag for repeat rules
     */
    private boolean changeFlag = true;

    /**
     * flag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = false;

    /**
     * True iff the possible dsep search is done.
     */
    private boolean possibleDsepSearchDone = true;

    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxPathLength = -1;

    /**
     * The depth for the fast adjacency search.
     */
    private int depth = -1;

    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * *** use RFCI ******
     */
    // set to true for now
    private boolean rfciUsed = true;

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;

    ICovarianceMatrix covarianceMatrix;

    int sampleSize;
    private double penaltyDiscount = 2;

    /**
     * Map from variables to their column indices in the data set.
     */
    private ConcurrentMap<Node, Integer> hashIndices;
    private PrintStream out = System.out;
    private boolean useGesOrientation = false;
    private Graph truePag;
    private SepsetMap possibleDsepSepsets = new SepsetMap();

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public RGFci(IndependenceTest independenceTest) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.alpha1 = independenceTest.getAlpha();
        this.variables.addAll(independenceTest.getVariables());
        buildIndexing(independenceTest.getVariables());
    }

    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<Node, Integer>();
        for (Node node : nodes) {
            this.hashIndices.put(node, variables.indexOf(node));
        }
    }

    /**
     * Constructs a new FCI search for the given independence test and background knowledge and a list of variables to
     * search over.
     */
    public RGFci(IndependenceTest independenceTest, List<Node> searchVars) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
        this.alpha1 = independenceTest.getAlpha();

        Set<Node> remVars = new HashSet<Node>();
        for (Node node1 : this.variables) {
            boolean search = false;
            for (Node node2 : searchVars) {
                if (node1.getName().equals(node2.getName())) {
                    search = true;
                }
            }
            if (!search) {
                remVars.add(node1);
            }
        }
        this.variables.removeAll(remVars);
        buildIndexing(independenceTest.getVariables());
    }

    //========================PUBLIC METHODS==========================//

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    public Graph search() {
        List<Node> nodes = getIndependenceTest().getVariables();

        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        setMaxPathLength(maxPathLength);

        this.graph = new EdgeListGraph(nodes);

//        Fas fas = new Fas(new IndTestFisherZ(independenceTest.getCov(), 0.001));
//        fas.setKnowledge(getKnowledge());
//        fas.setDepth(depth);
//        fas.setVerbose(verbose);
//        graph = fas.search();
//
        covarianceMatrix = independenceTest.getCov();
        sampleSize = covarianceMatrix.getSampleSize();
        Fgs ges = new Fgs(covarianceMatrix);
        ges.setKnowledge(getKnowledge());
        ges.setPenaltyDiscount(getPenaltyDiscount());
        ges.setFaithfulnessAssumed(true);
        ges.setVerbose(false);
        ges.setLog(false);
        ges.setNumPatternsToStore(0);
        this.graph = ges.search();

        System.out.println(graph.getNumEdges() + " num edges in graph");

        // Remove extraneous edges.
        for (Edge edge : graph.getEdges()) {
            Node i = edge.getNode1();
            Node k = edge.getNode2();

            if (getSepset(i, k) != null) {
                graph.removeEdge(edge);
            }
        }

        graph.reorientAllWith(Endpoint.CIRCLE);

        // RFCI (Colombo et al, 2012)
        fciOrientbk(getKnowledge(), graph, variables);
        ruleR0_RFCI(getRTuples());  // RFCI Algorithm 4.4
        doFinalOrientation();

        logger.log("graph", "Returning graph: " + graph);

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

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public boolean isPossibleDsepSearchDone() {
        return possibleDsepSearchDone;
    }

    public void setPossibleDsepSearchDone(boolean possibleDsepSearchDone) {
        this.possibleDsepSearchDone = possibleDsepSearchDone;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength == Integer.MAX_VALUE ? -1 : maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength == -1
                ? Integer.MAX_VALUE : maxPathLength;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public ICovarianceMatrix getCovMatrix() {
        return covarianceMatrix;
    }

    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    public PrintStream getOut() {
        return out;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public void setUseGesOrientation(boolean useGesOrientation) {
        this.useGesOrientation = useGesOrientation;
    }

    //===========================PRIVATE METHODS=========================//

    /**
     * Orients colliders in the graph.  (FCI Step C)
     * <p/>
     * Zhang's step F3, rule R0.
     */
    private void ruleR0() {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (isCollider(a, b, c)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    TetradLogger.getInstance().log("info", "Collider " + a + "*->" + b + "<-*" + c);
                    printWrongColliderMessage(a, b, c, "R0");
                }
            }
        }

        getIndependenceTest().setAlpha(alpha1);
    }

    ////////////////////////////////////////////
    // RFCI Algorithm 4.4 (Colombo et al, 2012)
    // Orient colliders
    ////////////////////////////////////////////
    private void ruleR0_RFCI(List<Node[]> rTuples) {
        List<Node[]> lTuples = new ArrayList<Node[]>();

        List<Node> nodes = graph.getNodes();

        ///////////////////////////////
        // process tuples in rTuples
        while (!rTuples.isEmpty()) {
            Node[] thisTuple = rTuples.remove(0);

            Node i = thisTuple[0];
            Node j = thisTuple[1];
            Node k = thisTuple[2];

            final List<Node> nodes1 = getSepset(i, k); //  sepsets.get(i, k);

            if (nodes1 == null) {
                lTuples.add(thisTuple);
                continue;
            }

            List<Node> sepSet = new ArrayList<Node>(nodes1);
            sepSet.remove(j);

            boolean independent1 = false;
            if (knowledge.noEdgeRequired(i.getName(), j.getName()))  // if BK allows
            {
                try {
                    independent1 = independenceTest.isIndependent(i, j, sepSet);
                } catch (Exception e) {
                    independent1 = true;
                }
            }

            boolean independent2 = false;
            if (knowledge.noEdgeRequired(j.getName(), k.getName()))  // if BK allows
            {
                try {
                    independent2 = independenceTest.isIndependent(j, k, sepSet);
                } catch (Exception e) {
                    independent2 = true;
                }
            }

            if (!independent1 && !independent2) {
                lTuples.add(thisTuple);
            } else {
                // set sepSets to minimal separating sets
                if (independent1) {
                    getSepset(i, j);
                    graph.removeEdge(i, j);
                }
                if (independent2) {
                    getSepset(j, k);
                    graph.removeEdge(j, k);
                }

                // add new unshielded tuples to rTuples
                for (Node thisNode : nodes) {
                    List<Node> adjacentNodes = graph.getAdjacentNodes(thisNode);
                    if (independent1) // <i, ., j>
                    {
                        if (adjacentNodes.contains(i) && adjacentNodes.contains(j)) {
                            Node[] newTuple = {i, thisNode, j};
                            rTuples.add(newTuple);
                        }
                    }
                    if (independent2) // <j, ., k>
                    {
                        if (adjacentNodes.contains(j) && adjacentNodes.contains(k)) {
                            Node[] newTuple = {j, thisNode, k};
                            rTuples.add(newTuple);
                        }
                    }
                }

                // remove tuples involving either (if independent1) <i, j>
                // or (if independent2) <j, k> from rTuples
                Iterator<Node[]> iter = rTuples.iterator();
                while (iter.hasNext()) {
                    Node[] curTuple = iter.next();
                    if ((independent1 && (curTuple[1] == i) &&
                            ((curTuple[0] == j) || (curTuple[2] == j)))
                            ||
                            (independent2 && (curTuple[1] == k) &&
                                    ((curTuple[0] == j) || (curTuple[2] == j)))
                            ||
                            (independent1 && (curTuple[1] == j) &&
                                    ((curTuple[0] == i) || (curTuple[2] == i)))
                            ||
                            (independent2 && (curTuple[1] == j) &&
                                    ((curTuple[0] == k) || (curTuple[2] == k)))) {
                        iter.remove();
                    }
                }

                // remove tuples involving either (if independent1) <i, j>
                // or (if independent2) <j, k> from lTuples
                iter = lTuples.iterator();
                while (iter.hasNext()) {
                    Node[] curTuple = iter.next();
                    if ((independent1 && (curTuple[1] == i) &&
                            ((curTuple[0] == j) || (curTuple[2] == j)))
                            ||
                            (independent2 && (curTuple[1] == k) &&
                                    ((curTuple[0] == j) || (curTuple[2] == j)))
                            ||
                            (independent1 && (curTuple[1] == j) &&
                                    ((curTuple[0] == i) || (curTuple[2] == i)))
                            ||
                            (independent2 && (curTuple[1] == j) &&
                                    ((curTuple[0] == k) || (curTuple[2] == k)))) {
                        iter.remove();
                    }
                }
            }
        }

        ///////////////////////////////////////////////////////
        // orient colliders (similar to original FCI ruleR0)
        for (Node[] thisTuple : lTuples) {
            Node i = thisTuple[0];
            Node j = thisTuple[1];
            Node k = thisTuple[2];

            if (isCollider(i, j, k)) {
                graph.setEndpoint(i, j, Endpoint.ARROW);
                graph.setEndpoint(k, j, Endpoint.ARROW);
                TetradLogger.getInstance().log("info", "Collider " + i + "*->" + j + "<-*" + k);
                printWrongColliderMessage(i, j, k, "R0_RFCI");
            }
        }

        getIndependenceTest().setAlpha(alpha1);
    }

    /**
     * Should be unshielded.
     */
    private boolean isCollider(Node i, Node j, Node k) {
        return isColliderSepset(i, j, k, graph);
    }

    private void printWrongColliderMessage(Node a, Node b, Node c, String location) {
        if (truePag != null && graph.isDefCollider(a, b, c) && !truePag.isDefCollider(a, b, c)) {
            boolean shielded = truePag.isAdjacentTo(a, c);

            System.out.println(location + ": Orienting collider by mistake: " + a + "*->" + b + "<-*" + c
                    + " " + (shielded ? "Shielded in true graph" : ""));
        }

        if (truePag != null && graph.isDefNoncollider(a, b, c) && !truePag.isDefNoncollider(a, b, c)) {
            boolean shielded = truePag.isAdjacentTo(a, c);

            System.out.println(location + ": Orienting noncollider by mistake: " + a + "*-o" + b + "o-*" + c
                    + " " + (shielded ? "Shielded in true graph" : ""));
        }

    }

    // Doesn't work.
    private boolean isColliderB(Node i, Node j, Node k, Graph graph) {
        Set<Node> empty = Collections.EMPTY_SET;
        Set<Node> ti = Collections.singleton(i);
        Set<Node> tk = Collections.singleton(k);

        double bumpi = insertEval(i, j, empty, getNaYX(i, j, graph), graph, hashIndices);
        double bumpk = insertEval(k, j, empty, getNaYX(i, j, graph), graph, hashIndices);
        double bumpik = insertEval(i, j, tk, getNaYX(i, j, graph), graph, hashIndices);
        double bumpki = insertEval(k, j, ti, getNaYX(i, j, graph), graph, hashIndices);

        if (bumpi > 0 && bumpi > bumpk) {
            if (bumpki > 0 && bumpki > bumpk) {
                return true;
            }
        }

        if (bumpk > 0 && bumpk > bumpi) {
            if (bumpik > 0 && bumpik > bumpi) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find all nodes that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
     * directed edge).
     */
    private static Set<Node> getNaYX(Node x, Node y, Graph graph) {
        List<Edge> yEdges = graph.getEdges(y);
        Set<Node> nayx = new HashSet<Node>();

        for (Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            Node z = edge.getDistalNode(y);

            if (!graph.isAdjacentTo(z, x)) {
                continue;
            }

            nayx.add(z);
        }

        return nayx;
    }

    private boolean isColliderSepset(Node i, Node j, Node k, Graph graph) {
//        final List<Node> sepset = getSepset(i, k, graph);
//        return sepset != null && !sepset.contains(j);


        List<List<Node>> sepsets = getSepsets(i, k);

        List<Node> possibleDSepSepset = possibleDsepSepsets.get(i, k);
        if (possibleDSepSepset != null) {
            sepsets.add(possibleDSepSepset);
        }

        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }

    private double insertEval(Node x, Node y, Set<Node> t, Set<Node> naYX, Graph graph,
                              Map<Node, Integer> hashIndices) {
        Set<Node> set1 = new HashSet<Node>(naYX);
        set1.addAll(t);
        List<Node> paY = graph.getParents(y);
        set1.addAll(paY);
        Set<Node> set2 = new HashSet<Node>(set1);
        set1.add(x);

        return scoreGraphChange(y, set1, set2, hashIndices);
    }

    private double scoreGraphChange(Node y, Set<Node> parents1,
                                    Set<Node> parents2, Map<Node, Integer> hashIndices) {
        int yIndex = hashIndices.get(y);

        double score1, score2;

        int[] parentIndices1 = new int[parents1.size()];

        int count = -1;
        for (Node parent : parents1) {
            parentIndices1[++count] = hashIndices.get(parent);
        }

        score1 = localSemScore(yIndex, parentIndices1);

        int[] parentIndices2 = new int[parents2.size()];

        int count2 = -1;
        for (Node parent : parents2) {
            parentIndices2[++count2] = hashIndices.get(parent);
        }

        score2 = localSemScore(yIndex, parentIndices2);

        return score1 - score2;
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     */
    private double localSemScore(int i, int[] parents) {
        ICovarianceMatrix cov = getCovMatrix();
        double residualVariance = cov.getValue(i, i);
        int n = sampleSize();
        int p = parents.length;
        int k = (p * (p + 1)) / 2 + p;
        TetradMatrix covxx = getSelection1(cov, parents);
        TetradMatrix covxxInv = covxx.inverse();
        TetradVector covxy = getSelection2(cov, parents, i);
        TetradVector b = covxxInv.times(covxy);
        residualVariance -= covxy.dotProduct(b);

        if (residualVariance <= 0 && verbose) {
            System.out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / cov.getValue(i, i)));
            return Double.NaN;
        }

        double c = getPenaltyDiscount();
        return -n * Math.log(residualVariance) - c * k * Math.log(n);
    }

    public TetradMatrix getSelection1(ICovarianceMatrix cov, int[] rows) {
        TetradMatrix m = new TetradMatrix(rows.length, rows.length);

        for (int i = 0; i < rows.length; i++) {
            for (int j = i; j < rows.length; j++) {
                final double value = cov.getValue(rows[i], rows[j]);
                m.set(i, j, value);
                m.set(j, i, value);
            }
        }

        return m;
    }

    private TetradVector getSelection2(ICovarianceMatrix cov, int[] rows, int k) {
        TetradVector m = new TetradVector(rows.length);

        for (int i = 0; i < rows.length; i++) {
            final double value = cov.getValue(rows[i], k);
            m.set(i, value);
        }

        return m;
    }

    private int sampleSize() {
        return sampleSize;
    }

    private List<Node> getSepset(Node i, Node k) {
        if (possibleDsepSepsets.get(i, k) != null) {
            return possibleDsepSepsets.get(i, k);
        }

        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);

        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) return v;
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) return v;
                }
            }
        }

        return null;
    }

    private List<List<Node>> getSepsets(Node i, Node k) {
        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        List<List<Node>> sepsets = new ArrayList<List<Node>>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }


    ////////////////////////////////////////////////
    // collect in rTupleList all unshielded tuples
    ////////////////////////////////////////////////
    private List<Node[]> getRTuples() {
        List<Node[]> rTuples = new ArrayList<Node[]>();
        List<Node> nodes = graph.getNodes();

        for (Node j : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(j);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node i = adjacentNodes.get(combination[0]);
                Node k = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (!graph.isAdjacentTo(i, k)) {
                    Node[] newTuple = {i, j, k};
                    rTuples.add(newTuple);
                }

            }
        }

        return (rTuples);
    }

    //////////////////////////////////////////////////
    // Orients the graph according to rules for RFCI
    //////////////////////////////////////////////////
    private void doFinalOrientation() {


        SepsetProducer sepsets = new SepsetsMaxPValue(graph, getIndependenceTest(), possibleDsepSepsets, depth);
        FciOrient orient = new FciOrient(sepsets);

        // This loop handles Zhang's rules R1-R3 (same as in the original FCI)
        changeFlag = true;

        while (changeFlag) {
            changeFlag = false;
            orient.setChangeFlag(false);
            orient.rulesR1R2cycle(graph);
            orient.ruleR3(graph);
            changeFlag = orient.isChangeFlag();
            ruleR4();   // some changes to the original R4 inline
        }

        // For RFCI always executes R5-10

        // Now, by a remark on page 100 of Zhang's dissertation, we apply rule
        // R5 once.
        orient.ruleR5(graph);

        // Now, by a further remark on page 102, we apply R6,R7 as many times
        // as possible.
        changeFlag = true;

        while (changeFlag) {
            changeFlag = false;
            orient.setChangeFlag(false);
            orient.ruleR6R7(graph);
            changeFlag = orient.isChangeFlag();
        }

        // Finally, we apply R8-R10 as many times as possible.
        changeFlag = true;

        while (changeFlag) {
            changeFlag = false;
            orient.setChangeFlag(false);
            orient.rulesR8R9R10(graph);
            changeFlag = orient.isChangeFlag();
        }
    }

    /**
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from L to A with each node on the path (except L) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * L....A --> C
     * </pre>
     * <p/>
     * This is Zhang's rule R4, discriminating undirectedPaths.
     */
    private void ruleR4() {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A<-*Bo-*C
            List<Node> possA = graph.getNodesOutTo(b, Endpoint.ARROW);
            List<Node> possC = graph.getNodesInTo(b, Endpoint.CIRCLE);

            for (Node a : possA) {
                for (Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    LinkedList<Node> reachable = new LinkedList<Node>();
                    reachable.add(a);
                    reachablePathFind(a, b, c, reachable);

                    // process only one disciminating path per execution of this method
                    // because edges might have been removed and nodes in possA and possC
                    // might not be adjacent to b anymore
                    if ((rfciUsed) && changeFlag) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * a method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. </p> The body of
     * a DDP consists of colliders that are parents of c.
     */
    private void reachablePathFind(Node a, Node b, Node c,
                                   LinkedList<Node> reachable) {

        Map<Node, Node> next = new HashMap<Node, Node>();   // RFCI: stores the next node in the disciminating path
        // path containing the nodes in the traiangle
        next.put(a, b);
        next.put(b, c);

        Set<Node> cParents = new HashSet<Node>(graph.getParents(c));

        // Needed to avoid cycles in failure case.
        Set<Node> visited = new HashSet<Node>();
        visited.add(b);
        visited.add(c);

        // We don't want to include a,b,or c on the path, so they are added to
        // the "visited" set.  b and c are added explicitly here; a will be
        // added in the first while iteration.
        while (reachable.size() > 0) {
            Node x = reachable.removeFirst();
            visited.add(x);

            // Possible DDP path endpoints.
            List<Node> pathExtensions = graph.getNodesInTo(x, Endpoint.ARROW);
            pathExtensions.removeAll(visited);

            for (Node d : pathExtensions) {
                // If d is reachable and not adjacent to c, its a DDP
                // endpoint, so do DDP orientation. Otherwise, if d <-> c,
                // add d to the list of reachable nodes.
                if (!graph.isAdjacentTo(d, c)) {
                    if (rfciUsed) // RFCI
                    {
                        next.put(d, x);
                        doDdpOrientation_RFCI(d, a, b, c, next);
                    } else  // non-RFCI
                    {
                        // Check whether <a, b, c> should be reoriented given
                        // that d is not adjacent to c; if so, orient and stop.
                        doDdpOrientation(d, a, b, c);
                    }
                    return;
                } else if (cParents.contains(d)) {
                    if (graph.getEndpoint(x, d) == Endpoint.ARROW) {
                        reachable.add(d);

                        // RFCI: only record the next node of the first (shortest) occurence
                        if (next.get(d) == null) {
                            next.put(d, x);  // next node of d is x in the shortest path from a
                        }
                    }
                }
            }
        }
    }

    /**
     * Orients the edges inside the definte discriminating path triangle. Takes the left endpoint, and a,b,c as
     * arguments.
     */
    private void doDdpOrientation(Node d, Node a, Node b, Node c) {
        List<Node> sepset = getSepset(d, c);

        if (sepset == null) return;

        if (sepset == null) {
            throw new IllegalArgumentException("The edge from d to c must have " +
                    "been removed at this point.");
        }

        if (sepset.contains(b)) {
            graph.setEndpoint(c, b, Endpoint.TAIL);
            logger.log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Definite discriminating path d = " + d, graph.getEdge(b, c)));
            changeFlag = true;
        } else {
            if (!isArrowpointAllowed(a, b)) {
                return;
            }

            if (!isArrowpointAllowed(c, b)) {
                return;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
            logger.log("colliderOrientations", SearchLogUtils.colliderOrientedMsg("Definite discriminating path.. d = " + d, a, b, c));
            changeFlag = true;
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Orients the edges inside the definte discriminating path triangle.
    // Arguments: the left endpoint (i), the last three points (l, j, k),
    // and the hashMap (next) which contains the next nodes of the path
    /////////////////////////////////////////////////////////////////////////
    private void doDdpOrientation_RFCI(Node i, Node l, Node j, Node k,
                                       Map<Node, Node> next) {

        independenceTest.setAlpha(alpha1);
        List<Node> nodes = graph.getNodes();

        List<Node> sepset = getSepset(i, k);

        if (sepset == null) return;

//        if (sepset == null) {
//            throw new IllegalArgumentException("The edge from i to k needs to have " +
//											   "been removed at this point.");
//        }

        Node r = i;  // first node on the path

        while (r != k) {
            Node q = next.get(r);  // next node on the path after r

            if (knowledge.noEdgeRequired(r.getName(), q.getName()))  // if BK allows
            {
                List<Node> sepset1 = getSepset(i, k);

                if (sepset1 == null) continue;

                List<Node> sepSet2 = new ArrayList<Node>(sepset1);
                sepSet2.remove(r);
                sepSet2.remove(q);

                for (int setSize = 0; setSize <= sepSet2.size(); setSize++) {
                    ChoiceGenerator cg = new ChoiceGenerator(sepSet2.size(), setSize);
                    int[] combination;

                    while ((combination = cg.next()) != null) {
                        List<Node> condSet = GraphUtils.asList(combination, sepSet2);

                        boolean indep;
                        try {
                            indep = independenceTest.isIndependent(r, q, condSet);
                        } catch (Exception e) {
                            indep = false;
                        }

                        if (indep) {
//                            getSepsets().set(r, q, condSet);

                            // add new unshielded tuples to rTuples
                            List<Node[]> rTuples = new ArrayList<Node[]>();
                            for (Node thisNode : nodes) {
                                List<Node> adjacentNodes = graph.getAdjacentNodes(thisNode);
                                if (adjacentNodes.contains(r) && adjacentNodes.contains(q)) {
                                    Node[] newTuple = {r, thisNode, q};
                                    rTuples.add(newTuple);
                                }

                            }

                            graph.removeEdge(r, q);
                            changeFlag = true;

                            ruleR0_RFCI(rTuples);   // Algorithm 4.4 (Colombo et al, 2012)

                            return;
                        }

                    }
                }
            }

            r = q;

        }

        // similar to original rule R4 orientation of the triangle
        if (sepset.contains(j)) {
            //            System.out.println("DDP orientation: " + c + " *-- " + b);

            if (!isArrowpointAllowed(j, k)) {
                return;
            }

            graph.setEndpoint(j, k, Endpoint.ARROW);
            graph.setEndpoint(k, j, Endpoint.TAIL);

            //logger.log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Definite discriminating path d = " + d, graph.getEdge(b, c)));
            changeFlag = true;
        } else {

            if (!isArrowpointAllowed(l, j) || !isArrowpointAllowed(j, l)
                    || !isArrowpointAllowed(j, k) || !isArrowpointAllowed(k, j)) {
                return;
            }

            graph.setEndpoint(l, j, Endpoint.ARROW);
            graph.setEndpoint(j, l, Endpoint.ARROW);
            graph.setEndpoint(j, k, Endpoint.ARROW);
            graph.setEndpoint(k, j, Endpoint.ARROW);
            //logger.log("colliderOrientations", SearchLogUtils.colliderOrientedMsg("Definite discriminating path.. d = " + d, a, b, c));
            changeFlag = true;
        }

    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(IKnowledge bk, Graph graph, List<Node> variables) {
        logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it =
             bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);


            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            changeFlag = true;
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it =
             bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from*->to (?)
            // Orient from-->to

//            System.out.println("Rule R8: Orienting " + from + "-->" + to);

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            changeFlag = true;
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("info", "Finishing BK Orientation.");
    }


    /**
     * Helper method. Appears to check if an arrowpoint is permitted by background knowledge.
     *
     * @param x The possible other node.
     * @param y The possible point node.
     * @return Whether the arrowpoint is allowed.
     */
    private boolean isArrowpointAllowed(Node x, Node y) {
        if (graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (graph.getEndpoint(y, x) == Endpoint.ARROW) {
//            return true;
            if (!knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        if (graph.getEndpoint(y, x) == Endpoint.TAIL) {
            if (!knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        return graph.getEndpoint(y, x) == Endpoint.CIRCLE;
    }

    public void setTruePag(Graph truePag) {
        this.truePag = truePag;
    }

    public Graph getTruePag() {
        return truePag;
    }

    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }
}




