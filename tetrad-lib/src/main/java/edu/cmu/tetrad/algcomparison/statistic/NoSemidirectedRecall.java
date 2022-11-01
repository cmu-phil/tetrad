package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Collections;
import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author jdramsey
 */
public class NoSemidirectedRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "!semi(X,Y)-Rec";
    }

    @Override
    public String getDescription() {
        return "Proportion of not exists semi(X, Y) in true cpdag for which not exists semi(X, Y) in est";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fn = 0;

        Graph cpdag = SearchGraphUtils.cpdagForDag(trueGraph);

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (!cpdag.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                    if (!estGraph.existsSemiDirectedPathFromTo(x, Collections.singleton(y))) {
                        tp++;
                    } else {
                        fn++;
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
