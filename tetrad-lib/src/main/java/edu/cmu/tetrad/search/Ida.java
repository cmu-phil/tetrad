package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.Math.*;
import static java.util.Collections.singleton;

/**
 * Implements the IDA algorithm, Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann.
 * "Estimating high-dimensional intervention effects from observational data."
 * The Annals of Statistics 37.6A (2009): 3133-3164.
 *
 * @author jdramsey@andrew.cmu.edu
 */
public class Ida {
    private DataSet dataSet;
    private final double[][] data;
    private final Graph pattern;
    private Map<String, Integer> nodeIndices;

    public Ida(DataSet dataSet, Graph pattern) {
        this.dataSet = dataSet;
        this.data = dataSet.getDoubleData().transpose().toArray();
        this.pattern = pattern;

        nodeIndices = new HashMap<>();

        for (int i = 0; i < pattern.getNodes().size(); i++) {
            nodeIndices.put(pattern.getNodes().get(i).getName(), i);
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
    public LinkedList<Double> getEffects(Node x, Node y) {
        if (x == y) {
            throw new IllegalArgumentException("x == y");
        }

        List<Node> parents = pattern.getParents(x);
        List<Node> children = pattern.getChildren(x);

        List<Node> siblings = pattern.getAdjacentNodes(x);
        siblings.removeAll(parents);
        siblings.removeAll(children);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(siblings.size(), siblings.size());
        int[] choice;

        LinkedList<Double> effects = new LinkedList<>();

        while ((choice = gen.next()) != null) {
            List<Node> sibbled = GraphUtils.asList(choice, siblings);

            List<Node> regressors = new ArrayList<>();
            regressors.add(x);
            for (Node n : parents) if (!regressors.contains(n)) regressors.add(n);
            for (Node n : sibbled) if (!regressors.contains(n)) regressors.add(n);
            regressors.remove(y); // These are ignored anyway.

            double beta;

//            if (regressors.contains(y)) {// ||*/ !pattern.existsSemiDirectedPathFromTo(x, singleton(y))) {
//                beta = 0;
//            } else {
            beta = getBeta(regressors, y);
//            }

            effects.add(abs(beta));
        }

        Collections.sort(effects);

        return effects;
    }

    /**
     * Returns a map from nodes in V \ {Y} to their minimum effects.
     *
     * @param y The target variable
     * @return Thia map.
     */
    private Map<Node, Double> calculateMinimumEffectsOnY(Node y) {
        SortedMap<Node, Double> minEffects = new TreeMap<>();

        for (Node x : pattern.getNodes()) {
            if (x == y) continue;
            final LinkedList<Double> effects = getEffects(x, y);
            minEffects.put(x, effects.getFirst());
        }

        return minEffects;
    }

    /**
     * Returns the minimum effects of X on Y for X in V \ {Y}, sorted downward by minimum effect
     *
     * @param y The target variable.
     * @return Two sorted lists, one of nodes, the other of corresponding minimum effects, sorted downward by
     * minimum effect size.
     */
    public NodeEffects getSortedMinEffects(Node y) {
        Map<Node, Double> allEffects = calculateMinimumEffectsOnY(y);

        List<Node> nodes = new ArrayList<>(allEffects.keySet());

        nodes.sort((o1, o2) -> {
            final Double d1 = allEffects.get(o1);
            final Double d2 = allEffects.get(o2);
            return Double.compare(abs(d2), abs(d1));
        });

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

        public NodeEffects(List<Node> nodes, LinkedList<Double> effects) {
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
    }

    public double trueEffect(Node x, Node y, Graph trueDag) {
        if (x == y) throw new IllegalArgumentException("x == y");

        if (!trueDag.isAncestorOf(x, y)) return 0.0;

        trueDag = GraphUtils.replaceNodes(trueDag, dataSet.getVariables());

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
//
//    // x must be the first regressor.
//    private double getBeta1(List<Node> regressors, Node target) {
//        int yIndex = nodeIndices.get(target);
//        int[] xIndices = new int[regressors.size()];
//
//        for (int i = 0; i < regressors.size(); i++) {
//            xIndices[i] = nodeIndices.get(regressors.get(i));
//        }
//
//        TetradMatrix rX = covariances.getSelection(xIndices, xIndices);
//        TetradMatrix rY = covariances.getSelection(xIndices, new int[]{yIndex});
//
//        return rX.inverse().times(rY).get(0, 0);
//    }

    // x must be the first regressor.
    private double getBeta(List<Node> regressors, Node target) {
        int yIndex = nodeIndices.get(target.getName());
        int[] xIndices = new int[regressors.size()];
        for (int i = 0; i < regressors.size(); i++) xIndices[i] = nodeIndices.get(regressors.get(i).getName());

        double[] _target = data[yIndex];
        double[][] _regressors = new double[xIndices.length + 1][];
        for (int i = 0; i < xIndices.length; i++) _regressors[i] = data[xIndices[i]];

        for (int i = 0; i < regressors.size(); i++) {
            _regressors[i] = data[xIndices[i]];
        }

        double[] interceptCol = new double[data[0].length];
        Arrays.fill(interceptCol, 1.0);
        _regressors[regressors.size()] = interceptCol;

        TetradMatrix y = new TetradMatrix(new double[][]{_target}).transpose();
        TetradMatrix x = new TetradMatrix(_regressors).transpose();

        TetradMatrix xT = x.transpose();
        TetradMatrix xTx = xT.times(x);
        TetradMatrix xTxInv = xTx.inverse();
        TetradMatrix xTy = xT.times(y);
        TetradMatrix b = xTxInv.times(xTy);

        return b.get(0, 0);

    }
}
