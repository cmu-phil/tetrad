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

        Set<Triple> l = new HashSet<>();

        while ((choice = gen.next()) != null) {
            List<Node> v = GraphUtils.asList(choice, nodes);

            Node v1 = v.get(0);
            Node v2 = v.get(1);
            Node v3 = v.get(2);

            collect(gt, ge, l, v1, v3, v2);
            collect(gt, ge, l, v1, v2, v3);
            collect(gt, ge, l, v2, v1, v3);
        }

        int count = 0;
        int total = 0;

        for (Triple t : l) {
            Node x = t.getX();
            Node y = t.getY();
            Node z = t.getZ();

            if (!(gt.getEdge(x, y).isDirected() && ge.getEdge(x, y).isDirected())) {
                continue;
            }

            if (gt.isDirectedFromTo(x, y) == ge.isDirectedFromTo(y, x)) {
                count++;
                total++;
            }

            if (gt.isDirectedFromTo(y, z) == ge.isDirectedFromTo(z, y)) {
                count++;
            }

            total++;
            total++;
        }

        return count / (double) total;
    }

    private static void collect(Graph gt, Graph ge, Set<Triple> l, Node v1, Node v2, Node v3) {
        if (gt.isAdjacentTo(v1, v2) && gt.isAdjacentTo(v2, v3) && ge.isAdjacentTo(v1, v2) && ge.isAdjacentTo(v2, v3) && gt.isAdjacentTo(v1, v3) && !ge.isAdjacentTo(v1, v3)) {
            l.add(new Triple(v1, v2, v3));
        }
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
