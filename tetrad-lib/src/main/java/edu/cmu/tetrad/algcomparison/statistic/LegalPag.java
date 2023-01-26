package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Legal PAG
 *
 * @author jdramsey
 */
public class LegalPag implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LegalPAG";
    }

    @Override
    public String getDescription() {
        return "Legal PAG";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> estNodes = estGraph.getNodes();

        estNodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        Graph pag = new EdgeListGraph(estNodes);

        for (Edge edge : estGraph.getEdges()) {
            pag.addEdge(edge);
        }

        SearchGraphUtils.LegalPagRet legalPag = SearchGraphUtils.isLegalPag(pag);
        System.out.println(legalPag.getReason());
        if (legalPag.isLegalPag()) {
            return 1.0;
        }
        else {
            return 0.0;
        }
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
