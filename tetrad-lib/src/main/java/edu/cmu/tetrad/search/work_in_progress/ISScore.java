package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Interface for a score suitable for FGES
 */
public interface ISScore {

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

