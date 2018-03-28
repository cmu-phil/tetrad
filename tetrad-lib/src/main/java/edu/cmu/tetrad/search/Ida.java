package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.min;

/**
 * Implements the IDA algorithm, Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann.
 * "Estimating high-dimensional intervention effects from observational data."
 * The Annals of Statistics 37.6A (2009): 3133-3164.
 *
 * @author jdramsey@andrew.cmu.edu
 */
public class Ida {
    private DataSet dataSet;
    private final Graph pattern;
    private final List<Node> possibleCauses;
    private Map<String, Integer> nodeIndices;
    private ICovarianceMatrix allCovariances;

    public Ida(DataSet dataSet, Graph pattern, List<Node> possibleCauses) {
        this.dataSet = DataUtils.convertNumericalDiscreteToContinuous(dataSet);
        this.pattern = pattern;
        possibleCauses = GraphUtils.replaceNodes(possibleCauses, dataSet.getVariables());
        this.possibleCauses = possibleCauses;

        allCovariances = new CovarianceMatrixOnTheFly(this.dataSet);

        nodeIndices = new HashMap<>();

        for (int i = 0; i < pattern.getNodes().size(); i++) {
            nodeIndices.put(pattern.getNodes().get(i).getName(), i);
        }
    }

    /**
     * Returns the minimum effects of X on Y for X in V \ {Y}, sorted downward by minimum effect
     *
     * @param y The child variable.
     * @return Two sorted lists, one of nodes, the other of corresponding minimum effects, sorted downward by
     * minimum effect size.
     */
    public NodeEffects getSortedMinEffects(Node y) {
        Map<Node, Double> allEffects = calculateMinimumEffectsOnY(y);

        List<Node> nodes = new ArrayList<>(allEffects.keySet());
        Collections.shuffle(nodes);

        nodes.sort((o1, o2) -> Double.compare(abs(allEffects.get(o2)), abs(allEffects.get(o1))));

        LinkedList<Double> effects = new LinkedList<>();

        for (Node node : nodes) {
            effects.add(allEffects.get(node));
        }

        return new NodeEffects(nodes, effects);
    }

    /**
     * A list of nodes and corresponding minimum effects.
     *
     * @author jdramsey@andrew.cmu.edu
     */
    public class NodeEffects {
        private List<Node> nodes;
        private LinkedList<Double> effects;

        NodeEffects(List<Node> nodes, LinkedList<Double> effects) {
            this.setNodes(nodes);
            this.setEffects(effects);
        }

        public List<Node> getNodes() {
            return nodes;
        }

        public void setNodes(List<Node> nodes) {
            this.nodes = nodes;
        }

        public LinkedList<Double> getEffects() {
            return effects;
        }

        public void setEffects(LinkedList<Double> effects) {
            this.effects = effects;
        }

        public String toString() {
            StringBuilder b = new StringBuilder();

            for (int i = 0; i < nodes.size(); i++) {
                b.append(nodes.get(i)).append("=").append(effects.get(i)).append(" ");
            }

            return b.toString();
        }
    }

    public double trueEffect(Node x, Node y, Graph trueDag) {
        if (x == y) throw new IllegalArgumentException("x == y");

        if (!trueDag.isAncestorOf(x, y)) return 0.0;

        trueDag = GraphUtils.replaceNodes(trueDag, dataSet.getVariables());

        if (trueDag == null) {
            throw new NullPointerException("True graph is null.");
        }

        List<Node> regressors = new ArrayList<>();
        regressors.add(x);
        regressors.addAll(trueDag.getParents(x));

        return abs(getBeta(regressors, y));
    }

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
                final double m1 = abs(trueEffect - min);
                final double m2 = abs(trueEffect - max);
                return min(m1, m2);
            }
        }
    }

    /**
     * Returns a list of the possible effects of X on Y (with different possible parents from the pattern),
     * sorted low to high in absolute value.
     * <p>
     * 1. First, estimate a pattern P from the data.
     * 2. Then, consider all combinations C of adjacents of X that include all fo the parents of X in P.
     * 3. For each such C, regress Y onto {X} U C and record the coefficient beta for X in the regression.
     * 4. Report the list of such betas, sorted low to high.
     *
     * @param x The first variable.
     * @param y The second variable
     * @return a list of the possible effects of X on Y.
     */
    private LinkedList<Double> getEffects(Node x, Node y) {
        List<Node> parents = pattern.getParents(x);
        List<Node> children = pattern.getChildren(x);

        List<Node> siblings = pattern.getAdjacentNodes(x);
        siblings.removeAll(parents);
        siblings.removeAll(children);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(siblings.size(), siblings.size());
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
                        if (!pattern.isAdjacentTo(adj.get(0), adj.get(1))) continue CHOICE;
                    }
                }

                if (!sibbled.isEmpty()) {
                    for (Node p : parents) {
                        for (Node s : sibbled) {
                            if (!pattern.isAdjacentTo(p, s)) continue CHOICE;
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

        for (Node x : possibleCauses) {
            final LinkedList<Double> effects = getEffects(x, y);
            minEffects.put(x, effects.getFirst());
        }

        return minEffects;
    }

    // x must be the first regressor.
    private double getBeta(List<Node> regressors, Node child) {
        try {
            int yIndex = nodeIndices.get(child.getName());
            int[] xIndices = new int[regressors.size()];
            for (int i = 0; i < regressors.size(); i++) xIndices[i] = nodeIndices.get(regressors.get(i).getName());

            TetradMatrix rX = allCovariances.getSelection(xIndices, xIndices);
            TetradMatrix rY = allCovariances.getSelection(xIndices, new int[]{yIndex});

            TetradMatrix bStar = rX.inverse().times(rY);

            return bStar.get(0, 0);
        } catch (SingularMatrixException e) {
            return 0.0;
        }
    }
}