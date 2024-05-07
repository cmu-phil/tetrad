package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * The 2-cycle precision. This counts 2-cycles manually, wherever they occur in the graphs. The true positives are the
 * number of 2-cycles in both the true and estimated graphs. Thus, if the true does not contains X-&gt;Y,Y-&gt;X and
 * estimated graph does contain it, one false positive is counted.
 *
 * @author josephramsey, rubens (November 2016)
 * @version $Id: $Id
 */
public class TwoCycleTruePositive implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The TwoCycleTruePositive class represents a statistic that calculates the number of true positives for 2-cycles
     * in both the true and estimated graphs. It is a measure of the accuracy of the estimated 2-cycle relationships.
     * <p>
     * The calculation is performed by creating an ArrowConfusion object with the true and estimated graphs, and then
     * retrieving the number of 2-cycle true positives using the getTwoCycleTp() method of the ArrowConfusion object.
     * <p>
     * Example usage:
     * <p>
     * // Creating a TwoCycleTruePositive object TwoCycleTruePositive tctp = new TwoCycleTruePositive();
     * <p>
     * // Obtaining the 2-cycle true positive value for a given true graph, estimated graph, and data model double
     * twoCycleTp = tctp.getValue(trueGraph, estGraph, dataModel);
     * <p>
     * Note: This class implements the Statistic interface and provides the required methods getAbbreviation() and
     * getDescription(). It also provides a default constructor, which takes no parameters.
     *
     * @see Statistic
     * @see ArrowConfusion
     */
    public TwoCycleTruePositive() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "2CTP";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "2-cycle true positive";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        return adjConfusion.getTwoCycleTp();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
