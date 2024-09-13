package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.SublistGenerator;

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

    /**
     * The true SEM IM, if given.
     */
    private final SemIm trueSemIm;

    /**
     * A map from nodes in the estimated model to nodes in the SEM IM.
     */
    private HashMap<Node, Node> nodeMap;

    /**
     * Constructs a new IDA check for the given MPDAG and data set.
     *
     * @param graph     the MPDAG.
     * @param dataSet   the data set.
     * @param trueSemIm the true SEM IM.
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

        if (this.trueSemIm != null) {

            // If the true model is given, make a map from nodes in the estimated model to nodes in the SEM IM.
            // This is used to calculate the true total effects.
            nodeMap = new HashMap<>();

            for (Node node : getNodes()) {
                Node _node = trueSemIm.getSemPm().getGraph().getNode(node.getName());
                nodeMap.put(node, _node);
            }
        }
    }

    /**
     * Returns the squared distances of the true beta to the nearest endpoint of [minBeta, maxBeta]. Here, the true beta
     * is obtained from the true SEM IM by regressing x on y and all of y's true parents. In addition, minBeta is the
     * minimum beta coefficient of y regressed on x and all possible parents sets of x in the given dataset. Also,
     * maxBeta is the maximum beta coefficient of y regressed on x and all possible parents sets of x in the given
     * dataset.
     * <p>
     * This is an adaptation of IDA to the problem of calculating an IDA-like distance for a node conditional on its
     * parents, rather than for a pair of nodes. The parents maybe be different from the true model to the estimated
     * model, so need to consider the range of possible parents for the node in the estimated CPDAG (taking account of
     * the undirected edges adjacent to the node). We need to calculate the distance of the true beta to the nearest
     * endpoint for the range of possible betas for the node, given the possible parents.
     *
     * @param y               the name of the parent node.
     * @param x               the name of the child node.
     * @param trueCoefficient the true beta coefficient of y regressed on x and all of y's true parents.
     * @param mpdag           the MPDAG. (CPDAG, DAG, or CPDAG with background knowledge)
     * @param dataSet         the data set.
     * @return the average of the squared distances between the true total effects and the IDA effect ranges for a pair
     * y -&gt; x over all possible parents sets of y.
     */
    public static double getAverageSquaredDistanceNodeOnParent(String y, String x, double trueCoefficient, Graph mpdag,
                                                               DataSet dataSet) {

        // Make sure the arguments are not null.
        if (mpdag == null) {
            throw new NullPointerException("Graph is null.");
        }

        if (dataSet == null) {
            throw new NullPointerException("DataSet is null.");
        }

        mpdag = GraphUtils.replaceNodes(mpdag, dataSet.getVariables());

        Node yNode = dataSet.getVariable(y);
        Node xNode = dataSet.getVariable(x);

        if (xNode == null) {
            throw new NullPointerException("Node x is not in the data.");
        }

        if (yNode == null) {
            throw new NullPointerException("Node y is not in the data.");
        }

        if (!mpdag.containsNode(yNode)) {
            throw new IllegalArgumentException("The node y is not in the MPDAG.");
        }

        // Make sure the data set is continuous (not discrete or mixed).
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous data set.");
        }

        // Check to make sure the graph is an MPDAG.
        if (!mpdag.paths().isLegalMpdag()) {
            throw new IllegalArgumentException("Expecting an MPDAG.");
        }

        // Check to make sure the names of the variables in the mpdag are contained in the names of variables
        // in dataset as well as in the true SEM IM.
        if (!new HashSet<>(dataSet.getVariableNames()).containsAll(mpdag.getNodeNames())) {
            throw new IllegalArgumentException("The variables in the MPDAG are not contained in the data set.");
        }

        // Check if x and y are adjacent
        if (!mpdag.isAdjacentTo(yNode, xNode)) {
            return 0.0;
        }

        // Check if y is a parent of x
        if (!mpdag.isParentOf(yNode, xNode)) {
            return 0.0;
        }

        // Get the edges adjacent to y.
        Set<Edge> edges = mpdag.getEdges(yNode);

        // Separate edges into lists of those that are parents and those that are undirected. Discard any that are
        // children of y.
        List<Edge> parentEdges = new ArrayList<>();
        List<Edge> undirectedEdges = new ArrayList<>();

        for (Edge edge : edges) {
            if (mpdag.isParentOf(edge.getDistalNode(yNode), yNode)) {
                parentEdges.add(edge);
            } else if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge);
            }
        }

        RegressionDataset regressionDatasetSample = new RegressionDataset(dataSet);

        // Iterate over all combinations of the undirected edges; these can possibly be parents of x. Form all
        // combinations of the undirected edges and the parent edges.
        // Find the minimum and maximum beta values for the combinations.
        SublistGenerator sublistGenerator = new SublistGenerator(undirectedEdges.size(), undirectedEdges.size());
        int[] choice;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        while ((choice = sublistGenerator.next()) != null) {
            List<Edge> combination = new ArrayList<>();

            for (int i : choice) {
                combination.add(undirectedEdges.get(i));
            }

            combination.addAll(parentEdges);

            // Get the variables for the regression.
            List<Node> regressors = new ArrayList<>();
            regressors.add(yNode); // y must be the first regressor

            // Now add the other nodes in the combination.
            for (Edge edge : combination) {
                regressors.add(edge.getDistalNode(yNode));
            }

            RegressionResult result = regressionDatasetSample.regress(xNode, regressors);
            double betaThisCombination = result.getCoef()[1];

            if (betaThisCombination <= min) {
                min = betaThisCombination;
            }

            if (betaThisCombination >= max) {
                max = betaThisCombination;
            }
        }

        // Calculate the squared distance of trueCoefficient to the closest of max or min, or zero if trueCoefficient is between
        // max and min.
        if (trueCoefficient > min && trueCoefficient < max) {
            return 0.0;
        } else {
            double diff = Math.min(Math.abs(trueCoefficient - min), Math.abs(trueCoefficient - max));
            return diff * diff;
        }
    }

    /**
     * Calculates the true total effect between two nodes in the graph.
     *
     * @param pair the ordered pair of nodes for which the total effect is calculated.
     * @return the true total effect between the two nodes.
     */
    public double getTrueTotalEffect(OrderedPair<Node> pair) {
        return this.trueSemIm.getTotalEffect(nodeMap.get(pair.getFirst()), nodeMap.get(pair.getSecond()));
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
     * Gets the signed minimum absolute total effect value between two nodes.
     *
     * @param x the first node.
     * @param y the second node.
     * @return the signed minimum absolute total effect value between the two nodes.
     * @throws IllegalArgumentException if the nodes x and y are the same.
     */
    public double getIdaMinEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.absTotalEffects.get(new OrderedPair<>(x, y));
        LinkedList<Double> totalEffects = this.totalEffects.get(new OrderedPair<>(x, y));
        Double first = effects.getFirst();
        double ret = Double.NaN;

        for (Double totalEffect : totalEffects) {
            if (Math.abs(totalEffect) == first) {
                ret = totalEffect;
            }
        }

        return ret;
    }

    /**
     * Calculates the squared distance of the true total effect to the [min, max] IDA effect range of the given (x, y)
     * node pair, for x predicting y. If the true effect falls within [min, max], the method returns 0. Otherwise, the
     * squared distance to the nearest endpoint of the [min, max] range is returned.
     *
     * @param pair the pair of nodes.
     * @return the squared distance between the two nodes.
     */
    public double getSquaredDistance(OrderedPair<Node> pair) {
        Node x = pair.getFirst();
        Node y = pair.getSecond();
        double trueTotalEffect = getTrueTotalEffect(pair);
        double distance = ida.distance(this.totalEffects.get(new OrderedPair<>(x, y)), trueTotalEffect);
        return distance * distance;
    }

    /**
     * Returns the average of the squared distances between the true total effects and the IDA effect ranges the list of
     * node pairs indicated.
     *
     * @param pairs the list of node pairs.
     * @return the average of the squared distances between the true total effects and the IDA effect ranges.
     */
    public double getAverageSquaredDistance(List<OrderedPair<Node>> pairs) {
        List<OrderedPair<Node>> _pairs = getOrderedPairs();
        double sum = 0.0;

        for (OrderedPair<Node> pair : pairs) {
            if (!_pairs.contains(pair)) {
                throw new IllegalArgumentException("The pair " + pair + " is not in the dataset.");
            }

            sum += getSquaredDistance(pair);
        }

        return sum / pairs.size();
    }

    /**
     * Returns the squared difference between the minimum total effect and the true total effect for the given pair of
     * nodes.
     *
     * @param pair the pair of nodes.
     * @return the squared difference between the minimum total effect and the true total effect.
     */
    public double getSquaredMinTrueDistance(OrderedPair<Node> pair) {
        Node x = pair.getFirst();
        Node y = pair.getSecond();

        List<Double> totalEffects = ida.getTotalEffects(x, y);
        double trueTotalEffect = getTrueTotalEffect(pair);

        double min = Double.MAX_VALUE;

        for (double totalEffect : totalEffects) {
            double diff = totalEffect - trueTotalEffect;
            diff *= diff;
            if (diff < min) {
                min = diff;
            }
        }

        return min;
    }

    /**
     * Returns the average of the squared differences between the minimum total effects and the true total effects for
     * the list of node pairs indicated.
     *
     * @param pairs the list of node pairs.
     * @return the average of the squared differences between the minimum total effects and the true total effects.
     */
    public double getAvgMinSquaredDiffEstTrue(List<OrderedPair<Node>> pairs) {
        List<OrderedPair<Node>> _pairs = getOrderedPairs();
        double sum = 0.0;

        for (OrderedPair<Node> pair : pairs) {
            if (!_pairs.contains(pair)) {
                throw new IllegalArgumentException("The pair " + pair + " is not in the dataset.");
            }

            sum += getSquaredMinTrueDistance(pair);
        }

        return sum / pairs.size();
    }

    /**
     * Returns the squared difference between the maximum total effect and the true total effect for the given pair of
     * nodes.
     *
     * @param pair the pair of nodes.
     * @return the squared difference between the maximum total effect and the true total effect.
     */
    public double getSquaredMaxTrueDist(OrderedPair<Node> pair) {
        Node x = pair.getFirst();
        Node y = pair.getSecond();

        List<Double> totalEffects = ida.getTotalEffects(x, y);
        double trueTotalEffect = getTrueTotalEffect(pair);

        double max = 0;

        for (double totalEffect : totalEffects) {
            double diff = totalEffect - trueTotalEffect;
            diff *= diff;
            if (diff > max) {
                max = diff;
            }
        }

        return max;
    }

    /**
     * Returns the average of the squared differences between the maximum total effects and the true total effects for
     * the list of node pairs indicated.
     *
     * @param pairs the list of node pairs.
     * @return the average of the squared differences between the maximum total effects and the true total effects.
     */
    public double getAvgMaxSquaredDiffEstTrue(List<OrderedPair<Node>> pairs) {
        List<OrderedPair<Node>> _pairs = getOrderedPairs();
        double sum = 0.0;

        for (OrderedPair<Node> pair : pairs) {
            if (!_pairs.contains(pair)) {
                throw new IllegalArgumentException("The pair " + pair + " is not in the dataset.");
            }

            sum += getSquaredMaxTrueDist(pair);
        }

        return sum / pairs.size();
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
