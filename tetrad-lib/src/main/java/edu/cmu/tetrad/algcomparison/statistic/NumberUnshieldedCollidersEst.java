package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.List;

/**
 * Represents the NumberEdgesEst statistic, which calculates the number of unshielded colliders in the estimated graph.
 */
public class NumberUnshieldedCollidersEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumberUnshieldedCollidersEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#UCEst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Unshielded Colliders in the Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        List<Node> nodes = estGraph.getNodes();
        int count = 0;

        for (int i = 0; i < nodes.size(); i++) {
            Node x = nodes.get(i);
            List<Node> adj = estGraph.getAdjacentNodes(x);

            for (int j = 0; j < adj.size(); j++) {
                for (int k = j + 1; k < adj.size(); k++) {
                    Node y = adj.get(j);
                    Node z = adj.get(k);

                    if (estGraph.isDefCollider(y, x, z) && !estGraph.isAdjacentTo(y, z)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(value / 1000.);
    }
}
