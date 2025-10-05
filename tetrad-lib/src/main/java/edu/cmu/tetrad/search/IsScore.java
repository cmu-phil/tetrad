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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Instance-Specific BIC (IS-BIC) score for discrete data.
 *
 * <p>This score adapts the standard Bayesian Information Criterion (BIC) to the
 * instance-specific setting. As with the population version, the likelihood term is based on empirical counts, and the
 * penalty term is proportional to the number of free parameters in the local conditional distribution:</p>
 *
 * <pre>
 *   BIC = log-likelihood &ndash; 0.5 * penaltyDiscount * (numParams) * log(N)
 * </pre>
 *
 * <p>where {@code numParams = r_p * (K &ndash; 1)}, with {@code r_p} equal to the product
 * of category counts of the parent set and {@code K} the number of categories of the child variable.</p>
 *
 * <p>The instance-specific contribution does not alter the likelihood computation
 * itself. Instead, it is incorporated through a structure prior that rewards or penalizes local modifications
 * (addition, removal, or reversal of parents) relative to the baseline population model. In this way, IS-BIC balances
 * population-wide fit with adjustments that highlight edges most relevant to the chosen test case.</p>
 *
 * <p>This score is intended for use by search algorithms such as IS-FGES and IS-GFCI,
 * providing a lightweight alternative to IS-BDeu when a BIC-style criterion is preferred.</p>
 * <p>
 * Usage Notes
 * <ul>
 *   <li>Currently supports <strong>discrete variables only</strong>.</li>
 *   <li>Continuous data should be discretized before applying this score.</li>
 * </ul>
 *
 * @author fattaneh
 */
public interface IsScore {

    /**
     * Calculates the local score for a given node in the context of its parent nodes and children population.
     *
     * @param node                The index of the node for which the score is being calculated.
     * @param parents_is          An array representing the indices of the parent nodes.
     * @param parents_pop         An array representing the population of the parent nodes.
     * @param children_population An array representing the population of the children nodes.
     * @return The local score for the specified node.
     */
    double localScore(int node, int[] parents_is, int[] parents_pop, int[] children_population);

    /**
     * Calculates the difference between local scores before and after introducing a change in the relationship between
     * nodes x and y, considering their parent and children populations.
     *
     * @param x             The index of the node x.
     * @param y             The index of the node y.
     * @param parentsY_is   An array of indices representing the parent nodes of y.
     * @param parentsY_pop  An array of indices representing the population of parent nodes of y.
     * @param childrenY_pop An array of indices representing the population of children nodes of y.
     * @return The difference in local scores due to the relationship change between x and y.
     */
    double localScoreDiff(int x, int y, int[] parentsY_is, int[] parentsY_pop, int[] childrenY_pop);

    /**
     * Retrieves a list of variables represented as nodes.
     *
     * @return A list of nodes representing the variables.
     */
    List<Node> getVariables();

    /**
     * Determines whether the edge has a significant effect based on the given bump value.
     *
     * @param bump The threshold value to evaluate the significance of the effect on the edge.
     * @return true if the edge has a significant effect; false otherwise.
     */
    boolean isEffectEdge(double bump);

    /**
     * Retrieves the sample size used in the score computation.
     *
     * @return the sample size.
     */
    int getSampleSize();

    /**
     * Retrieves a variable node by its target name.
     *
     * @param targetName the name of the target variable to retrieve
     * @return the node representing the variable with the specified target name
     */
    Node getVariable(String targetName);

    /**
     * Retrieves the maximum allowable degree for nodes in the current scoring context.
     *
     * @return The maximum degree a node can have.
     */
    int getMaxDegree();

    /**
     * Determines whether the given list of nodes has a specific relationship with the specified node.
     *
     * @param z the list of nodes to be evaluated.
     * @param y the node to be checked against the list.
     * @return true if the list of nodes determines the specified node; false otherwise.
     */
    boolean determines(List<Node> z, Node y);

    /**
     * Calculates the local score for a given node in the context of its parent nodes and children population, without
     * using structure prior.
     * <p>
     * same as localSCire but this one doesn't use structure prior
     *
     * @param node         The index of the node for which the score is being calculated.
     * @param parents_is   An array representing the indices of the parent nodes.
     * @param parents_pop  An array representing the population of the parent nodes.
     * @param children_pop An array representing the population of the children nodes.
     * @return The local score for the specified node.
     */
    double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop);

    /**
     * Retrieves the prior value assigned to the structure.
     *
     * @return the structure prior value as a double.
     */
    double getStructurePrior();

    /**
     * Sets the prior value assigned to the structure.
     *
     * @param structurePrior the structure prior value to be set as a double.
     */
    void setStructurePrior(double structurePrior);

    /**
     * Retrieves the prior probability assigned to the sample.
     *
     * @return the sample prior value as a double.
     */
    double getSamplePrior();

    /**
     * Sets the prior probability value assigned to the sample.
     *
     * @param samplePrior the sample prior value to be set as a double.
     */
    void setSamplePrior(double samplePrior);

    /**
     * Retrieves the dataset used in the scoring calculations.
     *
     * @return the dataset used in the scoring calculations.
     */
    DataSet getDataSet();
}


