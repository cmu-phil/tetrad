package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;

import java.io.Serial;

/**
 * Legal PAG
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LegalPag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public LegalPag() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "LegalPAG";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph passes the Legal PAG check, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphSearchUtils.LegalPagRet legalPag = GraphSearchUtils.isLegalPag(estGraph);
        System.out.println(legalPag.getReason());

        if (legalPag.isLegalPag()) {
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
