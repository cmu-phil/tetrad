///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;


/**
 * Implements the Really Fast Causal Inference (RFCI) algorithm, which aims to do a correct inference of inferrable
 * causal structure under the assumption that unmeasured common causes of variables in the data may exist. The graph
 * returned is slightly different from the partial ancestral graph (PAG) returned by the FCI algorithm. The goal of of
 * the algorithm is to avoid certain expensive steps in the FCI procedure in a correct way. This was introduced here:
 * <p>
 * Colombo, D., Maathuis, M. H., Kalisch, M., &amp; Richardson, T. S. (2012). Learning high-dimensional directed acyclic
 * graphs with latent and selection variables. The Annals of Statistics, 294-321.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author josephramsey
 * @author Choh-Man Teng
 * @version $Id: $Id
 * @see Fci
 * @see Knowledge
 */
public final class Rfci implements IGraphSearch {
    /**
     * The variables to search over (optional)
     */
    private final List<Node> variables = new ArrayList<>();
    /**
     * The independence test to use.
     */
    private IndependenceTest test;
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
    private Knowledge knowledge = new Knowledge();
    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * The depth for the fast adjacency search.
     */
    private int depth = -1;
    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;
    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;
    private boolean replicatingGraph = false;

    /**
     * Constructs a new RFCI search for the given independence test and background knowledge.
     *
     * @param test a {@link IndependenceTest} object
     */
    public Rfci(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.test = test;
        this.variables.addAll(test.getVariables());
    }

    /**
     * Constructs a new RFCI search for the given independence test and background knowledge and a list of variables to
     * search over.
     *
     * @param test a {@link IndependenceTest} object
     * @param searchVars       a {@link java.util.List} object
     */
    public Rfci(IndependenceTest test, List<Node> searchVars) {
        if (test == null) {
            throw new NullPointerException();
        }

        this.test = test;
        this.variables.addAll(test.getVariables());

        List<Node> remVars = new ArrayList<>();
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

    /**
     * Runs the search and returns the RFCI PAG.
     *
     * @return This PAG.
     */
    public Graph search() throws InterruptedException {
        return search(getTest().getVariables());
    }

    /**
     * Searches of a specific sublist of nodes.
     *
     * @param nodes The sublist.
     * @return The RFCI PAG
     * @throws InterruptedException If the search is interrupted.
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        nodes = new ArrayList<>(nodes);

        Fas fas = new Fas(getTest());
        fas.setReplicatingGraph(replicatingGraph);
        return search(fas, nodes);
    }

    /**
     * Runs the search and returns the RFCI PAG.
     *
     * @param fas   The type of FAS to use for the initial step.
     * @param nodes The nodes to search over.
     * @return The RFCI PAG.
     * @throws InterruptedException If the search is interrupted.
     */
    public Graph search(Fas fas, List<Node> nodes) throws InterruptedException {
        long beginTime = MillisecondTimes.timeMillis();
        test.setVerbose(verbose);

        if (verbose) {
            TetradLogger.getInstance().log("Starting RFCI algorithm.");
            TetradLogger.getInstance().log("Independence test = " + getTest() + ".");
        }

        setMaxDiscriminatingPathLength(this.maxDiscriminatingPathLength);

        this.graph = new EdgeListGraph(nodes);

        long start1 = MillisecondTimes.timeMillis();

        fas.setKnowledge(getKnowledge());
        fas.setDepth(this.depth);
        fas.setVerbose(this.verbose);
        try {
            this.graph = fas.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.graph.reorientAllWith(Endpoint.CIRCLE);
        this.sepsets = fas.getSepsets();

        long stop1 = MillisecondTimes.timeMillis();
        long start2 = MillisecondTimes.timeMillis();

        FciOrient orient = new FciOrient(R0R4StrategyTestBased.defaultConfiguration(test, new Knowledge()));

        // For RFCI always executes R5-10
        orient.setCompleteRuleSetUsed(true);

        // The original FCI, with or without JiJi Zhang's orientation rules
        orient.fciOrientbk(getKnowledge(), this.graph, this.variables);
        ruleR0_RFCI(getRTuples());  // RFCI Algorithm 4.4

        orient.finalOrientation(this.graph);

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - beginTime;

        long stop2 = MillisecondTimes.timeMillis();

        if (verbose) {
            TetradLogger.getInstance().log("Elapsed time adjacency search = " + (stop1 - start1) / 1000L + "s");
            TetradLogger.getInstance().log("Elapsed time orientation search = " + (stop2 - start2) / 1000L + "s");
        }

        return this.graph;
    }

    public IndependenceTest getTest() {
        return test;
    }

    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();

        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(String.format("The nodes of the proposed new test are not equal list-wise\n" +
                                                             "to the nodes of the existing test."));
        }

        this.test = test;
    }

    /**
     * Sets the maximum number of variables conditioned on in any test.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * Returns the elapsed time of the search.
     *
     * @return This time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns the map from node pairs to sepsets found in search.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Returns the knowledge used in search.
     *
     * @return This knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge used in search.
     *
     * @param knowledge This knoweldge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the maximum length of any discriminating path, or -1 of unlimited.
     *
     * @return This number.
     */
    public int getMaxDiscriminatingPathLength() {
        return this.maxDiscriminatingPathLength == Integer.MAX_VALUE ? -1 : this.maxDiscriminatingPathLength;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        }

        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Returns whether verbose output should be printed.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private Set<Node> getSepset(Node i, Node k) {
        return this.sepsets.get(i, k);
    }

    /**
     * RFCI Algorithm 4.4 (Colombo et al, 2012) Orient colliders
     */
    private void ruleR0_RFCI(List<Node[]> rTuples) throws InterruptedException {
        List<Node[]> lTuples = new ArrayList<>();

        List<Node> nodes = this.graph.getNodes();

        // process tuples in rTuples
        while (!rTuples.isEmpty()) {
            Node[] thisTuple = rTuples.remove(0);

            Node i = thisTuple[0];
            Node j = thisTuple[1];
            Node k = thisTuple[2];

            Set<Node> nodes1 = getSepset(i, k);

            if (nodes1 == null) continue;

            Set<Node> sepSet = new HashSet<>(nodes1);
            sepSet.remove(j);

            boolean independent1 = false;
            if (this.knowledge.noEdgeRequired(i.getName(), j.getName()))  // if BK allows
            {
                try {
                    independent1 = this.test.checkIndependence(i, j, sepSet).isIndependent();
                } catch (Exception e) {
                    independent1 = true;
                }
            }

            boolean independent2 = false;
            if (this.knowledge.noEdgeRequired(j.getName(), k.getName()))  // if BK allows
            {
                try {
                    independent2 = this.test.checkIndependence(j, k, sepSet).isIndependent();
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
                    this.graph.removeEdge(i, j);
                }
                if (independent2) {
                    setMinSepSet(sepSet, j, k);
                    this.graph.removeEdge(j, k);
                }

                // add new unshielded tuples to rTuples
                for (Node thisNode : nodes) {
                    List<Node> adjacentNodes = this.graph.getAdjacentNodes(thisNode);
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

        // orient colliders (similar to original FCI ruleR0)
        for (Node[] thisTuple : lTuples) {
            Node i = thisTuple[0];
            Node j = thisTuple[1];
            Node k = thisTuple[2];

            Set<Node> sepset = getSepset(i, k);

            if (sepset == null) {
                continue;
            }

            if (!sepset.contains(j)
                && this.graph.isAdjacentTo(i, j) && this.graph.isAdjacentTo(j, k)) {

                if (!FciOrient.isArrowheadAllowed(i, j, graph, knowledge)) {
                    continue;
                }

                if (!FciOrient.isArrowheadAllowed(k, j, graph, knowledge)) {
                    continue;
                }

                this.graph.setEndpoint(i, j, Endpoint.ARROW);
                this.graph.setEndpoint(k, j, Endpoint.ARROW);
            }
        }

    }

    /**
     * collect in rTupleList all unshielded tuples
     */
    private List<Node[]> getRTuples() {
        List<Node[]> rTuples = new ArrayList<>();
        List<Node> nodes = this.graph.getNodes();

        for (Node j : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(this.graph.getAdjacentNodes(j));

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node i = adjacentNodes.get(combination[0]);
                Node k = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (!this.graph.isAdjacentTo(i, k)) {
                    Node[] newTuple = {i, j, k};
                    rTuples.add(newTuple);
                }

            }
        }

        return (rTuples);
    }

    /**
     * set the sepSet of x and y to the minimal such subset of the given sepSet and remove the edge <x, y> if background
     * knowledge allows
     */
    private void setMinSepSet(Set<Node> _sepSet, Node x, Node y) throws InterruptedException {
        Set<Node> empty = Collections.emptySet();
        boolean independent;

        List<Node> sepSet = new ArrayList<>(_sepSet);
        Collections.sort(sepSet);

        try {
            independent = this.test.checkIndependence(x, y, empty).isIndependent();
        } catch (Exception e) {
            independent = false;
        }

        if (independent) {
            getSepsets().set(x, y, empty);
            return;
        }

        int sepSetSize = sepSet.size();
        for (int i = 1; i <= sepSetSize; i++) {
            ChoiceGenerator cg = new ChoiceGenerator(sepSetSize, i);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Set<Node> condSet = GraphUtils.asSet(combination, sepSet);

                independent = this.test.checkIndependence(x, y, condSet).isIndependent();

                if (independent) {
                    getSepsets().set(x, y, condSet);
                    return;
                }
            }
        }
    }

    /**
     * Sets the state of whether the graph is set to replicate or not during the search process.
     *
     * @param replicatingGraph If true, the graph is in a replicating state. If false, it is not.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }
}





