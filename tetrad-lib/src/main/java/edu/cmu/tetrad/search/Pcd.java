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

    // The independence test used for the PC search.
    private final IndependenceTest independenceTest;
    // The logger for this class. The config needs to be set.
    private final TetradLogger logger = TetradLogger.getInstance();
    // Forbidden and required edges for the search.
    private Knowledge knowledge = new Knowledge();
    // Sepset information accumulated in the search.
    private SepsetMap sepsets;
    // The maximum number of nodes conditioned on in the search. The default it 1000.
    private int depth = 1000;
    // The graph that's constructed during the search.
    private Graph graph;
    // Elapsed time of the most recent search.
    private long elapsedTime;
    // True if cycles are to be prevented. Maybe expensive for large graphs (but also useful for large graphs).
    private boolean meekPreventCycles;
    // In an enumeration of triple types, these are the collider triples.
    private Set<Triple> unshieldedColliders;
    // In an enumeration of triple types, these are the noncollider triples.
    private Set<Triple> unshieldedNoncolliders;
    // The number of independence tests in the last search.
    private int numIndependenceTests;
    // True iff the algorithm should be run with verbose output.
    private boolean verbose;
    // True iff the algorithm should be run with False Discovery Rate tests.
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
     * <p>isMeekPreventCycles.</p>
     *
     * @return true, iff edges will not be added if they would create cycles.
     */
    public boolean isMeekPreventCycles() {
        return this.meekPreventCycles;
    }

    /**
     * <p>Setter for the field <code>meekPreventCycles</code>.</p>
     *
     * @param meekPreventCycles Set to true just in case edges will not be added if they would create cycles.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return the independence test being used in the search.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return the knowledge specification used in the search. Non-null.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification to be used in the search. May not be null.
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
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
     */
    public Graph search() {
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
     */
    public Graph search(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);

        return search(new Fas(getIndependenceTest()), nodes);
    }

    /**
     * <p>search.</p>
     *
     * @param fas   a {@link edu.cmu.tetrad.search.IFas} object
     * @param nodes a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph search(IFas fas, List<Node> nodes) {
        TetradLogger.getInstance().forceLogMessage("Starting PC algorithm");
        String message = "Independence test = " + getIndependenceTest() + ".";
        TetradLogger.getInstance().forceLogMessage(message);

//        this.logger.log("info", "Variables " + independenceTest.getVariable());

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

        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        this.numIndependenceTests = fas.getNumIndependenceTests();

        enumerateTriples();

        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, nodes);
        GraphSearchUtils.pcdOrientC(getIndependenceTest(), this.knowledge, this.graph);

        MeekRules rules = new MeekRules();
        rules.setMeekPreventCycles(this.meekPreventCycles);
        rules.setKnowledge(this.knowledge);
        rules.orientImplied(this.graph);

        this.elapsedTime = MillisecondTimes.timeMillis() - startTime;

        TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().forceLogMessage("Finishing PC Algorithm.");
        this.logger.flush();

        return this.graph;
    }

    /**
     * <p>Getter for the field <code>elapsedTime</code>.</p>
     *
     * @return the elapsed time of the search, in milliseconds.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * <p>Getter for the field <code>unshieldedColliders</code>.</p>
     *
     * @return the set of unshielded colliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedColliders() {
        return this.unshieldedColliders;
    }

    /**
     * <p>Getter for the field <code>unshieldedNoncolliders</code>.</p>
     *
     * @return the set of unshielded noncolliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedNoncolliders() {
        return this.unshieldedNoncolliders;
    }

    /**
     * <p>getAdjacencies.</p>
     *
     * @return the graph returned by <code>search()</code>. Non-null after <code>search</code> is called.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    /**
     * <p>Getter for the field <code>numIndependenceTests</code>.</p>
     *
     * @return the number of independence tests performed in the last search.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * <p>getNodes.</p>
     *
     * @return the list of nodes in the graph returned by <code>search()</code>. Non-null after <code>search</code> is
     * called.
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




