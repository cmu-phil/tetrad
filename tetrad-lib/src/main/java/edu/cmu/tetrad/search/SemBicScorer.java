package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemBicScorer {
    public static double scoreDag(Graph dag, final DataModel data) {
        if (dag == null) throw new NullPointerException("DAG not specified.");

        final Score score;

        if (data instanceof ICovarianceMatrix) {
            score = new SemBicScore((ICovarianceMatrix) dag);
        } else if (data instanceof DataSet) {
            score = new SemBicScore((DataSet) data);
        } else {
            throw new IllegalArgumentException("Expecting a covariance matrix of a dataset.");
        }

        dag = GraphUtils.replaceNodes(dag, data.getVariables());

        if (dag == null) {
            throw new NullPointerException("Dag was not specified.");
        }

        final Map<Node, Integer> hashIndices = SemBicScorer.buildIndexing(dag.getNodes());

        double _score = 0.0;

        for (final Node node : dag.getNodes()) {
            final List<Node> x = dag.getParents(node);

            final int[] parentIndices = new int[x.size()];

            int count = 0;
            for (final Node parent : x) {
                parentIndices[count++] = hashIndices.get(parent);
            }

            _score += score.localScore(hashIndices.get(node), parentIndices);
        }

        return _score;
    }

    // Maps adj to their indices for quick lookup.
    private static Map<Node, Integer> buildIndexing(final List<Node> nodes) {
        final Map<Node, Integer> hashIndices = new HashMap<>();

        int i = -1;

        for (final Node n : nodes) {
            hashIndices.put(n, ++i);
        }

        return hashIndices;
    }
}
