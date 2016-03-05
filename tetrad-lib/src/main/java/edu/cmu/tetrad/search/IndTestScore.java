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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Interface implemented by classes that do conditional independence testing. These classes are capable of serving as
 * conditional independence "oracles" for constraint-based searches.
 *
 * @author Don Crimbchin (djc2@andrew.cmu.edu)
 * @author Joseph Ramsey
 */
public class IndTestScore implements IndependenceTest {

    private final FgsScore score;
    private final List<Node> variables;
    private final HashMap<Node, Integer> variablesHash;
    private double bump = Double.NaN;

    public IndTestScore(FgsScore score, double parameter1) {
        if (score == null) throw new NullPointerException();
        this.score = score;
        this.variables = score.getVariables();
        this.variablesHash = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            this.variablesHash.put(variables.get(i), i);
        }

        score.setParameter1(parameter1);
    }

    /**
     * @return an Independence test for a subset of the variables.
     */
    public IndTestScore indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        double v = this.score.localScoreDiff(variables.indexOf(x), variables.indexOf(y), varIndices(z));
        this.bump = v;
        return v < 0;
    }

    private int[] varIndices(List<Node> z) {
        int[] indices = new int[z.size()];

        for (int i = 0; i < z.size(); i++) {
            indices[i] = variables.indexOf(z.get(i));
        }

        return indices;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    /**
     * @return the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return bump;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable by the given name.
     */
    public Node getVariable(String name) {
        for (Node node : variables) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        return null;
    }

    /**
     * @return the list of names for the variables in getNodesInEvidence.
     */
    public List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (Node node : variables) {
            names.add(node.getName());
        }

        return names;
    }

    /**
     * @return true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the significance level of the independence test.
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return score.getParameter1();
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        score.setParameter1(alpha);
    }

    /**
     * @return The data model for the independence test.
     */
    public DataModel getData() {
        throw new UnsupportedOperationException();
    }

    public ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException();
    }

    public List<DataSet> getDataSets() {
        throw new UnsupportedOperationException();
    }

    public int getSampleSize() {
        return score.getSampleSize();
    }

    public List<TetradMatrix> getCovMatrices() {
        throw new UnsupportedOperationException();
    }

    /**
     * A score that is higher with more likely models.
     */
    public double getScore() {
        return bump;
    }
}





