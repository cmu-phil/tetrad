package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;

import java.util.List;
import java.util.Set;

public class CompositeIndependenceTest implements IndependenceTest {
    private final IndependenceTest[] independenceTests;

    public CompositeIndependenceTest(IndependenceTest[] independenceTests) {
        this.independenceTests = independenceTests;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        return null;
    }

    @Override
    public List<Node> getVariables() {
        return null;
    }

    @Override
    public DataModel getData() {
        return null;
    }

    @Override
    public boolean isVerbose() {
        return false;
    }

    @Override
    public void setVerbose(boolean verbose) {

    }
}
