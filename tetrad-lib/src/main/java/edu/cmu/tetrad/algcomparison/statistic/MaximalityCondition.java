package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import java.util.List;

/**
 * @author josephramsey
 */
public class MaximalityCondition implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "MaximalMag";
    }

    @Override
    public String getDescription() {
        return "1 if the maximality condition passes in the MAG, 0 if not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = estGraph;

        Graph mag = GraphSearchUtils.pagToMag(estGraph);

        List<Node> nodes = pag.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) {
                    if (mag.paths().existsInducingPath(x, y)) {
                        return 0.0;
                    }
                }
            }
        }

        return 1;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
