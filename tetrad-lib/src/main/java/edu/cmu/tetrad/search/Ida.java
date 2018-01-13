package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.DepthChoiceGenerator;

import java.util.*;

import static java.lang.Math.abs;

/**
 * Implements the IDA algorithm, Maathuis, Marloes H., Markus Kalisch, and Peter Bühlmann.
 * "Estimating high-dimensional intervention effects from observational data."
 * The Annals of Statistics 37.6A (2009): 3133-3164.
 *
 * @author jdramsey@andrew.cmu.edu
 */
public class Ida {
    private ICovarianceMatrix covariances;
    private final Graph pattern;
    private final RegressionCovariance regression;

    public Ida(ICovarianceMatrix covariances) {
        this.covariances = covariances;
        Fges fges = new Fges(new SemBicScore(covariances));
        this.pattern = fges.search();
        regression = new RegressionCovariance(covariances);
    }

    /**
     * Returns a list of the possible effects of X on Y (with different possible parents from the pattern),
     * sorted low to high in absolute value.
     * <p>
     * 1. First, estimate a pattern P from the data.
     * 2. Then, consider all combinations C of adjacents of X that include all fo the parents of X in P.
     * 3. For each such C, regress Y onto {X} U C and record the coefficient beta for X in the regression.
     * 4. Report the minimum such beta.
     *
     * @param x The first variable.
     * @param y The second variable
     * @return a list of the possible effects of X on Y.
     */
    public List<Double> getEffects(Node x, Node y) {
        if (x == y) return new ArrayList<>();

        List<Node> parents = pattern.getParents(x);

        List<Node> siblings = pattern.getAdjacentNodes(x);
        siblings.removeAll(parents);
        siblings.remove(x);

        DepthChoiceGenerator gen = new DepthChoiceGenerator(siblings.size(), siblings.size());
        int[] choice;

        List<Double> effects = new ArrayList<>();

        while ((choice = gen.next()) != null) {
            List<Node> sibbled = GraphUtils.asList(choice, siblings);
            sibbled.remove(y);

            List<Node> regressors = new ArrayList<>();
            regressors.add(x);
            regressors.addAll(sibbled);

            RegressionResult result = regression.regress(y, regressors);

            double beta;

            if (regressors.contains(y)) {
                beta = 0.0;
            } else {
                beta = result.isZeroInterceptAssumed() ? result.getCoef()[0] : result.getCoef()[1];
            }

            effects.add(beta);
        }

        effects.sort(Comparator.comparingDouble(Math::abs));

        return effects;
    }

    /**
     * Returns a map from nodes in V \ {Y} to their minimum effects.
     *
     * @param y The target variable
     * @return Thia map.
     */
    public Map<Node, Double> calculateMinimumEffectsOnY(Node y) {
        Map<Node, Double> minEffects = new HashMap<>();

        for (Node x : covariances.getVariables()) {
            final List<Double> effects = getEffects(x, y);

            if (!effects.isEmpty()) {
                minEffects.put(x, effects.get(0));
            }
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
    public NodeEffects getSortedEffects(Node y) {
        Map<Node, Double> allEffects = calculateMinimumEffectsOnY(y);

        List<Node> nodes = new ArrayList<>(allEffects.keySet());

        nodes.sort((o1, o2) -> {
            final Double d1 = allEffects.get(o1);
            final Double d2 = allEffects.get(o2);
            return -Double.compare(abs(d1), abs(d2));
        });

        List<Double> effects = new ArrayList<>();

        for (Node node : nodes) {
            effects.add(allEffects.get(node));
        }

        return new NodeEffects(nodes, effects);
    }

    /**
     * A list of nodes and corresonding minimum effects.
     *
     * @author jdramsey@andrew.cmu.edu
     */
    public class NodeEffects {
        private List<Node> nodes;
        private List<Double> effects;

        public NodeEffects(List<Node> nodes, List<Double> effects) {
            this.setNodes(nodes);
            this.setEffects(effects);
        }

        public List<Node> getNodes() {
            return nodes;
        }

        public void setNodes(List<Node> nodes) {
            this.nodes = nodes;
        }

        public List<Double> getEffects() {
            return effects;
        }

        public void setEffects(List<Double> effects) {
            this.effects = effects;
        }
    }

    public double trueEffect(Node x, Node y, Graph trueDag) {
        trueDag = GraphUtils.replaceNodes(trueDag, covariances.getVariables());

        List<Node> regressors = new ArrayList<>();
        regressors.addAll(trueDag.getParents(x));
        if (!regressors.contains(x)) regressors.add(x);

        if (regressors.contains(y)) return 0.0;

        RegressionResult result = regression.regress(y, regressors);
        return result.isZeroInterceptAssumed() ? result.getCoef()[0] : result.getCoef()[1];
    }
}
