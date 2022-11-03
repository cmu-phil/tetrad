package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class SemidirectedRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "semi(X,Y)-Rec-CPDAG";
    }

    @Override
    public String getDescription() {
        return "Proportion of exists semi(X, Y) in true CPDAG for which exists semi(X, Y) in est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fn = 0;

        Graph cpdag = SearchGraphUtils.cpdagForDag(trueGraph);

        List<Node> nodes = estGraph.getNodes();

        nodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (cpdag.existsSemiDirectedPathFromTo(x, y)) {
                    if (estGraph.existsSemiDirectedPathFromTo(x, y)) {
                        tp++;
                    } else {
                        fn++;
//                        System.out.println("Found semidirected path semi(" + x + "," + y + ") in CPDAG but not est PAG");
                    }
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
