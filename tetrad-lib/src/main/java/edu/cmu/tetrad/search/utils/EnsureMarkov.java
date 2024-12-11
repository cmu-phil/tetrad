package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A helper class to encapsulate logic for ensuring a Markov property for subsequent testing after an initial local
 * Markov graph has been found.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see GraphUtils
 */
public class EnsureMarkov {
    /**
     * The initial Markov graph
     */
    private final Graph dag;
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
    /**
     * A boolean that determines whether to ensure Markov property.
     */
    private boolean ensureMarkov = false;

    /**
     * Constructs an EnsureMarkov class for a given Markov dag.
     *
     * @param dag  The initial Markov dag. This dag should pass a local Markov check.
     * @param test The independence test to use.
     */
    public EnsureMarkov(Graph dag, IndependenceTest test) {
        this.dag = new EdgeListGraph(dag);
        this.test = test;
    }

    /**
     * Adjusts the p-values for a local Markov condition in a given constraint-based partially directed acyclic graph
     * (CPDAG).
     *
     * @param cpdag        the constraint-based partially directed acyclic graph (CPDAG) to adjust p-values for
     * @param ensureMarkov a boolean flag indicating if the Markov condition should be ensured; should be true
     * @param test         the independence test to be used; must not be null and not an instance of MsepTest
     * @param pValues      a map of node pairs to sets of p-values used for adjustment
     * @param withoutPair  a pair of nodes for which adjustments are calculated without considering the edge between
     *                     them
     * @return a map of node pairs to sets of adjusted p-values
     * @throws IllegalArgumentException if ensureMarkov is false or if the test is null or an instance of MsepTest
     */
    public static Map<Pair<Node, Node>, Set<Double>> localMarkovAdjustPValues(Graph cpdag, boolean ensureMarkov, IndependenceTest test,
                                                                              Map<Pair<Node, Node>, Set<Double>> pValues, Pair<Node, Node> withoutPair) throws InterruptedException {
        if (!ensureMarkov) {
            throw new IllegalArgumentException("This method should only be called when ensureMarkov is true.");
        }

        if (test == null || test instanceof MsepTest) {
            throw new IllegalArgumentException("This method should only be called when the test is not null and not an instance of MsepTest.");
        }

        Node x = withoutPair.getLeft();
        Node y = withoutPair.getRight();

        var _pValues = new HashMap<>(pValues);

//        MsepTest msep = new MsepTest(cpdag);

        Set<Node> parentsX = new HashSet<>(cpdag.getParents(x));

        for (Node node : parentsX) {
            if (node.equals(y)) {
                parentsX.remove(node);
                break;
            }
        }

        Set<Node> parentsY = new HashSet<>(cpdag.getParents(y));

        for (Node node : parentsY) {
            if (node.equals(x)) {
                parentsY.remove(node);
                break;
            }
        }

        _pValues.remove(Pair.of(x, y));
        _pValues.remove(Pair.of(y, x));

        for (Node _y : cpdag.getNodes()) {
            if (x.equals(_y)) {
                continue;
            }

            if (!parentsX.contains(_y) && !cpdag.paths().existsDirectedPath(x, _y, withoutPair)) {
                IndependenceResult result = test.checkIndependence(x, _y, parentsX);
//                if (msep.checkIndependence(x, _y, parentsX).isIndependent()) {
                _pValues.putIfAbsent(Pair.of(x, _y), new HashSet<>());
                _pValues.get(Pair.of(x, _y)).add(result.getPValue());
//                }
            }
        }

        for (Node _x : cpdag.getNodes()) {
            if (y.equals(_x)) {
                continue;
            }

            if (!parentsY.contains(_x) && !cpdag.paths().existsDirectedPath(y, _x, withoutPair)) {
                IndependenceResult result = test.checkIndependence(y, _x, parentsY);
//                if (msep.checkIndependence(y, _x, parentsY).isIndependent()) {
                _pValues.putIfAbsent(Pair.of(y, _x), new HashSet<>());
                _pValues.get(Pair.of(y, _x)).add(result.getPValue());
//                }
            }
        }

        return _pValues;
    }

    /**
     * Sets whether to ensure Markov property. By default, false; this must be turned on.
     *
     * @param ensureMarkov True if the Markov property should be ensured.
     */
    public void setEnsureMarkov(boolean ensureMarkov) throws InterruptedException {
        this.ensureMarkov = ensureMarkov;

        if (ensureMarkov) {
            double initialFraction = GraphUtils.localMarkovInitializePValues(
                    dag, ensureMarkov, test, pValues);
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
     */
    public boolean markovIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceResult result = test.checkIndependence(x, y, z);

        if (result.isIndependent()) {
            if (ensureMarkov) {
                Map<Pair<Node, Node>, Set<Double>> _pValues = localMarkovAdjustPValues(dag, ensureMarkov,
                        test, pValues, Pair.of(x, y));

//                double baseline = GraphUtils.calculatePercentDependent(test, pValues);

                if (GraphUtils.pValuesAdP(_pValues) > test.getAlpha()) {
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
