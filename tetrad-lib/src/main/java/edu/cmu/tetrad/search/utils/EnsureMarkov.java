package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceResult;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
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
    /**
     * A boolean that determines whether to ensure Markov property.
     */
    private boolean ensureMarkov = false;
    private double initialFraction = Double.NaN;

    /**
     * Constructs an EnsureMarkov class for a given Markov graph.
     *
     * @param graph The initial Markov graph. This graph should pass a local Markov check.
     * @param test  The independence test to use.
     */
    public EnsureMarkov(Graph graph, IndependenceTest test) {
        this.graph = new EdgeListGraph(graph);
        this.test = test;
    }

    /**
     * Sets whether to ensure Markov property. By default, false; this must be turned on.
     *
     * @param ensureMarkov True if the Markov property should be ensured.
     */
    public void setEnsureMarkov(boolean ensureMarkov) {
        this.ensureMarkov = ensureMarkov;

        if (ensureMarkov) {
            initialFraction = GraphUtils.localMarkovInitializePValues(
                    graph, ensureMarkov, test, pValues);
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
    public boolean markovIndependence(Node x, Node y, Set<Node> z) {
        IndependenceResult result = test.checkIndependence(x, y, z);

        if (result.isIndependent()  ) {
            if (ensureMarkov) {
                Map<Pair<Node, Node>, Set<Double>> _pValues = GraphUtils.localMarkovAdjustPValues(graph, ensureMarkov,
                        test, pValues, Pair.of(x, y));

                double baseline = GraphUtils.calculatePercentDependent(test, pValues);

                if (GraphUtils.calculatePercentDependent(test, _pValues) <= test.getAlpha()) {
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
