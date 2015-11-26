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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kernelized PC algorithm. This is the same as the Pc class, the nonparametric kernel-based HSIC test is used for
 * independence testing and the parameters for this test can be set directly when Kpc is initialized.
 *
 * @author Robert Tillman.
 */
public class Kpc implements GraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private IndTestHsic independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * Sepset information accumulated in the search.
     */
    private SepsetMap sepset;

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
    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();

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
     * The number of false dependence judgements from FAS, judging from the true graph, if set. Temporary.
     */
    private int numFalseDependenceJudgements;

    /**
     * The number of dependence judgements from FAS. Temporary.
     */
    private int numDependenceJudgements;

    /**
     * The threshold for rejecting the null
     */
    private double alpha;

    /**
     * Use incomplete Choleksy factorization for Gram matrices
     */
    private double useIncompleteCholesky = 1e-18;

    /**
     * The regularizer for singular matrices
     */
    private double regularizer = .0001;

    /**
     * The number of bootstrap samples to generate during independence testing
     */
    private int perms = 100;
    private boolean verbose = false;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a new PC search using for the given dataset.
     *
     * @param dataset The oracle for conditional independence facts. This does not make a copy of the independence test,
     *                for fear of duplicating the data set!
     */
    public Kpc(DataSet dataset, double alpha) {
        if (dataset == null) {
            throw new NullPointerException();
        }

        this.alpha = alpha;
        this.independenceTest = new IndTestHsic(dataset, alpha);
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
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * @return the independence test being used in the search.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge specification to be used in the search. May not be null.
     */
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return the sepset map from the most recent search. Non-null after the first call to <code>search()</code>.
     */
    public SepsetMap getSepset() {
        return sepset;
    }

    /**
     * @return the getModel depth of search--that is, the maximum number of conditioning nodes for any conditional
     *         independence checked.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the depth of the search--that is, the maximum number of conditioning nodes for any conditional independence
     * checked.
     *
     * @param depth The depth of the search. The default is 1000. A value of -1 may be used to indicate that the depth
     *              should be high (1000). A value of Integer.MAX_VALUE may not be used, due to a bug on multi-core
     *              machines.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0.");
        }

        if (depth > 1000) {
            throw new IllegalArgumentException("Depth must be <= 1000.");
        }

        this.depth = depth;
    }

    /**
     * Runs PC starting with a complete graph over all nodes of the given conditional independence test, using the given
     * independence test and knowledge and returns the resultant graph. The returned graph will be a pattern if the
     * independence information is consistent with the hypothesis that there are no latent common causes. It may,
     * however, contain cycles or bidirected edges if this assumption is not born out, either due to the actual presence
     * of latent common causes, or due to statistical errors in conditional independence judgments.
     */
    public Graph search() {
        return search(independenceTest.getVariables());
    }

    /**
     * Runs PC starting with a commplete graph over the given list of nodes, using the given independence test and
     * knowledge and returns the resultant graph. The returned graph will be a pattern if the independence information
     * is consistent with the hypothesis that there are no latent common causes. It may, however, contain cycles or
     * bidirected edges if this assumption is not born out, either due to the actual presence of latent common causes,
     * or due to statistical errors in conditional independence judgments.
     * <p/>
     * All of the given nodes must be in the domain of the given conditional independence test.
     */
    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting kPC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");

//        this.logger.log("info", "Variables " + independenceTest.getVariables());

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        graph = new EdgeListGraph(nodes);
        graph.fullyConnect(Endpoint.TAIL);

        Fas fas = new Fas(graph, getIndependenceTest());
        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setTrueGraph(trueGraph);
        graph = fas.search();
        this.sepset = fas.getSepsets();
        this.numIndependenceTests = fas.getNumIndependenceTests();
        this.numFalseDependenceJudgements = fas.getNumFalseDependenceJudgments();
        this.numDependenceJudgements = fas.getNumDependenceJudgments();

        enumerateTriples();

        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);
        SearchGraphUtils.orientCollidersUsingSepsets(sepset, knowledge, graph, verbose);
        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        rules.setKnowledge(knowledge);
        rules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.elapsedTime = System.currentTimeMillis() - startTime;

        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        this.logger.log("info", "Finishing PC Algorithm.");
        this.logger.flush();

        return graph;
    }

    /**
     * @return the elapsed time of the search, in milliseconds.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * @return the set of unshielded colliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedColliders() {
        return unshieldedColliders;
    }

    /**
     * @return the set of unshielded noncolliders in the graph returned by <code>search()</code>. Non-null after
     * <code>search</code> is called.
     */
    public Set<Triple> getUnshieldedNoncolliders() {
        return unshieldedNoncolliders;
    }

    //===============================ADDED FOR KPC=========================//

    /**
     * Sets the significance level at which independence judgments should be made.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
        independenceTest.setAlpha(alpha);
    }

    /**
     * Sets the precision for the Incomplete Choleksy factorization method for approximating Gram matrices. A value <= 0
     * indicates that the Incomplete Cholesky method should not be used and instead use the exact matrices.
     */
    public void setIncompleteCholesky(double precision) {
        this.useIncompleteCholesky = precision;
        independenceTest.setIncompleteCholesky(precision);
    }

    /**
     * Set the number of bootstrap samples to use
     */
    public void setPerms(int perms) {
        this.perms = perms;
        independenceTest.setPerms(perms);
    }

    /**
     * Sets the regularizer
     */
    public void setRegularizer(double regularizer) {
        this.regularizer = regularizer;
        independenceTest.setRegularizer(regularizer);
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Gets the getModel precision for the Incomplete Cholesky
     */
    public double getPrecision() {
        return this.useIncompleteCholesky;
    }

    /**
     * Gets the getModel number of bootstrap samples used
     */
    public int getPerms() {
        return this.perms;
    }

    /**
     * Gets the getModel regularizer
     */
    public double getRegularizer() {
        return this.regularizer;
    }

    //===============================PRIVATE METHODS=======================//

    private void enumerateTriples() {
        this.unshieldedColliders = new HashSet<Triple>();
        this.unshieldedNoncolliders = new HashSet<Triple>();

        for (Node y : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(y);

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Node x = adj.get(choice[0]);
                Node z = adj.get(choice[1]);

                List<Node> nodes = sepset.get(x, z);

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
        return numIndependenceTests;
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public int getNumFalseDependenceJudgements() {
        return numFalseDependenceJudgements;
    }

    public int getNumDependenceJudgements() {
        return numDependenceJudgements;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}




