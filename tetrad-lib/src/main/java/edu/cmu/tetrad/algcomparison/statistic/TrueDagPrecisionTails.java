package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class TrueDagPrecisionTails implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "-->-Prec";
    }

    @Override
    public String getDescription() {
        return "Proportion of X-->Y in estimated for which there is a path X~~>Y in true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes){
            for (Node y : nodes) {
                if (x == y) continue;

                Edge edge = estGraph.getEdge(x, y);

                if (edge == null) continue;

                if (Edges.directedEdge(x, y).equals(edge)) {
                    if (trueGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fp++;
                    }

                    if (trueGraph.paths().isAncestorOf(y, x)) {
                        System.out.println("Should be " + y + "~~>" + x + ": " + estGraph.getEdge(x, y));
                    } else {
                        System.out.println("Should be " + x + "o~~>" + y + ": " + estGraph.getEdge(x, y));
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
