package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import java.io.Serial;

/**
 * Implies Legal MAG
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ImpliesLegalMag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public ImpliesLegalMag() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "ImpliesMag";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph implies a legal MAG, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph mag = GraphTransforms.zhangMagFromPag(estGraph);
        GraphSearchUtils.LegalMagRet legalPag = GraphSearchUtils.isLegalMag(estGraph);

        if (legalPag.isLegalMag()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
