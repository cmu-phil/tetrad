package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.List;

/**
 * Calculates the structural Hamming distance (SHD) between the estimated graph and
 * the true graph.
 *
 * @author jdramsey
 */
public class SHD2 implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "SHD2";
    }

    @Override
    public String getDescription() {
        return "Structural Hamming Distance 2";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {

//        List<Node> nodes = trueGraph.getNodes();
//        estGraph = GraphUtils.replaceNodes(estGraph, nodes);
//        int errors = 0;
//
//        for (int i = 0; i < nodes.size(); i++) {
//            for (int j = 0; j < nodes.size(); j++) {
//                if (i == j) continue;
//                Node a = nodes.get(i);
//                Node b = nodes.get(j);
//
//                Endpoint ea = null;
//                Endpoint eb = null;
//
//                if (trueGraph.isAdjacentTo(a, b)) {
//                    ea = trueGraph.getEndpoint(b, a);
//                }
//
//                if (estGraph.isAdjacentTo(a, b)) {
//                    eb = trueGraph.getEndpoint(b, a);
//                }
//
//                if (ea != null && eb != null && ea != eb) errors++;
//            }
//        }
//
//        return errors;

//
        AdjacencyConfusion c1 = new AdjacencyConfusion(trueGraph, estGraph);
        ArrowConfusion c2 = new ArrowConfusion(trueGraph, estGraph);
        return c1.getAdjFp() + c1.getAdjFn() + c2.getArrowsFp() + c2.getArrowsFn();
    }

    @Override
    /**
     * This will be given the index of the SHD stat.
     */
    public double getNormValue(double value) {
        return 1.0 - Math.tanh(0.001 * value);
    }
}
