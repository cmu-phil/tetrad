package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LatentCommonAncestorFalseNegativeBidirected implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "LCAFNB";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Latent Common Ancestor False Negative Bidirected";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);

        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (x.getNodeType() == NodeType.MEASURED && y.getNodeType() == NodeType.MEASURED) {
                    if (existsLatentCommonAncestor(trueGraph, new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE))) {
                        Edge edge2 = estGraph.getEdge(x, y);

                        if (edge2 == null) continue;

                        if (!(edge2 != null && Edges.isBidirectedEdge(edge2)
                                && existsLatentCommonAncestor(trueGraph, edge2))) {
                            fn++;
                        }
                    }
                }
            }
        }

        return fn;
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
