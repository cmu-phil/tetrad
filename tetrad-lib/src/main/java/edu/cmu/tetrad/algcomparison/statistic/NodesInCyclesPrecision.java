package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class NodesInCyclesPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NICP";
    }

    @Override
    public String getDescription() {
        return "Node in cycle precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        trueGraph = GraphUtils.replaceNodes(trueGraph, estGraph.getNodes());

        Set<Node> inTrue = getNodesInCycles(trueGraph);
        Set<Node> inEst = getNodesInCycles(estGraph);

        Set<Node> tp = new HashSet<>(inTrue);
        tp.retainAll(inEst);

        Set<Node> fp = new HashSet<>(inEst);
        fp.removeAll(inTrue);

        return tp.size() / (double) (tp.size() + fp.size());
    }

    private Set<Node> getNodesInCycles(Graph graph) {
        Set<Node> inCycle = new HashSet<>();

        for (Node x : graph.getNodes()) {
            if (GraphUtils.existsDirectedPathFromToBreathFirst(x, x, graph)) {
                inCycle.add(x);
            }
        }

        return inCycle;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
