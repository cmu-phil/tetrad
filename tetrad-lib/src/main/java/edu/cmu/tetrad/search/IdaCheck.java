package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;

import java.util.*;

/**
 * This calculates IDA effects for a CPDAG G for all pairs distinct (x, y) of variables, where the effect is the minimum
 * IDA effect of x on y, obtained by regressing y on x &cup; S and reporting the regression coefficient. Here, S ranges
 * over sets consisting of possible parents of x in G--that is, a set consisting of the parents of x in G plus some
 * combination of the neighbors for x in G, and excluding any children of x in G. Here, x and y may be any nodes in G.
 * These are IDA effects as defined in the following paper:
 * <p>
 * Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann. "Estimating high-dimensional intervention effects from
 * observational data." The Annals of Statistics 37.6A (2009): 3133-3164.
 * <p>
 * Additionally, if a linear SEM model is supplied over the same variables, a minimum squared distance is calculated of
 * each coefficient in this IM from the interval of IDA coefficients (min to max) for the pair (x, y) for each directed
 * edge x->y in the SEM IM. Here, (x, y) pairs are limited to the directed edges of the SEM model. If the true
 * coefficient falls within the interval [min, max], we give a distance of 0; otherwise, we give the distance to the
 * nearest endpoint. This distance is then squared, and we report the average of these squared distances.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Ida
 */
public class IdaCheck {

    /**
     * Represents a data set used in the IDA check.
     */
    private final DataSet dataSet;

    /**
     * The nodes in the dataset.
     */
    private final List<Node> nodes;

    /**
     * A list of OrderedPair objects representing all possible pairs of distinct nodes.
     */
    private final List<OrderedPair<Node>> pairs;

    /**
     * A map containing effects between node pairs. The keys of the map are instances of OrderedPair, which represents an
     * unordered pair of nodes. The values of the map are lists of Double values, representing the effects between the
     * node pairs.
     */
    private final Map<OrderedPair<Node>, LinkedList<Double>> effects;

    /**
     * The instance of IDA used in this class to calculate node effects and distances.
     */
    private final Ida ida;

    /**
     * Constructs a new IDA check for the given CPDAG and data set.
     */
    public IdaCheck(Graph graph, DataSet dataSet) {

        // check for null
        if (graph == null) {
            throw new NullPointerException("CPDAG is null.");
        }

        if (dataSet == null) {
            throw new NullPointerException("DataSet is null.");
        }

        // Check to make sure the graph is either a DAG or a CPDAG.
        if (!(graph.paths().isLegalDag() || graph.paths().isLegalCpdag())) {
            throw new IllegalArgumentException("Expecting a DAG or a CPDAG.");
        }

        // Convert the CPDAG to a CPDAG with the same nodes as the data set
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        // Check to makes sure the set of variables from the CPDAG is the same as the set of variables from the data set.
        if (!graph.getNodes().equals(dataSet.getVariables())) {
            throw new IllegalArgumentException("The variables in the CPDAG do not match the variables in the data set.");
        }

        this.dataSet = dataSet;
        this.nodes = dataSet.getVariables();
        this.effects = new HashMap<>();
        this.ida = new Ida(this.dataSet, graph, nodes);
        this.pairs = calcOrderedPairs();

        for (OrderedPair<Node> pair : calcOrderedPairs()) {
            LinkedList<Double> effects = ida.getEffects(pair.getFirst(), pair.getSecond());
            this.effects.put(pair, effects);
        }
    }

    /**
     * Returns a list of nodes.
     *
     * @return the list of nodes.
     */
    public List<Node> getNodes() {
        return new ArrayList<>(this.nodes);
    }

    /**
     * Retrieves a list of OrderedPair objects representing all possible pairs of distinct nodes in the graph.
     *
     * @return a list of OrderedPair objects.
     */
    public List<OrderedPair<Node>> getOrderedPairs() {
        return new ArrayList<>(this.pairs);
    }

    /**
     * Gets the minimum effect value between two nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return the minimum effect value between the two nodes.
     * @throws IllegalArgumentException if the nodes x and y are the same.
     */
    public double getMinEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.effects.get(new OrderedPair<>(x, y));
        return effects.getFirst();
    }

    /**
     * Returns the maximum effect value between two nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return the maximum effect value between the two nodes.
     * @throws IllegalArgumentException if the nodes x and y are the same.
     */
    public double getMaxEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.effects.get(new OrderedPair<>(x, y));
        return effects.getLast();
    }

    /**
     * Calculates the squared distance of the true effect to the [min, max] IDA effect range of the given (x, y) node
     * pair. If the true effect falls within [min, max], the method returns 0. Otherwise, the squared distance to the
     * nearest endpoint of the [min, max] range is returned.
     *
     * @param x          the first node.
     * @param y          the second node.
     * @param trueEffect the true effect value.
     * @return the squared distance between the two nodes.
     */
    public double getSquaredDistance(Node x, Node y, double trueEffect) {
        double distance = ida.distance(this.effects.get(new OrderedPair<>(x, y)), trueEffect);
        return distance * distance;
    }

    /**
     * Calculates a list of OrderedPair objects representing all possible pairs of distinct nodes in the graph.
     *
     * @return a list of OrderedPair objects.
     */
    private List<OrderedPair<Node>> calcOrderedPairs() {
        List<OrderedPair<Node>> OrderedPairs = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                OrderedPairs.add(new OrderedPair<>(nodes.get(i), nodes.get(j)));
            }
        }

        return OrderedPairs;
    }
}
