package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * Calculates the F1 statistic for adjacencies. See
 * <p>
 * https://en.wikipedia.org/wiki/F1_score
 * <p>
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 */
public class AncestorF1 implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AncestorF1() {

    }

    /**
     * Returns the name of the statistic.
     * @return the name of the statistic
     */
    @Override
    public String getAbbreviation() {
        return "Ancestor-F1";
    }

    /**
     * Returns the name of the statistic.
     * @return the name of the statistic
     */
    @Override
    public String getDescription() {
        return "F1 statistic for ancestry comparing the estimated graph to the true graph";
    }

    /**
     * Calculates the F1 statistic for adjacencies.
     * @param trueGraph The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph  The estimated graph (same type).
     * @param dataModel The data model.
     * @return the F1 statistic for adjacencies
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double precision = new AncestorPrecision().getValue(trueGraph, estGraph, dataModel);
        double recall = new AncestorRecall().getValue(trueGraph, estGraph, dataModel);
        return 2 * (precision * recall) / (precision + recall);
    }

    /**
     * Returns the norm value of the statistic.
     * @param value The value of the statistic.
     * @return  the value of the statistic
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
