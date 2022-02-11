//package edu.cmu.tetrad.algcomparison.statistic;
//
//import edu.cmu.tetrad.data.DataModel;
//import edu.cmu.tetrad.data.DataSet;
//import edu.cmu.tetrad.graph.Graph;
//import edu.cmu.tetrad.search.SearchGraphUtils;
//import edu.cmu.tetrad.search.SemBicScorer;
//import edu.cmu.tetrad.sem.FmlBicScorer;
//import edu.cmu.tetrad.sem.SemEstimator;
//import edu.cmu.tetrad.sem.SemPm;
//
//import static java.lang.Math.tanh;
//
///**
// * Returns the p-value of a linear, Gaussian model..
// *
// * @author jdramsey
// */
//public class PValue implements Statistic {
//    static final long serialVersionUID = 23L;
//
//    @Override
//    public String getAbbreviation() {
//        return "PValue";
//    }
//
//    @Override
//    public String getDescription() {
//        return "PValue";
//    }
//
//    @Override
//    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
//        Graph dag = SearchGraphUtils.dagFromPattern(estGraph);
//        SemPm pm = new SemPm(dag);
//        SemEstimator est = new SemEstimator((DataSet) dataModel, pm);
//        est.estimate();
//        return est.getEstimatedSem().getPValue();
//
//
////        FmlBicScorer scorer = new FmlBicScorer((DataSet) dataModel, 1);
////        scorer.score(SearchGraphUtils.dagFromPattern(estGraph));
////        return scorer.getPValue();
//    }
//
//    @Override
//    public double getNormValue(double value) {
//        return tanh(value / 10);
//    }
//}
//
