package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.io.Serial;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumCorrectVisibleAncestors implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumCorrectVisibleAncestors() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#CVA";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number visible X-->Y where X~~>Y in true";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.addEdgeSpecializationMarkup(estGraph);

        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                Node x = Edges.getDirectedEdgeTail(edge);
                Node y = Edges.getDirectedEdgeHead(edge);

                if (/*!existsCommonAncestor(trueGraph, edge) &&*/ trueGraph.paths().isAncestorOf(x, y)) {
                    tp++;

//                    System.out.println("Correct visible edge: " + edge);
                } else {
                    fp++;

//                    System.out.println("Incorrect visible edge: " + edge + " x = " + x + " y = " + y);
//                    System.out.println("\t ancestor = " + trueGraph.isAncestorOf(x, y));
//                    System.out.println("\t no common ancestor = " + !existsCommonAncestor(trueGraph, edge));

                }
            }
        }

        return tp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
