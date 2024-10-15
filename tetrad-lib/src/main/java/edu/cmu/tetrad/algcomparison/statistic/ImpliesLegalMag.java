package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import java.io.Serial;
import java.util.HashSet;
import java.util.List;

/**
 * Implies Legal MAG
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ImpliesLegalMag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public ImpliesLegalMag() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "ImpliesMag";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph implies a legal MAG, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> latent = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.LATENT).toList();

        List<Node> measured = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.MEASURED).toList();

        List<Node> selection = trueGraph.getNodes().stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);
        GraphSearchUtils.LegalMagRet legalPag = GraphSearchUtils.isLegalMag(estGraph, new HashSet<>(selection));

        if (legalPag.isLegalMag()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
