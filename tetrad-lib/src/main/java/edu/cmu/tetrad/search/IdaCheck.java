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
     * A list of NodePair objects representing all possible pairs of distinct nodes.
     */
    private final List<NodePair> nodePairs;

    /**
     * A map containing effects between node pairs. The keys of the map are instances of NodePair, which represents an
     * unordered pair of nodes. The values of the map are lists of Double values, representing the effects between the
     * node pairs.
     */
    private final Map<NodePair, LinkedList<Double>> effects;

    /**
     * The instance of IDA used in this class to calculate node effects and distances.
     */
    private final Ida ida;

    /**
     * Constructs a new IDA check for the given CPDAG and data set.
     */
    public IdaCheck(Graph cpdag, DataSet dataSet) {

        // check for null
        if (cpdag == null) {
            throw new NullPointerException("CPDAG is null.");
        }
        if (dataSet == null) {
            throw new NullPointerException("DataSet is null.");
        }

        // Check to make sure the CPDAG is legal
        if (!cpdag.paths().isLegalCpdag()) {
            throw new IllegalArgumentException("Expecting a CPDAG.");
        }

        // Convert the CPDAG to a CPDAG with the same nodes as the data set
        cpdag = GraphUtils.replaceNodes(cpdag, dataSet.getVariables());

        // Check to makes sure the set of variables from the CPDAG is the same as the set of variables from the data set.
        if (!cpdag.getNodes().equals(dataSet.getVariables())) {
            throw new IllegalArgumentException("The variables in the CPDAG do not match the variables in the data set.");
        }

        this.dataSet = dataSet;
        this.nodes = dataSet.getVariables();
        this.effects = new HashMap<>();
        this.ida = new Ida(this.dataSet, cpdag, nodes);
        this.nodePairs = calcNodePairs();

        for (NodePair nodePair : calcNodePairs()) {
            LinkedList<Double> effects = ida.getEffects(nodePair.getFirst(), nodePair.getSecond());
            this.effects.put(nodePair, effects);
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
     * Retrieves a list of NodePair objects representing all possible pairs of distinct nodes in the graph.
     *
     * @return a list of NodePair objects.
     */
    public List<NodePair> getNodePairs() {
        return new ArrayList<>(this.nodePairs);
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
        LinkedList<Double> effects = this.effects.get(new NodePair(x, y));
        Collections.sort(effects);
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
        LinkedList<Double> effects = this.effects.get(new NodePair(x, y));
        Collections.sort(effects);
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
        double distance = ida.distance(this.effects.get(new NodePair(x, y)), trueEffect);
        return distance * distance;
    }

    /**
     * Calculates the average squared distance between all pairs of nodes in the given SemIm object.
     * <p>
     * This method calculates the average squared distance by iterating over all edges in the SemGraph of the SemIm
     * object. For each edge, it retrieves the tail node (x), the head node (y), and the true effect value. It then
     * calculates the squared distance using the getSquaredDistance() method and adds it to the sum. After iterating
     * over all edges, it returns the sum divided by the total number of edges.
     * <p>
     * If the SemGraph is not a legal DAG (Directed Acyclic Graph), an IllegalArgumentException is thrown. Furthermore,
     * if the variables in the SemGraph's DAG are not the same as the variables in the DataSet, another
     * IllegalArgumentException is thrown. If the graph is empty (i.e., no edges), an IllegalArgumentException is also
     * thrown.
     *
     * @param im The SemIm object containing the SemGraph to calculate the average squared distance from.
     * @return The average squared distance between all pairs of nodes in the SemGraph.
     * @throws IllegalArgumentException If the SemGraph is not a legal DAG, the variables in the DAG are different from
     *                                  the variables in the DataSet, or the graph is empty.
     */
    public double getAverageSquaredDistance(SemIm im) {
        SemGraph graph = im.getSemPm().getGraph();
        graph.setShowErrorTerms(false);

        if (!graph.paths().isLegalDag()) {
            throw new IllegalArgumentException("Expecting a DAG from the SEM model.");
        }

        Graph dag = new EdgeListGraph(graph);
        Graph _dag = GraphUtils.replaceNodes(dag, dataSet.getVariables());

        if (!new HashSet<>(_dag.getNodes()).equals(new HashSet<>(dataSet.getVariables()))) {
            throw new IllegalArgumentException("Expecting the variables in the DAG for this SEM model to be the " + "same as the variables in the dataset.");
        }

        double sum = 0.0;
        int count = 0;

        Set<Edge> edges = dag.getEdges();

        if (edges.isEmpty())
            throw new IllegalArgumentException("The graph for this model is empty, so we can't calculate " + "an average squared IDA distance.");

        for (Edge edge : edges) {
            Node x = Edges.getDirectedEdgeTail(edge);
            Node y = Edges.getDirectedEdgeHead(edge);
            double trueEffect = im.getEdgeCoef(edge);
            double squaredDistance = getSquaredDistance(x, y, trueEffect);
            sum += squaredDistance;
            count++;
        }

        return sum / count;
    }

    /**
     * Calculates a list of NodePair objects representing all possible pairs of distinct nodes in the graph.
     *
     * @return a list of NodePair objects.
     */
    private List<NodePair> calcNodePairs() {
        List<NodePair> nodePairs = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                nodePairs.add(new NodePair(nodes.get(i), nodes.get(j)));
            }
        }

        return nodePairs;
    }
}
