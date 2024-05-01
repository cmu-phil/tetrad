package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.io.Serial;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BidirectedLatentPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public BidirectedLatentPrecision() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "<->-Lat-Prec";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Percent of bidirected edges for which a latent confounder exists";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                List<List<Node>> treks = trueGraph.paths().treks(x, y, -1);
                boolean existsLatentConfounder = false;

                for (List<Node> trek : treks) {
                    if (GraphUtils.isConfoundingTrek(trueGraph, trek, x, y)) {
                        existsLatentConfounder = true;
                        System.out.println(GraphUtils.pathString(trueGraph, trek));
                    }
                }

                if (existsLatentConfounder) {
                    tp++;
                } else {
                    fp++;
                }
            }
        }

        return tp / (double) (tp + fp);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
