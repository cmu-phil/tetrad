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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.*;


/**
 * Checks independence facts for variables associated with a sepset by simply querying the sepset
 *
 * @author Robert Tillman
 * @version $Id: $Id
 */
public class IndTestSepsetDci implements IndependenceTest {

    /**
     * The sepset being queried
     */
    private final SepsetMapDci sepset;

    /**
     * The map from nodes to variables.
     */
    private final Map<Node, Node> nodesToVariables;

    /**
     * The map from variables to nodes.
     */
    private final Map<Node, Node> variablesToNodes;

    /**
     * The list of observed variables (i.e. variables for observed nodes).
     */
    private final List<Node> observedVars;
    private boolean verbose;

    /**
     * Constructs a new independence test that returns d-separation facts for the given graph as independence results.
     *
     * @param sepset a {@link edu.cmu.tetrad.search.work_in_progress.SepsetMapDci} object
     * @param nodes  a {@link java.util.List} object
     */
    public IndTestSepsetDci(SepsetMapDci sepset, List<Node> nodes) {
        if (sepset == null) {
            throw new NullPointerException();
        }

        this.sepset = sepset;
        this.nodesToVariables = new HashMap<>();
        this.variablesToNodes = new HashMap<>();

        for (Node node : nodes) {
            this.nodesToVariables.put(node, node);
            this.variablesToNodes.put(node, node);
        }

        this.observedVars = calcObservedVars(nodes);
    }

    /**
     * Determines independence between a subset of variables.
     *
     * @param vars The sublist of variables.
     * @return This IndependenceTest object.
     * @throws IllegalArgumentException If the subset is empty or contains variables that are not original variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        for (Node var : vars) {
            if (!getVariables().contains(var)) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }
        }

        return this;
    }

    /**
     * Checks the independence between two nodes, given a set of conditioning nodes.
     *
     * @param x A Node object representing the first node.
     * @param y A Node object representing the second node.
     * @param z A set representing the set of conditioning nodes.
     * @return An IndependenceResult object containing the result of the independence test.
     * @throws NullPointerException if z is null or if any node in z is null.
     * @throws RuntimeException     if the p-value is undefined.
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

        boolean independent = false;

        if (this.sepset.get(x, y) != null) {
            Set<Set<Node>> condSets = this.sepset.getSet(x, y);
            for (Set<Node> condSet : condSets) {
                if (condSet.size() == z.size() && condSet.containsAll(z)) {
                    final double pValue = 1.0;

                    if (this.verbose) {
                        String message = LogUtilsSearch.independenceFactMsg(x, y, z, pValue);
                        TetradLogger.getInstance().log(message);
                    }
                    independent = true;
                    break;
                }
            }
        }

        if (this.verbose) {
            if (independent) {
                NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                TetradLogger.getInstance().log(
                        LogUtilsSearch.independenceFact(x, y, z) + " score = " + LogUtilsSearch.independenceFactMsg(x, y, z, getPValue()));
            }
        }

        if (Double.isNaN(getPValue())) {
            throw new RuntimeException("Undefined p-value encountered when testing " +
                                       LogUtilsSearch.independenceFact(x, y, z));
        }

        return new IndependenceResult(new IndependenceFact(x, y, z), independent, getPValue(), getAlpha() - getPValue());
    }

    /**
     * Needed for IndependenceTest interface. P value is not meaningful here.
     *
     * @return a double
     */
    public double getPValue() {
        return Double.NaN;
    }

    /**
     * <p>getVariables.</p>
     *
     * @return the list of TetradNodes over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return Collections.unmodifiableList(this.observedVars);
    }

    /**
     * Determines if a given Node is present in a List of Nodes.
     *
     * @param z  The List of Nodes to search in.
     * @param x1 The Node to search for.
     * @return True if the Node x1 is present in the List z, otherwise False.
     */
    public boolean determines(List<Node> z, Node x1) {
        return z.contains(x1);
    }

    /**
     * <p>getAlpha.</p>
     *
     * @return a double
     */
    public double getAlpha() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the alpha level for the independence test.
     *
     * @param alpha The alpha level to be set.
     */
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves the variable with the specified name.
     *
     * @param name the name of the variable to retrieve
     * @return the variable with the specified name, or null if not found
     */
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);

            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    /**
     * <p>getVariable.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return the variable associated with the given node in the graph.
     */
    public Node getVariable(Node node) {
        return this.nodesToVariables.get(node);
    }

    /**
     * <p>getNode.</p>
     *
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     * @return the node associated with the given variable in the graph.
     */
    public Node getNode(Node variable) {
        return this.variablesToNodes.get(variable);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return "D-separation";
    }

    /**
     * <p>getData.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getData() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbose flag.
     *
     * @param verbose True if verbose mode is enabled, false otherwise.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Calculates the observed variables from a list of nodes.
     *
     * @param nodes A list of nodes.
     * @return A list containing only the observed variables.
     */
    private List<Node> calcObservedVars(List<Node> nodes) {
        List<Node> observedVars = new ArrayList<>();

        for (Node node : nodes) {
            if (node.getNodeType() == NodeType.MEASURED) {
                observedVars.add(getVariable(node));
            }
        }

        return observedVars;
    }
}







