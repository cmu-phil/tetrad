package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Calculates the structural Hamming distance (SHD) between the estimated graph and the true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class StructuralHammingDistance implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    private boolean compareToCpdag = false;

    /**
     * Constructs the statistic.
     */
    public StructuralHammingDistance() {
    }

    public StructuralHammingDistance(boolean compareToCpdag) {
        this.compareToCpdag = compareToCpdag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "SHD " + (compareToCpdag ? "(CPDAG)" : "(PDAG)");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Structural Hamming Distance" +(compareToCpdag ? " compared to CDAG of true PDAG" : " compared to true PDAG");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return GraphSearchUtils.structuralhammingdistance(trueGraph, estGraph, compareToCpdag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(0.001 * value);
    }
}
