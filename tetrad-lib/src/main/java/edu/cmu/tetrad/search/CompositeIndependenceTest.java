package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.List;
import java.util.Set;

/**
 * <p>CompositeIndependenceTest class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CompositeIndependenceTest implements IndependenceTest {
    private final IndependenceTest[] independenceTests;
    private int effectiveSampleSize;

    /**
     * <p>Constructor for CompositeIndependenceTest.</p>
     *
     * @param independenceTests an array of {@link edu.cmu.tetrad.search.IndependenceTest} objects
     */
    public CompositeIndependenceTest(IndependenceTest[] independenceTests) {
        this.independenceTests = independenceTests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getData() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVerbose() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVerbose(boolean verbose) {

    }

    @Override
    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.effectiveSampleSize = effectiveSampleSize;
    }
}
