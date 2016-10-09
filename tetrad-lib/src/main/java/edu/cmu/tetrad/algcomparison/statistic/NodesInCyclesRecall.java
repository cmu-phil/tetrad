package edu.cmu.tetrad.algcomparison.statistic;

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
public class NodesInCyclesRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "NICR";
    }

    @Override
    public String getDescription() {
        return "Node in cycle recall";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        trueGraph = GraphUtils.replaceNodes(trueGraph, estGraph.getNodes());

        Set<Node> inTrue = getNodesInCycles(trueGraph);
        Set<Node> inEst = getNodesInCycles(estGraph);

        Set<Node> tp = new HashSet<>(inTrue);
        tp.retainAll(inEst);

        Set<Node> fn = new HashSet<>(inTrue);
        fn.removeAll(inEst);

        return tp.size() / (double) (tp.size() + fn.size());
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
