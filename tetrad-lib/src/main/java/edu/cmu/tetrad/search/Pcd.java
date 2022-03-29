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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the PC ("Peter/Clark") algorithm, as specified in Chapter 6 of Spirtes, Glymour, and Scheines, "Causation,
 * Prediction, and Search," 2nd edition, with a modified rule set in step D due to Chris Meek. For the modified rule
 * set, see Chris Meek (1995), "Causal inference and causal explanation with background knowledge."
 *
 * @author Joseph Ramsey.
 */
public class Pcd implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

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
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * In an enumeration of triple types, these are the collider triples.
     */
    private Set<Triple> unshieldedColliders;

    /**
     * In an enumeration of triple types, these are the noncollider triples.
     */
    private Set<Triple> unshieldedNoncolliders;

    /**
     * The number of indepdendence tests in the last search.
     */
    private int numIndependenceTests;

    /**
     * The true graph, for purposes of comparison. Temporary.
     */
    private Graph trueGraph;

    /**
     * The number of dependence judgements from FAS. Temporary.
     */
    private int numDependenceJudgements;

    /**
     * The initial graph for the Fast Adjacency Search, or null if there is none.
     */
    private Graph externalGraph;

    private boolean verbose;

    private boolean fdr;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new PC search using the given independence test as oracle.
     *
     * @param independenceTest The oracle for conditional independence facts. This does not make a copy of the
     *                         independence test, for fear of duplicating the data set!
     */
    public Pcd(final IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return true iff edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    /**
     * @param aggressivelyPreventCycles Set to true just in case edges will not be addeds if they would create cycles.
     */
    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * @return the independence test being used in the search.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification to be used in the search. May not be null.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return the sepset map from the most recent search. Non-null after the first call to <code>search()</code>.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
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
     *              should be high (1000). A value of Integer.MAX_VALUE may not be used, due to a bug on multi-core
     *              machines.
     */
    public void setDepth(final int depth) {
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
     */
    public Graph search() {
        return search(this.independenceTest.getVariables());
    }

    /**
     * Runs PC starting with a commplete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a CPDAG if the independence information
     * is consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p>
     * All of the given nodes must be in the domain of the given conditional independence test.
     */
    public Graph search(final List<Node> nodes) {
        return search(new Fas(getIndependenceTest()), nodes);
    }

    public Graph search(final IFas fas, final List<Node> nodes) {
        this.logger.log("info", "Starting PC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

//        this.logger.log("info", "Variables " + independenceTest.getVariable());

        final long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        final List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }


        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        this.numIndependenceTests = fas.getNumIndependenceTests();
        this.numDependenceJudgements = fas.getNumDependenceJudgments();

        enumerateTriples();

        SearchGraphUtils.pcOrientbk(this.knowledge, this.graph, nodes);
        SearchGraphUtils.pcdOrientC(this.sepsets, getIndependenceTest(), this.knowledge, this.graph);

        final MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        rules.setKnowledge(this.knowledge);
        rules.orientImplied(this.graph);

        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        return this.graph;
    }

    /**
     * @return the elapsed time of the search, in milliseconds.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the set of unshielded colliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedColliders() {
        return this.unshieldedColliders;
    }

    /**
     * @return the set of unshielded noncolliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedNoncolliders() {
        return this.unshieldedNoncolliders;
    }

    public Set<Edge> getAdjacencies() {
        final Set<Edge> adjacencies = new HashSet<>();
        for (final Edge edge : this.graph.getEdges()) {
            adjacencies.add(edge);
        }
        return adjacencies;
    }

    public Set<Edge> getNonadjacencies() {
        final Graph complete = GraphUtils.completeGraph(this.graph);
        final Set<Edge> nonAdjacencies = complete.getEdges();
        final Graph undirected = GraphUtils.undirectedGraph(this.graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    //===============================PRIVATE METHODS=======================//

    private void enumerateTriples() {
        this.unshieldedColliders = new HashSet<>();
        this.unshieldedNoncolliders = new HashSet<>();

        for (final Node y : this.graph.getNodes()) {
            final List<Node> adj = this.graph.getAdjacentNodes(y);

            if (adj.size() < 2) {
                continue;
            }

            final ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                final Node x = adj.get(choice[0]);
                final Node z = adj.get(choice[1]);

                final List<Node> nodes = this.sepsets.get(x, z);

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

    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    public void setTrueGraph(final Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public int getNumDependenceJudgements() {
        return this.numDependenceJudgements;
    }

    public List<Node> getNodes() {
        return this.graph.getNodes();
    }

    public List<Triple> getColliders(final Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getNoncolliders(final Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getAmbiguousTriples(final Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getUnderlineTriples(final Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Triple> getDottedUnderlineTriples(final Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * True iff the algorithm should be run with False Discovery Rate tests.
     */
    public boolean isFdr() {
        return this.fdr;
    }

    public void setFdr(final boolean fdr) {
        this.fdr = fdr;
    }
}




