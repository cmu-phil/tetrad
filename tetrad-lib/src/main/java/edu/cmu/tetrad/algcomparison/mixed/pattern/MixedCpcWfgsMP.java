package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.List;
import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcWfgsMP implements Algorithm {
    public Graph search(DataSet ds, Map<String, Number> parameters) {
        WFgs fgs = new WFgs(ds);
        fgs.setDepth(parameters.get("fgsDepth").intValue());
        fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Graph g =  fgs.search();

//        List<Node> nodes = g.getNodes();
//
//        for (Node y : nodes) {
//            List<Node> adj = g.getAdjacentNodes(y);
//
//            if (adj.size() < 2) continue;
//
//            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
//            int[] choice;
//
//            while ((choice = gen.next()) != null) {
//                List<Node> _adj = GraphUtils.asList(choice, adj);
//                Node x = _adj.get(0);
//                Node z = _adj.get(1);
//
//                if (g.isDefCollider(x, y, z)) {
//                    g.addUndirectedEdge(x, z);
//                }
//            }
//
//        }

        IndependenceTest test = new IndTestMixedLrt(ds, parameters.get("alpha").doubleValue());
        Cpc pc = new Cpc(test);
        pc.setInitialGraph(g);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "CPC with the mixed LRT test, using the output of WFGS as an intial graph, marrying parents of WFGS";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}
