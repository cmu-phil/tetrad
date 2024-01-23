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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks independence facts for variables associated with the nodes in a given graph by
 * checking m-separation facts on the underlying nodes. We use the IndependenceTest interface here so that this
 * m-separation test can be used in place of a statistical conditional independence test in algorithms to provide oracle
 * information.
 *
 * @author josephramsey
 */
public class MsepTest implements IndependenceTest {

    // A cache of results for independence facts.
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    private Map<Node, Set<Node>> ancestorMap;
    private IndependenceFacts independenceFacts;
    // The graph for which this is a variable map.
    private Graph graph;
    //The list of observed variables (i.e. variables for observed nodes).
    private List<Node> observedVars;
    // The list of translated observed variables (i.e. variables for observed nodes).
    private List<Node> _observedVars;
    // Whether verbose output should be printed.
    private boolean verbose = false;
    // The "p-value" of the last test (this is 0 or 1).
    private double pvalue = 0;

    /**
     * Constructor.
     *
     * @param graph The graph for which m-separation facts should be checked. This may be a DAG, CPDAG, or PAG. In the
     *              latter case, m-separation results will be returned (same algorithm).
     */
    public MsepTest(Graph graph) {
        this(graph, false);
    }

    /**
     * Constructor.
     *
     * @param facts     Independence facts to be used for direct calculations of m-separation.
     * @param variables The variables for the facts, if different from those that independence facts would return.
     * @see IndependenceFacts
     */
    public MsepTest(IndependenceFacts facts, List<Node> variables) {
        this(facts, false);
        facts.setNodes(variables);
    }

    /**
     * Constructor.
     *
     * @param facts Independence facts to be used for direct calculations of m-separation.
     * @see IndependenceFacts
     */
    public MsepTest(IndependenceFacts facts) {
        this(facts, false);
    }

    /**
     * Constructor.
     *
     * @param graph       The graph for which m-separation facts should be checked. This may be a DAG, CPDAG, or PAG. In
     *                    the latter case, m-separation results will be returned (same algorithm).
     * @param keepLatents Whether latent in the graph should be used in conditional independence facts. If the graph is
     *                    being marginalized, this should be false.
     */
    public MsepTest(Graph graph, boolean keepLatents) {
        if (graph == null) {
            throw new NullPointerException();
        }

        this.graph = graph;

        this.ancestorMap = graph.paths().getAncestorMap();
        this._observedVars = calcVars(graph.getNodes(), keepLatents);
        this.observedVars = new ArrayList<>(_observedVars);
    }

    /**
     * Constructor.
     *
     * @param facts       Independence facts to be used for direct calculations of m-separation.
     * @param keepLatents Whether latent in the graph should be used in conditional independence facts. If the graph is
     *                    being marginalized, this should be false.
     * @see IndependenceFacts
     */
    public MsepTest(IndependenceFacts facts, boolean keepLatents) {
        if (facts == null) {
            throw new NullPointerException();
        }

        this.independenceFacts = facts;

        this._observedVars = calcVars(facts.getVariables(), keepLatents);
        this.observedVars = new ArrayList<>(_observedVars);
    }

    /**
     * Returns a test over a subset of the variables.
     *
     * @return This test.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        List<Node> _vars = new ArrayList<>();

        for (Node var : vars) {
            Node _var = getVariable(var.getName());

            if (_var == null) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }

            _vars.add(_var);
        }

        this._observedVars = _vars;
        this.observedVars = new ArrayList<>(_observedVars);

        return this;
    }

    /**
     * Returns the list of observed variables in the given graph.
     *
     * @return This lsit.
     */
    private List<Node> calcVars(List<Node> nodes, boolean keepLatents) {
        if (keepLatents) {
            return nodes;
        } else {
            List<Node> _nodes = new ArrayList<>(nodes);
            _nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);
            return _nodes;
        }
    }

    /**
     * Checks the indicated m-separation fact, msep(x , y | z).
     *
     * @return An independence result for msep(x, y | z).
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node node : z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        if (!observedVars.contains(x)) {
            throw new IllegalArgumentException("Not an observed variable: " + x);
        }

        if (!observedVars.contains(y)) {
            throw new IllegalArgumentException("Not an observed variable: " + y);
        }

        for (Node _z : z) {
            if (!observedVars.contains(_z)) {
                throw new IllegalArgumentException("Not an observed variable: " + _z);
            }
        }

        if (facts.containsKey(new IndependenceFact(x, y, z))) {
            return facts.get(new IndependenceFact(x, y, z));
        }

        boolean mSeparated;

        if (graph != null) {
            mSeparated = !getGraph().paths().isMConnectedTo(x, y, z, ancestorMap);
        } else {
            mSeparated = independenceFacts.isIndependent(x, y, z);
        }

        if (this.verbose) {
            if (mSeparated) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, z, 1.0));
            }
        }

        double pValue;

        if (mSeparated) {
            pValue = 1.0;
        } else {
            pValue = 0.0;
        }

        if (Double.isNaN(pvalue)) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, z));
        }

        this.pvalue = pValue;

        IndependenceResult result = new IndependenceResult(new IndependenceFact(x, y, z), mSeparated, pValue, pvalue == 1 ? -1 : 1);
        facts.put(new IndependenceFact(x, y, z), result);
        return result;
    }

    /**
     * Auxiliary method to calculate msep(x, y | z) directly from nodes instead of from variables.
     *
     * @return True, if so.
     */
    public boolean isMSeparated(Node x, Node y, Set<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node aZ : z) {
            if (aZ == null) {
                throw new NullPointerException();
            }
        }

        return getGraph().paths().isMSeparatedFrom(x, y, z, ancestorMap);
    }

    /**
     * Return the list of TetradNodes over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return Collections.unmodifiableList(_observedVars);
    }

    /**
     * @throws UnsupportedOperationException Since this method is not feasible.
     */
    public boolean determines(List<Node> z, Node x1) {
        throw new UnsupportedOperationException("The 'determines' method is not implemented");
    }

    /**
     * Returns an alpha level, 0.5. This is an arbitrary nubmer that will help decide whether a pseudo p-value returned
     * by the test represents a dependence or an independence.
     *
     * @return 0.5.
     */
    public double getAlpha() {
        return 0.5;
    }

    /**
     * @throws UnsupportedOperationException it makes no sense to set an alpha level for a d-separation test away from
     *                                       the default.
     * @see #getAlpha()
     */
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException("Method mot implemented.");
    }

    /**
     * Returns the variable with the given name.
     *
     * @param name The name.
     * @return The variable.
     */
    public Node getVariable(String name) {
        for (Node variable : observedVars) {
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    /**
     * Returns the underlying graph that is being used to calculate d-separation relationships.
     *
     * @return This graph.
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return "M-separation".
     */
    public String toString() {
        return "M-separation";
    }

    /**
     * @throws UnsupportedOperationException Method doesn't make sense here.
     */
    public DataSet getData() {
        throw new UnsupportedOperationException("This is a m-separation test, no data available.");
    }

    /**
     * Returns True just in case verbose output should be printed.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





