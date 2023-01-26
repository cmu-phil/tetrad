package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * Returns 1 if the estimated graph is a legal PAG, 0 if not.
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
        return "Legal PAG (1 if legal PAG, 0 if not)";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        SearchGraphUtils.LegalPagRet legalPag = SearchGraphUtils.isLegalPag(estGraph);
        System.out.println(legalPag.getReason());
        return legalPag.isLegalPag() ? 1.0 : 0.0;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
