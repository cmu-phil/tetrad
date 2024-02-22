package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.List;
import java.util.Set;

/**
 * Represents a composite independence test that combines multiple independence tests together.
 *
 * @author josephramsey
 */
public class CompositeIndependenceTest implements IndependenceTest {
    private final IndependenceTest[] independenceTests;

    /**
     * Represents a composite independence test that combines multiple independence tests together.
     *
     * @param independenceTests an array of IndependenceTest objects to be combined
     */
    public CompositeIndependenceTest(IndependenceTest[] independenceTests) {
        this.independenceTests = independenceTests;
    }

    /**
     * Checks the independence between two nodes, given a set of conditioning nodes.
     *
     * @param x The first node.
     * @param y The second node.
     * @param z The set of conditioning nodes.
     * @return The result of the independence test.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        return null;
    }

    /**
     * Retrieves the variables associated with this IndependenceTest.
     *
     * @return a List of Node objects representing the variables associated with this IndependenceTest.
     */
    @Override
    public List<Node> getVariables() {
        return null;
    }

    /**
     * Retrieves the DataModel associated with this object.
     *
     * @return the DataModel associated with this object.
     */
    @Override
    public DataModel getData() {
        return null;
    }

    /**
     * Returns true if the test prints verbose output.
     *
     * @return True if the test is set to print verbose output, false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return false;
    }

    /**
     * Sets whether this test will print verbose output.
     *
     * @param verbose True, if verbose output should be printed. False otherwise.
     */
    @Override
    public void setVerbose(boolean verbose) {

    }
}
