package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.List;

/**
 * Checks whether a PAG is maximal.
 */
public class Maximal implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public Maximal() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "Maximal";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph is maximal, 0 if not";
    }

    /**
     * Checks whether a PAG is maximal.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        List<Node> nodes = estGraph.getNodes();
        boolean maximal = true;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node n1 = nodes.get(i);
                Node n2 = nodes.get(j);
                if (!estGraph.isAdjacentTo(n1, n2)) {
                    List<Node> inducingPath = estGraph.paths().getInducingPath(n1, n2);

                    if (inducingPath != null) {
                        TetradLogger.getInstance().log("Maximality check: Found an inducing path for "
                                                       + n1 + "..." + n2 + ": "
                                                       + GraphUtils.pathString(estGraph, inducingPath, false));
                        maximal = false;
                    }
                }
            }
        }

        return maximal ? 1.0 : 0.0;
    }

    /**
     * Returns the normalized value of the given statistic value.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic, between 0 and 1.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
