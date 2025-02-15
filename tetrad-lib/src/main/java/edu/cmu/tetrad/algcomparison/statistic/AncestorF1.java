package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the F1 statistic for adjacencies. See
 * <p>
 * <a href="https://en.wikipedia.org/wiki/F1_score">...</a>
 * <p>
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 * @version $Id: $Id
 */
public class AncestorF1 implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AncestorF1() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "Ancestor-F1";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getDescription() {
        return "F1 statistic for ancestry comparing the estimated graph to the true graph";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calculates the F1 statistic for adjacencies.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double precision = new AncestorPrecision().getValue(trueGraph, estGraph, dataModel, new Parameters());
        double recall = new AncestorRecall().getValue(trueGraph, estGraph, dataModel, new Parameters());
        return 2 * (precision * recall) / (precision + recall);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the norm value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
