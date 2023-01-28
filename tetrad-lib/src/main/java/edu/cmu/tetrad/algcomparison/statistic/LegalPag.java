package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * Legal PAG
 *
 * @author jdramsey
 */
public class LegalPag implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LegalPAG";
    }

    @Override
    public String getDescription() {
        return "1 if the estimated graph passes the Legal PAG check, 0 of not";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        SearchGraphUtils.LegalPagRet legalPag = SearchGraphUtils.isLegalPag(estGraph);
        System.out.println(legalPag.getReason());

        if (legalPag.isLegalPag()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
