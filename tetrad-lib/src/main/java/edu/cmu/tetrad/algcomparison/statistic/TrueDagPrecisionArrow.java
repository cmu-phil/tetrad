package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.Collections;
import java.util.List;

/**
 * @author jdramsey
 */
public class TrueDagPrecisionArrow implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "*->-Prec";
    }

    @Override
    public String getDescription() {
        return "Proportion of X*->Y in the estimated graph for which there is no path Y~~>X in the true graph";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                Edge e = estGraph.getEdge(x, y);

                if (Edges.directedEdge(x, y).equals(e)) {
                    if (!trueGraph.isAncestorOf(y, x)) {
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
