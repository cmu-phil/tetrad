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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;


/**
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in Fci.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of Fci.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 */
public final class Rfci implements GraphSearch {

    /**
     * The RFCI-PAG being constructed.
     */
    private Graph graph;

    /**
     * The SepsetMap being constructed.
     */
    private SepsetMap sepsets;

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
     * Elapsed time of last search.
     */
    private long elapsedTime;

    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private Graph truePag;


    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public Rfci(IndependenceTest independenceTest) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
    }

    /**
     * Constructs a new FCI search for the given independence test and background knowledge and a list of variables to
     * search over.
     */
    public Rfci(IndependenceTest independenceTest, List<Node> searchVars) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());

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

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    public Graph search() {
        return search(getIndependenceTest().getVariables());
    }

    public Graph search(List<Node> nodes) {
        return search(new FasStableConcurrent(getIndependenceTest()), nodes);
    }

    public Graph search(IFas fas, List<Node> nodes) {
        long beginTime = System.currentTimeMillis();

        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");

        setMaxPathLength(maxPathLength);

        this.graph = new EdgeListGraph(nodes);

        long start1 = System.currentTimeMillis();

        fas.setKnowledge(getKnowledge());
        fas.setDepth(depth);
        fas.setVerbose(verbose);
//        fas.setFci(true);
        graph = fas.search();
        graph.reorientAllWith(Endpoint.CIRCLE);
        this.sepsets = fas.getSepsets();

        long stop1 = System.currentTimeMillis();
        long start2 = System.currentTimeMillis();

        // The original FCI, with or without JiJi Zhang's orientation rules
        fciOrientbk(getKnowledge(), graph, variables);
        ruleR0_RFCI(getRTuples());  // RFCI Algorithm 4.4
        doFinalOrientation();

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - beginTime;

        logger.log("graph", "Returning graph: " + graph);
        long stop2 = System.currentTimeMillis();

        logger.log("info", "Elapsed time adjacency search = " + (stop1 - start1) / 1000L + "s");
        logger.log("info", "Elapsed time orientation search = " + (stop2 - start2) / 1000L + "s");

        return graph;
    }

    public SepsetMap getSepsets() {
        return this.sepsets;
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

    //===========================PRIVATE METHODS=========================//

    private List<Node> getSepset(Node i, Node k) {
        return this.sepsets.get(i, k);
    }


    private void printWrongColliderMessage(Node a, Node b, Node c, String location) {
        if (truePag != null && graph.isDefCollider(a, b, c) && !truePag.isDefCollider(a, b, c)) {
            System.out.println(location + ": Orienting collider by mistake: " + a + "*->" + b + "<-*" + c);
        }
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

            final List<Node> nodes1 = getSepset(i, k);

            if (nodes1 == null) continue;

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
                    setMinSepSet(sepSet, i, j);
                    graph.removeEdge(i, j);
                }
                if (independent2) {
                    setMinSepSet(sepSet, j, k);
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

            List<Node> sepset = getSepset(i, k);

            if (sepset == null) {
                continue;
            }

            if (!sepset.contains(j)
                    && graph.isAdjacentTo(i, j) && graph.isAdjacentTo(j, k)) {

                if (!isArrowpointAllowed(i, j)) {
                    continue;
                }

                if (!isArrowpointAllowed(k, j)) {
                    continue;
                }

                graph.setEndpoint(i, j, Endpoint.ARROW);
                graph.setEndpoint(k, j, Endpoint.ARROW);

                printWrongColliderMessage(i, j, k, "R0_RFCI");
            }
        }

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

    /////////////////////////////////////////////////////////////////////////////
    // set the sepSet of x and y to the minimal such subset of the given sepSet
    // and remove the edge <x, y> if background knowledge allows
    /////////////////////////////////////////////////////////////////////////////
    private void setMinSepSet(List<Node> sepSet, Node x, Node y) {
        // It is assumed that BK has been considered before calling this method
        // (for example, setting independent1 and independent2 in ruleR0_RFCI)
        /*
        // background knowledge requires this edge
		if (knowledge.noEdgeRequired(x.getName(), y.getName()))
		{
			return;
		}
		 */


        List<Node> empty = Collections.emptyList();
        boolean indep;

        try {
            indep = independenceTest.isIndependent(x, y, empty);
        } catch (Exception e) {
            indep = false;
        }

        if (indep) {
            getSepsets().set(x, y, empty);
            return;
        }

        int sepSetSize = sepSet.size();
        for (int i = 1; i <= sepSetSize; i++) {
            ChoiceGenerator cg = new ChoiceGenerator(sepSetSize, i);
            int[] combination;

            while ((combination = cg.next()) != null) {
                List<Node> condSet = GraphUtils.asList(combination, sepSet);

                try {
                    indep = independenceTest.isIndependent(x, y, condSet);
                } catch (Exception e) {
                    indep = false;
                }

                if (indep) {
                    getSepsets().set(x, y, condSet);
                    return;
                }
            }
        }
    }

    //////////////////////////////////////////////////
    // Orients the graph according to rules for RFCI
    //////////////////////////////////////////////////
    private void doFinalOrientation() {


        FciOrient orient = new FciOrient(new SepsetsSet(sepsets, independenceTest));

        // This loop handles Zhang's rules R1-R3 (same as in the original FCI)
        changeFlag = true;

        while (changeFlag) {
            changeFlag = false;
            orient.setChangeFlag(false);
            orient.rulesR1R2cycle(graph);
            orient.ruleR3(graph);
            changeFlag = orient.isChangeFlag();
            orient.ruleR4B(graph);   // some changes to the original R4 inline
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
            if (!knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        if (graph.getEndpoint(y, x) == Endpoint.TAIL) {
            if (!knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        return graph.getEndpoint(y, x) == Endpoint.CIRCLE;
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
}




