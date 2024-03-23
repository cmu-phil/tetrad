package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.sem.SemIm;

import java.util.*;

/**
 * This calculates total effects and absolute total effects for an MPDAG G for all pairs distinct (x, y) of variables,
 * where the total effect is obtained by regressing y on x &cup; S and reporting the regression coefficient. Here, S
 * ranges over sets consisting of possible parents of x in G--that is, a set consisting of the parents of x in G plus
 * some combination of the neighbors for x in G, and excluding any children of x in G. Absolute total effects are
 * calculated as the abolute values of the total effects; the minimum values for these across possible parent sets is
 * reported as suggested in this paper:
 * <p>
 * Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann. "Estimating high-dimensional intervention effects from
 * observational data." The Annals of Statistics 37.6A (2009): 3133-3164.
 * <p>
 * Additionally, if a linear SEM model is supplied over the same variables, a minimum squared distance is calculated of
 * each coefficient in this IM from the interval of total effects (min to max) for the pair (x, y) for each directed
 * edge x->y in the SEM IM. If the true total effect falls within the interval [min, max], we give a distance of 0;
 * otherwise, we give the distance to the nearest endpoint. This distance is then squared.
 * <p>
 * We also report the averages of each statistic across all pairs of distinct nodes in the dataset.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Ida
 */
public class IdaCheck {

    /**
     * The nodes in the dataset.
     */
    private final List<Node> nodes;

    /**
     * A list of OrderedPair objects representing all possible pairs of distinct nodes.
     */
    private final List<OrderedPair<Node>> pairs;

    /**
     * A map from ordered pairs of nodes X-&gt;Y to a list of total effects for X on Y.
     */
    private final Map<OrderedPair<Node>, LinkedList<Double>> totalEffects;

    /**
     * A map from ordered pairs of nodes X-&gt;Y to a list of absolute total effects for X on Y.
     */
    private final Map<OrderedPair<Node>, LinkedList<Double>> absTotalEffects;

    /**
     * The instance of IDA used in this class to calculate node effects and distances.
     */
    private final Ida ida;
    private final SemIm trueSemIm;

    /**
     * Constructs a new IDA check for the given MPDAG and data set.
     */
    public IdaCheck(Graph graph, DataSet dataSet, SemIm trueSemIm) {

        // check for null
        if (graph == null) {
            throw new NullPointerException("Graph is null.");
        }

        if (dataSet == null) {
            throw new NullPointerException("DataSet is null.");
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous data set.");
        }

        // Check to make sure the graph is an MPDAG.
        if (!graph.paths().isLegalMpdag()) {
            throw new IllegalArgumentException("Expecting an MPDAG.");
        }

        // Convert the MPDAG to a MPDAG with the same nodes as the data set
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());

        // Check to make sure the set of variables from the MPDAG is the same as the set of variables from the data set.
        if (!new HashSet<>(graph.getNodes()).equals(new HashSet<>(dataSet.getVariables()))) {
            throw new IllegalArgumentException("The variables in the MPDAG do not match the variables in the data set.");
        }

        this.nodes = dataSet.getVariables();
        this.totalEffects = new HashMap<>();
        this.absTotalEffects = new HashMap<>();
        this.ida = new Ida(dataSet, graph, nodes);
        this.pairs = calcOrderedPairs();

        for (OrderedPair<Node> pair : calcOrderedPairs()) {
            LinkedList<Double> totalEffects = ida.getTotalEffects(pair.getFirst(), pair.getSecond());
            LinkedList<Double> absTotalEffects = ida.getAbsTotalEffects(pair.getFirst(), pair.getSecond());
            this.totalEffects.put(pair, totalEffects);
            this.absTotalEffects.put(pair, absTotalEffects);
        }

        this.trueSemIm = trueSemIm;
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
     * Gets the minimum total effect value between two nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return the minimum total effect value between the two nodes.
     * @throws IllegalArgumentException if the nodes x and y are the same.
     */
    public double getMinTotalEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.totalEffects.get(new OrderedPair<>(x, y));
        return effects.getFirst();
    }

    /**
     * Returns the maximum total effect value between two nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return the maximum total effect value between the two nodes.
     * @throws IllegalArgumentException if the nodes x and y are the same.
     */
    public double getMaxTotalEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.totalEffects.get(new OrderedPair<>(x, y));
        return effects.getLast();
    }

    /**
     * Gets the minimum absolute total effect value between two nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return the minimum absolute total effect value between the two nodes.
     * @throws IllegalArgumentException if the nodes x and y are the same.
     */
    public double getMinAbsTotalEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.absTotalEffects.get(new OrderedPair<>(x, y));
        return effects.getFirst();
    }

    /**
     * Calculates the squared distance of the true total effect to the [min, max] IDA effect range of the given (x, y)
     * node pair. If the true effect falls within [min, max], the method returns 0. Otherwise, the squared distance to
     * the nearest endpoint of the [min, max] range is returned.
     *
     * @param x          the first node.
     * @param y          the second node.
     * @param trueEffect the true effect value.
     * @return the squared distance between the two nodes.
     */
    public double getSquaredDistance(Node x, Node y, double trueEffect) {
        double distance = ida.distance(this.totalEffects.get(new OrderedPair<>(x, y)), trueEffect);
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

    /**
     * The true SEM IM. May be null.
     */
    public SemIm getTrueSemIm() {
        return trueSemIm;
    }
}
