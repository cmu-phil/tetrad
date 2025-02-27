package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
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
public class F1All implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public F1All() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "F1All";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "F1 statistic for adjacencies and orientations combined";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        ArrowConfusion arrowConfusion = new ArrowConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getTp();
        int adjFp = adjConfusion.getFp();
        int adjFn = adjConfusion.getFn();
        int adjTn = adjConfusion.getTn();
        int arrowTp = arrowConfusion.getTp();
        int arrowFp = arrowConfusion.getFp();
        int arrowFn = arrowConfusion.getFn();
        int arrowTn = arrowConfusion.getTn();
        double adjPrecision = adjTp / (double) (adjTp + adjFp);
        double adjRecall = adjTp / (double) (adjTp + adjFn);
        double arrowPrecision = arrowTp / (double) (arrowTp + arrowFp);
        double arrowRecall = arrowTp / (double) (arrowTp + arrowFn);
        return 4 * (adjPrecision * adjRecall * arrowPrecision * arrowRecall)
               / (adjPrecision + adjRecall + arrowPrecision + arrowRecall);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
