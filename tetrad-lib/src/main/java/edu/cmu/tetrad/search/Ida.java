package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.min;

/**
 * Implements the IDA algorithm. The reference is here:
 * <p>
 * Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann. "Estimating high-dimensional intervention effects from
 * observational data." The Annals of Statistics 37.6A (2009): 3133-3164.
 * <p>
 * The IDA algorithm seeks to give a list of possible parents of a given variable Y and their corresponding
 * lower-bounded effects on Y. It regresses Y on X &cup; S, where X is a possible parent of Y and S is a set of possible
 * parents of X, and reports the regression coefficient. The set of such regressions is then sorted in ascending order
 * to give the total effects of X on Y. The absolute total effects are calculated as the absolute values of the total
 * effects.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Cstar
 * @see NodeEffects
 */
public class Ida {
    /**
     * The CPDAG (found, e.g., by running PC, or some other CPDAG producing algorithm)
     */
    private final Graph cpdag;
    /**
     * The possible causes to be considered.
     */
    private final List<Node> possibleCauses;
    /**
     * A map from node names to indices in the covariance matrix.
     */
    private final Map<String, Integer> nodeIndices;
    /**
     * The covariance matrix for the dataset.
     */
    private final ICovarianceMatrix allCovariances;

    /**
     * Constructor.
     *
     * @param dataSet        The dataset being searched over.
     * @param graph          The graph model. Should be a DAG or a CPDAG.
     * @param possibleCauses The possible causes to be considered.
     */
    public Ida(DataSet dataSet, Graph graph, List<Node> possibleCauses) {
        // Check nullity
        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }

        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (possibleCauses == null) {
            throw new NullPointerException("Possible causes must not be null.");
        }

        // Check tha the graph is either a DAG or a CPDAG.
        if (!(graph.paths().isLegalDag() || graph.paths().isLegalCpdag())) {
            throw new IllegalArgumentException("Expecting a DAG or a CPDAG.");
        }

        // Check that the dataset is continuous.
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        this.cpdag = graph;
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        this.possibleCauses = possibleCauses;
        this.allCovariances = new CovarianceMatrix(dataSet);
        this.nodeIndices = new HashMap<>();

        for (int i = 0; i < graph.getNodes().size(); i++) {
            this.nodeIndices.put(graph.getNodes().get(i).getName(), i);
        }
    }

    /**
     * Returns the distance between the effects and the true effect.
     *
     * @param effects    a {@link java.util.LinkedList} object
     * @param trueEffect a double
     * @return This difference.
     */
    public double distance(LinkedList<Double> effects, double trueEffect) {
        effects = new LinkedList<>(effects);
        if (effects.isEmpty()) return Double.NaN; // counted as not estimated.

        if (effects.size() == 1) {
            double effect = effects.get(0);
            return abs(effect - trueEffect);
        } else {
            Collections.sort(effects);
            double min = effects.getFirst();
            double max = effects.getLast();

            if (trueEffect >= min && trueEffect <= max) {
                return 0.0;
            } else {
                double m1 = abs(trueEffect - min);
                double m2 = abs(trueEffect - max);
                return min(m1, m2);
            }
        }
    }

    /**
     * Calculates the total effects of node x on node y.
     *
     * @param x The node whose total effects are to be calculated.
     * @param y The node for which the total effects are calculated.
     * @return A LinkedList of Double values representing the total effects of node x on node y. The LinkedList is
     * sorted in ascending order.
     */
    public LinkedList<Double> getTotalEffects(Node x, Node y) {
        List<Node> parents = this.cpdag.getParents(x);
        List<Node> children = this.cpdag.getChildren(x);

        List<Node> siblings = new ArrayList<>(this.cpdag.getAdjacentNodes(x));
        siblings.removeAll(parents);
        siblings.removeAll(children);
        siblings.remove(y);

        int size = siblings.size();
        SublistGenerator gen = new SublistGenerator(size, size);
        int[] choice;

        LinkedList<Double> totalEffects = new LinkedList<>();

        CHOICE:
        while ((choice = gen.next()) != null) {
            try {
                List<Node> siblingsChoice = GraphUtils.asList(choice, siblings);

                if (siblingsChoice.size() > 1) {
                    ChoiceGenerator gen2 = new ChoiceGenerator(siblingsChoice.size(), 2);
                    int[] choice2;

                    while ((choice2 = gen2.next()) != null) {
                        List<Node> adj = GraphUtils.asList(choice2, siblingsChoice);
                        if (this.cpdag.isAdjacentTo(adj.get(0), adj.get(1))) continue CHOICE;
                    }
                }

                if (!siblingsChoice.isEmpty()) {
                    for (Node p : parents) {
                        for (Node s : siblingsChoice) {
                            if (this.cpdag.isAdjacentTo(p, s)) continue CHOICE;
                        }
                    }
                }

                Set<Node> _regressors = new HashSet<>();
                _regressors.add(x);
                _regressors.addAll(parents);
                _regressors.addAll(siblingsChoice);
                List<Node> regressors = new ArrayList<>(_regressors);

                double beta;

                if (regressors.contains(y)) {
                    beta = 0.0;
                } else {
                    beta = getBeta(regressors, x, y);
                }

                totalEffects.add(beta);
            } catch (Exception e) {
                TetradLogger.getInstance().forceLogMessage(e.getMessage());
            }
        }

        Collections.sort(totalEffects);
        return totalEffects;
    }

    /**
     * This method calculates the absolute total effects of node x on node y.
     *
     * @param x The node for which the total effects are calculated.
     * @param y The node whose total effects are obtained.
     * @return A LinkedList of Double values representing the absolute total effects of node x on node y. The LinkedList
     * is sorted in ascending order.
     */
    public LinkedList<Double> getAbsTotalEffects(Node x, Node y) {
        LinkedList<Double> totalEffects = getTotalEffects(x, y);
        LinkedList<Double> absTotalEffects = new LinkedList<>();
        for (double d : totalEffects) {
            absTotalEffects.add(Math.abs(d));
        }

        Collections.sort(absTotalEffects);
        return absTotalEffects;
    }

    /**
     * Returns a map from nodes in V \ {Y} to their minimum effects.
     *
     * @param y The child variable
     * @return Thia map.
     */
    public Map<Node, Double> calculateMinimumTotalEffectsOnY(Node y) {
        SortedMap<Node, Double> minEffects = new TreeMap<>();

        for (Node x : this.possibleCauses) {
            if (!(this.cpdag.containsNode(x) && this.cpdag.containsNode(y))) continue;
            LinkedList<Double> effects = getTotalEffects(x, y);
            minEffects.put(x, effects.getFirst());
        }

        return minEffects;
    }

    /**
     * Calculates the beta coefficient for a given set of regressors and a child node.
     * <p>
     * Note that x must be the first regressor.
     *
     * @param regressors The list of regressor nodes.
     * @param parent     The parent node for which the beta coefficient is calculated.
     * @param child      The child node for which the beta coefficient is calculated.
     * @return The beta coefficient for the parent->child regression.
     * @throws RuntimeException If a singularity is encountered during the regression process.
     */
    private double getBeta(List<Node> regressors, Node parent, Node child) {
        if (!regressors.contains(parent)) throw new IllegalArgumentException("The regressors must contain the parent node.");

        try {
            int xIndex = regressors.indexOf(parent);
            int yIndex = this.nodeIndices.get(child.getName());
            int[] xIndices = new int[regressors.size()];
            for (int i = 0; i < regressors.size(); i++) xIndices[i] = this.nodeIndices.get(regressors.get(i).getName());

            Matrix rX = this.allCovariances.getSelection(xIndices, xIndices);
            Matrix rY = this.allCovariances.getSelection(xIndices, new int[]{yIndex});
            Matrix bStar = null;

            try {
                bStar = rX.inverse().times(rY);
            } catch (SingularMatrixException e) {
                System.out.println("Singularity encountered when regressing " +
                                   LogUtilsSearch.getScoreFact(child, regressors));
            }

            return bStar != null ? bStar.get(xIndex, 0) : 0.0;
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when regressing " +
                                       LogUtilsSearch.getScoreFact(child, regressors));
        }
    }

    /**
     * Gives a list of nodes (parents or children) and corresponding minimum effects for the IDA algorithm.
     *
     * @author josephramsey
     */
    public static class NodeEffects {
        /**
         * The nodes.
         */
        private List<Node> nodes;
        /**
         * The effects.
         */
        private LinkedList<Double> effects;

        /**
         * Constructor.
         *
         * @param nodes   The nodes.
         * @param effects The effects.
         */
        NodeEffects(List<Node> nodes, LinkedList<Double> effects) {
            this.setNodes(nodes);
            this.setEffects(effects);
        }

        /**
         * Returns the nodes.
         *
         * @return The nodes.
         */
        public List<Node> getNodes() {
            return this.nodes;
        }

        /**
         * Sets the nodes.
         *
         * @param nodes The nodes.
         */
        public void setNodes(List<Node> nodes) {
            this.nodes = nodes;
        }

        /**
         * Returns the effects.
         *
         * @return The effects.
         */
        public LinkedList<Double> getEffects() {
            return this.effects;
        }

        /**
         * Sets the effects.
         *
         * @param effects The effects.
         */
        public void setEffects(LinkedList<Double> effects) {
            this.effects = effects;
        }

        /**
         * Returns a string representation of this object.
         *
         * @return A string representation of this object.
         */
        public String toString() {
            StringBuilder b = new StringBuilder();

            for (int i = 0; i < this.nodes.size(); i++) {
                b.append(this.nodes.get(i)).append("=").append(this.effects.get(i)).append(" ");
            }

            return b.toString();
        }
    }
}
