package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.HashSet;
import java.util.List;

/**
 * MaximalMag statistic.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MaximalityCondition implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public MaximalityCondition() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "MaximalMag";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the maximality condition passes in the MAG, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = estGraph;

        List<Node> latent = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.LATENT).toList();

        List<Node> measured = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.MEASURED).toList();

        List<Node> selection = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);

        List<Node> nodes = pag.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) {
                    if (mag.paths().existsInducingPath(x, y, new HashSet<>(selection))) {
                        return 0.0;
                    }
                }
            }
        }

        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
