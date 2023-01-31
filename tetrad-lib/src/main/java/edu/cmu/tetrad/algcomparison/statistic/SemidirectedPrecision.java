package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class SemidirectedPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "Semidirected-Prec";
    }

    @Override
    public String getDescription() {
        return "Proportion of (X, Y) where if semidirected path in est then also in true";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fp = 0;

        List<Node> nodes = estGraph.getNodes();

        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.getPaths().existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                    if (trueGraph.getPaths().existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                        tp++;
                    } else {
                        fp++;
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
