/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.OrderedPair;
import edu.cmu.tetrad.sem.SemIm;

import java.util.*;

/**
 * This calculates total effects and absolute total effects for an PDAG G for all pairs distinct (x, y) of variables,
 * where the total effect is obtained by regressing y on x &cup; S and reporting the regression coefficient. Here, S
 * ranges over sets consisting of possible parents of x in G--that is, a set consisting of the parents of x in G plus
 * some combination of the neighbors for x in G, and excluding any children of x in G. Absolute total effects are
 * calculated as the abolute values of the total effects; the minimum values for these across possible parent sets is
 * reported as suggested in this paper:
 * <p>
 * Maathuis, Marloes H., Markus Kalisch, and Peter BÃ¼hlmann. "Estimating high-dimensional intervention effects from
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
     * The graph being used to estimate IDA.
     */
    private final Graph graph;

    /**
     * A map from nodes in the estimated model to nodes in the SEM IM.
     */
    private HashMap<Node, Node> nodeMap;

    /**
     * Indicates whether the optimal IDA results should be displayed (Witte et al. 2020). Typically used as a flag to
     * determine if specific computations or results related to the optimal IDA should be shown in the context of
     * analyzing total effects or related metrics between nodes.
     */
    private boolean showOptimalIda = true;

    /**
     * Constructs a new IDA check for the given PDAG and data set.
     *
     * @param graph     the PDAG.
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

        // Check to make sure the graph is an PDAG.
        if (!(graph.paths().isLegalPdag() || graph.paths().isLegalPag())) {
            throw new IllegalArgumentException("Expecting an PDAG or PAG    .");
        }

        // Convert the PDAG to a PDAG with the same nodes as the data set
        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        this.graph = graph;

        // Check to make sure the set of variables from the PDAG is the same as the set of variables from the data set.
        if (!new HashSet<>(graph.getNodes()).equals(new HashSet<>(dataSet.getVariables()))) {
            throw new IllegalArgumentException("The variables in the PDAG do not match the variables in the data set.");
        }

        this.nodes = dataSet.getVariables();
        this.totalEffects = new HashMap<>();
        this.absTotalEffects = new HashMap<>();
        this.ida = new Ida(dataSet, graph, nodes);
        this.pairs = calcOrderedPairs();

        // Fill totalEffects / absTotalEffects respecting current idaType
        computeIdaResults();

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
     * (Re)computes totalEffects and absTotalEffects for all ordered pairs
     * using the current IDA type (REGULAR vs OPTIMAL).
     */
    private void computeIdaResults() {
        // Make sure Ida is in sync with the flag
        ida.setIdaType(showOptimalIda ? Ida.IDA_TYPE.OPTIMAL : Ida.IDA_TYPE.REGULAR);

        // Clear old results
        this.totalEffects.clear();
        this.absTotalEffects.clear();

        // Recompute for all pairs
        for (OrderedPair<Node> pair : this.pairs) {
            LinkedList<Double> total = ida.getTotalEffects(pair.getFirst(), pair.getSecond());
            LinkedList<Double> abs = ida.getAbsTotalEffects(pair.getFirst(), pair.getSecond());

            this.totalEffects.put(pair, total);
            this.absTotalEffects.put(pair, abs);
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
//    public double getMinTotalEffect(Node x, Node y) {
//        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
//        LinkedList<Double> effects = this.totalEffects.get(new OrderedPair<>(x, y));
//        return effects.getFirst();
//    }
    public double getMinTotalEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.totalEffects.get(new OrderedPair<>(x, y));
        if (effects == null || effects.isEmpty()) {
            return Double.NaN;  // not O-set-eligible
        }
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
//    public double getMaxTotalEffect(Node x, Node y) {
//        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
//        LinkedList<Double> effects = this.totalEffects.get(new OrderedPair<>(x, y));
//        return effects.getLast();
//    }
    public double getMaxTotalEffect(Node x, Node y) {
        if (x == y) throw new IllegalArgumentException("Expecting the nodes x and y to be distinct.");
        LinkedList<Double> effects = this.totalEffects.get(new OrderedPair<>(x, y));
        if (effects == null || effects.isEmpty()) {
            return Double.NaN;  // not O-set-eligible
        }
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

        OrderedPair<Node> key = new OrderedPair<>(x, y);
        LinkedList<Double> abs = this.absTotalEffects.get(key);
        LinkedList<Double> total = this.totalEffects.get(key);

        if (abs == null || abs.isEmpty() || total == null || total.isEmpty()) {
            return Double.NaN; // not O-set-eligible
        }

        double targetAbs = abs.getFirst();  // smallest |effect|
        double ret = Double.NaN;

        for (double te : total) {
            if (Math.abs(te) == targetAbs) {
                ret = te;
                break;
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
        LinkedList<Double> effects = this.totalEffects.get(pair);
        if (effects == null || effects.isEmpty()) {
            return Double.NaN;  // no O-IDA effects for this pair
        }

        double trueTotalEffect = getTrueTotalEffect(pair);
        double distance = ida.distance(effects, trueTotalEffect);
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
        List<OrderedPair<Node>> allPairs = getOrderedPairs();
        double sum = 0.0;
        int count = 0;

        for (OrderedPair<Node> pair : pairs) {
            if (!allPairs.contains(pair)) {
                throw new IllegalArgumentException("The pair " + pair + " is not in the dataset.");
            }

            LinkedList<Double> effects = this.totalEffects.get(pair);
            if (effects == null || effects.isEmpty()) {
                continue;  // not O-set-eligible
            }

            sum += getSquaredDistance(pair);
            count++;
        }

        return count == 0 ? Double.NaN : sum / count;
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

        if (totalEffects.isEmpty()) {
            return trueTotalEffect;
        }

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
        List<OrderedPair<Node>> allPairs = getOrderedPairs();
        double sum = 0.0;
        int count = 0;

        for (OrderedPair<Node> pair : pairs) {
            if (!allPairs.contains(pair)) {
                throw new IllegalArgumentException("The pair " + pair + " is not in the dataset.");
            }

            LinkedList<Double> effects = this.totalEffects.get(pair);
            if (effects == null || effects.isEmpty()) {
                continue;  // not O-set-eligible
            }

            sum += getSquaredMinTrueDistance(pair);
            count++;
        }

        return count == 0 ? Double.NaN : sum / count;
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

        if (totalEffects.isEmpty()) {
            return trueTotalEffect;
        }

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
        List<OrderedPair<Node>> allPairs = getOrderedPairs();
        double sum = 0.0;
        int count = 0;

        for (OrderedPair<Node> pair : pairs) {
            if (!allPairs.contains(pair)) {
                throw new IllegalArgumentException("The pair " + pair + " is not in the dataset.");
            }

            LinkedList<Double> effects = this.totalEffects.get(pair);
            if (effects == null || effects.isEmpty()) {
                continue;  // not O-set-eligible
            }

            sum += getSquaredMaxTrueDist(pair);
            count++;
        }

        return count == 0 ? Double.NaN : sum / count;
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
     * Sets whether the "show optimal IDA" option is enabled or disabled.
     *
     * @param selected a boolean value indicating whether to enable or disable the "show optimal IDA" option.
     */
    public void setShowOptimalIda(boolean selected) {
        this.showOptimalIda = selected;
    }

    /**
     * Checks if the "show optimal IDA" option is enabled.
     *
     * @return true if the "show optimal IDA" option is enabled, false otherwise.
     */
    public boolean isShowOptimalIda() {
        return this.showOptimalIda;
    }

    public void recompute() {
        computeIdaResults();
    }

    /**
     * The graph being used to estimate IDA.
     */
    public Graph getGraph() {
        return graph.copy();
    }
}

