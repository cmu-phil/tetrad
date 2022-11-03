package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.List;

/**
 * @author jdramsey
 */
public class ProportionSemidirectedPathsNotReversedEst implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "semi(X,Y,est)==>!semi(Y,X,true)";
    }

    @Override
    public String getDescription() {
        return "Proportion of semi(X, Y) in estimated graph for which there is no semi(Y, X) in true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = estGraph.getNodes();
        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        int tp = 0;
        int fp = 0;

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.existsSemiDirectedPathFromTo(x, y)) {
                    if (!trueGraph.existsSemiDirectedPathFromTo(y, x)) {
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
