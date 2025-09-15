package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to encapsulate logic for preserving a Markov property for subsequent testing after an initial local
 * Markov graph has been found.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see GraphUtils
 */
public class PreserveMarkov {
    /**
     * The initial Markov graph
     */
    private final Graph graph;
    /**
     * The independence test to use.
     */
    private final IndependenceTest test;
    /**
     * A map that stores p-values for pairs of nodes.
     * <p>
     * The map uses a pair of nodes as keys, represented by Pair<Node, Node>, and stores a set of p-values (doubles)
     * associated with each pair. This structure is useful in statistical or graph-theoretical contexts where p-values
     * need to be tracked between pairs of nodes.
     */
    private Map<Pair<Node, Node>, Set<Double>> pValues = new HashMap<>();
    private boolean preserveMarkov = false;

    /**
     * Constructs an PreserveMarkov class for a given Markov graph.
     *
     * @param graph          The initial Markov graph. This graph should pass a local Markov check.
     * @param test           The independence test to use.
     * @param preserveMarkov True if Markov should be preserved.
     */
    public PreserveMarkov(Graph graph, IndependenceTest test, boolean preserveMarkov) {
        this.graph = new EdgeListGraph(graph);
        this.test = test;
        try {
            setPreserveMarkov(preserveMarkov);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new instance of the PreserveMarkov class by copying the fields from another PreserveMarkov object.
     *
     * @param preserveMarkov The PreserveMarkov object to be copied. Must not be null.
     */
    public PreserveMarkov(PreserveMarkov preserveMarkov) {
        this.graph = new EdgeListGraph(preserveMarkov.graph);
        this.test = preserveMarkov.test;
        this.pValues = new HashMap<>(preserveMarkov.pValues);
    }

    /**
     * Adjusts the p-values for a local Markov condition in a given constraint-based partially directed acyclic graph
     * (CPDAG).
     *
     * @param graph          the constraint-based partially directed acyclic graph (CPDAG) to adjust p-values for
     * @param preserveMarkov a boolean flag indicating if the Markov condition should be preserved; should be true
     * @param test           the independence test to be used; must not be null and not an instance of MsepTest
     * @param pValues        a map of node pairs to sets of p-values used for adjustment
     * @param withoutPair    a pair of nodes for which adjustments are calculated without considering the edge between
     *                       them
     * @return a map of node pairs to sets of adjusted p-values
     * @throws IllegalArgumentException if preserveMarkov is false or if the test is null or an instance of MsepTest
     * @throws InterruptedException     if any
     */
    public static Map<Pair<Node, Node>, Set<Double>> markovAdjustPValues(Graph graph, boolean preserveMarkov, IndependenceTest test,
                                                                         Map<Pair<Node, Node>, Set<Double>> pValues, Pair<Node, Node> withoutPair) throws InterruptedException {
        if (!preserveMarkov) {
            throw new IllegalArgumentException("This method should only be called when preserveMarkov is true.");
        }

        if (test == null || test instanceof MsepTest) {
            throw new IllegalArgumentException("This method should only be called when the test is not null and not an instance of MsepTest.");
        }

        Node x = withoutPair.getLeft();
        Node y = withoutPair.getRight();

        var _pValues = new HashMap<>(pValues);

        Set<Node> mbx = new HashSet<>(graph.paths().markovBlanket(x));

        for (Node node : mbx) {
            if (node.equals(y)) {
                mbx.remove(node);
                break;
            }
        }

        Set<Node> mby = new HashSet<>(graph.paths().markovBlanket(y));

        for (Node node : mby) {
            if (node.equals(x)) {
                mby.remove(node);
                break;
            }
        }

        _pValues.remove(Pair.of(x, y));
        _pValues.remove(Pair.of(y, x));

        for (Node _y : graph.getNodes()) {
            if (x.equals(_y)) {
                continue;
            }

            if (!mbx.contains(_y)) {// && !graph.paths().existsDirectedPath(x, _y, withoutPair)) {
                IndependenceResult result = test.checkIndependence(x, _y, mbx);
                _pValues.putIfAbsent(Pair.of(x, _y), new HashSet<>());
                _pValues.get(Pair.of(x, _y)).add(result.getPValue());
            }
        }

        for (Node _x : graph.getNodes()) {
            if (y.equals(_x)) {
                continue;
            }

            if (!mby.contains(_x)) {// && !graph.paths().existsDirectedPath(y, _x, withoutPair)) {
                IndependenceResult result = test.checkIndependence(y, _x, mby);
                _pValues.putIfAbsent(Pair.of(y, _x), new HashSet<>());
                _pValues.get(Pair.of(y, _x)).add(result.getPValue());
            }
        }

        return _pValues;
    }

    /**
     * Sets whether to preserve the Markov property. By default, false; this must be turned on.
     *
     * @param preserveMarkov True if the Markov property should be preserved.
     * @throws InterruptedException if any.
     */
    private void setPreserveMarkov(boolean preserveMarkov) throws InterruptedException {
        this.preserveMarkov = preserveMarkov;

        if (this.preserveMarkov) {
            double initialFraction = GraphUtils.localMarkovInitializePValues(
                    graph, preserveMarkov, test, pValues);
            System.out.println("Initial percent dependent = " + initialFraction);
        } else {
            pValues.clear();
        }
    }

    /**
     * Checks the independence of two nodes given a set of conditioning nodes, and if Markov is to be preserved, checks
     * to make sure the additional independence does not generate p-values that violate the Markov property. Returns the
     * independence-result, marked as 'true' if the nodes are independent or if the Markov property is preserved and the
     * nodes are independent and the Markov property wouldn't be violated by the additional independence.
     *
     * @param x The first node.
     * @param y The second node.
     * @param z The set of conditioning nodes.
     * @return True if the nodes are independent.
     * @throws InterruptedException if any.
     */
    public boolean markovIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceResult result = test.checkIndependence(x, y, z);

        if (result.isIndependent()) {
            if (preserveMarkov) {
                Map<Pair<Node, Node>, Set<Double>> _pValues = markovAdjustPValues(graph, preserveMarkov,
                        test, pValues, Pair.of(x, y));

//                double baseline = GraphUtils.calculatePercentDependent(test, pValues);

                if (_pValues.size() > 10 && GraphUtils.pValuesAdP(_pValues) > test.getAlpha()) {
//                    if (GraphUtils.calculatePercentDependent(test, _pValues) <= test.getAlpha()) {
                    pValues = _pValues;
                    return true;
                }
            } else {
                return true;
            }
        }

        return false;
    }
}
