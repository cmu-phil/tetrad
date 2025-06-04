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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Modifies the PC algorithm to handle the deterministic case. Edges removals or orientations based on conditional
 * independence test involving deterministic relationships are not done.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author peterspirtes
 * @author josephramsey.
 * @version $Id: $Id
 * @see Fasd
 * @see Pc
 * @see Knowledge
 */
public class Pcd implements IGraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;
    /**
     * Forbidden and required edges for the search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Sepset information accumulated in the search.
     */
    private SepsetMap sepsets;
    /**
     * The maximum number of nodes conditioned on in the search. The default it 1000.
     */
    private int depth = 1000;
    /**
     * The graph that's constructed during the search.
     */
    private Graph graph;
    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;
    /**
     * True if cycles are to be prevented. Maybe expensive for large graphs (but also useful for large graphs).
     */
    private boolean guaranteeCpdag;
    /**
     * In an enumeration of triple types, these are the collider triples.
     */
    private Set<Triple> unshieldedColliders;
    /**
     * In an enumeration of triple types, these are the noncollider triples.
     */
    private Set<Triple> unshieldedNoncolliders;
    /**
     * The number of independence tests in the last search.
     */
    private int numIndependenceTests;
    /**
     * True iff the algorithm should be run with verbose output.
     */
    private boolean verbose;
    /**
     * True iff the algorithm should be run with False Discovery Rate tests.
     */
    private boolean fdr;

    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public Pcd(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }


    /**
     * Returns whether the algorithm should prevent cycles during the search.
     *
     * @return true if cycles should be prevented, false otherwise.
     */
    public boolean isGuaranteeCpdag() {
        return this.guaranteeCpdag;
    }

    /**
     * Sets whether the algorithm should prevent cycles during the search.
     *
     * @param guaranteeCpdag true if cycles should be prevented, false otherwise
     */
    public void setGuaranteeCpdag(boolean guaranteeCpdag) {
        this.guaranteeCpdag = guaranteeCpdag;
    }

    /**
     * Retrieves the IndependenceTest used by this method.
     *
     * @return The IndependenceTest used by this method.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Retrieves the Knowledge object used by this method.
     *
     * @return The Knowledge object used by this method.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object used by this method.
     *
     * @param knowledge The knowledge object used by this method.
     * @throws NullPointerException if knowledge is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * <p>Getter for the field <code>sepsets</code>.</p>
     *
     * @return the sepset map from the most recent search. Non-null after the first call to <code>search()</code>.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * <p>Getter for the field <code>depth</code>.</p>
     *
     * @return the current depth of search--that is, the maximum number of conditioning nodes for any conditional
     * independence checked.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the depth of the search--that is, the maximum number of conditioning nodes for any conditional independence
     * checked.
     *
     * @param depth The depth of the search. The default is 1000. A value of -1 may be used to indicate that the depth
     *              should be high (1000). A value of Integer.MAX_VALUE may not be used due to a bug on multicore
     *              machines.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth > 1000) {
            throw new IllegalArgumentException("Depth must be <= 1000.");
        }

        this.depth = depth;
    }

    /**
     * Runs PC starting with a complete graph over all nodes of the given conditional independence test, using the given
     * independence test and knowledge and returns the resultant graph. The returned graph will be a CPDAG if the
     * independence information is consistent with the hypothesis that there are no latent common causes. It may,
     * however, contain cycles or bidirected edges if this assumption is not born out, either due to the actual presence
     * of latent common causes, or due to statistical errors in conditional independence judgments.
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        return search(this.independenceTest.getVariables());
    }

    /**
     * Runs PC starting with a complete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a CPDAG if the independence information is
     * consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p>
     * All the given nodes must be in the domain of the given conditional independence test.
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     * @throws InterruptedException if any
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        nodes = new ArrayList<>(nodes);

        return search(new Fas(getIndependenceTest()), nodes);
    }

    /**
     * Searches for a graph using the given IFas instance and list of nodes.
     *
     * @param fas   The IFas instance to use for the search.
     * @param nodes The list of nodes to search for.
     * @return The resultant graph. The returned graph will be a CPDAG if the independence information is consistent
     * with the hypothesis that there are no latent common causes. It may, however, contain cycles or bidirected edges
     * if this assumption is not born out, either due to the actual presence of latent common causes, or due to
     * statistical errors in conditional independence judgments.
     * @throws NullPointerException     If fas is null or if the independence test is null.
     * @throws IllegalArgumentException If any of the given nodes is not in the domain of the independence test
     *                                  provided.
     * @throws InterruptedException if any
     */
    public Graph search(Fas fas, List<Node> nodes) throws InterruptedException {

        if (verbose) {
            TetradLogger.getInstance().log("Starting PC algorithm");
            String message = "Independence test = " + getIndependenceTest() + ".";
            TetradLogger.getInstance().log(message);
        }

        long startTime = MillisecondTimes.timeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!new HashSet<>(allNodes).containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                                               "be in the domain of the independence test provided.");
        }


        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        try {
            this.graph = fas.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.sepsets = fas.getSepsets();

        enumerateTriples();

        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, nodes, verbose);
        GraphSearchUtils.pcdOrientC(getIndependenceTest(), this.knowledge, this.graph);

        MeekRules rules = new MeekRules();
        rules.setMeekPreventCycles(this.guaranteeCpdag);
        rules.setKnowledge(this.knowledge);
        rules.setVerbose(verbose);
        rules.orientImplied(this.graph);

        this.elapsedTime = MillisecondTimes.timeMillis() - startTime;

        if (verbose) {
            TetradLogger.getInstance().log("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
            TetradLogger.getInstance().log("Finishing PC Algorithm.");
        }

        return this.graph;
    }

    /**
     * Returns the elapsed time in milliseconds since the start of the method.
     *
     * @return the elapsed time in milliseconds
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Retrieves the set of unshielded colliders in the graph returned by the method search().
     *
     * @return The set of unshielded colliders. Non-null after search() is called.
     */
    public Set<Triple> getUnshieldedColliders() {
        return this.unshieldedColliders;
    }

    /**
     * Retrieves the set of unshielded noncolliders in the graph returned by the method search().
     *
     * @return The set of unshielded noncolliders. Non-null after search() is called.
     */
    public Set<Triple> getUnshieldedNoncolliders() {
        return this.unshieldedNoncolliders;
    }

    /**
     * Returns the set of adjacent edges in the graph.
     *
     * @return The set of adjacent edges.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    /**
     * Retrieves the number of independence tests performed by the graph search.
     *
     * @return The number of independence tests performed.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * Retrieves the list of nodes in the graph.
     *
     * @return The list of nodes in the graph.
     */
    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    /**
     * True iff the algorithm should be run with verbose output.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * True iff the algorithm should be run with False Discovery Rate tests.
     *
     * @return True, if so.
     */
    public boolean isFdr() {
        return this.fdr;
    }

    /**
     * Sets whether this test will run with False Discovery Rate tests.
     *
     * @param fdr True, if so.
     */
    public void setFdr(boolean fdr) {
        this.fdr = fdr;
    }

    /**
     * Enumerates the triples in the graph and classifies them as unshielded colliders or unshielded noncolliders.
     * <p>
     * The unshielded colliders and unshielded noncolliders are stored in the respective instance variables of the
     * class.
     */
    private void enumerateTriples() {
        this.unshieldedColliders = new HashSet<>();
        this.unshieldedNoncolliders = new HashSet<>();

        for (Node y : this.graph.getNodes()) {
            List<Node> adj = new ArrayList<>(this.graph.getAdjacentNodes(y));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Node x = adj.get(choice[0]);
                Node z = adj.get(choice[1]);

                Set<Node> nodes = this.sepsets.get(x, z);

                // Note that checking adj(x, z) does not suffice when knowledge
                // has been specified.
                if (nodes == null) {
                    continue;
                }

                if (nodes.contains(y)) {
                    getUnshieldedNoncolliders().add(new Triple(x, y, z));
                } else {
                    getUnshieldedColliders().add(new Triple(x, y, z));
                }
            }
        }
    }
}




