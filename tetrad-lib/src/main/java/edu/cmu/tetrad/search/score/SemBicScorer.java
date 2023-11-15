package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores an entire DAG using the SemBicScore.
 *
 * @author josephramsey
 * @see SemBicScore
 */
public class SemBicScorer {

    /**
     * Scores the given DAG using the given data model, usimg a BIC score.
     *
     * @param dag  The DAG.
     * @param data a continuous dataset or a covariance matrix.
     * @return The BIC score of the DAG.
     */
    public static double scoreDag(Graph dag, DataModel data, boolean precomputeCovariances) {
        return scoreDag(dag, data, 1.0, precomputeCovariances);
    }

    /**
     * Scores the given DAG using the given data model, usimg a BIC score.
     *
     * @param dag             The DAG.
     * @param data            a continuous dataset or a covariance matrix.
     * @param penaltyDiscount The penalty discount.
     * @return The BIC score of the DAG.
     */
    public static double scoreDag(Graph dag, DataModel data, double penaltyDiscount, boolean precomputeCovariances) {
        if (dag == null) throw new NullPointerException("DAG not specified.");

        SemBicScore score;

        if (data instanceof ICovarianceMatrix) {
            score = new SemBicScore((ICovarianceMatrix) data);
        } else if (data instanceof DataSet) {
            score = new SemBicScore((DataSet) data, precomputeCovariances);
        } else {
            throw new IllegalArgumentException("Expecting a covariance matrix of a dataset.");
        }

        score.setPenaltyDiscount(penaltyDiscount);

        dag = GraphUtils.replaceNodes(dag, data.getVariables());

        Map<Node, Integer> hashIndices = SemBicScorer.buildIndexing(dag.getNodes());

        double _score = 0.0;

        for (Node node : dag.getNodes()) {
            List<Node> x = dag.getParents(node);

            int[] parentIndices = new int[x.size()];

            int count = 0;
            for (Node parent : x) {
                parentIndices[count++] = hashIndices.get(parent);
            }

            double score1 = score.localScore(hashIndices.get(node), parentIndices);
            if (!Double.isNaN(score1)) {
                _score += score1;
            }
        }

        return _score;
    }

    // Maps adj to their indices for quick lookup.
    private static Map<Node, Integer> buildIndexing(List<Node> nodes) {
        Map<Node, Integer> hashIndices = new HashMap<>();

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }

        return hashIndices;
    }
}
