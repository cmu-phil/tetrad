package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.List;

/**
 * Proportion of semi(X, Y) in estimated graph for which there is no semi(Y, X) in true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ProportionSemidirectedPathsNotReversedEst implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "semi(X,Y,est)==>!semi(Y,X,true)";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Proportion of semi(X, Y) in estimated graph for which there is no semi(Y, X) in true graph";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = estGraph.getNodes();
        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        int tp = 0;
        int fp = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.paths().existsSemiDirectedPath(x, y)) {
                    if (!trueGraph.paths().existsSemiDirectedPath(y, x)) {
                        tp++;
                    } else {
                        fp++;
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
