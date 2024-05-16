package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.LocalGraphConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

public class LocalGraphPrecision implements Statistic {
    @Override
    public String getAbbreviation() {
        return "LGP";
    }

    @Override
    public String getDescription() {
        return "Local Graph Precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        LocalGraphConfusion lgConfusion = new LocalGraphConfusion(trueGraph, estGraph);
        int lgTp = lgConfusion.getTp();
        int lgFp = lgConfusion.getFp();
        return lgTp / (double) (lgTp + lgFp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
