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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Gives an interface that can be implemented by classes that do conditional independence testing. These classes are
 * capable of serving as conditional independence "oracles" for constraint-based searches. Many methods are given
 * defaults so that such a test will be easy to implement in Python using JPype.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IndependenceTest {

    /**
     * Checks the independence between two variables x and y given a conditioning set z.
     *
     * @param x The first variable to test, represented as a Node object.
     * @param y The second variable to test, represented as a Node object.
     * @param z The set of conditioning variables, represented as a Set of Node objects.
     * @return An IndependenceResult object representing the outcome of the independence test.
     * @throws InterruptedException If the process is interrupted during the execution.
     */
    IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException;

    /**
     * Retrieves the list of variables associated with this independence test.
     *
     * @return A list of {@link Node} objects representing the variables.
     */
    List<Node> getVariables();

    /**
     * Retrieves the data model associated with this test.
     *
     * @return A {@link DataModel} object representing the data model.
     */
    DataModel getData();

    /**
     * Returns true if the test prints verbose output.
     *
     * @return True if the case.
     */
    boolean isVerbose();

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if so.
     */
    void setVerbose(boolean verbose);

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    String toString();


    /**
     * Returns an Independence test for a sublist of the variables.
     *
     * @param vars The sublist of variables.
     * @return a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    default IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("Independence subset feature is not implemented.");
    }

    /**
     * Checks the independence fact in question and returns and independence result.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return The independence result.
     * @see IndependenceResult
     * @throws java.lang.InterruptedException if any.
     */
    default IndependenceResult checkIndependence(Node x, Node y, Node... z) throws InterruptedException {
        Set<Node> zList = GraphUtils.asSet(z);
        return checkIndependence(x, y, zList);
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    default int getSampleSize() {
        DataModel data = getData();
        if (data instanceof CovarianceMatrix) {
            return ((CovarianceMatrix) data).getSampleSize();
        } else if (data instanceof DataSet) {
            return ((DataSet) data).getNumRows();
        } else {
            throw new UnsupportedOperationException("Expecting a dataset or a covariance matrix.");
        }
    }

    /**
     * Returns The variable by the given name.
     *
     * @param name a {@link java.lang.String} object
     * @return This variable.
     */
    default Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    /**
     * Returns the list of names for the variables in getNodesInEvidence.
     *
     * @return this list.
     */
    default List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<>();
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * Returns true if y is determined the variable in z.
     *
     * @param z a {@link java.util.Set} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @return True, if so.
     */
    default boolean determines(Set<Node> z, Node y) {
        throw new UnsupportedOperationException("Determines method is not implemented.");
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This level.
     * @throws java.lang.UnsupportedOperationException if there is no significance level.
     */
    default double getAlpha() {
        throw new UnsupportedOperationException("The getAlpha() method is not implemented for this test.");
    }

    /**
     * Sets the significance level.
     *
     * @param alpha This level.
     */
    default void setAlpha(double alpha) {
        throw new UnsupportedOperationException("The setAlpha() method is not implemented for this test.");
    }

    /**
     * Returns the covariance matrix.
     *
     * @return This matrix.
     * @throws java.lang.UnsupportedOperationException If this method is not supported for a particular test.
     */
    default ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException("The getCov() method is not implemented for this test.");
    }

    /**
     * Returns the datasets for this test
     *
     * @return these datasets.
     * @throws UnsupportedOperationException If this method is not supported for a particular test.
     */
    default List<DataSet> getDataSets() {
        throw new UnsupportedOperationException("The getDataSets() method is not implemented for this test.");
    }
}




