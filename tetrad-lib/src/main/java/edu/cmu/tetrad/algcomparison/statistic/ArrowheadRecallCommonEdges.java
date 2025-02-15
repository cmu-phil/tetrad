package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The arrow recall. This counts arrowheads maniacally, wherever they occur in the graphs. The true positives are the
 * number of arrowheads in both the true and estimated graphs. Thus, if the true contains X*-&gt;Y and estimated graph
 * either does not contain an edge from X to Y or else does not contain an arrowhead at X for an edge from X to Y, one
 * false positive is counted. Similarly for false negatives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ArrowheadRecallCommonEdges implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public ArrowheadRecallCommonEdges() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "AHRC";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Arrowhead recall (common edges)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        double arrowsTp = adjConfusion.getTpc();
        double arrowsFn = adjConfusion.getFnc();
        double den = arrowsTp + arrowsFn;
        return arrowsTp / den;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
