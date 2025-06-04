package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.OrientationConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Represents an implementation of the Statistic interface that calculates the Orientation Recall.
 * <p>
 * The Orientation Recall is a statistic that measures the accuracy of the estimated orientation of edges in a graph
 * compared to the true graph. It calculates the ratio of true positive orientations to the sum of true positive and
 * false negative orientations.
 */
public class OrientationRecall implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public OrientationRecall() {

    }

    /**
     * Returns the abbreviation for the statistic.
     *
     * @return The abbreviation.
     */
    @Override
    public String getAbbreviation() {
        return "OR";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Orientation Recall";
    }

    /**
     * Calculates the Orientation Recall statistic, which measures the accuracy of the estimated orientation of edges in
     * a graph compared to the true graph.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        OrientationConfusion oriConfusion = new OrientationConfusion(trueGraph, estGraph);
        int oriTp = oriConfusion.getTp();
        int oriFn = oriConfusion.getFn();
        return oriTp / (double) (oriTp + oriFn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
