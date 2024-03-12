package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NodesInCyclesPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NodesInCyclesPrecision() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NICP";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Node in cycle precision";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
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
            if (graph.paths().existsDirectedPathFromTo(x, x)) {
                inCycle.add(x);
            }
        }

        return inCycle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
