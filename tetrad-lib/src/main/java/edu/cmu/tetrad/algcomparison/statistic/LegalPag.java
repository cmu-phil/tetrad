package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;

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
        return "1 if the estimated graph passes the Legal PAG check, 0 of not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        List<Node> estNodes = estGraph.getNodes();
//
//        estNodes.removeIf(node -> node.getNodeType() == NodeType.LATENT);
//
//        Graph pag = SearchGraphUtils.dagToPag(estGraph);
//
////        Graph pag = new EdgeListGraph(estNodes);
////
////        for (Edge edge : estGraph.getEdges()) {
////            pag.addEdge(edge);
////        }

        SearchGraphUtils.LegalPagRet legalPag = SearchGraphUtils.isLegalPag(estGraph);
        System.out.println(legalPag.getReason());

//        if (legalPag.isLegalPag() != (estGraph.getGraphType() == EdgeListGraph.GraphType.PAG)) {
//            throw new IllegalArgumentException("Wasn't correctly labeled as a PAG");
//        }

        if (legalPag.isLegalPag()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
