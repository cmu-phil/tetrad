package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import java.util.List;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class DefiniteDirectedPathPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "DDPP";
    }

    @Override
    public String getDescription() {
        return "Proportion of DP(X, Y) in est for which DD(X, Y) in CPDAG(true)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        int tp = 0, fp = 0;

        List<Node> nodes = trueGraph.getNodes();
        Graph cpdag = GraphSearchUtils.cpdagForDag(trueGraph);

        GraphUtils.addPagColoring(estGraph);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                Edge e = estGraph.getEdge(x, y);

                if (e != null && e.pointsTowards(y) && e.getProperties().contains(Edge.Property.dd)) {

//                if (estGraph.existsDirectedPathFromTo(x, y)) {
                    if (cpdag.paths().existsDirectedPathFromTo(x, y)) {
                        tp++;
                    } else {
                        fp++;
                    }
//                }
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
