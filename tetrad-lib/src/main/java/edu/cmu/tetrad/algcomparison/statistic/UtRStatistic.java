package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class UtRStatistic implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "UtR";
    }

    @Override
    public String getDescription() {
        return "0 = completely correct, 1 = completely reversed";
    }

    @Override
    public double getValue(Graph gt, Graph ge, DataModel dataModel) {
        ge = GraphUtils.replaceNodes(ge, gt.getNodes());

        List<Node> nodes = ge.getNodes();

        ChoiceGenerator gen = new ChoiceGenerator(nodes.size(), 3);
        int[] choice;

        Set<Edge> l = new HashSet<>();

        while ((choice = gen.next()) != null) {
            List<Node> v = GraphUtils.asList(choice, nodes);

            Node v1 = v.get(0);
            Node v2 = v.get(1);
            Node v3 = v.get(2);

            count(ge, l, v1, v3, v2);
            count(ge, l, v1, v2, v3);
            count(ge, l, v2, v1, v3);
        }

        int c = 0;
        int t = 0;

        for (Edge e : l) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (gt.isDirectedFromTo(x, y) && !ge.isDirectedFromTo(x, y)) {
                c++;
            } else if (gt.isDirectedFromTo(y, x) && !ge.isDirectedFromTo(y, x)) {
                c++;
            }

            t++;
        }

        return c / (double) t;
    }

    private static void count(Graph ge, Set<Edge> l, Node v1, Node v2, Node v3) {
        if (ge.isAdjacentTo(v1, v2) && ge.isAdjacentTo(v2, v3) && !ge.isAdjacentTo(v1, v3)) {
            l.add(Edges.undirectedEdge(v2, v1));
            l.add(Edges.undirectedEdge(v2, v3));
        }
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
