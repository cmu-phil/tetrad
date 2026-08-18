/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

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
     * @return a {@link IndependenceTest} object
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
     * Returns true just in case {@link IndependenceResult#getPValue()} for this test is a probability, so that
     * larger values mean stronger evidence of independence. This is the case for genuine hypothesis tests. It is
     * NOT the case for a score wrapped as a test (see
     * {@link edu.cmu.tetrad.search.test.ScoreIndTest}), whose reported "p-value" is a score difference that may be
     * any real number and for which SMALLER (more negative) values mean stronger evidence of independence.
     * <p>
     * Callers that rank candidate separating sets by strength of independence -- the max-p heuristic, or a
     * tie-break between two competing sepsets -- must consult this flag and reverse their comparison when it is
     * false; otherwise they will systematically select the WEAKEST separating set found rather than the
     * strongest. Callers that only ask whether a fact holds should use {@link IndependenceResult#isIndependent()},
     * which is correct for every test.
     *
     * @return True if the reported p-value is a probability (the default), false if it is a score difference.
     */
    default boolean isPValueAProbability() {
        return true;
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

    /**
     * Indicates whether the test supports subsampling.
     *
     * @return {@code true} if the test can be subsampled; {@code false} otherwise.
     */
    default boolean canBeSubsampled() {
        return this instanceof RowsSettable;
    }

    /**
     * Declares what this test can do, natively, with data containing missing values. The default is
     * {@link MissingValueSupport#NONE}, meaning that missingness must be handled upstream (e.g., by imputation, or
     * by supplying an EM-estimated covariance matrix if the test consumes one); tests that handle missing values
     * themselves should override this.
     *
     * @return This support level.
     */
    default MissingValueSupport getMissingValueSupport() {
        return MissingValueSupport.NONE;
    }
}





