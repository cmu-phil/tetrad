package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.SublistGenerator;
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
 * lower-bounded effects on Y.
 *
 * @author josephramsey
 * @see Cstar
 * @see NodeEffects
 */
public class Ida {
    // The dataset being searched over.
    private final DataSet dataSet;
    // The CPDAG (found, e.g., by running PC, or some other CPDAG producing algorithm.
    private final Graph cpdag;
    // The possible causes to be considered.
    private final List<Node> possibleCauses;
    // A map from node names to indices in the covariance matrix.
    private final Map<String, Integer> nodeIndices;
    // The covariance matrix for the dataset.
    private final ICovarianceMatrix allCovariances;

    /**
     * Constructor.
     *
     * @param dataSet        The dataset being searched over.
     * @param cpdag          The CPDAG (found, e.g., by running PC, or some other CPDAG producing algorithm.
     * @param possibleCauses The possible causes to be considered.
     */
    public Ida(DataSet dataSet, Graph cpdag, List<Node> possibleCauses) {
        this.dataSet = DataTransforms.convertNumericalDiscreteToContinuous(dataSet);
        this.cpdag = cpdag;
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        this.possibleCauses = possibleCauses;

        this.allCovariances = new CovarianceMatrix(this.dataSet);

        this.nodeIndices = new HashMap<>();

        for (int i = 0; i < cpdag.getNodes().size(); i++) {
            this.nodeIndices.put(cpdag.getNodes().get(i).getName(), i);
        }
    }

    /**
     * Returns the minimum effects of X on Y for X in V \ {Y}, sorted downward by minimum effect
     *
     * @param y The child variable.
     * @return Two sorted lists, one of possible parents, the other of corresponding minimum effects, sorted downward by
     * minimum effect size.
     * @see Ida
     */
    public NodeEffects getSortedMinEffects(Node y) {
        Map<Node, Double> allEffects = calculateMinimumEffectsOnY(y);

        List<Node> nodes = new ArrayList<>(allEffects.keySet());
        RandomUtil.shuffle(nodes);

        nodes.sort((o1, o2) -> Double.compare(abs(allEffects.get(o2)), abs(allEffects.get(o1))));

        LinkedList<Double> effects = new LinkedList<>();

        for (Node node : nodes) {
            effects.add(allEffects.get(node));
        }

        return new NodeEffects(nodes, effects);
    }

    /**
     * Calculates the true effect of (x, y) given the true DAG (which must be provided).
     *
     * @param trueDag The true DAG.
     * @return The true effect of (x, y).
     */
    public double trueEffect(Node x, Node y, Graph trueDag) {
        if (x == y) throw new IllegalArgumentException("x == y");

        if (!trueDag.paths().isAncestorOf(x, y)) return 0.0;

        trueDag = GraphUtils.replaceNodes(trueDag, this.dataSet.getVariables());

        List<Node> regressors = new ArrayList<>();
        regressors.add(x);
        regressors.addAll(trueDag.getParents(x));

        return abs(getBeta(regressors, y));
    }

    /**
     * Returns the distance between the effects and the true effect.
     *
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
     * Returns a list of the possible effects of X on Y (with different possible parents from the pattern), sorted low
     * to high in absolute value.
     * <p>
     * 1. First, estimate a pattern P from the data. 2. Then, consider all combinations C of siblings Z of X (Z--X) that
     * include all the parents of X in P. 3. For each such C, regress Y onto {X} U C and record the coefficient beta for
     * X in the regression. 4. Report the list of such betas, sorted low to high.
     *
     * @param x The first variable.
     * @param y The second variable
     * @return a list of the possible effects of X on Y.
     */
    private LinkedList<Double> getEffects(Node x, Node y) {
        List<Node> parents = this.cpdag.getParents(x);
        List<Node> children = this.cpdag.getChildren(x);

        List<Node> siblings = new ArrayList<>(this.cpdag.getAdjacentNodes(x));
        siblings.removeAll(parents);
        siblings.removeAll(children);

        SublistGenerator gen = new SublistGenerator(siblings.size(), siblings.size());
        int[] choice;

        LinkedList<Double> effects = new LinkedList<>();

        CHOICE:
        while ((choice = gen.next()) != null) {
            try {
                List<Node> sibbled = GraphUtils.asList(choice, siblings);

                if (sibbled.size() > 1) {
                    ChoiceGenerator gen2 = new ChoiceGenerator(sibbled.size(), 2);
                    int[] choice2;

                    while ((choice2 = gen2.next()) != null) {
                        List<Node> adj = GraphUtils.asList(choice2, sibbled);
                        if (!this.cpdag.isAdjacentTo(adj.get(0), adj.get(1))) continue CHOICE;
                    }
                }

                if (!sibbled.isEmpty()) {
                    for (Node p : parents) {
                        for (Node s : sibbled) {
                            if (!this.cpdag.isAdjacentTo(p, s)) continue CHOICE;
                        }
                    }
                }

                List<Node> regressors = new ArrayList<>();
                regressors.add(x);
                for (Node n : parents) if (!regressors.contains(n)) regressors.add(n);
                for (Node n : sibbled) if (!regressors.contains(n)) regressors.add(n);

                if (regressors.contains(y)) {
                    effects.add(0.0);
                } else {
                    effects.add(abs(getBeta(regressors, y)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Collections.sort(effects);

        return effects;
    }

    /**
     * Returns a map from nodes in V \ {Y} to their minimum effects.
     *
     * @param y The child variable
     * @return Thia map.
     */
    public Map<Node, Double> calculateMinimumEffectsOnY(Node y) {
        SortedMap<Node, Double> minEffects = new TreeMap<>();

        for (Node x : this.possibleCauses) {
            if (!(this.cpdag.containsNode(x) && this.cpdag.containsNode(y))) continue;

            LinkedList<Double> effects = getEffects(x, y);
            minEffects.put(x, effects.getFirst());
        }

        return minEffects;
    }

    // x must be the first regressor.
    private double getBeta(List<Node> regressors, Node child) {
        try {
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

            return bStar != null ? bStar.get(0, 0) : 0.0;
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
        // The nodes.
        private List<Node> nodes;
        // The effects.
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