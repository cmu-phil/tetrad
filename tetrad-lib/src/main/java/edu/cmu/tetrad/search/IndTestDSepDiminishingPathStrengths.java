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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.Math.abs;

/**
 * Checks independence facts for variables associated with the nodes in a given graph by checking d-separation facts on
 * the underlying nodes.
 *
 * @author Joseph Ramsey
 */
public class IndTestDSepDiminishingPathStrengths implements IndependenceTest {

    private final StandardizedSemIm semIm;
    /**
     * The graph for which this is a variable map.
     */
    private SemGraph graph;

    /**
     * The list of observed variables (i.e. variables for observed nodes).
     */
    private Set<Node> observedVars;
    private List<Node> _observedVars;
    private HashSet<IndependenceFact> facts;
    private boolean verbose = false;
    private double pvalue = 0;
    private double alpha = 0.001;

    public IndTestDSepDiminishingPathStrengths(StandardizedSemIm semIm, double alpha) {
        this(semIm, false, alpha);
    }

    /**
     * Constructs a new independence test that returns d-separation facts for the given semIm as independence results.
     */
    public IndTestDSepDiminishingPathStrengths(StandardizedSemIm semIm, boolean keepLatents, double alpha) {
        if (semIm == null) {
            throw new NullPointerException();
        }

        this.graph = semIm.getSemPm().getGraph();
        this.graph.setShowErrorTerms(false);

        this.semIm = semIm;

        this._observedVars = calcVars(graph, keepLatents);
        this.observedVars = new HashSet<>(_observedVars);

        this.alpha = alpha;
    }

    /**
     * Required by IndependenceTest.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        if (vars.isEmpty()) {
            throw new IllegalArgumentException("Subset may not be empty.");
        }

        List<Node> _vars = new ArrayList<Node>();

        for (Node var : vars) {
            Node _var = getVariable(var.getName());

            if (_var == null) {
                throw new IllegalArgumentException(
                        "All vars must be original vars");
            }

            _vars.add(_var);
        }

        this._observedVars = _vars;
        this.observedVars = new HashSet<>(_observedVars);

        facts = new HashSet<>();

        return this;
    }

    /**
     * @return the list of observed nodes in the given graph.
     */
    private List<Node> calcVars(Graph graph, boolean keepLatents) {
        if (keepLatents) {
            return graph.getNodes();
        } else {
            List<Node> observedVars = new ArrayList<Node>();

            for (Node node : graph.getNodes()) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    observedVars.add(node);
                }
            }

            return observedVars;
        }
    }

    /**
     * Checks the indicated d-separation fact.
     *
     * @param x one node.
     * @param y a second node.
     * @param z a List of nodes (conditioning variables)
     * @return true iff x _||_ y | z
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
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

        boolean dSeparated = !isDConnectedTo4(x, y, z, graph, getAlpha());

        System.out.println("Dseparated = " + dSeparated);

        if (verbose) {
            if (dSeparated) {
                double pValue = 1.0;
                TetradLogger.getInstance().log("independencies", SearchLogUtils.independenceFactMsg(x, y, z, pValue));
                System.out.println(SearchLogUtils.independenceFactMsg(x, y, z, pValue));
            } else {
                double pValue = 0.0;
                TetradLogger.getInstance().log("dependencies", SearchLogUtils.dependenceFactMsg(x, y, z, pValue));
                System.out.println(SearchLogUtils.dependenceFactMsg(x, y, z, pValue));
            }
        }

        if (dSeparated) {
            if (this.facts != null) {
                this.facts.add(new IndependenceFact(x, y, z));
            }

            pvalue = 1.0;
        } else {
            pvalue = 0.0;
        }

        return dSeparated;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * Auxiliary method to calculate dseparation facts directly from nodes instead of from variables.
     */
    public boolean isDSeparated(Node x, Node y, List<Node> z) {
        if (z == null) {
            throw new NullPointerException();
        }

        for (Node aZ : z) {
            if (aZ == null) {
                throw new NullPointerException();
            }
        }

        return getGraph().isDSeparatedFrom(x, y, z);
    }

    /**
     * Needed for IndependenceTest interface. P value is not meaningful here.
     */
    public double getPValue() {
        return this.pvalue;
    }

    /**
     * @return the list of TetradNodes over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return Collections.unmodifiableList(_observedVars);
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> nodes = _observedVars;
        List<String> nodeNames = new ArrayList<String>();
        for (Node var : nodes) {
            nodeNames.add(var.getName());
        }
        return nodeNames;
    }

    public boolean determines(List z, Node x1) {
        return false; //z.contains(x1);
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        //
    }

    public Node getVariable(String name) {
        for (Node variable : observedVars) {
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    // Depth first.
    public boolean isDConnectedTo4(Node x, Node y, List<Node> z, Graph graph, double alpha) {
        LinkedList<Node> path = new LinkedList<Node>();

        path.add(x);

        for (Node c : graph.getAdjacentNodes(x)) {
            if (isDConnectedToVisit4(x, c, y, path, z, graph, alpha)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDConnectedToVisit4(Node a, Node b, Node y, LinkedList<Node> path, List<Node> z,
                                         Graph graph, double alpha) {


        double r = 1;
        path.addLast(b);

        for (int i = 0; i < path.size() - 1; i++) {
            double edgeCoef = 0;
            edgeCoef = semIm.getEdgeCoefficient(path.get(i), path.get(i + 1));
            if (Double.isNaN(edgeCoef)) edgeCoef = semIm.getEdgeCoefficient(path.get(i + 1), path.get(i));

            if (!Double.isNaN(edgeCoef)) {
                r *= edgeCoef;
            }

            System.out.println("Coef " + i + " = " + edgeCoef + " r = " + r);
        }


        System.out.println("path length = " + (path.size() - 1) + " path correlation = " + r);

        int n = 100;

        double fisherZ = Math.sqrt(n - 3 - z.size()) * 0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));


        double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
        System.out.println("Fisher Z = " + fisherZ + " p = " + pvalue);

        if (pvalue > alpha || Double.isNaN(pvalue)) {
            path.removeLast();
            return false;
        }

        this.pvalue = pvalue;

//        if (abs(r) < alpha) {
//            return false;
//        }

        path.removeLast();

        if (b == y) {
            path.addLast(b);
            return true;
        }

        if (path.contains(b)) {
            return false;
        }

        path.addLast(b);


        System.out.println("R = " + r + " !!!");

        for (Node c : graph.getAdjacentNodes(b)) {
            if (a == c) continue;

            if (reachable(a, b, c, z, graph)) {
                if (isDConnectedToVisit4(b, c, y, path, z, graph, alpha)) {
//                    path.removeLast();
                    return true;
                }
            }
        }

        path.removeLast();
        return false;
    }

    private static boolean reachable(Node a, Node b, Node c, List<Node> z, Graph graph) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        boolean ancestor = isAncestor(b, z, graph);
        return collider && ancestor;
    }

    private static boolean isAncestor(Node b, List<Node> z, Graph graph) {
        boolean ancestor = false;

        for (Node n : z) {
            if (graph.isAncestorOf(b, n)) {
                ancestor = true;
                break;
            }
        }

        return ancestor;
    }

    /**
     * @return the underlying graph.
     */
    public Graph getGraph() {
        return this.graph;
    }

    public String toString() {
        return "D-separation";
    }

    public DataSet getData() {
        return null;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    public void startRecordingFacts() {
        this.facts = new HashSet<IndependenceFact>();
    }

    public HashSet<IndependenceFact> getFacts() {
        return facts;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}





