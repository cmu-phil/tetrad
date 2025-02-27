package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.CommonAncestorTruePositiveBidirected.existsCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CommonAncestorFalseNegativeBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public CommonAncestorFalseNegativeBidirected() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "CAFNB";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Common Ancestor False Negative Bidirected";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);

        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (x.getNodeType() == NodeType.MEASURED && y.getNodeType() == NodeType.MEASURED) {
                    if (existsCommonAncestor(trueGraph, new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE))) {
                        Edge edge2 = estGraph.getEdge(x, y);

                        if (edge2 == null) continue;

                        if (!(edge2 != null && Edges.isBidirectedEdge(edge2)
                              && existsCommonAncestor(trueGraph, edge2))) {
                            fn++;
                        }
                    }
                }
            }
        }

        return fn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
