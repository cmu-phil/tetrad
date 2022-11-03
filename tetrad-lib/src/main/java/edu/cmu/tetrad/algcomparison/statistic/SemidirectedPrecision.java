package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.SearchGraphUtils;

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
        return "semi(X,Y)-Prec-CPDAG";
    }

    @Override
    public String getDescription() {
        return "Proportion of exists semi(X, Y) for which exists semi(X, Y) in true CPDAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fp = 0;

        Graph cpdag = SearchGraphUtils.cpdagForDag(trueGraph);

        List<Node> nodes = estGraph.getNodes();

        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                    if (cpdag.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                        tp++;
                    } else {
                        fp++;
//                        System.out.println("Found semidirected path semi(" + x + "," + y + ") in est PAG but not CPDAG");
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
